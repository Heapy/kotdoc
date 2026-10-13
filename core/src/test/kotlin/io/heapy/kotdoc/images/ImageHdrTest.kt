package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class ImageHdrTest {
    private val nativePlatform: String get() {
        val os = System.getProperty("os.name"); val arch = System.getProperty("os.arch")
        return when {
            os.startsWith("Mac") && arch in listOf("aarch64", "arm64") -> "macos-aarch64"
            os.startsWith("Linux") && arch in listOf("amd64", "x86_64") -> "linux-x86_64"
            else -> error("No original HDR reference for $os/$arch")
        }
    }
    private fun lines(platform: String) = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/hdr-$platform.jsonl.gz"))).bufferedReader().use { it.readLines().drop(1) }
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
    private fun generated(kind: String): List<UInt> {
        val special = listOf(0u,0x80000000u,1u,0x80000001u,0x7fffffu,0x800000u,0x3f000000u,0xbf000000u,0x3f800000u,0xbf800000u,0x40000000u,0xc0000000u,0x7f7fffffu,0xff7fffffu,0x7f800000u,0xff800000u,0x7f800001u,0x7fc00000u,0xffc01234u)
        return when (kind) {
            "patterns" -> {
                val patterns = (0..1).flatMap { s -> (0..255).flatMap { e -> listOf(0,1,0x3fffff,0x400000,0x7ffffe,0x7fffff).map { m -> ((s shl 31) or (e shl 23) or m).toUInt() } } }
                patterns.indices.flatMap { i -> (0..<3).map { c -> patterns[(i + c * 257) % patterns.size] } }
            }
            "lcg" -> { var state = 0x484452u; List(100002) { state = state * 1664525u + 1013904223u; (state and 0x7fffffffu) % 0x7f800000u } }
            "cube" -> special.flatMap { r -> special.flatMap { g -> special.flatMap { b -> listOf(r, g, b) } } }
            else -> error(kind)
        }
    }
    private fun canonical(data: ByteArray): ByteArray {
        val out = data.copyOf()
        for (i in out.indices step 4) {
            val value = (out[i].toInt() and 255) or ((out[i+1].toInt() and 255) shl 8) or ((out[i+2].toInt() and 255) shl 16) or (out[i+3].toInt() shl 24)
            if (value and 0x7f800000 == 0x7f800000 && value and 0x7fffff != 0) {
                out[i] = 0; out[i+1] = 0; out[i+2] = 0xc0.toByte(); out[i+3] = 0x7f
            }
        }
        return out
    }
    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun serialized(vector: RasterVector<Float, FloatArray>): ByteArray = ByteArray(Math.toIntExact(vector.length * 4)) { i -> (vector[(i / 4).toLong()].toRawBits() ushr ((i % 4) * 8)).toByte() }
    private fun check(c: JsonObject) {
        val mode = c.s("mode"); val op = c.s("op"); val observe = c.s("observe")
        val p = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }
        val (w, h, parameter, offset, length) = p
        val values = if (c.s("kind") == "values") c.getValue("values").jsonArray.map { it.jsonPrimitive.content.toUInt() } else generated(c.s("kind"))
        fun parameter(): Float { if (mode == "bad-parameter") error("parameter forced"); return Float.fromBits(parameter.toInt()) }
        val actual = runCatching {
            if (mode == "bad-image") error("image forced")
            val src = RasterImage(w, h, RasterCodecs.RGBF, lazy {
                if (mode == "bad-data") error("data forced")
                RasterVector(RasterStorages.Floats, FloatArray(values.size) { Float.fromBits(values[it].toInt()) }).slice(offset, length)
            })
            val out = when (op) {
                "gamma" -> ImageHdr.gammaCorrection(::parameter, src)
                "tone" -> ImageHdr.toneMapping(::parameter, src)
                "tone-pixels" -> RasterImage(w, h, RasterCodecs.RGBF, lazy {
                    val data = FloatArray(Math.toIntExact(length))
                    for (at in 0L..<length step 3) {
                        val pixel = RasterImage(1, 1, RasterCodecs.RGBF, src.vector.slice(at, 3))
                        val mapped = ImageHdr.toneMapping(::parameter, pixel).vector
                        for (channel in 0..<3) data[Math.toIntExact(at) + channel] = mapped[channel.toLong()]
                    }
                    RasterVector(RasterStorages.Floats, data)
                })
                else -> error(op)
            }
            Triple(out.width, out.height, if (observe == "metadata") null else serialized(out.vector))
        }
        val expected = c.getValue("expected").jsonObject
        if ("exception" in expected) {
            assertTrue(actual.isFailure)
            assertEquals(expected.s("exception"), actual.exceptionOrNull()?.message?.lineSequence()?.first())
            return
        }
        val (width, height, data) = actual.getOrThrow()
        assertEquals(expected.getValue("width").jsonPrimitive.long, width)
        assertEquals(expected.getValue("height").jsonPrimitive.long, height)
        if (data != null) {
            assertEquals(expected.getValue("length").jsonPrimitive.int, data.size)
            // Host powf is checked bit exactly, including NaN bits. For pure tone
            // arithmetic only NaN payload/sign may vary; all other bits stay exact.
            assertEquals(expected.s(if (op == "gamma") "sha256" else "canonicalSha256"), sha(if (op == "gamma") data else canonical(data)))
        }
    }
    private fun tests(platform: String, toneOnly: Boolean): Stream<DynamicTest> = lines(platform).mapNotNull { line ->
        val header = Json.parseToJsonElement(line).jsonObject
        if (toneOnly && header.s("op") == "gamma") null else DynamicTest.dynamicTest("$platform ${header.s("name")}") { check(Json.parseToJsonElement(line).jsonObject) }
    }.asSequence().asStream()
    @TestFactory fun originalHostHdr() = tests(nativePlatform, false)
    @TestFactory fun originalOtherPlatformTone() = tests(if (nativePlatform == "macos-aarch64") "linux-x86_64" else "macos-aarch64", true)
    @Test fun bothPlatformsUseIdenticalRequests() {
        fun requests(platform: String) = lines(platform).map { Json.parseToJsonElement(it).jsonObject.filterKeys { key -> key != "expected" } }
        assertEquals(requests("macos-aarch64"), requests("linux-x86_64"))
    }
}
