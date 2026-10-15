// Text.Pandoc.UTF8.toText and text-2.1.2 strict decoding.
// © 2006-2024 John MacFarlane, GPL-2.0-or-later; text BSD-2-Clause notices in licenses/.
package io.heapy.kotdoc.resources

/** A host UnicodeException is distinct from the PandocMonad typed UTF-8 error. */
class UnicodeDecodeException(val byte: Int) : RuntimeException(
    "Cannot decode byte '\\x${byte.toString(16).padStart(2,'0')}': Data.Text.Encoding: Invalid UTF-8 stream",
)
object PandocUtf8 {
    fun toText(bytes: ByteArray): String {
        val bom=bytes.size>=3 && bytes[0]==0xef.toByte() && bytes[1]==0xbb.toByte() && bytes[2]==0xbf.toByte()
        val filtered=if(!bom && 13.toByte() !in bytes)bytes else bytes.copyOfRange(if(bom)3 else 0,bytes.size).filter { it!=13.toByte() }.toByteArray()
        return decodeStrict(filtered)
    }
    private fun decodeStrict(bytes: ByteArray): String = buildString {
        var at=0
        while(at<bytes.size) {
            val lead=bytes[at].toInt() and 255
            if(lead<128) {appendCodePoint(lead);at++;continue}
            val n=when(lead) { in 0xc2..0xdf->2;in 0xe0..0xef->3;in 0xf0..0xf4->4;else->throw UnicodeDecodeException(lead) }
            var cp=lead and when(n) {2->31;3->15;else->7}
            for(i in 1..<n) {
                if(at+i==bytes.size) {
                    // skipIncomplete folds the buffered bytes through the strict
                    // builder. Its strict right argument forces the last byte's
                    // error first, unlike the invalid-sequence path.
                    throw UnicodeDecodeException(bytes.last().toInt() and 255)
                }
                val b=bytes[at+i].toInt() and 255
                if(b !in 0x80..0xbf || i==1 && when(lead) {0xe0->b<0xa0;0xed->b>0x9f;0xf0->b<0x90;0xf4->b>0x8f;else->false})throw UnicodeDecodeException(lead)
                cp=(cp shl 6) or (b and 63)
            }
            appendCodePoint(cp);at+=n
        }
    }
}
