// Text.Pandoc.TeX, Pandoc 3.12.1.
// Copyright (C) 2017-2024 John MacFarlane; GPL-2.0-or-later.
// Text.concat demand rules: (c) 2008-2012 Bryan O'Sullivan, Duncan Coutts,
// Tom Harper; (c) 2021 Andrew Lelechenko. BSD-2-Clause, licenses/text-BSD-2-Clause.txt.
package io.heapy.kotdoc.tex

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.*

typealias SourcePos = io.heapy.kotdoc.parsing.SourcePos
typealias TokenList = Deferred<DeferredChain<Tok>>
typealias ArgumentList = Deferred<DeferredChain<ArgSpec>>
typealias ProcessorList = Deferred<DeferredChain<TokenList>>
typealias TokenDefault = Deferred<TeXMaybe<DeferredChain<Tok>>>

sealed interface TeXMaybe<out A> {
    data object Nothing : TeXMaybe<kotlin.Nothing>
    class Just<A>(val value: Deferred<A>) : TeXMaybe<A>
}
fun tokenList(tokens: List<Tok>): TokenList = DeferredChain.of(tokens)
fun tokenDefault(tokens: List<Tok>?): TokenDefault = Deferred.now(
    if (tokens == null) TeXMaybe.Nothing else TeXMaybe.Just(tokenList(tokens)))

/** Constructor ordinals and field order are those of the original derived Eq/Ord. */
sealed class TokType(val ordinal: Int) : Comparable<TokType> {
    class CtrlSeq(val deferredText: Deferred<String>) : TokType(0) {
        constructor(text: String) : this(Deferred.now(TextChars.fromString(text)))
        val text get() = TextChars.fromString(deferredText.value())
    }
    data object Spaces : TokType(1)
    data object Newline : TokType(2)
    data object Symbol : TokType(3)
    data object Word : TokType(4)
    data object Comment : TokType(5)
    data object Esc1 : TokType(6)
    data object Esc2 : TokType(7)
    class Arg(val deferredNumber: Deferred<Long>) : TokType(8) {
        constructor(number: Long) : this(Deferred.now(number))
        val number get() = deferredNumber.value()
    }
    class DeferredArg(val deferredNumber: Deferred<Long>) : TokType(9) {
        constructor(number: Long) : this(Deferred.now(number))
        val number get() = deferredNumber.value()
    }
    final override fun compareTo(other: TokType): Int {
        val tag = ordinal.compareTo(other.ordinal)
        if (tag != 0) return tag
        return when (this) {
            is CtrlSeq -> UnicodeTextOrder.compare(text, (other as CtrlSeq).text)
            is Arg -> number.compareTo((other as Arg).number)
            is DeferredArg -> number.compareTo((other as DeferredArg).number)
            else -> 0
        }
    }
    final override fun equals(other: Any?) = other is TokType && compareTo(other) == 0
    final override fun hashCode() = 31 * ordinal + when (this) {
        is CtrlSeq -> text.hashCode()
        is Arg -> number.hashCode()
        is DeferredArg -> number.hashCode()
        else -> 0
    }
    fun show(precedence: Int = 0) = TeXShow.show(this, precedence)
    final override fun toString() = show()
}

class Tok(val deferredPosition: Deferred<SourcePos>, val deferredType: Deferred<TokType>, text: Deferred<String>) : Comparable<Tok> {
    val deferredText: Deferred<String> = text.mapValue(TextChars::fromString)
    constructor(position: SourcePos, type: TokType, text: String) : this(Deferred.now(position), Deferred.now(type), Deferred.now(text))
    val position get() = deferredPosition.value()
    val type get() = deferredType.value()
    val text get() = deferredText.value()
    override fun compareTo(other: Tok): Int = position.compareTo(other.position).takeIf { it != 0 }
        ?: type.compareTo(other.type).takeIf { it != 0 } ?: UnicodeTextOrder.compare(text, other.text)
    override fun equals(other: Any?) = other is Tok && position == other.position && type == other.type && text == other.text
    override fun hashCode() = 31 * (31 * position.hashCode() + type.hashCode()) + text.hashCode()
    fun show(precedence: Int = 0) = TeXShow.show(this, precedence)
    override fun toString() = show()
}

enum class ExpansionPoint { ExpandWhenDefined, ExpandWhenUsed;
    fun show(precedence: Int = 0) = TeXShow.show(this, precedence)
}
enum class MacroScope { GlobalScope, GroupScope;
    fun show(precedence: Int = 0) = TeXShow.show(this, precedence)
}

/** Macro and ArgSpec derive Show only; their Kotlin identity is not a derived Eq. */
class Macro(
    val deferredScope: Deferred<MacroScope>, val deferredExpansion: Deferred<ExpansionPoint>,
    val arguments: ArgumentList, val default: TokenDefault, val replacement: TokenList,
) {
    constructor(scope: MacroScope, expansion: ExpansionPoint, arguments: List<ArgSpec>, default: List<Tok>?, replacement: List<Tok>) :
        this(Deferred.now(scope), Deferred.now(expansion), DeferredChain.of(arguments), tokenDefault(default), tokenList(replacement))
    val scope get() = deferredScope.value()
    val expansion get() = deferredExpansion.value()
    fun show(precedence: Int = 0) = TeXShow.show(this, precedence)
    override fun toString() = show()
}
class Embellishment(val deferredToken: Deferred<Tok>, val default: TokenDefault) {
    constructor(token: Tok, default: List<Tok>?) : this(Deferred.now(token), tokenDefault(default))
    val token get() = deferredToken.value()
}
sealed class ArgSpec {
    class ArgNum(val deferredNumber: Deferred<Long>) : ArgSpec() {
        constructor(number: Long) : this(Deferred.now(number))
        val number get() = deferredNumber.value()
    }
    class Pattern(val tokens: TokenList) : ArgSpec() { constructor(tokens: List<Tok>) : this(tokenList(tokens)) }
    class BoolArg(val deferredSkipSpace: Deferred<Boolean>, val deferredToken: Deferred<Tok>) : ArgSpec() {
        constructor(skipSpace: Boolean, token: Tok) : this(Deferred.now(skipSpace), Deferred.now(token))
    }
    class DelimArg(val deferredSkipSpace: Deferred<Boolean>, val deferredOpen: Deferred<Tok>, val deferredClose: Deferred<Tok>, val default: TokenDefault) : ArgSpec() {
        constructor(skipSpace: Boolean, open: Tok, close: Tok, default: List<Tok>?) :
            this(Deferred.now(skipSpace), Deferred.now(open), Deferred.now(close), tokenDefault(default))
    }
    object VerbArg : ArgSpec()
    class EmbellishArg(val embellishments: Deferred<DeferredChain<Embellishment>>) : ArgSpec() {
        constructor(embellishments: List<Embellishment>) : this(DeferredChain.of(embellishments))
    }
    class ProcessedArg(val processors: ProcessorList, val deferredArgument: Deferred<ArgSpec>) : ArgSpec() {
        constructor(processors: List<List<Tok>>, argument: ArgSpec) : this(DeferredChain.of(processors.map(::tokenList)), Deferred.now(argument))
    }
    class BodyArg(val deferredTrim: Deferred<Boolean>, val deferredNumber: Deferred<Long>) : ArgSpec() {
        constructor(trim: Boolean, number: Long) : this(Deferred.now(trim), Deferred.now(number))
    }
    class VerbBodyArg(val deferredTrim: Deferred<Boolean>, val deferredNumber: Deferred<Long>) : ArgSpec() {
        constructor(trim: Boolean, number: Long) : this(Deferred.now(trim), Deferred.now(number))
    }
    fun show(precedence: Int = 0) = TeXShow.show(this, precedence)
    final override fun toString() = show()
}

/** Adjacent source-name groups retain first positions, exact Text and lazy tails. */
object TeXSourceConversions {
    val tokens = ToSources<TokenList>(::toSources)
    fun toSources(tokens: List<Tok>): Sources = toSources(tokenList(tokens))
    fun toSources(tokens: TokenList): Sources {
        class Split(val prefix: TokenList, val rest: TokenList)
        fun groups(input: TokenList): Deferred<DeferredChain<SourceChunk>> = input.mapValue { chain ->
            when (chain) {
                DeferredChain.Nil -> DeferredChain.Nil
                is DeferredChain.Cons -> {
                    val first = chain.head
                    fun split(input: TokenList): Deferred<Split> = input.mapValue { rest ->
                        when (rest) {
                            DeferredChain.Nil -> Split(Deferred.now(DeferredChain.Nil), Deferred.now(DeferredChain.Nil))
                            is DeferredChain.Cons -> {
                                if (first.value().position.name != rest.head.value().position.name)
                                    Split(Deferred.now(DeferredChain.Nil), input)
                                else {
                                    val next = split(rest.tail)
                                    Split(Deferred.now(DeferredChain.Cons(rest.head, next.flatMapValue { it.prefix })), next.flatMapValue { it.rest })
                                }
                            }
                        }
                    }
                    val split = split(chain.tail)
                    val grouped = Deferred.now(DeferredChain.Cons(first, split.flatMapValue { it.prefix }))
                    val text = Deferred.later {
                        // Text.mconcat = Text.concat inspects []/[t] before
                        // demanding Text values. In particular, grouping the
                        // second token precedes reading the first token's Text.
                        val head = grouped.value()
                        if (head.tail.value() === DeferredChain.Nil) return@later head.head.value().text
                        buildString {
                            var current: DeferredChain<Tok> = grouped.value()
                            while (current is DeferredChain.Cons) { append(current.head.value().text); current = current.tail.value() }
                        }
                    }
                    val chunk = first.mapValue { SourceChunk.deferred(it.deferredPosition, text) }
                    DeferredChain.Cons(chunk, Deferred.defer { groups(split.flatMapValue { it.rest }) })
                }
            }
        }
        return Sources(Deferred.defer { groups(tokens) })
    }
}
