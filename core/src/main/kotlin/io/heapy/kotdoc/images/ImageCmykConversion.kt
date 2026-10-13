// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal data class PixelCMYK8(val cyan: UByte, val magenta: UByte, val yellow: UByte, val black: UByte)
internal data class PixelYCbCrK8(val luma: UByte, val cb: UByte, val cr: UByte, val black: UByte)
internal data class PixelRGB16(val red: UShort, val green: UShort, val blue: UShort)
internal data class PixelCMYK16(val cyan: UShort, val magenta: UShort, val yellow: UShort, val black: UShort)
internal data class ImageCMYK8(val width: Int, val height: Int, val data: ByteArray)
internal data class ImageYCbCrK8(val width: Int, val height: Int, val data: ByteArray)
internal data class ImageRGB16(val width: Int, val height: Int, val data: ShortArray)
internal data class ImageCMYK16(val width: Int, val height: Int, val data: ShortArray)

internal object ImageCmykConversion {
    fun toCmyk(pixel: PixelRGB8): PixelCMYK8 {
        val r = pixel.red.toInt(); val g = pixel.green.toInt(); val b = pixel.blue.toInt()
        val maximum = maxOf(r, g, b)
        if (maximum == 0) return PixelCMYK8(0u, 0u, 0u, 255u)
        fun ink(value: Int) = (255 * (maximum - value) / maximum).toUByte()
        return PixelCMYK8(ink(r), ink(g), ink(b), (255 - maximum).toUByte())
    }

    fun toCmyk(pixel: PixelRGB16): PixelCMYK16 {
        val r = pixel.red.toInt(); val g = pixel.green.toInt(); val b = pixel.blue.toInt()
        val maximum = maxOf(r, g, b)
        if (maximum == 0) return PixelCMYK16(0u, 0u, 0u, 65535u)
        // The original Word32 product can exceed signed Int32 but cannot overflow Word32.
        fun ink(value: Int) = (65535L * (maximum - value) / maximum).toUShort()
        return PixelCMYK16(ink(r), ink(g), ink(b), (65535 - maximum).toUShort())
    }

    fun toRgb(pixel: PixelCMYK8): PixelRGB8 {
        val inverseBlack = 255 - pixel.black.toInt()
        fun light(value: UByte) = ((255 - value.toInt()) * inverseBlack / 255).coerceIn(0, 255).toUByte()
        return PixelRGB8(light(pixel.cyan), light(pixel.magenta), light(pixel.yellow))
    }

    fun toRgb(pixel: PixelCMYK16): PixelRGB16 {
        val inverseBlack = 65535L - pixel.black.toLong()
        // Preserve division by 65536: even full white becomes 65534 in the original.
        fun light(value: UShort) = (((65535L - value.toLong()) * inverseBlack) shr 16).toUShort()
        return PixelRGB16(light(pixel.cyan), light(pixel.magenta), light(pixel.yellow))
    }

    private fun red(p: PixelYCbCrK8) = p.luma.toFloat() + 1.402f * p.cr.toFloat() - 179.456f
    private fun green(p: PixelYCbCrK8) = p.luma.toFloat() - 0.3441363f * p.cb.toFloat() - 0.71413636f * p.cr.toFloat() + 135.4589f
    private fun blue(p: PixelYCbCrK8) = p.luma.toFloat() + 1.772f * p.cb.toFloat() - 226.816f
    private fun clamp(value: Float) = value.coerceIn(0f, 255f).toInt().toUByte()

    fun toRgb(pixel: PixelYCbCrK8) = PixelRGB8(clamp(red(pixel)), clamp(green(pixel)), clamp(blue(pixel)))
    fun toCmyk(pixel: PixelYCbCrK8) = PixelCMYK8(clamp(255f - red(pixel)), clamp(255f - green(pixel)), clamp(255f - blue(pixel)), pixel.black)

    private inline fun map8(width: Int, height: Int, components: Int, write: (Int, ByteArray, Int) -> Unit): ByteArray {
        val result = ByteArray(Math.toIntExact(width.toLong() * height * components))
        var pixel = 0
        for (y in 0..<height) for (x in 0..<width) { write(pixel, result, pixel * components); pixel++ }
        return result
    }

    private inline fun map16(width: Int, height: Int, components: Int, write: (Int, ShortArray, Int) -> Unit): ShortArray {
        val result = ShortArray(Math.toIntExact(width.toLong() * height * components))
        var pixel = 0
        for (y in 0..<height) for (x in 0..<width) { write(pixel, result, pixel * components); pixel++ }
        return result
    }

    fun toCmyk(image: ImageRGB8) = ImageCMYK8(image.width, image.height, map8(image.width, image.height, 4) { i, out, at ->
        val src = i * 3; val data = image.data
        val p = toCmyk(PixelRGB8(data[src].toUByte(), data[src + 1].toUByte(), data[src + 2].toUByte()))
        out[at] = p.cyan.toByte(); out[at + 1] = p.magenta.toByte(); out[at + 2] = p.yellow.toByte(); out[at + 3] = p.black.toByte()
    })

    fun toRgb(image: ImageCMYK8) = ImageRGB8(image.width, image.height, map8(image.width, image.height, 3) { i, out, at ->
        val src = i * 4; val data = image.data
        val p = toRgb(PixelCMYK8(data[src].toUByte(), data[src + 1].toUByte(), data[src + 2].toUByte(), data[src + 3].toUByte()))
        out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte()
    })

    fun toCmyk(image: ImageRGB16) = ImageCMYK16(image.width, image.height, map16(image.width, image.height, 4) { i, out, at ->
        val src = i * 3; val data = image.data
        val p = toCmyk(PixelRGB16(data[src].toUShort(), data[src + 1].toUShort(), data[src + 2].toUShort()))
        out[at] = p.cyan.toShort(); out[at + 1] = p.magenta.toShort(); out[at + 2] = p.yellow.toShort(); out[at + 3] = p.black.toShort()
    })

    fun toRgb(image: ImageCMYK16) = ImageRGB16(image.width, image.height, map16(image.width, image.height, 3) { i, out, at ->
        val src = i * 4; val data = image.data
        val p = toRgb(PixelCMYK16(data[src].toUShort(), data[src + 1].toUShort(), data[src + 2].toUShort(), data[src + 3].toUShort()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort()
    })

    private fun ycck(data: ByteArray, at: Int) = PixelYCbCrK8(data[at].toUByte(), data[at + 1].toUByte(), data[at + 2].toUByte(), data[at + 3].toUByte())

    fun toRgb(image: ImageYCbCrK8) = ImageRGB8(image.width, image.height, map8(image.width, image.height, 3) { i, out, at ->
        val p = toRgb(ycck(image.data, i * 4))
        out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte()
    })

    fun toCmyk(image: ImageYCbCrK8) = ImageCMYK8(image.width, image.height, map8(image.width, image.height, 4) { i, out, at ->
        val p = toCmyk(ycck(image.data, i * 4))
        out[at] = p.cyan.toByte(); out[at + 1] = p.magenta.toByte(); out[at + 2] = p.yellow.toByte(); out[at + 3] = p.black.toByte()
    })
}
