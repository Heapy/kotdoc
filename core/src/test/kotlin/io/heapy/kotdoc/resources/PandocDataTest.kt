package io.heapy.kotdoc.resources

import io.heapy.kotdoc.archive.ZipDecodeException
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.language.Lang
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.*
import io.heapy.kotdoc.state.CommonState
import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.translations.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Protocol executor; expected bytes/effect order/errors come from original Haskell. */
class PandocDataTest {
    private fun chars(v: JsonElement)=CharacterString(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun raw(s: String)=CharacterStringChars.fromString(s)
    private fun raw(s: CharacterString)=JsonArray(s.points().map(::JsonPrimitive))
    private fun bytes(v: JsonElement)=v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun text(v: JsonElement)=TextChars.fromPoints(chars(v).points())
    private fun log(ms: List<LogMessage>)=Json.parseToJsonElement(Logging.encodeLogMessagesText(ms))
    private fun state(s: CommonState)=buildJsonObject {
        put("user",s.userDataDir?.let(::raw) ?: JsonNull)
        put("headers",JsonArray(s.requestHeaders.map { (a,b)->buildJsonArray { add(a);add(b) } }))
        put("verbosity",s.verbosity.name);put("log",log(s.log))
        put("translations",s.translations?.let { (l,t)->buildJsonArray { add(l.show());add(t?.show()?.let(::JsonPrimitive) ?: JsonNull) } } ?: JsonNull)
    }
    private class HostFailure : Exception()
    private inner class Runtime(private val mode: Int,specs: JsonArray): PandocDataRuntime,PandocDataDirectories {
        private var current=CommonState.default
        val events=mutableListOf<JsonElement>()
        override val filePaths=if(mode>=2) PartialPaths.Windows else PartialPaths.Posix
        override val embeddedData=if(mode%2==0) PackagedPandocData else null
        private val responses=specs.associate { v->v.jsonArray.let { p ->
            (p[0].jsonPrimitive.content to if(p[1]==JsonNull) null else p[1].jsonArray.map(::chars)) to p[2].jsonArray.toMutableList()
        } }.toMutableMap()
        fun emit(name: String,vararg args: JsonElement) { events.add(JsonArray(listOf(JsonPrimitive(name))+args)) }
        override fun getCommonState()=current
        override fun putCommonState(state: CommonState) { emit("put",this@PandocDataTest.state(state));current=state }
        override fun logOutput(message: LogMessage) { emit("output",log(listOf(message)).jsonArray.single()) }
        private fun effect(name: String,args: List<CharacterString>,fallback: JsonElement): JsonElement {
            emit(name,JsonArray(args.map(::raw)),state(current))
            val exact=name to args;val key=if(responses.containsKey(exact)) exact else name to null
            val choices=responses[key];val r=choices?.firstOrNull()?.jsonArray ?: buildJsonArray { add("ok");add(fallback);add(JsonArray(emptyList())) }
            if(choices!=null && choices.size>1) choices.removeAt(0)
            return when(r[0].jsonPrimitive.content) {
                "ok" -> { operations(r[2].jsonArray);r[1] }
                "typed" -> { operations(r[2].jsonArray);throw PandocError.PandocSomeError(text(r[1])) }
                "host" -> { operations(r[1].jsonArray);throw HostFailure() }
                else -> error("response")
            }
        }
        private fun ioEffect(name: String,args: List<CharacterString>,fallback: JsonElement): JsonElement =
            try { effect(name,args,fallback) } catch(e: PandocError) { throw HostFailure() }
        override fun readFileStrict(path: CharacterString)=bytes(effect("strict",listOf(path),JsonArray(emptyList())))
        override fun readFileLazy(path: CharacterString)=bytes(effect("lazy",listOf(path),JsonArray(emptyList())))
        override fun fileExists(path: CharacterString)=effect("exists",listOf(path),JsonPrimitive(false)).jsonPrimitive.boolean
        override fun getDataFileName(path: CharacterString)=chars(effect("fileName",listOf(path),raw(CharacterStringChars.append(raw("installed/"),path))))
        override fun lookupEnv(name: String)=effect("env",listOf(raw(name)),JsonNull).let { if(it==JsonNull) null else text(it) }
        override fun getCurrentTime(): PandocUtcTime {
            val v=effect("time",emptyList(),buildJsonArray { add("40587");add("0") }).jsonArray
            return PandocUtcTime(BigInteger(v[0].jsonPrimitive.content),BigInteger(v[1].jsonPrimitive.content))
        }
        override fun getDataDir()=chars(ioEffect("dataDir",emptyList(),raw(raw("installed"))))
        override fun listDirectory(directory: CharacterString)=ioEffect("list",listOf(directory),JsonArray(emptyList())).jsonArray.map(::chars)
        override fun doesDirectoryExist(directory: CharacterString)=ioEffect("directory",listOf(directory),JsonPrimitive(false)).jsonPrimitive.boolean
        override fun getXdgDataDirectory(application: String)=chars(ioEffect("xdg",listOf(raw("XdgData"),raw(application)),raw(raw("xdg/pandoc"))))
        override fun getAppUserDataDirectory(application: String)=chars(ioEffect("legacy",listOf(raw(application)),raw(raw("legacy/pandoc"))))
        fun operations(ops: JsonArray) { ops.forEach { operation(it.jsonArray) } }
        private fun outputBytes(name: String,b: ByteArray) {
            emit(name,buildJsonObject { put("length",b.size);put("sha",MessageDigest.getInstance("SHA-256").digest(b).toHexString()) })
        }
        private fun rational(picos: BigInteger): String {
            val den=BigInteger.TEN.pow(12);val gcd=picos.gcd(den);val n=picos/gcd
            return (if(n.signum()<0) "($n)" else "$n")+" % "+(den/gcd)
        }
        private fun operation(v: JsonArray) { when(v[0].jsonPrimitive.content) {
            "user" -> setUserDataDir(if(v[1]==JsonNull) null else chars(v[1]))
            "header" -> modifyCommonState { it.copy(requestHeaders=listOf(text(v[1]) to text(v[2]))+it.requestHeaders) }
            "check" -> emit("check",PandocData.checkUserDataDir(this,chars(v[1]))?.let(::raw) ?: JsonNull)
            "read" -> outputBytes("read",readDataFile(chars(v[1])))
            "default" -> outputBytes("default",PandocData.readDefaultDataFile(this,chars(v[1])))
            "timestamp" -> emit("timestamp",JsonPrimitive(rational(PandocData.getTimestamp(this).posixPicoseconds())))
            "posix" -> emit("posix",JsonPrimitive(rational(PandocData.getPOSIXTime(this))))
            "names" -> emit("names",JsonArray(PandocData.getDataFileNames(embeddedData,this).map(::raw)))
            "userDefault" -> emit("userDefault",raw(PandocData.defaultUserDataDir(this)))
            "translations" -> { setTranslations(Lang.fromString(v[1].jsonPrimitive.content));emit("translations",JsonPrimitive(getTranslations().show())) }
            "caught" -> try { operations(v[1].jsonArray) } catch(e: PandocError) { emit("caught",JsonPrimitive(e.toString())) }
            "snapshot" -> emit("snapshot",state(current))
            "throwTyped" -> throw PandocError.PandocSomeError(text(v[1]))
            "throwHost" -> throw HostFailure()
            else -> error("operation")
        } }
    }
    private fun run(a: JsonArray): JsonElement = when(a[0].jsonPrimitive.content) {
        "catalog" -> JsonArray(PackagedPandocData.names.map { name->buildJsonArray {
            add(raw(name));add(JsonArray(checkNotNull(PackagedPandocData.read(name)).map { JsonPrimitive(it.toInt() and 255) }))
        } })
        "epoch" -> safeReadInteger(text(a[1]))?.toString()?.let(::JsonPrimitive) ?: JsonNull
        "run" -> {
            val runtime=Runtime(a[1].jsonPrimitive.int,a[3].jsonArray)
            val status=try { runtime.operations(a[2].jsonArray);"success" }
                catch(e: PandocError) { e.toString() } catch(e: HostFailure) { "host" } catch(e: ZipDecodeException) { "host" }
            buildJsonObject { put("status",status);put("state",state(runtime.getCommonState()));put("events",JsonArray(runtime.events)) }
        }
        else -> error("request")
    }
    @TestFactory
    fun originalDataObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/pandoc-data/data.jsonl.gz"))).bufferedReader().use { r->
        r.readLine();r.lineSequence().mapIndexed { i,line->
            var transport: String?=line
            DynamicTest.dynamicTest("Data ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun packagedPayloadsAreOwnedByTheCaller() {
        val name=PackagedPandocData.names.first();val original=checkNotNull(PackagedPandocData.read(name));val copy=original.copyOf()
        original.fill(0);assertContentEquals(copy,PackagedPandocData.read(name))
    }
}
