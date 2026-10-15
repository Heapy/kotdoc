// Text.Pandoc.Readers.LaTeX.Parsing, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.tex.SourcePos

/** Bridges token parsing back into a Sources parser without consuming its lookahead. */
class LaTeXParsingBridge(val kernel: LaTeXKernel = LaTeXKernel()) {
    fun <U> applyMacros(text: String,macros: MacrosAccess<U>,options: ReaderOptionsAccess<U>): Parsec<U,String> =
        ReaderOptionGuards.guardDisabled(options,Extension.read("latex_macros")).map { TextChars.fromString(text) } or
            Parsec.state<U>().flatMap { outer ->
                val state=LaTeXState(options=options.extractReaderOptions(outer.user),macros=MacroScopes(macros.extractMacros(outer.user)))
                val p=kernel.anyTok().many().map { LaTeXTokens.untokenize(it) }
                Parsec.lift<U,ParserReply<TokenParseState<Tok,LaTeXState>,String>> {
                    p.run(TokenParseState(TokStream(false,LaTeXTokens.tokenize(SourcePos("math"),text)),SourcePos("math"),state),LaTeXEnv())
                }.flatMap { if(it.success)Parsec.pure(it.value!!) else Parsec.fail(it.error.showText()) }
            }

    fun <U,A> rawLaTeXParser(environment: LaTeXEnv,tokens: TokenList,preparser: LP<Unit>,valueParser: LP<A>,
        macros: MacrosAccess<U>,options: ReaderOptionsAccess<U>): Parsec<U,Pair<A,String>> = Parsec.state<U>().flatMap { outer ->
        val state=LaTeXState(options=options.extractReaderOptions(outer.user))
        val withMacros=state.copy(macros=MacroScopes(macros.extractMacros(outer.user)))
        val first=tokens.value() as? DeferredChain.Cons
        val setStart=if(first==null)ReaderTokenParsec.pure<LaTeXEnv,Tok,LaTeXState,Unit>(Unit)
            else LaTeXParsing.modify { it.copy(position=first.head.value().position) }
        val prepare=LaTeXParsing.withRaw(setStart.then(preparser).then(LaTeXParsing.state().map { it.position }))
        Parsec.lift<U,ParserReply<TokenParseState<Tok,LaTeXState>,Pair<SourcePos,TokenList>>> {
            prepare.run(TokenParseState(TokStream(false,tokens),SourcePos("chunk"),state),environment)
        }.flatMap { prepared ->
            if(!prepared.success)Parsec.zero() else {
                val (end,rawTokens)=prepared.value!!
                val parseValue=LaTeXParsing.withRaw(valueParser).flatMap { result -> LaTeXParsing.state().map { result to it.user } }
                Parsec.lift<U,ParserReply<TokenParseState<Tok,LaTeXState>,Pair<Pair<A,TokenList>,LaTeXState>>> {
                    parseValue.run(TokenParseState(TokStream(false,rawTokens),SourcePos("chunk"),withMacros),environment)
                }.flatMap { parsed ->
                    if(!parsed.success)Parsec.zero() else {
                        val (result,finalState)=parsed.value!!
                        fun rawChar(): Parsec<U,Int> = Parsec.state<U>().flatMap {
                            if(it.position>=end)Parsec.zero() else SourceParsers.anyChar()
                        }
                        Parsec.modify<U> { it.copy(user=macros.updateMacros({ old -> old+finalState.macros.head },it.user)) }.then(
                            ReaderOptionGuards.guardEnabled(options,Extension.read("latex_macros")).then(rawChar().skipMany())
                                .map { LaTeXTokens.untokenize(result.second) } or
                                rawChar().many().map { TextChars.fromPoints(it.toIntArray()) }
                        ).map { text ->
                            var current=rawTokens.value();var last: Tok?=null
                            while(current is DeferredChain.Cons) {last=current.head.value();current=current.tail.value()}
                            val trailingSpace=last?.let { it.type is TokType.CtrlSeq && it.text.endsWith(' ') }==true
                            result.first to if(trailingSpace && !text.endsWith(' '))text+" " else text
                        }
                    }
                }
            }
        }
    }
}
