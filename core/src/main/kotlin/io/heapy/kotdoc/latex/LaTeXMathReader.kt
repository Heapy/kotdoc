// Text.Pandoc.Readers.LaTeX.Math, Pandoc 3.12.1.
// Copyright (C) John MacFarlane; GPL-2.0-or-later, licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*

class LaTeXMathReader(val kernel: LaTeXKernel = LaTeXKernel()) {
    private fun <A> pure(x: A): LP<A> = ReaderTokenParsec.pure(x)
    private fun <A> defer(p: ()->LP<A>): LP<A> = ReaderTokenParsec.defer(p)
    private fun <A,B> till(p: LP<A>,end: LP<B>): LP<List<A>> = ReaderTokenParsec { env -> TokenParsec.manyTill(p.runReader(env),end.runReader(env)) }
    private fun update(f: (LaTeXState)->LaTeXState) = LaTeXParsing.update(f)
    private fun cons(t: Tok,tail: TokenList): TokenList = Deferred.now(DeferredChain.Cons(Deferred.now(t),tail))
    fun <A> withMathMode(p: LP<A>): LP<A> = LaTeXParsing.state().flatMap { initial ->
        update { it.copy(mathMode=true) }.then(p).flatMap { value -> update { it.copy(mathMode=initial.user.mathMode) }.map { value } }
    }
    private fun dollarContents(depth: Long): LP<TokenList> = kernel.anyTok().flatMap { t ->
        when {
            t.type==TokType.Symbol && t.text=="$" && depth==0L -> pure(tokenList(emptyList()))
            t.type==TokType.Symbol && t.text=="\\" -> kernel.anyTok().flatMap { next -> defer { dollarContents(depth) }.map { cons(t,cons(next,it)) } }
            t.type==TokType.Symbol && t.text=="{" -> defer { dollarContents(depth+1) }.map { cons(t,it) }
            t.type==TokType.Symbol && t.text=="}" -> if(depth>0)defer { dollarContents(depth-1) }.map { cons(t,it) } else ReaderTokenParsec.zero()
            else -> defer { dollarContents(depth) }.map { cons(t,it) }
        }
    }
    fun dollarsMath(): LP<Inlines> = kernel.symbol('$').then(kernel.symbol('$').map { true }.option(false)).flatMap { display ->
        withMathMode(defer { dollarContents(0) }).map(LaTeXTokens::untokenize).attempt().flatMap { text ->
            if(display)kernel.symbol('$').map { mathDisplay(text) } else pure(mathInline(text))
        } or if(display)pure(mathInline("")) else ReaderTokenParsec.zero()
    }
    private fun mathEnv(name: String): LP<String> = withMathMode(kernel.blankline().optional().then(till(kernel.anyTok(),kernel.end(name)))
        .map { LaTeXTokens.untokenize(it).trimEnd { c -> c==' ' || c=='\r' || c=='\n' || c=='\t' } })
    val inlineEnvironments: Map<String,LP<Inlines>> by lazy {
        val result=sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder)
        fun put(name: String,inner: String?) { result[name]=mathEnv(name).map { text ->
            mathDisplay(if(inner==null)text else "\\begin{$inner}\n$text\n\\end{$inner}")
        } }
        put("displaymath",null);result["math"]=mathEnv("math").map(Builder::math)
        for(name in listOf("equation","equation*","gather","gather*","multline","multline*","eqnarray","eqnarray*","align","align*","alignat","alignat*","flalign","flalign*"))put(name,name)
        for(name in listOf("dmath","dmath*","subequations"))put(name,null)
        for(name in listOf("dgroup","dgroup*","darray","darray*"))put(name,"aligned")
        java.util.Collections.unmodifiableMap(result)
    }
    val inlineEnvironmentNames: List<String> get() = inlineEnvironments.keys.toList()
    fun inlineEnvironment(): LP<Inlines> = kernel.controlSeq("begin").then(kernel.braced()).flatMap { ts ->
        inlineEnvironments[LaTeXTokens.untokenize(ts)] ?: ReaderTokenParsec.zero()
    }.attempt()
    fun theoremstyle(): LP<Blocks> = kernel.braced().flatMap { ts ->
        val style=when(LaTeXTokens.untokenize(ts)) {"plain"->TheoremStyle.PlainStyle;"definition"->TheoremStyle.DefinitionStyle;"remark"->TheoremStyle.RemarkStyle;else->null}
        (if(style==null)pure(Unit) else update { it.copy(lastTheoremStyle=style) }).map { Blocks() }
    }
    fun newtheorem(): LP<Blocks> = kernel.symbol('*').before(kernel.sp()).map { false }.option(true).flatMap { number ->
        kernel.braced().flatMap { ts ->
            val name=LaTeXTokens.untokenize(ts)
            val opt=kernel.bracketedToks().map<String?> { LaTeXTokens.untokenize(it) }.option(null)
            kernel.sp().then(opt).flatMap { series -> kernel.sp().then(kernel.braced() or kernel.anyTok().map { tokenList(listOf(it)) }).flatMap { showName ->
                kernel.sp().then(opt).flatMap { syncTo -> update { state ->
                    val spec=TheoremSpec(showName,state.lastTheoremStyle,series,syncTo,number,DottedNum(listOf(0)))
                    state.copy(theoremMap=state.theoremMap+(name to spec))
                }.map { Blocks() } }
            } }
        }
    }
    fun theoremEnvironment(blocks: LP<Blocks>,inlines: LP<Inlines>,opt: LP<Inlines>,name: String): LP<Blocks> =
        LaTeXParsing.resetCaption().then(LaTeXParsing.state()).flatMap { state ->
            val spec=state.user.theoremMap[name] ?: return@flatMap ReaderTokenParsec.zero()
            opt.map { Builder.space+Builder.str("(")+it+Builder.str(")") }.option(Inlines()).flatMap { optTitle ->
                kernel.env(name,blocks).flatMap { content ->
                    val label=content.values.firstNotNullOfOrNull(::extractLabelFromBlock)
                    val num: LP<Inlines> = if(!spec.number)pure(Inlines()) else {
                        val series=spec.series ?: name
                        LaTeXParsing.getNextNumber { it.theoremMap[series]?.lastNum ?: DottedNum(listOf(0)) }.flatMap { n ->
                            update { current ->
                                val old=current.theoremMap[series]
                                val table=if(old==null)current.theoremMap else current.theoremMap+(series to
                                    TheoremSpec(old.name,old.style,old.series,old.syncTo,old.number,n))
                                current.copy(theoremMap=table,labels=if(label==null)current.labels else current.labels+(label to Builder.str(n.render()).values))
                            }.map { Builder.space+Builder.text(n.render()) }
                        }
                    }
                    num.flatMap { number -> LaTeXParsing.parseFromToks(inlines,spec.name).map { titleName ->
                        val bold=if(spec.style==TheoremStyle.RemarkStyle)Builder.emph(titleName+number) else Builder.strong(titleName+number)
                        val title=bold+optTitle+Builder.str(".")+Builder.space
                        var body=if(spec.style==TheoremStyle.PlainStyle)Blocks(italicizer.walkBlocks(content.values)) else content
                        if(label!=null)body=kernel.removeLabel(label,body) { visitor,value -> Blocks(visitor.walkBlocks(value.values)) }
                        Builder.divWith(Attr(label ?: "",listOf(name)),addTitle(title,body))
                    } }
                }
            }
        }
    fun proof(blocks: LP<Blocks>,opt: LP<Inlines>): LP<Blocks> = opt.option(Builder.text("Proof")).flatMap { title ->
        kernel.env("proof",blocks).map { body -> Builder.divWith(Attr(classes=listOf("proof")),addQed(addTitle(Builder.emph(title+Builder.str(".")),body))) }
    }
    companion object {
        fun mathDisplay(text: String) = Builder.displayMath(MathParsing.trimMath(text))
        fun mathInline(text: String) = Builder.math(MathParsing.trimMath(text))
        fun extractLabelFromBlock(block: Block): String? {
            if(block !is Block.Para)return null
            for(inline in block.content)if(inline is Inline.Span)inline.attr.keyValues.firstOrNull { it.first=="label" }?.let { return it.second }
            return null
        }
        fun addTitle(title: Inlines,blocks: Blocks): Blocks {
            val first=blocks.values.firstOrNull()
            return if(first is Block.Para)Blocks(listOf(Block.Para(title.values+Inline.Space+first.content))+blocks.values.drop(1))
            else Builder.para(title)+blocks
        }
        fun addQed(blocks: Blocks): Blocks {
            val sign=Builder.str("\u00a0\u25fb");val last=blocks.values.lastOrNull()
            return if(last is Block.Para)Blocks(blocks.values.dropLast(1)+Block.Para(last.content+sign.values)) else blocks+Builder.para(sign)
        }
        fun italicize(block: Block): Block = when(block) {
            is Block.Para->if(block.content.size==1 && block.content[0] is Inline.Image)block else Block.Para(listOf(Inline.Emph(block.content)))
            is Block.Plain->if(block.content.size==1 && block.content[0] is Inline.Image)block else Block.Plain(listOf(Inline.Emph(block.content)))
            else->block
        }
        private val italicizer=object : PandocVisitor() { override fun transformBlock(value: Block) = italicize(value) }
    }
}
