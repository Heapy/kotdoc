// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal object ImageLuma {
    fun computeLuma(pixel: UByte) = pixel
    fun computeLuma(pixel: UShort) = pixel
    fun computeLuma(pixel: UInt) = pixel
    fun computeLuma(pixel: Float) = pixel
    fun computeLuma(pixel: PixelYA8) = pixel.luma
    fun computeLuma(pixel: PixelYCbCr8) = pixel.luma
    fun computeLuma(pixel: PixelRGB8) = kotlin.math.floor(0.3 * pixel.red.toDouble() + 0.59 * pixel.green.toDouble() + 0.11 * pixel.blue.toDouble()).toInt().toUByte()
    fun computeLuma(pixel: PixelRGBA8) = kotlin.math.floor(0.3 * pixel.red.toDouble() + 0.59 * pixel.green.toDouble() + 0.11 * pixel.blue.toDouble()).toInt().toUByte()
    fun computeLuma(pixel: PixelRGB16) = kotlin.math.floor(0.3 * pixel.red.toDouble() + 0.59 * pixel.green.toDouble() + 0.11 * pixel.blue.toDouble()).toInt().toUShort()
    fun computeLuma(pixel: PixelRGBF) = 0.3f * pixel.red + 0.59f * pixel.green + 0.11f * pixel.blue

    fun extractLumaPlane(image: ImageY8) = image
    fun extractLumaPlane(image: ImageY16) = image
    fun extractLumaPlane(image: ImageY32) = image
    fun extractLumaPlane(image: ImageYF) = image
    fun extractLumaPlane(image: ImageYA8) = ImagePlanes.extractComponent(PlaneLuma, image)
    fun extractLumaPlane(image: ImageYCbCr8) = ImagePlanes.extractComponent(PlaneLuma, image)

    fun extractLumaPlane(image: ImageRGB8): ImageY8 {
        val data = ByteArray(Math.toIntExact(image.width.toLong() * image.height))
        var pixel = 0
        for (y in 0..<image.height) for (x in 0..<image.width) {
            val at = pixel * 3
            data[pixel] = computeLuma(PixelRGB8(image.data[at].toUByte(), image.data[at + 1].toUByte(), image.data[at + 2].toUByte())).toByte()
            pixel++
        }
        return ImageY8(image.width, image.height, data)
    }

    fun extractLumaPlane(image: ImageRGBA8): ImageY8 {
        val data = ByteArray(Math.toIntExact(image.width.toLong() * image.height))
        var pixel = 0
        for (y in 0..<image.height) for (x in 0..<image.width) {
            val at = pixel * 4
            data[pixel] = computeLuma(PixelRGBA8(image.data[at].toUByte(), image.data[at + 1].toUByte(), image.data[at + 2].toUByte(), image.data[at + 3].toUByte())).toByte()
            pixel++
        }
        return ImageY8(image.width, image.height, data)
    }

    fun extractLumaPlane(image: ImageRGB16): ImageY16 {
        val data = ShortArray(Math.toIntExact(image.width.toLong() * image.height))
        var pixel = 0
        for (y in 0..<image.height) for (x in 0..<image.width) {
            val at = pixel * 3
            data[pixel] = computeLuma(PixelRGB16(image.data[at].toUShort(), image.data[at + 1].toUShort(), image.data[at + 2].toUShort())).toShort()
            pixel++
        }
        return ImageY16(image.width, image.height, data)
    }

    fun extractLumaPlane(image: ImageRGBF): ImageYF {
        val data = FloatArray(Math.toIntExact(image.width.toLong() * image.height))
        var pixel = 0
        for (y in 0..<image.height) for (x in 0..<image.width) {
            val at = pixel * 3
            data[pixel] = computeLuma(PixelRGBF(image.data[at], image.data[at + 1], image.data[at + 2]))
            pixel++
        }
        return ImageYF(image.width, image.height, data)
    }
}
