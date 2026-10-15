package io.heapy.kotdoc.translations

import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.logging.Verbosity
import io.heapy.kotdoc.logging.assertLogJsonEquals
import io.heapy.kotdoc.resources.PandocText
import io.heapy.kotdoc.state.CommonState
import io.heapy.kotdoc.state.PandocStateContext
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Transport only; all expected tables, errors and state/effect traces are original Haskell. */
class TranslationLoadingTest {
    private fun chars(a: JsonElement) = CharacterString(a.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun text(a: JsonElement) = TextChars.fromPoints(chars(a).points())
    private fun bytes(a: JsonElement) = a.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun raw(a: CharacterString) = JsonArray(a.points().map(::JsonPrimitive))
    private fun messages(values: List<LogMessage>) = Json.parseToJsonElement(Logging.encodeLogMessagesText(values))
    private fun message(value: LogMessage) = messages(listOf(value)).jsonArray.single()
    private fun lang(a: JsonElement): Lang {
        if (a is JsonPrimitive) return Lang.fromString(a.content)
        val v = a.jsonArray
        fun optional(a: JsonElement) = if (a == JsonNull) null else text(a)
        return Lang(text(v[0]),optional(v[1]),optional(v[2]),v[3].jsonArray.map(::text),
            v[4].jsonArray.map { p -> p.jsonArray.let { text(it[0]) to it[1].jsonArray.map { p -> p.jsonArray.let { text(it[0]) to text(it[1]) } } } },
            v[5].jsonArray.map(::text))
    }
    private fun shape(t: Translations) = buildJsonObject {
        put("show",t.show());put("terms",JsonArray(Term.entries.map { t.lookupTerm(it)?.let(::JsonPrimitive) ?: JsonNull }))
    }
    private fun state(s: CommonState) = buildJsonObject {
        put("cache",s.translations?.let { (l,t) -> buildJsonArray { add(l.show());add(t?.let(::shape) ?: JsonNull) } } ?: JsonNull)
        put("user",s.userDataDir?.let(::raw) ?: JsonNull)
        put("headers",JsonArray(s.requestHeaders.map { (a,b) -> buildJsonArray { add(a);add(b) } }))
        put("resources",JsonArray(s.resourcePath.map(::raw)));put("verbosity",s.verbosity.name)
        put("log",messages(s.log))
    }
    private class HostFailure : Exception()
    private inner class Runtime(files: JsonArray, private val mode: Int) : TranslationRuntime {
        private var common = CommonState.default
        val events = mutableListOf<JsonElement>()
        private var outputs = 0
        private val files = files.associate { p -> p.jsonArray.let { chars(it[0]) to it[1].jsonArray.toMutableList() } }
        override fun getCommonState() = common
        override fun putCommonState(state: CommonState) {
            emit("put",this@TranslationLoadingTest.state(state));common = state
        }
        fun emit(name: String,vararg args: JsonElement) { events.add(JsonArray(listOf(JsonPrimitive(name))+args)) }
        override fun logOutput(message: LogMessage) {
            emit("output",message(message),state(common));val n = outputs++
            when(mode) {
                1 -> modifyCommonState { it.copy(userDataDir=CharacterStringChars.fromString("callback"),verbosity=Verbosity.ERROR,
                    log=listOf(LogMessage.NoLangSpecified)+it.log,translations=Lang.fromString("de") to Translations.empty) }
                2 -> {
                    modifyCommonState { it.copy(verbosity=Verbosity.ERROR,userDataDir=CharacterStringChars.fromString("nested-callback")) }
                    report(LogMessage.ScriptingInfo("nested",null))
                }
                3 -> {
                    modifyCommonState { it.copy(userDataDir=CharacterStringChars.fromString("typed-callback")) }
                    throw PandocError.PandocSomeError("callback-error")
                }
                4 -> {
                    modifyCommonState { it.copy(userDataDir=CharacterStringChars.fromString("host-callback")) };throw HostFailure()
                }
                5 -> if (n == 0) { setTranslations(Lang.fromString("de"));getTranslations() }
            }
        }
        override fun readDataFile(file: CharacterString): ByteArray {
            emit("read",raw(file),state(common))
            val choices = files[file]
            val response = choices?.firstOrNull()?.jsonArray ?: JsonArray(listOf(JsonPrimitive("missing")))
            if (choices != null && choices.size>1) choices.removeAt(0)
            return when(response[0].jsonPrimitive.content) {
                "bytes" -> { operations(response[2].jsonArray);bytes(response[1]) }
                "typed" -> { operations(response[2].jsonArray);throw PandocError.PandocSomeError(text(response[1])) }
                "host" -> { operations(response[1].jsonArray);throw HostFailure() }
                "missing" -> throw PandocError.PandocCouldNotFindDataFileError(TextChars.fromPoints(file.points()))
                else -> error("file response")
            }
        }
        fun operations(ops: JsonArray) { ops.forEach { operation(it.jsonArray) } }
        private fun operation(a: JsonArray) { when(a[0].jsonPrimitive.content) {
            "set" -> setTranslations(lang(a[1]))
            "cache" -> modifyCommonState { it.copy(translations=if(a[1]==JsonNull) null else lang(a[1]) to if(a[2]==JsonNull) null else Translations.fromJson(a[2])) }
            "get" -> emit("get",shape(getTranslations()))
            "term" -> emit("term",JsonPrimitive(translateTerm(Term.entries[a[1].jsonPrimitive.int])))
            "snapshot" -> emit("snapshot",state(common))
            "verbosity" -> setVerbosity(Verbosity.entries[a[1].jsonPrimitive.int])
            "user" -> setUserDataDir(if(a[1]==JsonNull) null else chars(a[1]))
            "headers" -> setRequestHeaders(a[1].jsonArray.map { p -> p.jsonArray.let { text(it[0]) to text(it[1]) } })
            "resources" -> setResourcePath(a[1].jsonArray.map(::chars))
            "report" -> report(LogMessage.ScriptingWarning(text(a[1]),null))
            "caught" -> try { operations(a[1].jsonArray) } catch (e: PandocError) { emit("caught",JsonPrimitive(e.toString())) }
            "failTyped" -> throw PandocError.PandocSomeError(text(a[1]))
            "failHost" -> throw HostFailure()
            else -> error("operation")
        } }
    }
    private fun run(a: JsonArray): JsonElement = when(a[0].jsonPrimitive.content) {
        "read" -> when(val r = readTranslations(text(a[1]))) {
            is TranslationReadResult.Success -> shape(r.value)
            is TranslationReadResult.Failure -> buildJsonObject { put("error",r.error) }
        }
        "text" -> try { buildJsonObject { put("text",PandocText.toTextM(chars(a[1]),bytes(a[2]))) } }
            catch(e: PandocError) { buildJsonObject { put("error",e.toString()) } }
        "run" -> {
            val r = Runtime(a[2].jsonArray,a[3].jsonPrimitive.int)
            val status = try { r.operations(a[1].jsonArray);"success" }
                catch(e: PandocError) { e.toString() } catch(e: HostFailure) { "host" }
            buildJsonObject { put("state",state(r.getCommonState()));put("status",status);put("events",JsonArray(r.events)) }
        }
        else -> error("request")
    }
    @TestFactory
    fun originalTranslationLoadingObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/translation-loading/data.jsonl.gz"))).bufferedReader().use { r ->
        r.readLine();r.lineSequence().mapIndexed { i,line ->
            var transport: String? = line
            DynamicTest.dynamicTest("Translation loading ${i+1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport = null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun equalLanguageSelectionRetainsLoadedTableAndStateIdentity() {
        val cached = Translations.fromJson(buildJsonObject { put("Figure","cached") })
        val l = Lang.fromString("en-US")
        val initial = CommonState(translations=l to cached)
        val context = PandocStateContext(initial)
        context.setTranslations(Lang.fromString("en-US"))
        assertSame(initial,context.getCommonState());assertSame(cached,context.getCommonState().translations?.second)
        context.setTranslations(Lang.fromString("en"))
        assertNull(context.getCommonState().translations?.second)
    }
}
