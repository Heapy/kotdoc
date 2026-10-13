// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import java.nio.ByteOrder

/** A component slice of existing storage, without copying it. */
internal data class ImageBufferView(val offset: Long, val length: Long)

internal object ImagePacked {
    private val littleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
    private fun slice(start: Long, count: Long, length: Long) {
        if (start < 0 || count < 0 || start > length - count) throw ImagePixelException("invalid slice ($start,$count,$length)")
    }
    private fun checked(view: ImageBufferView, capacity: Int): ImageBufferView {
        slice(view.offset, view.length, capacity.toLong())
        return view
    }
    private fun word(start: Long, components: Int, bits: Int, read: (Int) -> ULong): ULong {
        var value = 0uL
        for (c in 0..<components) {
            val shift = (if (littleEndian) c else components - 1 - c) * bits
            value = value or (read(Math.toIntExact(start + c)) shl shift)
        }
        return value
    }
    private fun put(start: Long, components: Int, bits: Int, value: ULong, write: (Int, ULong) -> Unit) {
        for (c in 0..<components) {
            val shift = (if (littleEndian) c else components - 1 - c) * bits
            write(Math.toIntExact(start + c), value shr shift)
        }
    }
    private fun fill(view: ImageBufferView, components: Int, bits: Int, value: ULong, write: (Int, ULong) -> Unit) {
        for (i in 0L..<(view.length / components)) put(view.offset + i * components, components, bits, value, write)
    }
    private fun between(view: ImageBufferView, start: Long, count: Long, components: Int, bits: Int, value: ULong, write: (Int, ULong) -> Unit) {
        // Original M.slice checks a component count, then the cast vector writes count packed pixels.
        slice(start, count, view.length)
        for (i in 0L..<count) put(view.offset + start + i * components, components, bits, value, write)
    }

    fun packPixel(pixel: UByte): UByte = pixel
    fun unpackPixelY8(value: UByte): UByte = value
    fun readPackedPixelAt(image: MutableImageY8, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): UByte {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        val packed = word(start, 1, 8) { image.data[it].toUByte().toULong() }
        return unpackPixelY8(packed.toUByte())
    }
    fun writePackedPixelAt(image: MutableImageY8, index: Long, pixel: UByte, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        put(start, 1, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun fillImageWith(image: MutableImageY8, pixel: UByte, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 1, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageY8, pixel: UByte, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 1, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }

    fun packPixel(pixel: UShort): UShort = pixel
    fun unpackPixelY16(value: UShort): UShort = value
    fun readPackedPixelAt(image: MutableImageY16, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): UShort {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        val packed = word(start, 1, 16) { image.data[it].toUShort().toULong() }
        return unpackPixelY16(packed.toUShort())
    }
    fun writePackedPixelAt(image: MutableImageY16, index: Long, pixel: UShort, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        put(start, 1, 16, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toShort() }
    }
    fun fillImageWith(image: MutableImageY16, pixel: UShort, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 1, 16, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toShort() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageY16, pixel: UShort, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 1, 16, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toShort() }
    }

    fun packPixel(pixel: UInt): UInt = pixel
    fun unpackPixelY32(value: UInt): UInt = value
    fun readPackedPixelAt(image: MutableImageY32, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): UInt {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        val packed = word(start, 1, 32) { image.data[it].toUInt().toULong() }
        return unpackPixelY32(packed.toUInt())
    }
    fun writePackedPixelAt(image: MutableImageY32, index: Long, pixel: UInt, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        put(start, 1, 32, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toInt() }
    }
    fun fillImageWith(image: MutableImageY32, pixel: UInt, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 1, 32, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toInt() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageY32, pixel: UInt, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 1, 32, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toInt() }
    }

    fun packPixel(pixel: Float): Float = pixel
    fun unpackPixelYF(value: Float): Float = value
    fun readPackedPixelAt(image: MutableImageYF, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): Float {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        val packed = word(start, 1, 32) { image.data[it].toRawBits().toUInt().toULong() }
        return unpackPixelYF(Float.fromBits(packed.toInt()))
    }
    fun writePackedPixelAt(image: MutableImageYF, index: Long, pixel: Float, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 1L) * 1
        put(start, 1, 32, packPixel(pixel).toRawBits().toUInt().toULong()) { at, value -> image.data[at] = Float.fromBits(value.toInt()) }
    }
    fun fillImageWith(image: MutableImageYF, pixel: Float, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 1, 32, packPixel(pixel).toRawBits().toUInt().toULong()) { at, value -> image.data[at] = Float.fromBits(value.toInt()) }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageYF, pixel: Float, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 1, 32, packPixel(pixel).toRawBits().toUInt().toULong()) { at, value -> image.data[at] = Float.fromBits(value.toInt()) }
    }

    fun packPixel(pixel: PixelYA8): UShort = (pixel.luma.toUInt() or (pixel.alpha.toUInt() shl 8)).toUShort()
    fun unpackPixelYA8(value: UShort): PixelYA8 = PixelYA8(value.toUByte(), (value.toUInt() shr 8).toUByte())
    fun readPackedPixelAt(image: MutableImageYA8, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): PixelYA8 {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 2L) * 2
        val packed = word(start, 2, 8) { image.data[it].toUByte().toULong() }
        return unpackPixelYA8(packed.toUShort())
    }
    fun writePackedPixelAt(image: MutableImageYA8, index: Long, pixel: PixelYA8, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 2L) * 2
        put(start, 2, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun fillImageWith(image: MutableImageYA8, pixel: PixelYA8, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 2, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageYA8, pixel: PixelYA8, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 2, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }

    fun packPixel(pixel: PixelYA16): UInt = (pixel.luma.toUInt() shl 0) or (pixel.alpha.toUInt() shl 16)
    fun unpackPixelYA16(value: UInt): PixelYA16 = PixelYA16((value shr 0).toUShort(), (value shr 16).toUShort())
    fun readPackedPixelAt(image: MutableImageYA16, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): PixelYA16 {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 2L) * 2
        val packed = word(start, 2, 16) { image.data[it].toUShort().toULong() }
        return unpackPixelYA16(packed.toUInt())
    }
    fun writePackedPixelAt(image: MutableImageYA16, index: Long, pixel: PixelYA16, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 2L) * 2
        put(start, 2, 16, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toShort() }
    }
    fun fillImageWith(image: MutableImageYA16, pixel: PixelYA16, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 2, 16, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toShort() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageYA16, pixel: PixelYA16, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 2, 16, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toShort() }
    }

    fun packPixel(pixel: PixelRGBA8): UInt = (pixel.red.toUInt() shl 0) or (pixel.green.toUInt() shl 8) or (pixel.blue.toUInt() shl 16) or (pixel.alpha.toUInt() shl 24)
    fun unpackPixelRGBA8(value: UInt): PixelRGBA8 = PixelRGBA8((value shr 0).toUByte(), (value shr 8).toUByte(), (value shr 16).toUByte(), (value shr 24).toUByte())
    fun readPackedPixelAt(image: MutableImageRGBA8, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): PixelRGBA8 {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        val packed = word(start, 4, 8) { image.data[it].toUByte().toULong() }
        return unpackPixelRGBA8(packed.toUInt())
    }
    fun writePackedPixelAt(image: MutableImageRGBA8, index: Long, pixel: PixelRGBA8, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        put(start, 4, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun fillImageWith(image: MutableImageRGBA8, pixel: PixelRGBA8, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 4, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageRGBA8, pixel: PixelRGBA8, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 4, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }

    fun packPixel(pixel: PixelRGBA16): ULong = (pixel.red.toULong() shl 0) or (pixel.green.toULong() shl 16) or (pixel.blue.toULong() shl 32) or (pixel.alpha.toULong() shl 48)
    fun unpackPixelRGBA16(value: ULong): PixelRGBA16 = PixelRGBA16((value shr 0).toUShort(), (value shr 16).toUShort(), (value shr 32).toUShort(), (value shr 48).toUShort())
    fun readPackedPixelAt(image: MutableImageRGBA16, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): PixelRGBA16 {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        val packed = word(start, 4, 16) { image.data[it].toUShort().toULong() }
        return unpackPixelRGBA16(packed)
    }
    fun writePackedPixelAt(image: MutableImageRGBA16, index: Long, pixel: PixelRGBA16, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        put(start, 4, 16, packPixel(pixel)) { at, value -> image.data[at] = value.toShort() }
    }
    fun fillImageWith(image: MutableImageRGBA16, pixel: PixelRGBA16, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 4, 16, packPixel(pixel)) { at, value -> image.data[at] = value.toShort() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageRGBA16, pixel: PixelRGBA16, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 4, 16, packPixel(pixel)) { at, value -> image.data[at] = value.toShort() }
    }

    fun packPixel(pixel: PixelCMYK8): UInt = (pixel.cyan.toUInt() shl 0) or (pixel.magenta.toUInt() shl 8) or (pixel.yellow.toUInt() shl 16) or (pixel.black.toUInt() shl 24)
    fun unpackPixelCMYK8(value: UInt): PixelCMYK8 = PixelCMYK8((value shr 0).toUByte(), (value shr 8).toUByte(), (value shr 16).toUByte(), (value shr 24).toUByte())
    fun readPackedPixelAt(image: MutableImageCMYK8, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): PixelCMYK8 {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        val packed = word(start, 4, 8) { image.data[it].toUByte().toULong() }
        return unpackPixelCMYK8(packed.toUInt())
    }
    fun writePackedPixelAt(image: MutableImageCMYK8, index: Long, pixel: PixelCMYK8, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        put(start, 4, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun fillImageWith(image: MutableImageCMYK8, pixel: PixelCMYK8, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 4, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageCMYK8, pixel: PixelCMYK8, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 4, 8, packPixel(pixel).toULong()) { at, value -> image.data[at] = value.toByte() }
    }

    fun packPixel(pixel: PixelCMYK16): ULong = (pixel.cyan.toULong() shl 0) or (pixel.magenta.toULong() shl 16) or (pixel.yellow.toULong() shl 32) or (pixel.black.toULong() shl 48)
    fun unpackPixelCMYK16(value: ULong): PixelCMYK16 = PixelCMYK16((value shr 0).toUShort(), (value shr 16).toUShort(), (value shr 32).toUShort(), (value shr 48).toUShort())
    fun readPackedPixelAt(image: MutableImageCMYK16, index: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())): PixelCMYK16 {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        val packed = word(start, 4, 16) { image.data[it].toUShort().toULong() }
        return unpackPixelCMYK16(packed)
    }
    fun writePackedPixelAt(image: MutableImageCMYK16, index: Long, pixel: PixelCMYK16, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        val start = view.offset + Math.floorDiv(index, 4L) * 4
        put(start, 4, 16, packPixel(pixel)) { at, value -> image.data[at] = value.toShort() }
    }
    fun fillImageWith(image: MutableImageCMYK16, pixel: PixelCMYK16, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        fill(view, 4, 16, packPixel(pixel)) { at, value -> image.data[at] = value.toShort() }
    }
    fun unsafeWritePixelBetweenAt(image: MutableImageCMYK16, pixel: PixelCMYK16, start: Long, count: Long, view: ImageBufferView = ImageBufferView(0, image.data.size.toLong())) {
        checked(view, image.data.size)
        between(view, start, count, 4, 16, packPixel(pixel)) { at, value -> image.data[at] = value.toShort() }
    }
}
