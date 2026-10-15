// Text.Pandoc.Shared, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

/** Complete Shared helpers used by the HTML reader. */
object InlineShared {
    val htmlSpanLikeElements: Set<String> = java.util.Collections.unmodifiableSet(sortedSetOf("kbd", "mark", "dfn", "abbr"))

    fun addMetaField(key: String, value: MetaValue, meta: Map<String, MetaValue>): Map<String, MetaValue> {
        val old = meta[key]
        val combined = when (old) {
            null -> value
            is MetaValue.MetaList -> MetaValue.MetaList(old.values + if (value is MetaValue.MetaList) value.values else listOf(value))
            else -> MetaValue.MetaList(listOf(old, value))
        }
        return meta + (key to combined)
    }
    fun addMetaField(key: String, value: CharacterString, meta: Map<String, MetaValue>) = addMetaField(key, MetaValue.MetaString(Chars.build(value)), meta)
    fun addMetaField(key: String, value: String, meta: Map<String, MetaValue>) = addMetaField(key, value.toMetaValue(), meta)
    fun addMetaField(key: String, value: Boolean, meta: Map<String, MetaValue>) = addMetaField(key, value.toMetaValue(), meta)
    fun addMetaField(key: String, value: Inlines, meta: Map<String, MetaValue>) = addMetaField(key, value.toMetaValue(), meta)
    fun addMetaField(key: String, value: Blocks, meta: Map<String, MetaValue>) = addMetaField(key, value.toMetaValue(), meta)
    fun <T> addMetaField(key: String, value: T, meta: Map<String, MetaValue>, convert: (T) -> MetaValue) =
        addMetaField(key, convert(value), meta)

    fun extractSpaces(f: (Inlines) -> Inlines, value: Inlines): Inlines {
        fun boundary(i: Inline?) = when (i) {
            Inline.Space -> Builder.space
            Inline.SoftBreak -> Builder.softbreak
            else -> Inlines()
        }
        val left = boundary(value.values.firstOrNull())
        val right = boundary(value.values.lastOrNull())
        return left + f(value.trim()) + right
    }

    fun formatCode(attr: Attr, value: Inlines): Inlines {
        fun plaintext(i: Inline) = i is Inline.Str || i == Inline.Space || i == Inline.SoftBreak || i is Inline.Quoted
        val visitor = object : PandocVisitor() {
            override fun transformInlines(value: List<Inline>): List<Inline> = buildList {
                var at = 0
                while (at < value.size) {
                    if (!plaintext(value[at])) {
                        add(value[at++])
                    } else {
                        val start = at++
                        while (at < value.size && plaintext(value[at])) at++
                        add(Inline.Code(attr, DocumentShared.stringifyInlines(value.subList(start, at))))
                    }
                }
            }
        }
        return Inlines(visitor.walkInlines(value.values))
    }
}
