// Codec.Archive.Zip 0.5, © 2008–2012 John MacFarlane, BSD-3-Clause.
// Complete original source/notice: vendor/zip-archive-0.5 and licenses/.
package io.heapy.kotdoc.archive

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.Utf8Text
import io.heapy.kotdoc.templates.PartialPaths
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.DataFormatException

enum class ZipCompression { Deflate, NoCompression }
sealed interface ZipEncryption {
    data object NoEncryption : ZipEncryption
    class PKWAREEncryption(control: Int) : ZipEncryption {
        val control = control and 255
        override fun equals(other: Any?) = other is PKWAREEncryption && control == other.control
        override fun hashCode() = control
    }
}
class ZipEntry(
    val path: CharacterString,
    val compression: ZipCompression,
    val encryption: ZipEncryption = ZipEncryption.NoEncryption,
    val modified: BigInteger = BigInteger.ZERO,
    crc: Long, compressedSize: Long, uncompressedSize: Long,
    extra: ByteArray = byteArrayOf(), comment: ByteArray = byteArrayOf(),
    versionMadeBy: Int = 0, internalAttributes: Int = 0, externalAttributes: Long = 0,
    data: ByteArray,
) {
    val crc = crc and 0xffffffffL
    val compressedSize = compressedSize and 0xffffffffL
    val uncompressedSize = uncompressedSize and 0xffffffffL
    val versionMadeBy = versionMadeBy and 65535
    val internalAttributes = internalAttributes and 65535
    val externalAttributes = externalAttributes and 0xffffffffL
    private val extraBytes = extra.copyOf();private val commentBytes = comment.copyOf();private val dataBytes = data.copyOf()
    val extra: ByteArray get() = extraBytes.copyOf()
    val comment: ByteArray get() = commentBytes.copyOf()
    val data: ByteArray get() = dataBytes.copyOf()
    val encrypted: Boolean get() = encryption != ZipEncryption.NoEncryption
    val cMode: Int get() = (externalAttributes ushr 16).toInt()
    val symbolicLink: Boolean get() = cMode and 0xa000 == 0xa000
    override fun equals(other: Any?) = other is ZipEntry && path == other.path && compression == other.compression &&
        encryption == other.encryption && modified == other.modified && crc == other.crc && compressedSize == other.compressedSize &&
        uncompressedSize == other.uncompressedSize && versionMadeBy == other.versionMadeBy && internalAttributes == other.internalAttributes &&
        externalAttributes == other.externalAttributes && extraBytes.contentEquals(other.extraBytes) && commentBytes.contentEquals(other.commentBytes) && dataBytes.contentEquals(other.dataBytes)
    override fun hashCode() = listOf(path,compression,encryption,modified,crc,compressedSize,uncompressedSize,versionMadeBy,internalAttributes,externalAttributes,
        extraBytes.contentHashCode(),commentBytes.contentHashCode(),dataBytes.contentHashCode()).hashCode()
}
class ZipArchive(entries: List<ZipEntry> = emptyList(), signature: ByteArray? = null, comment: ByteArray = byteArrayOf()) {
    val entries: List<ZipEntry> = Collections.unmodifiableList(ArrayList(entries))
    private val signatureBytes = signature?.copyOf();private val commentBytes = comment.copyOf()
    val signature: ByteArray? get() = signatureBytes?.copyOf()
    val comment: ByteArray get() = commentBytes.copyOf()
    fun files(): List<CharacterString> = entries.map { it.path }
    fun delete(path: CharacterString, platform: PartialPaths = PartialPaths.System) =
        ZipArchive(entries.filter { ZipCodec.normalize(it.path,platform) != ZipCodec.normalize(path,platform) },signatureBytes,commentBytes)
    fun add(entry: ZipEntry, platform: PartialPaths = PartialPaths.System) = ZipArchive(listOf(entry)+delete(entry.path,platform).entries,signatureBytes,commentBytes)
    fun find(path: CharacterString, platform: PartialPaths = PartialPaths.System) = entries.firstOrNull { ZipCodec.normalize(it.path,platform) == ZipCodec.normalize(path,platform) }
}
class ZipLimitException(val detail: CharacterString) : Exception() {
    override val message: String get() = "Zip64NotSupported ${HaskellString.quoteCodePoints(detail.points())}"
}
class ZipDecodeException(override val message: String) : Exception(message)
sealed interface ZipDecodeResult {
    data class Success(val archive: ZipArchive, val consumed: Long) : ZipDecodeResult
    data class Failure(val error: String) : ZipDecodeResult
}

/** The original pure ZIP record codec and entry operations. Platform IO is separate. */
object ZipCodec {
    private fun chars(s: String) = CharacterStringChars.fromString(s)
    // FilePath's structural operators inspect only ASCII syntax. Escape every
    // non-ASCII Char reversibly before delegating, so two raw surrogate Chars
    // cannot collapse into a supplementary Char in JVM String conversion.
    private fun escaped(path: CharacterString) = buildString { path.points().forEach { p ->
        if (p < 128) append(p.toChar()) else { append('\ue000');append(p.toString(16).padStart(6,'0')) }
    } }
    private fun unescaped(s: String): CharacterString {
        val out = mutableListOf<Int>();var at = 0
        while (at < s.length) if (s[at] == '\ue000') { out.add(s.substring(at+1,at+7).toInt(16));at+=7 } else out.add(s[at++].code)
        return CharacterString(out.toIntArray())
    }
    fun normalize(path: CharacterString, platform: PartialPaths = PartialPaths.System): CharacterString {
        val encoded = escaped(path);var directory = platform.directory(encoded)
        if (platform == PartialPaths.Windows) {
            val d = unescaped(directory).points()
            if (d.size >= 3 && ParserCharacters.isAlpha(d[0]) && d[1] == 58 && d[2] in intArrayOf(47,92))
                directory = escaped(CharacterString(d.copyOfRange(3,d.size)))
        }
        return unescaped((platform.directories(directory).filter { it != "." } + platform.fileName(encoded)).joinToString("/"))
    }
    fun toEntry(path: CharacterString, modified: BigInteger, bytes: ByteArray, platform: PartialPaths = PartialPaths.System): ZipEntry {
        val raw = bytes.copyOf();val compressed = deflate(raw)
        val method = if (compressed.size < raw.size) ZipCompression.Deflate else ZipCompression.NoCompression
        val payload = if (method == ZipCompression.Deflate) compressed else raw
        return ZipEntry(normalize(path,platform),method,modified=modified,crc=CRC32().also { it.update(raw) }.value,
            compressedSize=payload.size.toLong(),uncompressedSize=raw.size.toLong(),data=payload)
    }
    private fun deflate(raw: ByteArray): ByteArray {
        val z = Deflater(Deflater.DEFAULT_COMPRESSION,true)
        try {
            z.setInput(raw);z.finish();val out = ByteArrayOutputStream();val buffer = ByteArray(32768)
            while (!z.finished()) out.write(buffer,0,z.deflate(buffer))
            return out.toByteArray()
        } finally { z.end() }
    }
    /** Return decoded bytes and compressed input consumption, excluding trailing data. */
    private fun inflate(raw: ByteArray, discard: Boolean = false): Pair<ByteArray,Int> {
        val z = Inflater(true)
        try {
            z.setInput(raw);val out = ByteArrayOutputStream();val buffer = ByteArray(32768)
            while (!z.finished()) {
                val size = try { z.inflate(buffer) } catch (e: DataFormatException) { throw ZipDecodeException("Codec.Compression.Zlib: compressed data stream format error (${e.message ?: ""})") }
                if (!discard) out.write(buffer,0,size)
                if (size == 0 && !z.finished()) {
                    if (z.needsDictionary()) throw ZipDecodeException("Codec.Compression.Zlib: compressed data stream requires custom dictionary")
                    if (z.needsInput()) throw ZipDecodeException("Codec.Compression.Zlib: premature end of compressed data stream")
                }
            }
            return out.toByteArray() to (raw.size-z.remaining)
        } finally { z.end() }
    }
    fun fromEntry(entry: ZipEntry): ByteArray = if (entry.compression == ZipCompression.NoCompression) entry.data else inflate(entry.data).first
    fun symbolicLinkTarget(entry: ZipEntry): CharacterString? = if (entry.symbolicLink)
        CharacterString(fromEntry(entry).map { it.toInt() and 255 }.toIntArray()) else null
    private val crcTable = LongArray(256) { value ->
        var n = value.toLong();repeat(8) { n = (n ushr 1) xor if (n and 1L != 0L) 0xedb88320L else 0L };n
    }
    fun fromEncryptedEntry(password: CharacterString, entry: ZipEntry): ByteArray? {
        val encryption = entry.encryption
        if (encryption == ZipEncryption.NoEncryption) return fromEntry(entry)
        val raw = entry.data
        if (raw.size < 12) return null
        var k0=305419896L;var k1=591751049L;var k2=878082192L
        fun crc(k: Long,b: Int) = (k ushr 8) xor crcTable[((k xor b.toLong()) and 255).toInt()]
        fun update(b: Int) { k0=crc(k0,b);k1=((k1+(k0 and 255))*134775813+1) and 0xffffffffL;k2=crc(k2,(k1 ushr 24).toInt()) }
        password.points().forEach { update(it and 255) }
        val decoded=ByteArray(raw.size)
        for (i in raw.indices) {
            val tmp=k2 or 2;val mask=(((tmp*(tmp xor 1)) and 0xffffffffL) ushr 8).toInt() and 255
            val b=(raw[i].toInt() and 255) xor mask;decoded[i]=b.toByte();update(b)
        }
        if ((decoded[11].toInt() and 255) != (encryption as ZipEncryption.PKWAREEncryption).control) return null
        val content=decoded.copyOfRange(12,decoded.size)
        return if (entry.compression == ZipCompression.NoCompression) content else inflate(content).first
    }
    data class DosTime(val date: Int, val time: Int)
    fun toDosTime(epoch: BigInteger): DosTime {
        val time=epoch.coerceIn(BigInteger.valueOf(315532800),BigInteger.valueOf(4354819198)).toLong()
        val date=Instant.ofEpochSecond(time).atOffset(ZoneOffset.UTC)
        return DosTime(date.dayOfMonth+(date.monthValue shl 5)+((date.year-1980) shl 9),date.second/2+(date.minute shl 5)+(date.hour shl 11))
    }
    fun fromDosTime(date: Int, time: Int): BigInteger {
        val year=1980+((date and 65535) ushr 9);val month=((date ushr 5) and 15).coerceIn(1,12)
        val base=LocalDate.of(year,month,1);val day=(date and 31).coerceIn(1,base.lengthOfMonth())
        val seconds=2*(time and 31)+60*((time ushr 5) and 63)+3600*((time and 65535) ushr 11)
        return BigInteger.valueOf(base.withDayOfMonth(day).toEpochDay()*86400+minOf(seconds,86400))
    }
    private class Writer : ByteArrayOutputStream() {
        fun u16(v: Int) { write(v);write(v ushr 8) }
        fun u32(v: Long) { repeat(4) { write((v ushr (8*it)).toInt()) } }
        fun bytes(v: ByteArray) = write(v)
    }
    fun encode(archive: ZipArchive, platform: PartialPaths = PartialPaths.System): ByteArray {
        if (archive.entries.size >= 65535) throw ZipLimitException(chars("65535 or more entries require ZIP64"))
        val out=Writer();val offsets=mutableListOf<Long>()
        fun name(e: ZipEntry)=TextChars.fromPoints(normalize(e.path,platform).points()).toByteArray(Charsets.UTF_8)
        fun common(e: ZipEntry) {
            out.u16(20);out.u16(0x800);out.u16(if(e.compression==ZipCompression.Deflate) 8 else 0)
            val d=toDosTime(e.modified);out.u16(d.time);out.u16(d.date)
            out.u32(e.crc);out.u32(e.compressedSize);out.u32(e.uncompressedSize)
        }
        for (entry in archive.entries) {
            offsets.add(out.size().toLong());val n=name(entry)
            out.u32(0x04034b50);common(entry);out.u16(n.size);out.u16(entry.extra.size);out.bytes(n);out.bytes(entry.extra);out.bytes(entry.data)
        }
        val cdOffset=out.size().toLong()
        var cdSize=0L
        for ((index,entry) in archive.entries.withIndex()) {
            val n=name(entry)
            out.u32(0x02014b50);out.u16(entry.versionMadeBy);common(entry)
            out.u16(n.size);out.u16(entry.extra.size);out.u16(entry.comment.size);out.u16(0);out.u16(entry.internalAttributes)
            out.u32(entry.externalAttributes);out.u32(offsets[index]);out.bytes(n);out.bytes(entry.extra);out.bytes(entry.comment)
            cdSize=(cdSize+46+n.size+entry.extra.size+entry.comment.size) and 0xffffffffL
        }
        archive.signature?.let { out.u32(0x05054b50);out.u16(it.size);out.bytes(it) }
        out.u32(0x06054b50);out.u16(0);out.u16(0);out.u16(archive.entries.size);out.u16(archive.entries.size)
        out.u32(cdSize);out.u32(cdOffset);out.u16(archive.comment.size);out.bytes(archive.comment)
        return out.toByteArray()
    }
    private class Reader(val bytes: ByteArray) {
        var at=0
        fun need(n: Int) { if(n < 0 || n > bytes.size-at) throw ZipDecodeException("not enough bytes") }
        fun u8(): Int { need(1);return bytes[at++].toInt() and 255 }
        fun u16()=u8() or (u8() shl 8)
        fun u32()=u16().toLong() or (u16().toLong() shl 16)
        fun peek(): Long { val old=at;return try { u32() } finally { at=old } }
        fun skip(n: Int) { need(n);at+=n }
        fun take(n: Long): ByteArray { if(n>Int.MAX_VALUE) throw ZipDecodeException("not enough bytes");need(n.toInt());return bytes.copyOfRange(at,at+n.toInt()).also { at+=n.toInt() } }
        fun method(): ZipCompression = when(val m=u16()) { 0->ZipCompression.NoCompression;8->ZipCompression.Deflate;else->throw ZipDecodeException("Unknown compression method $m") }
    }
    // Preserve the pinned GHC/clang CPP result, including its string-gap
    // line splicing. Those continuation prefixes become literal spaces,
    // backslashes and decimal digits; replacing them with a standard CP437
    // table changes the retained implementation's observable filenames.
    private val cp437 = intArrayOf(199,252,233,226,228,224,229,231,234,235,232,239,238,236,196,197) +
        "  \\201".codePoints().toArray() + intArrayOf(230,198,244,246,242,251,249,255,214,220,162,163,165,8359,402) +
        "  \\225".codePoints().toArray() + intArrayOf(237,243,250,241,209,170,186,191,8976,172,189,188,161,171,187) +
        "  \\9617".codePoints().toArray() + intArrayOf(9618,9619,9474,9508,9569,9570,9558,9557,9571,9553,9559,9565,9564,9563,9488) +
        "  \\9492".codePoints().toArray() + intArrayOf(9524,9516,9500,9472,9532,9566,9567,9562,9556,9577,9574,9568,9552,9580,9575) +
        "  \\9576".codePoints().toArray() + intArrayOf(9572,9573,9561,9560,9554,9555,9579,9578,9496,9484,9608,9604,9612,9616,9600) +
        "  \\945".codePoints().toArray() + intArrayOf(223,915,960,931,963,181,964,934,920,937,948,8734,966,949,8745) +
        "  \\8801".codePoints().toArray() + intArrayOf(177,8805,8804,8992,8993,247,8776,176,8729,183,8730,8319,178,9632,160)
    private fun fileName(flags: Int,bytes: ByteArray) = if(flags and 0x800 != 0) chars(Utf8Text.decodeLenient(bytes))
        else CharacterString(bytes.map { val b=it.toInt() and 255;if(b<128) b else cp437[b-128] }.toIntArray())
    fun decode(bytes: ByteArray): ZipDecodeResult = try {
        val r=Reader(bytes.copyOf());val locals=mutableMapOf<Long,ByteArray>()
        while(r.peek()==0x04034b50L) {
            val offset=r.at.toLong();r.u32();r.skip(2);val flags=r.u16();val method=r.method();r.skip(8);val size=r.u32()
            if(size==0xffffffffL) throw ZipDecodeException("Can't read ZIP64 archive.")
            r.skip(4);val name=r.u16();val extra=r.u16();r.skip(name);r.skip(extra)
            val data=if(flags and 8 == 0) r.take(size) else {
                val n=if(method==ZipCompression.Deflate) inflate(r.bytes.copyOfRange(r.at,r.bytes.size),true).second else {
                    var cursor=r.at;var found=-1
                    fun next(): Int { if(cursor>=r.bytes.size) throw ZipDecodeException("getCompressedData can't find data descriptor signature");return r.bytes[cursor++].toInt() and 255 }
                    while(found<0) if(next()==0x50 && next()==0x4b && next()==7 && next()==8) found=cursor-4
                    found-r.at
                }
                val payload=r.take(n.toLong());if(r.peek()==0x08074b50L) r.skip(4)
                r.skip(4);val declared=r.u32();r.skip(4)
                if(declared!=payload.size.toLong()) throw ZipDecodeException("Content size mismatch in data descriptor record: expected $declared, got ${payload.size} bytes")
                payload
            }
            locals[offset]=data
        }
        val entries=mutableListOf<ZipEntry>()
        while(r.peek()==0x02014b50L) {
            r.u32();val version=r.u16();val needed=r.u8();r.skip(1)
            if(needed>20) throw ZipDecodeException("This archive requires zip >= 2.0 to extract.")
            val flags=r.u16();val method=r.method();val time=r.u16();val date=r.u16();val crc=r.u32()
            val encryption=if(flags and 1 == 0) ZipEncryption.NoEncryption else {
                if(flags and 64 != 0) throw ZipDecodeException("Strong encryption is not supported")
                ZipEncryption.PKWAREEncryption(if(flags and 8 == 0) (crc ushr 24).toInt() else time ushr 8)
            }
            val compressed=r.u32();val uncompressed=r.u32();val name=r.u16();val extra=r.u16();val comment=r.u16()
            r.skip(2);val internal=r.u16();val external=r.u32();val offset=r.u32()
            val path=fileName(flags,r.take(name.toLong()));val extraBytes=r.take(extra.toLong());val commentBytes=r.take(comment.toLong())
            val data=locals[offset] ?: throw ZipDecodeException("Unable to find data at offset $offset")
            entries.add(ZipEntry(path,method,encryption,fromDosTime(date,time),crc,compressed,uncompressed,extraBytes,commentBytes,version,internal,external,data))
        }
        val beforeSignature=r.at
        val signature=try { if(r.u32()!=0x05054b50L) throw ZipDecodeException("ensure not satisfied");r.take(r.u16().toLong()) }
            catch(e: ZipDecodeException) { r.at=beforeSignature;null }
        if(r.u32()!=0x06054b50L) throw ZipDecodeException("Did not find end of central directory signature")
        r.skip(16);val comment=r.take(r.u16().toLong())
        ZipDecodeResult.Success(ZipArchive(entries,signature,comment),r.at.toLong())
    } catch(e: ZipDecodeException) { ZipDecodeResult.Failure(e.message) }
}
