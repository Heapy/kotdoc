package io.heapy.kotdoc.templates

import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class TemplateParserTest {
    private val protocol = TemplateRuntimeTest()
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun position(p: SourcePos) = JsonArray(listOf(JsonPrimitive(p.name), JsonPrimitive(p.line), JsonPrimitive(p.column)))
    private fun error(e: ParseError) = buildJsonObject {
        put("position", position(e.position)); put("show", e.toString())
        put("messages", JsonArray(e.messages.sortedBy { it.kind }.map { JsonArray(listOf(JsonPrimitive(it.kind.ordinal), JsonPrimitive(it.text))) }))
    }
    private fun errorValue(v: JsonElement): ParseError {
        val a = v.jsonArray; val p = a[0].jsonArray
        return ParseError(SourcePos(p[0].s(), p[1].n(), p[2].n()), a[1].jsonArray.map {
            ParseMessage(MessageKind.entries[it.jsonArray[0].jsonPrimitive.int], it.jsonArray[1].s())
        })
    }
    private fun pure(value: JsonElement): Parsec<Long, JsonElement> = Parsec.pure(value)
    private fun core(v: JsonElement): Parsec<Long, JsonElement> {
        val a = v.jsonArray
        fun p(i: Int) = core(a[i])
        fun state() = Parsec.state<Long>()
        return when (a[0].s()) {
            "pure" -> pure(a[1]); "zero" -> Parsec.zero(); "fail" -> Parsec.fail(a[1].s()); "unexpected" -> Parsec.unexpected(a[1].s())
            "char" -> Parsec.char<Long>(a[1].jsonPrimitive.int).map { JsonPrimitive(it) }
            "string" -> Parsec.string<Long>(a[1].s()).map(::JsonPrimitive)
            "string-atomic" -> Parsec.stringAtomic<Long>(a[1].s()).map(::JsonPrimitive)
            "many-chars" -> Parsec.char<Long>(a[1].jsonPrimitive.int).many().map { cps -> JsonPrimitive(buildString { cps.forEach(::appendCodePoint) }) }
            "concat-string" -> p(1).combine(p(2)) { x, y -> JsonPrimitive(x.s() + y.s()) }
            "sat" -> Parsec.satisfy<Long>(when (a[1].s()) {
                "alpha" -> ParserCharacters::isAlpha; "alnum" -> ParserCharacters::isAlphaNum
                "space" -> ParserCharacters::isSpace; "digit" -> ParserCharacters::isDigit; "any" -> { _ -> true }
                else -> error(v)
            }).map(::JsonPrimitive)
            "token" -> Parsec.anyToken<Long>().map(::JsonPrimitive)
            "then" -> p(1).then(p(2)); "before" -> p(1).before(p(2)); "pair" -> p(1).flatMap { x -> p(2).map { y -> JsonArray(listOf(x, y)) } }
            "choice" -> p(1) or p(2); "try" -> p(1).attempt(); "look" -> p(1).lookAhead(); "label" -> p(1).label(a[2].s())
            "many" -> p(1).many().map(::JsonArray); "many1" -> p(1).many1().map(::JsonArray)
            "many-alt" -> p(1).manyAlternative().map(::JsonArray)
            "notchar" -> Parsec.char<Long>(a[1].jsonPrimitive.int).notFollowedBy(Parsec.Companion::showChar).map { JsonNull }
            "eof" -> Parsec.eof<Long>().map { JsonNull }; "position" -> state().map { position(it.position) }
            "set-position" -> Parsec.modify<Long> { it.copy(position = SourcePos(a[1].s(), a[2].n(), a[3].n())) }.map { JsonNull }
            "input" -> state().map { JsonPrimitive(it.input.text.substring(it.input.index)) }
            "set-input" -> Parsec.modify<Long> { it.copy(input = ParseInput(a[1].s())) }.map { JsonNull }
            "user" -> state().map { JsonPrimitive(it.user) }; "set-user" -> Parsec.modify<Long> { it.copy(user = a[1].n()) }.map { JsonNull }
            else -> error(v)
        }
    }
    private fun coreRun(input: JsonElement): JsonElement {
        val a = input.jsonArray
        val r = core(a[0]).run(ParseState(ParseInput(a[2].s()), SourcePos(a[1].s()), a[3].n()))
        return buildJsonObject {
            put("consumed", r.consumed); put("ok", r.success); put("error", error(r.error))
            if (r.success) {
                put("value", r.value ?: JsonNull); put("position", position(r.state.position))
                put("remaining", r.state.input.text.substring(r.state.input.index)); put("user", r.state.user)
            }
        }
    }
    private fun <A : Any> compiled(input: JsonElement, codec: TemplateValues<A>, case: JsonObject): JsonElement {
        val a = input.jsonArray; val reads = mutableListOf<String>()
        val files = a[2].jsonArray.associate { it.jsonArray[0].s() to it.jsonArray[1].s() }
        val compiler = TemplateCompiler(codec, PartialResolver { path -> reads.add(path); files[path] ?: "" }, PartialPaths.Posix)
        val result: JsonElement = when (val r = compiler.compile(a[0].s(), a[1].s())) {
            is CompiledTemplate.Failure -> buildJsonObject { put("error", r.error) }
            is CompiledTemplate.Success -> {
                case["golden"]?.let { expected ->
                    val app = TemplateCompiler(codec, PartialResolver { files[it] ?: "" }, PartialPaths.Posix)
                        .apply(a[0].s(), a[1].s(), protocol.context(case.getValue("context"), codec), codec.context)
                    val doc = assertIs<AppliedTemplate.Success<A>>(app).doc
                    val width = case["width"]?.jsonPrimitive?.longOrNull
                    assertEquals(expected.s(), codec.toText(codec.layout.render(width, doc)))
                }
                protocol.compiledTree(r.template, codec)
            }
        }
        return buildJsonObject { put("reads", JsonArray(reads.map(::JsonPrimitive))); put("result", result) }
    }
    private fun actual(case: JsonObject): JsonElement {
        val input = case.getValue("input")
        return when (case.getValue("op").s()) {
            "compile-only-map" -> compiled(input, TemplateValues.Text, case)
            "compile-only-string-map" -> compiled(input, TemplateValues.String, case)
            "char-flags" -> JsonArray((input.jsonArray[0].jsonPrimitive.int..<input.jsonArray[1].jsonPrimitive.int).map { JsonPrimitive(ParserCharacters.flags(it)) })
            "partial-path" -> JsonPrimitive((if (input.jsonArray[0].s() == "posix") PartialPaths.Posix else PartialPaths.Windows)
                .resolve(input.jsonArray[1].s(), input.jsonArray[2].s()))
            "parsec-run" -> coreRun(input)
            "compile-file" -> {
                val root = System.getProperty("kotdoc.root")
                try {
                    when (val r = TemplateCompiler.Text.compileFile(java.nio.file.Path.of(root).resolve(input.s()).toString())) {
                        is CompiledTemplate.Success -> protocol.compiledTree(r.template, TemplateValues.Text)
                        is CompiledTemplate.Failure -> buildJsonObject {
                            val normalized = r.error.replace(root, "<repo>")
                            val end = normalized.indexOf("\" (line ")
                            put("error", if (end < 0) normalized else normalized.take(end).replace('\\', '/') + normalized.drop(end))
                        }
                    }
                } catch (e: java.io.IOException) { buildJsonObject { put("ioError", "IOException") } }
            }
            "error-values" -> {
                val e1 = errorValue(input.jsonArray[0]); val e2 = errorValue(input.jsonArray[1])
                val ms = e1.messages.sortedBy { it.kind } + e2.messages.sortedBy { it.kind }
                fun order(n: Int) = if (n < 0) "LT" else if (n > 0) "GT" else "EQ"
                buildJsonObject {
                    put("first", error(e1)); put("second", error(e2)); put("equal", e1 == e2)
                    put("merged", error(e1.merge(e2))); put("sourceOrder", order(e1.position.compareTo(e2.position)))
                    put("messageEquals", JsonArray(ms.map { a -> JsonArray(ms.map { b -> JsonPrimitive(a == b) }) }))
                    put("messageOrders", JsonArray(ms.map { a -> JsonArray(ms.map { b -> JsonPrimitive(order(a.compareTo(b))) }) }))
                }
            }
            else -> error(case)
        }
    }
    @TestFactory
    fun originalParserObservations(): Stream<DynamicTest> {
        val input = javaClass.getResourceAsStream("/templates/parser.jsonl.gz") ?: error("Missing parser corpus")
        val reader = GZIPInputStream(input).bufferedReader(); reader.readLine()
        return reader.lineSequence().mapIndexed { index, line ->
            DynamicTest.dynamicTest("original parser $index") {
                val c = Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"), actual(c), "Original input: ${c.getValue("input")}")
            }
        }.asStream().onClose(reader::close)
    }
    @Test
    fun originalConstantMemoryPureSequenceAndLongSourceName() {
        fun pureSequence(n: Int): Parsec<Unit, Unit> = if (n == 0) Parsec.pure(Unit) else
            Parsec.pure<Unit, Unit>(Unit).flatMap { Parsec.defer { pureSequence(n - 1) } }
        val sequence = pureSequence(1_000_000).run(ParseState(ParseInput(""), SourcePos("test"), Unit))
        assertTrue(sequence.success); assertFalse(sequence.consumed)
        val text = "a ".repeat(100_000)
        val word = (Parsec.char<Unit>(97) or Parsec.char(98)).many()
        val parser = word.flatMap { first -> Parsec.char<Unit>(32).then(word).many().map { listOf(first) + it } }
        val parsed = parser.run(ParseState(ParseInput(text), SourcePos(text), Unit))
        assertTrue(parsed.success); assertEquals(text.length, parsed.state.input.index)
        assertEquals(100_001, parsed.value?.size)
    }
    @Test
    fun deepSyntaxAndFileResolverEffects() {
        val depth = 2_000
        val result = TemplateCompiler.Text.compile("deep", "$" + "if(x)$" + ("$" + "if(x)$").repeat(depth - 1) + "body" + ("$" + "endif$").repeat(depth))
        assertIs<CompiledTemplate.Success<String>>(result)
        assertEquals(listOf("x"), result.template.getVariables())
        val flat = assertIs<CompiledTemplate.Success<String>>(TemplateCompiler.Text.compile("flat", "$$".repeat(20_000)))
        assertEquals("$".repeat(20_000), TemplateValues.Text.layout.render(null, TemplateRenderer.Text.render(flat.template, Context())))
        val dir = Files.createTempDirectory("kotdoc-partial-")
        try {
            Files.writeString(dir.resolve("part.txt"), "hello\n")
            Files.writeString(dir.resolve("main.txt"), "[" + "$" + "part()$]\n")
            val compiler = TemplateCompiler(TemplateValues.Text, PartialResolver.Files)
            val compiled = assertIs<CompiledTemplate.Success<String>>(compiler.compile(dir.resolve("main.txt").toString(), "$" + "part()$"))
            assertEquals("hello", TemplateValues.Text.layout.render(null, TemplateRenderer.Text.render(compiled.template, Context())))
            val root = assertIs<CompiledTemplate.Success<String>>(TemplateCompiler.Text.compileFile(dir.resolve("main.txt").toString()))
            assertEquals("[hello]\n", TemplateValues.Text.layout.render(null, TemplateRenderer.Text.render(root.template, Context())))
            assertFailsWith<java.io.IOException> { compiler.compile(dir.resolve("main.txt").toString(), "$" + "missing()$") }
        } finally {
            Files.deleteIfExists(dir.resolve("part.txt")); Files.deleteIfExists(dir.resolve("main.txt")); Files.deleteIfExists(dir)
        }
    }
}
