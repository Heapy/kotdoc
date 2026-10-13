// Codec.Picture.Jpg.Internal.Common and FastIdct: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import java.util.concurrent.CancellationException

internal data class JpgUnpackerParameter(
    val dcHuffmanTree: IntArray,
    val acHuffmanTree: IntArray,
    val componentIndex: Long,
    val restartInterval: Long,
    val componentWidth: Long,
    val componentHeight: Long,
    val subSampling: Pair<Long, Long>,
    val coefficientRange: Pair<Long, Long>,
    val successiveApprox: Pair<Long, Long>,
    val readerIndex: Long,
    val indiceVector: Long,
    val blockIndex: Long,
    val blockMcuX: Long,
    val blockMcuY: Long,
) {
    override fun toString(): String {
        fun Pair<Long, Long>.show() = "($first,$second)"
        fun IntArray.show() = joinToString(",", "[", "]") { (it and 0xffff).toString() }
        return "JpgUnpackerParameter {dcHuffmanTree = ${dcHuffmanTree.show()}, " +
            "acHuffmanTree = ${acHuffmanTree.show()}, componentIndex = $componentIndex, " +
            "restartInterval = $restartInterval, componentWidth = $componentWidth, componentHeight = $componentHeight, " +
            "subSampling = ${subSampling.show()}, coefficientRange = ${coefficientRange.show()}, " +
            "successiveApprox = ${successiveApprox.show()}, readerIndex = $readerIndex, indiceVector = $indiceVector, " +
            "blockIndex = $blockIndex, blockMcuX = $blockMcuX, blockMcuY = $blockMcuY}"
    }
}

internal object JpegBlocks {
    fun empty(): ShortArray = ShortArray(64)
    fun toBlockSize(value: Long): Long = Math.floorDiv(value + 7L, 8L)
    fun decodeRestartInterval(): Int = -1
    fun unpackInt(reader: ImageBitReader, count: Int): Int = reader.readJpegInt(count)

    fun decodeInt(reader: ImageBitReader, count: Int): Int {
        require(count > 0)
        val sign = reader.readJpegBit()
        val range = (1L shl (count - 1)).toInt()
        val rest = unpackInt(reader, count - 1)
        return if (sign) range + rest else 1 - range * 2 + rest
    }

    fun dcCoefficientDecode(tree: IntArray, reader: ImageBitReader): Short {
        val count = JpegHuffman.decode(tree, reader)
        return if (count == 0) 0 else decodeInt(reader, count).toShort()
    }

    fun decodeRrrrSsss(tree: IntArray, reader: ImageBitReader): Pair<Int, Int> {
        val value = JpegHuffman.decode(tree, reader)
        return (value ushr 4 and 15) to (value and 15)
    }

    fun deQuantize(table: ShortArray, block: ShortArray): ShortArray {
        repeat(64) { block[it] = (block[it] * table[it]).toShort() }
        return block
    }

    private val zigZag = intArrayOf(
        0, 1, 5, 6, 14, 15, 27, 28,
        2, 4, 7, 13, 16, 26, 29, 42,
        3, 8, 12, 17, 25, 30, 41, 43,
        9, 11, 18, 24, 31, 40, 44, 53,
        10, 19, 23, 32, 39, 45, 52, 54,
        20, 22, 33, 38, 46, 51, 55, 60,
        21, 34, 37, 47, 50, 56, 59, 61,
        35, 36, 48, 49, 57, 58, 62, 63,
    )
    private val forward = IntArray(64) { zigZag.indexOf(it) }

    fun zigZagReorder(destination: ShortArray, block: ShortArray): ShortArray {
        repeat(64) { destination[it] = block[zigZag[it]] }
        return destination
    }
    fun zigZagForward(destination: ShortArray, block: ShortArray): ShortArray {
        repeat(64) { destination[it] = block[forward[it]] }
        return destination
    }
    fun zigZagForward(destination: IntArray, block: IntArray): IntArray {
        repeat(64) { destination[it] = block[forward[it]] }
        return destination
    }
    fun zigZagForward(destination: ByteArray, block: ByteArray): ByteArray {
        repeat(64) { destination[it] = block[forward[it]] }
        return destination
    }
    fun <T> zigZagForward(destination: MutableList<T>, block: List<T>): MutableList<T> {
        repeat(64) { destination[it] = block[forward[it]] }
        return destination
    }
    fun <T> zigZagForwardValues(block: List<T>): List<T> = List(64) { block[forward[it]] }

    fun inverseDct(block: ShortArray): ShortArray = levelShift(fastIdct(block))
    fun decodeMacroBlock(table: ShortArray, destination: ShortArray, block: ShortArray): ShortArray =
        inverseDct(zigZagReorder(destination, deQuantize(table, block)))

    fun levelShift(block: ShortArray): ShortArray {
        repeat(64) { block[it] = (block[it] + 128).toShort() }
        return block
    }

    // Haskell's IDctStage uses machine Int (64 bits on the pinned reference).
    // Each record update reads the previous stage; row writes narrow to Int16.
    private data class Stage(
        val x0: Long, val x1: Long, val x2: Long, val x3: Long,
        val x4: Long, val x5: Long, val x6: Long, val x7: Long, val x8: Long = 0,
    )
    private fun first(c: Stage, column: Boolean): Stage {
        val shift = if (column) 3 else 0
        val bias = if (column) 4L else 0L
        val a = 565L * (c.x4 + c.x5) + bias
        val b = 2408L * (c.x6 + c.x7) + bias
        return c.copy(
            x4 = (a + (2841L - 565L) * c.x4) shr shift,
            x5 = (a - (2841L + 565L) * c.x5) shr shift,
            x6 = (b - (2408L - 1609L) * c.x6) shr shift,
            x7 = (b - (2408L + 1609L) * c.x7) shr shift,
            x8 = b,
        )
    }
    private fun second(c: Stage, column: Boolean): Stage {
        val shift = if (column) 3 else 0
        val a = 1108L * (c.x3 + c.x2) + if (column) 4L else 0L
        return c.copy(
            x0 = c.x0 - c.x1, x8 = c.x0 + c.x1, x1 = c.x4 + c.x6,
            x2 = (a - (2676L + 1108L) * c.x2) shr shift,
            x3 = (a + (2676L - 1108L) * c.x3) shr shift,
            x4 = c.x4 - c.x6, x6 = c.x5 + c.x7, x5 = c.x5 - c.x7,
        )
    }
    private fun third(c: Stage): Stage = c.copy(
        x7 = c.x8 + c.x3, x8 = c.x8 - c.x3, x3 = c.x0 + c.x2, x0 = c.x0 - c.x2,
        x2 = (181L * (c.x4 + c.x5) + 128L) shr 8,
        x4 = (181L * (c.x4 - c.x5) + 128L) shr 8,
    )
    private fun transform(block: ShortArray, offset: Int, column: Boolean) {
        val step = if (column) 8 else 1
        fun at(i: Int): Long = block[offset + i * step].toLong()
        val shift = if (column) 8 else 11
        val initial = Stage((at(0) shl shift) + if (column) 8192L else 128L,
            at(4) shl shift, at(6), at(2), at(1), at(7), at(5), at(3))
        val c = third(second(first(initial, column), column))
        val values = longArrayOf(c.x7 + c.x1, c.x3 + c.x2, c.x0 + c.x4, c.x8 + c.x6,
            c.x8 - c.x6, c.x0 - c.x4, c.x3 - c.x2, c.x7 - c.x1)
        for (i in 0..<8) {
            val value = values[i] shr if (column) 14 else 8
            block[offset + i * step] = (if (column) value.coerceIn(-256L, 255L) else value).toShort()
        }
    }
    fun fastIdct(block: ShortArray): ShortArray {
        repeat(8) { transform(block, it * 8, column = false) }
        repeat(8) { transform(block, it, column = true) }
        return block
    }

    fun rasterMap(width: Long, height: Long, action: (Long, Long) -> Unit) {
        var y = 0L
        while (y < height) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("JPEG block traversal interrupted")
            var x = 0L
            while (x < width) { action(x, y); x++ }
            y++
        }
    }

    fun unpackMacroBlock(
        componentCount: Long, widthCoefficient: Long, heightCoefficient: Long, componentIndex: Long,
        x: Long, y: Long, imageWidth: Long, imageHeight: Long, image: ByteArray, block: ShortArray,
    ) {
        rasterMap(8, 8) { i, j ->
            val yBase = y * 8 + j * heightCoefficient
            val value = block[(i + j * 8).toInt()].toInt().coerceIn(0, 255).toByte()
            rasterMap(widthCoefficient, heightCoefficient) { w, h ->
                val xPosition = x * 8 + i * widthCoefficient + w
                val yPosition = yBase + h
                if (xPosition < imageWidth && yPosition < imageHeight) {
                    val index = (xPosition + yPosition * imageWidth) * componentCount + componentIndex
                    if (index < 0 || index >= image.size.toLong()) throw IndexOutOfBoundsException("JPEG pixel index $index")
                    image[index.toInt()] = value
                }
            }
        }
    }
}
