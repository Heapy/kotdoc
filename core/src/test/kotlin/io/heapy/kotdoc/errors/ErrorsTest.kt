package io.heapy.kotdoc.errors

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlin.test.*

class ErrorsTest {
    private val transport=ErrorTransport()
    private fun points(c: CharacterString)=JsonArray(c.points().map(::JsonPrimitive))
    private fun points(s: String)=JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private class Exited(val code: Int) : RuntimeException()
    private fun run(a: JsonArray): JsonElement = when(a[0].jsonPrimitive.content) {
        "error" -> {
            val e=transport.error(a[1]);val p=a[2].jsonPrimitive.long
            assertEquals(e.renderError(),e.message)
            buildJsonObject { put("rendered",e.renderError());put("displayed",points(e.message));put("shown",points(e.show(p)));put("shownText",TextChars.fromPoints(e.show(p).points())) }
        }
        "handle" -> {
            val e=transport.error(a[1]);val out=ByteArrayOutputStream();var writes=0
            val runtime=object : PandocErrorRuntime {
                override fun writeNativeLine(value: CharacterString) {
                    assertIs<PandocError.PandocIOError>(e);writes++
                    out.write(TextChars.fromPoints(value.points()).toByteArray(Charsets.UTF_8));out.write(10)
                }
                override fun writeUtf8Line(value: String) {
                    assertFalse(e is PandocError.PandocIOError);writes++
                    out.write(value.toByteArray(Charsets.UTF_8));out.write(10)
                }
                override fun exit(code: Int): Nothing { assertEquals(1,writes);throw Exited(code) }
            }
            try { handleError(PandocErrorResult.Failure(e),runtime);error("exit missing") }
            catch(exit: Exited) { buildJsonObject { put("exit",exit.code);put("stderr",out.toByteArray().toHexString()) } }
        }
        "io" -> {
            val e=transport.io(a[1]);buildJsonObject { put("shown",points(e.show()));put("text",e.displayedText());put("displayed",points(e.show())) }
        }
        "ioEq" -> JsonPrimitive(transport.io(a[1])==transport.io(a[2]))
        "cite" -> {
            val e=transport.cite(a[1]);buildJsonObject { put("shown",e.show(a[2].jsonPrimitive.long));put("pretty",e.pretty()) }
        }
        "citeEq" -> JsonPrimitive(transport.cite(a[1])==transport.cite(a[2]))
        else -> error(a[0])
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/errors/data.jsonl.gz")))
        .bufferedReader().use { reader ->
            reader.readLine()
            reader.lineSequence().mapIndexed { i,line -> DynamicTest.dynamicTest("Original errors $i") {
                val c=Json.parseToJsonElement(line).jsonObject;val a=c.getValue("request").jsonArray
                assertEquals(c.getValue("expected"),run(a),a.toString().take(400))
            } }.toList()
        }
    @Test
    fun successAndWriterFailureEffects() {
        val result=Any()
        val unused=object : PandocErrorRuntime {
            override fun writeNativeLine(value: CharacterString): Unit = error("unexpected write")
            override fun writeUtf8Line(value: String): Unit = error("unexpected write")
            override fun exit(code: Int): Nothing = error("unexpected exit")
        }
        assertSame(result,handleError(PandocErrorResult.Success(result),unused))
        val failure: Throwable=IllegalStateException("writer failed")
        val broken=object : PandocErrorRuntime {
            override fun writeNativeLine(value: CharacterString): Unit = throw failure
            override fun writeUtf8Line(value: String): Unit = throw failure
            override fun exit(code: Int): Nothing = error("exit after failed write")
        }
        assertSame(failure,assertFailsWith<IllegalStateException> { handleError(PandocErrorResult.Failure(PandocError.PandocSomeError("x")),broken) })
        assertSame(failure,assertFailsWith<IllegalStateException> { handleError(PandocErrorResult.Failure(PandocError.PandocIOError("x",GhcIoError.userError("x"))),broken) })
        val tagged=PandocError.PandocSomeError("x")
        assertIs<PandocError>(tagged)
        assertFalse(failure is PandocError)
    }
}
