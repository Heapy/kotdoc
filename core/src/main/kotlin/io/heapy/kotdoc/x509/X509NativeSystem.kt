// System.X509 native boundaries; licenses/crypton-x509-system-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.resources.GhcEnvironmentLookup
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal interface X509Crypt32Functions: StdCallLibrary {
    fun CertOpenSystemStoreW(provider: Pointer?,name: WString): Pointer?
    fun CertEnumCertificatesInStore(store: Pointer,previous: Pointer?): Pointer?
    fun CertCloseStore(store: Pointer,flags: Int): Int
}
internal class X509Crypt32CertificateApi(private val functions: X509Crypt32Functions): X509WindowsCertificateApi {
    override fun openRoot()=functions.CertOpenSystemStoreW(null,WString("ROOT"))
    override fun next(store: Pointer,previous: Pointer?)=functions.CertEnumCertificatesInStore(store,previous)
    override fun context(pointer: Pointer): X509WindowsCertificateContext {
        val encoding=pointer.getInt(0).toUInt()
        val data=pointer.getPointer(Native.POINTER_SIZE.toLong())
        val length=pointer.getInt((2*Native.POINTER_SIZE).toLong()).toUInt()
        return X509WindowsCertificateContext(encoding) {
            require(length<=Int.MAX_VALUE.toUInt()) {"certificate exceeds JVM byte-array capacity"}
            if(length==0u) byteArrayOf() else data.getByteArray(0,length.toInt())
        }
    }
    override fun close(store: Pointer) {functions.CertCloseStore(store,0)}
}
object X509NativeSystemEffects: X509SystemEffects {
    override fun lookupEnv(name: String)=GhcEnvironmentLookup.lookupEnv(Chars.fromString(name))?.let(Chars::build)
    override fun getEnv(name: String)=Chars.build(GhcEnvironmentLookup.getEnv(Chars.fromString(name)))
    override fun readCertificateStore(path: String): Lazy<X509CertificateStore?> = try {
        // A source empty FilePath is not Java's Path("") current-directory alias.
        if(path.isEmpty()) lazyOf(null) else X509CertificateStore.readDeferred(Path.of(path))
    } catch(_: InvalidPathException) {lazyOf(null)}
    override fun startMacProcess(command: List<String>): X509MacCertificateProcess {
        val process=ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        return object: X509MacCertificateProcess {
            override fun outputChunks(): Sequence<ByteArray> = sequence {
                val input=process.inputStream;val buffer=ByteArray(32752)
                while(true) {val n=input.read(buffer);if(n<0) {input.close();break};if(n>0) yield(buffer.copyOf(n))}
            }
            override fun waitFor()=process.waitFor()
        }
    }
    override val windows: X509WindowsCertificateApi by lazy {X509Crypt32CertificateApi(Native.load("Crypt32",X509Crypt32Functions::class.java))}
}
