package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Each original instance and private generator boundary has its own source replay. */
class OriginalTypesGeneratorDomainTest {
    private fun value(kind:String,x:JsonElement):Any = when(kind) {
        "inlines" -> x.jsonArray.map(PandocJson::decodeInline)
        "blocks" -> x.jsonArray.map(PandocJson::decodeBlock)
        "inline" -> PandocJson.decodeInline(x)
        "block" -> PandocJson.decodeBlock(x)
        "pandoc" -> PandocJson.decode(x)
        "citation" -> PandocJson.decodeCitation(x)
        "row" -> PandocJson.decodeRow(x)
        "head" -> PandocJson.decodeTableHead(x)
        "body" -> PandocJson.decodeTableBody(x)
        "foot" -> PandocJson.decodeTableFoot(x)
        "cell" -> PandocJson.decodeCell(x)
        "caption" -> PandocJson.decodeCaption(x)
        "meta" -> PandocJson.decodeMeta(x)
        "mode" -> enumValueOf<CitationMode>(x.jsonObject.getValue("t").jsonPrimitive.content)
        "math" -> enumValueOf<MathType>(x.jsonObject.getValue("t").jsonPrimitive.content)
        "quote" -> enumValueOf<QuoteType>(x.jsonObject.getValue("t").jsonPrimitive.content)
        "alignment" -> enumValueOf<Alignment>(x.jsonObject.getValue("t").jsonPrimitive.content)
        "style" -> enumValueOf<ListNumberStyle>(x.jsonObject.getValue("t").jsonPrimitive.content)
        "delim" -> enumValueOf<ListNumberDelim>(x.jsonObject.getValue("t").jsonPrimitive.content)
        "text" -> x.jsonPrimitive.content
        "attr" -> PandocJson.decodeAttr(x)
        else -> error(kind)
    }
    private fun default(kind:String):OriginalQuickCheck.Gen<out Any> = with(OriginalSeededTypesArbitrary) {
        when(kind) {
            "inlines" -> inlines(); "blocks" -> blocks(); "inline" -> inline(); "block" -> block()
            "pandoc" -> pandoc(); "mode" -> mode(); "citation" -> citation(); "row" -> row()
            "head" -> head(); "body" -> body(); "foot" -> foot(); "cell" -> cell(); "caption" -> caption()
            "math" -> math(); "quote" -> quote(); "meta" -> meta(); "alignment" -> alignment()
            "style" -> style(); "delim" -> delim()
            else -> error(kind)
        }
    }
    private fun helper(kind:String,depth:Int):OriginalQuickCheck.Gen<out Any> = with(OriginalSeededTypesArbitrary) {
        when(kind) {
            "text" -> text(); "attr" -> attr(); "inlines" -> inlines(depth); "inline" -> inline(depth)
            "block" -> block(depth); "row" -> row(depth); "head" -> head(depth); "body" -> body(depth)
            "foot" -> foot(depth); "cell" -> cell(depth)
            else -> default(kind)
        }
    }
    private fun cases(resource:String, helpers:Boolean):List<DynamicTest> {
        val stream=javaClass.getResourceAsStream("/pandoc-types/$resource.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { line ->
            val r=Json.parseToJsonElement(line).jsonObject
            val kind=r.getValue("kind").jsonPrimitive.content
            val seed=r.getValue("seed").jsonPrimitive.long
            val size=r.getValue("size").jsonPrimitive.int
            val depth=r["depth"]?.jsonPrimitive?.int ?: 0
            DynamicTest.dynamicTest("$kind depth=$depth seed=$seed size=$size witness=${r["witness"] ?: "none"}") {
                val gen=if(helpers) helper(kind,depth) else default(kind)
                assertEquals(value(kind,r.getValue("expected")),gen.run(OriginalQuickCheck.Seed.fromInt(seed),size))
            }
        }
    }
    @TestFactory fun allOriginalArbitraryInstances() = cases("standalone-generators",false)
    @TestFactory fun originalPrivateGeneratorBoundaries() = cases("generator-helpers",true)
}
