// Canonical composition from unicode-transforms-0.4.0.1,
// Copyright (c) 2016 Harendra Kumar, BSD-3-Clause.
// Unicode tables from unicode-data-0.9.0, Copyright (c) 2020
// Composewell Technologies and Contributors, Apache-2.0. See licenses/.
package io.heapy.kotdoc.latex

import kotlinx.serialization.json.*

/** The pinned unicode-transforms NFC path, including its pinned Unicode data.
 * Host JDK/ICU normalization uses different combining classes for newer marks. */
internal object CanonicalNormalization {
    private val data = Json.parseToJsonElement(checkNotNull(javaClass.getResourceAsStream(
        "/io/heapy/kotdoc/unicode-nfc-0.9.0.json"
    )).bufferedReader().use { it.readText() }).jsonObject.getValue("data").jsonObject
    private fun rows(name: String) = data.getValue(name).jsonArray.map { it.jsonArray }
    private val classes = IntArray(0x110000).apply {
        for (row in rows("classes")) this[row[0].jsonPrimitive.int] = row[1].jsonPrimitive.int
    }
    private val decompositions = rows("decompositions").associate {
        it[0].jsonPrimitive.int to it[1].jsonArray.map { c -> c.jsonPrimitive.int }.toIntArray()
    }
    private fun pair(a: Int, b: Int): Long = (a.toLong() shl 21) or b.toLong()
    private fun compositions(name: String) = rows(name).associate {
        pair(it[0].jsonPrimitive.int, it[1].jsonPrimitive.int) to it[2].jsonPrimitive.int
    }
    private val compositions = compositions("compositions")
    private val starterCompositions = compositions("starterCompositions")

    fun normalize(text: String): String {
        val output = StringBuilder(text.length)
        // RegBuf contains a starter (or leading nonstarter) and its marks.
        // Stable sorting when flushed has the same ordering as insertIntoRegBuf.
        val regular = ArrayList<Int>()
        var jamo = -1
        fun flushRegular() {
            if (regular.isEmpty()) return
            regular.sortWith(compareBy { classes[it] })
            var starter = regular[0]
            val uncombined = ArrayList<Int>()
            var i = 1
            while (i < regular.size) {
                val c = regular[i++]
                val composed = compositions[pair(starter, c)]
                if (composed != null) starter = composed else {
                    uncombined.add(c)
                    // Failed composition blocks every subsequent equal class.
                    while (i < regular.size && classes[regular[i]] == classes[c]) {
                        uncombined.add(regular[i++])
                    }
                }
            }
            output.appendCodePoint(starter)
            for (c in uncombined) output.appendCodePoint(c)
            regular.clear()
        }
        fun flushJamo() {
            if (jamo >= 0) { output.appendCodePoint(jamo); jamo = -1 }
        }
        fun insertJamo(c: Int) {
            val previous = jamo
            when {
                c <= 0x1112 -> { flushJamo(); jamo = c }
                c < 0x1161 || c == 0x11a7 -> { flushJamo(); output.appendCodePoint(c) }
                c <= 0x1175 && previous in 0x1100..0x1112 ->
                    jamo = 0xac00 + (previous - 0x1100) * 588 + (c - 0x1161) * 28
                c <= 0x11a7 -> { flushJamo(); output.appendCodePoint(c) }
                previous in 0xac00..0xd7a3 && (previous - 0xac00) % 28 == 0 -> {
                    output.appendCodePoint(previous + c - 0x11a7); jamo = -1
                }
                else -> { flushJamo(); output.appendCodePoint(c) }
            }
        }
        // Explicit work stack replaces the source's recursive decomposition.
        val pending = ArrayDeque<Int>()
        var index = 0
        while (index < text.length || pending.isNotEmpty()) {
            val c = if (pending.isNotEmpty()) pending.removeLast() else {
                val cp = text.codePointAt(index)
                index += Character.charCount(cp)
                if (cp in 0xd800..0xdfff) 0xfffd else cp
            }
            if (c in 0x1100..0x11c2) {
                flushRegular()
                if (jamo < 0) jamo = c else insertJamo(c)
                continue
            }
            if (c in 0xac00..0xd7a3) {
                flushRegular(); flushJamo(); jamo = c
                continue
            }
            flushJamo()
            val decomposition = decompositions[c]
            if (decomposition != null) {
                for (i in decomposition.indices.reversed()) pending.addLast(decomposition[i])
            } else if (regular.isEmpty() || classes[c] != 0) {
                regular.add(c)
            } else {
                val composed = if (regular.size == 1) starterCompositions[pair(regular[0], c)] else null
                if (composed != null) regular[0] = composed else {
                    flushRegular(); regular.add(c)
                }
            }
        }
        flushRegular(); flushJamo()
        return output.toString()
    }
}
