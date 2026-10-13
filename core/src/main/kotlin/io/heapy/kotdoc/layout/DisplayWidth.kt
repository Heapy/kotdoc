// Text.DocLayout 0.6: Copyright (C) 2010-2024 John MacFarlane.
// BSD-3-Clause; see licenses/DocLayout-BSD-3-Clause.txt.
package io.heapy.kotdoc.layout

import io.heapy.kotdoc.ast.Emoji
import kotlinx.serialization.json.*

internal enum class Width { Narrow, Wide, Combining, Control, Ambiguous, ZWJ, EmojiPresentationMod, EmojiSkinToneMod }

object DisplayWidth {
    private val rows = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/io/heapy/kotdoc/doclayout-widths.json")).readText()).jsonArray
    private val boundaries = rows.map { it.jsonArray[0].jsonPrimitive.int }.toIntArray()
    private val widths = rows.map { Width.valueOf(it.jsonArray[1].jsonPrimitive.content) }
    private data class EmojiInfo(val variation: Boolean, val skinTone: Boolean)
    private val emojis = buildMap<Int, EmojiInfo> {
        for (emoji in Emoji.baseEmojis) {
            val chars = emoji.codePoints().toArray(); val next = chars.getOrNull(1)
            val old = get(chars[0])
            put(chars[0], EmojiInfo(old?.variation == true || next != null && isEmojiVariation(next), old?.skinTone == true || next != null && isSkinToneModifier(next)))
        }
    }
    fun isSkinToneModifier(c: Int) = c in 0x1f3fb..0x1f3ff
    fun isEmojiVariation(c: Int) = c in 0xfe0e..0xfe0f
    fun isZWJ(c: Int) = c == 0x200d
    private fun width(c: Int, wide: Boolean): Width {
        if (isZWJ(c)) return Width.ZWJ
        if (c == 0xfe0f) return Width.EmojiPresentationMod
        if (isSkinToneModifier(c)) return Width.EmojiSkinToneMod
        val i = boundaries.binarySearch(c).let { if (it < 0) -it - 2 else it }
        val w = if (i < 0) Width.Control else widths[i]
        return if (w == Width.Ambiguous) (if (wide) Width.Wide else Width.Narrow) else w
    }
    private class State(var first: Boolean, var total: Long = 0, var last: Int = 32, var tentative: Long = 0) {
        fun update(c: Int, wide: Boolean, shortcut: Boolean) {
            val w = (if (shortcut) (if (wide) WidthShortcuts.wide(c) else WidthShortcuts.narrow(c)) else null) ?: width(c, wide)
            val previous = emojis[last]
            when {
                w == Width.ZWJ && (previous != null || last == 0xfe0f || isSkinToneModifier(last)) -> { total -= 2; tentative = 2 }
                w == Width.EmojiPresentationMod && previous?.variation == true -> tentative = 2
                w == Width.EmojiSkinToneMod && previous?.skinTone == true -> tentative = 2
                else -> {
                    total += tentative
                    tentative = when (w) {
                        Width.Narrow -> 1; Width.Wide, Width.EmojiSkinToneMod -> 2
                        Width.Combining -> if (first) 1 else 0
                        Width.Ambiguous -> if (wide) 2 else 1
                        else -> 0
                    }
                }
            }
            first = false; last = c
        }
    }
    fun charWidth(c: Int): Long {
        require(c in 0..0x10ffff)
        return State(false).apply { update(c, false, true) }.let { it.total + it.tentative }
    }
    fun realLength(text: String): Long = length(text.codePoints().toArray())
    fun realLengthNarrowContext(text: String) = realLength(text)
    fun realLengthWideContext(text: String) = length(text.codePoints().toArray(), wide = true)
    fun realLengthNarrowContextNoShortcut(text: String) = length(text.codePoints().toArray(), shortcut = false)
    fun realLengthWideContextNoShortcut(text: String) = length(text.codePoints().toArray(), wide = true, shortcut = false)
    fun length(points: IntArray, wide: Boolean = false, shortcut: Boolean = true): Long {
        val state = State(true)
        for (c in points) { require(c in 0..0x10ffff); state.update(c, wide, shortcut) }
        return state.total + state.tentative
    }
}
