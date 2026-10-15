// Text.Pandoc.Readers.LaTeX.Inline, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.highlighting.Highlighting
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.tex.*
import io.heapy.kotdoc.translations.Term
import io.heapy.kotdoc.translations.TranslationRuntime
import io.heapy.kotdoc.translations.translateTerm

/** The term effect shares the caller's Pandoc state; the runtime constructor
 * supplies the existing complete translation loader/cache implementation. */
class LaTeXInlines(val kernel: LaTeXKernel, private val term: (Term)->String) {
    constructor(runtime: TranslationRuntime): this(LaTeXKernel(runtime),runtime::translateTerm)
    private fun <A> pure(value: A): LP<A> = ReaderTokenParsec.pure(value)
    private fun <A> defer(parser: ()->LP<A>): LP<A> = ReaderTokenParsec.defer(parser)
    private fun <A> effect(action: ()->A): LP<A> = ReaderTokenParsec.lift(TokenParsec.lift(action))
    private fun registry(build: MutableMap<String,LP<Inlines>>.()->Unit): Map<String,LP<Inlines>> =
        java.util.Collections.unmodifiableMap(sortedMapOf<String,LP<Inlines>>(UnicodeTextOrder).apply(build))
    private fun <A,B> till(p: LP<A>,end: LP<B>): LP<List<A>> = ReaderTokenParsec { env -> TokenParsec.manyTill(p.runReader(env),end.runReader(env)) }
    private fun label(): LP<Inlines> = kernel.braced().flatMap { ts ->
        val name=LaTeXTokens.untokenize(ts)
        LaTeXParsing.update { it.copy(lastLabel=name) }.map { Builder.spanWith(Attr(name,keyValues=listOf("label" to name)),Inlines()) }
    }
    private fun reference(kind: String): LP<Inlines> = kernel.braced().map { ts ->
        val name=LaTeXTokens.untokenize(ts)
        Builder.linkWith(Attr(keyValues=listOf("reference-type" to kind,"reference" to name)),"#$name","",inBrackets(Builder.str(name)))
    }
    fun rawInlineOr(name: String,fallback: LP<Inlines>): LP<Inlines> = LaTeXParsing.state().flatMap { state ->
        if(Extension.read("raw_tex") in state.user.options.extensions)kernel.getRawCommand(name,"\\$name").map { Builder.rawInline("latex",it) } else fallback
    }
    private fun verb(): LP<Inlines> = kernel.anySymbol().flatMap { token ->
        val marker=singlePoint(token.text) ?: return@flatMap ReaderTokenParsec.zero()
        LaTeXParsing.withVerbatimMode(till(kernel.newlineTok().notFollowedBy { "()" }.then(kernel.verbTok(marker)),kernel.symbol(marker))
            .map { Builder.code(LaTeXTokens.untokenize(it)) })
    }
    private fun inlineCode(classes: List<String>): LP<Inlines> = kernel.anySymbol().flatMap { token ->
        val marker=singlePoint(token.text) ?: return@flatMap ReaderTokenParsec.zero()
        val stop=if(marker=='{'.code)'}'.code else marker
        LaTeXParsing.withVerbatimMode(till(kernel.verbTok(stop),kernel.symbol(stop)).map {
            Builder.codeWith(Attr(classes=classes),LaTeXTokens.untokenize(it).replace('\n',' '))
        })
    }
    private fun lstinline(): LP<Inlines> = kernel.keyvals().option(emptyList()).flatMap { options -> inlineCode(listOfNotNull(listingsLanguage(options))) }
    private fun mintinline(): LP<Inlines> = kernel.skipopts().then(kernel.braced()).flatMap { inlineCode(listOf(LaTeXTokens.untokenize(it))) }
    fun romanNumeralArg(): LP<Long> {
        val number=kernel.satisfyTok(LaTeXKernel::isWordTok).many1().flatMap { ts ->
            val text=LaTeXTokens.untokenize(ts)
            if(!text.codePoints().allMatch(ParserCharacters::isDigit))ReaderTokenParsec.fail("Non-digits in argument to \\Rn or \\RN")
            else HaskellNumber.readInteger(text)?.toLong()?.let(::pure) ?: ReaderTokenParsec.zero()
        }
        val braced=kernel.symbol('{').then(kernel.spaces()).then(number).before(kernel.spaces()).before(kernel.symbol('}'))
        return kernel.spaces().then(number or braced)
    }
    fun accentWith(tok: LP<Inlines>,mark: Int,fallback: Int?): LP<Inlines> = tok.option(Inlines()).map { value ->
        val first=value.values.firstOrNull()
        when {
            first is Inline.Str && first.text.isNotEmpty() -> {
                val end=Character.charCount(first.text.codePointAt(0))
                Inlines(listOf(Inline.Str(normalizeAccent(first.text.substring(0,end),mark)+first.text.substring(end)))+value.values.drop(1))
            }
            value.values.isEmpty() || value.values==listOf(Inline.Space) -> Builder.str(TextChars.fromPoints(intArrayOf(fallback ?: mark)))
            else->value
        }
    }
    fun verbCommands(): Map<String,LP<Inlines>> = registry {
        put("verb",defer { verb() });put("Verb",defer { verb() })
        put("lstinline",defer { lstinline() });put("mintinline",defer { mintinline() })
    }
    fun miscCommands(): Map<String,LP<Inlines>> = registry { for((name,text) in miscLiterals)put(name,pure(Builder.str(text))) }
    fun accentCommands(tok: LP<Inlines>): Map<String,LP<Inlines>> = registry {
        for((name,text) in accentLiterals)put(name,pure(Builder.str(text)))
        for((name,spec) in accentSpecs)put(name,defer { accentWith(tok,spec.first,spec.second) })
    }
    fun charCommands(): Map<String,LP<Inlines>> = registry {
        for((name,text) in charLiterals)put(name,pure(Builder.str(text)))
        put("/",pure(Inlines()));put("@",pure(Inlines()));put("ps",pure(Builder.str("PS.")+Builder.space))
        put("\\",LaTeXParsing.state().flatMap { st ->
            if(st.user.inTableCell)ReaderTokenParsec.zero() else kernel.rawopt().optional().then(kernel.spaces()).map { Builder.linebreak }
        })
    }
    fun biblatexInlineCommands(tok: LP<Inlines>): Map<String,LP<Inlines>> = registry {
        put("RN",defer { romanNumeralArg().map { Builder.str(DocumentShared.romanNumeral(it)) } })
        put("Rn",defer { romanNumeralArg().map { Builder.str(DocumentShared.romanNumeral(it).lowercase()) } })
        fun wrap(name: String,f: (Inlines)->Inlines) { put(name,tok.map { Builder.spanWith(Attr(),f(it)) }) }
        wrap("mkbibquote",Builder::doubleQuoted);wrap("mkbibemph",Builder::emph);wrap("mkbibitalic",Builder::emph);wrap("mkbibbold",Builder::strong)
        wrap("mkbibparens") { Builder.str("(")+it+Builder.str(")") };wrap("mkbibbrackets",::inBrackets);wrap("autocap") { it }
        put("textnormal",tok.map { Builder.spanWith(Attr(classes=listOf("nodecor")),it) })
        put("bibstring",kernel.braced().map { ts -> val text=LaTeXTokens.untokenize(ts);Builder.spanWith(Attr(keyValues=listOf("bibstring" to text)),Builder.str(text)) })
        put("adddot",pure(Builder.str(".")));put("adddotspace",pure(Builder.spanWith(Attr(),Builder.str(".")+Builder.space)))
        put("addabbrvspace",pure(Builder.space));put("hyphen",pure(Builder.str("-")))
    }
    fun nameCommands(): Map<String,LP<Inlines>> = registry { for((name,t) in nameTerms)put(name,effect { Builder.str(term(t)) }) }
    fun refCommands(): Map<String,LP<Inlines>> = registry {
        put("label",rawInlineOr("label",defer { label() }))
        for((name,kind) in listOf("ref" to "ref","cref" to "ref+label","Cref" to "ref+Label","vref" to "ref","eqref" to "eqref","autoref" to "ref+label"))
            put(name,rawInlineOr(name,defer { reference(kind) }))
    }
    private fun acronym(form: String,plural: Boolean): LP<Inlines> = kernel.braced().map { ts ->
        val name=LaTeXTokens.untokenize(ts)
        val text=Builder.str(name)+(if(plural)Builder.str("s") else Inlines())
        Builder.spanWith(Attr(keyValues=listOf("acronym-label" to name,"acronym-form" to (if(plural)"plural+" else "singular+")+form)),text)
    }
    fun acronymCommands(): Map<String,LP<Inlines>> = registry {
        for((name,form) in singularAcronyms)put(name,defer { acronym(form,false) })
        for((name,form) in pluralAcronyms)put(name,defer { acronym(form,true) })
    }
    companion object {
        fun normalizeNfc(text: String): String = CanonicalNormalization.normalize(text)
        fun normalizeAccent(first: String,mark: Int): String = normalizeNfc(TextChars.fromString(first)+TextChars.fromPoints(intArrayOf(mark)))
        private fun singlePoint(text: String): Int? = text.takeIf { it.codePointCount(0,it.length)==1 }?.codePointAt(0)
        private fun inBrackets(value: Inlines) = Builder.str("[")+value+Builder.str("]")
        fun listingsLanguage(options: List<Pair<String,String>>): String? = options.firstOrNull { it.first=="language" }?.second?.let { Highlighting.fromListingsLanguage(it) ?: it }
        private val miscLiterals=mapOf(
            "pounds" to "£",
            "euro" to "€",
            "copyright" to "©",
            "textasciicircum" to "^",
            "textasciitilde" to "~",
            "textbaht" to "฿",
            "textblank" to "␢",
            "textbigcircle" to "○",
            "textbrokenbar" to "¦",
            "textbullet" to "•",
            "textcentoldstyle" to "¢",
            "textcopyright" to "©",
            "textdagger" to "†",
            "textdegree" to "°",
            "textdollar" to "$",
            "textdong" to "₫",
            "textlira" to "₤",
            "textmu" to "μ",
            "textmusicalnote" to "♪",
            "textonehalf" to "½",
            "textonequarter" to "¼",
            "textparagraph" to "¶",
            "textpertenthousand" to "‱",
            "textpeso" to "₱",
            "textquotesingle" to "'",
            "textregistered" to "®",
            "textsection" to "§",
            "textsterling" to "£",
            "textthreequarters" to "¾",
            "textthreesuperior" to "³",
            "texttwosuperior" to "²",
            "textyen" to "¥"
        )
        private val accentLiterals=mapOf(
            "aa" to "å",
            "AA" to "Å",
            "ss" to "ß",
            "o" to "ø",
            "O" to "Ø",
            "L" to "Ł",
            "l" to "ł",
            "ae" to "æ",
            "AE" to "Æ",
            "oe" to "œ",
            "OE" to "Œ",
            "i" to "ı",
            "j" to "ȷ"
        )
        private val charLiterals=mapOf(
            "ldots" to "…",
            "vdots" to "⋮",
            "dots" to "…",
            "mdots" to "…",
            "sim" to "~",
            "sep" to ",",
            "P" to "¶",
            "S" to "§",
            "$" to "$",
            "%" to "%",
            "&" to "&",
            "#" to "#",
            "_" to "_",
            "{" to "{",
            "}" to "}",
            "-" to "­",
            "qed" to " ◻",
            "lq" to "‘",
            "rq" to "’",
            "textquoteleft" to "‘",
            "textquoteright" to "’",
            "textquotedblleft" to "“",
            "textquotedblright" to "”",
            "," to " ",
            " " to " ",
            "TeX" to "TeX",
            "LaTeX" to "LaTeX",
            "bar" to "|",
            "textless" to "<",
            "textgreater" to ">",
            "textbackslash" to "\\",
            "backslash" to "\\",
            "slash" to "/",
            "faCheck" to "✓",
            "faClose" to "✗",
            "bshyp" to "\\­",
            "fshyp" to "/­",
            "dothyp" to ".­",
            "colonhyp" to ":­",
            "hyp" to "-",
            "glq" to "‚",
            "grq" to "‘",
            "glqq" to "„",
            "grqq" to "“",
            "flq" to "‹",
            "frq" to "›",
            "flqq" to "«",
            "frqq" to "»",
            "dq" to "\"",
            "guillemetleft" to "«",
            "guillemotleft" to "«",
            "guillemetright" to "»",
            "guillemotright" to "»",
            "guilsinglleft" to "‹",
            "guilsinglright" to "›",
            "quotedblbase" to "„",
            "quotesinglbase" to ",",
            "textquotedbl" to "\""
        )
        val accentSpecs: Map<String,Pair<Int,Int?>> = java.util.Collections.unmodifiableMap(sortedMapOf<String,Pair<Int,Int?>>(UnicodeTextOrder).apply {
            put("H", 779 to null)
            put("`", 768 to 96)
            put("'", 769 to 39)
            put("^", 770 to 94)
            put("~", 771 to 126)
            put("\"", 776 to null)
            put(".", 775 to null)
            put("=", 772 to null)
            put("|", 781 to null)
            put("b", 817 to null)
            put("c", 807 to null)
            put("G", 783 to null)
            put("h", 777 to null)
            put("d", 803 to null)
            put("f", 785 to null)
            put("r", 778 to null)
            put("t", 865 to null)
            put("U", 782 to null)
            put("v", 780 to null)
            put("u", 774 to null)
            put("k", 808 to null)
            put("textogonekcentered", 808 to null)
            put("newtie", 785 to null)
            put("textcircled", 8413 to null)
        })
        private val nameTerms=mapOf(
            "figurename" to Term.Figure,
            "prefacename" to Term.Preface,
            "refname" to Term.References,
            "bibname" to Term.Bibliography,
            "chaptername" to Term.Chapter,
            "partname" to Term.Part,
            "contentsname" to Term.Contents,
            "listfigurename" to Term.ListOfFigures,
            "listtablename" to Term.ListOfTables,
            "indexname" to Term.Index,
            "abstractname" to Term.Abstract,
            "tablename" to Term.Table,
            "enclname" to Term.Encl,
            "ccname" to Term.Cc,
            "headtoname" to Term.To,
            "pagename" to Term.Page,
            "seename" to Term.See,
            "seealsoname" to Term.SeeAlso,
            "proofname" to Term.Proof,
            "glossaryname" to Term.Glossary,
            "lstlistingname" to Term.Listing
        )
        private val singularAcronyms=mapOf(
            "gls" to "short",
            "Gls" to "short",
            "glsdesc" to "long",
            "Glsdesc" to "long",
            "GLSdesc" to "long",
            "acrlong" to "long",
            "Acrlong" to "long",
            "acrfull" to "full",
            "Acrfull" to "full",
            "acrshort" to "abbrv",
            "Acrshort" to "abbrv",
            "ac" to "short",
            "acf" to "full",
            "acs" to "abbrv",
            "acl" to "long",
            "Ac" to "short",
            "Acf" to "full",
            "Acs" to "abbrv",
            "Acl" to "long"
        )
        private val pluralAcronyms=mapOf(
            "glspl" to "short",
            "Glspl" to "short",
            "glsdescplural" to "long",
            "Glsdescplural" to "long",
            "GLSdescplural" to "long",
            "acp" to "short",
            "acfp" to "full",
            "acsp" to "abbrv",
            "aclp" to "long",
            "Acp" to "short",
            "Acfp" to "full",
            "Acsp" to "abbrv",
            "Aclp" to "long"
        )
    }
}
