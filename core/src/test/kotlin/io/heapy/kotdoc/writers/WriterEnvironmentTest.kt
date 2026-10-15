package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.DocumentShared
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.ast.PandocJson
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.translations.Translations
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** JSON/state transport only. All expected values and effect order are native. */
class WriterEnvironmentTest {
    private fun text(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun raw(s: CharacterString) = JsonArray(s.points().map(::JsonPrimitive))
    private fun path(s: String) = CharacterStringChars.fromString(s)
    private fun logs(xs: List<LogMessage>) = Json.parseToJsonElement(Logging.encodeLogMessagesText(xs))
    private fun language(v: JsonElement): Lang {
        if (v is JsonPrimitive) return Lang.fromString(v.content)
        val a = v.jsonArray
        fun optional(v: JsonElement) = if (v == JsonNull) null else text(v)
        return Lang(text(a[0]), optional(a[1]), optional(a[2]), a[3].jsonArray.map(::text),
            a[4].jsonArray.map { p -> p.jsonArray.let { text(it[0]) to it[1].jsonArray.map { p ->
                p.jsonArray.let { text(it[0]) to text(it[1]) }
            } } }, a[5].jsonArray.map(::text))
    }
    private fun state(s: CommonState) = buildJsonObject {
        put("log", logs(s.log)); put("userDataDir", s.userDataDir?.let(::raw) ?: JsonNull)
        put("sourceURL", s.sourceURL?.let(::JsonPrimitive) ?: JsonNull)
        put("headers", JsonArray(s.requestHeaders.map { (k, v) -> buildJsonArray { add(k); add(v) } }))
        put("noCheckCertificate", s.noCheckCertificate); put("mediaBag", s.mediaBag.show())
        put("translations", s.translations?.let { (l, t) -> buildJsonArray {
            add(l.show()); add(t?.show()?.let(::JsonPrimitive) ?: JsonNull)
        } } ?: JsonNull)
        put("inputFiles", JsonArray(s.inputFiles.map(::raw))); put("outputFile", s.outputFile?.let(::raw) ?: JsonNull)
        put("resourcePath", JsonArray(s.resourcePath.map(::raw))); put("manager", JsonNull)
        put("verbosity", s.verbosity.name); put("trace", s.trace)
    }
    private class HostFailure : Exception()
    private fun run(a: JsonArray): JsonElement {
        if (a[0].jsonPrimitive.content == "date") return DocumentShared.normalizeDate(text(a[1]))?.let(::JsonPrimitive) ?: JsonNull
        check(a[0].jsonPrimitive.content == "setup")
        val meta = PandocJson.decode(a[1]).meta
        val cache = if (a[2] == JsonNull) null else a[2].jsonArray.let {
            language(it[0]) to if (it[1] == JsonNull) null else Translations.fromJson(it[1])
        }
        var common = CommonState(log = listOf(LogMessage.NoLangSpecified), userDataDir = path("seed-dir"),
            sourceURL = "seed-source", requestHeaders = listOf("x-seed" to "value"), noCheckCertificate = true,
            inputFiles = listOf(path("seed-in")), outputFile = path("seed-out"), resourcePath = listOf(path("seed-resource")),
            verbosity = Verbosity.entries[a[3].jsonPrimitive.int], trace = true, translations = cache)
        val events = mutableListOf<JsonElement>()
        fun emit(name: String, vararg values: JsonElement) { events.add(JsonArray(listOf(JsonPrimitive(name)) + values)) }
        val runtime = object : PandocState {
            override fun getCommonState() = common
            override fun putCommonState(state: CommonState) {
                emit("put", state(state)); common = state
            }
            override fun logOutput(message: LogMessage) {
                emit("output", logs(listOf(message)).jsonArray.single(), state(common))
                when (a[4].jsonPrimitive.int) {
                    1 -> modifyCommonState { it.copy(translations = Lang("de") to Translations.empty,
                        userDataDir = path("callback"), verbosity = Verbosity.ERROR, log = listOf(LogMessage.NoLangSpecified) + it.log) }
                    2 -> {
                        setVerbosity(Verbosity.ERROR); report(LogMessage.ScriptingInfo("nested", null))
                        modifyCommonState { it.copy(userDataDir = path("nested-callback")) }
                    }
                    3 -> { setUserDataDir(path("typed-callback")); throw PandocError.PandocSomeError("callback-error") }
                    4 -> { setUserDataDir(path("host-callback")); throw HostFailure() }
                    5 -> WriterShared.setupTranslations(this, emptyMap())
                }
            }
        }
        val status = try { WriterShared.setupTranslations(runtime, meta); "success" }
            catch (error: PandocError) { error.toString() } catch (error: HostFailure) { "host" }
        return buildJsonObject {
            put("state", state(common)); put("events", JsonArray(events)); put("status", status)
            put("lookup", DocumentShared.lookupMetaString("lang", meta))
        }
    }

    @TestFactory
    fun originalDateAndWriterEnvironmentObservations(): List<DynamicTest> = GZIPInputStream(
        checkNotNull(javaClass.getResourceAsStream("/writer-environment/data.jsonl.gz"))
    ).bufferedReader().use { reader ->
        reader.readLine()
        reader.lineSequence().mapIndexed { i, line ->
            var transport: String? = line
            DynamicTest.dynamicTest("Writer environment ${i + 1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject; transport = null
                assertLogJsonEquals(c.getValue("expected"), run(c.getValue("request").jsonArray))
            }
        }.toList()
    }

    @Test
    fun defaultLanguageRetainsItsOriginalScriptFieldAndLoadedTable() {
        val lang = Lang("en", script = "US")
        val table = Translations.empty
        val initial = CommonState(translations = lang to table)
        val runtime = PandocStateContext(initial)
        WriterShared.setupTranslations(runtime, emptyMap())
        assertSame(initial, runtime.getCommonState())
        assertSame(table, runtime.getCommonState().translations?.second)
        assertNull(runtime.getCommonState().translations?.first?.region)
    }
}
