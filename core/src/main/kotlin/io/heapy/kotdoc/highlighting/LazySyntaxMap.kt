// Skylighting.Syntax 0.15 / Data.Map's lazy value boundary.
// Copyright John MacFarlane and containers contributors. BSD-3-Clause.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.ast.UnicodeTextOrder
import java.util.Collections

internal interface ImmutableOrderedSyntaxMap

/** Immutable ordered keys with independently shared lazy values. */
internal class LazySyntaxMap<A>(names: Iterable<String>, lookup: (String) -> A) : java.util.AbstractMap<String, A>(), ImmutableOrderedSyntaxMap {
    private val cells = names.toSortedSet(UnicodeTextOrder).associateWith { name -> lazy { lookup(name) } }
    internal val initializedValues get() = cells.values.count { it.isInitialized() }
    override val size get() = cells.size
    override fun containsKey(key: String) = key in cells
    override fun get(key: String): A? = cells[key]?.value
    override val entries: MutableSet<MutableMap.MutableEntry<String, A>> = Collections.unmodifiableSet(object : java.util.AbstractSet<MutableMap.MutableEntry<String, A>>() {
        override val size get() = cells.size
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, A>> {
            val iterator = cells.entries.iterator()
            return object : MutableIterator<MutableMap.MutableEntry<String, A>> {
                override fun hasNext() = iterator.hasNext()
                override fun remove(): Nothing = throw UnsupportedOperationException()
                override fun next(): MutableMap.MutableEntry<String, A> {
                    val cell = iterator.next()
                    return object : MutableMap.MutableEntry<String, A> {
                        override val key get() = cell.key
                        override val value get() = cell.value.value
                        override fun setValue(newValue: A): Nothing = throw UnsupportedOperationException()
                        override fun equals(other: Any?) = other is Map.Entry<*, *> && key == other.key && value == other.value
                        override fun hashCode() = key.hashCode() xor (value?.hashCode() ?: 0)
                    }
                }
            }
        }
    })
    override fun clear(): Nothing = throw UnsupportedOperationException()
}
