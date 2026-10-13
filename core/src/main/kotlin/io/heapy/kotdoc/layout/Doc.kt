// Text.DocLayout 0.6, BSD-3-Clause; Copyright (C) 2010-2024 John MacFarlane.
package io.heapy.kotdoc.layout

sealed interface Doc<out A> {
    data class Text<A>(val width: Long, val value: A) : Doc<A>
    data class Block<A>(val width: Long, val lines: List<Attributed<A>>) : Doc<A>
    data class VFill<A>(val width: Long, val value: A) : Doc<A>
    data class CookedText<A>(val width: Long, val value: Attributed<A>) : Doc<A>
    data class Prefixed<A>(val prefix: String, val doc: Doc<A>) : Doc<A>
    data class BeforeNonBlank<A>(val doc: Doc<A>) : Doc<A>
    data class Flush<A>(val doc: Doc<A>) : Doc<A>
    data object BreakingSpace : Doc<Nothing>
    data class AfterBreak(val value: String) : Doc<Nothing>
    data object CarriageReturn : Doc<Nothing>
    data object NewLine : Doc<Nothing>
    data class BlankLines(val count: Long) : Doc<Nothing>
    data class Concat<A>(val left: Doc<A>, val right: Doc<A>) : Doc<A>
    data class Styled<A>(val style: StyleRequest, val doc: Doc<A>) : Doc<A>
    data class Linked<A>(val link: String, val doc: Doc<A>) : Doc<A>
    data object Empty : Doc<Nothing>
}
operator fun <A> Doc<A>.plus(other: Doc<A>): Doc<A> = when {
    other === Doc.Empty -> this
    this === Doc.Empty -> other
    else -> Doc.Concat(this, other)
}
