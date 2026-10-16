package io.heapy.kotdoc.http

import com.sun.jna.Platform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HttpExecutionNativeTest {
    private val supported get()=Platform.isMac() || Platform.isLinux()
    private fun manager()=HttpManagers.newPosixManager(HttpManagers.defaultPosixManagerSettings().copy(
        proxyInsecure=HttpProxyOverrides.fromRequest,proxySecure=HttpProxyOverrides.fromRequest))
    private fun request(port: Int)=HttpRequest(host=HttpBytes.pack("127.0.0.1"),port=port.toLong(),
        responseTimeout=HttpResponseTimeout.Micro(5_000_000))
    private fun InputStream.headers(): String {
        val result=StringBuilder()
        while(!result.endsWith("\r\n\r\n")) {
            val byte=read();check(byte>=0) {"EOF before request headers: $result"};result.append(byte.toChar())
            check(result.length<65536)
        }
        return result.toString()
    }
    private fun Socket.send(status: Int=200,body: String="ok",headers: String="") {
        getOutputStream().write(("HTTP/1.1 $status Message\r\nContent-Length: ${body.length}\r\n$headers\r\n$body").toByteArray(Charsets.ISO_8859_1))
        getOutputStream().flush()
    }
    private fun <A> server(action: (ServerSocket)->A,client: (Int)->Unit): A =
        ServerSocket(0,10,InetAddress.getByName("127.0.0.1")).use {listener->
            listener.soTimeout=5000
            val task=FutureTask {action(listener)};val thread=Thread.ofVirtual().start(task)
            try {client(listener.localPort);task.get(10,TimeUnit.SECONDS)}
            finally {listener.close();thread.join(5000);assertFalse(thread.isAlive)}
        }
    @Test fun nativeHistoryReusesConnectionAndCarriesCookiesAndStripsHeaders() {
        if(!supported) return
        val requests=server({listener->listener.accept().use {peer->
            peer.soTimeout=5000
            val seen=mutableListOf<String>();val input=peer.getInputStream()
            seen+=input.headers();peer.send(302,"first","Location: /two\r\nSet-Cookie: sid=one; Path=/\r\n")
            seen+=input.headers();peer.send(307,"second","Location: /three\r\nSet-Cookie: next=two; Path=/\r\n")
            seen+=input.headers();peer.send(200,"final","Connection: close\r\n")
            seen
        }}) {port->
            val req=request(port).copy(requestHeaders=listOf(HttpHeader("Authorization","secret")),
                shouldStripHeaderOnRedirect={it==HttpHeaderName(HttpBytes.pack("Authorization"))})
            HttpExecution.system.withResponseHistory(req,manager()) {history->
                assertEquals(listOf(302L,307L),history.redirects.map {it.second.status.code})
                assertEquals(listOf("first","second"),history.redirects.map {it.second.body.joinToString("") {it.latin1()}})
                assertEquals("/three",history.finalRequest.path.latin1())
                assertEquals("final",HttpBodies.consume(history.finalResponse.body).joinToString("") {it.latin1()})
                assertEquals(setOf("sid","next"),history.finalResponse.cookieJar.cookies.map {it.name.latin1()}.toSet())
                assertTrue(history.redirects.first().second.headers.any {it.named("Set-Cookie")})
            }
        }
        assertTrue("Authorization: secret\r\n" in requests[0])
        assertFalse("Authorization:" in requests[1]);assertFalse("Authorization:" in requests[2])
        assertTrue("Cookie: sid=one\r\n" in requests[1]);assertTrue("sid=one" in requests[2]);assertTrue("next=two" in requests[2])
    }
    @Test fun nativeReusedClosedConnectionRetriesWithNewSocket() {
        if(!supported) return
        val requests=server({listener->
            val seen=mutableListOf<String>()
            repeat(2) {i->listener.accept().use {peer->
                peer.soTimeout=5000;seen+=peer.getInputStream().headers();peer.send(body="body$i")
            }}
            seen
        }) {port->
            val manager=manager();val request=request(port)
            repeat(2) {i->assertEquals("body$i",HttpExecution.system.httpLbs(request,manager).body.joinToString("") {it.latin1()})}
        }
        assertEquals(2,requests.size)
    }
    @Test fun nativeZeroRedirectCountDiffersBetweenOpenAndHistoryAsUpstream() {
        if(!supported) return
        for(history in listOf(false,true)) server({listener->listener.accept().use {peer->
            peer.soTimeout=5000;peer.getInputStream().headers();peer.send(302,"redirect","Location: /again\r\nConnection: close\r\n")
        }}) {port->
            val req=request(port).copy(redirectCount=0)
            if(history) {
                val error=assertThrows(HttpRequestException::class.java) {HttpExecution.system.responseOpenHistory(req,manager())}
                assertInstanceOf(HttpFailureContent.TooManyRedirects::class.java,error.failure)
            } else assertEquals(302L,HttpExecution.system.httpLbs(req,manager()).status.code)
        }
    }
    @Test fun nativeBracketClosesUnconsumedResponseWhenCallbackThrows() {
        if(!supported) return
        val eof=server({listener->listener.accept().use {peer->
            peer.soTimeout=5000;peer.getInputStream().headers()
            peer.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n\r\nx".toByteArray())
            peer.getOutputStream().flush();peer.getInputStream().read()
        }}) {port->
            val failure=IllegalStateException("callback")
            assertSame(failure,assertThrows(IllegalStateException::class.java) {
                HttpExecution.system.withResponse(request(port),manager()) {throw failure}
            })
        }
        assertEquals(-1,eof)
    }
    @Test fun nativeHeaderTimeoutDiscardsConnection() {
        if(!supported) return
        val eof=server({listener->listener.accept().use {peer->
            peer.soTimeout=5000;peer.getInputStream().headers();peer.getInputStream().read()
        }}) {port->
            val error=assertThrows(HttpRequestException::class.java) {
                HttpExecution.system.responseOpen(request(port).copy(responseTimeout=HttpResponseTimeout.Micro(200_000)),manager())
            }
            assertEquals(HttpFailureContent.ResponseTimeout,error.failure)
        }
        assertEquals(-1,eof)
    }
    @Test fun acquisitionTimeoutAndCancellationDiscardLateUninterruptibleConnectors() {
        for(cancel in listOf(false,true)) {
            val entered=CountDownLatch(1);val finish=CountDownLatch(1);val closed=CountDownLatch(1);val closes=AtomicInteger()
            val manager=HttpManagers.newManager(HttpManagerSettings(rawConnection={HttpConnector {_,_,_->
                entered.countDown()
                while(finish.count>0) try {finish.await()} catch(_: InterruptedException) {}
                HttpConnections.makeConnection({HttpBytes.empty},{},{closes.incrementAndGet();closed.countDown()})
            }},proxyInsecure=HttpProxyOverrides.fromRequest,proxySecure=HttpProxyOverrides.fromRequest),{})
            val task=FutureTask {HttpExecution.system.responseOpen(HttpRequest(cookieJar=null,
                responseTimeout=HttpResponseTimeout.Micro(if(cancel) 5_000_000 else 100_000)),manager)}
            val thread=Thread.ofVirtual().start(task)
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS))
                if(cancel) thread.interrupt()
                val error=assertThrows(java.util.concurrent.ExecutionException::class.java) {task.get(5,TimeUnit.SECONDS)}
                if(cancel) assertInstanceOf(InterruptedException::class.java,error.cause)
                else assertEquals(HttpFailureContent.ConnectionTimeout,(error.cause as HttpRequestException).failure)
                finish.countDown();assertTrue(closed.await(5,TimeUnit.SECONDS));assertEquals(1,closes.get())
            } finally {finish.countDown();thread.join(5000);assertFalse(thread.isAlive)}
        }
    }
}
