// Codec.Picture.Jpg.Internal.DefaultTable: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import java.util.concurrent.CancellationException

internal enum class DctComponent { DC, AC }

internal sealed interface HuffmanTree {
    data class Branch(val left: HuffmanTree, val right: HuffmanTree) : HuffmanTree
    data class Leaf(val value: Int) : HuffmanTree {
        init { require(value in 0..255) }
    }
    data object Empty : HuffmanTree
}

internal object JpegHuffman {
    fun build(table: List<List<Int>>): HuffmanTree {
        fun full(tree: HuffmanTree): Boolean = when (tree) {
            HuffmanTree.Empty -> false
            is HuffmanTree.Leaf -> true
            is HuffmanTree.Branch -> full(tree.left) && full(tree.right)
        }
        fun insert(tree: HuffmanTree, depth: Int, value: Int): HuffmanTree = when (tree) {
            HuffmanTree.Empty -> if (depth == 0) HuffmanTree.Leaf(value)
                else HuffmanTree.Branch(insert(HuffmanTree.Empty, depth - 1, value), HuffmanTree.Empty)
            is HuffmanTree.Leaf -> error("Inserting in value, shouldn't happen")
            is HuffmanTree.Branch -> if (full(tree.left))
                HuffmanTree.Branch(tree.left, insert(tree.right, depth - 1, value))
                else HuffmanTree.Branch(insert(tree.left, depth - 1, value), tree.right)
        }
        var tree: HuffmanTree = HuffmanTree.Empty
        table.forEachIndexed { index, row -> row.forEach { tree = insert(tree, index + 1, it) } }
        return tree
    }

    fun pack(tree: HuffmanTree): IntArray {
        val table = IntArray(512) { 0x8000 }
        fun packAt(node: HuffmanTree, index: Int): Int = when (node) {
            HuffmanTree.Empty -> index + 1
            is HuffmanTree.Leaf -> { table[index] = 0x4000 or node.value; index + 1 }
            is HuffmanTree.Branch -> when {
                node.left is HuffmanTree.Leaf && node.right is HuffmanTree.Leaf ->
                    packAt(node.right, packAt(node.left, index))
                node.left is HuffmanTree.Leaf -> {
                    packAt(node.left, index)
                    val next = packAt(node.right, index + 2)
                    table[index + 1] = (index + 2) and 0xffff
                    next
                }
                node.right is HuffmanTree.Leaf -> {
                    val next = packAt(node.left, index + 2)
                    packAt(node.right, index + 1)
                    table[index] = (index + 2) and 0xffff
                    next
                }
                else -> {
                    val nextLeft = packAt(node.left, index + 2)
                    val nextRight = packAt(node.right, nextLeft)
                    table[index] = (index + 2) and 0xffff
                    table[index + 1] = nextLeft and 0xffff
                    nextRight
                }
            }
        }
        packAt(tree, 0)
        return table
    }

    data class WriterCode(val depth: Int, val code: Int)

    fun inverse(tree: HuffmanTree): List<WriterCode> {
        // The original has 255 entries, so a leaf with symbol 255 fails.
        val codes = MutableList(255) { WriterCode(0, 0) }
        fun visit(node: HuffmanTree, depth: Int, code: Int) {
            when (node) {
                HuffmanTree.Empty -> Unit
                is HuffmanTree.Leaf -> codes[node.value] = WriterCode(depth, code)
                is HuffmanTree.Branch -> {
                    val nextDepth = (depth + 1) and 255
                    val shifted = (code shl 1) and 0xffff
                    visit(node.left, nextDepth, shifted)
                    visit(node.right, nextDepth, shifted or 1)
                }
            }
        }
        visit(tree, 0, 0)
        return codes
    }

    fun decode(table: IntArray, reader: ImageBitReader): Int {
        var index = 0
        while (true) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("JPEG Huffman decoding interrupted")
            val value = table[index + if (reader.readJpegBit()) 1 else 0] and 0xffff
            if (value and 0x8000 != 0) return 0
            if (value and 0x4000 != 0) return value and 255
            index = value
        }
    }

    // fromListN 64 truncates excess values and does not pad short inputs.
    fun <T> macroBlock(values: List<T>): List<T> = values.take(64)

    fun scaleQuantisation(quality: Long, table: ShortArray): ShortArray {
        val q = quality.coerceAtLeast(0)
        val coefficient = when {
            q == 0L -> 10000L
            q < 50L -> 5000L / q
            else -> 200L - q * 2L
        }
        // Haskell Int is signed 64-bit; multiplication wraps before floor div.
        return ShortArray(table.size) { i ->
            Math.floorDiv(table[i].toLong() * coefficient, 100L).coerceIn(1L, 255L).toShort()
        }
    }
}
