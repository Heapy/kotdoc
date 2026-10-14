// Regex.KDE.Match 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting.regex

import io.heapy.kotdoc.parsing.RegexCharacters
import io.heapy.kotdoc.parsing.Utf8String
import java.math.BigInteger
import java.util.TreeMap
import java.util.TreeSet

object KdeMatcher {
    data class Capture(val offset: Int, val length: Int) : Comparable<Capture> {
        override fun compareTo(other: Capture) = compareValuesBy(this, other, Capture::offset, Capture::length)
    }
    class Result(bytes: ByteArray, captures: Map<Long, Capture>) {
        private val content = bytes.copyOf()
        val bytes get() = content.copyOf()
        val captures: Map<Long, Capture> = java.util.Collections.unmodifiableMap(TreeMap(captures))
    }
    private data class Path(val bits: BigInteger = BigInteger.ZERO, val length: Int = 0) : Comparable<Path> {
        fun snoc(bit: Int) = Path(bits.shiftLeft(1).or(BigInteger.valueOf(bit.toLong())), length + 1)
        override fun compareTo(other: Path): Int = bits.shiftLeft(maxOf(0, other.length - length))
            .compareTo(other.bits.shiftLeft(maxOf(0, length - other.length))).takeIf { it != 0 } ?: length.compareTo(other.length)
    }
    private data class State(val offset: Int, val captures: Map<Long, Capture>, val path: Path) : Comparable<State> {
        override fun compareTo(other: State): Int {
            path.compareTo(other.path).takeIf { it != 0 }?.let { return it }
            offset.compareTo(other.offset).takeIf { it != 0 }?.let { return it }
            val a = captures.entries.iterator(); val b = other.captures.entries.iterator()
            while (a.hasNext() && b.hasNext()) {
                val x = a.next(); val y = b.next()
                x.key.compareTo(y.key).takeIf { it != 0 }?.let { return it }
                x.value.compareTo(y.value).takeIf { it != 0 }?.let { return it }
            }
            return a.hasNext().compareTo(b.hasNext())
        }
    }
    private data class StateKey(val offset: Int, val captures: Map<Long, Capture>)
    private fun sorted(states: Iterable<State>) = TreeSet<State>().also { it.addAll(states) }
    private fun dedup(states: Iterable<State>): Set<State> {
        val seen = HashSet<StateKey>()
        return TreeSet<State>().also { out -> for (s in sorted(states)) if (seen.add(StateKey(s.offset, s.captures))) out.add(s) }
    }
    private fun choice(bit: Int, states: Set<State>) = sorted(states.map { it.copy(path = it.path.snoc(bit)) })
    private fun prune(states: Set<State>): Set<State> = if (states.size > 2000) sorted(states.take(2000)) else states
    fun extractCapturingGroups(regex: KdeRegex): Map<Long, KdeRegex> {
        val groups = TreeMap<Long, KdeRegex>(); val stack = ArrayDeque<KdeRegex>(); stack.addLast(regex)
        while (stack.isNotEmpty()) when (val r = stack.removeLast()) {
            is KdeRegex.MatchCapture -> { groups.putIfAbsent(r.number, r.regex); stack.addLast(r.regex) }
            is KdeRegex.MatchAlt -> { stack.addLast(r.right); stack.addLast(r.left) }
            is KdeRegex.MatchConcat -> { stack.addLast(r.right); stack.addLast(r.left) }
            is KdeRegex.MatchSome -> stack.addLast(r.regex)
            is KdeRegex.AssertPositive -> stack.addLast(r.regex)
            is KdeRegex.AssertNegative -> stack.addLast(r.regex)
            is KdeRegex.Possessive -> stack.addLast(r.regex)
            is KdeRegex.Lazy -> stack.addLast(r.regex)
            else -> Unit
        }
        groups[0] = regex
        return java.util.Collections.unmodifiableMap(groups)
    }
    fun match(regex: KdeRegex, bytes: ByteArray): Result? = matchWithGroups(extractCapturingGroups(regex), regex, bytes)
    fun matchWithGroups(groups: Map<Long, KdeRegex>, regex: KdeRegex, input: ByteArray): Result? = Engine(input.copyOf(), TreeMap(groups)).run(regex)

    private class Engine(val bytes: ByteArray, val groups: Map<Long, KdeRegex>) {
        val work = ArrayDeque<() -> Unit>()
        private fun deliver(states: Set<State>, done: (Set<State>) -> Unit) { work.addLast { done(states) } }
        private fun lastCharOffset(n: Int): Int? {
            if (n == 0) return null
            var k = n - 1
            while (k > 0 && (bytes[k].toInt() and 255) in 0x80..<0xc0) k--
            return k
        }
        private fun boundary(n: Int): Boolean {
            val off = lastCharOffset(n) ?: return true
            val first = Utf8String.decode(bytes, off) ?: return true
            val next = Utf8String.decode(bytes, off + first.bytes) ?: return true
            return RegexCharacters.isWordChar(first.point) != RegexCharacters.isWordChar(next.point)
        }
        private fun captured(s: State, number: Long, sensitive: Boolean, dir: Direction): State? {
            val capture = s.captures[number] ?: return null
            val text = bytes.copyOfRange(capture.offset.coerceIn(0, bytes.size),
                (capture.offset.coerceAtLeast(0) + capture.length.coerceAtLeast(0)).coerceIn(0, bytes.size))
            if (sensitive) {
                val start = if (dir == Direction.Forward) s.offset else s.offset - text.size
                if (start < 0 || start > bytes.size || text.size > bytes.size - start || text.indices.any { bytes[start + it] != text[it] }) return null
                return s.copy(offset = if (dir == Direction.Forward) s.offset + text.size else start)
            }
            val points = Utf8String.toString(text).codePoints().toArray()
            var offset = s.offset
            for (point in if (dir == Direction.Forward) points else points.reversedArray()) {
                val off = if (dir == Direction.Forward) offset else lastCharOffset(offset) ?: return null
                val decoded = Utf8String.decode(bytes, off) ?: return null
                if (RegexCharacters.toLower(point) != RegexCharacters.toLower(decoded.point)) return null
                offset = if (dir == Direction.Forward) off + decoded.bytes else off
            }
            return s.copy(offset = offset)
        }
        private fun each(states: Set<State>, action: (State, (Set<State>) -> Unit) -> Unit, done: (Set<State>) -> Unit) {
            val iterator = states.iterator(); val output = TreeSet<State>()
            fun next() {
                if (!iterator.hasNext()) { deliver(output, done); return }
                val state = iterator.next()
                action(state) { result -> output.addAll(result); work.addLast(::next) }
            }
            work.addLast(::next)
        }
        private fun loop(regex: KdeRegex, dir: Direction, states: Set<State>, active: Set<Pair<Long, Int>>, cont: Int, stop: Int, done: (Set<State>) -> Unit) {
            exec(regex, dir, states, active) { first ->
                val initial = dedup(first); val seen = initial.mapTo(HashSet()) { StateKey(it.offset, it.captures) }; val output = TreeSet<State>()
                fun step(current: Set<State>) {
                    if (current.isEmpty()) { deliver(output, done); return }
                    output.addAll(choice(stop, current))
                    exec(regex, dir, choice(cont, current), active) { matched ->
                        val next = sorted(dedup(prune(matched)).filter { StateKey(it.offset, it.captures) !in seen })
                        next.forEach { seen.add(StateKey(it.offset, it.captures)) }
                        work.addLast { step(next) }
                    }
                }
                work.addLast { step(initial) }
            }
        }
        private fun exec(regex: KdeRegex, dir: Direction, states: Set<State>, active: Set<Pair<Long, Int>>, done: (Set<State>) -> Unit) {
            work.addLast {
                fun finish(result: Iterable<State>) = deliver(sorted(result), done)
                when (regex) {
                    KdeRegex.MatchNull -> deliver(states, done)
                    is KdeRegex.MatchChar, KdeRegex.MatchAnyChar -> finish(states.mapNotNull { s ->
                        val off = if (dir == Direction.Forward) s.offset else lastCharOffset(s.offset) ?: return@mapNotNull null
                        val d = Utf8String.decode(bytes, off) ?: return@mapNotNull null
                        if (regex is KdeRegex.MatchChar && !regex.predicate(d.point)) null
                        else s.copy(offset = if (dir == Direction.Forward) off + d.bytes else off)
                    })
                    KdeRegex.AssertBeginning -> finish(states.filter { it.offset == 0 })
                    KdeRegex.AssertEnd -> finish(states.filter { it.offset == bytes.size })
                    KdeRegex.AssertWordBoundary -> finish(states.filter { boundary(it.offset) })
                    is KdeRegex.MatchCaptured -> finish(states.mapNotNull { captured(it, regex.number, regex.caseSensitive, dir) })
                    is KdeRegex.MatchConcat -> {
                        val left = regex.left
                        if (left is KdeRegex.MatchConcat) exec(KdeRegex.MatchConcat(left.left, KdeRegex.MatchConcat(left.right, regex.right)), dir, states, active, done)
                        else if (dir == Direction.Forward) exec(left, dir, states, active) { first ->
                            if (first.isEmpty()) deliver(first, done) else exec(regex.right, dir, prune(first), active, done)
                        } else exec(regex.right, dir, states, active) { first -> exec(left, dir, first, active, done) }
                    }
                    is KdeRegex.MatchAlt -> exec(regex.left, dir, choice(0, states), active) { left ->
                        exec(regex.right, dir, choice(1, states), active) { right -> deliver(dedup(left + right), done) }
                    }
                    is KdeRegex.MatchSome -> loop(regex.regex, dir, states, active, 0, 1, done)
                    is KdeRegex.Lazy -> if (regex.regex is KdeRegex.MatchSome) loop(regex.regex.regex, dir, states, active, 1, 0, done)
                        else exec(regex.regex, dir, states, active, done)
                    is KdeRegex.MatchCapture -> each(states, { s, next -> exec(regex.regex, dir, sorted(listOf(s)), active) { found ->
                        next(sorted(found.map { m -> m.copy(captures = TreeMap(m.captures).also { it[regex.number] = Capture(s.offset, m.offset - s.offset) }) }))
                    } }, done)
                    is KdeRegex.Possessive -> each(states, { s, next -> exec(regex.regex, dir, sorted(listOf(s)), active) { found ->
                        next(sorted(found.firstOrNull()?.let { listOf(it.copy(path = s.path)) } ?: emptyList()))
                    } }, done)
                    is KdeRegex.AssertPositive -> each(states, { s, next -> exec(regex.regex, regex.direction, sorted(listOf(s)), active) { found ->
                        next(sorted(found.firstOrNull()?.let { listOf(it.copy(offset = s.offset, path = s.path)) } ?: emptyList()))
                    } }, done)
                    is KdeRegex.AssertNegative -> each(states, { s, next -> exec(regex.regex, regex.direction, sorted(listOf(s)), active) { found ->
                        next(if (found.isEmpty()) sorted(listOf(s)) else emptySet())
                    } }, done)
                    is KdeRegex.MatchDynamic -> {
                        val literal = ("%${regex.number}").codePoints().toArray().map { cp -> KdeRegex.MatchChar { it == cp } }
                            .asReversed().fold<KdeRegex, KdeRegex>(KdeRegex.MatchNull) { tail, r -> KdeRegex.MatchConcat(r, tail) }
                        exec(literal, dir, states, active, done)
                    }
                    is KdeRegex.Subroutine -> {
                        val group = groups[regex.number]
                        if (group == null) deliver(states, done) else each(states, { s, next ->
                            val call = regex.number to s.offset
                            if (call in active) next(emptySet()) else exec(group, dir, sorted(listOf(s)), active + call, next)
                        }) { found -> deliver(dedup(found), done) }
                    }
                }
            }
        }
        fun run(regex: KdeRegex): Result? {
            var result: Result? = null
            exec(regex, Direction.Forward, sorted(listOf(State(0, emptyMap(), Path()))), emptySet()) { states ->
                states.firstOrNull()?.let { result = Result(bytes.copyOfRange(0, it.offset), it.captures) }
            }
            while (work.isNotEmpty()) work.removeLast().invoke()
            return result
        }
    }
}
