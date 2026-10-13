// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal data class PixelRGB8(val red: UByte, val green: UByte, val blue: UByte)
internal data class PixelYCbCr8(val luma: UByte, val cb: UByte, val cr: UByte)
internal data class ImageRGB8(val width: Int, val height: Int, val data: ByteArray)
internal data class ImageYCbCr8(val width: Int, val height: Int, val data: ByteArray)

internal object ImageColorConversion {
    private fun fix(value: Float) = kotlin.math.floor(value * 65536 + 0.5f).toInt()
    private val rY = fix(0.29900f)
    private val gY = fix(0.58700f)
    private val bY = fix(0.11400f)
    private val rCb = -fix(0.16874f)
    private val gCb = -fix(0.33126f)
    private val bCb = fix(0.5f)
    private val gCr = -fix(0.41869f)
    private val bCr = -fix(0.08131f)
    private val crR = IntArray(256) { (fix(1.40200f) * (it - 128) + 32768) shr 16 }
    private val cbB = IntArray(256) { (fix(1.77200f) * (it - 128) + 32768) shr 16 }
    private val crG = IntArray(256) { -fix(0.71414f) * (it - 128) }
    private val cbG = IntArray(256) { -fix(0.34414f) * (it - 128) + 32768 }

    fun convertPixel(pixel: PixelRGB8): PixelYCbCr8 {
        val r = pixel.red.toInt(); val g = pixel.green.toInt(); val b = pixel.blue.toInt()
        return PixelYCbCr8(
            ((rY * r + gY * g + bY * b + 32768) shr 16).toUByte(),
            ((rCb * r + gCb * g + bCb * b + 8421375) shr 16).toUByte(),
            ((bCb * r + 8421375 + gCr * g + bCr * b) shr 16).toUByte(),
        )
    }

    fun convertPixel(pixel: PixelYCbCr8): PixelRGB8 {
        val y = pixel.luma.toInt(); val cb = pixel.cb.toInt(); val cr = pixel.cr.toInt()
        return PixelRGB8(
            (y + crR[cr]).coerceIn(0, 255).toUByte(),
            (y + ((cbG[cb] + crG[cr]) shr 16)).coerceIn(0, 255).toUByte(),
            (y + cbB[cb]).coerceIn(0, 255).toUByte(),
        )
    }

    fun convertImage(image: ImageRGB8): ImageYCbCr8 {
        val data = ByteArray(Math.toIntExact(image.width.toLong() * image.height * 3))
        for (at in data.indices step 3) {
            val r = image.data[at].toInt() and 255
            val g = image.data[at + 1].toInt() and 255
            val b = image.data[at + 2].toInt() and 255
            data[at] = ((rY * r + gY * g + bY * b + 32768) shr 16).toByte()
            data[at + 1] = ((rCb * r + gCb * g + bCb * b + 8421375) shr 16).toByte()
            data[at + 2] = ((bCb * r + 8421375 + gCr * g + bCr * b) shr 16).toByte()
        }
        return ImageYCbCr8(image.width, image.height, data)
    }

    fun convertImage(image: ImageYCbCr8): ImageRGB8 {
        val data = ByteArray(Math.toIntExact(image.width.toLong() * image.height * 3))
        for (at in data.indices step 3) {
            val y = image.data[at].toInt() and 255
            val cb = image.data[at + 1].toInt() and 255
            val cr = image.data[at + 2].toInt() and 255
            data[at] = (y + crR[cr]).coerceIn(0, 255).toByte()
            data[at + 1] = (y + ((cbG[cb] + crG[cr]) shr 16)).coerceIn(0, 255).toByte()
            data[at + 2] = (y + cbB[cb]).coerceIn(0, 255).toByte()
        }
        return ImageRGB8(image.width, image.height, data)
    }
}
