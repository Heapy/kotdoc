package io.heapy.kotdoc.http

import com.sun.jna.Platform
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class HttpDigestNativeTest {
    private val supported get()=Platform.isMac() || Platform.isLinux()
    private data class Received(val headers: String,val body: ByteArray)
    private fun Socket.receive(): Received {
        soTimeout=5000
        val input=getInputStream();val headers=StringBuilder()
        while(!headers.endsWith("\r\n\r\n")) {val b=input.read();check(b>=0) {"EOF before HTTP headers"};headers.append(b.toChar());check(headers.length<65536)}
        val length=headers.lines().firstOrNull {it.startsWith("Content-Length:",ignoreCase=true)}?.substringAfter(':')?.trim()?.toInt() ?: 0
        return Received(headers.toString(),input.readNBytes(length))
    }
    private fun Socket.send(status: Int,headers: String="") {
        getOutputStream().write("HTTP/1.1 $status Test\r\nContent-Length: 0\r\n$headers\r\n".toByteArray())
        getOutputStream().flush()
    }
    private fun manager()=HttpManagers.newPosixManager(HttpManagers.defaultPosixManagerSettings().copy(
        proxyInsecure=HttpProxyOverrides.fromRequest,proxySecure=HttpProxyOverrides.fromRequest))
    private fun <A> server(handler: (ServerSocket)->A,client: (Int)->Unit): A = ServerSocket(0,5,InetAddress.getByName("127.0.0.1")).use {listener->
        listener.soTimeout=5000;val task=FutureTask {handler(listener)};val thread=Thread.ofVirtual().start(task)
        try {client(listener.localPort);task.get(10,TimeUnit.SECONDS)}
        finally {listener.close();thread.join(5000);assertFalse(thread.isAlive)}
    }
    private fun request(port: Int)=HttpRequests.parseUrlThrow("http://127.0.0.1:$port/digest-auth/qop/user/passwd")
        .copy(responseTimeout=HttpResponseTimeout.Micro(5_000_000))
    private fun expectedAuthorization(): String {
        val row=GZIPInputStream(javaClass.getResourceAsStream("/http-tls/data.jsonl.gz")!!).bufferedReader().use {r->
            r.lineSequence().drop(1).map {Json.parseToJsonElement(it).jsonObject}
                .first {it.getValue("group").jsonPrimitive.content=="original-Spec-digest-authentication-local-challenge"}
        }
        val headers=row.getValue("expected").jsonObject.getValue("result").jsonObject.getValue("request").jsonObject.getValue("headers").jsonArray
        fun bytes(v: JsonElement)=v.jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray().toString(Charsets.ISO_8859_1)
        return headers.first {bytes(it.jsonArray[0])=="Authorization"}.jsonArray[1].let(::bytes)
    }
    @Test fun originalDigestAuthenticationUsesRealProbeThenAuthenticates() {
        if(!supported) return
        val captured=server({listener->listener.accept().use {peer->
            val first=peer.receive();peer.send(401,"WWW-Authenticate: Digest realm=\"test\",nonce=\"nonce\",qop=\"auth\"\r\nSet-Cookie: challenge=one; Path=/\r\n")
            val second=peer.receive();peer.send(200,"Connection: close\r\n");listOf(first,second)
        }}) {port->
            val manager=manager()
            val authenticated=HttpDigestAuth.applyDigestAuth(HttpBytes.pack("user"),HttpBytes.pack("passwd"),request(port),manager).invoke()
            assertEquals(200L,HttpExecution.system.httpNoBody(authenticated,manager).status.code)
        }
        assertTrue(captured[0].headers.startsWith("GET /digest-auth/qop/user/passwd HTTP/1.1\r\n"))
        assertFalse("Authorization:" in captured[0].headers)
        assertTrue("Authorization: ${expectedAuthorization()}\r\n" in captured[1].headers)
        assertTrue("Cookie: challenge=one\r\n" in captured[1].headers)
    }
    @Test fun originalIncorrectDigestAuthenticationThrowsUnexpectedStatus() {
        if(!supported) return
        server({listener->listener.accept().use {peer->peer.receive();peer.send(200,"Connection: close\r\n")}}) {port->
            val inner=HttpDigestAuth.applyDigestAuth(HttpBytes.pack("user"),HttpBytes.pack("passwd"),request(port),manager())
            val error=assertThrows(DigestAuthException::class.java) {inner()}
            assertEquals(DigestAuthExceptionDetails.UnexpectedStatusCode,error.details)
            assertTrue(error.display().startsWith("Unable to submit digest credentials due to: received unexpected status code."))
        }
    }
    @Test fun missingNonceValidationIsDeferredUntilAfterTheRealProbe() {
        if(!supported) return
        server({listener->listener.accept().use {peer->peer.receive();peer.send(401,"WWW-Authenticate: Digest realm=\"test\"\r\nConnection: close\r\n")}}) {port->
            var checkerCalls=0
            val initial=request(port).copy(checkResponse={_,_->checkerCalls++;error("custom checker must be replaced")})
            val inner=HttpDigestAuth.applyDigestAuth(HttpBytes.pack("user"),HttpBytes.pack("passwd"),initial,manager())
            assertEquals(0,checkerCalls)
            assertEquals(DigestAuthExceptionDetails.MissingNonce,assertThrows(DigestAuthException::class.java) {inner()}.details)
        }
    }
    @Test fun probeSendsOriginalMethodAndBodyAndReturnedRequestRetainsThem() {
        if(!supported) return
        val captured=server({listener->listener.accept().use {peer->
            val first=peer.receive();peer.send(401,"WWW-Authenticate: Digest realm=\"test\",nonce=\"nonce\",qop=\"auth\"\r\n")
            val second=peer.receive();peer.send(200,"Connection: close\r\n");listOf(first,second)
        }}) {port->
            val manager=manager();val initial=request(port).copy(method=HttpBytes.pack("POST"),requestBody=HttpRequestBody.Strict(HttpBytes.pack("payload")))
            val authenticated=HttpDigestAuth.applyDigestAuth(HttpBytes.pack("user"),HttpBytes.pack("passwd"),initial,manager).invoke()
            assertSame(initial.requestBody,authenticated.requestBody)
            assertEquals(200L,HttpExecution.system.httpNoBody(authenticated,manager).status.code)
        }
        for(req in captured) {assertTrue(req.headers.startsWith("POST "));assertArrayEquals("payload".toByteArray(),req.body)}
    }
}
