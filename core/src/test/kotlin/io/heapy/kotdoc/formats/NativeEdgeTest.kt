package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

class NativeEdgeTest {
    private fun cases(): JsonObject = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/native/edge-cases.json")).readText()).jsonObject

    @TestFactory
    fun `reader matches pinned Haskell Read lexical boundaries`(): List<DynamicTest> = cases().getValue("reader").jsonArray.map { entry ->
        val value = entry.jsonObject
        val input = value.getValue("input").jsonPrimitive.content
        DynamicTest.dynamicTest(input) {
            if (value.getValue("accepted").jsonPrimitive.boolean) assertEquals(PandocJson.decode(value.getValue("json")), NativeReader.read(input))
            else assertFails { NativeReader.read(input) }
        }
    }

    @TestFactory
    fun `writer matches GHC numeric escaping and ribbon width boundaries`(): List<DynamicTest> = cases().getValue("writer").jsonArray.map { entry ->
        val value = entry.jsonObject
        val columns = value.getValue("columns").jsonPrimitive.int
        val standalone = value.getValue("standalone").jsonPrimitive.boolean
        DynamicTest.dynamicTest("${value.getValue("name")} columns=$columns standalone=$standalone") {
            val doc = NativeReader.read(value.getValue("input").jsonPrimitive.content)
            assertEquals(value.getValue("expected").jsonPrimitive.content, NativeWriter.write(doc, columns, standalone))
        }
    }

    @Test
    fun `Aeson special Double encoding and signed zero differ from Native parsing`() {
        for ((number, json) in listOf(Double.NaN to "null", Double.POSITIVE_INFINITY to "\"+inf\"", Double.NEGATIVE_INFINITY to "\"-inf\"")) {
            val encoded = PandocJson.encodeColWidth(ColWidth.Width(number))
            assertEquals(Json.parseToJsonElement("""{"t":"ColWidth","c":$json}"""), encoded)
            val decoded = (PandocJson.decodeColWidth(encoded) as ColWidth.Width).value
            if (number.isNaN()) assertTrue(decoded.isNaN()) else assertEquals(number, decoded)
        }
        val width = PandocJson.decodeColWidth(Json.parseToJsonElement("""{"t":"ColWidth","c":-0.0}""")) as ColWidth.Width
        assertEquals(0.0.toRawBits(), width.value.toRawBits())
        val native = "Table (\"\",[],[]) (Caption Nothing []) [(AlignDefault,ColWidth (-0.0))] (TableHead (\"\",[],[]) []) [] (TableFoot (\"\",[],[]) [])"
        val table = NativeReader.read(native).blocks.single() as Block.Table
        assertEquals((-0.0).toRawBits(), (table.columns.single().width as ColWidth.Width).value.toRawBits())
    }
}
