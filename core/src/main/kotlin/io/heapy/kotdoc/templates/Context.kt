// Text.DocTemplates.Internal 0.11.1, Copyright (C) 2009-2019 John MacFarlane.
// BSD-3-Clause; see licenses/Doctemplates-BSD-3-Clause.txt.
package io.heapy.kotdoc.templates

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.layout.Doc
import io.heapy.kotdoc.layout.forEachValue
import io.heapy.kotdoc.layout.mapValues
import java.util.ArrayDeque
import java.util.Collections

/** Template variables. Map union is left biased, as in Data.Map's Semigroup. */
class Context<out A>(entries: Map<String, Val<A>> = emptyMap()) {
    val entries: Map<String, Val<A>> = Collections.unmodifiableMap(entries.toSortedMap(UnicodeTextOrder))
    operator fun get(key: String): Val<A>? = entries[key]
    operator fun plus(other: Context<@UnsafeVariance A>): Context<A> = Context(other.entries + entries)
    fun <B : Any> lookup(key: String, decoder: FromContext<@UnsafeVariance A, B>): B? =
        entries[key]?.let(decoder::fromVal)
    fun <B> mapValues(f: (A) -> B): Context<B> = Context(entries.mapValues { it.value.mapValues(f) })
    fun forEachValue(f: (A) -> Unit) = entries.values.forEach { it.forEachValue(f) }
    fun <B> foldValues(initial: B, f: (B, A) -> B): B {
        var result = initial
        forEachValue { result = f(result, it) }
        return result
    }
    override fun equals(other: Any?) = other is Context<*> && entries == other.entries
    override fun hashCode() = entries.hashCode()
    override fun toString() = "Context($entries)"
}

sealed interface Val<out A> {
    data class SimpleVal<A>(val doc: Doc<A>) : Val<A>
    class ListVal<out A>(values: List<Val<A>>) : Val<A> {
        val values: List<Val<A>> = Collections.unmodifiableList(values.toList())
        override fun equals(other: Any?) = other is ListVal<*> && values == other.values
        override fun hashCode() = values.hashCode()
        override fun toString() = "ListVal($values)"
    }
    data class MapVal<A>(val context: Context<A>) : Val<A>
    data class BoolVal(val value: Boolean) : Val<Nothing>
    data object NullVal : Val<Nothing>
}

/** Explicit Kotlin equivalents of the original conversion type classes. */
fun interface ToContext<A, B> {
    fun toVal(value: B): Val<A>
    fun toContext(value: B): Context<A> = when (val v = toVal(value)) {
        is Val.MapVal -> v.context
        else -> Context()
    }
}
fun interface FromContext<A, B : Any> {
    fun fromVal(value: Val<A>): B?
    fun lookupContext(key: String, context: Context<A>): B? = context.lookup(key, this)
}

/** Map each SimpleVal document, without flattening it or changing cached widths. */
fun <A, B> Val<A>.mapDocs(f: (Doc<A>) -> Doc<B>): Val<B> {
    data class Frame<A>(val value: Val<A>, val finish: Boolean = false)
    val todo = ArrayDeque<Frame<A>>()
    val done = ArrayDeque<Val<B>>()
    todo.push(Frame(this))
    while (todo.isNotEmpty()) {
        val frame = todo.pop()
        when (val value = frame.value) {
            is Val.SimpleVal -> done.push(Val.SimpleVal(f(value.doc)))
            is Val.BoolVal -> done.push(value)
            Val.NullVal -> done.push(Val.NullVal)
            is Val.ListVal -> if (!frame.finish) {
                todo.push(Frame(value, true))
                value.values.asReversed().forEach { todo.push(Frame(it)) }
            } else {
                val children = List(value.values.size) { done.pop() }.asReversed()
                done.push(Val.ListVal(children))
            }
            is Val.MapVal -> if (!frame.finish) {
                todo.push(Frame(value, true))
                value.context.entries.values.toList().asReversed().forEach { todo.push(Frame(it)) }
            } else {
                val keys = value.context.entries.keys.toList()
                val children = List(keys.size) { done.pop() }.asReversed()
                done.push(Val.MapVal(Context(keys.zip(children).toMap())))
            }
        }
    }
    return done.pop()
}
fun <A, B> Val<A>.mapValues(f: (A) -> B): Val<B> = mapDocs { it.mapValues(f) }
fun <A> Val<A>.forEachValue(f: (A) -> Unit) {
    val todo = ArrayDeque<Val<A>>()
    todo.push(this)
    while (todo.isNotEmpty()) when (val value = todo.pop()) {
        is Val.SimpleVal -> value.doc.forEachValue(f)
        is Val.ListVal -> value.values.asReversed().forEach(todo::push)
        is Val.MapVal -> value.context.entries.values.toList().asReversed().forEach(todo::push)
        else -> Unit
    }
}
fun <A, B> Val<A>.foldValues(initial: B, f: (B, A) -> B): B {
    var result = initial
    forEachValue { result = f(result, it) }
    return result
}
