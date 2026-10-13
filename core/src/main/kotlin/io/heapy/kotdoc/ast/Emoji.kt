// Text.Pandoc.Emoji: Copyright (C) 2015 John MacFarlane, GPL-2.0-or-later.
// Text.Emoji/Trie: Copyright (c) 2019 John MacFarlane, BSD-3-Clause.
// Emoji alias data: Copyright (c) 2019 GitHub, Inc., MIT. See licenses/.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import java.util.Collections

object Emoji {
    private val data = Json.parseToJsonElement(checkNotNull(
        javaClass.getResource("/io/heapy/kotdoc/emojis.json")
    ).readText()).jsonObject

    /** The dependency's association list, including repeated emoji and alias order. */
    val aliases: List<Pair<String, String>> = Collections.unmodifiableList(data.getValue("emojis").jsonArray.map {
        it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content
    })
    /** Pandoc's ordered map of aliases to emoji. */
    val emojis: Map<String, String> = Collections.unmodifiableMap(aliases.toMap().toSortedMap(UnicodeTextOrder))
    val baseEmojis: List<String> = strings("base")
    val zwjEmojis: List<String> = strings("zwj")
    private val reverse: Map<String, List<String>> = aliases.groupBy({ it.second }, { it.first })
        .mapValues { Collections.unmodifiableList(it.value) }

    private class Trie {
        val children = mutableMapOf<Int, Trie>()
        var aliases: List<String>? = null
    }
    private val trie = Trie().apply {
        for ((emoji, aliases) in reverse) {
            var node = this
            emoji.codePoints().forEach { node = node.children.getOrPut(it) { Trie() } }
            node.aliases = aliases
        }
    }

    private fun strings(key: String): List<String> = Collections.unmodifiableList(
        data.getValue(key).jsonArray.map { it.jsonPrimitive.content })

    fun emojiFromAlias(alias: String): String? = emojis[alias]
    fun aliasesFromEmoji(emoji: String): List<String>? = reverse[emoji]

    fun emojiToInline(alias: String): Inline.Span? = emojiFromAlias(alias)?.let {
        Inline.Span(Attr(classes = listOf("emoji"), keyValues = listOf("data-emoji" to alias)), listOf(Inline.Str(it)))
    }

    /** Longest complete code-point match; replacement text is never scanned again. */
    fun replaceEmojis(text: String, replacement: (String, List<String>) -> String): String = buildString {
        var offset = 0
        while (offset < text.length) {
            var node = trie
            var cursor = offset
            var end = offset
            var best: List<String>? = null
            while (cursor < text.length) {
                val cp = text.codePointAt(cursor)
                node = node.children[cp] ?: break
                cursor += Character.charCount(cp)
                node.aliases?.let { best = it; end = cursor }
            }
            val matched = best
            if (matched != null) {
                append(replacement(text.substring(offset, end), matched))
                offset = end
            } else {
                val cp = text.codePointAt(offset)
                appendCodePoint(cp)
                offset += Character.charCount(cp)
            }
        }
    }
}
