// System.X509/Common/Unix/MacOS/Win32, crypton-x509-system 1.9.0.
// Vincent Hanquez; licenses/crypton-x509-system-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import com.sun.jna.Platform
import com.sun.jna.Pointer
import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.pem.*
import java.io.IOException

enum class X509SystemPlatform {Unix,MacOS,Windows;
    companion object {fun current()=when {Platform.isWindows()->Windows;Platform.isMac()->MacOS;else->Unix}}
}
interface X509MacCertificateProcess {
    fun outputChunks(): Sequence<ByteArray>
    fun waitFor(): Int
}
class X509WindowsCertificateContext(val encoding: UInt,private val content: ()->ByteArray) {
    fun encoded()=content().copyOf()
}
interface X509WindowsCertificateApi {
    fun openRoot(): Pointer?
    fun next(store: Pointer,previous: Pointer?): Pointer?
    fun context(pointer: Pointer): X509WindowsCertificateContext
    fun close(store: Pointer)
}
interface X509SystemEffects {
    fun lookupEnv(name: String): String?
    fun getEnv(name: String): String
    fun readCertificateStore(path: String): Lazy<X509CertificateStore?>
    fun startMacProcess(command: List<String>): X509MacCertificateProcess
    val windows: X509WindowsCertificateApi
}
class X509SystemLoader(private val platform: X509SystemPlatform=X509SystemPlatform.current(),private val effects: X509SystemEffects=X509NativeSystemEffects) {
    /** Environment lookup and native I/O run now; the returned store retains lazy decoding. */
    fun getSystemCertificateStore(): X509CertificateStore {
        val file=effects.lookupEnv("SSL_CERT_FILE")
        val dir=effects.lookupEnv("SSL_CERT_DIR") // traverse performs both effects before asum.
        val override=file ?: dir
        if(override!=null) {
            val read=effects.readCertificateStore(override)
            return X509CertificateStore.deferred {read.value ?: X509CertificateStore.empty()}
        }
        return when(platform) {
            X509SystemPlatform.Unix -> unix()
            X509SystemPlatform.MacOS -> mac()
            X509SystemPlatform.Windows -> windows()
        }
    }
    private fun unix(): X509CertificateStore {
        val paths=try {listOf(effects.getEnv("SYSTEM_CERTIFICATE_PATH"))} catch(_: IOException) {defaultSystemPaths}
        val stores=paths.map(effects::readCertificateStore)
        return X509CertificateStore.deferred {X509CertificateStore.concatenate(stores.mapNotNull {it.value})}
    }
    private fun mac(): X509CertificateStore {
        val process=effects.startMacProcess(listOf("security","find-certificate","-pa",rootCAKeyChain,systemKeyChain))
        val parsed=when(val p=Pem.parseChunks(process.outputChunks())) {
            is PemParseResult.Failure -> error(p.message)
            is PemParseResult.Success -> p.sections
        }
        val codec=X509SignedCodec(X509CertificateCodec)
        val targets=X509Values {sequence {
            for(pem in parsed) if(pem.name.points().contentEquals(certificateLabel)) when(val r=codec.decodeSignedObject(pem.content)) {
                is Asn1ParseResult.Failure -> Unit
                is Asn1ParseResult.Success -> yield(lazy {r.value})
            }
        }}
        targets.cells().iterator().hasNext() // targets `seq` precedes waitForProcess.
        process.waitFor() // Exit status is intentionally ignored by the source.
        return X509CertificateStore.makeDeferred {targets.cells()}
    }
    private fun windows(): X509CertificateStore {
        val api=effects.windows
        val store=api.openRoot() ?: error("no store")
        data class Frame(val pointer: Pointer,var cert: Lazy<X509SignedCertificate>?=null)
        val frames=mutableListOf<Frame>();var previous: Pointer?=null
        while(true) {
            // Enum is outside its own frame's catch, but inside its parent's catch.
            val current=try {api.next(store,previous)} catch(e: Asn1Exception) {
                if(frames.isEmpty()) throw e
                previous=frames.removeAt(frames.lastIndex).pointer
                continue
            } ?: break
            val frame=Frame(current);frames.add(frame)
            try {
                val context=api.context(current)
                if(context.encoding==1u) when(val decoded=X509.decodeSignedCertificate(context.encoded())) {
                    is Asn1ParseResult.Failure -> Unit
                    is Asn1ParseResult.Success -> frame.cert=lazy {decoded.value}
                }
            } catch(_: Asn1Exception) {frames.removeAt(frames.lastIndex)}
            previous=current
        }
        api.close(store) // As upstream, close follows normal enumeration, not a finally.
        val certs=frames.mapNotNull {it.cert}
        return X509CertificateStore.makeDeferred {certs.asSequence()}
    }
    companion object {
        val defaultSystemPaths: List<String> get()=listOf("/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem","/etc/ssl/certs/","/system/etc/security/cacerts/","/usr/local/share/certs/","/etc/ssl/cert.pem")
        const val rootCAKeyChain="/System/Library/Keychains/SystemRootCertificates.keychain"
        const val systemKeyChain="/Library/Keychains/System.keychain"
        private val certificateLabel="CERTIFICATE".codePoints().toArray()
    }
}
object X509System {fun getSystemCertificateStore()=X509SystemLoader().getSystemCertificateStore()}
