// Skylighting.Format.LaTeX 0.1 / ConTeXt 0.1.0.2 / Typst 0.1.
// Copyright (c) 2016-2018 John MacFarlane.
// ConTeXt: Copyright © 2022, Albert Krewinkel, John MacFarlane. BSD-3-Clause;
// see vendor/skylighting-format-{latex,context,typst}-*/LICENSE.
package io.heapy.kotdoc.highlighting.format

import io.heapy.kotdoc.highlighting.*
import io.heapy.kotdoc.parsing.ParserCharacters
import java.util.Locale

internal fun safeFormatText(text: String) = buildString {
    text.codePoints().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) }
}
private fun escaped(text: String, escape: (Int) -> String?): String = buildString {
    safeFormatText(text).codePoints().forEach { point -> val replacement = escape(point); if (replacement == null) appendCodePoint(point) else append(replacement) }
}
private fun whitespace(text: String) = safeFormatText(text).codePoints().allMatch(ParserCharacters::isSpace)
internal fun colorHex(color: HighlightColor) = "#" + listOf(color.red,color.green,color.blue).joinToString("") { it.toString(16).padStart(2,'0') }
private fun fractions(color: HighlightColor) = listOf(color.red,color.green,color.blue).map { String.format(Locale.ROOT,"%.2f",it.toInt()/255.0) }
private fun unlines(lines: List<String>) = lines.joinToString("\n",postfix="\n")

object LaTeXFormat {
    private fun escape(text: String, inline: Boolean) = escaped(text) { c -> when (c) {
        92 -> "\\textbackslash{}"; 123 -> "\\{"; 125 -> "\\}"; 124 -> if (inline) "\\VerbBar{}" else null
        95 -> "\\_"; 38 -> "\\&"; 37 -> "\\%"; 35 -> "\\#"; 96 -> "\\textasciigrave{}"
        39 -> "\\textquotesingle{}"; 45 -> "{-}"; 126 -> "\\textasciitilde{}"; 94 -> "\\^{}"; 62 -> "\\textgreater{}"; 60 -> "\\textless{}"
        else -> null
    } }
    private fun format(lines: List<SourceLine>, inline: Boolean) = lines.joinToString("\n") { line -> line.joinToString("") { token ->
        val text = escape(token.text,inline)
        if (token.type == TokenType.NormalTok && whitespace(token.text)) text else "\\${token.type.name}{$text}"
    } }
    fun formatInline(options: FormatOptions, lines: List<SourceLine>) = "\\VERB|${format(lines,true)}|"
    fun formatBlock(options: FormatOptions, lines: List<SourceLine>): String = unlines(listOf(
        "\\begin{Shaded}",
        "\\begin{Highlighting}[" + (if (options.numberLines) "numbers=left," + (if (options.startNumber==1L) "" else ",firstnumber=${options.startNumber}") + "," else "") + "]",
        format(lines,false), "\\end{Highlighting}", "\\end{Shaded}"))
    fun styleToLaTeX(style: HighlightStyle): String {
        val preamble = mutableListOf("\\usepackage{color}","\\usepackage{fancyvrb}","\\newcommand{\\VerbBar}{|}","\\newcommand{\\VERB}{\\Verb[commandchars=\\\\\\{\\}]}","\\DefineVerbatimEnvironment{Highlighting}{Verbatim}{commandchars=\\\\\\{\\}}","% Add ',fontsize=\\small' for more characters per line")
        style.backgroundColor?.let { color -> preamble.addAll(listOf("\\usepackage{framed}","\\definecolor{shadecolor}{RGB}{${color.red},${color.green},${color.blue}}","\\newenvironment{Shaded}{\\begin{snugshade}}{\\end{snugshade}}")) }
            ?: preamble.add("\\newenvironment{Shaded}{}{}")
        val macros = TokenType.entries.map { type ->
            val token = style.tokenStyles[type] ?: TokenStyle.Default
            var text = "#1"
            token.background?.let { text = "\\colorbox[rgb]{${fractions(it).joinToString(",")}}{$text}" }
            if (token.italic) text = "\\textit{$text}"
            if (token.bold) text = "\\textbf{$text}"
            if (token.underline) text = "\\underline{$text}"
            (token.color ?: style.defaultColor)?.let { text = "\\textcolor[rgb]{${fractions(it).joinToString(",")}}{$text}" }
            "\\newcommand{\\${type.name}}[1]{$text}"
        }.sorted()
        return unlines(preamble + macros)
    }
}

object ConTeXtFormat {
    private fun escape(text: String) = escaped(text) { c -> when (c) {
        92 -> "\\letterbackslash{}"; 123 -> "\\letteropenbrace{}"; 125 -> "\\letterclosebrace{}"; 124 -> "\\letterbar{}"
        36 -> "\\letterdollar{}"; 95 -> "\\letterunderscore{}"; 37 -> "\\letterpercent{}"; 35 -> "\\letterhash{}"; 47 -> "\\letterslash{}"; 126 -> "\\lettertilde{}"
        else -> null
    } }
    private fun format(lines: List<SourceLine>) = lines.joinToString("\n") { line -> line.joinToString("") { token ->
        val text = escape(token.text)
        if (token.type == TokenType.NormalTok && whitespace(token.text)) text else "/BTEX\\${token.type.name}{${text.replace("  "," { }")}}/ETEX"
    }.replace("/ETEX /BTEX"," ").replace("/ETEX/BTEX","") }
    fun formatInline(options: FormatOptions, lines: List<SourceLine>) = "\\highlight{${format(lines)}}"
    fun formatBlock(options: FormatOptions, lines: List<SourceLine>) = unlines(listOf("\\starthighlighting" + (if (options.numberLines) "[numbering=line]" else ""),format(lines),"\\stophighlighting"))
    fun styleToConTeXt(style: HighlightStyle): String {
        val preamble = mutableListOf<String>()
        style.backgroundColor?.let { c -> preamble.add("\\definecolor[shadecolor][x=${c.red.toString(16)}${c.green.toString(16)}${c.blue.toString(16)}]") }
        preamble.addAll(listOf("\\defineframedtext [shaded]","  [backgroundcolor=shadecolor,","   background=color,","   frame=off,","   offset=0pt,","   width=local]","\\definetyping [highlighting]","  [escape=yes,","   before={\\startshaded},","   after={\\stopshaded}]","\\definetype [highlight]","  [escape=yes]"))
        val macros = TokenType.entries.map { type ->
            val token = style.tokenStyles[type] ?: TokenStyle.Default
            var text = "#1"
            if (token.italic) text = "\\em $text"
            if (token.bold) text = "\\bf $text"
            if (token.underline) text = "\\underbar{$text}"
            (token.color ?: style.defaultColor)?.let { val (r,g,b) = fractions(it); text = "\\colored[r=$r,g=$g,b=$b]{$text}" }
            "\\define[1]\\${type.name}{$text}"
        }.sorted()
        return unlines(preamble+macros)
    }
}

object TypstFormat {
    private fun quoted(text: String) = "\""+safeFormatText(text).replace("\\","\\\\").replace("\"","\\\"")+"\""
    fun formatInline(options: FormatOptions, lines: List<SourceLine>) = lines.joinToString("#EndLine()\n") { line -> line.joinToString("") { "#${it.type.name}(${quoted(it.text)});" } }
    fun formatBlock(options: FormatOptions, lines: List<SourceLine>) = "#Skylighting(" +
        (if (options.numberLines) "number: true, start: ${options.startNumber}, " else "") + "(" +
        lines.joinToString("\n") { "[${formatInline(options,listOf(it))}]," } + "));"
    fun styleToTypst(style: HighlightStyle): String {
        fun rgb(color: HighlightColor) = "rgb(\"${colorHex(color)}\")"
        val lineNumberFill = style.lineNumberColor?.let { "fill: ${rgb(it)}, " } ?: ""
        val preamble = listOf("/* Function definitions for syntax highlighting generated by skylighting: */","#let EndLine() = raw(\"\\n\")","#let Skylighting(fill: none, number: false, start: 1, sourcelines) = {","   let blocks = []","   let lnum = start - 1","   let bgcolor = ${style.backgroundColor?.let(::rgb) ?: "none"}","   for ln in sourcelines {","     if number {","       lnum = lnum + 1","       blocks = blocks + box(width: if start + sourcelines.len() > 999 { 30pt } else { 24pt }, text(${lineNumberFill}[ #lnum ]))","     }","     blocks = blocks + ln + EndLine()","   }","   block(fill: bgcolor, blocks)","}")
        val macros = TokenType.entries.map { type ->
            val token = style.tokenStyles[type] ?: TokenStyle.Default
            var text = "text(" + (if (token.bold) "weight: \"bold\"," else "") + (if (token.italic) "style: \"italic\"," else "") +
                ((token.color ?: style.defaultColor)?.let { "fill: ${rgb(it)}," } ?: "") + "raw(s))"
            // Original Typst formatter currently ignores per-token background.
            if (token.underline) text = "underline($text)"
            "#let ${type.name}(s) = $text"
        }.sorted()
        return unlines(preamble+macros)
    }
}
