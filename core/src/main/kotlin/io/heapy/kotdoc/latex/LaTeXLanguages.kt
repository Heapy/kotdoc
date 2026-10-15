// Text.Pandoc.Readers.LaTeX.Lang, Copyright (C) 2018-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.translations.setTranslations

class LaTeXLanguages(val kernel: LaTeXKernel = LaTeXKernel()) {
    private fun <A> pure(value: A): LP<A> = ReaderTokenParsec.pure(value)
    private fun <A> defer(parser: () -> LP<A>): LP<A> = ReaderTokenParsec.defer(parser)
    private fun <A> effect(action: () -> A): LP<A> = ReaderTokenParsec.lift(TokenParsec.lift(action))
    private fun <A> withQuote(context: QuoteContext, parser: LP<A>): LP<A> = ReaderTokenParsec { env ->
        LaTeXStateAccess.tokenQuotes().withQuoteContext(context,parser.runReader(env))
    }
    private fun enquote(tok: LP<Inlines>,starred: Boolean,language: String?): LP<Inlines> = kernel.skipopts().then(LaTeXParsing.state()).flatMap { st ->
        val lang=language?.let(::babelLangToBCP47)
        val single=starred || st.user.quoteContext==QuoteContext.InDoubleQuote
        withQuote(if(single)QuoteContext.InSingleQuote else QuoteContext.InDoubleQuote,tok).map { text ->
            val content=if(lang==null)text else Builder.spanWith(Attr(keyValues=listOf("lang" to lang.renderLang())),text)
            if(single)Builder.singleQuoted(content) else Builder.doubleQuoted(content)
        }
    }
    fun enquoteCommands(tok: LP<Inlines>): Map<String,LP<Inlines>> {
        val result=sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder)
        for(starred in listOf(false,true)) {
            val suffix=if(starred)"*" else ""
            result["enquote"+suffix]=defer { enquote(tok,starred,null) }
            for(name in listOf("foreignquote","hyphenquote"))result[name+suffix]=defer {
                kernel.braced().flatMap { enquote(tok,starred,LaTeXTokens.untokenize(it)) }
            }
        }
        return java.util.Collections.unmodifiableMap(result)
    }
    private fun foreignlanguage(tok: LP<Inlines>): LP<Inlines> = kernel.braced().flatMap { tokens ->
        val lang=babelLangToBCP47(LaTeXTokens.untokenize(tokens))
        tok.map { if(lang==null)it else Builder.spanWith(Attr(keyValues=listOf("lang" to lang.renderLang())),it) }
    }
    private fun rawOptions(): LP<String> = kernel.rawopt().map { it.filter { c -> c!='[' && c!=']' } }.option("")
    private fun inlineLanguage(tok: LP<Inlines>,lang: (String)->Lang): LP<Inlines> = rawOptions().flatMap { options ->
        val tag=lang(options).renderLang()
        tok.map { InlineShared.extractSpaces({ content -> Builder.spanWith(Attr(keyValues=listOf("lang" to tag)),content) },it) }
    }
    fun inlineLanguageCommands(tok: LP<Inlines>): Map<String,LP<Inlines>> {
        val result=sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder)
        result["foreignlanguage"]=defer { foreignlanguage(tok) }
        for((name,lang) in polyglossiaLangToBCP47)result["text"+name]=defer { inlineLanguage(tok,lang) }
        return java.util.Collections.unmodifiableMap(result)
    }
    fun setDefaultLanguage(): LP<Blocks> = rawOptions().flatMap { options -> kernel.braced().flatMap { tokens ->
        val lang=polyglossiaLangToBCP47[LaTeXTokens.untokenize(tokens)]?.invoke(options)
        if(lang==null)pure(Blocks()) else effect { kernel.context.setTranslations(lang) }
            .then(LaTeXParsing.update { it.setMeta("lang",Builder.str(lang.renderLang()).toMetaValue()) }).map { Blocks() }
    } }

    companion object {
        private fun options(value: String) = value.filter { it!=' ' }
        val polyglossiaLangToBCP47: Map<String,(String)->Lang> = run {
            val result=sortedMapOf<String,(String)->Lang>(UnicodeTextOrder)
            result["afrikaans"]={ Lang("af") }
            result["amharic"]={ Lang("am") }
            result["assamese"]={ Lang("as") }
            result["asturian"]={ Lang("ast") }
            result["bulgarian"]={ Lang("bg") }
            result["bengali"]={ Lang("bn") }
            result["tibetan"]={ Lang("bo") }
            result["breton"]={ Lang("br") }
            result["catalan"]={ Lang("ca") }
            result["welsh"]={ Lang("cy") }
            result["czech"]={ Lang("cs") }
            result["coptic"]={ Lang("cop") }
            result["danish"]={ Lang("da") }
            result["divehi"]={ Lang("dv") }
            result["esperanto"]={ Lang("eo") }
            result["spanish"]={ Lang("es") }
            result["estonian"]={ Lang("et") }
            result["basque"]={ Lang("eu") }
            result["farsi"]={ Lang("fa") }
            result["finnish"]={ Lang("fi") }
            result["french"]={ Lang("fr") }
            result["friulan"]={ Lang("fur") }
            result["irish"]={ Lang("ga") }
            result["scottish"]={ Lang("gd") }
            result["ethiopic"]={ Lang("gez") }
            result["galician"]={ Lang("gl") }
            result["hebrew"]={ Lang("he") }
            result["hindi"]={ Lang("hi") }
            result["croatian"]={ Lang("hr") }
            result["magyar"]={ Lang("hu") }
            result["armenian"]={ Lang("hy") }
            result["gujarati"]={ Lang("gu") }
            result["interlingua"]={ Lang("ia") }
            result["indonesian"]={ Lang("id") }
            result["icelandic"]={ Lang("is") }
            result["italian"]={ Lang("it") }
            result["japanese"]={ Lang("ja") }
            result["khmer"]={ Lang("km") }
            result["kurmanji"]={ Lang("kmr") }
            result["kannada"]={ Lang("kn") }
            result["korean"]={ Lang("ko") }
            result["lao"]={ Lang("lo") }
            result["lithuanian"]={ Lang("lt") }
            result["latvian"]={ Lang("lv") }
            result["malayalam"]={ Lang("ml") }
            result["mongolian"]={ Lang("mn") }
            result["marathi"]={ Lang("mr") }
            result["dutch"]={ Lang("nl") }
            result["nynorsk"]={ Lang("nn") }
            result["norsk"]={ Lang("no") }
            result["nko"]={ Lang("nqo") }
            result["occitan"]={ Lang("oc") }
            result["oriya"]={ Lang("or") }
            result["punjabi"]={ Lang("pa") }
            result["polish"]={ Lang("pl") }
            result["piedmontese"]={ Lang("pms") }
            result["portuguese"]={ Lang("pt") }
            result["romansh"]={ Lang("rm") }
            result["romanian"]={ Lang("ro") }
            result["russian"]={ Lang("ru") }
            result["sanskrit"]={ Lang("sa") }
            result["samin"]={ Lang("se") }
            result["slovak"]={ Lang("sk") }
            result["albanian"]={ Lang("sq") }
            result["serbian"]={ Lang("sr") }
            result["swedish"]={ Lang("sv") }
            result["syriac"]={ Lang("syr") }
            result["tamil"]={ Lang("ta") }
            result["telugu"]={ Lang("te") }
            result["thai"]={ Lang("th") }
            result["turkmen"]={ Lang("tk") }
            result["turkish"]={ Lang("tr") }
            result["ukrainian"]={ Lang("uk") }
            result["urdu"]={ Lang("ur") }
            result["vietnamese"]={ Lang("vi") }

            result["arabic"]={ value -> Lang("ar",region=when(options(value)) {
                "locale=algeria"->"DZ";"locale=mashriq"->"SY";"locale=libya"->"LY";"locale=morocco"->"MA"
                "locale=mauritania"->"MR";"locale=tunisia"->"TN";else->null
            }) }
            result["german"]={ value -> when(options(value)) {
                "spelling=old"->Lang("de",region="DE",variants=listOf("1901"))
                "variant=austrian,spelling=old"->Lang("de",region="AT",variants=listOf("1901"))
                "variant=austrian"->Lang("de",region="AT")
                "variant=swiss,spelling=old"->Lang("de",region="CH",variants=listOf("1901"))
                "variant=swiss"->Lang("de",region="CH");else->Lang("de")
            } }
            result["lsorbian"]={ Lang("dsb") }
            result["greek"]={ value -> when(options(value)) {
                "variant=poly"->Lang("el",region="polyton");"variant=ancient"->Lang("grc");else->Lang("el")
            } }
            result["english"]={ value -> Lang("en",region=when(options(value)) {
                "variant=australian"->"AU";"variant=canadian"->"CA";"variant=british"->"GB"
                "variant=newzealand"->"NZ";"variant=american"->"US";else->null
            }) }
            result["usorbian"]={ Lang("hsb") }
            result["latin"]={ value -> Lang("la",variants=if(options(value)=="variant=classic")listOf("x-classic") else emptyList()) }
            result["slovenian"]={ Lang("sl") };result["serbianc"]={ Lang("sr",script="Cyrl") }
            result["pinyin"]={ Lang("zh",script="Latn",variants=listOf("pinyin")) }
            java.util.Collections.unmodifiableMap(result)
        }
        fun babelLangToBCP47(name: String): Lang? = when(name) {
            "austrian"->Lang("de",region="AT",variants=listOf("1901"));"naustrian"->Lang("de",region="AT")
            "swissgerman"->Lang("de",region="CH",variants=listOf("1901"));"nswissgerman"->Lang("de",region="CH")
            "german"->Lang("de",region="DE",variants=listOf("1901"));"ngerman"->Lang("de",region="DE")
            "lowersorbian"->Lang("dsb");"uppersorbian"->Lang("hsb")
            "polytonicgreek","polutonikogreek"->Lang("el",variants=listOf("polyton"));"slovene"->Lang("sl")
            "australian"->Lang("en",region="AU");"canadian"->Lang("en",region="CA");"british"->Lang("en",region="GB")
            "newzealand"->Lang("en",region="NZ");"american"->Lang("en",region="US");"classiclatin"->Lang("la",variants=listOf("x-classic"))
            else->polyglossiaLangToBCP47[name]?.invoke("")
        }
    }
}
