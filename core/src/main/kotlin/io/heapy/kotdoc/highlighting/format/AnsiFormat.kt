// Skylighting.Format.ANSI 0.1, Copyright (c) 2016-2022 John MacFarlane.
// BSD-3-Clause; vendor/skylighting-format-ansi-0.1/LICENSE.
// SGR rendering follows ansi-terminal-types 1.1.3 (BSD-3-Clause).
package io.heapy.kotdoc.highlighting.format

import io.heapy.kotdoc.highlighting.*
import kotlin.math.abs

object AnsiFormat {
    private const val reset = "\u001b[0m"
    private data class Palette(val code: Int, val r: Int, val g: Int, val b: Int)
    private val sixteen = listOf(
        Palette(0,0,0,0),Palette(1,128,0,0),Palette(2,0,128,0),Palette(3,128,128,0),Palette(4,0,0,128),Palette(5,128,0,128),Palette(6,0,128,128),Palette(7,192,192,192),
        Palette(8,128,128,128),Palette(9,255,0,0),Palette(10,0,255,0),Palette(11,255,255,0),Palette(12,0,0,255),Palette(13,255,0,255),Palette(14,0,255,255),Palette(15,255,255,255))
    private val twoFiftySix = buildList {
        // Original palette order is significant when distances tie.
        for (i in 0..<24) { val x=8+10*i; add(Palette(232+i,x,x,x)) }
        val cube = listOf(0,95,135,175,215,255)
        for (r in 0..<6) for (g in 0..<6) for (b in 0..<6) add(Palette(16+36*r+6*g+b,cube[r],cube[g],cube[b]))
        addAll(sixteen)
    }
    private fun nearest(color: HighlightColor, palette: List<Palette>): Int = palette.minBy {
        abs(color.red.toInt()-it.r)+abs(color.green.toInt()-it.g)+abs(color.blue.toInt()-it.b)
    }.code
    private fun csi(parameters: List<Int>) = "\u001b["+parameters.joinToString(";")+"m"
    private fun style(level: ANSIColorLevel, foreground: HighlightColor?, background: HighlightColor?, bold: Boolean=false, italic: Boolean=false, underline: Boolean=false): String {
        val params = mutableListOf<Int>(); var paletteText = ""
        fun color(value: HighlightColor?, foreground: Boolean) {
            if (value==null) return
            when (level) {
                ANSIColorLevel.ANSI16Color -> { val n=nearest(value,sixteen); params.add((if (foreground) 30 else 40)+(if (n>=8) 60 else 0)+n%8) }
                ANSIColorLevel.ANSI256Color -> paletteText += csi(listOf(if (foreground) 38 else 48,5,nearest(value,twoFiftySix)))
                ANSIColorLevel.ANSITrueColor -> params.addAll(listOf(if (foreground) 38 else 48,2,value.red.toInt(),value.green.toInt(),value.blue.toInt()))
            }
        }
        color(foreground,true); color(background,false)
        params.addAll(listOf(if (bold) 1 else 22,if (italic) 3 else 23,if (underline) 4 else 24))
        return (if (foreground==null && background==null) reset else "")+paletteText+csi(params)
    }
    fun format(options: FormatOptions, style: HighlightStyle, lines: List<SourceLine>): String {
        val level=options.ansiColorLevel
        val base=style(level,style.defaultColor,style.backgroundColor)
        val content=numberedLines(options.startNumber,lines) { n,line -> buildString {
            if (options.numberLines) { append(style(level,style.lineNumberColor ?: style.defaultColor,style.lineNumberBackgroundColor ?: style.backgroundColor)); append(n); append(base); append('\t') }
            for (token in line) {
                val spec=style.tokenStyles[token.type] ?: TokenStyle.Default
                append(style(level,spec.color ?: style.defaultColor,spec.background ?: style.backgroundColor,spec.bold,spec.italic,spec.underline))
                append(safeFormatText(token.text)); append(base)
            }
        } }.joinToString("\n")
        return reset+base+content+reset
    }
}
