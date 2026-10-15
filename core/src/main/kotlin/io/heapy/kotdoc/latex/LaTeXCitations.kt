// Text.Pandoc.Readers.LaTeX.Citation, Pandoc 3.12.1.
// Copyright (C) John MacFarlane; GPL-2.0-or-later, licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*

class LaTeXCitations(val kernel: LaTeXKernel = LaTeXKernel()) {
    private fun <A> pure(x: A): LP<A> = ReaderTokenParsec.pure(x)
    private fun <A> defer(p: ()->LP<A>): LP<A> = ReaderTokenParsec.defer(p)
    private fun <A,B> till(p: LP<A>,end: LP<B>): LP<List<A>> = ReaderTokenParsec { env -> TokenParsec.manyTill(p.runReader(env),end.runReader(env)) }
    private fun concat(xs: List<Inlines>) = xs.fold(Inlines(),Inlines::plus)
    private fun <A> maybe(p: LP<A>): LP<A?> = p.map<A?> { it }.option(null)
    private fun opt(inline: LP<Inlines>): LP<Inlines> = kernel.sp().then(kernel.bracketedToks()).before(kernel.sp()).attempt().flatMap { tokens ->
        LaTeXParsing.parseFromToks(inline.many().map(::concat),tokens)
    }
    private fun citationLabel(): LP<String> = kernel.sp().then(
        (kernel.satisfyTok(LaTeXKernel::isWordTok) or kernel.symbolIn(bibtexKeyChars)).many1()
            .before(kernel.sp()).before(kernel.symbol(',').optional()).before(kernel.sp())
    ).map(LaTeXTokens::untokenize)
    private fun simpleCiteArgs(inline: LP<Inlines>): LP<List<Citation>> = defer {
        maybe(opt(inline).map { it.values }).flatMap { first -> maybe(opt(inline).map { it.values }).flatMap { second ->
            kernel.bgroup().then(till(citationLabel(),kernel.egroup())).attempt().map { keys ->
                val pre=if(first!=null && second!=null)first else emptyList()
                val suf=if(first==null)emptyList() else second ?: first
                addPrefix(pre,addSuffix(suf,keys.map { Citation(it,citationMode=CitationMode.NormalCitation) }))
            }
        } }.attempt()
    }
    fun cites(inline: LP<Inlines>,mode: CitationMode,multi: Boolean): LP<List<Citation>> = defer {
        val groups: LP<List<List<Citation>>> = if(multi) {
            val paropt=kernel.parenWrapped(inline,Inlines(),Inlines::plus).map { it.values }
            maybe(paropt).flatMap { first -> maybe(paropt).flatMap { second ->
                val pre=if(first!=null && second!=null)first else emptyList()
                val suf=second ?: first ?: emptyList()
                simpleCiteArgs(inline).many1().map { original ->
                    val all=original.toMutableList()
                    fun prenote(cs: List<Citation>): List<Citation> {
                        val prefix=if(cs.firstOrNull()?.citationPrefix?.isNotEmpty()==true && pre.isNotEmpty())pre+Inline.Space else pre
                        return addPrefix(prefix,cs)
                    }
                    val post=if(suf.isEmpty())emptyList() else listOf(Inline.Str(","),Inline.Space)+suf
                    if(all.isEmpty())listOf(emptyList()) else {
                        all[all.lastIndex]=addSuffix(post,all.last())
                        all[0]=prenote(all[0]);all
                    }
                }
            } }
        } else simpleCiteArgs(inline).map { listOf(it) }
        groups.map { groups ->
            val cs=groups.flatten()
            if(mode==CitationMode.AuthorInText)cs.firstOrNull()?.let { listOf(it.copy(citationMode=mode))+cs.drop(1) } ?: emptyList()
            else cs.map { it.copy(citationMode=mode) }
        }.attempt()
    }
    private fun citationWith(inline: LP<Inlines>,name: String,mode: CitationMode,multi: Boolean): LP<Inlines> =
        LaTeXParsing.withRaw(cites(inline,mode,multi)).map { (cs,raw) -> Builder.cite(cs,Builder.rawInline("latex","\\$name"+LaTeXTokens.untokenize(raw))) }
    private fun complexNatbibCitation(inline: LP<Inlines>,mode: CitationMode): LP<Inlines> = defer {
        val item=kernel.symbol(';').notFollowedBy { it.show() }.then(inline).many1()
        val items=item.flatMap { first -> kernel.symbol(';').then(item).many().map { listOf(first)+it } }
        // In the source, (<$>) applies mconcat to the sepBy1 result: it flattens
        // [ [Inlines] ], keeping each callback's result separate.
        LaTeXParsing.withRaw(kernel.bgroup().then(items).before(kernel.egroup()).map { it.flatten().flatMap(::handleCitationPart) }).flatMap { (cs,raw) ->
            if(cs.isEmpty())ReaderTokenParsec.zero() else pure(Builder.cite(listOf(cs[0].copy(citationMode=mode))+cs.drop(1),
                Builder.rawInline("latex","\\citetext"+LaTeXTokens.untokenize(raw))))
        }.attempt()
    }
    fun citationCommands(inline: LP<Inlines>): Map<String,LP<Inlines>> {
        val result=sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder)
        fun put(mode: CitationMode,multi: Boolean,vararg names: String) { for(name in names)result[name]=citationWith(inline,name,mode,multi) }
        put(CitationMode.NormalCitation,false,"cite","Cite","citep","citep*","citeal","citealp","citealp*","autocite","smartcite","parencite","supercite",
            "Autocite","Smartcite","Parencite","Supercite")
        put(CitationMode.SuppressAuthor,false,"citeyearpar","citeyear","autocite*","cite*","parencite*","Citeyearpar","Citeyear","Autocite*","Cite*","Parencite*")
        put(CitationMode.AuthorInText,false,"textcite","citet","citet*","citealt","citealt*","Textcite")
        put(CitationMode.AuthorInText,true,"textcites","Textcites")
        put(CitationMode.NormalCitation,true,"cites","autocites","parencites","supercites","Cites","Autocites","Parencites","Supercites")
        for(name in listOf("footcite","footcitetext","Footcite","Footcitetext","footcites","footcitetexts","Footcites","Footcitetexts")) {
            result[name]=citationWith(inline,name,CitationMode.NormalCitation,name.endsWith('s')).map(::inNote)
        }
        result["citetext"]=complexNatbibCitation(inline,CitationMode.NormalCitation)
        val tok=kernel.spaces().then(kernel.grouped(inline,Inlines(),Inlines::plus))
        result["citeauthor"]=tok.then(kernel.sp()).then(kernel.controlSeq("citetext")).attempt()
            .then(complexNatbibCitation(inline,CitationMode.AuthorInText)) or citationWith(inline,"citeauthor",CitationMode.AuthorInText,false)
        result["nocite"]=citationWith(inline,"nocite",CitationMode.NormalCitation,false).flatMap { LaTeXParsing.addMeta("nocite",it.toMetaValue()).map { Inlines() } }
        return java.util.Collections.unmodifiableMap(result)
    }
    companion object {
        private val bibtexKeyChars=".:;?!`'()/*@_+=-&[]".codePoints().toArray().toSet()
        fun addPrefix(prefix: List<Inline>,cs: List<Citation>) = cs.firstOrNull()?.let { listOf(it.copy(citationPrefix=prefix+it.citationPrefix))+cs.drop(1) } ?: emptyList()
        fun addSuffix(suffix: List<Inline>,cs: List<Citation>) = cs.lastOrNull()?.let { cs.dropLast(1)+it.copy(citationSuffix=it.citationSuffix+suffix) } ?: emptyList()
        fun handleCitationPart(inlines: Inlines): List<Citation> {
            val at=inlines.values.indexOfFirst { it is Inline.Cite }
            if(at<0)return emptyList()
            val cite=inlines.values[at] as Inline.Cite
            return addPrefix(inlines.values.take(at),addSuffix(inlines.values.drop(at+1),cite.citations))
        }
        fun inNote(inlines: Inlines) = Builder.note(Builder.para(inlines+Builder.str(".")))
    }
}
