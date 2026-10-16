// Network.HTTP.Client.Manager/Types 0.7.19, (c) 2013 Michael Snoyman.
// MIT; complete source and notice retained in vendor/ and licenses/http-client-MIT.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.GhcIOException
import java.io.IOException

/** The source SomeException carries its own Show dictionary. Foreign JVM
 * exceptions retain identity; callers can supply their original dictionary. */
interface HttpExceptionShow { fun showsPrec(precedence: Int): String }
class HttpExceptionValue(val exception: Throwable, private val display: (Int) -> String = { p ->
    when (exception) { is HttpExceptionShow -> exception.showsPrec(p); is GhcIOException -> exception.error.toString(); else -> exception.toString() }
}) {
    fun showsPrec(precedence: Int) = display(precedence)
}
class HttpGhcIOException(error: GhcIoError) : GhcIOException(error), HttpExceptionShow {
    override fun showsPrec(precedence: Int) = error.toString()
}

sealed class HttpConnectionKey : Comparable<HttpConnectionKey> {
    data class Raw(val address: UInt?, val host: HttpBytes, val port: Long) : HttpConnectionKey()
    data class Secure(val address: UInt?, val host: HttpBytes, val port: Long) : HttpConnectionKey()
    data class Proxy(val host: HttpBytes, val port: Long, val authorization: HttpBytes?, val ultimateHost: HttpBytes, val ultimatePort: Long) : HttpConnectionKey()
    private val rank get() = when (this) { is Raw -> 0; is Secure -> 1; is Proxy -> 2 }
    override fun compareTo(other: HttpConnectionKey): Int {
        rank.compareTo(other.rank).takeIf { it != 0 }?.let { return it }
        fun <A : Comparable<A>> maybe(a: A?, b: A?) = when { a == null -> if (b == null) 0 else -1; b == null -> 1; else -> a.compareTo(b) }
        fun direct(a: UInt?, h: HttpBytes, p: Long, b: UInt?, j: HttpBytes, q: Long) =
            maybe(a, b).takeIf { it != 0 } ?: h.compareTo(j).takeIf { it != 0 } ?: p.compareTo(q)
        return when (this) {
            is Raw -> (other as Raw).let { direct(address, host, port, it.address, it.host, it.port) }
            is Secure -> (other as Secure).let { direct(address, host, port, it.address, it.host, it.port) }
            is Proxy -> (other as Proxy).let {
                host.compareTo(it.host).takeIf { n -> n != 0 } ?: port.compareTo(it.port).takeIf { n -> n != 0 }
                    ?: maybe(authorization, it.authorization).takeIf { n -> n != 0 }
                    ?: ultimateHost.compareTo(it.ultimateHost).takeIf { n -> n != 0 } ?: ultimatePort.compareTo(it.ultimatePort)
            }
        }
    }
    fun show(precedence: Int = 0): String {
        fun int(n: Long) = if (n < 0) "($n)" else n.toString()
        fun address(n: UInt?) = n?.let { "(Just $it)" } ?: "Nothing"
        val body = when (this) {
            is Raw -> "CKRaw ${address(address)} ${host.show()} ${int(port)}"
            is Secure -> "CKSecure ${address(address)} ${host.show()} ${int(port)}"
            is Proxy -> "CKProxy ${host.show()} ${int(port)} ${authorization?.let { "(Just ${it.show()})" } ?: "Nothing"} ${ultimateHost.show()} ${int(ultimatePort)}"
        }
        return if (precedence > 10) "($body)" else body
    }
}

fun interface HttpConnector { fun connect(address: UInt?, host: String, port: Long): HttpConnection }
fun interface HttpProxyConnector {
    fun connect(connectBytes: HttpBytes, parse: (HttpConnection) -> Unit, ultimateHost: String,
        address: UInt?, proxyHost: String, proxyPort: Long): HttpConnection
}
fun interface HttpProxyOverride { fun prepare(secure: Boolean): (HttpRequest) -> HttpRequest }
interface HttpManagerExceptionWrapper { fun <A> wrap(request: HttpRequest, action: () -> A): A }
object HttpDefaultManagerExceptionWrapper : HttpManagerExceptionWrapper {
    override fun <A> wrap(request: HttpRequest, action: () -> A): A = try { action() }
        catch (e: IOException) { throw HttpContentException(HttpFailureContent.InternalException(HttpExceptionValue(e))) }
}

/** Explicit transport factory: the original default socket factory and runtime
 * initialization remain separate work. No missing-transport stub is installed. */
data class HttpManagerSettings(
    val rawConnection: () -> HttpConnector,
    val connectionCount: Long = 10,
    val tlsConnection: () -> HttpConnector = { HttpConnector { _, _, _ -> throw HttpContentException(HttpFailureContent.TlsNotSupported) } },
    val tlsProxyConnection: () -> HttpProxyConnector = { HttpProxyConnector { _, _, _, _, _, _ -> throw HttpContentException(HttpFailureContent.TlsNotSupported) } },
    val responseTimeout: HttpResponseTimeout = HttpResponseTimeout.Default,
    val retryableException: (Throwable) -> Boolean = HttpManagers::defaultRetryableException,
    val wrapException: HttpManagerExceptionWrapper = HttpDefaultManagerExceptionWrapper,
    val idleConnectionCount: Long = 512,
    val modifyRequest: (HttpRequest) -> HttpRequest = { it },
    val modifyResponse: (HttpResponse<HttpBodyReader>) -> HttpResponse<HttpBodyReader> = { it },
    val proxyInsecure: HttpProxyOverride = HttpProxyOverrides.defaultProxy(),
    val proxySecure: HttpProxyOverride = HttpProxyOverrides.defaultProxy(),
    val maxHeaderLength: Long? = 4096,
    val maxNumberHeaders: Long? = 100,
)

class HttpManager internal constructor(
    internal val connections: KeyedPool<HttpConnectionKey, HttpConnection>,
    val responseTimeout: HttpResponseTimeout,
    val retryableException: (Throwable) -> Boolean,
    val wrapException: HttpManagerExceptionWrapper,
    val modifyRequest: (HttpRequest) -> HttpRequest,
    val modifyResponse: (HttpResponse<HttpBodyReader>) -> HttpResponse<HttpBodyReader>,
    val setProxy: (HttpRequest) -> HttpRequest,
    val maxHeaderLength: Long?, val maxNumberHeaders: Long?,
)

object HttpManagers {
    /** Original default raw factory, using the implemented native POSIX ABI.
     * Winsock initialization and its native IO backend remain a separate port. */
    fun defaultPosixManagerSettings() = HttpManagerSettings(
        rawConnection = rawConnectionModifySocket(HttpPosixSocketBackend(), {}),
    )
    fun newPosixManager(settings: HttpManagerSettings = defaultPosixManagerSettings(),
        poolRuntime: KeyedPoolRuntime = KeyedPoolSystemRuntime,
    ) = newManager(settings, {}, poolRuntime) // Original withSocketsDo is identity on POSIX.

    fun <S, A> rawConnectionModifySocket(backend: HttpSocketBackend<S, A>, tweak: (S) -> Unit,
        runtime: HttpSocketRaceRuntime = HttpSocketRaceSystemRuntime,
    ): () -> HttpConnector = { HttpConnector { address, host, port ->
        HttpSocketConnections.openSocketConnection(backend, tweak, address, host, port, runtime)
    } }
    fun <S, A> rawConnectionModifySocketSize(backend: HttpSocketBackend<S, A>, tweak: (S) -> Unit,
        runtime: HttpSocketRaceRuntime = HttpSocketRaceSystemRuntime,
    ): () -> HttpSizedConnector = { HttpSizedConnector { size, address, host, port ->
        HttpSocketConnections.openSocketConnectionSize(backend, tweak, size, address, host, port, runtime)
    } }
    fun defaultRetryableException(e: Throwable): Boolean = e is IOException || e is HttpContentException &&
        (e.failure == HttpFailureContent.NoResponseDataReceived || e.failure == HttpFailureContent.IncompleteHeaders)

    /** Socket initialization is a required transport effect until the original
     * native socket backend supplies the default overload. It runs first. */
    fun newManager(settings: HttpManagerSettings, initializeSockets: () -> Unit,
        poolRuntime: KeyedPoolRuntime = KeyedPoolSystemRuntime,
    ): HttpManager {
        initializeSockets()
        val http = settings.proxyInsecure.prepare(false)
        val https = settings.proxySecure.prepare(true)
        val create = createConnection(settings)
        val pool = KeyedPool.createKeyedPool<HttpConnectionKey, HttpConnection>(create, { it.close() },
            settings.connectionCount, settings.idleConnectionCount, {}, poolRuntime)
        return HttpManager(pool, settings.responseTimeout, settings.retryableException, settings.wrapException,
            settings.modifyRequest, settings.modifyResponse, { if (it.secure) https(it) else http(it) },
            settings.maxHeaderLength, settings.maxNumberHeaders)
    }
    /** Original deprecated API is deliberately a no-op, not pool shutdown. */
    fun closeManager(@Suppress("UNUSED_PARAMETER") manager: HttpManager) = Unit
    fun <A> withManager(settings: HttpManagerSettings, initializeSockets: () -> Unit,
        poolRuntime: KeyedPoolRuntime = KeyedPoolSystemRuntime, action: (HttpManager) -> A,
    ): A = action(newManager(settings, initializeSockets, poolRuntime))

    fun dropProxyAuthSecure(request: HttpRequest): HttpRequest = if (request.secure && request.proxy != null)
        request.copy(requestHeaders = request.requestHeaders.filterNot { it.named("Proxy-Authorization") }) else request

    fun connectionKey(request: HttpRequest): HttpConnectionKey {
        val proxy = request.proxy
        return when {
            proxy == null && !request.secure -> HttpConnectionKey.Raw(request.hostAddress, request.host, request.port)
            proxy == null -> HttpConnectionKey.Secure(request.hostAddress, request.host, request.port)
            !request.secure || request.proxySecureMode == ProxySecureMode.ProxySecureWithoutConnect -> HttpConnectionKey.Raw(null, proxy.host, proxy.port)
            else -> HttpConnectionKey.Proxy(proxy.host, proxy.port, request.requestHeaders.firstOrNull { it.named("Proxy-Authorization") }?.value, request.host, request.port)
        }
    }
    fun getConnection(request: HttpRequest, manager: HttpManager): KeyedManaged<HttpConnection> {
        if (request.host.size == 0) throw HttpContentException(HttpFailureContent.InvalidDestinationHost(request.host))
        return manager.connections.take(connectionKey(request))
    }
    fun managedConnection(managed: KeyedManaged<HttpConnection>) = HttpManagedConnection(managed.resource, managed::release, managed::keepAlive)
    fun createConnection(settings: HttpManagerSettings): (HttpConnectionKey) -> HttpConnection {
        val raw = settings.rawConnection()
        val tls = settings.tlsConnection()
        val proxy = settings.tlsProxyConnection()
        return { key ->
            try {
                when (key) {
                    is HttpConnectionKey.Raw -> raw.connect(key.address, key.host.latin1(), key.port)
                    is HttpConnectionKey.Secure -> tls.connect(key.address, key.host.latin1(), key.port)
                    is HttpConnectionKey.Proxy -> {
                        val bytes = HttpBytes.pack("CONNECT ") + key.ultimateHost + HttpBytes.pack(":${key.ultimatePort} HTTP/1.1\r\n") +
                            (key.authorization?.let { HttpBytes.pack("Proxy-Authorization: ") + it + HttpBytes.pack("\r\n") } ?: HttpBytes.empty) +
                            HttpBytes.pack("Host: ") + key.ultimateHost + HttpBytes.pack(":${key.ultimatePort}\r\n\r\n")
                        val parse: (HttpConnection) -> Unit = { connection ->
                            val status = HttpHeaders.parseStatusHeaders(settings.maxHeaderLength, settings.maxNumberHeaders, connection).status
                            if (status.code != 200L) throw HttpContentException(HttpFailureContent.ProxyConnectException(key.ultimateHost, key.ultimatePort, status))
                        }
                        proxy.connect(bytes, parse, key.ultimateHost.latin1(), null, key.host.latin1(), key.port)
                    }
                }
            } catch (e: IOException) { throw HttpContentException(HttpFailureContent.ConnectionFailure(HttpExceptionValue(e))) }
        }
    }
}

object HttpProxyOverrides {
    val fromRequest = HttpProxyOverride { { request -> request } }
    val noProxy = HttpProxyOverride { { request -> request.copy(proxy = null) } }
    fun useProxy(proxy: HttpProxy) = HttpProxyOverride { { request -> request.copy(proxy = proxy) } }
    fun useProxySecureWithoutConnect(proxy: HttpProxy) = HttpProxyOverride { { request -> request.copy(proxy = proxy, proxySecureMode = ProxySecureMode.ProxySecureWithoutConnect) } }
    fun environment(fallback: HttpProxy?, environment: () -> List<Pair<String, String>> = HttpProxyEnvironment::systemEnvironment,
        windows: Boolean = com.sun.jna.Platform.isWindows(), registry: HttpProxyRegistry = HttpNativeWindowsRegistry,
    ) = namedEnvironment(null, fallback, environment, windows, registry)
    fun namedEnvironment(name: String?, fallback: HttpProxy?, environment: () -> List<Pair<String, String>> = HttpProxyEnvironment::systemEnvironment,
        windows: Boolean = com.sun.jna.Platform.isWindows(), registry: HttpProxyRegistry = HttpNativeWindowsRegistry,
    ) = override(name, fallback?.let(HttpEnvHelper::UseProxy) ?: HttpEnvHelper.NoProxy, environment, windows, registry)
    fun defaultProxy(environment: () -> List<Pair<String, String>> = HttpProxyEnvironment::systemEnvironment,
        windows: Boolean = com.sun.jna.Platform.isWindows(), registry: HttpProxyRegistry = HttpNativeWindowsRegistry,
    ) = override(null, HttpEnvHelper.FromRequest, environment, windows, registry)
    private fun override(name: String?, fallback: HttpEnvHelper, environment: () -> List<Pair<String, String>>,
        windows: Boolean, registry: HttpProxyRegistry,
    ) = HttpProxyOverride { secure -> HttpProxyEnvironment.systemProxyHelper(name, HttpProxyEnvironment.httpProtocol(secure), fallback, environment, windows, registry) }
}
