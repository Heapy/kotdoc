// GHC 9.12.2 GHC.Internal.Data.OldList.actualSort, BSD-3-Clause.
// Copyright notices: licenses/GHC-base-BSD-3-Clause.txt; unchanged source in vendor/ghc-pretty-sort-9.12.2.
package io.heapy.kotdoc.json

/** Source natural runs/four-way reduction, including the fused exhausted-list transitions. */
internal object PrettySort {
    fun <A> sort(input: List<A>, gt: (A, A) -> Boolean): List<A> {
        fun fused(streams: List<Sequence<A>>): Sequence<A> = sequence {
            class Head(stream: Sequence<A>) {
                private val iterator = stream.iterator()
                var present = iterator.hasNext()
                var value: A? = if (present) iterator.next() else null
                @Suppress("UNCHECKED_CAST") fun get(): A = value as A
                fun pop(): A {
                    val result = get(); present = iterator.hasNext()
                    value = if (present) iterator.next() else null
                    return result
                }
            }
            var a = Head(streams[0]); var b = Head(streams[1])
            var c = Head(streams.getOrElse(2) { emptySequence() })
            val d = Head(streams.getOrElse(3) { emptySequence() })
            // 2=merge, 3=merge3, 31=merge3X, 32=merge3Y,
            // 4=merge4, 41=merge4X, 42=merge4Y, 43=merge4XY.
            var state = streams.size
            var x: A? = null; var y: A? = null
            @Suppress("UNCHECKED_CAST") fun heldX(): A = x as A
            @Suppress("UNCHECKED_CAST") fun heldY(): A = y as A
            while (true) when (state) {
                2 -> when {
                    a.present && b.present -> yield(if (gt(a.get(), b.get())) b.pop() else a.pop())
                    a.present -> yield(a.pop())
                    b.present -> yield(b.pop())
                    else -> break
                }
                3 -> when {
                    !a.present -> { a = b; b = c; state = 2 }
                    !b.present -> { b = c; state = 2 }
                    else -> { x = if (gt(a.get(), b.get())) b.pop() else a.pop(); state = 31 }
                }
                31 -> when {
                    !c.present -> { yield(heldX()); state = 2 }
                    gt(heldX(), c.get()) -> yield(c.pop())
                    else -> { yield(heldX()); state = 3 }
                }
                32 -> when {
                    !a.present -> { yield(heldY()); a = b; b = c; state = 2 }
                    gt(a.get(), heldY()) -> { yield(heldY()); state = 3 }
                    else -> yield(a.pop())
                }
                4 -> when {
                    !a.present -> { a = b; b = c; c = d; state = 3 }
                    !b.present -> { b = c; c = d; state = 3 }
                    else -> { x = if (gt(a.get(), b.get())) b.pop() else a.pop(); state = 41 }
                }
                41 -> when {
                    !c.present -> { c = d; state = 31 }
                    !d.present -> state = 31
                    else -> { y = if (gt(c.get(), d.get())) d.pop() else c.pop(); state = 43 }
                }
                42 -> when {
                    !a.present -> { a = b; b = c; c = d; state = 32 }
                    !b.present -> { b = c; c = d; state = 32 }
                    else -> { x = if (gt(a.get(), b.get())) b.pop() else a.pop(); state = 43 }
                }
                43 -> if (gt(heldX(), heldY())) { yield(heldY()); state = 41 }
                      else { yield(heldX()); state = 42 }
            }
        }
        fun merge(a: Sequence<A>, b: Sequence<A>) = fused(listOf(a,b))
        fun merge3(a: Sequence<A>, b: Sequence<A>, c: Sequence<A>) = fused(listOf(a,b,c))
        fun merge4(a: Sequence<A>, b: Sequence<A>, c: Sequence<A>, d: Sequence<A>) = fused(listOf(a,b,c,d))
        if (input.size <= 1) return input
        if (input.size <= 4) {
            val x = input.map { sequenceOf(it) }
            return when (x.size) { 2 -> merge(x[0],x[1]); 3 -> merge3(x[0],x[1],x[2]); else -> merge4(x[0],x[1],x[2],x[3]) }.toList()
        }
        var runs = mutableListOf<Sequence<A>>()
        var i = 0
        while (i < input.size) {
            val start = i++
            if (i < input.size) {
                val descending = gt(input[i - 1], input[i]); i++
                while (i < input.size && gt(input[i - 1], input[i]) == descending) i++
                val run = input.subList(start, i)
                runs.add((if (descending) run.asReversed() else run).asSequence())
                // sequences [] = [[]], not []; the trailing empty run affects
                // four-way reduction grouping for nontransitive comparisons.
                if (i == input.size) runs.add(emptySequence())
            } else runs.add(sequenceOf(input[start]))
        }
        while (runs.size > 1) {
            val next = mutableListOf<Sequence<A>>(); i = 0
            while (i < runs.size) {
                val remain = runs.size - i
                val take = when (remain) { 1 -> 1; 2, 5 -> 2; 3, 6 -> 3; else -> 4 }
                val a = runs[i]
                next.add(when (take) { 1 -> a; 2 -> merge(a,runs[i+1]); 3 -> merge3(a,runs[i+1],runs[i+2]); else -> merge4(a,runs[i+1],runs[i+2],runs[i+3]) })
                i += take
            }
            runs = next
        }
        return runs.single().toList()
    }
}
