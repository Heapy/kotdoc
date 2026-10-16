// Network.HTTP.Client.TLS, http-client-tls 0.4.0, Copyright (c) Michael Snoyman.
// MIT; licenses/http-client-tls-MIT.txt. Complete original implementation in vendor/.
package io.heapy.kotdoc.http

import java.security.MessageDigest

enum class DigestAuthExceptionDetails {
    UnexpectedStatusCode,MissingWWWAuthenticateHeader,WWWAuthenticateIsNotDigest,MissingRealm,MissingNonce;
    fun show()=name
}
class DigestAuthException(val request: HttpRequest,val response: HttpResponse<Unit>,val details: DigestAuthExceptionDetails):
    RuntimeException(),HttpExceptionShow {
    override fun showsPrec(precedence: Int): String {
        val result="DigestAuthException ${request.show()} ${HttpResponses.showResponse(response,{"()"},11)} ${details.show()}"
        return if(precedence>10) "($result)" else result
    }
    fun display(): String {
        val reason=when(details) {
            DigestAuthExceptionDetails.UnexpectedStatusCode->"received unexpected status code"
            DigestAuthExceptionDetails.MissingWWWAuthenticateHeader->"missing WWW-Authenticate response header"
            DigestAuthExceptionDetails.WWWAuthenticateIsNotDigest->"WWW-Authenticate response header does not indicate Digest"
            DigestAuthExceptionDetails.MissingRealm->"WWW-Authenticate response header does include realm"
            DigestAuthExceptionDetails.MissingNonce->"WWW-Authenticate response header does include nonce"
        }
        return "Unable to submit digest credentials due to: $reason.\n\nRequest: ${request.show()}.\n\nResponse: ${HttpResponses.showResponse(response,{"()"})}"
    }
    override val message get()=display()
}
object HttpDigestAuth {
    /** The outer IO probes the server. Validation lives in the returned inner effect. */
    fun applyDigestAuth(user: HttpBytes,password: HttpBytes,initial: HttpRequest,manager: HttpManager,
        execution: HttpExecution = HttpExecution.system,
    ): ()->HttpRequest = applyDigestAuth(user,password,initial) {execution.httpNoBody(it,manager)}

    fun applyDigestAuth(user: HttpBytes,password: HttpBytes,initial: HttpRequest,
        httpNoBody: (HttpRequest)->HttpResponse<Unit>,
    ): ()->HttpRequest {
        val request=initial.copy(checkResponse={_,_->})
        val response=httpNoBody(request)
        return {
            fun failure(reason: DigestAuthExceptionDetails): Nothing=throw DigestAuthException(request,response,reason)
            // HTTP Status Eq compares the numeric code, not the reason phrase.
            if(response.status.code!=401L) failure(DigestAuthExceptionDetails.UnexpectedStatusCode)
            val header=response.headers.firstOrNull {it.named("WWW-Authenticate")}?.value
                ?: failure(DigestAuthExceptionDetails.MissingWWWAuthenticateHeader)
            val prefix=HttpBytes.pack("Digest ")
            val h=header.bytes()
            if(h.size<prefix.size || !HttpHeader(HttpBytes(h.copyOfRange(0,prefix.size)),HttpBytes.empty).named(prefix))
                failure(DigestAuthExceptionDetails.WWWAuthenticateIsNotDigest)
            val pairs=toPairs(HttpBytes(h.copyOfRange(prefix.size,h.size))).map {(k,v)->strip(k) to strip(v)}
            fun find(name: String)=pairs.firstOrNull {it.first==HttpBytes.pack(name)}?.second
            val realm=find("realm") ?: failure(DigestAuthExceptionDetails.MissingRealm)
            val nonce=find("nonce") ?: failure(DigestAuthExceptionDetails.MissingNonce)
            val qop=find("qop")!=null
            fun md5(bytes: HttpBytes)=HttpBytes.pack(MessageDigest.getInstance("MD5").digest(bytes.bytes()).toHexString())
            val colon=HttpBytes.pack(":")
            val ha1=md5(user+colon+realm+colon+password)
            val ha2=md5(request.method+colon+request.path)
            val digest=if(qop) md5(ha1+colon+nonce+HttpBytes.pack(":00000001:deadbeef:auth:")+ha2)
                else md5(ha1+colon+nonce+colon+ha2)
            val auth=HttpBytes.pack("Digest username=\"")+user+HttpBytes.pack("\", realm=\"")+realm+
                HttpBytes.pack("\", nonce=\"")+nonce+HttpBytes.pack("\", uri=\"")+request.path+
                HttpBytes.pack("\", response=\"")+digest+HttpBytes.pack("\"")+
                (find("opaque")?.let {HttpBytes.pack(", opaque=\"")+it+HttpBytes.pack("\"")} ?: HttpBytes.empty)+
                (if(qop) HttpBytes.pack(", qop=auth, nc=00000001, cnonce=\"deadbeef\"") else HttpBytes.empty)
            request.copy(requestHeaders=listOf(HttpHeader("Authorization",auth.latin1()))+
                request.requestHeaders.filterNot {it.named("Authorization")},cookieJar=response.cookieJar)
        }
    }
    private fun strip(bytes: HttpBytes)=HttpBytes(bytes.bytes().dropWhile {it==32.toByte()}.dropLastWhile {it==32.toByte()}.toByteArray())
    private fun toPairs(bytes: HttpBytes): List<Pair<HttpBytes,HttpBytes>> {
        val input=bytes.bytes();var at=0
        fun part(from: Int,to: Int)=HttpBytes(input.copyOfRange(from,to))
        return buildList {
            while(at<input.size) {
                while(at<input.size && input[at]==32.toByte()) at++
                val start=at
                while(at<input.size && input[at]!=61.toByte() && input[at]!=44.toByte()) at++
                val key=part(start,at)
                if(at==input.size) {add(key to HttpBytes.empty);break}
                if(input[at++]==44.toByte()) {add(key to HttpBytes.empty);continue}
                val valueStart=at
                if(at<input.size && input[at]==34.toByte()) {
                    var end=at+1;while(end<input.size && input[end]!=34.toByte()) end++
                    if(end<input.size) {
                        val value=part(at+1,end)
                        at=end;while(at<input.size && input[at]!=44.toByte()) at++
                        if(at<input.size) at++
                        add(key to value);continue
                    }
                }
                at=valueStart;while(at<input.size && input[at]!=44.toByte()) at++
                val value=part(valueStart,at);if(at<input.size) at++
                add(key to value)
            }
        }
    }
}
