// containers 0.7 Data.Map.Internal insert/delete/union and weight balancing.
// BSD-3-Clause; original source and complete notice are retained in vendor/ and licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.ast.UnicodeTextOrder

/** Immutable source weight-balanced tree. A saved root is an O(1) snapshot. */
internal class PersistentTextMap<V> private constructor(internal val root: Node<V>?) : Iterable<Pair<String, V>> {
    internal class Node<V>(val key: String, val value: V, val left: Node<V>?, val right: Node<V>?) {
        val size: Long = 1 + size(left) + size(right)
    }
    val size get() = size(root)
    operator fun get(key: String): V? {
        var node = root
        while (node != null) {
            val order = UnicodeTextOrder.compare(key, node.key)
            if (order == 0) return node.value
            node = if (order < 0) node.left else node.right
        }
        return null
    }
    fun insert(key: String, value: V) = PersistentTextMap(insert(key, value, root, false))
    fun delete(key: String) = PersistentTextMap(delete(key, root))
    operator fun plus(other: PersistentTextMap<V>) = PersistentTextMap(union(root, other.root))
    override fun iterator(): Iterator<Pair<String, V>> = sequence {
        val stack = ArrayDeque<Node<V>>(); var node = root
        while (node != null || stack.isNotEmpty()) {
            while (node != null) { stack.addLast(node); node = node.left }
            val next = stack.removeLast(); yield(next.key to next.value); node = next.right
        }
    }.iterator()
    companion object {
        fun <V> empty() = PersistentTextMap<V>(null)
        private fun size(n: Node<*>?) = n?.size ?: 0L
        private fun <V> bin(k: String, v: V, l: Node<V>?, r: Node<V>?) = Node(k, v, l, r)
        private fun <V> insert(k: String, v: V, t: Node<V>?, retain: Boolean): Node<V> {
            if (t == null) return bin(k, v, null, null)
            val order = UnicodeTextOrder.compare(k, t.key)
            return when {
                order < 0 -> {
                    val l = insert(k, v, t.left, retain)
                    if (l === t.left) t else balanceL(t.key, t.value, l, t.right)
                }
                order > 0 -> {
                    val r = insert(k, v, t.right, retain)
                    if (r === t.right) t else balanceR(t.key, t.value, t.left, r)
                }
                retain || (k === t.key && v === t.value) -> t
                else -> bin(k, v, t.left, t.right)
            }
        }
        private fun <V> delete(k: String, t: Node<V>?): Node<V>? {
            if (t == null) return null
            val order = UnicodeTextOrder.compare(k, t.key)
            return when {
                order < 0 -> {
                    val l = delete(k, t.left)
                    if (l === t.left) t else balanceR(t.key, t.value, l, t.right)
                }
                order > 0 -> {
                    val r = delete(k, t.right)
                    if (r === t.right) t else balanceL(t.key, t.value, t.left, r)
                }
                else -> glue(t.left, t.right)
            }
        }
        private fun <V> union(a: Node<V>?, b: Node<V>?): Node<V>? {
            if (b == null) return a
            if (b.left == null && b.right == null) return insert(b.key, b.value, a, true)
            if (a != null && a.left == null && a.right == null) return insert(a.key, a.value, b, false)
            if (a == null) return b
            val (bl, br) = split(a.key, b)
            val l = union(a.left, bl); val r = union(a.right, br)
            return if (l === a.left && r === a.right) a else link(a.key, a.value, l, r)
        }
        private fun <V> split(k: String, t: Node<V>?): Pair<Node<V>?, Node<V>?> {
            if (t == null) return null to null
            val order = UnicodeTextOrder.compare(k, t.key)
            return when {
                order < 0 -> split(k, t.left).let { (l, r) -> l to link(t.key, t.value, r, t.right) }
                order > 0 -> split(k, t.right).let { (l, r) -> link(t.key, t.value, t.left, l) to r }
                else -> t.left to t.right
            }
        }
        private fun <V> insertMin(k: String, v: V, t: Node<V>?): Node<V> =
            if (t == null) bin(k, v, null, null) else balanceL(t.key, t.value, insertMin(k, v, t.left), t.right)
        private fun <V> insertMax(k: String, v: V, t: Node<V>?): Node<V> =
            if (t == null) bin(k, v, null, null) else balanceR(t.key, t.value, t.left, insertMax(k, v, t.right))
        private fun <V> link(k: String, v: V, l: Node<V>?, r: Node<V>?): Node<V> = when {
            l == null -> insertMin(k, v, r)
            r == null -> insertMax(k, v, l)
            3 * l.size < r.size -> balanceL(r.key, r.value, link(k, v, l, r.left), r.right)
            3 * r.size < l.size -> balanceR(l.key, l.value, l.left, link(k, v, l.right, r))
            else -> bin(k, v, l, r)
        }
        private fun <V> glue(l: Node<V>?, r: Node<V>?): Node<V>? = when {
            l == null -> r
            r == null -> l
            l.size > r.size -> maxView(l).let { (n, rest) -> balanceR(n.key, n.value, rest, r) }
            else -> minView(r).let { (n, rest) -> balanceL(n.key, n.value, l, rest) }
        }
        private fun <V> minView(t: Node<V>): Pair<Node<V>, Node<V>?> = if (t.left == null) t to t.right else
            minView(t.left).let { (n, rest) -> n to balanceR(t.key, t.value, rest, t.right) }
        private fun <V> maxView(t: Node<V>): Pair<Node<V>, Node<V>?> = if (t.right == null) t to t.left else
            maxView(t.right).let { (n, rest) -> n to balanceL(t.key, t.value, t.left, rest) }
        private fun <V> balanceL(k: String, v: V, l: Node<V>?, r: Node<V>?): Node<V> {
            if (r == null) return when {
                l == null -> bin(k, v, null, null)
                l.left == null && l.right == null -> bin(k, v, l, null)
                l.left == null -> l.right!!.let { lr -> bin(lr.key, lr.value, bin(l.key, l.value, null, null), bin(k, v, null, null)) }
                l.right == null -> bin(l.key, l.value, l.left, bin(k, v, null, null))
                l.right.size < 2 * l.left.size -> bin(l.key, l.value, l.left, bin(k, v, l.right, null))
                else -> l.right.let { lr -> bin(lr.key, lr.value, bin(l.key, l.value, l.left, lr.left), bin(k, v, lr.right, null)) }
            }
            if (l == null || l.size <= 3 * r.size) return bin(k, v, l, r)
            val ll = l.left ?: error("Failure in Data.Map.balanceL")
            val lr = l.right ?: error("Failure in Data.Map.balanceL")
            return if (lr.size < 2 * ll.size) bin(l.key, l.value, ll, bin(k, v, lr, r))
            else bin(lr.key, lr.value, bin(l.key, l.value, ll, lr.left), bin(k, v, lr.right, r))
        }
        private fun <V> balanceR(k: String, v: V, l: Node<V>?, r: Node<V>?): Node<V> {
            if (l == null) return when {
                r == null -> bin(k, v, null, null)
                r.left == null && r.right == null -> bin(k, v, null, r)
                r.left == null -> bin(r.key, r.value, bin(k, v, null, null), r.right)
                r.right == null -> r.left.let { rl -> bin(rl.key, rl.value, bin(k, v, null, null), bin(r.key, r.value, null, null)) }
                r.left.size < 2 * r.right.size -> bin(r.key, r.value, bin(k, v, null, r.left), r.right)
                else -> r.left.let { rl -> bin(rl.key, rl.value, bin(k, v, null, rl.left), bin(r.key, r.value, rl.right, r.right)) }
            }
            if (r == null || r.size <= 3 * l.size) return bin(k, v, l, r)
            val rl = r.left ?: error("Failure in Data.Map.balanceR")
            val rr = r.right ?: error("Failure in Data.Map.balanceR")
            return if (rl.size < 2 * rr.size) bin(r.key, r.value, bin(k, v, l, rl), rr)
            else bin(rl.key, rl.value, bin(k, v, l, rl.left), bin(r.key, r.value, rl.right, rr))
        }
    }
}
