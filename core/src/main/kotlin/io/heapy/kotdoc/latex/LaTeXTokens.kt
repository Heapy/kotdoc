// Text.Pandoc.Readers.LaTeX.Parsing, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos
import java.math.BigInteger

/** Original token stream flag is reset whenever a token is removed. */
class TokStream(val expanded: Boolean, val tokens: TokenList) : TokenStream<Tok> {
    constructor(expanded: Boolean, tokens: List<Tok>) : this(expanded, tokenList(tokens))
    class Step(val token: Deferred<Tok>, val tail: TokStream)
    fun unconsLazy(): Step? = when (val ts = tokens.value()) {
        DeferredChain.Nil -> null
        is DeferredChain.Cons -> Step(ts.head, TokStream(false, ts.tail))
    }
    override fun uncons(): TokenStep<Tok>? = unconsLazy()?.let { TokenStep(it.token.value(), it.tail) }
    operator fun plus(other: TokStream): TokStream {
        fun append(xs: TokenList): TokenList = xs.flatMapValue { chain -> when (chain) {
            DeferredChain.Nil -> other.tokens
            is DeferredChain.Cons -> Deferred.now(DeferredChain.Cons(chain.head, Deferred.defer { append(chain.tail) }))
        } }
        return TokStream(if (tokens.value() === DeferredChain.Nil) other.expanded else expanded, append(tokens))
    }
    companion object { val empty get() = TokStream(false, emptyList()) }
}

object LaTeXTokens {
    private fun space(c: Int) = c == 32 || c == 9
    private fun letter(c: Int) = ParserCharacters.isAlpha(c)
    private fun lowerHex(c: Int) = c in 48..57 || c in 97..102
    private data class Mode(val atLetter: Boolean = false, val explSyntax: Boolean = false) {
        fun isLetter(c: Int) = when (c) { 64 -> atLetter; 58,95 -> explSyntax; else -> letter(c) }
        fun command(name: String) = when (name) {
            "makeatletter" -> copy(atLetter = true)
            "makeatother" -> copy(atLetter = false)
            "ExplSyntaxOn" -> copy(explSyntax = true)
            "ExplSyntaxOff" -> copy(explSyntax = false)
            else -> this
        }
    }
    fun tokenize(position: SourcePos, text: String): TokenList = tokenize(Deferred.now(position), Deferred.now(text))
    fun tokenize(position: Deferred<SourcePos>, text: Deferred<String>): TokenList {
        val normalized = text.mapValue(TextChars::fromString)
        fun scan(pos: Deferred<SourcePos>, input: String, at: Int, mode: Mode): TokenList = Deferred.later {
            if (at == input.length) return@later DeferredChain.Nil
            fun cp(i: Int) = input.codePointAt(i)
            fun next(i: Int) = i + Character.charCount(cp(i))
            fun span(start: Int, predicate: (Int) -> Boolean): Int {
                var end = start
                while (end < input.length && predicate(cp(end))) end = next(end)
                return end
            }
            fun column(n: Long) = pos.mapValue { it.copy(column = it.column + n) }
            fun width(end: Int) = input.codePointCount(at, end).toLong()
            fun token(type: TokType, end: Int, rest: Int = end, nextMode: Mode = mode,
                      nextPos: Deferred<SourcePos> = column(width(end))): DeferredChain<Tok> = DeferredChain.Cons(
                Deferred.now(Tok(pos, Deferred.now(type), Deferred.later { input.substring(at, end) })),
                Deferred.defer { scan(nextPos, input, rest, nextMode) })
            val c = cp(at); val after = next(at)
            when {
                c == 10 -> token(TokType.Newline, after, nextPos = pos.mapValue { it.copy(line = it.line + 1, column = 1) })
                space(c) -> token(TokType.Spaces, span(at, ::space))
                ParserCharacters.isAlphaNum(c) -> token(TokType.Word, span(at, ParserCharacters::isAlphaNum))
                c == 37 -> token(TokType.Comment, span(after) { it != 10 })
                c == 92 -> when {
                    after == input.length -> token(TokType.CtrlSeq(" "), after)
                    mode.isLetter(cp(after)) -> {
                        val wordEnd = span(after, mode::isLetter)
                        val end = span(wordEnd, ::space)
                        val name = input.substring(after, wordEnd)
                        token(TokType.CtrlSeq(name), end, nextMode = mode.command(name))
                    }
                    space(cp(after)) || cp(after) == 10 -> {
                        val r1 = span(after, ::space)
                        val r2 = if (r1 < input.length && cp(r1) == 10) next(r1) else r1
                        val r3 = if (r2 != r1) span(r2, ::space) else r1
                        // Preserve the original position quirk: escaped newlines
                        // increase only the column, including the second-newline fallback.
                        if (r3 < input.length && cp(r3) == 10)
                            token(TokType.CtrlSeq(" "), r1, r1, nextPos = column(width(r3)))
                        else token(TokType.CtrlSeq(" "), r3)
                    }
                    else -> token(TokType.CtrlSeq(String(Character.toChars(cp(after)))), next(after))
                }
                c == 35 -> {
                    val doubled = after < input.length && cp(after) == 35
                    val digits = if (doubled) next(after) else after
                    val end = span(digits) { it in 48..57 }
                    if (end > digits) {
                        // Read Int accepts arbitrarily large decimal syntax and
                        // fromInteger wraps at the original 64-bit Int boundary.
                        val number = BigInteger(input.substring(digits, end)).toLong()
                        token(if (doubled) TokType.DeferredArg(number) else TokType.Arg(number), end)
                    } else if (!doubled) token(TokType.Symbol, after) else {
                        DeferredChain.Cons(Deferred.now(Tok(pos, Deferred.now(TokType.Symbol), Deferred.now("#"))),
                            Deferred.now(DeferredChain.Cons(Deferred.now(Tok(column(1), Deferred.now(TokType.Symbol), Deferred.now("#"))),
                                Deferred.defer { scan(column(2), input, digits, mode) })))
                    }
                }
                c == 94 && after < input.length && cp(after) == 94 -> {
                    val rest = next(after)
                    when {
                        rest < input.length && lowerHex(cp(rest)) && next(rest) < input.length && lowerHex(cp(next(rest))) ->
                            token(TokType.Esc2, next(next(rest)))
                        rest < input.length && cp(rest) < 128 -> token(TokType.Esc1, next(rest))
                        else -> {
                            // Both carets are emitted before scanning the tail.
                            val tailPos = column(2)
                            DeferredChain.Cons(Deferred.now(Tok(pos, Deferred.now(TokType.Symbol), Deferred.now("^"))),
                                Deferred.now(DeferredChain.Cons(Deferred.now(Tok(column(1), Deferred.now(TokType.Symbol), Deferred.now("^"))),
                                    Deferred.defer { scan(tailPos, input, rest, mode) })))
                        }
                    }
                }
                else -> token(TokType.Symbol, after)
            }
        }
        return normalized.flatMapValue { scan(position, it, 0, Mode()) }
    }
    fun tokenizeSources(sources: Sources): TokenList {
        fun loop(chunks: Deferred<DeferredChain<SourceChunk>>, current: TokenList): TokenList = Deferred.later {
            var remaining = chunks
            var tokens = current.value()
            while (tokens === DeferredChain.Nil) {
                when (val next = remaining.value()) {
                    DeferredChain.Nil -> return@later DeferredChain.Nil
                    is DeferredChain.Cons -> {
                        val chunk = next.head.value()
                        tokens = tokenize(chunk.deferredPosition, Deferred.later { chunk.text }).value()
                        remaining = next.tail
                    }
                }
            }
            tokens as DeferredChain.Cons
            DeferredChain.Cons(tokens.head, Deferred.defer { loop(remaining, tokens.tail) })
        }
        return loop(sources.unSources, Deferred.now(DeferredChain.Nil))
    }
    /** Uses the current parser position for the first chunk, without changing input. */
    fun <U> getInputTokens(): Parsec<U, TokenList> = Parsec.state<U>().map { state ->
        val sources = state.input.stream as Sources
        val positioned = sources.unSources.mapValue { chain -> when (chain) {
            DeferredChain.Nil -> DeferredChain.Nil
            is DeferredChain.Cons -> DeferredChain.Cons(chain.head.mapValue { it.withPosition(state.position) }, chain.tail)
        } }
        tokenizeSources(Sources(positioned))
    }
    fun untoken(token: Tok): String = token.text
    fun setpos(position: io.heapy.kotdoc.tex.SourcePos, token: Tok): Tok =
        Tok(Deferred.now(position), token.deferredType, token.deferredText)
    fun isWordTok(token: Tok) = token.type === TokType.Word
    fun isArgTok(token: Tok) = token.type is TokType.Arg
    fun isNewlineTok(token: Tok) = token.type === TokType.Newline
    fun tokTypeIn(types: List<TokType>, token: Tok) = token.type in types
    fun untokenize(tokens: List<Tok>): String = untokenize(tokenList(tokens))
    fun untokenize(tokens: TokenList): String {
        fun startsWithLetter(input: TokenList): Boolean {
            var rest = input.value()
            while (rest is DeferredChain.Cons) {
                val t = rest.head.value().text
                if (t.isNotEmpty()) return letter(t.codePointAt(0))
                rest = rest.tail.value()
            }
            return false
        }
        fun segments(input: TokenList): Deferred<DeferredChain<String>> = input.mapValue { chain -> when (chain) {
            DeferredChain.Nil -> DeferredChain.Nil
            is DeferredChain.Cons -> {
                val tok = chain.head.value()
                val separate = tok.type is TokType.CtrlSeq && tok.text.let {
                    it.isNotEmpty() && letter(it.codePointBefore(it.length)) && startsWithLetter(chain.tail)
                }
                val tail = Deferred.defer { segments(chain.tail) }
                DeferredChain.Cons(tok.deferredText, if (separate) Deferred.now(DeferredChain.Cons(Deferred.now(" "), tail)) else tail)
            }
        } }
        val first = segments(tokens).value()
        if (first === DeferredChain.Nil) return ""
        first as DeferredChain.Cons
        // Text.concat inspects []/[t] before forcing the first Text value.
        if (first.tail.value() === DeferredChain.Nil) return first.head.value()
        return buildString {
            var current: DeferredChain<String> = first
            while (current is DeferredChain.Cons) { append(current.head.value()); current = current.tail.value() }
        }
    }
}
