// Skylighting.Tokenizer 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.ast.UnicodeText
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.highlighting.regex.*
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters
import io.heapy.kotdoc.parsing.Utf8String
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

data class HighlightToken(val type: TokenType, val text: String)
typealias SourceLine = List<HighlightToken>

class TokenizerConfig(
    syntaxMap: SyntaxMap,
    val traceOutput: Boolean = false,
    val trace: (String) -> Unit = { System.err.println(it) },
) {
    val syntaxMap = syntaxMap(syntaxMap)
    fun haskellShow(precedence: Int = 0): String {
        val text = "TokenizerConfig {syntaxMap = ${syntaxShow(syntaxMap)}, traceOutput = ${if (traceOutput) "True" else "False"}}"
        return if (precedence > 10) "($text)" else text
    }
    override fun toString() = haskellShow()
}

sealed interface Tokenization {
    class Success(lines: List<SourceLine>) : Tokenization {
        val lines = syntaxList(lines.map(::syntaxList))
    }
    data class Failure(val error: String) : Tokenization
}

/** Kotlin implementation of the original physical-line state machine. */
object Tokenizer {
    fun tokenize(config: TokenizerConfig, syntax: Syntax, input: String): Tokenization =
        Engine(config).tokenize(syntax, input)

    fun normalizeHighlighting(tokens: List<HighlightToken>): SourceLine {
        val result = mutableListOf<HighlightToken>()
        var i = 0
        while (i < tokens.size) {
            val first = tokens[i++]
            if (first.text.isEmpty()) continue
            val text = StringBuilder(first.text)
            while (i < tokens.size && tokens[i].type == first.type) text.append(tokens[i++].text)
            result.add(HighlightToken(first.type, text.toString()))
        }
        return syntaxList(result)
    }

    private class NoMatch : RuntimeException(null, null, false, false)
    private class TokenError(val error: String) : RuntimeException(null, null, false, false)
    private class Stack(val context: Context, val captures: Map<Long, ByteArray>, val tail: Stack?)
    private data class State(
        val bytes: ByteArray, val offset: Int, val endline: Boolean, val prevChar: Int,
        val stack: Stack, val captures: Map<Long, ByteArray> = emptyMap(),
        val column: Long = 0, val continuation: Boolean = false,
        val firstNonspace: Long? = null, val loopCounter: Int = 0,
    )
    private sealed interface Attempt {
        data object Miss : Attempt
        data class Hit(val token: HighlightToken?) : Attempt
    }

    private class Engine(val config: TokenizerConfig) {
        lateinit var state: State
        private val work = ArrayDeque<() -> Unit>()
        private fun info(message: () -> String) { if (config.traceOutput) config.trace(message()) }
        private fun guard(test: Boolean) { if (!test) throw NoMatch() }
        private fun lookup(name: String, syntax: Syntax): Context? =
            syntax.contexts[if (name.isEmpty()) syntax.startingContext.takeUnless(String::isEmpty) ?: return null else name]
        private fun current() = state.stack.context
        private fun remaining() = state.bytes.copyOfRange(state.offset, state.bytes.size)
        private fun point(at: Int = state.offset) = Utf8String.decode(state.bytes, at)
        private fun byte(at: Int) = state.bytes.getOrNull(at)?.toInt()?.and(255)
        private fun scalarCount(text: String) = text.codePointCount(0, text.length)
        // utf8-string encodes Haskell Char verbatim, including surrogate Char;
        // Data.Text input encoding above/below instead replaces lone surrogates.
        private fun charBytes(point: Int): ByteArray = when {
            point < 0x80 -> byteArrayOf(point.toByte())
            point < 0x800 -> byteArrayOf((0xc0 + (point shr 6)).toByte(), (0x80 + (point and 63)).toByte())
            point < 0x10000 -> byteArrayOf((0xe0 + (point shr 12)).toByte(), (0x80 + ((point shr 6) and 63)).toByte(), (0x80 + (point and 63)).toByte())
            else -> byteArrayOf((0xf0 + (point shr 18)).toByte(), (0x80 + ((point shr 12) and 63)).toByte(), (0x80 + ((point shr 6) and 63)).toByte(), (0x80 + (point and 63)).toByte())
        }
        private fun encode(text: String): ByteArray = buildString {
            text.codePoints().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) }
        }.toByteArray(Charsets.UTF_8)
        private fun strict(bytes: ByteArray): String = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw TokenError("ByteString ${HaskellString.quoteCodePoints(bytes.map { it.toInt() and 255 }.toIntArray())}is not UTF8")
        }
        private fun endOfChars(n: Int, start: Int = state.offset): Int {
            var at = start; var left = n
            while (at < state.bytes.size && left > 0) { at += checkNotNull(point(at)).bytes; left-- }
            return at
        }
        private fun takeChars(n: Int): String {
            guard(n != 0)
            val end = endOfChars(n)
            guard(end > state.offset)
            val text = strict(state.bytes.copyOfRange(state.offset, end))
            state = state.copy(offset = end, endline = end == state.bytes.size,
                prevChar = text.codePointBefore(text.length), column = state.column + n, loopCounter = 0)
            return text
        }

        fun tokenize(syntax: Syntax, input: String): Tokenization {
            val start = lookup(syntax.startingContext, syntax) ?: return Tokenization.Failure("No starting context specified")
            state = State(byteArrayOf(), 0, input.isEmpty(), 10, Stack(start, emptyMap(), null))
            val bytes = encode(input)
            val lines = mutableListOf<SourceLine>()
            return try {
                var at = 0
                while (at < bytes.size) {
                    var end = at
                    while (end < bytes.size && bytes[end] != 10.toByte()) end++
                    val line = bytes.copyOfRange(at, end)
                    state = state.copy(bytes = line, offset = 0, endline = line.isEmpty(), prevChar = 10,
                        column = 0, continuation = false, loopCounter = 0,
                        firstNonspace = line.indices.firstOrNull { !ParserCharacters.isSpace(line[it].toInt() and 255) }?.toLong())
                    if (line.isEmpty()) {
                        lineSwitches(empty = true)
                        lines.add(emptyList())
                    } else {
                        val tokens = mutableListOf<HighlightToken>()
                        while (!state.endline) {
                            val before = state
                            when (val attempt = getToken()) {
                                Attempt.Miss -> { state = before; break }
                                is Attempt.Hit -> attempt.token?.let(tokens::add)
                            }
                        }
                        if (!state.endline) throw TokenError("Could not match anything at line ${lines.size + 1} column ${state.column}")
                        lines.add(normalizeHighlighting(tokens))
                        if (!state.continuation) lineSwitches(empty = false)
                    }
                    at = end + 1
                }
                Tokenization.Success(lines)
            } catch (e: TokenError) { Tokenization.Failure(e.error) }
        }

        private fun switches(switches: List<ContextSwitch>) {
            for (switch in switches) when (switch) {
                ContextSwitch.Pop -> {
                    val tail = state.stack.tail
                    if (tail == null) info { "WARNING: Tried to pop only element on context stack!" }
                    else { state = state.copy(stack = tail); stackInfo() }
                }
                is ContextSwitch.Push -> {
                    val name = switch.context
                    val context = config.syntaxMap[name.syntax]?.let { lookup(name.context, it) }
                        ?: throw TokenError("Unknown syntax or context: ${name.haskellShow()}")
                    state = state.copy(stack = Stack(context, emptyMap(), state.stack)); stackInfo()
                }
            }
        }
        private fun stackInfo() = info {
            val names = mutableListOf<String>(); var stack: Stack? = state.stack
            while (stack != null) { names.add(stack.context.name); stack = stack.tail }
            "CONTEXT STACK ${syntaxShow(names)}"
        }
        private fun sameContexts(a: Stack, b: Stack): Boolean {
            var x: Stack? = a; var y: Stack? = b
            while (x != null && y != null) {
                if (x === y) return true
                if (x.context != y.context) return false
                x = x.tail; y = y.tail
            }
            return x == null && y == null
        }
        private fun lineSwitches(empty: Boolean) {
            var count = 1024
            while (true) {
                val context = current()
                val changes = if (empty) context.lineEmptyContext else context.lineEndContext
                if (changes.isEmpty()) return
                if (count <= 0) {
                    info { "Endless switch context transitions for line ${if (empty) "empty" else "end"} context, aborting highlighting of line." }
                    return
                }
                if (!empty) info { "checkLineEnd for ${HaskellString.quote(context.name)} cLineEndContext = ${syntaxShow(changes)}" }
                val before = state.stack
                switches(changes)
                if (sameContexts(before, state.stack)) return
                count--
            }
        }
        private fun capture(number: Long): String {
            info { "Retrieving capture $number" }
            val bytes = state.stack.captures[number] ?: run { info { "Not found" }; throw NoMatch() }
            val text = strict(bytes); info { "Got ${HaskellString.quote(text)}" }; return text
        }
        private fun addCaptures() {
            if (state.captures.isEmpty()) return
            info {
                "Adding captures to ${HaskellString.quote(current().name)}: Captures {unCaptures = fromList " +
                    state.captures.toSortedMap().entries.joinToString(",", "[", "]") {
                        "(${it.key},${HaskellString.quoteCodePoints(it.value.map { b -> b.toInt() and 255 }.toIntArray())})"
                    } + "}"
            }
            state = state.copy(stack = Stack(current(), state.captures, state.stack.tail))
        }

        // Deliveries, child rules and IncludeRules all use this work stack. A
        // finite nested rule chain never consumes the JVM call stack.
        private fun deliver(value: Attempt, done: (Attempt) -> Unit) { work.addLast { done(value) } }
        private fun select(rules: List<Rule>, done: (Attempt) -> Unit) {
            val before = state
            fun next(index: Int) {
                work.addLast {
                    state = before
                    if (index == rules.size) deliver(Attempt.Miss, done)
                    else tryRule(rules[index]) { result ->
                        if (result == Attempt.Miss) next(index + 1) else deliver(result, done)
                    }
                }
            }
            next(0)
        }
        private fun getToken(): Attempt {
            if (state.endline) return Attempt.Miss
            val context = current()
            if (state.loopCounter > 1024) {
                info { "Endless state transitions, aborting highlighting of line." }
                val text = strict(remaining())
                state = state.copy(offset = state.bytes.size, endline = true,
                    prevChar = text.codePointBefore(text.length), column = state.column + scalarCount(text))
                return Attempt.Hit(HighlightToken(context.attribute, text))
            }
            state = state.copy(loopCounter = state.loopCounter + 1)
            var result: Attempt? = null
            select(context.rules) { result = it }
            while (work.isNotEmpty()) work.removeLast().invoke()
            if (result != Attempt.Miss) return checkNotNull(result)
            return try {
                when {
                    context.fallthroughContext.isNotEmpty() -> { switches(context.fallthroughContext); Attempt.Hit(null) }
                    context.fallthrough -> { switches(listOf(ContextSwitch.Pop)); Attempt.Hit(null) }
                    else -> {
                        val token = HighlightToken(context.attribute, normalChunk())
                        info { "FALLTHROUGH ${showToken(token)}" }; Attempt.Hit(token)
                    }
                }
            } catch (_: NoMatch) { Attempt.Miss }
        }
        private fun tryRule(rule: Rule, done: (Attempt) -> Unit) {
            if (state.offset == state.bytes.size) { deliver(Attempt.Miss, done); return }
            info { "Trying rule ${rule.haskellShow()}" }
            if (rule.column != null && rule.column != state.column || rule.firstNonspace && state.firstNonspace != state.column) {
                deliver(Attempt.Miss, done); return
            }
            val old = state
            state = state.copy(captures = emptyMap())
            fun finish(parent: HighlightToken?) {
                select(rule.children) { child ->
                    val token = when {
                        parent == null -> null
                        rule.lookahead -> {
                            state = state.copy(bytes = old.bytes, offset = old.offset, endline = old.endline,
                                prevChar = old.prevChar, column = old.column, loopCounter = old.loopCounter)
                            null
                        }
                        else -> parent.copy(text = parent.text + ((child as? Attempt.Hit)?.token?.text ?: ""))
                    }
                    info { "${rule.matcher.haskellShow().takeWhile { it != ' ' }} MATCHED ${showToken(token)}" }
                    switches(rule.contextSwitch); addCaptures(); deliver(Attempt.Hit(token), done)
                }
            }
            if (rule.matcher is Matcher.IncludeRules) {
                val name = rule.matcher.context
                val context = config.syntaxMap[name.syntax]?.let { lookup(name.context, it) }
                    ?: throw TokenError("IncludeRules in ${current().syntax} requires undefined context ${name.context}##${name.syntax}")
                select(context.rules) { result ->
                    when (result) {
                        Attempt.Miss -> deliver(result, done)
                        is Attempt.Hit -> {
                            state = state.copy(captures = emptyMap())
                            val token = result.token?.let { if (rule.includeAttribute && it.type == TokenType.NormalTok) it.copy(type = rule.attribute) else it }
                            finish(token)
                        }
                    }
                }
            } else {
                val text = try { match(rule) } catch (_: NoMatch) { deliver(Attempt.Miss, done); return }
                finish(if (text.isEmpty()) null else HighlightToken(rule.attribute, text))
            }
        }
        private fun showToken(token: HighlightToken?) = if (token == null) "Nothing" else "Just (${token.type.name},${HaskellString.quote(token.text)})"
        private fun prefix(text: String): Boolean {
            val pattern = encode(text)
            return pattern.size <= state.bytes.size - state.offset && pattern.indices.all { pattern[it] == state.bytes[state.offset + it] }
        }
        private fun peekText(n: Int) = strict(state.bytes.copyOfRange(state.offset, endOfChars(n)))
        private fun dynamicChar(char: Int, dynamic: Boolean): Int =
            if (dynamic && char in 48..57) capture((char - 48).toLong()).let { guard(it.isNotEmpty()); it.codePointAt(0) } else char
        private fun dynamicText(text: String): String {
            val parts = text.split('%')
            return buildString {
                append(parts[0])
                for (part in parts.drop(1)) {
                    if (part.isNotEmpty() && part[0] in '0'..'9') { append(capture((part[0] - '0').toLong())); append(part.substring(1)) }
                    else { append('%'); append(part) }
                }
            }
        }
        private fun normalChunk(): String {
            val first = point() ?: throw NoMatch()
            var end = state.offset
            when {
                first.point == 32 -> while (byte(end) == 32) end++
                first.point < 128 && ParserCharacters.isAlphaNum(first.point) ->
                    while (end < state.bytes.size) { val d = checkNotNull(point(end)); if (!ParserCharacters.isAlphaNum(d.point)) break; end += d.bytes }
                else -> return takeChars(1)
            }
            var count = 0; var at = state.offset
            while (at < end) { at += checkNotNull(point(at)).bytes; count++ }
            return takeChars(count)
        }
        private fun match(rule: Rule): String = when (val matcher = rule.matcher) {
            is Matcher.DetectChar -> { guard(point()?.point == dynamicChar(matcher.char, rule.dynamic)); takeChars(1) }
            is Matcher.Detect2Chars -> {
                val c = dynamicChar(matcher.char, rule.dynamic); val d = dynamicChar(matcher.char1, rule.dynamic)
                val first = point(); guard(first?.point == c)
                guard(point(state.offset + checkNotNull(first).bytes)?.point == d); takeChars(2)
            }
            is Matcher.AnyChar -> { guard(point()?.point in matcher.chars); takeChars(1) }
            is Matcher.RangeDetect -> {
                val first = point(); guard(first?.point == matcher.char)
                val start = state.offset + checkNotNull(first).bytes; var at = start
                while (at < state.bytes.size && checkNotNull(point(at)).point != matcher.char1) at += checkNotNull(point(at)).bytes
                guard(at < state.bytes.size); val interior = strict(state.bytes.copyOfRange(start, at)); takeChars(scalarCount(interior) + 2)
            }
            is Matcher.StringDetect -> {
                val text = if (rule.dynamic) dynamicText(matcher.text).also { info { "Dynamic string: ${HaskellString.quote(it)}" } } else matcher.text
                val n = scalarCount(text)
                guard(if (rule.caseSensitive) prefix(text) else UnicodeText.toCaseFold(text) == UnicodeText.toCaseFold(peekText(n)))
                takeChars(n)
            }
            is Matcher.WordDetect -> {
                val n = scalarCount(matcher.text)
                val text = if (rule.caseSensitive) { guard(prefix(matcher.text)); matcher.text } else peekText(n).also {
                    guard(UnicodeText.toCaseFold(matcher.text) == UnicodeText.toCaseFold(it))
                }
                guard(text.isNotEmpty())
                guard(state.prevChar in rule.wordDelimiters || text.codePointAt(0) in rule.wordDelimiters)
                guard((point(endOfChars(n))?.point ?: 10) in rule.wordDelimiters || text.codePointBefore(text.length) in rule.wordDelimiters)
                takeChars(scalarCount(text))
            }
            is Matcher.Keyword -> {
                val words = when (val w = matcher.words) {
                    is KeywordWords.Unresolved -> throw TokenError("Keyword with unresolved list ${HaskellString.quote(w.name)}")
                    is KeywordWords.Resolved -> w.words
                }
                guard(state.prevChar in matcher.attributes.delimiters)
                var at = state.offset
                while (at < state.bytes.size && checkNotNull(point(at)).point !in matcher.attributes.delimiters) at += checkNotNull(point(at)).bytes
                guard(at != state.offset)
                val text = strict(state.bytes.copyOfRange(state.offset, at)); guard(text in words); takeChars(scalarCount(text))
            }
            is Matcher.RegExpr -> regex(rule.dynamic, matcher.regex)
            Matcher.Int, Matcher.HlCOct, Matcher.HlCHex, Matcher.Float -> {
                guard(state.prevChar in rule.wordDelimiters)
                val start = state.offset; var at = start
                when (matcher) {
                    Matcher.Int -> { while (digit(byte(at))) at++; guard(at > start) }
                    Matcher.HlCOct -> { guard(byte(at++) == 48); val digits = at; while (byte(at) in 48..55) at++; guard(at > digits) }
                    Matcher.HlCHex -> { guard(byte(at++) == 48); guard(byte(at++) in listOf(88, 120)); val digits = at; while (hex(byte(at))) at++; guard(at > digits) }
                    Matcher.Float -> {
                        while (digit(byte(at))) at++; val before = at - start
                        guard(byte(at++) == 46); val after = at; while (digit(byte(at))) at++
                        guard(before != 0 || at > after)
                        if (byte(at) in listOf(69, 101)) {
                            var exponent = at + 1; if (byte(exponent) in listOf(43, 45)) exponent++
                            val digits = exponent; while (digit(byte(exponent))) exponent++
                            if (exponent > digits) at = exponent
                        }
                    }
                }
                takeChars(at - start)
            }
            Matcher.HlCStringChar -> takeChars(escapeEnd(state.offset) - state.offset)
            Matcher.HlCChar -> {
                val start = state.offset; guard(byte(start) == 39)
                val end = if (byte(start + 1) == 92) escapeEnd(start + 1) else {
                    guard(byte(start + 1) != null && byte(start + 1) !in listOf(39, 92)); start + 2
                }
                guard(byte(end) == 39); takeChars(end + 1 - start)
            }
            is Matcher.LineContinue -> {
                guard(remaining().contentEquals(charBytes(matcher.char)))
                state = state.copy(continuation = true); takeChars(1)
            }
            Matcher.DetectSpaces -> {
                var at = state.offset; while (at < state.bytes.size && ParserCharacters.isSpace(checkNotNull(byte(at)))) at++
                guard(at > state.offset); takeChars(at - state.offset)
            }
            Matcher.DetectIdentifier -> {
                val start = state.offset; val first = checkNotNull(byte(start))
                guard(first == 95 || first < 128 && ParserCharacters.isAlpha(first))
                var at = start + 1
                while (at < state.bytes.size) { val d = checkNotNull(byte(at)); if (d >= 128 || !(ParserCharacters.isAlphaNum(d) || d == 95)) break; at++ }
                takeChars(at - start)
            }
            is Matcher.IncludeRules -> error("IncludeRules requires the rule work stack")
        }
        private fun digit(c: Int?) = c != null && c in 48..57
        private fun hex(c: Int?) = c != null && (c in 48..57 || c in 65..70 || c in 97..102)
        private fun escapeEnd(start: Int): Int {
            guard(byte(start) == 92)
            var at = start + 2
            when (val c = byte(start + 1)) {
                88, 120 -> { val digits = at; while (hex(byte(at))) at++; guard(at > digits) }
                48 -> while (byte(at) in 48..55) at++
                else -> guard(c != null && c.toChar() in "abefnrtv\"'?\\")
            }
            return at
        }
        private fun regex(dynamic: Boolean, re: RE): String {
            val pattern = re.bytes
            if (pattern.size >= 2 && pattern[0] == 92.toByte() && pattern[1] == 98.toByte()) {
                val first = point()
                if (first != null) guard(RegexCharacters.isWordChar(state.prevChar) != RegexCharacters.isWordChar(first.point))
            }
            val compiled = when (val c = re.compile()) {
                is CompiledRE.Failure -> throw TokenError("Error compiling regex ${Utf8String.toString(pattern)}: ${c.error}")
                is CompiledRE.Success -> c
            }
            val input = remaining()
            val matched = if (dynamic) KdeMatcher.match(subDynamic(compiled.regex), input)
                else KdeMatcher.matchWithGroups(compiled.groups, compiled.regex, input)
            guard(matched != null); val result = checkNotNull(matched)
            if (result.captures.isNotEmpty()) state = state.copy(captures = result.captures.mapValues { (_, c) ->
                val start = c.offset.coerceIn(0, input.size)
                val end = (start.toLong() + c.length.coerceAtLeast(0)).coerceAtMost(input.size.toLong()).toInt()
                input.copyOfRange(start, end)
            })
            var at = 0; var length = 0; val bytes = result.bytes
            while (at < bytes.size) { at += checkNotNull(Utf8String.decode(bytes, at)).bytes; length++ }
            return takeChars(length)
        }
        private fun subDynamic(regex: KdeRegex): KdeRegex {
            val tasks = ArrayDeque<() -> Unit>(); val values = ArrayDeque<KdeRegex>()
            fun visit(r: KdeRegex) {
                tasks.addLast {
                    when (r) {
                        is KdeRegex.MatchDynamic -> {
                            val points = capture(r.number).codePoints().toArray()
                            var value: KdeRegex = KdeRegex.MatchNull
                            for (p in points.reversed()) value = KdeRegex.MatchConcat(KdeRegex.MatchChar { it == p }, value)
                            values.addLast(value)
                        }
                        is KdeRegex.MatchAlt -> { tasks.addLast { val b = values.removeLast(); val a = values.removeLast(); values.addLast(KdeRegex.MatchAlt(a, b)) }; visit(r.right); visit(r.left) }
                        is KdeRegex.MatchConcat -> { tasks.addLast { val b = values.removeLast(); val a = values.removeLast(); values.addLast(KdeRegex.MatchConcat(a, b)) }; tasks.addLast { visit(r.right) }; visit(r.left) }
                        is KdeRegex.MatchSome -> { tasks.addLast { values.addLast(KdeRegex.MatchSome(values.removeLast())) }; visit(r.regex) }
                        is KdeRegex.MatchCapture -> { tasks.addLast { values.addLast(KdeRegex.MatchCapture(r.number, values.removeLast())) }; visit(r.regex) }
                        is KdeRegex.AssertPositive -> { tasks.addLast { values.addLast(KdeRegex.AssertPositive(r.direction, values.removeLast())) }; visit(r.regex) }
                        is KdeRegex.AssertNegative -> { tasks.addLast { values.addLast(KdeRegex.AssertNegative(r.direction, values.removeLast())) }; visit(r.regex) }
                        // Original substitution deliberately leaves Lazy and Possessive opaque.
                        else -> values.addLast(r)
                    }
                }
            }
            visit(regex); while (tasks.isNotEmpty()) tasks.removeLast().invoke()
            return values.single()
        }
    }
}
