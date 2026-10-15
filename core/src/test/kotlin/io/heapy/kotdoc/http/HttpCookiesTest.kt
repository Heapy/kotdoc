package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.time.Instant
import java.util.zip.GZIPInputStream

class HttpCookiesTest {
    private fun bytes(v: JsonElement) = HttpBytes(v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
    private fun json(v: HttpBytes) = JsonArray(v.bytes().map { JsonPrimitive(it.toInt() and 255) })
    private fun utc(v: JsonElement) = v.jsonArray.let { PandocUtcTime(BigInteger(it[0].jsonPrimitive.content),BigInteger(it[1].jsonPrimitive.content)) }
    private fun json(v: PandocUtcTime) = JsonArray(listOf(JsonPrimitive(v.day.toString()),JsonPrimitive(v.dayPicoseconds.toString())))
    private fun cookie(v: JsonElement): HttpCookie = v.jsonObject.let {
        HttpCookie(bytes(it.getValue("name")),bytes(it.getValue("value")),utc(it.getValue("expiry")),bytes(it.getValue("domain")),bytes(it.getValue("path")),utc(it.getValue("creation")),utc(it.getValue("access")),
            it.getValue("persistent").jsonPrimitive.boolean,it.getValue("hostOnly").jsonPrimitive.boolean,it.getValue("secureOnly").jsonPrimitive.boolean,it.getValue("httpOnly").jsonPrimitive.boolean)
    }
    private fun json(c: HttpCookie) = buildJsonObject {
        put("name",json(c.name));put("value",json(c.value));put("expiry",json(c.expiryTime));put("domain",json(c.domain));put("path",json(c.path))
        put("creation",json(c.creationTime));put("access",json(c.lastAccessTime));put("persistent",c.persistent);put("hostOnly",c.hostOnly);put("secureOnly",c.secureOnly);put("httpOnly",c.httpOnly)
    }
    private fun jar(v: JsonElement) = HttpCookies.createCookieJar(v.jsonArray.map(::cookie))
    private fun json(j: HttpCookieJar) = JsonArray(HttpCookies.destroyCookieJar(j).map(::json))
    private fun headers(v: JsonElement) = v.jsonArray.map { it.jsonArray.let { HttpHeader(bytes(it[0]),bytes(it[1])) } }
    private fun jsonHeaders(hs: List<HttpHeader>) = JsonArray(hs.map { JsonArray(listOf(json(it.name),json(it.value))) })
    private fun bool(v: JsonObject,key: String) = v[key]?.jsonPrimitive?.boolean ?: false
    private fun request(v: JsonElement): HttpRequest = v.jsonObject.let {
        HttpRequest(host=it["host"]?.let(::bytes) ?: HttpBytes.pack("localhost"),path=it["path"]?.let(::bytes) ?: HttpBytes.pack("/"),secure=bool(it,"secure"),
            method=it["method"]?.let(::bytes) ?: HttpBytes.pack("GET"),requestBody=HttpRequestBody.Strict(it["body"]?.let(::bytes) ?: HttpBytes.empty),
            requestHeaders=it["headers"]?.let(::headers) ?: emptyList(),cookieJar=if("cookieJar" !in it) HttpCookieJar.empty else it["cookieJar"]?.takeUnless { v->v is JsonNull }?.let(::jar))
    }
    private fun json(r: HttpRequest): JsonObject = buildJsonObject {
        put("host",json(r.host));put("path",json(r.path));put("secure",r.secure);put("method",json(r.method));put("headers",jsonHeaders(r.requestHeaders))
        put("body",(r.requestBody as? HttpRequestBody.Strict)?.data?.let(::json) ?: JsonNull);put("cookieJar",r.cookieJar?.let(::json) ?: JsonNull)
    }
    private fun <A> json(r: HttpResponse<A>): JsonObject = buildJsonObject {
        put("status",JsonArray(listOf(JsonPrimitive(r.status.code),json(r.status.message))));put("version",JsonArray(listOf(JsonPrimitive(r.version.major),JsonPrimitive(r.version.minor))))
        put("headers",jsonHeaders(r.headers));put("cookieJar",json(r.cookieJar));put("request",json(HttpResponses.getOriginalRequest(r)));put("early",jsonHeaders(r.early))
    }
    private fun rawResponse(r: HttpRequest): HttpResponse<HttpBodyReader> {
        val conn=HttpDummyConnection(listOf(HttpBytes.pack("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc"))).connection
        return HttpResponses.getResponse(null,null,null,r,HttpManagedConnection(conn,{}))
    }
    private fun set(v: JsonObject): HttpSetCookie {
        val c=v["setCookie"]?.jsonObject ?: return WebCookies.parseSetCookie(bytes(v.getValue("set")))
        fun optional(key: String)=c[key]?.takeUnless {it is JsonNull}
        return HttpSetCookie(name=bytes(c.getValue("name")),value=bytes(c.getValue("value")),path=optional("path")?.let(::bytes),domain=optional("domain")?.let(::bytes),
            expires=optional("expires")?.let(::utc),maxAgePicoseconds=optional("maxAge")?.jsonPrimitive?.content?.let(::BigInteger),
            httpOnly=bool(c,"httpOnly"),secure=bool(c,"secure"),sameSite=optional("sameSite")?.jsonPrimitive?.content?.let(CookieSameSite::valueOf),partitioned=bool(c,"partitioned"))
    }
    private fun observe(v: JsonObject): JsonElement = try {
        when(v.getValue("mode").jsonPrimitive.content) {
            "ip" -> bytes(v.getValue("bytes")).latin1().let { text->buildJsonObject {
                put("v4",CookieIp.ipv4(text)?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull)
                put("v6",CookieIp.ipv6(text)?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull)
            } }
            "origin" -> JsonPrimitive(HttpCookies.isPotentiallyTrustworthyOrigin(bool(v,"secure"),bytes(v.getValue("host"))))
            "is-ip" -> JsonPrimitive(HttpCookies.isIpAddress(bytes(v.getValue("bytes"))))
            "domain" -> JsonPrimitive(HttpCookies.domainMatches(bytes(v.getValue("host")),bytes(v.getValue("domain"))))
            "path" -> JsonPrimitive(HttpCookies.pathMatches(bytes(v.getValue("request")),bytes(v.getValue("cookie"))))
            "default-path" -> json(HttpCookies.defaultPath(request(v.getValue("request"))))
            "compare" -> {
                val a=cookie(v.getValue("left"));val b=cookie(v.getValue("right"));val order=HttpCookies.compareCookies(a,b)
                buildJsonObject {put("equal",HttpCookies.equalCookie(a,b));put("equiv",HttpCookies.equivCookie(a,b));put("compare",if(order<0)"LT" else if(order>0)"GT" else "EQ")}
            }
            "jar-pair" -> {
                val a=jar(v.getValue("left"));val b=jar(v.getValue("right"));val combined=a+b
                buildJsonObject {put("equal",HttpCookies.equalCookieJar(a,b));put("equiv",HttpCookies.equivCookieJar(a,b));put("combined",json(combined));put("show",combined.show())}
            }
            "remove" -> HttpCookies.removeExistingCookieFromCookieJar(cookie(v.getValue("cookie")),jar(v.getValue("jar"))).let { (old,result)->buildJsonObject {put("old",old?.let(::json) ?: JsonNull);put("jar",json(result))} }
            "insert" -> json(HttpCookies.insertCheckedCookie(cookie(v.getValue("cookie")),jar(v.getValue("jar")),bool(v,"http")))
            "evict" -> json(HttpCookies.evictExpiredCookies(jar(v.getValue("jar")),utc(v.getValue("now"))))
            "generate" -> HttpCookies.generateCookie(set(v),request(v.getValue("request")),utc(v.getValue("now")),bool(v,"http"))?.let(::json) ?: JsonNull
            "generated-field" -> {
                val c=HttpCookies.generateCookie(set(v),request(v.getValue("request")),utc(v.getValue("now")),bool(v,"http"))
                when(v.getValue("field").jsonPrimitive.content) {
                    "exists"->JsonPrimitive(c!=null);"name"->c?.name?.let(::json) ?: JsonNull
                    "persistent"->c?.let {JsonPrimitive(it.persistent)} ?: JsonNull;"expiry"->c?.expiryTime?.let(::json) ?: JsonNull
                    "equiv-self"->c?.let {JsonPrimitive(HttpCookies.equivCookie(it,it))} ?: JsonNull
                    "equal-self"->c?.let {JsonPrimitive(HttpCookies.equalCookie(it,it))} ?: JsonNull
                    "show"->c?.show()?.let(::JsonPrimitive) ?: JsonNull;else->error(v)
                }
            }
            "set-demand" -> set(v).let {c->buildJsonObject {put("present",c.hasMaxAge);put("equalAbsent",c==c.copy(maxAge=lazyOf(null)))} }
            "receive", "receive-compute", "receive-evict" -> {
                val r=request(v.getValue("request"));val now=utc(v.getValue("now"))
                val received=HttpCookies.receiveSetCookie(set(v),r,now,bool(v,"http"),jar(v.getValue("jar")))
                when(v.getValue("mode").jsonPrimitive.content) {
                    "receive"->json(received);"receive-evict"->json(HttpCookies.evictExpiredCookies(received,now))
                    else->HttpCookies.computeCookieString(r,received,now,bool(v,"http")).let { (header,result)->buildJsonObject {
                        put("header",json(header));put("names",JsonArray(result.cookies.map {json(it.name)}));put("persistent",JsonArray(result.cookies.map {JsonPrimitive(it.persistent)}))
                    } }
                }
            }
            "compute" -> HttpCookies.computeCookieString(request(v.getValue("request")),jar(v.getValue("jar")),utc(v.getValue("now")),bool(v,"http")).let { (header,result)->buildJsonObject {put("header",json(header));put("jar",json(result))} }
            "insert-request" -> HttpCookies.insertCookiesIntoRequest(request(v.getValue("request")),jar(v.getValue("jar")),utc(v.getValue("now"))).let { (r,result)->buildJsonObject {put("request",json(r));put("jar",json(result))} }
            "response" -> {
                val r=request(v.getValue("request"));val before=rawResponse(r).copy(headers=headers(v.getValue("headers")),cookieJar=jar(v.getValue("responseJar")),early=listOf(HttpHeader("Link","early")))
                val (cookies,after)=HttpCookies.updateCookieJar(before,r,utc(v.getValue("now")),jar(v.getValue("jar")))
                assertSame(before.close,after.close);assertSame(before.body,after.body)
                val lbs=HttpResponses.lbsResponse(after)
                buildJsonObject {put("jar",json(cookies));put("response",json(lbs));put("body",JsonArray(lbs.body.map(::json)))}
            }
            "get-response" -> {
                val response=HttpResponses.lbsResponse(rawResponse(request(v.getValue("request"))).copy(early=listOf(HttpHeader("Link","early"))))
                buildJsonObject {put("response",json(response));put("body",JsonArray(response.body.map(::json)))}
            }
            else->error(v)
        }
    } catch(e: CookieReadException) {buildJsonObject {put("error",e.message!!)}}
      catch(e: CookieBytesException) {buildJsonObject {put("error",e.message!!)}}

    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(javaClass.getResourceAsStream("/http-cookie/data.jsonl.gz")!!).bufferedReader().use {r->
        r.readLine();r.lineSequence().mapIndexed {index,line->val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${c.getValue("group").jsonPrimitive.content}") {assertEquals(c.getValue("expected"),observe(c.getValue("request").jsonObject))}
        }.toList()
    }
    private fun now(): PandocUtcTime = Instant.now().let {PandocUtcTime.fromPosixPicoseconds(BigInteger.valueOf(it.epochSecond)*BigInteger.TEN.pow(12)+BigInteger.valueOf(it.nano.toLong())*BigInteger.valueOf(1000))}
    @TestFactory
    fun originalCookieSpec(): List<DynamicTest> {
        val units=mutableListOf<DynamicTest>()
        fun unit(name: String,action: ()->Unit) {units.add(DynamicTest.dynamicTest(name,action))}
        unit("cookie equality - case insensitive Eq") {
            val now=now();val a=HttpCookie(HttpBytes.pack("test"),HttpBytes.pack("value"),now,HttpBytes.pack("doMain.Org"),HttpBytes.pack("/"),now,now,false,false,false,false)
            assertTrue(HttpCookies.equivCookie(a.copy(domain=HttpBytes.pack("DOMAIn.ORg")),a))
        }
        unit("domainMatches - case insensitive") {
            assertTrue(HttpCookies.domainMatches(HttpBytes.pack("www.org"),HttpBytes.pack("www.org")))
            assertTrue(HttpCookies.domainMatches(HttpBytes.pack("wWw.OrG"),HttpBytes.pack("Www.oRG")))
            assertFalse(HttpCookies.domainMatches(HttpBytes.pack("wxw.OrG"),HttpBytes.pack("Www.oRG")))
        }
        unit("domainMatches - case insensitive, partial") {
            assertFalse(HttpCookies.domainMatches(HttpBytes.pack("www.org"),HttpBytes.pack("xxx.www.org")))
            assertTrue(HttpCookies.domainMatches(HttpBytes.pack("xxx.www.org"),HttpBytes.pack("WWW.ORG")))
        }
        fun later(t: PandocUtcTime)=PandocUtcTime.fromPosixPicoseconds(t.posixPicoseconds()+BigInteger.valueOf(60)*BigInteger.TEN.pow(12))
        val modifications: List<Triple<String,(HttpCookie)->HttpCookie,Boolean>> = listOf(
            Triple("cookie_name",{it.copy(name=HttpBytes.pack("othername"))},true),Triple("cookie_value",{it.copy(value=HttpBytes.pack("othervalue"))},false),
            Triple("cookie_expiry_time",{it.copy(expiryTime=lazyOf(later(it.expiryTime)))},false),Triple("cookie_domain",{it.copy(domain=it.domain+HttpBytes.pack(".com"))},true),
            Triple("cookie_path",{it.copy(path=it.path+HttpBytes.pack("/sub"))},true),Triple("cookie_creation_time",{it.copy(creationTime=later(it.creationTime))},false),
            Triple("cookie_last_access_time",{it.copy(lastAccessTime=later(it.lastAccessTime))},false),Triple("cookie_persistent",{it.copy(persistent=!it.persistent)},false),
            Triple("cookie_host_only",{it.copy(hostOnly=!it.hostOnly)},false),Triple("cookie_secure_only",{it.copy(secureOnly=!it.secureOnly)},false),Triple("cookie_http_only",{it.copy(httpOnly=!it.httpOnly)},false))
        for((name,modify,equiv) in modifications) unit(name) {
            val raw="somename=somevalue.v=1.k=1.d=1590419679.t=u.l=s.u=8b2734ae-9dd1-11ea-bd7f-3bcf5b8d5d2a.r=795e71b5; Path=/access; Domain=example.com; HttpOnly; Secure"
            val c=HttpCookies.generateCookie(WebCookies.parseSetCookie(HttpBytes.pack(raw)),HttpRequests.parseRequest("http://www.example.com/path"),now(),true)!!
            assertFalse(HttpCookies.equalCookie(c,modify(c)));if(equiv)assertFalse(HttpCookies.equivCookie(c,modify(c)))
        }
        unit("isPotentiallyTrustworthyOrigin") {
            assertTrue(HttpCookies.isPotentiallyTrustworthyOrigin(true,HttpBytes.empty))
            val untrusty=listOf("example","example.","example.com","foolocalhost","1.1.1.1","::1","[::2]")
            val trusty=listOf("127.0.0.1","127.0.0.2","127.127.127.127","[::1]","[0:0:0:0:0:0:0:1]","localhost","localhost.","a.b.c.localhost","a.b.c.localhost.")
            assertFalse(untrusty.any {HttpCookies.isPotentiallyTrustworthyOrigin(false,HttpBytes.pack(it))})
            assertTrue(trusty.all {HttpCookies.isPotentiallyTrustworthyOrigin(false,HttpBytes.pack(it))})
        }
        return units
    }
    @Test
    fun jarOwnsItsImmutableListSnapshot() {
        val data=mutableListOf<HttpCookie>();val t=now()
        val cookie=HttpCookie(HttpBytes.pack("a"),HttpBytes.pack("v"),t,HttpBytes.pack("domain"),HttpBytes.pack("/"),t,t,false,false,false,false)
        data.add(cookie);val jar=HttpCookies.createCookieJar(data);data.clear()
        assertSame(cookie,HttpCookies.destroyCookieJar(jar).single())
        assertThrows(UnsupportedOperationException::class.java) {(jar.cookies as MutableList).clear()}
    }
}
