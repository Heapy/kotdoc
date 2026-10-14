// Regex.KDE.Regex 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting.regex

enum class Direction { Forward, Backward }
sealed interface KdeRegex {
    data object MatchAnyChar : KdeRegex
    data class MatchDynamic(val number: Long) : KdeRegex
    class MatchChar(val predicate: (Int) -> Boolean) : KdeRegex
    data class MatchSome(val regex: KdeRegex) : KdeRegex
    data class MatchAlt(val left: KdeRegex, val right: KdeRegex) : KdeRegex
    class MatchConcat private constructor(val left: KdeRegex, tail: kotlin.Lazy<KdeRegex>) : KdeRegex {
        constructor(left: KdeRegex, right: KdeRegex) : this(left, lazyOf(right))
        val right: KdeRegex by tail
        companion object {
            internal fun deferred(left: KdeRegex, right: () -> KdeRegex) = MatchConcat(left, kotlin.lazy(right))
        }
    }
    data class MatchCapture(val number: Long, val regex: KdeRegex) : KdeRegex
    data class MatchCaptured(val number: Long, val caseSensitive: Boolean) : KdeRegex
    data object AssertWordBoundary : KdeRegex
    data object AssertBeginning : KdeRegex
    data object AssertEnd : KdeRegex
    data class AssertPositive(val direction: Direction, val regex: KdeRegex) : KdeRegex
    data class AssertNegative(val direction: Direction, val regex: KdeRegex) : KdeRegex
    data class Possessive(val regex: KdeRegex) : KdeRegex
    data class Lazy(val regex: KdeRegex) : KdeRegex
    data class Subroutine(val number: Long) : KdeRegex
    data object MatchNull : KdeRegex

}

fun KdeRegex.haskellShow(): String = buildString {
    val stack = ArrayDeque<Any>(); stack.addLast(this@haskellShow)
    fun wrapped(prefix: String, vararg children: KdeRegex) {
        stack.addLast(")")
        for (i in children.indices.reversed()) { stack.addLast(children[i]); if (i > 0) stack.addLast(" ") }
        stack.addLast("($prefix ")
    }
    while (stack.isNotEmpty()) when (val r = stack.removeLast()) {
        is String -> append(r)
        KdeRegex.MatchAnyChar -> append("MatchAnyChar")
        is KdeRegex.MatchDynamic -> append("MatchDynamic ${r.number}")
        is KdeRegex.MatchChar -> append("(MatchChar <fn>)")
        is KdeRegex.MatchSome -> wrapped("MatchSome", r.regex)
        is KdeRegex.MatchAlt -> wrapped("MatchAlt", r.left, r.right)
        is KdeRegex.MatchConcat -> wrapped("MatchConcat", r.left, r.right)
        is KdeRegex.MatchCapture -> wrapped("MatchCapture ${r.number}", r.regex)
        is KdeRegex.MatchCaptured -> append("(MatchCaptured ${r.number} ${if (r.caseSensitive) "True" else "False"})")
        KdeRegex.AssertWordBoundary -> append("AssertWordBoundary")
        KdeRegex.AssertBeginning -> append("AssertBeginning")
        KdeRegex.AssertEnd -> append("AssertEnd")
        is KdeRegex.AssertPositive -> wrapped("AssertPositive ${r.direction}", r.regex)
        is KdeRegex.AssertNegative -> wrapped("AssertNegativeLookahead ${r.direction}", r.regex)
        is KdeRegex.Possessive -> wrapped("Possessive", r.regex)
        is KdeRegex.Lazy -> wrapped("Lazy", r.regex)
        is KdeRegex.Subroutine -> append("(Subroutine ${r.number})")
        KdeRegex.MatchNull -> append("MatchNull")
    }
}
