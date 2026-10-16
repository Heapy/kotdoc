// GHC 9.12.2 GHC.Internal.IO.Encoding mkGlobal, initial CAFs and argvEncoding.
// University of Glasgow; complete licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import com.sun.jna.Platform
import java.util.concurrent.atomic.AtomicReference

/** A source encoding CAF shares its value and synchronous evaluation failure. */
internal fun <A> ghcEncodingLazy(initializer: () -> A): Lazy<A> = object : Lazy<A> {
    private val result = lazy { try { Result.success(initializer()) } catch (e: Throwable) { Result.failure(e) } }
    override val value: A get() = result.value.getOrThrow()
    override fun isInitialized(): Boolean = result.isInitialized()
}

/** Reads/writes exchange an encoding thunk; they do not force its definition. */
internal class GhcEncodingState(
    locale: () -> GhcTextEncoding,
    filesystem: () -> GhcTextEncoding,
    foreign: () -> GhcTextEncoding,
    private val windowsArgv: Boolean,
) {
    val initLocaleEncoding = GhcTextEncoding.deferred(locale)
    val initFileSystemEncoding = GhcTextEncoding.deferred(filesystem)
    val initForeignEncoding = GhcTextEncoding.deferred(foreign)
    private val localeRef = AtomicReference(initLocaleEncoding)
    private val filesystemRef = AtomicReference(initFileSystemEncoding)
    private val foreignRef = AtomicReference(initForeignEncoding)
    private val utf8 by lazy { GhcTextEncoding.mkTextEncoding("UTF-8") }

    fun getLocaleEncoding(): GhcTextEncoding = localeRef.get()
    fun setLocaleEncoding(encoding: GhcTextEncoding) { localeRef.set(encoding) }
    fun getFileSystemEncoding(): GhcTextEncoding = filesystemRef.get()
    fun setFileSystemEncoding(encoding: GhcTextEncoding) { filesystemRef.set(encoding) }
    fun getForeignEncoding(): GhcTextEncoding = foreignRef.get()
    fun setForeignEncoding(encoding: GhcTextEncoding) { foreignRef.set(encoding) }
    fun argvEncoding(): GhcTextEncoding = if (windowsArgv) utf8 else getFileSystemEncoding()
}

/** Process-wide GHC settings. Initial encodings remain shared independently of
 * later writes. POSIX native initializers use the shared locale-name snapshot. */
object GhcEncodings {
    private fun initial(mode: GhcCodingFailureMode): GhcTextEncoding {
        if (Platform.isWindows()) throw NotImplementedError("GHC Windows CodePage initial encoding backend is not ported")
        return GhcTextEncoding.mkTextEncodingWithMode(GhcLocaleEncodingName.initial, mode)
    }
    private val state = GhcEncodingState(
        { initial(GhcCodingFailureMode.Error) },
        { initial(GhcCodingFailureMode.Roundtrip) },
        { initial(GhcCodingFailureMode.Ignore) },
        Platform.isWindows(),
    )
    val initLocaleEncoding: GhcTextEncoding get() = state.initLocaleEncoding
    val initFileSystemEncoding: GhcTextEncoding get() = state.initFileSystemEncoding
    val initForeignEncoding: GhcTextEncoding get() = state.initForeignEncoding
    fun getLocaleEncoding(): GhcTextEncoding = state.getLocaleEncoding()
    fun setLocaleEncoding(encoding: GhcTextEncoding) = state.setLocaleEncoding(encoding)
    fun getFileSystemEncoding(): GhcTextEncoding = state.getFileSystemEncoding()
    fun setFileSystemEncoding(encoding: GhcTextEncoding) = state.setFileSystemEncoding(encoding)
    fun getForeignEncoding(): GhcTextEncoding = state.getForeignEncoding()
    fun setForeignEncoding(encoding: GhcTextEncoding) = state.setForeignEncoding(encoding)
    fun argvEncoding(): GhcTextEncoding = state.argvEncoding()
}
