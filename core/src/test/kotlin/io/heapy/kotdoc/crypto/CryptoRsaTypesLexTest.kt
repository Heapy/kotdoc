package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
class CryptoRsaTypesLexTest {
    @TestFactory fun originalLexicalDemandAndConversions():List<DynamicTest> {
        val rows=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-rsa-types/LexObservations.json.gz")!!).bufferedReader().use{it.readText()}).jsonArray
        check(rows.size==6900)
        return rows.mapIndexed{i,row->val r=row.jsonObject;DynamicTest.dynamicTest("original lexP numeric consumer $i"){assertEquals(r.getValue("expected"),RsaTypesLexProof.observe(r.getValue("request").jsonObject))}}
    }
}
