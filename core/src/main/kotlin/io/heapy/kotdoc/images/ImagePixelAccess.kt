// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal class ImagePixelException(message: String) : RuntimeException(message)

internal object ImagePixelAccess {
    fun pixelBaseIndex(width: Long, x: Long, y: Long, components: Int): Long = (x + y * width) * components
    private fun checked(index: Long, length: Int): Int {
        if (index < 0 || index >= length) throw ImagePixelException("index out of bounds ($index,$length)")
        return index.toInt()
    }

    fun pixelBaseIndex(image: ImageY8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun mutablePixelBaseIndex(image: MutableImageY8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun pixelAt(image: ImageY8, x: Long, y: Long): UByte {
        val at = pixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)].toUByte()
    }
    fun readPixel(image: MutableImageY8, x: Long, y: Long): UByte {
        val at = mutablePixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)].toUByte()
    }
    fun writePixel(image: MutableImageY8, x: Long, y: Long, pixel: UByte) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.toByte()
    }
    fun unsafePixelAtY8(data: ByteArray, index: Long): UByte = data[Math.toIntExact(index + 0)].toUByte()
    fun unsafeReadPixelY8(data: ByteArray, index: Long): UByte = unsafePixelAtY8(data, index)
    fun unsafeReadPixel(image: MutableImageY8, index: Long): UByte = unsafeReadPixelY8(image.data, index)
    fun unsafeWritePixel(data: ByteArray, index: Long, pixel: UByte) {
        data[Math.toIntExact(index + 0)] = pixel.toByte()
    }

    fun pixelBaseIndex(image: ImageY16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun mutablePixelBaseIndex(image: MutableImageY16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun pixelAt(image: ImageY16, x: Long, y: Long): UShort {
        val at = pixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)].toUShort()
    }
    fun readPixel(image: MutableImageY16, x: Long, y: Long): UShort {
        val at = mutablePixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)].toUShort()
    }
    fun writePixel(image: MutableImageY16, x: Long, y: Long, pixel: UShort) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.toShort()
    }
    fun unsafePixelAtY16(data: ShortArray, index: Long): UShort = data[Math.toIntExact(index + 0)].toUShort()
    fun unsafeReadPixelY16(data: ShortArray, index: Long): UShort = unsafePixelAtY16(data, index)
    fun unsafeReadPixel(image: MutableImageY16, index: Long): UShort = unsafeReadPixelY16(image.data, index)
    fun unsafeWritePixel(data: ShortArray, index: Long, pixel: UShort) {
        data[Math.toIntExact(index + 0)] = pixel.toShort()
    }

    fun pixelBaseIndex(image: ImageY32, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun mutablePixelBaseIndex(image: MutableImageY32, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun pixelAt(image: ImageY32, x: Long, y: Long): UInt {
        val at = pixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)].toUInt()
    }
    fun readPixel(image: MutableImageY32, x: Long, y: Long): UInt {
        val at = mutablePixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)].toUInt()
    }
    fun writePixel(image: MutableImageY32, x: Long, y: Long, pixel: UInt) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.toInt()
    }
    fun unsafePixelAtY32(data: IntArray, index: Long): UInt = data[Math.toIntExact(index + 0)].toUInt()
    fun unsafeReadPixelY32(data: IntArray, index: Long): UInt = unsafePixelAtY32(data, index)
    fun unsafeReadPixel(image: MutableImageY32, index: Long): UInt = unsafeReadPixelY32(image.data, index)
    fun unsafeWritePixel(data: IntArray, index: Long, pixel: UInt) {
        data[Math.toIntExact(index + 0)] = pixel.toInt()
    }

    fun pixelBaseIndex(image: ImageYF, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun mutablePixelBaseIndex(image: MutableImageYF, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 1)
    fun pixelAt(image: ImageYF, x: Long, y: Long): Float {
        val at = pixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)]
    }
    fun readPixel(image: MutableImageYF, x: Long, y: Long): Float {
        val at = mutablePixelBaseIndex(image, x, y)
        return image.data[checked(at + 0, image.data.size)]
    }
    fun writePixel(image: MutableImageYF, x: Long, y: Long, pixel: Float) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel
    }
    fun unsafePixelAtYF(data: FloatArray, index: Long): Float = data[Math.toIntExact(index + 0)]
    fun unsafeReadPixelYF(data: FloatArray, index: Long): Float = unsafePixelAtYF(data, index)
    fun unsafeReadPixel(image: MutableImageYF, index: Long): Float = unsafeReadPixelYF(image.data, index)
    fun unsafeWritePixel(data: FloatArray, index: Long, pixel: Float) {
        data[Math.toIntExact(index + 0)] = pixel
    }

    fun pixelBaseIndex(image: ImageYA8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 2)
    fun mutablePixelBaseIndex(image: MutableImageYA8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 2)
    fun pixelAt(image: ImageYA8, x: Long, y: Long): PixelYA8 {
        val at = pixelBaseIndex(image, x, y)
        return PixelYA8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte())
    }
    fun readPixel(image: MutableImageYA8, x: Long, y: Long): PixelYA8 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelYA8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte())
    }
    fun writePixel(image: MutableImageYA8, x: Long, y: Long, pixel: PixelYA8) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.luma.toByte()
        image.data[checked(at + 1, image.data.size)] = pixel.alpha.toByte()
    }
    fun unsafePixelAtYA8(data: ByteArray, index: Long): PixelYA8 = PixelYA8(data[Math.toIntExact(index + 0)].toUByte(), data[Math.toIntExact(index + 1)].toUByte())
    fun unsafeReadPixelYA8(data: ByteArray, index: Long): PixelYA8 = unsafePixelAtYA8(data, index)
    fun unsafeReadPixel(image: MutableImageYA8, index: Long): PixelYA8 = unsafeReadPixelYA8(image.data, index)
    fun unsafeWritePixel(data: ByteArray, index: Long, pixel: PixelYA8) {
        data[Math.toIntExact(index + 0)] = pixel.luma.toByte()
        data[Math.toIntExact(index + 1)] = pixel.alpha.toByte()
    }

    fun pixelBaseIndex(image: ImageYA16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 2)
    fun mutablePixelBaseIndex(image: MutableImageYA16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 2)
    fun pixelAt(image: ImageYA16, x: Long, y: Long): PixelYA16 {
        val at = pixelBaseIndex(image, x, y)
        return PixelYA16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort())
    }
    fun readPixel(image: MutableImageYA16, x: Long, y: Long): PixelYA16 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelYA16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort())
    }
    fun writePixel(image: MutableImageYA16, x: Long, y: Long, pixel: PixelYA16) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.luma.toShort()
        image.data[checked(at + 1, image.data.size)] = pixel.alpha.toShort()
    }
    fun unsafePixelAtYA16(data: ShortArray, index: Long): PixelYA16 = PixelYA16(data[Math.toIntExact(index + 0)].toUShort(), data[Math.toIntExact(index + 1)].toUShort())
    fun unsafeReadPixelYA16(data: ShortArray, index: Long): PixelYA16 = unsafePixelAtYA16(data, index)
    fun unsafeReadPixel(image: MutableImageYA16, index: Long): PixelYA16 = unsafeReadPixelYA16(image.data, index)
    fun unsafeWritePixel(data: ShortArray, index: Long, pixel: PixelYA16) {
        data[Math.toIntExact(index + 0)] = pixel.luma.toShort()
        data[Math.toIntExact(index + 1)] = pixel.alpha.toShort()
    }

    fun pixelBaseIndex(image: ImageRGB8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun mutablePixelBaseIndex(image: MutableImageRGB8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun pixelAt(image: ImageRGB8, x: Long, y: Long): PixelRGB8 {
        val at = pixelBaseIndex(image, x, y)
        return PixelRGB8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte())
    }
    fun readPixel(image: MutableImageRGB8, x: Long, y: Long): PixelRGB8 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelRGB8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte())
    }
    fun writePixel(image: MutableImageRGB8, x: Long, y: Long, pixel: PixelRGB8) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.red.toByte()
        image.data[checked(at + 1, image.data.size)] = pixel.green.toByte()
        image.data[checked(at + 2, image.data.size)] = pixel.blue.toByte()
    }
    fun unsafePixelAtRGB8(data: ByteArray, index: Long): PixelRGB8 = PixelRGB8(data[Math.toIntExact(index + 0)].toUByte(), data[Math.toIntExact(index + 1)].toUByte(), data[Math.toIntExact(index + 2)].toUByte())
    fun unsafeReadPixelRGB8(data: ByteArray, index: Long): PixelRGB8 = unsafePixelAtRGB8(data, index)
    fun unsafeReadPixel(image: MutableImageRGB8, index: Long): PixelRGB8 = unsafeReadPixelRGB8(image.data, index)
    fun unsafeWritePixel(data: ByteArray, index: Long, pixel: PixelRGB8) {
        data[Math.toIntExact(index + 0)] = pixel.red.toByte()
        data[Math.toIntExact(index + 1)] = pixel.green.toByte()
        data[Math.toIntExact(index + 2)] = pixel.blue.toByte()
    }

    fun pixelBaseIndex(image: ImageRGB16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun mutablePixelBaseIndex(image: MutableImageRGB16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun pixelAt(image: ImageRGB16, x: Long, y: Long): PixelRGB16 {
        val at = pixelBaseIndex(image, x, y)
        return PixelRGB16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort(), image.data[checked(at + 2, image.data.size)].toUShort())
    }
    fun readPixel(image: MutableImageRGB16, x: Long, y: Long): PixelRGB16 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelRGB16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort(), image.data[checked(at + 2, image.data.size)].toUShort())
    }
    fun writePixel(image: MutableImageRGB16, x: Long, y: Long, pixel: PixelRGB16) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.red.toShort()
        image.data[checked(at + 1, image.data.size)] = pixel.green.toShort()
        image.data[checked(at + 2, image.data.size)] = pixel.blue.toShort()
    }
    fun unsafePixelAtRGB16(data: ShortArray, index: Long): PixelRGB16 = PixelRGB16(data[Math.toIntExact(index + 0)].toUShort(), data[Math.toIntExact(index + 1)].toUShort(), data[Math.toIntExact(index + 2)].toUShort())
    fun unsafeReadPixelRGB16(data: ShortArray, index: Long): PixelRGB16 = unsafePixelAtRGB16(data, index)
    fun unsafeReadPixel(image: MutableImageRGB16, index: Long): PixelRGB16 = unsafeReadPixelRGB16(image.data, index)
    fun unsafeWritePixel(data: ShortArray, index: Long, pixel: PixelRGB16) {
        data[Math.toIntExact(index + 0)] = pixel.red.toShort()
        data[Math.toIntExact(index + 1)] = pixel.green.toShort()
        data[Math.toIntExact(index + 2)] = pixel.blue.toShort()
    }

    fun pixelBaseIndex(image: ImageRGBF, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun mutablePixelBaseIndex(image: MutableImageRGBF, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun pixelAt(image: ImageRGBF, x: Long, y: Long): PixelRGBF {
        val at = pixelBaseIndex(image, x, y)
        return PixelRGBF(image.data[checked(at + 0, image.data.size)], image.data[checked(at + 1, image.data.size)], image.data[checked(at + 2, image.data.size)])
    }
    fun readPixel(image: MutableImageRGBF, x: Long, y: Long): PixelRGBF {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelRGBF(image.data[checked(at + 0, image.data.size)], image.data[checked(at + 1, image.data.size)], image.data[checked(at + 2, image.data.size)])
    }
    fun writePixel(image: MutableImageRGBF, x: Long, y: Long, pixel: PixelRGBF) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.red
        image.data[checked(at + 1, image.data.size)] = pixel.green
        image.data[checked(at + 2, image.data.size)] = pixel.blue
    }
    fun unsafePixelAtRGBF(data: FloatArray, index: Long): PixelRGBF = PixelRGBF(data[Math.toIntExact(index + 0)], data[Math.toIntExact(index + 1)], data[Math.toIntExact(index + 2)])
    fun unsafeReadPixelRGBF(data: FloatArray, index: Long): PixelRGBF = unsafePixelAtRGBF(data, index)
    fun unsafeReadPixel(image: MutableImageRGBF, index: Long): PixelRGBF = unsafeReadPixelRGBF(image.data, index)
    fun unsafeWritePixel(data: FloatArray, index: Long, pixel: PixelRGBF) {
        data[Math.toIntExact(index + 0)] = pixel.red
        data[Math.toIntExact(index + 1)] = pixel.green
        data[Math.toIntExact(index + 2)] = pixel.blue
    }

    fun pixelBaseIndex(image: ImageRGBA8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun mutablePixelBaseIndex(image: MutableImageRGBA8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun pixelAt(image: ImageRGBA8, x: Long, y: Long): PixelRGBA8 {
        val at = pixelBaseIndex(image, x, y)
        return PixelRGBA8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte(), image.data[checked(at + 3, image.data.size)].toUByte())
    }
    fun readPixel(image: MutableImageRGBA8, x: Long, y: Long): PixelRGBA8 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelRGBA8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte(), image.data[checked(at + 3, image.data.size)].toUByte())
    }
    fun writePixel(image: MutableImageRGBA8, x: Long, y: Long, pixel: PixelRGBA8) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.red.toByte()
        image.data[checked(at + 1, image.data.size)] = pixel.green.toByte()
        image.data[checked(at + 2, image.data.size)] = pixel.blue.toByte()
        image.data[checked(at + 3, image.data.size)] = pixel.alpha.toByte()
    }
    fun unsafePixelAtRGBA8(data: ByteArray, index: Long): PixelRGBA8 = PixelRGBA8(data[Math.toIntExact(index + 0)].toUByte(), data[Math.toIntExact(index + 1)].toUByte(), data[Math.toIntExact(index + 2)].toUByte(), data[Math.toIntExact(index + 3)].toUByte())
    fun unsafeReadPixelRGBA8(data: ByteArray, index: Long): PixelRGBA8 = unsafePixelAtRGBA8(data, index)
    fun unsafeReadPixel(image: MutableImageRGBA8, index: Long): PixelRGBA8 = unsafeReadPixelRGBA8(image.data, index)
    fun unsafeWritePixel(data: ByteArray, index: Long, pixel: PixelRGBA8) {
        data[Math.toIntExact(index + 0)] = pixel.red.toByte()
        data[Math.toIntExact(index + 1)] = pixel.green.toByte()
        data[Math.toIntExact(index + 2)] = pixel.blue.toByte()
        data[Math.toIntExact(index + 3)] = pixel.alpha.toByte()
    }

    fun pixelBaseIndex(image: ImageRGBA16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun mutablePixelBaseIndex(image: MutableImageRGBA16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun pixelAt(image: ImageRGBA16, x: Long, y: Long): PixelRGBA16 {
        val at = pixelBaseIndex(image, x, y)
        return PixelRGBA16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort(), image.data[checked(at + 2, image.data.size)].toUShort(), image.data[checked(at + 3, image.data.size)].toUShort())
    }
    fun readPixel(image: MutableImageRGBA16, x: Long, y: Long): PixelRGBA16 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelRGBA16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort(), image.data[checked(at + 2, image.data.size)].toUShort(), image.data[checked(at + 3, image.data.size)].toUShort())
    }
    fun writePixel(image: MutableImageRGBA16, x: Long, y: Long, pixel: PixelRGBA16) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.red.toShort()
        image.data[checked(at + 1, image.data.size)] = pixel.green.toShort()
        image.data[checked(at + 2, image.data.size)] = pixel.blue.toShort()
        image.data[checked(at + 3, image.data.size)] = pixel.alpha.toShort()
    }
    fun unsafePixelAtRGBA16(data: ShortArray, index: Long): PixelRGBA16 = PixelRGBA16(data[Math.toIntExact(index + 0)].toUShort(), data[Math.toIntExact(index + 1)].toUShort(), data[Math.toIntExact(index + 2)].toUShort(), data[Math.toIntExact(index + 3)].toUShort())
    fun unsafeReadPixelRGBA16(data: ShortArray, index: Long): PixelRGBA16 = unsafePixelAtRGBA16(data, index)
    fun unsafeReadPixel(image: MutableImageRGBA16, index: Long): PixelRGBA16 = unsafeReadPixelRGBA16(image.data, index)
    fun unsafeWritePixel(data: ShortArray, index: Long, pixel: PixelRGBA16) {
        data[Math.toIntExact(index + 0)] = pixel.red.toShort()
        data[Math.toIntExact(index + 1)] = pixel.green.toShort()
        data[Math.toIntExact(index + 2)] = pixel.blue.toShort()
        data[Math.toIntExact(index + 3)] = pixel.alpha.toShort()
    }

    fun pixelBaseIndex(image: ImageYCbCr8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun mutablePixelBaseIndex(image: MutableImageYCbCr8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 3)
    fun pixelAt(image: ImageYCbCr8, x: Long, y: Long): PixelYCbCr8 {
        val at = pixelBaseIndex(image, x, y)
        return PixelYCbCr8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte())
    }
    fun readPixel(image: MutableImageYCbCr8, x: Long, y: Long): PixelYCbCr8 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelYCbCr8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte())
    }
    fun writePixel(image: MutableImageYCbCr8, x: Long, y: Long, pixel: PixelYCbCr8) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.luma.toByte()
        image.data[checked(at + 1, image.data.size)] = pixel.cb.toByte()
        image.data[checked(at + 2, image.data.size)] = pixel.cr.toByte()
    }
    fun unsafePixelAtYCbCr8(data: ByteArray, index: Long): PixelYCbCr8 = PixelYCbCr8(data[Math.toIntExact(index + 0)].toUByte(), data[Math.toIntExact(index + 1)].toUByte(), data[Math.toIntExact(index + 2)].toUByte())
    fun unsafeReadPixelYCbCr8(data: ByteArray, index: Long): PixelYCbCr8 = unsafePixelAtYCbCr8(data, index)
    fun unsafeReadPixel(image: MutableImageYCbCr8, index: Long): PixelYCbCr8 = unsafeReadPixelYCbCr8(image.data, index)
    fun unsafeWritePixel(data: ByteArray, index: Long, pixel: PixelYCbCr8) {
        data[Math.toIntExact(index + 0)] = pixel.luma.toByte()
        data[Math.toIntExact(index + 1)] = pixel.cb.toByte()
        data[Math.toIntExact(index + 2)] = pixel.cr.toByte()
    }

    fun pixelBaseIndex(image: ImageYCbCrK8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun mutablePixelBaseIndex(image: MutableImageYCbCrK8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun pixelAt(image: ImageYCbCrK8, x: Long, y: Long): PixelYCbCrK8 {
        val at = pixelBaseIndex(image, x, y)
        return PixelYCbCrK8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte(), image.data[checked(at + 3, image.data.size)].toUByte())
    }
    fun readPixel(image: MutableImageYCbCrK8, x: Long, y: Long): PixelYCbCrK8 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelYCbCrK8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte(), image.data[checked(at + 3, image.data.size)].toUByte())
    }
    fun writePixel(image: MutableImageYCbCrK8, x: Long, y: Long, pixel: PixelYCbCrK8) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.luma.toByte()
        image.data[checked(at + 1, image.data.size)] = pixel.cb.toByte()
        image.data[checked(at + 2, image.data.size)] = pixel.cr.toByte()
        image.data[checked(at + 3, image.data.size)] = pixel.black.toByte()
    }
    fun unsafePixelAtYCbCrK8(data: ByteArray, index: Long): PixelYCbCrK8 = PixelYCbCrK8(data[Math.toIntExact(index + 0)].toUByte(), data[Math.toIntExact(index + 1)].toUByte(), data[Math.toIntExact(index + 2)].toUByte(), data[Math.toIntExact(index + 3)].toUByte())
    fun unsafeReadPixelYCbCrK8(data: ByteArray, index: Long): PixelYCbCrK8 = unsafePixelAtYCbCrK8(data, index)
    fun unsafeReadPixel(image: MutableImageYCbCrK8, index: Long): PixelYCbCrK8 = unsafeReadPixelYCbCrK8(image.data, index)
    fun unsafeWritePixel(data: ByteArray, index: Long, pixel: PixelYCbCrK8) {
        data[Math.toIntExact(index + 0)] = pixel.luma.toByte()
        data[Math.toIntExact(index + 1)] = pixel.cb.toByte()
        data[Math.toIntExact(index + 2)] = pixel.cr.toByte()
        data[Math.toIntExact(index + 3)] = pixel.black.toByte()
    }

    fun pixelBaseIndex(image: ImageCMYK8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun mutablePixelBaseIndex(image: MutableImageCMYK8, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun pixelAt(image: ImageCMYK8, x: Long, y: Long): PixelCMYK8 {
        val at = pixelBaseIndex(image, x, y)
        return PixelCMYK8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte(), image.data[checked(at + 3, image.data.size)].toUByte())
    }
    fun readPixel(image: MutableImageCMYK8, x: Long, y: Long): PixelCMYK8 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelCMYK8(image.data[checked(at + 0, image.data.size)].toUByte(), image.data[checked(at + 1, image.data.size)].toUByte(), image.data[checked(at + 2, image.data.size)].toUByte(), image.data[checked(at + 3, image.data.size)].toUByte())
    }
    fun writePixel(image: MutableImageCMYK8, x: Long, y: Long, pixel: PixelCMYK8) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.cyan.toByte()
        image.data[checked(at + 1, image.data.size)] = pixel.magenta.toByte()
        image.data[checked(at + 2, image.data.size)] = pixel.yellow.toByte()
        image.data[checked(at + 3, image.data.size)] = pixel.black.toByte()
    }
    fun unsafePixelAtCMYK8(data: ByteArray, index: Long): PixelCMYK8 = PixelCMYK8(data[Math.toIntExact(index + 0)].toUByte(), data[Math.toIntExact(index + 1)].toUByte(), data[Math.toIntExact(index + 2)].toUByte(), data[Math.toIntExact(index + 3)].toUByte())
    fun unsafeReadPixelCMYK8(data: ByteArray, index: Long): PixelCMYK8 = unsafePixelAtCMYK8(data, index)
    fun unsafeReadPixel(image: MutableImageCMYK8, index: Long): PixelCMYK8 = unsafeReadPixelCMYK8(image.data, index)
    fun unsafeWritePixel(data: ByteArray, index: Long, pixel: PixelCMYK8) {
        data[Math.toIntExact(index + 0)] = pixel.cyan.toByte()
        data[Math.toIntExact(index + 1)] = pixel.magenta.toByte()
        data[Math.toIntExact(index + 2)] = pixel.yellow.toByte()
        data[Math.toIntExact(index + 3)] = pixel.black.toByte()
    }

    fun pixelBaseIndex(image: ImageCMYK16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun mutablePixelBaseIndex(image: MutableImageCMYK16, x: Long, y: Long) = pixelBaseIndex(image.width.toLong(), x, y, 4)
    fun pixelAt(image: ImageCMYK16, x: Long, y: Long): PixelCMYK16 {
        val at = pixelBaseIndex(image, x, y)
        return PixelCMYK16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort(), image.data[checked(at + 2, image.data.size)].toUShort(), image.data[checked(at + 3, image.data.size)].toUShort())
    }
    fun readPixel(image: MutableImageCMYK16, x: Long, y: Long): PixelCMYK16 {
        val at = mutablePixelBaseIndex(image, x, y)
        return PixelCMYK16(image.data[checked(at + 0, image.data.size)].toUShort(), image.data[checked(at + 1, image.data.size)].toUShort(), image.data[checked(at + 2, image.data.size)].toUShort(), image.data[checked(at + 3, image.data.size)].toUShort())
    }
    fun writePixel(image: MutableImageCMYK16, x: Long, y: Long, pixel: PixelCMYK16) {
        val at = mutablePixelBaseIndex(image, x, y)
        image.data[checked(at + 0, image.data.size)] = pixel.cyan.toShort()
        image.data[checked(at + 1, image.data.size)] = pixel.magenta.toShort()
        image.data[checked(at + 2, image.data.size)] = pixel.yellow.toShort()
        image.data[checked(at + 3, image.data.size)] = pixel.black.toShort()
    }
    fun unsafePixelAtCMYK16(data: ShortArray, index: Long): PixelCMYK16 = PixelCMYK16(data[Math.toIntExact(index + 0)].toUShort(), data[Math.toIntExact(index + 1)].toUShort(), data[Math.toIntExact(index + 2)].toUShort(), data[Math.toIntExact(index + 3)].toUShort())
    fun unsafeReadPixelCMYK16(data: ShortArray, index: Long): PixelCMYK16 = unsafePixelAtCMYK16(data, index)
    fun unsafeReadPixel(image: MutableImageCMYK16, index: Long): PixelCMYK16 = unsafeReadPixelCMYK16(image.data, index)
    fun unsafeWritePixel(data: ShortArray, index: Long, pixel: PixelCMYK16) {
        data[Math.toIntExact(index + 0)] = pixel.cyan.toShort()
        data[Math.toIntExact(index + 1)] = pixel.magenta.toShort()
        data[Math.toIntExact(index + 2)] = pixel.yellow.toShort()
        data[Math.toIntExact(index + 3)] = pixel.black.toShort()
    }
}
