package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

class OriginalTypesNoShrinkTest {
    @TestFactory fun originalImplicitEmptyShrinkers(): List<DynamicTest> {
        val stream = javaClass.getResourceAsStream("/pandoc-types/no-shrink.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { line ->
            val r = Json.parseToJsonElement(line).jsonObject
            val kind = r.getValue("kind").jsonPrimitive.content
            val x = r.getValue("input")
            DynamicTest.dynamicTest("$kind seed=${r["seed"]} size=${r["size"]}") {
                val actual = when (kind) {
                    "pandoc" -> OriginalTypesShrink.pandoc(PandocJson.decode(x))
                    "meta" -> OriginalTypesShrink.meta(PandocJson.decodeMeta(x))
                    "citation" -> OriginalTypesShrink.citation(PandocJson.decodeCitation(x))
                    "mode" -> OriginalTypesShrink.mode(enumValueOf<CitationMode>(x.jsonObject.getValue("t").jsonPrimitive.content))
                    "math" -> OriginalTypesShrink.math(enumValueOf<MathType>(x.jsonObject.getValue("t").jsonPrimitive.content))
                    "quote" -> OriginalTypesShrink.quote(enumValueOf<QuoteType>(x.jsonObject.getValue("t").jsonPrimitive.content))
                    "alignment" -> OriginalTypesShrink.alignment(enumValueOf<Alignment>(x.jsonObject.getValue("t").jsonPrimitive.content))
                    "style" -> OriginalTypesShrink.style(enumValueOf<ListNumberStyle>(x.jsonObject.getValue("t").jsonPrimitive.content))
                    "delim" -> OriginalTypesShrink.delim(enumValueOf<ListNumberDelim>(x.jsonObject.getValue("t").jsonPrimitive.content))
                    else -> error(kind)
                }
                val expected = r.getValue("expected").jsonArray
                assertEquals(0,expected.size,"the original default shrink has no candidates")
                assertEquals(expected.size,actual.count())
            }
        }
    }
}
