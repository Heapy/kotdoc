// Text.Pandoc.ImageSize: Copyright (C) 2011–2024 John MacFarlane.
// Translated from Pandoc 3.12.1, GPL-2.0-or-later.
package io.heapy.kotdoc.images

import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.xml.XmlLight
import io.heapy.kotdoc.xml.XmlParseException
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** The SVG/EPS/PDF parsers; full bitmap decoder dispatch is a separate dependency. */
object DocumentImageSize {
    fun svgSize(bytes: ByteArray, dpi: BigInteger = 96.toBigInteger()): ImageSize? {
        // svgSize's dropBOM followed by UTF8.toText's own dropBOM removes up to
        // two prefixes. UTF8.toText removes CR bytes before strict decoding.
        var start = 0
        repeat(2) {
            if (bytes.size-start >= 3 && bytes[start] == 0xef.toByte() && bytes[start+1] == 0xbb.toByte() && bytes[start+2] == 0xbf.toByte()) start += 3
        }
        val normalized = bytes.copyOfRange(start,bytes.size).filterNot { it == 13.toByte() }.toByteArray()
        val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(normalized)).toString()
        val root = try { XmlLight.parseElement(text) } catch (_: XmlParseException) { return null }
        val viewBox by lazy {
            root.attr("viewBox")?.replace(',',' ')?.split(HaskellNumber::isSpace)?.let { parts ->
                if (parts.size != 4) null else {
                    val numbers = parts.map { HaskellNumber.readDouble(it) }
                    if (numbers.any { it == null }) null else numbers.map { it!! }
                }
            }
        }
        fun dim(name: String): BigInteger? = root.attr(name)?.let(Dimensions::lengthToDim)
            ?.takeUnless { it is Dimension.Percent }?.let { Dimensions.inPixel(it,dpi) }
        val w = dim("width") ?: viewBox?.let { HaskellNumber.floor(it[2]) } ?: return null
        val h = dim("height") ?: viewBox?.let { HaskellNumber.floor(it[3]) } ?: return null
        return ImageSize(w,h,dpi,dpi)
    }

    fun epsSize(bytes: ByteArray): ImageSize? {
        val lines = bytes.toString(Charsets.ISO_8859_1).split('\n').takeWhile { it.startsWith('%') }
        val first = lines.firstOrNull { it.startsWith("%%BoundingBox:") } ?: return null
        // ByteString.Char8.words also treats byte 0xa0 as whitespace;
        // Attoparsec.Char8.skipSpace used by the PDF parser does not.
        val parts = first.split { byteSpace(it) || it == '\u00a0' }
        if (parts.size != 5) return null
        val coordinates = parts.drop(1).map { raw ->
            HaskellNumber.readInteger(raw.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)) ?: return null
        }
        return ImageSize(coordinates[2]-coordinates[0],coordinates[3]-coordinates[1])
    }

    fun pdfSize(bytes: ByteArray): ImageSize? {
        data class Frame(val text: String, val position: Int)
        val pending = ArrayDeque<Frame>()
        pending.addLast(Frame(bytes.toString(Charsets.ISO_8859_1),0))
        while (pending.isNotEmpty()) {
            val frame = pending.removeLast()
            val text = frame.text
            var pos = frame.position
            while (pos < text.length) {
                val slash = text.indexOf('/',pos)
                if (slash < 0) break
                if (text.startsWith("/MediaBox",slash)) mediaBox(text,slash+9)?.let { return it }
                if (text.startsWith("/Type",slash)) {
                    val type = skipSpace(text,slash+5)
                    if (text.startsWith("/ObjStm",type)) {
                        val stream = objectStream(text,type+7)
                        if (stream != null) {
                            // Attoparsec backtracks to the original slash if neither
                            // the decompressed stream nor the following input succeeds.
                            pending.addLast(Frame(text,slash+1))
                            pending.addLast(Frame(text,stream.second))
                            decompress(stream.first)?.let { pending.addLast(Frame(it.toString(Charsets.ISO_8859_1),0)) }
                            break
                        }
                    }
                }
                pos = slash+1
            }
        }
        return null
    }

    private fun mediaBox(text: String, start: Int): ImageSize? {
        var pos = skipSpace(text,start)
        if (text.getOrNull(pos) != '[') return null
        pos++
        val coordinates = ArrayList<BigInteger>(4)
        repeat(4) {
            pos = skipSpace(text,pos)
            val begin = pos
            while (pos < text.length && (text[pos] in '0'..'9' || text[pos] == '.')) pos++
            val number = HaskellNumber.readDouble(text.substring(begin,pos)) ?: return null
            coordinates.add(HaskellNumber.floor(number))
        }
        pos = skipSpace(text,pos)
        if (text.getOrNull(pos) != ']') return null
        return ImageSize(coordinates[2]-coordinates[0],coordinates[3]-coordinates[1])
    }
    private fun objectStream(text: String, start: Int): Pair<ByteArray, Int>? {
        var pos = start
        while (pos < text.length) {
            if (text.startsWith("stream",pos) && text.getOrNull(pos+6)?.let(::eol) == true) {
                pos += 6
                while (pos < text.length && eol(text[pos])) pos++
                val end = text.indexOf("endstream",pos)
                if (end < 0) return null
                return text.substring(pos,end).toByteArray(Charsets.ISO_8859_1) to (end+9)
            }
            while (pos < text.length && !eol(text[pos])) pos++
            if (pos == text.length) return null
            while (pos < text.length && eol(text[pos])) pos++
        }
        return null
    }
    private fun decompress(bytes: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes)
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n > 0) result.write(buffer,0,n)
                else if (!inflater.finished()) return null
            }
            result.toByteArray()
        } catch (_: DataFormatException) { null } finally { inflater.end() }
    }
    private fun eol(c: Char): Boolean = c == '\r' || c == '\n'
    private fun byteSpace(c: Char): Boolean = c == ' ' || c in '\t'..'\r'
    private fun skipSpace(text: String, start: Int): Int {
        var pos=start
        while (pos < text.length && byteSpace(text[pos])) pos++
        return pos
    }
    private fun String.split(space: (Char) -> Boolean): List<String> {
        val result=mutableListOf<String>()
        var pos=0
        while (pos < length) {
            while (pos < length && space(this[pos])) pos++
            val start=pos
            while (pos < length && !space(this[pos])) pos++
            if (pos > start) result.add(substring(start,pos))
        }
        return result
    }
}
