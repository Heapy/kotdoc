// Port of Pandoc Text.Pandoc.Readers.Native and the corresponding derived Read shapes.
// Copyright (C) 2011-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*

/** Reads Pandoc, [Block], Block, [Inline], or Inline in that precedence order. */
object NativeReader {
    private val codec = PandocJsonCodec(nativeFloatingPoint = true)
    fun read(input: String): Pandoc {
        val value = Parser(input).read()
        if (value is Constructor && value.name == "Pandoc") {
            val args = value.args(2)
            return Pandoc(codec.decodeMeta(meta(args[0])), args[1].list().map { codec.decodeBlock(block(it)) })
        }
        val values = if (value is Sequence) value.values else listOf(value)
        if (values.all { it is Constructor && it.name in blockTags }) {
            return Pandoc(blocks = values.map { codec.decodeBlock(block(it)) })
        }
        return Pandoc(blocks = listOf(Block.Plain(values.map { codec.decodeInline(inline(it)) })))
    }

    private val blockTags = setOf("Plain", "Para", "LineBlock", "CodeBlock", "RawBlock", "BlockQuote", "OrderedList", "BulletList", "DefinitionList", "Header", "HorizontalRule", "Table", "Figure", "Div")
    private val simpleInlineTags = setOf("Emph", "Underline", "Strong", "Strikeout", "Superscript", "Subscript", "SmallCaps")

    private fun meta(value: Value): JsonObject {
        val record = value.record("Meta", listOf("unMeta"))
        return mapping(record.getValue("unMeta"))
    }
    private fun mapping(value: Value): JsonObject = JsonObject(value.constructor("fromList", 1)[0].list().associate {
        val pair = it.tuple(2)
        pair[0].text() to metaValue(pair[1])
    })
    private fun metaValue(value: Value): JsonElement {
        val name = value.name()
        val arg = value.constructor(name, 1)[0]
        return tagged(name, when (name) {
            "MetaMap" -> mapping(arg)
            "MetaList" -> many(arg, ::metaValue)
            "MetaBool" -> JsonPrimitive(when (arg.name()) { "True" -> { arg.constructor("True", 0); true }; "False" -> { arg.constructor("False", 0); false }; else -> error("Expected True or False") })
            "MetaString" -> str(arg)
            "MetaInlines" -> many(arg, ::inline)
            "MetaBlocks" -> many(arg, ::block)
            else -> error("Unknown metadata constructor: $name")
        })
    }
    private fun inline(value: Value): JsonElement {
        val name = value.name()
        if (name in setOf("Space", "SoftBreak", "LineBreak")) { value.constructor(name, 0); return tagged(name) }
        val content = when (name) {
            "Str" -> str(value.constructor(name, 1)[0])
            in simpleInlineTags -> many(value.constructor(name, 1)[0], ::inline)
            "Quoted" -> value.constructor(name, 2).let { array(enum(it[0]), many(it[1], ::inline)) }
            "Cite" -> value.constructor(name, 2).let { array(many(it[0], ::citation), many(it[1], ::inline)) }
            "Code" -> value.constructor(name, 2).let { array(attr(it[0]), str(it[1])) }
            "Math" -> value.constructor(name, 2).let { array(enum(it[0]), str(it[1])) }
            "RawInline" -> value.constructor(name, 2).let { array(format(it[0]), str(it[1])) }
            "Link", "Image" -> value.constructor(name, 3).let { array(attr(it[0]), many(it[1], ::inline), target(it[2])) }
            "Note" -> many(value.constructor(name, 1)[0], ::block)
            "Span" -> value.constructor(name, 2).let { array(attr(it[0]), many(it[1], ::inline)) }
            else -> error("Unknown inline constructor: $name")
        }
        return tagged(name, content)
    }
    private fun block(value: Value): JsonElement {
        val name = value.name()
        if (name == "HorizontalRule") { value.constructor(name, 0); return tagged(name) }
        val content = when (name) {
            "Plain", "Para" -> many(value.constructor(name, 1)[0], ::inline)
            "LineBlock" -> many(value.constructor(name, 1)[0]) { many(it, ::inline) }
            "CodeBlock" -> value.constructor(name, 2).let { array(attr(it[0]), str(it[1])) }
            "RawBlock" -> value.constructor(name, 2).let { array(format(it[0]), str(it[1])) }
            "BlockQuote" -> many(value.constructor(name, 1)[0], ::block)
            "OrderedList" -> value.constructor(name, 2).let { args ->
                val attrs = args[0].tuple(3)
                array(array(integer(attrs[0]), enum(attrs[1]), enum(attrs[2])), many(args[1]) { many(it, ::block) })
            }
            "BulletList" -> many(value.constructor(name, 1)[0]) { many(it, ::block) }
            "DefinitionList" -> many(value.constructor(name, 1)[0]) { term ->
                term.tuple(2).let { array(many(it[0], ::inline), many(it[1]) { definition -> many(definition, ::block) }) }
            }
            "Header" -> value.constructor(name, 3).let { array(integer(it[0]), attr(it[1]), many(it[2], ::inline)) }
            "Table" -> value.constructor(name, 6).let { array(attr(it[0]), caption(it[1]), many(it[2], ::colSpec), tableHead(it[3]), many(it[4], ::tableBody), tableFoot(it[5])) }
            "Figure" -> value.constructor(name, 3).let { array(attr(it[0]), caption(it[1]), many(it[2], ::block)) }
            "Div" -> value.constructor(name, 2).let { array(attr(it[0]), many(it[1], ::block)) }
            else -> error("Unknown block constructor: $name")
        }
        return tagged(name, content)
    }
    private fun attr(value: Value): JsonElement = value.tuple(3).let { array(str(it[0]), many(it[1], ::str), many(it[2], ::target)) }
    private fun target(value: Value): JsonElement = value.tuple(2).let { array(str(it[0]), str(it[1])) }
    private fun format(value: Value): JsonElement = str(value.constructor("Format", 1)[0])
    private fun caption(value: Value): JsonElement = value.constructor("Caption", 2).let {
        val short = when (it[0].name()) {
            "Nothing" -> { it[0].constructor("Nothing", 0); JsonNull }
            "Just" -> many(it[0].constructor("Just", 1)[0], ::inline)
            else -> error("Expected Nothing or Just for short caption")
        }
        array(short, many(it[1], ::block))
    }
    private fun colSpec(value: Value): JsonElement = value.tuple(2).let {
        val width = when (it[1].name()) {
            "ColWidthDefault" -> { it[1].constructor("ColWidthDefault", 0); tagged("ColWidthDefault") }
            "ColWidth" -> PandocJson.encodeColWidth(ColWidth.Width(floating(it[1].constructor("ColWidth", 1)[0])))
            else -> error("Unknown column width constructor")
        }
        array(enum(it[0]), width)
    }
    private fun tableHead(value: Value): JsonElement = value.constructor("TableHead", 2).let { array(attr(it[0]), many(it[1], ::row)) }
    private fun tableFoot(value: Value): JsonElement = value.constructor("TableFoot", 2).let { array(attr(it[0]), many(it[1], ::row)) }
    private fun tableBody(value: Value): JsonElement = value.constructor("TableBody", 4).let { array(attr(it[0]), integer(it[1].constructor("RowHeadColumns", 1)[0]), many(it[2], ::row), many(it[3], ::row)) }
    private fun row(value: Value): JsonElement = value.constructor("Row", 2).let { array(attr(it[0]), many(it[1], ::cell)) }
    private fun cell(value: Value): JsonElement = value.constructor("Cell", 5).let { array(attr(it[0]), enum(it[1]), integer(it[2].constructor("RowSpan", 1)[0]), integer(it[3].constructor("ColSpan", 1)[0]), many(it[4], ::block)) }
    private fun citation(value: Value): JsonElement {
        val fields = value.record("Citation", listOf("citationId", "citationPrefix", "citationSuffix", "citationMode", "citationNoteNum", "citationHash"))
        return buildJsonObject {
            put("citationId", str(fields.getValue("citationId")))
            put("citationPrefix", many(fields.getValue("citationPrefix"), ::inline))
            put("citationSuffix", many(fields.getValue("citationSuffix"), ::inline))
            put("citationMode", enum(fields.getValue("citationMode")))
            put("citationNoteNum", integer(fields.getValue("citationNoteNum")))
            put("citationHash", integer(fields.getValue("citationHash")))
        }
    }
    private fun tagged(name: String, content: JsonElement? = null): JsonObject = buildJsonObject {
        put("t", name); if (content != null) put("c", content)
    }
    private fun enum(value: Value): JsonElement { val name = value.name(); value.constructor(name, 0); return tagged(name) }
    private fun str(value: Value): JsonElement = JsonPrimitive(value.text())
    private fun integer(value: Value): JsonElement {
        val raw = value.number()
        val unsigned = raw.removePrefix("-")
        val number = when {
            unsigned.startsWith("0x", true) -> unsigned.substring(2).toBigInteger(16)
            unsigned.startsWith("0o", true) -> unsigned.substring(2).toBigInteger(8)
            else -> unsigned.toBigInteger()
        }
        // Haskell Read Int accepts arbitrary integer literals and wraps to machine width.
        return JsonPrimitive((if (raw.startsWith('-')) -number else number).toLong())
    }
    private fun floating(value: Value): Double {
        val text = if (value is Constructor) { value.args(0); value.name } else value.number()
        val unsigned = text.removePrefix("-")
        val result = when {
            unsigned.startsWith("0x", true) -> unsigned.substring(2).toBigInteger(16).toDouble()
            unsigned.startsWith("0o", true) -> unsigned.substring(2).toBigInteger(8).toDouble()
            else -> return text.toDouble()
        }
        return if (text.startsWith('-')) -result else result
    }
    private fun many(value: Value, f: (Value) -> JsonElement): JsonArray = JsonArray(value.list().map(f))
    private fun array(vararg elements: JsonElement): JsonArray = JsonArray(elements.toList())

    private sealed interface Value
    private data class Constructor(val name: String, val args: List<Value> = emptyList()) : Value {
        fun args(count: Int): List<Value> { require(args.size == count) { "Expected $count arguments for $name, found ${args.size}" }; return args }
    }
    private data class Record(val name: String, val fields: Map<String, Value>) : Value
    private data class Sequence(val values: List<Value>) : Value
    private data class Tuple(val values: List<Value>) : Value
    private data class TextValue(val value: String) : Value
    private data class NumberValue(val value: String) : Value
    private fun Value.name(): String = (this as? Constructor)?.name ?: error("Expected constructor, found $this")
    private fun Value.constructor(name: String, arity: Int): List<Value> {
        require(this is Constructor && this.name == name) { "Expected constructor $name" }
        return args(arity)
    }
    private fun Value.record(name: String, fields: List<String>): Map<String, Value> {
        require(this is Record && this.name == name && this.fields.keys.toList() == fields) { "Expected $name record fields $fields in declaration order" }
        return this.fields
    }
    private fun Value.list(): List<Value> = (this as? Sequence)?.values ?: error("Expected list")
    private fun Value.tuple(size: Int): List<Value> = (this as? Tuple)?.values?.also { require(it.size == size) { "Expected $size-tuple" } } ?: error("Expected tuple")
    private fun Value.text(): String = (this as? TextValue)?.value ?: error("Expected string")
    private fun Value.number(): String = (this as? NumberValue)?.value ?: error("Expected number")

    private class Parser(private val input: String) {
        private var position = 0
        fun read(): Value = expression().also { skipSpace(); require(position == input.length) { "Trailing native input at $position" } }

        private fun expression(): Value {
            skipSpace()
            val application = input.getOrNull(position)?.isLetter() == true
            val first = atom()
            if (!application || first !is Constructor) return first
            val args = mutableListOf<Value>()
            skipSpace()
            while (position < input.length && input[position] !in ",])}") {
                args += atom()
                skipSpace()
            }
            return first.copy(args = args)
        }
        private fun atom(): Value {
            skipSpace()
            require(position < input.length) { "Unexpected end of native input" }
            return when (input[position]) {
                '"' -> TextValue(string())
                '[' -> { position++; Sequence(separated(']')) }
                '(' -> {
                    position++
                    val values = separated(')')
                    if (values.size == 1) values.single() else Tuple(values)
                }
                '-', in '0'..'9' -> {
                    if (input.startsWith("-Infinity", position)) {
                        position += "-Infinity".length
                        return NumberValue("-Infinity")
                    }
                    if (input.startsWith("-NaN", position)) {
                        position += "-NaN".length
                        return NumberValue("-NaN")
                    }
                    val match = numberPattern.find(input, position)
                    require(match != null && match.range.first == position) { "Invalid number at $position" }
                    position += match.value.length
                    NumberValue(match.value)
                }
                else -> {
                    val name = identifier()
                    skipSpace()
                    if (consume('{')) {
                        val fields = linkedMapOf<String, Value>()
                        if (!consume('}')) {
                            do {
                                val key = identifier()
                                expect('=')
                                require(key !in fields) { "Duplicate record field: $key" }
                                fields[key] = expression()
                            } while (consume(','))
                            expect('}')
                        }
                        Record(name, fields)
                    } else Constructor(name)
                }
            }
        }
        private fun separated(end: Char): List<Value> {
            val values = mutableListOf<Value>()
            if (consume(end)) return values
            do { values += expression() } while (consume(','))
            expect(end)
            return values
        }
        private fun identifier(): String {
            skipSpace()
            val start = position
            require(position < input.length && (input[position].isLetter() || input[position] == '_')) { "Expected identifier at $position" }
            position++
            while (position < input.length && (input[position].isLetterOrDigit() || input[position] in "_'")) position++
            return input.substring(start, position)
        }
        private fun skipSpace() { while (position < input.length && input[position].isWhitespace()) position++ }
        private fun consume(char: Char): Boolean { skipSpace(); if (position < input.length && input[position] == char) { position++; return true }; return false }
        private fun expect(char: Char) { require(consume(char)) { "Expected '$char' at $position" } }

        private fun string(): String = buildString {
            position++
            while (true) {
                require(position < input.length) { "Unterminated native string" }
                val char = input[position++]
                if (char == '"') break
                if (char != '\\') { append(char); continue }
                require(position < input.length) { "Unterminated native escape" }
                val escaped = input[position++]
                when (escaped) {
                    'a' -> append('\u0007'); 'b' -> append('\b'); 't' -> append('\t'); 'n' -> append('\n')
                    'v' -> append('\u000b'); 'f' -> append('\u000c'); 'r' -> append('\r')
                    '\\', '"', '\'' -> append(escaped)
                    '&' -> Unit
                    '^' -> {
                        require(position < input.length && input[position] in '@'..'_') { "Invalid control escape" }
                        append((input[position++].code - 64).toChar())
                    }
                    in '0'..'9', 'o', 'x' -> {
                        val radix = when (escaped) { 'o' -> 8; 'x' -> 16; else -> 10 }
                        val start = if (radix == 10) --position else position
                        while (position < input.length && input[position].digitToIntOrNull(radix) != null) position++
                        require(position > start) { "Empty numeric escape" }
                        val cp = input.substring(start, position).toInt(radix)
                        require(cp in 0..0x10ffff) { "Out-of-range Unicode escape" }
                        appendCodePoint(if (cp in 0xd800..0xdfff) 0xfffd else cp)
                    }
                    else -> if (escaped.isWhitespace()) {
                        skipSpace(); require(position < input.length && input[position++] == '\\') { "Unterminated string gap" }
                    } else {
                        position--
                        val names = HaskellString.controlNames + "DEL"
                        val name = names.sortedByDescending { it.length }.firstOrNull { input.startsWith(it, position) }
                            ?: error("Unknown escape at $position")
                        append(if (name == "DEL") '\u007f' else names.indexOf(name).toChar())
                        position += name.length
                    }
                }
            }
        }
        companion object {
            private val numberPattern = Regex("-?(?:0[xX][0-9a-fA-F]+|0[oO][0-7]+|[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)")
        }
    }
}
