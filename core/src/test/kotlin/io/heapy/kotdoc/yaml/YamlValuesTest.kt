package io.heapy.kotdoc.yaml

import io.heapy.kotdoc.json.PrettyJson
import io.heapy.kotdoc.json.PrettyValue
import io.heapy.kotdoc.json.ScientificNumber
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Only transport projections; expectations are captured from actual original Haskell. */
class YamlValuesTest {
    private fun chars(a: JsonElement) = CharacterString(a.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun text(a: JsonElement) = TextChars.fromPoints(chars(a).points())
    private fun anchor(a: JsonElement) = if (a == JsonNull) null else chars(a)
    private fun tag(a: JsonElement): YamlTag {
        val v = a.jsonArray
        return if (v[0].jsonPrimitive.content == "UriTag") YamlTag.UriTag(chars(v[1]))
        else when(v[0].jsonPrimitive.content) {
            "StrTag" -> YamlTag.StrTag; "FloatTag" -> YamlTag.FloatTag; "NullTag" -> YamlTag.NullTag
            "BoolTag" -> YamlTag.BoolTag; "SetTag" -> YamlTag.SetTag; "IntTag" -> YamlTag.IntTag
            "SeqTag" -> YamlTag.SeqTag; "MapTag" -> YamlTag.MapTag; "NoTag" -> YamlTag.NoTag
            else -> error("tag")
        }
    }
    private fun event(a: JsonElement): YamlValueEvent {
        val v = a.jsonArray
        return when(v[0].jsonPrimitive.content) {
            "StreamStart" -> YamlValueEvent.StreamStart; "StreamEnd" -> YamlValueEvent.StreamEnd
            "DocumentStart" -> YamlValueEvent.DocumentStart; "DocumentEnd" -> YamlValueEvent.DocumentEnd
            "Alias" -> YamlValueEvent.Alias(chars(v[1]))
            "Scalar" -> YamlValueEvent.Scalar(YamlScalarEvent(v[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray(),tag(v[2]),YamlStyle.entries[v[3].jsonPrimitive.int],anchor(v[4])))
            "SequenceStart" -> YamlValueEvent.SequenceStart(tag(v[1]),YamlCollectionStyle.entries[v[2].jsonPrimitive.int],anchor(v[3]))
            "SequenceEnd" -> YamlValueEvent.SequenceEnd
            "MappingStart" -> YamlValueEvent.MappingStart(tag(v[1]),YamlCollectionStyle.entries[v[2].jsonPrimitive.int],anchor(v[3]))
            "MappingEnd" -> YamlValueEvent.MappingEnd
            else -> error("event")
        }
    }
    private fun path(a: JsonElement) = a.jsonArray.map { p ->
        val v = p.jsonArray
        if (v[0].jsonPrimitive.content == "key") YamlPathElement.Key(text(v[1])) else YamlPathElement.Index(v[1].jsonPrimitive.long)
    }
    private fun value(a: JsonElement): PrettyValue {
        val v = a.jsonArray
        return when(v[0].jsonPrimitive.content) {
            "null" -> PrettyValue.Null
            "bool" -> PrettyValue.Bool(v[1].jsonPrimitive.boolean)
            "text" -> PrettyValue.Text(text(v[1]))
            "number" -> PrettyValue.Number(ScientificNumber(BigInteger(v[1].jsonPrimitive.content),v[2].jsonPrimitive.long))
            "array" -> PrettyValue.Array(v[1].jsonArray.map(::value))
            "object" -> PrettyValue.Object(v[1].jsonArray.associate { it.jsonArray.let { p -> text(p[0]) to value(p[1]) } })
            else -> error("value")
        }
    }
    private fun exception(a: JsonElement): YamlValueException {
        val v = a.jsonArray
        return when(v[0].jsonPrimitive.content) {
            "NonScalarKey" -> YamlValueException.NonScalarKey
            "UnknownAlias" -> YamlValueException.UnknownAlias(chars(v[1]))
            "UnexpectedEvent" -> YamlValueException.UnexpectedEvent(if(v[1] == JsonNull) null else event(v[1]),if(v[2] == JsonNull) null else event(v[2]))
            "InvalidYaml" -> YamlValueException.InvalidYaml(if(v[1] == JsonNull) null else v[1].jsonArray.let { d ->
                if(d[0].jsonPrimitive.content == "Generic") YamlExceptionDetail.Generic(chars(d[1]))
                else d[3].jsonArray.let { m -> YamlExceptionDetail.Parse(chars(d[1]),chars(d[2]),YamlMark(m[0].jsonPrimitive.long.toULong(),m[1].jsonPrimitive.long.toULong(),m[2].jsonPrimitive.long.toULong())) }
            })
            "MultipleDocuments" -> YamlValueException.MultipleDocuments
            "AesonException" -> YamlValueException.AesonException(chars(v[1]))
            "OtherParseException" -> YamlValueException.OtherParseException(chars(v[1]))
            "NonStringKey" -> YamlValueException.NonStringKey(path(v[1]))
            "NonStringKeyAlias" -> YamlValueException.NonStringKeyAlias(chars(v[1]),value(v[2]))
            "CyclicIncludes" -> YamlValueException.CyclicIncludes
            "LoadSettingsException" -> YamlValueException.LoadSettingsException(chars(v[1]),exception(v[2]))
            else -> error("exception")
        }
    }
    private fun errorResult(error: YamlValueException) = buildJsonObject {
        put("error",error.javaClass.simpleName);put("pretty",JsonArray(error.pretty().points().map(::JsonPrimitive)))
    }
    private fun shape(value: PrettyValue): JsonElement = buildJsonArray {
        when(value) {
            PrettyValue.Null -> add("null")
            is PrettyValue.Bool -> { add("bool");add(value.value) }
            is PrettyValue.Text -> { add("text");add(value.value) }
            is PrettyValue.Number -> { add("number");add(value.value.coefficient.toString());add(value.value.base10Exponent) }
            is PrettyValue.Array -> { add("array");add(JsonArray(value.values.map(::shape))) }
            is PrettyValue.Object -> {
                add("object");add(JsonArray(value.values.entries.sortedWith { a,b -> PrettyJson.compare.compare(a.key,b.key) }.map { buildJsonArray { add(it.key);add(shape(it.value)) } }))
            }
        }
    }
    private fun warning(w: YamlDuplicateKey) = JsonArray(w.path.map { p -> buildJsonArray {
        when(p) {
            is YamlPathElement.Key -> { add("key");add(p.value) }
            is YamlPathElement.Index -> { add("index");add(p.value) }
        }
    } })
    private fun run(a: JsonArray): JsonElement {
        if(a[0].jsonPrimitive.content == "exception") return errorResult(exception(a[1]))
        if(a[0].jsonPrimitive.content == "warning") return JsonArray(YamlDuplicateKey(path(a[1])).show(a[2].jsonPrimitive.long).codePoints().toArray().map(::JsonPrimitive))
        val all = a[0].jsonPrimitive.boolean;var consumed = 0
        val src: () -> YamlValueEvent? = if(a[1].jsonPrimitive.content == "events") {
            val events = a[2].jsonArray.map(::event);var index = 0
            { events.getOrNull(index++) }
        } else YamlValues.parserSource(a[2].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
        val observed = { src().also { if(it != null) consumed++ } }
        val parsed = if(all) YamlValues.decodeAllEvents(observed) else when(val result = YamlValues.decodeEvents(observed)) {
            is YamlValueResult.Failure -> result
            is YamlValueResult.Success -> YamlValueResult.Success(result.warnings,listOf(result.value))
        }
        return buildJsonObject {
            put("consumed",consumed)
            put("result",when(parsed) {
                is YamlValueResult.Failure -> errorResult(parsed.error)
                is YamlValueResult.Success -> buildJsonObject {
                    put("warnings",JsonArray(parsed.warnings.map(::warning)));put("warningsShown",JsonArray(parsed.warnings.map { JsonPrimitive(it.show()) }));put("values",JsonArray(parsed.value.map(::shape)))
                }
            })
        }
    }
    @TestFactory
    fun originalYamlValueObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/yaml-values/data.jsonl.gz"))).bufferedReader().use { r ->
        r.readLine();r.lineSequence().mapIndexed { i,line ->
            var transport: String? = line
            DynamicTest.dynamicTest("Yaml values ${i+1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport = null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun collectionAnchorsShareCompletedImmutableValues() {
        val result = assertIs<YamlValueResult.Success<PrettyValue>>(YamlValues.decode("[&a [one], *a]".toByteArray()))
        val nodes = assertIs<PrettyValue.Array>(result.value).values
        assertSame(nodes[0],nodes[1])
    }
    @Test
    fun eventEqualityAndWarningEqualityUseOwnedContents() {
        val bytes = byteArrayOf(65)
        val a = YamlValueEvent.Scalar(YamlScalarEvent(bytes,YamlTag.NoTag,YamlStyle.Plain,null))
        val b = YamlValueEvent.Scalar(YamlScalarEvent(bytes.copyOf(),YamlTag.NoTag,YamlStyle.Plain,null))
        bytes.fill(0);assertEquals(a,b);assertEquals(a.hashCode(),b.hashCode())
        val path = mutableListOf<YamlPathElement>(YamlPathElement.Key("key"))
        val warning = YamlDuplicateKey(path);path.clear()
        assertEquals(YamlDuplicateKey(listOf(YamlPathElement.Key("key"))),warning)
    }
    @Test
    fun deeplyNestedEventStreamsDecodeWithoutJvmRecursion() {
        val depth = 10_000;var at = 0
        val source = {
            when(val i = at++) {
                0 -> YamlValueEvent.StreamStart
                1 -> YamlValueEvent.DocumentStart
                in 2 until depth + 2 -> YamlValueEvent.SequenceStart(YamlTag.NoTag,YamlCollectionStyle.Any,null)
                depth + 2 -> YamlValueEvent.Scalar(YamlScalarEvent("leaf".toByteArray(),YamlTag.NoTag,YamlStyle.Plain,null))
                in depth + 3 until 2 * depth + 3 -> YamlValueEvent.SequenceEnd
                2 * depth + 3 -> YamlValueEvent.DocumentEnd
                2 * depth + 4 -> YamlValueEvent.StreamEnd
                else -> error("Source demanded beyond stream end at $i")
            }
        }
        var value = assertIs<YamlValueResult.Success<PrettyValue>>(YamlValues.decodeEvents(source)).value
        repeat(depth) { value = assertIs<PrettyValue.Array>(value).values.single() }
        assertEquals("leaf",assertIs<PrettyValue.Text>(value).value)
    }
    @Test
    fun allDocumentsAreParsedBeforeTypedConversionAndConversionStopsAtFirstFailure() {
        var calls = 0
        val malformed = YamlValues.decodeAllAs("--- one\n--- [".toByteArray()) { calls++;YamlFromJsonResult.Success(it) }
        assertIs<YamlValueResult.Failure>(malformed);assertEquals(0,calls)
        val failure = YamlValues.decodeAllAs("--- one\n--- two\n--- three".toByteArray()) {
            calls++
            if(calls == 2) YamlFromJsonResult.Failure(CharacterStringChars.fromString("second document")) else YamlFromJsonResult.Success(it)
        }
        assertEquals(2,calls);assertIs<YamlValueException.AesonException>(assertIs<YamlValueResult.Failure>(failure).error)
    }
}
