// aeson-pretty 0.8.11: Falko Peters; Aeson 2.2.3.0: Bryan O'Sullivan et al.
// BSD-3-Clause; original notices in licenses/ and sources in vendor/.
package io.heapy.kotdoc.json

import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import java.util.ArrayDeque

/** Aeson Value model; Scientific fields are retained instead of converting numbers to Double. */
sealed interface PrettyValue {
    data object Null : PrettyValue
    data class Bool(val value: Boolean) : PrettyValue
    class Text(value: String) : PrettyValue { val value = TextChars.fromString(value) }
    data class Number(val value: ScientificNumber) : PrettyValue
    class Array(values: List<PrettyValue>) : PrettyValue { val values = values.toList() }
    class Object(values: Map<String, PrettyValue>) : PrettyValue {
        val values = buildMap { values.forEach { (k,v) -> put(TextChars.fromString(k),v) } }
    }

    companion object {
        fun fromDouble(value: Double): PrettyValue = when {
            value.isNaN() -> Null
            value == Double.POSITIVE_INFINITY -> Text("+inf")
            value == Double.NEGATIVE_INFINITY -> Text("-inf")
            else -> Number(ScientificNumber.fromDouble(value))
        }
    }
}

sealed interface JsonIndent {
    data class Spaces(val count: Long) : JsonIndent
    data object Tab : JsonIndent
}
sealed interface JsonNumberFormat {
    data object Generic : JsonNumberFormat
    data object Scientific : JsonNumberFormat
    data object Decimal : JsonNumberFormat
    class Custom(val format: (ScientificNumber) -> String) : JsonNumberFormat
}
data class PrettyJsonConfig(
    val indent: JsonIndent = JsonIndent.Spaces(4),
    val compare: Comparator<String> = PrettyJson.keepOrder,
    val numberFormat: JsonNumberFormat = JsonNumberFormat.Generic,
    val trailingNewline: Boolean = false,
)

/** All aeson-pretty configuration modes over defined Values. Output uses an iterative stack. */
object PrettyJson {
    val keepOrder = Comparator<String> { _, _ -> 0 }
    val compare = Comparator<String> { a, b ->
        var i = 0; var j = 0
        var result = 0
        while (i < a.length && j < b.length) {
            val x = a.codePointAt(i); val y = b.codePointAt(j)
            if (x != y) { result = x.compareTo(y); break }
            i += Character.charCount(x); j += Character.charCount(y)
        }
        if (result == 0) (a.length - i).compareTo(b.length - j) else result
    }

    fun keyOrder(keys: List<String>): Comparator<String> {
        val indices = buildMap { keys.forEachIndexed { i, k -> putIfAbsent(TextChars.fromString(k), i.toLong()) } }
        return Comparator { a, b -> (indices[a] ?: Long.MAX_VALUE).compareTo(indices[b] ?: Long.MAX_VALUE) }
    }

    fun encode(value: PrettyValue, config: PrettyJsonConfig = PrettyJsonConfig()): String = buildString {
        chunks(value, config).forEach { append(it) }
    }
    fun encodeUtf8(value: PrettyValue, config: PrettyJsonConfig = PrettyJsonConfig()): ByteArray = encode(value, config).toByteArray(Charsets.UTF_8)

    private sealed interface Task {
        class Value(val value: PrettyValue, val level: Long) : Task
        class Literal(val text: String) : Task
        class Indent(val level: Long) : Task
        class Items(val values: Iterator<Pair<String?, PrettyValue>>, val level: Long, val end: String, var first: Boolean = true) : Task
    }

    /** Text chunks are demand-driven; numeric custom functions run when their value is reached. */
    fun chunks(value: PrettyValue, config: PrettyJsonConfig = PrettyJsonConfig()): Sequence<String> = sequence {
        val compact = config.indent == JsonIndent.Spaces(0)
        val newline = if (compact) "" else "\n"
        val stack = ArrayDeque<Task>()
        if (config.trailingNewline) stack.addLast(Task.Literal("\n"))
        stack.addLast(Task.Value(value, 0))
        while (stack.isNotEmpty()) when (val task = stack.removeLast()) {
            is Task.Literal -> if (task.text.isNotEmpty()) yield(task.text)
            is Task.Indent -> {
                val count = when (val indent = config.indent) {
                    is JsonIndent.Spaces -> maxOf(indent.count, 0)
                    JsonIndent.Tab -> 1L
                }
                var level = task.level
                while (level > 0 && count > 0) {
                    var remaining = count
                    while (remaining > 0) {
                        val n = minOf(remaining, 4096).toInt()
                        yield(if (config.indent == JsonIndent.Tab) "\t" else " ".repeat(n))
                        remaining -= n
                    }
                    level--
                }
            }
            is Task.Items -> {
                if (task.values.hasNext()) {
                    val (key, item) = task.values.next()
                    yield(if (task.first) newline else ",$newline")
                    task.first = false
                    stack.addLast(task)
                    stack.addLast(Task.Value(item, task.level + 1))
                    if (key != null) stack.addLast(Task.Literal(quote(key) + if (compact) ":" else ": "))
                    stack.addLast(Task.Indent(task.level + 1))
                } else {
                    stack.addLast(Task.Literal(task.end))
                    if (!task.first) { yield(newline); stack.addLast(Task.Indent(task.level)) }
                }
            }
            is Task.Value -> when (val v = task.value) {
                PrettyValue.Null -> yield("null")
                is PrettyValue.Bool -> yield(v.value.toString())
                is PrettyValue.Text -> yield(quote(v.value))
                is PrettyValue.Number -> yieldAll(when (val fmt = config.numberFormat) {
                    JsonNumberFormat.Generic -> ScientificFormatter.prettyGenericChunks(v.value)
                    JsonNumberFormat.Scientific -> ScientificFormatter.chunks(v.value, ScientificFormat.Exponent)
                    JsonNumberFormat.Decimal -> ScientificFormatter.chunks(v.value, ScientificFormat.Fixed)
                    is JsonNumberFormat.Custom -> sequenceOf(TextChars.fromString(fmt.format(v.value)))
                })
                is PrettyValue.Array -> {
                    yield("[")
                    stack.addLast(Task.Items(v.values.asSequence().map { null to it }.iterator(), task.level, "]"))
                }
                is PrettyValue.Object -> {
                    yield("{")
                    // Pinned Aeson +ordered-keymap first enumerates keys in scalar Text order.
                    val input = v.values.entries.sortedWith { a, b -> compare.compare(a.key, b.key) }.map { it.key to it.value }
                    val sorted = PrettySort.sort(input) { a, b -> config.compare.compare(a.first, b.first) > 0 }
                    stack.addLast(Task.Items(sorted.iterator(), task.level, "}"))
                }
            }
        }
    }

    internal fun quote(text: String): String = buildString {
        append('"')
        text.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
        } }
        append('"')
    }

    /** Adapter for defined kotlinx JSON trees; this does not replace Aeson's JSON parser. */
    fun fromJson(value: JsonElement): PrettyValue {
        val results = ArrayDeque<PrettyValue>()
        val pending = ArrayDeque<Pair<JsonElement, Boolean>>()
        pending.addLast(value to false)
        while (pending.isNotEmpty()) {
            val (v, ready) = pending.removeLast()
            when (v) {
                is JsonArray -> if (ready) {
                    val children = List(v.size) { results.removeLast() }.asReversed()
                    results.addLast(PrettyValue.Array(children))
                } else {
                    pending.addLast(v to true); v.asReversed().forEach { pending.addLast(it to false) }
                }
                is JsonObject -> if (ready) {
                    val entries = v.entries.toList()
                    val children = List(v.size) { results.removeLast() }.asReversed()
                    results.addLast(PrettyValue.Object(entries.mapIndexed { i, entry -> entry.key to children[i] }.toMap()))
                } else {
                    pending.addLast(v to true); v.values.toList().asReversed().forEach { pending.addLast(it to false) }
                }
                JsonNull -> results.addLast(PrettyValue.Null)
                is JsonPrimitive -> results.addLast(when {
                    v.isString -> PrettyValue.Text(v.content)
                    v.booleanOrNull != null -> PrettyValue.Bool(v.boolean)
                    else -> PrettyValue.Number(ScientificNumber.fromDecimal(v.content))
                })
            }
        }
        return results.single()
    }
}
