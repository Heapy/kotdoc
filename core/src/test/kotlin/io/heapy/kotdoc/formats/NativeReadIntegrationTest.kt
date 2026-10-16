package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.AstShow
import io.heapy.kotdoc.ast.PandocJson
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeReadIntegrationTest {
    @TestFactory fun originalNativeReadIntegration():List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/native/read-integration.jsonl.gz")))
            .bufferedReader().use { it.readLines() }.map { line ->
                val row=Json.parseToJsonElement(line).jsonObject
                DynamicTest.dynamicTest(row.getValue("name").jsonPrimitive.content) {
                    val input=row.getValue("input").jsonPrimitive.content
                    if(row.getValue("returncode").jsonPrimitive.int==0) {
                        val expected=PandocJson.decode(Json.parseToJsonElement(row.getValue("stdout").jsonPrimitive.content))
                        // Canonical structural representation handles deep values without recursive data-class equals.
                        assertEquals(AstShow.show(expected),AstShow.show(NativeReader.read(input)))
                    } else {
                        val error=assertFailsWith<IllegalArgumentException> {NativeReader.read(input)}
                        assertEquals(row.getValue("stderr").jsonPrimitive.content.trimEnd(),error.message)
                    }
                }
            }
}
