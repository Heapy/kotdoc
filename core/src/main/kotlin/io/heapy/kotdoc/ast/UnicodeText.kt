// Port of text-2.1.3 caseConvert and generated CaseMapping tables.
// Copyright (c) 2008-2009 Tom Harper, 2009-2011 Bryan O'Sullivan,
// 2021 Andrew Lelechenko. BSD-2-Clause; see licenses/text-BSD-2-Clause.txt.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*

/** Context-independent full case conversion from the pinned Haskell text table. */
object UnicodeText {
    private val data = Json.parseToJsonElement(checkNotNull(
        javaClass.getResource("/io/heapy/kotdoc/case-mappings.json")
    ).readText()).jsonObject
    private fun mapping(name: String): Map<Int, String> = data.getValue(name).jsonObject.entries.associate {
        it.key.toInt(16) to it.value.jsonPrimitive.content
    }
    private val lower = mapping("lower")
    private val upper = mapping("upper")
    private val fold = mapping("fold")

    fun toLower(text: String): String = convert(text, lower)
    fun toUpper(text: String): String = convert(text, upper)
    fun toCaseFold(text: String): String = convert(text, fold)

    private fun convert(text: String, mapping: Map<Int, String>): String = buildString {
        text.codePoints().forEach { cp ->
            val replacement = mapping[cp]
            if (replacement == null) appendCodePoint(cp) else append(replacement)
        }
    }
}
