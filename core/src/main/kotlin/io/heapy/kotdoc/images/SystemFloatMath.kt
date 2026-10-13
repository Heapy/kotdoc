package io.heapy.kotdoc.images

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

internal interface SystemFloatFunctions : Library {
    fun powf(x: Float, y: Float): Float
}

/** GHC's powerFloat# calls the platform's float32 powf, not double pow. */
internal object SystemFloatMath {
    private val functions by lazy {
        val library = when {
            Platform.isMac() -> "System"
            Platform.isWindows() -> "ucrtbase"
            else -> "m"
        }
        Native.load(library, SystemFloatFunctions::class.java)
    }
    fun pow(x: Float, y: Float): Float = functions.powf(x, y)
}
