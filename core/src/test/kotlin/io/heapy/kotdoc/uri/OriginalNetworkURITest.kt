// Translated from network-uri 2.6.4.2 tests/uri001.hs, (c) 2004 Graham Klyne.
// BSD-3-Clause: licenses/Network-uri-Graham-Klyne-BSD-3-Clause.txt.
package io.heapy.kotdoc.uri

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.random.Random

/** All active source assertions; their literals are extracted without an oracle.
 * The property predicates are unchanged. Kotlin's deterministic generator is
 * supplemented by every scalar Char; this is not a port of QuickCheck's RNG. */
class OriginalNetworkURITest {
    private fun auth(v: JsonElement) = v.jsonArray.let { URIAuth(it[0].jsonPrimitive.content,it[1].jsonPrimitive.content,it[2].jsonPrimitive.content) }
    private fun uri(v: JsonElement) = v.jsonArray.let { URI(it[0].jsonPrimitive.content,it[1].takeUnless { a->a==JsonNull }?.let(::auth),it[2].jsonPrimitive.content,it[3].jsonPrimitive.content,it[4].jsonPrimitive.content) }
    private fun raw(s: String) = Chars.fromString(s)
    private fun text(s: CharacterString) = Chars.build(s)
    private fun parse(fn: String,s: String): URI? = when(fn) {
        "parseURI"->NetworkURI.parseURI(s)
        "parseURIReference"->NetworkURI.parseURIReference(s)
        "parseRelativeReference"->NetworkURI.parseRelativeReference(s)
        "parseAbsoluteURI"->NetworkURI.parseAbsoluteURI(s)
        else->error(fn)
    }
    private fun normalize(fn: String,s: CharacterString) = when(fn) {
        "normalizeCase"->NetworkURI.normalizeCase(s)
        "normalizeEscape"->NetworkURI.normalizeEscape(s)
        "normalizePathSegments"->NetworkURI.normalizePathSegments(s)
        else->error(fn)
    }
    private fun testRelSplit(label: String,base: String,uabs: String,urel: String) {
        val puabs=NetworkURI.parseURIReference(uabs);val pubas=NetworkURI.parseURIReference(base)
        val result=when { puabs==null->"Invalid URI: $urel";pubas==null->"Invalid URI: $uabs";else->NetworkURI.relativeFrom(puabs,pubas).toString() }
        assertEquals(urel,result,label)
    }
    private fun testRelJoin(label: String,base: String,urel: String,uabs: String) {
        val purel=NetworkURI.parseURIReference(urel);val pubas=NetworkURI.parseURIReference(base)
        val result=when { purel==null->"Invalid URI: $urel";pubas==null->"Invalid URI: $uabs";else->NetworkURI.relativeTo(purel,pubas).toString() }
        assertEquals(uabs,result,label)
    }
    private fun run(c: JsonObject) {
        val kind=c.getValue("kind").jsonPrimitive.content;val x=c.getValue("args").jsonArray;val expected=c.getValue("expected")
        fun s(i: Int)=x[i].jsonPrimitive.content
        fun eq(value: String)=assertEquals(expected.jsonPrimitive.content,value)
        when(kind) {
            "testURIRef"->{
                val type=expected.jsonPrimitive.content;val input=raw(s(0))
                assertEquals(type!="InvRf",NetworkURI.isURIReference(input),"test_isURIReference:${s(0)}")
                assertEquals(type=="RelRf",NetworkURI.isRelativeReference(input),"test_isRelativeReference:${s(0)}")
                assertEquals(type=="AbsId",NetworkURI.isAbsoluteURI(input),"test_isAbsoluteURI:${s(0)}")
            }
            "testURIRefComponents"->assertEquals(expected.takeUnless { it==JsonNull }?.let(::uri),NetworkURI.parseURIReference(s(0)))
            "authority"->assertEquals(auth(expected),NetworkURI.parseURI(s(0))?.authority)
            "testRelative","testRelJoin","testRelSplit"->{
                val label=c.getValue("label").jsonPrimitive.content
                when(kind) {
                    "testRelJoin"->testRelJoin(label,s(0),s(1),s(2))
                    "testRelSplit"->testRelSplit(label,s(0),s(1),s(2))
                    else->{testRelSplit(label+"(rel)",s(0),s(1),s(2));testRelJoin(label+"(abs)",s(0),s(2),s(1))}
                }
            }
            "normalization"->eq(text(x[1].jsonArray.fold(raw(s(0))) { value,fn->normalize(fn.jsonPrimitive.content,value) }))
            "render"->{val u=uri(x[0]);eq(if(x[1].jsonPrimitive.boolean)text(NetworkURI.uriToString({it},u)) else u.toString())}
            "escapeURIString"->eq(text(NetworkURI.escapeURIString(if(s(1)=="component")NetworkURI::isUnescapedInURIComponent else NetworkURI::isUnescapedInURI,raw(s(0)))))
            "unEscapeString"->eq(text(NetworkURI.unEscapeString(raw(s(0)))))
            "assertUnescapeEscapeInverse"->assertEquals(raw(s(0)),NetworkURI.unEscapeString(NetworkURI.escapeURIString(NetworkURI::isUnescapedInURIComponent,raw(s(0)))))
            "relativeTo"->{
                val ref=checkNotNull(NetworkURI.parseURIReference(s(0)));val base=checkNotNull(NetworkURI.parseURIReference(s(1)))
                eq((if(x[2].jsonPrimitive.boolean)NetworkURI.nonStrictRelativeTo(ref,base) else NetworkURI.relativeTo(ref,base)).toString())
            }
            "alternative"->{
                val fn=s(0)
                if(fn.startsWith("parse"))eq(parse(fn,s(1))?.let { "Just $it" } ?: "Nothing")
                else assertEquals(expected.jsonPrimitive.boolean,when(fn) {
                    "isURI"->NetworkURI.isURI(raw(s(1)));"isURIReference"->NetworkURI.isURIReference(raw(s(1)))
                    "isRelativeReference"->NetworkURI.isRelativeReference(raw(s(1)));"isAbsoluteURI"->NetworkURI.isAbsoluteURI(raw(s(1)))
                    else->error(fn)
                })
            }
            "testIsAbsolute"->assertTrue(NetworkURI.uriIsAbsolute(checkNotNull(NetworkURI.parseURIReference(s(0)))))
            "testIsRelative"->assertTrue(NetworkURI.uriIsRelative(checkNotNull(NetworkURI.parseURIReference(s(0)))))
            "pathSegments"->{
                val u=checkNotNull(parse(s(0),s(1))) { "URI failed to parse" }
                val stripped=text(u.path).removePrefix("/").removeSuffix("/")
                assertEquals(stripped,NetworkURI.pathSegments(u).joinToString("/") { text(it) })
            }
            "rectify"->eq(NetworkURI.rectify(uri(x[0])).toString())
            "rectifyAuth"->eq(text(NetworkURI.uriAuthToString({it},NetworkURI.rectifyAuth(auth(x[0])))))
            else->error(kind)
        }
    }
    @TestFactory fun originalUnits(): List<DynamicTest> {
        val data=Json.parseToJsonElement(checkNotNull(javaClass.getResource("/uri/upstream-units.json")).readText()).jsonObject
        val units=data.getValue("units").jsonArray;assertEquals(339,units.size)
        return units.mapIndexed { index,c->DynamicTest.dynamicTest("$index: ${c.jsonObject.getValue("name").jsonPrimitive.content}") { run(c.jsonObject) } }
    }
    private fun validUnicodePoint(c: Int) = c !in 0xfdd0..0xfdef && c and 0xfffe != 0xfffe
    private fun scalar(c: Int) = c !in 0xd800..0xdfff
    private fun loop(s: IntArray) {
        if(s.all(::validUnicodePoint)) {
            val original=CharacterString(s)
            val escaped=NetworkURI.escapeURIString({false},original)
            assertEquals(original,NetworkURI.unEscapeString(escaped))
        }
    }
    @Test fun propEscapeUnEscapeLoop() {
        val random=Random(314159)
        repeat(10000) { size->
            val input=IntArray(random.nextInt(size%101+1)) { var c: Int;do { c=random.nextInt(0x110000) } while(!scalar(c));c }
            loop(input)
        }
        // Single-character witnesses exhaust QuickCheck Char's scalar domain.
        for(c in 0..0x10ffff)if(scalar(c))loop(intArrayOf(c))
    }
    @Test fun propEscapeUnEscapeLoopHiChars() {
        // Source property maps c' to c' .|. 0xff00 before its validity premise.
        for(original in 0..0x10ffff)if(scalar(original))loop(intArrayOf(original or 0xff00))
    }
}
