package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.pem.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
private fun JsonObject.bytes(k: String) = getValue(k).jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
private fun byteJSON(bytes: ByteArray) = JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })
private fun pointJSON(chars: CharacterString) = JsonArray(chars.points().map(::JsonPrimitive))
private fun left(value: String) = buildJsonObject { put("left", value) }
private fun right(value: JsonElement) = buildJsonObject { put("right", value) }

class CertificateBinaryTest {
    private fun pemJSON(section: PemSection): JsonElement = buildJsonObject {
        put("name", pointJSON(section.name)); put("headers", JsonArray(section.headers.map { (k, v) -> JsonArray(listOf(pointJSON(k), byteJSON(v))) })); put("content", byteJSON(section.content))
    }
    private fun pemResult(result: PemParseResult): JsonElement = when (result) {
        is PemParseResult.Success -> right(JsonArray(result.sections.map(::pemJSON)))
        is PemParseResult.Failure -> left(result.message)
    }
    private fun readPem(v: JsonObject) = PemSection(CharacterString(v.getValue("name").jsonArray.map { it.jsonPrimitive.int }.toIntArray()),
        v.getValue("headers").jsonArray.map { h -> val parts = h.jsonArray; CharacterString(parts[0].jsonArray.map { it.jsonPrimitive.int }.toIntArray()) to parts[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray() }, v.bytes("content"))
    private fun lengthJSON(length: Asn1Length): JsonElement = JsonArray(when (length) {
        is Asn1Length.Short -> listOf(0L, length.value)
        is Asn1Length.LongForm -> listOf(1L, length.octets, length.value)
        Asn1Length.Indefinite -> listOf(2L)
    }.map(::JsonPrimitive))
    private fun headerJSON(header: Asn1Header): JsonElement = buildJsonObject {
        put("class", header.kind.ordinal); put("tag", header.tag); put("constructed", header.constructed); put("length", lengthJSON(header.length))
    }
    private fun readHeader(v: JsonObject): Asn1Header {
        val length = v.getValue("length").jsonArray.map { it.jsonPrimitive.long }
        return Asn1Header(Asn1Class.entries[v.getValue("class").jsonPrimitive.int], v.getValue("tag").jsonPrimitive.long, v.getValue("constructed").jsonPrimitive.boolean,
            when (length[0]) { 0L -> Asn1Length.Short(length[1]); 1L -> Asn1Length.LongForm(length[1], length[2]); else -> Asn1Length.Indefinite })
    }
    private fun eventJSON(event: Asn1Event): JsonElement = when (event) {
        is Asn1Event.Header -> buildJsonObject { put("header", headerJSON(event.header)) }
        is Asn1Event.Primitive -> buildJsonObject { put("primitive", byteJSON(event.bytes)) }
        Asn1Event.ConstructionBegin -> JsonPrimitive("begin")
        Asn1Event.ConstructionEnd -> JsonPrimitive("end")
    }
    private fun readEvent(v: JsonElement): Asn1Event = if (v is JsonPrimitive) {
        if (v.content == "begin") Asn1Event.ConstructionBegin else Asn1Event.ConstructionEnd
    } else {
        val o = v.jsonObject
        o["header"]?.let { Asn1Event.Header(readHeader(it.jsonObject)) } ?: Asn1Event.Primitive(o.bytes("primitive"))
    }
    private fun rawResult(result: Asn1Result<List<Asn1Event>>): JsonElement = when (result) {
        is Asn1Result.Failure -> left(result.error.show())
        is Asn1Result.Success -> right(JsonArray(result.value.map(::eventJSON)))
    }
    private fun <A> getTrace(parser: Asn1Get<A>, position: ULong, chunks: List<ByteArray>, json: (A) -> JsonElement): JsonArray {
        var result = parser.start(chunks.first(), position); var i = 1
        return JsonArray(buildList {
            while (true) {
                add(when (val current = result) {
                    is Asn1GetResult.Done -> buildJsonObject { put("kind", "done"); put("value", json(current.value)); put("position", JsonPrimitive(current.state.position)); put("remaining", byteJSON(current.state.input)) }
                    is Asn1GetResult.Failed -> buildJsonObject { put("kind", "fail"); put("message", current.description()) }
                    is Asn1GetResult.Partial -> buildJsonObject { put("kind", "partial") }
                })
                val current = result
                if (current !is Asn1GetResult.Partial || i == chunks.size) break
                result = current.feed(chunks[i++])
            }
        })
    }
    private fun program(v: JsonObject): Asn1Get<JsonElement> = when (v.text("op")) {
        "word" -> Asn1Get.word8.map { JsonPrimitive(it) }
        "bytes", "copy" -> Asn1Get.bytes(v.getValue("count").jsonPrimitive.long).map(::byteJSON)
        "pure" -> Asn1Get.pure(v.getValue("value"))
        "fail" -> Asn1Get.fail(v.text("message"))
        "empty" -> Asn1Get.fail("empty")
        "zero" -> Asn1Get.fail("mzero")
        "choice" -> program(v.getValue("first").jsonObject).orElse(program(v.getValue("second").jsonObject))
        "sequence" -> v.getValue("programs").jsonArray.fold(Asn1Get.pure(emptyList<JsonElement>())) { parser, next -> parser.flatMap { values -> program(next.jsonObject).map { values + it } } }.map { JsonArray(it) }
        "map" -> program(v.getValue("program").jsonObject).map { buildJsonObject { put("mapped", it) } }
        else -> error(v)
    }
    private fun observe(v: JsonObject): JsonElement = try {
        fun chunks() = v.getValue("chunks").jsonArray.map { c -> c.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray() }
        fun join(chunks: List<ByteArray>) = ByteArrayOutputStream().also { out -> chunks.forEach(out::write) }.toByteArray()
        when (v.text("api")) {
            "base64" -> {
                val bytes = v.bytes("bytes")
                buildJsonObject { put("encoded", byteJSON(PemBase64.encode(bytes))); put("decoded", when (val r = PemBase64.decode(bytes)) { is PemBase64.Decoded -> right(byteJSON(r.bytes)); is PemBase64.Invalid -> left(r.message) }); put("valid", PemBase64.isValid(bytes)); put("correct", PemBase64.isBase64(bytes)) }
            }
            "pemParse" -> {
                val chunks = chunks(); val result = Pem.parseChunks(chunks)
                buildJsonObject { put("parsed", pemResult(result)); put("strict", pemResult(Pem.parse(join(chunks)))); put("shown", when (result) { is PemParseResult.Failure -> left(result.message); is PemParseResult.Success -> right(JsonArray(result.sections.map { JsonPrimitive(it.toString()) })) }) }
            }
            "pemWrite" -> {
                val sections = v.getValue("sections").jsonArray.map { readPem(it.jsonObject) }
                val written = join(sections.map(Pem::write))
                buildJsonObject { put("bytes", byteJSON(written)); put("parsed", pemResult(Pem.parse(written))); put("shown", JsonArray(sections.map { JsonPrimitive(it.toString()) })); put("lazyChunks", JsonArray(sections.map { JsonArray(Pem.writeChunks(it).map(::byteJSON)) })); put("equal", sections == sections.map { PemSection(it.name, it.headers, it.content) }) }
            }
            "lazyDemand" -> {
                val stream = v.getValue("chunks").jsonArray.asSequence().map { element ->
                    if (element is JsonPrimitive) error(element.content)
                    element.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                }
                if (v.text("kind") == "pem") pemResult(Pem.parseChunks(stream)) else rawResult(Asn1Raw.parseChunks(stream))
            }
            "raw" -> {
                val chunks = chunks(); var state = Asn1Raw.newParseState()
                val trace = buildList {
                    for (chunk in chunks) when (val step = Asn1Raw.runParseState(state, chunk)) {
                        is Asn1Result.Failure -> { add(left(step.error.show())); break }
                        is Asn1Result.Success -> { state = step.value.state; add(buildJsonObject { put("events", JsonArray(step.value.events.map(::eventJSON))); put("done", Asn1Raw.isParseDone(state)) }) }
                    }
                }
                buildJsonObject { put("strict", rawResult(Asn1Raw.parse(join(chunks)))); put("lazy", rawResult(Asn1Raw.parseChunks(chunks))); put("trace", JsonArray(trace)) }
            }
            "writeRaw" -> {
                val events = v.getValue("events").jsonArray.map(::readEvent)
                buildJsonObject { put("bytes", byteJSON(Asn1Raw.write(events))); put("chunks", JsonArray(Asn1Raw.writeChunks(events).map(::byteJSON))); put("shown", JsonArray(events.map { JsonPrimitive(it.toString()) })) }
            }
            "putHeader" -> {
                val header = readHeader(v.getValue("header").jsonObject)
                buildJsonObject { put("bytes", byteJSON(Asn1Raw.putHeader(header))); put("shown", header.toString()) }
            }
            "headerRoundtrip" -> {
                val header = readHeader(v.getValue("header").jsonObject); val bytes = Asn1Raw.putHeader(header)
                val parsed = getTrace(Asn1Serialize.header, 0u, listOf(bytes), ::headerJSON).single()
                val result = Asn1Serialize.header.start(bytes)
                buildJsonObject { put("bytes", byteJSON(bytes)); put("parsed", parsed); put("identity", result is Asn1GetResult.Done && result.value == header) }
            }
            "eventRoundtrip" -> {
                val events = v.getValue("events").jsonArray.map(::readEvent); val chunks = Asn1Raw.writeChunks(events); val parsed = Asn1Raw.parseChunks(chunks)
                buildJsonObject { put("chunks", JsonArray(chunks.map(::byteJSON))); put("parsed", rawResult(parsed)); put("identity", parsed is Asn1Result.Success && parsed.value == events) }
            }
            "getHeader" -> getTrace(Asn1Serialize.header, v.getValue("position").jsonPrimitive.content.toULong(), chunks(), ::headerJSON)
            "get" -> getTrace(program(v.getValue("program").jsonObject), v.getValue("position").jsonPrimitive.content.toULong(), chunks()) { it }
            "integer" -> {
                val bytes = v.bytes("bytes"); val number = v.text("integer").toBigInteger(); val (nu, u) = Asn1Integers.unsigned(bytes); val (ns, s) = Asn1Integers.signed(bytes)
                buildJsonObject { put("unsigned", JsonArray(listOf(JsonPrimitive(nu), JsonPrimitive(u.toString())))); put("signed", JsonArray(listOf(JsonPrimitive(ns), JsonPrimitive(s.toString())))); put("unsignedBytes", byteJSON(Asn1Integers.unsignedBytes(number))); put("signedBytes", byteJSON(Asn1Integers.signedBytes(number))); put("variable", byteJSON(Asn1Integers.variableBytes(number))) }
            }
            else -> error(v)
        }
    } catch (e: IllegalStateException) { buildJsonObject { put("fatal", e.message!!.lineSequence().first()) } }

    @TestFactory fun originalCertificateBinary(): List<DynamicTest> =
        GZIPInputStream(javaClass.getResourceAsStream("/certificate-binary/data.jsonl.gz")!!).bufferedReader().use { reader ->
            val metadata = Json.parseToJsonElement(reader.readLine()).jsonObject
            val cases = reader.lineSequence().map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(metadata.getValue("cases").jsonPrimitive.int, cases.size)
            cases.mapIndexed { i, c -> DynamicTest.dynamicTest("$i ${c.text("group")}") { assertEquals(c.getValue("expected"), observe(c.getValue("request").jsonObject)) } }
        }
}
