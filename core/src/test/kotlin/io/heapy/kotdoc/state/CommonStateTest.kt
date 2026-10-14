package io.heapy.kotdoc.state

import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.language.LangParseResult
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.*
import io.heapy.kotdoc.translations.Translations
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.net.http.HttpClient
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Protocol/constructor transport; expected states and events come only from GHC. */
class CommonStateTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun chars(v: JsonElement) = CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun text(v: JsonElement) = TextChars.fromPoints(chars(v).points())
    private fun points(s: CharacterString) = JsonArray(s.points().map(::JsonPrimitive))
    private fun path(v: JsonElement) = if (v == JsonNull) null else chars(v)
    private fun optionalText(v: JsonElement) = if (v == JsonNull) null else text(v)
    private fun messages(v: JsonElement) = v.jsonArray.map { LoggingTest().message(it) }
    private fun log(v: List<LogMessage>) = Json.parseToJsonElement(Logging.encodeLogMessagesText(v))
    private fun state(s: CommonState): JsonElement = buildJsonObject {
        put("log", log(s.log)); put("userDataDir", s.userDataDir?.let(::points) ?: JsonNull)
        put("sourceURL", s.sourceURL?.let(::JsonPrimitive) ?: JsonNull)
        put("headers", JsonArray(s.requestHeaders.map { (k,v) -> buildJsonArray { add(k); add(v) } }))
        put("noCheckCertificate", s.noCheckCertificate); put("mediaBag", s.mediaBag.show())
        put("translations", s.translations?.let { (l,t) -> buildJsonArray {
            add(l.show()); add(t?.show()?.let(::JsonPrimitive) ?: JsonNull)
        } } ?: JsonNull)
        put("inputFiles", JsonArray(s.inputFiles.map(::points)))
        put("outputFile", s.outputFile?.let(::points) ?: JsonNull)
        put("resourcePath", JsonArray(s.resourcePath.map(::points)))
        put("manager", s.manager?.let { JsonPrimitive(managers.indexOfFirst { m -> m === it }.also { n -> assertTrue(n >= 0) }) } ?: JsonNull)
        put("verbosity", s.verbosity.name); put("trace", s.trace)
    }
    private fun run(request: JsonArray): JsonElement {
        val events = mutableListOf<JsonElement>()
        val traces = StringBuilder()
        val mode = request[2].jsonPrimitive.int
        fun event(name: String, vararg values: JsonElement) { events.add(JsonArray(listOf(JsonPrimitive(name)) + values)) }
        val context = PandocStateContext(output = { c, msg ->
            event("output", log(listOf(msg)).jsonArray.single(), state(c.getCommonState()))
            when (mode) {
                1 -> {
                    c.setUserDataDir(CharacterStringChars.fromString("callback"))
                    c.modifyCommonState { it.copy(log = listOf(LogMessage.NoLangSpecified) + it.log) }
                    c.setVerbosity(Verbosity.ERROR)
                }
                2 -> {
                    c.setVerbosity(Verbosity.ERROR)
                    c.report(LogMessage.ScriptingInfo("nested", null))
                    c.setUserDataDir(CharacterStringChars.fromString("nested-callback"))
                }
                3 -> throw PandocError.PandocSomeError("callback-error")
                4 -> throw IllegalStateException("host-error")
            }
        }, tracing = { traces.append(it).append('\n') })
        fun operations(ops: JsonElement) {
            for (op in ops.jsonArray) {
                val a = op.jsonArray
                when (a[0].s()) {
                    "verbosity" -> context.setVerbosity(Verbosity.entries[a[1].jsonPrimitive.int])
                    "traceFlag" -> context.setTrace(a[1].jsonPrimitive.boolean)
                    "trace" -> context.trace(text(a[1]))
                    "header" -> context.setRequestHeader(text(a[1]), text(a[2]))
                    "headers" -> context.setRequestHeaders(a[1].jsonArray.map { it.jsonArray.let { p -> text(p[0]) to text(p[1]) } })
                    "certificate" -> context.setNoCheckCertificate(a[1].jsonPrimitive.boolean)
                    "manager" -> context.modifyCommonState { it.copy(manager = if (a[1] == JsonNull) null else managers[a[1].jsonPrimitive.int]) }
                    "media" -> context.insertMedia(chars(a[1]), optionalText(a[2]), a[3].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())
                    "input" -> context.setInputFiles(a[1].jsonArray.map(::chars))
                    "output" -> context.setOutputFile(path(a[1]))
                    "resource" -> context.setResourcePath(a[1].jsonArray.map(::chars))
                    "user" -> context.setUserDataDir(path(a[1]))
                    "source" -> context.modifyCommonState { it.copy(sourceURL = optionalText(a[1])) }
                    "translations" -> context.modifyCommonState { it.copy(translations = if (a[1] == JsonNull) null else
                        (Lang.parseLang(text(a[1])) as LangParseResult.Success).value to
                            if (a[2] == JsonNull) null else Translations.fromJson(a[2])) }
                    "log" -> context.modifyCommonState { it.copy(log = messages(a[1])) }
                    "report" -> context.report(LoggingTest().message(a[1]))
                    "snapshot" -> event("snapshot", state(context.getCommonState()))
                    "getLog" -> event("getLog", log(context.getLog()))
                    "silent" -> {
                        val (result, captured) = context.runSilently { operations(a[1]); a[2] }
                        assertSame(a[2], result)
                        event("silent", result, log(captured), state(context.getCommonState()))
                    }
                    "caught" -> try { operations(a[1]) } catch (e: PandocError) { event("caught", JsonPrimitive(e.toString())) }
                    "failPandoc" -> throw PandocError.PandocSomeError("action-error")
                    "failHost" -> throw IllegalStateException("host-error")
                    "toLang" -> event("lang", context.toLang(optionalText(a[1]))?.show()?.let(::JsonPrimitive) ?: JsonNull)
                    else -> error(a[0])
                }
            }
        }
        val status = try { operations(request[1]); "success" }
            catch (e: PandocError) { e.toString() }
            catch (e: IllegalStateException) { "host" }
        return buildJsonObject {
            put("state", state(context.getCommonState())); put("status", status)
            put("events", JsonArray(events))
            put("traceBytes", JsonArray(traces.toString().toByteArray(Charsets.UTF_8).map { JsonPrimitive(it.toInt() and 255) }))
        }
    }
    @TestFactory
    fun originalStateObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/common-state/data.jsonl.gz")))
        .bufferedReader().use { r ->
            r.readLine()
            r.lineSequence().mapIndexed { i,line ->
                val c = Json.parseToJsonElement(line).jsonObject
                DynamicTest.dynamicTest("${i + 1}: ${c.getValue("group").s()}") {
                    assertLogJsonEquals(c.getValue("expected"), run(c.getValue("request").jsonArray))
                }
            }.toList()
        }

    @Test
    fun snapshotsOwnCollectionsAndShareImmutableValues() {
        val old = LogMessage.ScriptingWarning("before", null)
        val logs = mutableListOf<LogMessage>(old)
        val paths = mutableListOf(CharacterString(intArrayOf(0xd800,0xdc00)))
        val headers = mutableListOf("a" to "b")
        val s = CommonState(log = logs, inputFiles = paths, resourcePath = paths, requestHeaders = headers)
        logs.clear(); paths.clear(); headers.clear()
        assertSame(old, s.log.single())
        assertContentEquals(intArrayOf(0xd800,0xdc00), s.inputFiles.single().points())
        assertEquals(listOf("a" to "b"), s.requestHeaders)
        val c = PandocStateContext(s)
        repeat(10_000) { c.report(LogMessage.ScriptingInfo(it.toString(), null)) }
        assertEquals(10_001, c.getCommonState().log.size)
        assertSame(old, s.log.single())
        assertSame(s.mediaBag, c.getMediaBag())
        assertSame(s.log, s.copy(trace = true).log)
        val chronology = c.getLog()
        assertSame(old, chronology.first())
        c.report(LogMessage.NoLangSpecified)
        assertEquals(10_001, chronology.size)
    }

    @Test
    fun silentRestoresOnlyItsTwoFieldsAndPreservesTypedErrorIdentity() {
        val original = LogMessage.NoLangSpecified
        val c = PandocStateContext(CommonState(log = listOf(original), verbosity = Verbosity.INFO))
        val failure = PandocError.PandocSomeError("same exception")
        val thrown = assertFailsWith<PandocError.PandocSomeError> {
            c.runSilently {
                c.report(LogMessage.ScriptingWarning("discard", null))
                c.setTrace(true)
                throw failure
            }
        }
        assertSame(failure, thrown)
        assertSame(original, c.getCommonState().log.single())
        assertEquals(Verbosity.INFO, c.getVerbosity())
        assertTrue(c.getTrace())
        val value = Any()
        assertSame(value, c.runSilently { value }.first)
    }
    companion object { private val managers = listOf(HttpClient.newHttpClient(), HttpClient.newHttpClient()) }
}
