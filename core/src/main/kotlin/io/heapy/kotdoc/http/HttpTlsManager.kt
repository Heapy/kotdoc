// Network.HTTP.Client.TLS, http-client-tls 0.4.0, (c) Michael Snoyman.
// MIT; licenses/http-client-tls-MIT.txt. Connection types/exceptions: crypton-connection
// 0.4.6, (c) Vincent Hanquez, BSD-3-Clause; complete notices and source retained.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.Text212Lowercase
import io.heapy.kotdoc.tls.TlsError
import io.heapy.kotdoc.tls.TlsException
import io.heapy.kotdoc.uri.NetworkURI
import java.io.IOException

sealed interface HttpSocketProxy {
    data class Simple(val host: CharacterString,val port: UShort): HttpSocketProxy
    data class Environment(val name: CharacterString?): HttpSocketProxy
    data class Other(val host: CharacterString,val port: UShort): HttpSocketProxy
}
class ConnectionLineTooLong: RuntimeException(),HttpExceptionShow {
    override fun showsPrec(precedence: Int)="LineTooLong"
}
class ConnectionHostNotResolved(val host: CharacterString): RuntimeException(),HttpExceptionShow {
    override fun showsPrec(precedence: Int): String {
        val s="HostNotResolved ${HaskellString.quoteCodePoints(host.points())}"
        return if(precedence>10) "($s)" else s
    }
}
class ConnectionHostCannotConnect(val host: CharacterString,errors: List<GhcIOException>): RuntimeException(),HttpExceptionShow {
    val errors=java.util.Collections.unmodifiableList(errors.toList())
    override fun showsPrec(precedence: Int): String {
        val s="HostCannotConnect ${HaskellString.quoteCodePoints(host.points())} ${errors.joinToString(",","[","]") {it.error.toString()}}"
        return if(precedence>10) "($s)" else s
    }
}
class HttpTlsConnectionParams<T>(host: CharacterString,val port: UShort,val secure: T?,socks: Lazy<HttpSocketProxy?>) {
    val host=host
    val socks by socks
    constructor(host: CharacterString,port: UShort,secure: T?,socks: HttpSocketProxy?):this(host,port,secure,lazyOf(socks))
}

/** The original Network.Connection boundary. No default/fake TLS transport is installed. */
interface HttpTlsConnectionBackend<C,N,T> {
    val defaultTlsSettings: T
    fun initializeContext(): C
    fun connect(context: Lazy<C>,params: HttpTlsConnectionParams<T>): N
    fun close(connection: N)
    fun getChunk(connection: N): HttpBytes
    fun put(connection: N,bytes: HttpBytes)
    fun setSecure(context: Lazy<C>,connection: N,settings: T)
}

/** Original http-client-tls orchestration; TLS handshakes/certificates belong to the backend. */
class HttpTlsManagers<C,N,T>(private val backend: HttpTlsConnectionBackend<C,N,T>,
    private val defaults: HttpManagerSettings,
    private val initializeSockets: ()->Unit,
    private val environment: ()->List<Pair<String,String>> = HttpProxyEnvironment::systemEnvironment,
    private val poolRuntime: KeyedPoolRuntime = KeyedPoolSystemRuntime,
) {
    private val globalContext: Lazy<C> = sourceLazy {backend.initializeContext()}
    private val globalManager by sourceLazy {java.util.concurrent.atomic.AtomicReference(newTlsManager())}

    fun mkManagerSettings(tls: T,socks: HttpSocketProxy?=null)=mkManagerSettingsContext(null,tls,socks)
    fun mkManagerSettingsContext(context: C?,tls: T,socks: HttpSocketProxy?=null)=
        mkManagerSettingsContext(defaults,context,tls,socks,socks)
    fun mkManagerSettingsContext(settings: HttpManagerSettings,context: C?,tls: T,
        insecureSocks: HttpSocketProxy?,secureSocks: HttpSocketProxy?,
    ): HttpManagerSettings = settingsDeferred(settings,context?.let(::lazyOf),tls,insecureSocks,secureSocks)
    private fun settingsDeferred(settings: HttpManagerSettings,context: Lazy<C>?,tls: T,
        insecureSocks: HttpSocketProxy?,secureSocks: HttpSocketProxy?,
    ): HttpManagerSettings = settings.copy(
        tlsConnection=tlsConnectionDeferred(context,tls,secureSocks),
        tlsProxyConnection=tlsProxyConnectionDeferred(context,tls,secureSocks),
        // Intentionally use defaultManagerSettings, even when settings has a custom raw factory.
        rawConnection=if(insecureSocks==null) defaults.rawConnection else tlsConnectionDeferred(context,null,insecureSocks),
        retryableException={e->e is TlsException.PostHandshake && e.error==TlsError.EOF || HttpManagers.defaultRetryableException(e)},
        wrapException=object: HttpManagerExceptionWrapper {
            override fun <A> wrap(request: HttpRequest,action: ()->A): A = try {action()} catch(e: Throwable) {
                if(e is IOException || e is TlsException || e is ConnectionLineTooLong ||
                    e is ConnectionHostNotResolved || e is ConnectionHostCannotConnect)
                    throw HttpRequestException(request,HttpFailureContent.InternalException(HttpExceptionValue(e)))
                throw e
            }
        })
    fun tlsManagerSettings()=mkManagerSettings(backend.defaultTlsSettings)

    fun getTlsConnection(context: C?,tls: T?,socks: HttpSocketProxy?): ()->HttpConnector =
        tlsConnectionDeferred(context?.let(::lazyOf),tls,socks)
    private fun tlsConnectionDeferred(context: Lazy<C>?,tls: T?,socks: HttpSocketProxy?): ()->HttpConnector = {
        val selected=context ?: lazyOf(backend.initializeContext())
        HttpConnector {_,host,port->
            val connection=backend.connect(selected,HttpTlsConnectionParams(Chars.fromString(HttpConnections.strippedHostName(host)),port.toUShort(),tls,socks))
            try {convertConnection(connection)} catch(e: Throwable) {backend.close(connection);throw e}
        }
    }
    fun getTlsProxyConnection(context: C?,tls: T,socks: HttpSocketProxy?): ()->HttpProxyConnector =
        tlsProxyConnectionDeferred(context?.let(::lazyOf),tls,socks)
    private fun tlsProxyConnectionDeferred(context: Lazy<C>?,tls: T,socks: HttpSocketProxy?): ()->HttpProxyConnector = {
        val selected=context ?: lazyOf(backend.initializeContext())
        HttpProxyConnector {bytes,check,serverName,_,host,port->
            val proxy=sourceLazy {
                if(socks==null) HttpSocketProxy.Other(Chars.fromString(HttpConnections.strippedHostName(host)),port.toUShort())
                else throw HttpTlsProxySocksConflict()
            }
            val connection=backend.connect(selected,HttpTlsConnectionParams(Chars.fromString(HttpConnections.strippedHostName(serverName)),port.toUShort(),null,proxy))
            try {
                backend.put(connection,bytes)
                val converted=convertConnection(connection)
                check(converted)
                backend.setSecure(selected,connection,tls)
                converted
            } catch(e: Throwable) {backend.close(connection);throw e}
        }
    }
    fun convertConnection(connection: N): HttpConnection {
        // Finalization needs the transport and connection, not this adapter's
        // global manager (which can itself contain this pooled connection).
        val transport=backend
        return HttpConnections.makeConnection(
            {transport.getChunk(connection)},{transport.put(connection,it)},
            {try {transport.close(connection)} catch(_: IOException) {}})
    }

    fun newTlsManager(): HttpManager=newTlsManagerSettings(null)
    fun newTlsManagerWith(settings: HttpManagerSettings): HttpManager=newTlsManagerSettings(settings)
    private fun newTlsManagerSettings(supplied: HttpManagerSettings?): HttpManager {
        val env=environment().toList()
        val insecure=parseSocksSettings(env,"http_proxy");val secure=parseSocksSettings(env,"https_proxy")
        // globalConnectionContext is shared by factories, not forced while building settings.
        var settings=settingsDeferred(supplied ?: defaults,globalContext,backend.defaultTlsSettings,insecure,secure)
        if(supplied!=null) settings=settings.copy(tlsConnection=supplied.tlsConnection,tlsProxyConnection=supplied.tlsProxyConnection)
        if(secure!=null) settings=settings.copy(proxySecure=HttpProxyOverrides.fromRequest)
        if(insecure!=null) settings=settings.copy(proxyInsecure=HttpProxyOverrides.fromRequest)
        return HttpManagers.newManager(settings,initializeSockets,poolRuntime)
    }
    fun getGlobalManager()=globalManager.get()
    fun setGlobalManager(manager: HttpManager) {globalManager.set(manager)}

    companion object {
        fun parseSocksSettings(environment: List<Pair<String,String>>,name: String): HttpSocketProxy.Simple? {
            val textName=packText(name)
            val folded by lazy {environment.associate {Text212Lowercase.convert(packText(it.first)) to it.second}}
            val raw=environment.firstOrNull {it.first==textName}?.second ?: folded[textName] ?: return null
            val uri=NetworkURI.parseURI(raw) ?: return null
            if(Chars.build(uri.scheme) !in listOf("socks5:","socks5h:") || Chars.build(uri.path) !in listOf("","/") ||
                uri.query.points().isNotEmpty() || uri.fragment.points().isNotEmpty()) return null
            val authority=uri.authority ?: return null
            val port=Chars.build(authority.port)
            if(!port.startsWith(':') || port.length==1) return null
            var n=0u
            for(c in port.drop(1)) {if(c !in '0'..'9') return null;n=(n*10u+(c-'0').toUInt()) and 65535u}
            return HttpSocketProxy.Simple(authority.regName,n.toUShort())
        }
        private fun packText(s: String)=buildString {s.codePoints().forEach {appendCodePoint(if(it in 0xd800..0xdfff) 0xfffd else it)}}
    }
}
class HttpTlsProxySocksConflict: RuntimeException("Cannot use SOCKS and TLS proxying together")

/** A source CAF retains a synchronous evaluation failure as well as a value. */
private fun <A> sourceLazy(initializer: ()->A): Lazy<A> = object: Lazy<A> {
    private val result=lazy {try {Result.success(initializer())} catch(e: Throwable) {Result.failure(e)}}
    override val value get()=result.value.getOrThrow()
    override fun isInitialized()=result.isInitialized()
}
