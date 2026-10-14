// Skylighting.Format.HTML 0.1.2.2, Copyright (c) 2016-2022 John MacFarlane.
// BSD-3-Clause; vendor/skylighting-format-blaze-html-0.1.2.2/LICENSE.
// Entity rendering follows blaze-markup 0.8.3.0 (BSD-3-Clause).
package io.heapy.kotdoc.highlighting.format

import io.heapy.kotdoc.highlighting.*

internal fun <A> numberedLines(start: Long, lines: List<SourceLine>, render: (Long, SourceLine) -> A): List<A> {
    val result = mutableListOf<A>(); var number = start
    for (line in lines) {
        result.add(render(number,line))
        // Original LineNo derives Enum from Int: [start..] stops at maxBound.
        if (number == Long.MAX_VALUE) break
        number++
    }
    return result
}

object HtmlFormat {
    private val shortNames = listOf("kw","dt","dv","bn","fl","cn","ch","sc","st","vs","ss","im","co","do","an","cv","ot","fu","va","cf","op","bu","ex","pp","at","re","in","wa","al","er","")
    private fun short(type: TokenType) = shortNames[type.ordinal]
    private fun escaped(text: String) = buildString {
        safeFormatText(text).codePoints().forEach { p -> when (p) {
            60 -> append("&lt;"); 62 -> append("&gt;"); 38 -> append("&amp;"); 34 -> append("&quot;"); 39 -> append("&#39;")
            else -> appendCodePoint(p)
        } }
    }
    private fun attribute(name: String, text: String) = " $name=\"${escaped(text)}\""
    private fun tokens(options: FormatOptions, line: SourceLine) = line.joinToString("") { token ->
        val text = escaped(token.text)
        if (token.type == TokenType.NormalTok) text
        else "<span class=\"${short(token.type)}\"" + (if (options.titleAttributes) " title=\"${token.type.name}\"" else "") + ">$text</span>"
    }
    private fun code(options: FormatOptions, classes: List<String>, content: String): String {
        val names = (listOf("sourceCode")+classes.filter { it!="sourceCode" }).joinToString(" ")
        val zero = options.startNumber - 1
        return "<code"+attribute("class",names)+(if (zero!=0L) attribute("style","counter-reset: source-line $zero;") else "")+">$content</code>"
    }
    fun formatInline(options: FormatOptions, lines: List<SourceLine>) = code(options,(options.codeClasses+options.containerClasses).distinct(),lines.joinToString("\n") { tokens(options,it) })
    fun formatBlock(options: FormatOptions, lines: List<SourceLine>) = block(options,lines,html4=false)
    fun formatHtml4Block(options: FormatOptions, lines: List<SourceLine>) = block(options,lines,html4=true)
    private fun block(options: FormatOptions, lines: List<SourceLine>, html4: Boolean): String {
        val classes = (listOf("sourceCode")+(if (options.numberLines) listOf("numberSource") else emptyList())+options.containerClasses.filter { it!="sourceCode" }).joinToString(" ")
        val text = numberedLines(options.startNumber,lines) { n,line ->
            val id = safeFormatText(options.lineIdPrefix)+n
            val aria = if (html4) "" else if (options.numberLines) attribute("aria-label",n.toString()) else attribute("aria-hidden","true")
            "<span"+attribute("id",id)+"><a"+attribute("href","#$id")+aria+(if (!options.numberLines) attribute("tabindex","-1") else "")+"></a>"+tokens(options,line)+"</span>"
        }.joinToString("\n")
        return "<div class=\"sourceCode\"><pre"+attribute("class",classes)+">"+code(options,options.codeClasses,text)+"</pre></div>"
    }
    fun styleToCss(style: HighlightStyle): String {
        val divspec = listOf("html { -webkit-text-size-adjust: 100%; }","pre > code.sourceCode { white-space: pre; position: relative; }","pre > code.sourceCode > span { display: inline-block; line-height: 1.25; }","pre > code.sourceCode > span:empty { height: 1.2em; }",".sourceCode { overflow: visible; }","code.sourceCode > span { color: inherit; text-decoration: inherit; }","div.sourceCode { margin: 1em 0; }","pre.sourceCode { margin: 0; }","@media screen {","div.sourceCode { overflow: auto; }","}","@media print {","pre > code.sourceCode { white-space: pre-wrap; }","pre > code.sourceCode > span { text-indent: -5em; padding-left: 5em; }","}")
        val numberspec = listOf("pre.numberSource code","  { counter-reset: source-line 0; }","pre.numberSource code > span","  { position: relative; left: -4em; counter-increment: source-line; }","pre.numberSource code > span > a:first-child::before","  { content: counter(source-line);","    position: relative; left: -1em; text-align: right; vertical-align: baseline;","    border: none; display: inline-block;","    -webkit-touch-callout: none; -webkit-user-select: none;","    -khtml-user-select: none; -moz-user-select: none;","    -ms-user-select: none; user-select: none;","    padding: 0 4px; width: 4em;",
            (style.lineNumberBackgroundColor?.let { "    background-color: ${colorHex(it)};\n" } ?: "")+(style.lineNumberColor?.let { "    color: ${colorHex(it)};\n" } ?: "")+"  }",
            "pre.numberSource { margin-left: 3em; "+(style.lineNumberColor?.let { "border-left: 1px solid ${colorHex(it)}; " } ?: "")+" padding-left: 4px; }")
        val colorspec = listOf(listOf("div.sourceCode\n  {",style.defaultColor?.let { "color: ${colorHex(it)};" } ?: "",style.backgroundColor?.let { "background-color: ${colorHex(it)};" } ?: "","}").joinToString(" "))
        val linkspec = listOf("@media screen {","pre > code.sourceCode > span > a:first-child::before { text-decoration: underline; }","}")
        val tokens = style.tokenStyles.entries.map { (type,token) ->
            "code span"+(short(type).takeUnless(String::isEmpty)?.let { ".$it" } ?: "")+" { "+
                (token.color?.let { "color: ${colorHex(it)}; " } ?: "")+(token.background?.let { "background-color: ${colorHex(it)}; " } ?: "")+
                (if (token.bold) "font-weight: bold; " else "")+(if (token.italic) "font-style: italic; " else "")+
                (if (token.underline) "text-decoration: underline; " else "")+"} /* ${type.wireName} */"
        }.sorted()
        return (divspec+numberspec+colorspec+linkspec+tokens).joinToString("\n",postfix="\n")
    }
}
