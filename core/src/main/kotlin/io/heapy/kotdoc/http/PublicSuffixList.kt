// Network.PublicSuffixList.{Types,Lookup,Serialize}, bundled in http-client-0.7.19.
// Copyright (c) 2013 Michael Snoyman; MIT.
// Full original notice: licenses/http-client-MIT.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.layout.TextChars
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.TreeMap

/** An immutable snapshot of the original Text-keyed Map, including non-leaf rules. */
class PublicSuffixTree(children: Map<String, PublicSuffixTree> = emptyMap()) {
    val children: Map<String, PublicSuffixTree> = Collections.unmodifiableMap(
        TreeMap<String, PublicSuffixTree>(::compareSuffixText).apply {
            children.forEach { (key, value) -> put(TextChars.fromString(key), value) }
        },
    )
    override fun equals(other: Any?) = other is PublicSuffixTree && children == other.children
    override fun hashCode() = children.hashCode()
}

data class PublicSuffixData(val rules: PublicSuffixTree, val exceptions: PublicSuffixTree)

/** The pinned upstream list is deliberately not replaced by the host's current PSL. */
object PublicSuffixList {
    val dataStructure: PublicSuffixData by lazy {
        val path = "/io/heapy/kotdoc/http/public-suffix-list.bin"
        getDataStructure(checkNotNull(javaClass.getResourceAsStream(path)) { "Missing $path" }.use { it.readBytes() })
    }

    fun effectiveTLDPlusOne(domain: String): String? = effectiveTLDPlusOne(dataStructure, domain)
    fun isSuffix(domain: String): Boolean = effectiveTLDPlusOne(domain) == null
    fun isSuffix(data: PublicSuffixData, domain: String): Boolean = effectiveTLDPlusOne(data, domain) == null

    /** As upstream, expects lowercase ASCII/IDNA input; does not normalize it. */
    fun effectiveTLDPlusOne(data: PublicSuffixData, domain: String): String? {
        val text = TextChars.fromString(domain)
        val labels = text.split('.')
        if (labels.size == 1) return null
        val reversed = labels.asReversed()
        val exception = recurse(data.exceptions, reversed)
        val rule = recurse(data.rules, reversed)
        return when {
            exception === LookupResult.AtLeaf -> text
            exception is LookupResult.OffEnd && exception.leaf -> exception.text.split('.').drop(1).joinToString(".")
            rule is LookupResult.OffEnd -> if ('.' in rule.text) rule.text else labels.takeLast(2).joinToString(".")
            else -> null
        }
    }

    private sealed interface LookupResult {
        data object Inside : LookupResult
        data object AtLeaf : LookupResult
        data class OffEnd(val leaf: Boolean, val text: String) : LookupResult
    }

    private fun recurse(initial: PublicSuffixTree, labels: List<String>): LookupResult {
        var tree = initial
        val previous = ArrayDeque<String>()
        for (label in labels) {
            val next = tree.children[label] ?: tree.children["*"]
            previous.addFirst(label)
            if (next == null) return LookupResult.OffEnd(tree.children.isEmpty(), previous.joinToString("."))
            tree = next
        }
        return if (tree.children.isEmpty()) LookupResult.AtLeaf else LookupResult.Inside
    }

    /** Two NUL-delimited trees. EOF implicitly closes every open node; trailing bytes are ignored. */
    fun getDataStructure(bytes: ByteArray): PublicSuffixData {
        var at = 0
        fun tree(): PublicSuffixTree {
            val children = linkedMapOf<String, PublicSuffixTree>()
            while (at < bytes.size && bytes[at] != 0.toByte()) {
                val start = at
                while (at < bytes.size && bytes[at] != 0.toByte()) at++
                val text = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, at - start)).toString()
                if (at < bytes.size) at++
                children[text] = tree()
            }
            if (at < bytes.size) at++
            return PublicSuffixTree(children)
        }
        return PublicSuffixData(tree(), tree())
    }

    fun putDataStructure(data: PublicSuffixData): ByteArray {
        val output = ByteArrayOutputStream()
        fun tree(node: PublicSuffixTree) {
            for ((key, value) in node.children) {
                output.write(key.toByteArray(Charsets.UTF_8))
                output.write(0)
                tree(value)
            }
            output.write(0)
        }
        tree(data.rules)
        tree(data.exceptions)
        return output.toByteArray()
    }
}

private fun compareSuffixText(a: String, b: String): Int {
    var x = 0
    var y = 0
    while (x < a.length && y < b.length) {
        val ac = a.codePointAt(x)
        val bc = b.codePointAt(y)
        if (ac != bc) return ac.compareTo(bc)
        x += Character.charCount(ac)
        y += Character.charCount(bc)
    }
    return (a.length - x).compareTo(b.length - y)
}
