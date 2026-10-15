// Text.Pandoc.Readers.LaTeX.Parsing, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later. Parser wrappers use the existing ParsecT/ReaderT interpreter.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.InlineShared
import io.heapy.kotdoc.ast.MetaValue
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*

/** State-scoping helpers independent of the command/macro dispatch grammar. */
object LaTeXParsing {
    private fun <A> lift(p: TokenParsec<Tok, LaTeXState, A>): LP<A> = ReaderTokenParsec.lift(p)
    fun state(): LP<TokenParseState<Tok, LaTeXState>> = lift(TokenParsec.state())
    fun modify(f: (TokenParseState<Tok, LaTeXState>) -> TokenParseState<Tok, LaTeXState>): LP<Unit> = lift(TokenParsec.modify(f))
    fun update(f: (LaTeXState) -> LaTeXState): LP<Unit> = modify { it.copy(user = f(it.user)) }
    fun askEnv(): LP<LaTeXEnv> = ReaderTokenParsec.ask()
    fun <A> withVerbatimMode(parser: LP<A>): LP<A> = state().flatMap { initial ->
        if (initial.user.verbatimMode) parser
        else update { it.copy(verbatimMode = true) }.then(parser).flatMap { result ->
            update { it.copy(verbatimMode = false) }.map { result }
        }
    }
    fun <A> disablingWithRaw(parser: LP<A>): LP<A> = state().flatMap { initial ->
        update { it.copy(enableWithRaw = false) }.then(parser).flatMap { result ->
            update { it.copy(enableWithRaw = initial.user.enableWithRaw) }.map { result }
        }
    }
    fun <A> parseFromToks(parser: LP<A>, tokens: TokenList): LP<A> = state().flatMap { initial ->
        val first = tokens.value()
        val position = if (first is DeferredChain.Cons) first.head.value().position else initial.position
        modify { it.copy(input = TokStream(false, tokens), position = position,
            user = it.user.copy(rawTokens = tokenList(emptyList()), rawTokenCount = 0, rawScopes = 0)) }
            .then(parser).flatMap { result ->
                modify { it.copy(input = initial.input, position = initial.position,
                    user = it.user.copy(rawTokens = initial.user.rawTokens, rawTokenCount = initial.user.rawTokenCount,
                        rawScopes = initial.user.rawScopes)) }.map { result }
            }
    }
    fun <A> withRaw(parser: LP<A>): LP<Pair<A, TokenList>> = state().flatMap { initial ->
        update { it.copy(rawScopes = it.rawScopes + 1) }.then(parser).flatMap { result -> state().flatMap { final ->
            val captured = Deferred.later {
                var count = final.user.rawTokenCount - initial.user.rawTokenCount
                var tokens = final.user.rawTokens.value()
                val heads = ArrayList<Deferred<Tok>>()
                while (count > 0 && tokens is DeferredChain.Cons) {
                    heads.add(tokens.head); tokens = tokens.tail.value(); count--
                }
                var out: Deferred<DeferredChain<Tok>> = Deferred.now(DeferredChain.Nil)
                for (head in heads) out = Deferred.now(DeferredChain.Cons(head, out))
                out.value()
            }
            update { if (it.rawScopes <= 1) it.copy(rawScopes = 0, rawTokens = tokenList(emptyList()), rawTokenCount = 0)
                else it.copy(rawScopes = it.rawScopes - 1) }.map { result to captured }
        } }
    }
    fun getNextNumber(current: (LaTeXState) -> DottedNum): LP<DottedNum> = state().map { parser ->
        val st = parser.user
        val chapter = st.lastHeaderNum.numbers.firstOrNull().takeIf { st.hasChapters }
        val numbers = current(st).numbers
        DottedNum(when (numbers.size) {
            2 -> if (chapter == null) listOf(1) else if (chapter == numbers[0]) listOf(chapter, numbers[1] + 1) else listOf(chapter, 1)
            1 -> if (chapter == null) listOf(numbers[0] + 1) else listOf(chapter, 1)
            else -> if (chapter == null) listOf(1) else listOf(chapter, 1)
        })
    }
    fun resetCaption(): LP<Unit> = update { it.copy(caption = null, lastLabel = null) }
    fun addMeta(field: String, value: MetaValue): LP<Unit> = update { it.copy(meta = InlineShared.addMetaField(field, value, it.meta)) }
}
