// crypton-pem 0.3.0; Vincent Hanquez 2010-2018.
// Full notice: licenses/crypton-pem-BSD-3-Clause.txt.
package io.heapy.kotdoc.pem

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import java.io.ByteArrayOutputStream

class PemSection(val name: CharacterString, headers: List<Pair<CharacterString, ByteArray>>, content: ByteArray) {
    internal val storedHeaders = headers.map { (k, v) -> k to v.copyOf() }
    internal val storedContent = content.copyOf()
    val headers: List<Pair<CharacterString, ByteArray>> get() = storedHeaders.map { (k, v) -> k to v.copyOf() }
    val content: ByteArray get() = storedContent.copyOf()
    override fun equals(other: Any?): Boolean = other is PemSection && name == other.name && storedContent.contentEquals(other.storedContent) &&
        storedHeaders.size == other.storedHeaders.size && storedHeaders.zip(other.storedHeaders).all { (a, b) -> a.first == b.first && a.second.contentEquals(b.second) }
    override fun hashCode() = 31 * (31 * name.hashCode() + storedHeaders.fold(1) { h, (k, v) -> 31 * h + 31 * k.hashCode() + v.contentHashCode() }) + storedContent.contentHashCode()
    override fun toString() = "PEM {pemName = ${HaskellString.quoteCodePoints(name.points())}, pemHeader = " +
        storedHeaders.joinToString(",", "[", "]") { (k, v) -> "(${HaskellString.quoteCodePoints(k.points())},${quoteBytes(v)})" } + ", pemContent = ${quoteBytes(storedContent)}}"
    private fun quoteBytes(bytes: ByteArray) = HaskellString.quoteCodePoints(bytes.map { it.toInt() and 255 }.toIntArray())
}
sealed interface PemParseResult {
    data class Success(val sections: List<PemSection>) : PemParseResult
    data class Failure(val message: String) : PemParseResult
}
object Pem {
    fun parse(bytes: ByteArray): PemParseResult = parseChunks(sequenceOf(bytes))
    fun parseChunks(chunks: List<ByteArray>): PemParseResult = parseChunks(chunks.asSequence())
    fun parseChunks(chunks: Sequence<ByteArray>): PemParseResult {
        val lines = sequence {
            var line = ByteArrayOutputStream()
            for (chunk in chunks) for (byte in chunk) {
                if (byte == 10.toByte()) { yield(line.toByteArray()); line = ByteArrayOutputStream() } else line.write(byte.toInt())
            }
            if (line.size() > 0) yield(line.toByteArray())
        }.map { if (it.lastOrNull() == 13.toByte()) it.copyOf(it.size - 1) else it }.iterator()
        val sections = mutableListOf<PemSection>()
        fun parseName(line: String, prefix: String): String? {
            val tail = line.substring(prefix.length); val dash = tail.indexOf('-')
            return if (dash >= 0 && tail.substring(dash) == "-----") tail.substring(0, dash) else null
        }
        while (lines.hasNext()) {
            val line = lines.next().toString(Charsets.ISO_8859_1)
            if (!line.startsWith("-----BEGIN ")) continue
            val name = parseName(line, "-----BEGIN ") ?: return PemParseResult.Failure("invalid PEM delimiter found")
            if (!lines.hasNext()) return PemParseResult.Failure("invalid PEM: no more content in header context")
            val content = ByteArrayOutputStream()
            while (true) {
                if (!lines.hasNext()) return PemParseResult.Failure("invalid PEM: no end marker found")
                val raw = lines.next(); val value = raw.toString(Charsets.ISO_8859_1)
                if (value.startsWith("-----END ")) {
                    val end = parseName(value, "-----END ") ?: return PemParseResult.Failure("invalid PEM delimiter found")
                    if (end != name) return PemParseResult.Failure("invalid PEM: end name doesn't match start name")
                    sections.add(PemSection(Chars.fromString(name), emptyList(), content.toByteArray())); break
                }
                when (val decoded = PemBase64.decode(raw)) {
                    is PemBase64.Decoded -> content.write(decoded.bytes)
                    is PemBase64.Invalid -> return PemParseResult.Failure("invalid PEM: decoding failed: ${decoded.message}")
                }
            }
        }
        return PemParseResult.Success(sections)
    }
    fun write(section: PemSection): ByteArray = ByteArrayOutputStream().also { out -> writeChunks(section).forEach(out::write) }.toByteArray()
    fun writeChunks(section: PemSection): List<ByteArray> = buildList {
        fun chars(s: CharacterString) = s.points().map(Int::toByte).toByteArray()
        val name = chars(section.name)
        add("-----BEGIN ".toByteArray() + name + "-----\n".toByteArray())
        if (section.storedHeaders.isNotEmpty()) add(ByteArrayOutputStream().apply {
            section.storedHeaders.forEach { (k, v) -> write(chars(k)); write(58); write(v); write(10) }; write(10)
        }.toByteArray())
        val content = section.storedContent; var i = 0
        do { val end = minOf(i + 48, content.size); add(PemBase64.encode(content.copyOfRange(i, end)) + byteArrayOf(10)); i = end } while (i < content.size)
        add("-----END ".toByteArray() + name + "-----\n".toByteArray())
    }
}
