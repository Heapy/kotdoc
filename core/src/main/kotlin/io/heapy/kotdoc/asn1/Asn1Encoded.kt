// Data.ASN1.Prim / BinaryEncoding.encodeToRaw, crypton-asn1-encoding 0.10.0.
// Vincent Hanquez 2010-2013; licenses/crypton-asn1-encoding-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

/** The encoded length and event stream can be demanded independently. */
class Asn1Encoded private constructor(private val prim: Asn1Primitives, private val roots: List<Node>) {
    private class Node(val value: Asn1Value, val children: MutableList<Node>? = null) {
        var length: Long? = null
        var header: Asn1Header? = null
        var bytes: ByteArray? = null
    }
    private class Frame(val node: Node, var index: Int, var sum: Long = 0)
    @Synchronized private fun data(node: Node): ByteArray {
        node.bytes?.let { return it }
        return prim.primitiveData(node.value).also { node.bytes = it }
    }
    @Synchronized private fun size(node: Node): Long {
        node.length?.let { return it }
        val stack = ArrayDeque<Frame>(); stack.addLast(Frame(node,node.children?.lastIndex ?: -1))
        while (stack.isNotEmpty()) {
            val frame = stack.last(); val current = frame.node
            if (current.length != null) { stack.removeLast(); continue }
            val children = current.children
            if (children != null && frame.index >= 0) {
                val child = children[frame.index]
                val length = child.length
                if (length == null) { stack.addLast(Frame(child,child.children?.lastIndex ?: -1)); continue }
                frame.sum += length; frame.index--; continue
            }
            val contentSize = if (children == null) {
                data(current).size.toLong()
            } else frame.sum
            val header = prim.encodeHeader(children != null,Asn1Primitives.smallestLength(contentSize),current.value)
            current.header = header; current.length = Asn1Raw.putHeader(header).size+contentSize
            stack.removeLast()
        }
        return checkNotNull(node.length)
    }
    val length: Long get() {
        var sum = 0L
        // encodeList evaluates its suffix length before the current element.
        for (i in roots.indices.reversed()) sum += size(roots[i])
        return sum
    }
    val events: Sequence<Asn1Event> get() = lazyEvents.map { it.value }
    /** List cells and their event values are independently lazy upstream. */
    val lazyEvents: Sequence<Lazy<Asn1Event>> get() = sequence {
        data class Cursor(val nodes: List<Node>, var index: Int, val closing: Boolean)
        val stack = ArrayDeque<Cursor>(); stack.addLast(Cursor(roots,0,false))
        while (stack.isNotEmpty()) {
            val cursor = stack.last()
            if (cursor.index == cursor.nodes.size) {
                stack.removeLast(); if (cursor.closing) yield(lazyOf(Asn1Event.ConstructionEnd)); continue
            }
            val node = cursor.nodes[cursor.index++]
            yield(lazyOf(Asn1Event.Header.deferred { size(node); checkNotNull(node.header) }))
            val children = node.children
            if (children == null) yield(lazy { Asn1Event.Primitive(data(node)) })
            else { yield(lazyOf(Asn1Event.ConstructionBegin)); stack.addLast(Cursor(children,0,true)) }
        }
    }
    companion object {
        private fun nodes(values: List<Asn1Value>, preserveTopEnd: Boolean): List<Node> {
            val roots = mutableListOf<Node>(); var current = roots; val parents = ArrayDeque<MutableList<Node>>()
            for (value in values) when (value) {
                is Asn1Value.Start -> { val node = Node(value,mutableListOf()); current.add(node); parents.addLast(current); current = node.children!! }
                is Asn1Value.End -> if (parents.isNotEmpty()) current = parents.removeLast() else if (preserveTopEnd) current.add(Node(value))
                else -> current.add(Node(value))
            }
            return roots
        }
        internal fun primitive(prim: Asn1Primitives, value: Asn1Value) = Asn1Encoded(prim,listOf(Node(value)))
        internal fun list(prim: Asn1Primitives, values: List<Asn1Value>, preserveTopEnd: Boolean) = Asn1Encoded(prim,nodes(values,preserveTopEnd))
        internal fun constructed(prim: Asn1Primitives, value: Asn1Value.Start, values: List<Asn1Value>) = Asn1Encoded(prim,listOf(Node(value,nodes(values,false).toMutableList())))
    }
}
