// Text.Pandoc.Writers.Shared, Copyright (C) 2013-2024 John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.Doc
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.templates.*
import java.util.ArrayDeque

/** Metadata/template bridge. Writers run once per leaf, in source map/list order. */
object WriterMetadata {
    fun <A : Any> metaToContext(
        options: WriterOptions, values: TemplateValues<A>,
        blockWriter: (List<Block>) -> Doc<A>, inlineWriter: (List<Inline>) -> Doc<A>,
        metadata: Map<String, MetaValue>,
    ): Context<A> = if (options.template == null) Context() else
        addVariablesToContext(options, values, metaToContextPrime(blockWriter, inlineWriter, metadata))

    /** Source metaToContext': independent of the template and writer variables. */
    fun <A> metaToContextPrime(
        blockWriter: (List<Block>) -> Doc<A>, inlineWriter: (List<Inline>) -> Doc<A>,
        metadata: Map<String, MetaValue>,
    ): Context<A> {
        data class Work(val value: MetaValue? = null, val keys: List<String>? = null, val count: Int = 0)
        val todo = ArrayDeque<Work>()
        val done = ArrayDeque<Val<A>>()
        todo.push(Work(MetaValue.MetaMap(metadata)))
        while (todo.isNotEmpty()) {
            val work = todo.pop()
            when (val value = work.value) {
                is MetaValue.MetaMap -> {
                    val entries = value.values.toSortedMap(UnicodeTextOrder).entries.toList()
                    todo.push(Work(keys = entries.map { it.key }))
                    entries.asReversed().forEach { todo.push(Work(it.value)) }
                }
                is MetaValue.MetaList -> {
                    todo.push(Work(count = value.values.size))
                    value.values.asReversed().forEach { todo.push(Work(it)) }
                }
                is MetaValue.MetaBool -> done.push(Val.BoolVal(value.value))
                is MetaValue.MetaString -> done.push(Val.SimpleVal(inlineWriter(Builder.text(value.value).values)))
                is MetaValue.MetaBlocks -> done.push(Val.SimpleVal(blockWriter(value.content)))
                is MetaValue.MetaInlines -> done.push(Val.SimpleVal(inlineWriter(value.content)))
                null -> {
                    val children = List(work.keys?.size ?: work.count) { done.pop() }.asReversed()
                    done.push(if (work.keys == null) Val.ListVal(children)
                        else Val.MapVal(Context(work.keys.zip(children).toMap())))
                }
            }
        }
        return (done.pop() as Val.MapVal).context
    }

    fun <A : Any> addVariablesToContext(options: WriterOptions, values: TemplateValues<A>, context: Context<A>): Context<A> {
        val json = contextJson(values, context)
        val generated = Context(mapOf("meta-json" to Val.SimpleVal(values.layout.literal(values.fromText(json)))))
        return generated + options.variables.mapValues(values.fromText) + context
    }

    // Context's source JSON instance has only strings, booleans, null, lists
    // and maps. Serialize iteratively, preserving its ordered keys and exact
    // rendered strings, without imposing the host JSON tree's recursion limit.
    private sealed interface JsonWork<out A> {
        data class Value<A>(val value: Val<A>) : JsonWork<A>
        data class Map<A>(val context: Context<A>) : JsonWork<A>
        data class Text(val text: String) : JsonWork<Nothing>
    }
    internal fun <A : Any> contextJson(values: TemplateValues<A>, context: Context<A>): String = buildString {
        val todo = ArrayDeque<JsonWork<A>>()
        todo.push(JsonWork.Map(context))
        while (todo.isNotEmpty()) when (val work = todo.pop()) {
            is JsonWork.Text -> append(work.text)
            is JsonWork.Map -> {
                append('{'); todo.push(JsonWork.Text("}"))
                val entries = work.context.entries.entries.toList()
                for (index in entries.indices.reversed()) {
                    val entry = entries[index]
                    todo.push(JsonWork.Value(entry.value)); todo.push(JsonWork.Text(":"))
                    todo.push(JsonWork.Text(quoteValueString(entry.key)))
                    if (index > 0) todo.push(JsonWork.Text(","))
                }
            }
            is JsonWork.Value -> when (val value = work.value) {
                Val.NullVal -> append("null")
                is Val.BoolVal -> append(if (value.value) "true" else "false")
                is Val.SimpleVal -> append(quoteValueString(values.toText(values.layout.render(null, value.doc))))
                is Val.MapVal -> todo.push(JsonWork.Map(value.context))
                is Val.ListVal -> {
                    append('['); todo.push(JsonWork.Text("]"))
                    for (index in value.values.indices.reversed()) {
                        todo.push(JsonWork.Value(value.values[index]))
                        if (index > 0) todo.push(JsonWork.Text(","))
                    }
                }
            }
        }
    }

    // Aeson 2.2.3.0's Value encoder uses numeric escapes for backspace/form
    // feed, unlike kotlinx.serialization's short escapes. Literal backslashes
    // are scanned directly so strings containing "\\b"/"\\f" stay unchanged.
    private fun quoteValueString(value: String): String = buildString {
        append('"')
        for (point in TextChars.points(TextChars.fromString(value))) when (point) {
            34 -> append("\\\""); 92 -> append("\\\\")
            9 -> append("\\t"); 10 -> append("\\n"); 13 -> append("\\r")
            in 0..31 -> append("\\u").append(point.toString(16).padStart(4, '0'))
            else -> appendCodePoint(point)
        }
        append('"')
    }

    fun <A, B : Any> getField(field: String, context: Context<A>, decoder: FromContext<A, B>): B? =
        context.lookup(field, decoder)

    fun <A, B> setField(field: String, value: B, context: Context<A>, converter: ToContext<A, B>): Context<A> {
        val newValue = converter.toVal(value)
        val oldValue = context[field]
        val result = when (oldValue) {
            null -> newValue
            is Val.ListVal -> Val.ListVal(oldValue.values + newValue)
            else -> Val.ListVal(listOf(oldValue, newValue))
        }
        return Context(context.entries + (field to result))
    }
    fun <A, B> resetField(field: String, value: B, context: Context<A>, converter: ToContext<A, B>): Context<A> =
        Context(context.entries + (field to converter.toVal(value)))

    fun <A, B> defField(field: String, value: B, context: Context<A>, converter: ToContext<A, B>): Context<A> =
        if (field in context.entries) context else resetField(field, value, context, converter)

    fun getLang(options: WriterOptions, metadata: Map<String, MetaValue>): String? =
        options.variables.lookup("lang", TemplateValues.Text.targetDecoder) ?: when (val value = metadata["lang"]) {
            is MetaValue.MetaString -> value.value
            is MetaValue.MetaInlines -> (value.content.singleOrNull() as? Inline.Str)?.text
            is MetaValue.MetaBlocks -> when (val block = value.content.singleOrNull()) {
                is Block.Plain -> (block.content.singleOrNull() as? Inline.Str)?.text
                is Block.Para -> (block.content.singleOrNull() as? Inline.Str)?.text
                else -> null
            }
            else -> null
        }

    fun lookupMetaBool(key: String, metadata: Map<String, MetaValue>): Boolean = when (val value = metadata[key]) {
        is MetaValue.MetaBlocks -> value.content.isNotEmpty()
        is MetaValue.MetaInlines -> value.content.isNotEmpty()
        is MetaValue.MetaString -> value.value.isNotEmpty()
        is MetaValue.MetaBool -> value.value
        else -> false
    }
    fun lookupMetaBlocks(key: String, metadata: Map<String, MetaValue>): List<Block> = when (val value = metadata[key]) {
        is MetaValue.MetaBlocks -> value.content
        is MetaValue.MetaInlines -> listOf(Block.Plain(value.content))
        is MetaValue.MetaString -> listOf(Block.Plain(listOf(Inline.Str(value.value))))
        else -> emptyList()
    }
    fun lookupMetaInlines(key: String, metadata: Map<String, MetaValue>): List<Inline> = when (val value = metadata[key]) {
        is MetaValue.MetaString -> listOf(Inline.Str(value.value))
        is MetaValue.MetaInlines -> value.content
        is MetaValue.MetaBlocks -> when (val block = value.content.singleOrNull()) {
            is Block.Plain -> block.content; is Block.Para -> block.content; else -> emptyList()
        }
        else -> emptyList()
    }
    fun lookupMetaString(key: String, metadata: Map<String, MetaValue>): String = DocumentShared.lookupMetaString(key, metadata)
}
