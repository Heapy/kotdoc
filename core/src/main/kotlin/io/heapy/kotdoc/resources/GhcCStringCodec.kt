// GHC 9.12.2 encoding/C-string interface. University of Glasgow; BSD notice retained.
package io.heapy.kotdoc.resources

internal interface GhcCStringCodec {
    fun decode(bytes: ByteArray): IntArray
    fun encodeCString(points: IntArray, nullTerminate: Boolean = true): ByteArray
}
