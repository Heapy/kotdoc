// Data.Map/Set.Internal 0.7: fromDistinctAscList, link and weight balancing.
// Copyright containers contributors. BSD-3-Clause; licenses/Containers-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import java.util.Collections

/** Binary does not validate ascending/distinct input. Keep its original tree and iteration. */
private class AscTree(val index: Int, val left: AscTree? = null, val right: AscTree? = null) {
    val size: Int = 1 + (left?.size ?: 0) + (right?.size ?: 0)
    companion object {
        private fun size(t: AscTree?) = t?.size ?: 0
        private fun balance(index: Int, l: AscTree?, r: AscTree?): AscTree {
            val sl = size(l); val sr = size(r)
            if (sl + sr <= 1) return AscTree(index, l, r)
            if (sl > 3 * sr) {
                val a = checkNotNull(l)
                return if (size(a.right) < 2 * size(a.left)) AscTree(a.index, a.left, AscTree(index, a.right, r))
                else { val b = checkNotNull(a.right); AscTree(b.index, AscTree(a.index, a.left, b.left), AscTree(index, b.right, r)) }
            }
            if (sr > 3 * sl) {
                val a = checkNotNull(r)
                return if (size(a.left) < 2 * size(a.right)) AscTree(a.index, AscTree(index, l, a.left), a.right)
                else { val b = checkNotNull(a.left); AscTree(b.index, AscTree(index, l, b.left), AscTree(a.index, b.right, a.right)) }
            }
            return AscTree(index, l, r)
        }
        private fun insertMin(i: Int, t: AscTree?): AscTree = if (t == null) AscTree(i) else balance(t.index, insertMin(i, t.left), t.right)
        private fun insertMax(i: Int, t: AscTree?): AscTree = if (t == null) AscTree(i) else balance(t.index, t.left, insertMax(i, t.right))
        private fun link(i: Int, l: AscTree?, r: AscTree?): AscTree = when {
            l == null -> insertMin(i, r)
            r == null -> insertMax(i, l)
            3 * l.size < r.size -> balance(r.index, link(i, l, r.left), r.right)
            3 * r.size < l.size -> balance(l.index, l.left, link(i, l.right, r))
            else -> AscTree(i, l, r)
        }
        fun build(count: Int): AscTree? {
            val stack = ArrayDeque<Pair<Int, AscTree>>()
            var current: AscTree? = null
            for (i in 0..<count) {
                val previous = current
                if (previous != null) { stack.addLast(i to previous); current = null }
                else {
                    var tree = AscTree(i)
                    while (stack.isNotEmpty() && stack.last().second.size == tree.size) {
                        val (root, left) = stack.removeLast(); tree = AscTree(root, left, tree)
                    }
                    current = tree
                }
            }
            while (stack.isNotEmpty()) { val (root, left) = stack.removeLast(); current = link(root, left, current) }
            return current
        }
    }
    fun search(compare: (Int) -> Int): Int? {
        var node: AscTree? = this
        while (node != null) {
            val order = compare(node.index)
            if (order == 0) return node.index
            node = if (order < 0) node.left else node.right
        }
        return null
    }
}

internal class BinaryAscSet<A>(values: Iterable<A>, private val comparator: Comparator<A>) : java.util.AbstractSet<A>() {
    private val values = Collections.unmodifiableList(values.toList())
    private val tree = AscTree.build(this.values.size)
    override val size get() = values.size
    override fun iterator(): MutableIterator<A> = values.iterator()
    override fun contains(element: A) = tree?.search { comparator.compare(element, values[it]) } != null
    override fun equals(other: Any?) = other is Set<*> && size == other.size && values == other.toList()
    override fun hashCode() = values.sumOf { it?.hashCode() ?: 0 }
}

internal class BinaryAscMap<K, V>(pairs: Iterable<Pair<K, V>>, private val comparator: Comparator<K>) : java.util.AbstractMap<K, V>() {
    private val pairs = Collections.unmodifiableList(pairs.toList())
    private val tree = AscTree.build(this.pairs.size)
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = Collections.unmodifiableSet(object : java.util.AbstractSet<MutableMap.MutableEntry<K, V>>() {
        private val rows = Collections.unmodifiableList(this@BinaryAscMap.pairs.map { SimpleImmutableEntry(it.first, it.second) as MutableMap.MutableEntry<K, V> })
        override val size get() = rows.size
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = rows.iterator()
    })
    override fun get(key: K): V? = tree?.search { comparator.compare(key, pairs[it].first) }?.let { pairs[it].second }
    override fun containsKey(key: K) = tree?.search { comparator.compare(key, pairs[it].first) } != null
    @Suppress("UNCHECKED_CAST")
    override fun equals(other: Any?): Boolean {
        if (other !is Map<*, *> || size != other.size) return false
        val entries = other.entries.map { (it.key as K) to it.value }
        val ordered = if (other is BinaryAscMap<*, *>) entries else entries.sortedWith { a, b -> comparator.compare(a.first, b.first) }
        return pairs == ordered
    }
    override fun hashCode() = pairs.sumOf { (it.first?.hashCode() ?: 0) xor (it.second?.hashCode() ?: 0) }
}
