package io.heapy.kotdoc.http

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.resources.PandocGregorian
import io.heapy.kotdoc.resources.PandocUtcTime
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream

class WebCookiesTest {
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun bytes(v: HttpBytes) = JsonArray(v.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun text(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun points(v: String) = JsonArray(v.codePoints().toArray().map(::JsonPrimitive))
    private fun utc(v: JsonElement) = v.jsonArray.let { PandocUtcTime(BigInteger(it[0].jsonPrimitive.content), BigInteger(it[1].jsonPrimitive.content)) }
    private fun view(v: PandocUtcTime) = JsonArray(listOf(JsonPrimitive(v.day.toString()), JsonPrimitive(v.dayPicoseconds.toString())))
    private fun pairs(v: JsonElement) = v.jsonArray.map { it.jsonArray.let { bytes(it[0]) to bytes(it[1]) } }
    private fun textPairs(v: JsonElement) = v.jsonArray.map { it.jsonArray.let { text(it[0]) to text(it[1]) } }
    private fun viewPairs(v: List<Pair<HttpBytes, HttpBytes>>) = JsonArray(v.map { (a,b) -> JsonArray(listOf(bytes(a), bytes(b))) })
    private fun viewTextPairs(v: List<Pair<String, String>>) = JsonArray(v.map { (a,b) -> JsonArray(listOf(points(a), points(b))) })
    private fun <T> optional(v: JsonObject, key: String, parse: (JsonElement) -> T): T? = v[key]?.takeUnless { it is JsonNull }?.let(parse)
    private fun cookie(v: JsonObject) = HttpSetCookie(
        name = bytes(v.getValue("name")), value = bytes(v.getValue("value")),
        path = optional(v, "path", ::bytes), expires = optional(v, "expires", ::utc),
        maxAgePicoseconds = optional(v, "maxAge") { BigInteger(it.jsonPrimitive.content) },
        domain = optional(v, "domain", ::bytes), httpOnly = v.getValue("httpOnly").jsonPrimitive.boolean,
        secure = v.getValue("secure").jsonPrimitive.boolean,
        sameSite = optional(v, "sameSite") { CookieSameSite.valueOf(it.jsonPrimitive.content) },
        partitioned = v.getValue("partitioned").jsonPrimitive.boolean,
    )
    private fun view(c: HttpSetCookie) = buildJsonObject {
        put("name", bytes(c.name)); put("value", bytes(c.value)); put("path", c.path?.let(::bytes) ?: JsonNull)
        put("expires", c.expires?.let(::view) ?: JsonNull); put("maxAge", c.maxAgePicoseconds?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        put("domain", c.domain?.let(::bytes) ?: JsonNull); put("httpOnly", c.httpOnly); put("secure", c.secure)
        put("sameSite", c.sameSite?.name?.let(::JsonPrimitive) ?: JsonNull); put("partitioned", c.partitioned)
    }
    private fun viewSet(c: HttpSetCookie) = buildJsonObject {
        put("cookie", view(c)); put("render", bytes(WebCookies.renderSetCookieBS(c))); put("show", c.show())
        put("reparsed", view(WebCookies.parseSetCookie(WebCookies.renderSetCookie(c))))
    }
    private fun observe(v: JsonObject): JsonElement = try {
        when (v.getValue("mode").jsonPrimitive.content) {
            "default" -> viewSet(WebCookies.defaultSetCookie).also { assertEquals(WebCookies.def, WebCookies.defaultSetCookie) }
            "parse" -> {
                val input = bytes(v.getValue("bytes")); val cookies = WebCookies.parseCookies(input); val text = WebCookies.parseCookiesText(input)
                buildJsonObject {
                    put("cookies", viewPairs(cookies)); put("text", viewTextPairs(text))
                    put("render", bytes(WebCookies.renderCookiesBS(cookies))); put("renderText", bytes(WebCookies.renderCookiesText(text)))
                }
            }
            "render" -> {
                val output = WebCookies.renderCookies(pairs(v.getValue("cookies")))
                assertEquals(output, WebCookies.renderCookiesBS(pairs(v.getValue("cookies"))))
                buildJsonObject { put("bytes", bytes(output)); put("parsed", viewPairs(WebCookies.parseCookies(output))) }
            }
            "render-text" -> {
                val output = WebCookies.renderCookiesText(textPairs(v.getValue("cookies")))
                buildJsonObject { put("bytes", bytes(output)); put("parsed", viewTextPairs(WebCookies.parseCookiesText(output))) }
            }
            "set" -> viewSet(v["cookie"]?.jsonObject?.let(::cookie) ?: WebCookies.parseSetCookie(bytes(v.getValue("bytes"))))
            "set-field" -> {
                val parsed = WebCookies.parseSetCookie(bytes(v.getValue("bytes")))
                val c = if (v["copy"]?.jsonPrimitive?.boolean == true) parsed.copy(name=HttpBytes.pack("copied")) else parsed
                when (v.getValue("field").jsonPrimitive.content) {
                    "name" -> bytes(c.name)
                    "maxAge" -> c.maxAgePicoseconds?.toString()?.let(::JsonPrimitive) ?: JsonNull
                    "eq-name-different" -> JsonPrimitive(c == c.copy(name=HttpBytes.pack("different")))
                    "eq-self" -> JsonPrimitive(c == c)
                    "clear-max-age" -> viewSet(c.copy(maxAge=lazyOf(null)))
                    "clear-max-age-then-eq" -> c.copy(maxAge=lazyOf(null)).let { JsonPrimitive(it == it) }
                    else -> error(v)
                }
            }
            "date" -> {
                val time = WebCookies.parseCookieExpires(bytes(v.getValue("bytes")))
                buildJsonObject {
                    put("time", time?.let(::view) ?: JsonNull); put("formatted", time?.let { bytes(WebCookies.formatCookieExpires(it)) } ?: JsonNull)
                    put("show", time?.let { JsonPrimitive(CookieDates.showUtc(it)) } ?: JsonNull)
                }
            }
            "format-date" -> utc(v.getValue("time")).let { time -> buildJsonObject {
                put("bytes", bytes(WebCookies.formatCookieExpires(time))); put("show", CookieDates.showUtc(time))
            } }
            "equal" -> JsonPrimitive(cookie(v.getValue("left").jsonObject) == cookie(v.getValue("right").jsonObject))
            else -> error(v)
        }
    } catch (e: CookieReadException) { buildJsonObject { put("error", e.message!!) } }

    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/web-cookie/data.jsonl.gz")!!).bufferedReader().use { reader ->
        reader.readLine()
        reader.lineSequence().mapIndexed { index, line ->
            val case = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${case.getValue("group").jsonPrimitive.content}") {
                val request = case.getValue("request").jsonObject
                assertEquals(case.getValue("expected"), observe(request))
                when (case["property"]?.jsonPrimitive?.content) {
                    "cookies" -> pairs(request.getValue("cookies")).let { assertEquals(it, WebCookies.parseCookies(WebCookies.renderCookies(it))) }
                    "text" -> textPairs(request.getValue("cookies")).let { assertEquals(it, WebCookies.parseCookiesText(WebCookies.renderCookiesText(it))) }
                    "set" -> cookie(request.getValue("cookie").jsonObject).let { c ->
                        val reparsed = WebCookies.parseSetCookie(WebCookies.renderSetCookie(c))
                        assertEquals(c, reparsed); assertEquals(c.hashCode(), reparsed.hashCode())
                    }
                }
            }
        }.toList()
    }

    @TestFactory
    fun originalLiteralTests(): List<DynamicTest> {
        fun expected() = listOf("a" to "a1", "b" to "b2", "c" to "c3").map { (a,b) -> HttpBytes.pack(a) to HttpBytes.pack(b) }
        return listOf(
            DynamicTest.dynamicTest("parseCookies") { assertEquals(expected(), WebCookies.parseCookies(HttpBytes.pack("a=a1;b=b2; c=c3"))) },
            DynamicTest.dynamicTest("parseQuotedCookies") { assertEquals(expected(), WebCookies.parseCookies(HttpBytes.pack("a=\"a1\";b=\"b2\"; c=\"c3\""))) },
            DynamicTest.dynamicTest("parseQuotedSetCookie") {
                val c = WebCookies.parseSetCookie(HttpBytes.pack("a=\"a1\""))
                assertEquals(HttpBytes.pack("a") to HttpBytes.pack("a1"), c.name to c.value)
            },
        ) + listOf(24 to 2024, 69 to 2069, 70 to 1970).map { (input, expected) ->
            DynamicTest.dynamicTest("year $input") {
                val c = WebCookies.parseSetCookie(HttpBytes.pack("foo=bar; Expires=Mon, 29-Jul-$input 04:52:08 GMT"))
                assertEquals(expected.toString(), PandocGregorian.showGregorian(c.expires!!.day).dropLast(6))
            }
        }
    }
}
