package io.heapy.kotdoc.uri

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Protocol transport. All expected function results come from the original library. */
class NetworkURITest {
    private fun raw(v: JsonElement)=CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun rv(s: CharacterString)=JsonArray(s.points().map(::JsonPrimitive))
    private fun av(a: URIAuth)=JsonArray(listOf(rv(a.userInfo),rv(a.regName),rv(a.port)))
    private fun uv(u: URI)=JsonArray(listOf(rv(u.scheme),u.authority?.let(::av) ?: JsonNull,rv(u.path),rv(u.query),rv(u.fragment)))
    private fun auth(v: JsonElement)=v.jsonArray.let { URIAuth(raw(it[0]),raw(it[1]),raw(it[2])) }
    private fun uri(v: JsonElement)=v.jsonArray.let { URI(raw(it[0]),it[1].takeUnless { a->a==JsonNull }?.let(::auth),raw(it[2]),raw(it[3]),raw(it[4])) }
    private fun parse(kind: Int,s: CharacterString)=when(kind) { 0->NetworkURI.parseURI(s);1->NetworkURI.parseURIReference(s);2->NetworkURI.parseRelativeReference(s);3->NetworkURI.parseAbsoluteURI(s);else->error(kind) }
    private fun flags(c: Int)=JsonArray(listOf(NetworkURI.isReserved(c),NetworkURI.isUnreserved(c),NetworkURI.isAllowedInURI(c),NetworkURI.isUnescapedInURI(c),NetworkURI.isUnescapedInURIComponent(c)).map(::JsonPrimitive))
    private fun normalize(kind: Int,s: CharacterString)=when(kind) { 0->NetworkURI.normalizeCase(s);1->NetworkURI.normalizeEscape(s);2->NetworkURI.normalizePathSegments(s);else->error(kind) }
    private fun response(v: JsonElement): JsonElement {
        val x=v.jsonArray
        fun n(i: Int)=x[i].jsonPrimitive.int
        return when(x[0].jsonPrimitive.content) {
            "parse"->parse(n(1),raw(x[2]))?.let(::uv) ?: JsonNull
            "valid"->JsonPrimitive(when(n(1)) { in 0..3->parse(n(1),raw(x[2]))!=null;4->NetworkURI.isIPv4address(raw(x[2]));5->NetworkURI.isIPv6address(raw(x[2]));else->error(v) })
            "absolute"->JsonPrimitive(NetworkURI.uriIsAbsolute(uri(x[1])))
            "relative"->JsonPrimitive(NetworkURI.uriIsRelative(uri(x[1])))
            "resolve"->{ val a=uri(x[2]);val b=uri(x[3]);uv(when(n(1)) { 0->NetworkURI.relativeTo(a,b);1->NetworkURI.nonStrictRelativeTo(a,b);2->NetworkURI.relativeFrom(a,b);else->error(v) }) }
            "render"->rv(NetworkURI.uriToString({ check(x[2]!=JsonNull);raw(x[2]) },uri(x[1]),raw(x[3])))
            "render-auth"->rv(NetworkURI.uriAuthToString({ check(x[2]!=JsonNull);raw(x[2]) },x[1].takeUnless { it==JsonNull }?.let(::auth),raw(x[3])))
            "escape"->{ val keep=x[1].jsonArray.map { it.jsonPrimitive.int }.toSet();rv(NetworkURI.escapeURIString({ it in keep },raw(x[2]))) }
            "unescape"->rv(NetworkURI.unEscapeString(raw(x[1])))
            "normalize"->rv(normalize(n(1),raw(x[2])))
            "segments"->JsonArray(NetworkURI.pathSegments(uri(x[1])).map(::rv))
            "rectify"->uv(NetworkURI.rectify(uri(x[1])))
            "rectify-auth"->av(NetworkURI.rectifyAuth(auth(x[1])))
            "components"->{ val u=uri(x[1]);JsonArray(listOf(NetworkURI.scheme(u),NetworkURI.authority(u),NetworkURI.path(u),NetworkURI.query(u),NetworkURI.fragment(u)).map(::rv)) }
            "char"->flags(n(1))
            "unicode"->{
                val digest=MessageDigest.getInstance("SHA-256");val prefix=ByteArray(4)
                repeat(n(2)) { offset->val cp=n(1)+offset
                    val escaped=NetworkURI.escapeURIChar({ false },cp)
                    val output=JsonArray(listOf(rv(escaped),rv(NetworkURI.unEscapeString(escaped)),rv(NetworkURI.normalizeCase(CharacterString(intArrayOf(120,58,37,cp,cp)))),flags(cp)))
                    val bytes=output.toString().toByteArray(Charsets.UTF_8);val size=bytes.size
                    prefix[0]=(size ushr 24).toByte();prefix[1]=(size ushr 16).toByte();prefix[2]=(size ushr 8).toByte();prefix[3]=size.toByte()
                    digest.update(prefix);digest.update(bytes)
                }
                JsonPrimitive(digest.digest().toHexString())
            }
            else->error(v)
        }
    }
    @TestFactory fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/uri/data.jsonl.gz"))).bufferedReader().use { reader->
        reader.readLine();reader.lineSequence().mapIndexed { index,line->var transport: String?=line
            DynamicTest.dynamicTest("original URI $index") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),response(c.getValue("request")),"case $index: ${c.getValue("request")}")
            }
        }.toList()
    }
}
