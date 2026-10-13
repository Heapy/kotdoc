// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal data class PixelYA8(val luma: UByte, val alpha: UByte)
internal data class PixelYA16(val luma: UShort, val alpha: UShort)
internal data class PixelRGBA8(val red: UByte, val green: UByte, val blue: UByte, val alpha: UByte)
internal data class PixelRGBA16(val red: UShort, val green: UShort, val blue: UShort, val alpha: UShort)
internal data class PixelRGBF(val red: Float, val green: Float, val blue: Float) {
    // Derived upstream Eq uses IEEE equality: NaN is non-reflexive; both zeros are equal.
    override fun equals(other: Any?) = other is PixelRGBF && red == other.red && green == other.green && blue == other.blue
    override fun hashCode(): Int {
        fun hash(value: Float) = if (value == 0f) 0 else value.hashCode()
        return (hash(red) * 31 + hash(green)) * 31 + hash(blue)
    }
}
internal data class ImageY8(val width: Int, val height: Int, val data: ByteArray)
internal data class ImageY16(val width: Int, val height: Int, val data: ShortArray)
internal data class ImageYF(val width: Int, val height: Int, val data: FloatArray)
internal data class ImageYA8(val width: Int, val height: Int, val data: ByteArray)
internal data class ImageYA16(val width: Int, val height: Int, val data: ShortArray)
internal data class ImageRGBA8(val width: Int, val height: Int, val data: ByteArray)
internal data class ImageRGBA16(val width: Int, val height: Int, val data: ShortArray)
internal data class ImageRGBF(val width: Int, val height: Int, val data: FloatArray)

internal object ImagePromotion {
    fun toYa8(value: UByte) = PixelYA8(value, 255u)
    fun toFloat(value: UByte) = value.toFloat() / 255f
    fun to16(value: UByte) = (value.toInt() * 257).toUShort()
    fun toRgb8(value: UByte) = PixelRGB8(value, value, value)
    fun toRgb16(value: UByte) = to16(value).let { PixelRGB16(it, it, it) }
    fun toRgba8(value: UByte) = PixelRGBA8(value, value, value, 255u)
    fun toYa16(value: UShort) = PixelYA16(value, 65535u)
    fun toRgb16(value: UShort) = PixelRGB16(value, value, value)
    fun toRgba16(value: UShort) = PixelRGBA16(value, value, value, 65535u)
    fun toRgbFloat(value: Float) = PixelRGBF(value, value, value)
    fun toRgb8(value: PixelYA8) = toRgb8(value.luma)
    fun toRgb16(value: PixelYA8) = toRgb16(value.luma)
    fun toRgba8(value: PixelYA8) = PixelRGBA8(value.luma, value.luma, value.luma, value.alpha)
    fun toRgb16(value: PixelYA16) = toRgb16(value.luma)
    fun toRgba16(value: PixelYA16) = PixelRGBA16(value.luma, value.luma, value.luma, value.alpha)
    fun toRgba16(value: PixelRGB16) = PixelRGBA16(value.red, value.green, value.blue, 65535u)
    fun toRgba8(value: PixelRGB8) = PixelRGBA8(value.red, value.green, value.blue, 255u)
    fun toRgbFloat(value: PixelRGB8) = PixelRGBF(toFloat(value.red), toFloat(value.green), toFloat(value.blue))
    fun toRgb16(value: PixelRGB8) = PixelRGB16(to16(value.red), to16(value.green), to16(value.blue))
    fun toRgba16(value: PixelRGB8) = PixelRGBA16(to16(value.red), to16(value.green), to16(value.blue), 65535u)
    fun toRgba16(value: PixelRGBA8) = PixelRGBA16(to16(value.red), to16(value.green), to16(value.blue), to16(value.alpha))

    // Original same-type promotePixel/promoteImage and convertPixel/convertImage are id.
    fun <T> promoteIdentity(value: T): T = value
    fun <T> convertIdentity(value: T): T = value

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
    private inline fun mapFloat(width: Int, height: Int, components: Int, write: (Int, FloatArray, Int) -> Unit): FloatArray {
        val result = FloatArray(Math.toIntExact(width.toLong() * height * components))
        var pixel = 0
        for (y in 0..<height) for (x in 0..<width) { write(pixel, result, pixel * components); pixel++ }
        return result
    }

    fun toYa8(image: ImageY8) = ImageYA8(image.width, image.height, map8(image.width, image.height, 2) { i, out, at ->
        val src = i
        val p = toYa8(image.data[src].toUByte())
        out[at] = p.luma.toByte(); out[at + 1] = p.alpha.toByte()
    })

    fun toFloat(image: ImageY8) = ImageYF(image.width, image.height, mapFloat(image.width, image.height, 1) { i, out, at ->
        val src = i
        val p = toFloat(image.data[src].toUByte())
        out[at] = p
    })

    fun to16(image: ImageY8) = ImageY16(image.width, image.height, map16(image.width, image.height, 1) { i, out, at ->
        val src = i
        val p = to16(image.data[src].toUByte())
        out[at] = p.toShort()
    })

    fun toRgb8(image: ImageY8) = ImageRGB8(image.width, image.height, map8(image.width, image.height, 3) { i, out, at ->
        val src = i
        val p = toRgb8(image.data[src].toUByte())
        out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte()
    })

    fun toRgb16(image: ImageY8) = ImageRGB16(image.width, image.height, map16(image.width, image.height, 3) { i, out, at ->
        val src = i
        val p = toRgb16(image.data[src].toUByte())
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort()
    })

    fun toRgba8(image: ImageY8) = ImageRGBA8(image.width, image.height, map8(image.width, image.height, 4) { i, out, at ->
        val src = i
        val p = toRgba8(image.data[src].toUByte())
        out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte(); out[at + 3] = p.alpha.toByte()
    })

    fun toYa16(image: ImageY16) = ImageYA16(image.width, image.height, map16(image.width, image.height, 2) { i, out, at ->
        val src = i
        val p = toYa16(image.data[src].toUShort())
        out[at] = p.luma.toShort(); out[at + 1] = p.alpha.toShort()
    })

    fun toRgb16(image: ImageY16) = ImageRGB16(image.width, image.height, map16(image.width, image.height, 3) { i, out, at ->
        val src = i
        val p = toRgb16(image.data[src].toUShort())
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort()
    })

    fun toRgba16(image: ImageY16) = ImageRGBA16(image.width, image.height, map16(image.width, image.height, 4) { i, out, at ->
        val src = i
        val p = toRgba16(image.data[src].toUShort())
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort(); out[at + 3] = p.alpha.toShort()
    })

    fun toRgbFloat(image: ImageYF) = ImageRGBF(image.width, image.height, mapFloat(image.width, image.height, 3) { i, out, at ->
        val src = i
        val p = toRgbFloat(image.data[src])
        out[at] = p.red; out[at + 1] = p.green; out[at + 2] = p.blue
    })

    fun toRgb8(image: ImageYA8) = ImageRGB8(image.width, image.height, map8(image.width, image.height, 3) { i, out, at ->
        val src = i * 2
        val p = toRgb8(PixelYA8(image.data[src].toUByte(), image.data[src + 1].toUByte()))
        out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte()
    })

    fun toRgb16(image: ImageYA8) = ImageRGB16(image.width, image.height, map16(image.width, image.height, 3) { i, out, at ->
        val src = i * 2
        val p = toRgb16(PixelYA8(image.data[src].toUByte(), image.data[src + 1].toUByte()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort()
    })

    fun toRgba8(image: ImageYA8) = ImageRGBA8(image.width, image.height, map8(image.width, image.height, 4) { i, out, at ->
        val src = i * 2
        val p = toRgba8(PixelYA8(image.data[src].toUByte(), image.data[src + 1].toUByte()))
        out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte(); out[at + 3] = p.alpha.toByte()
    })

    fun toRgb16(image: ImageYA16) = ImageRGB16(image.width, image.height, map16(image.width, image.height, 3) { i, out, at ->
        val src = i * 2
        val p = toRgb16(PixelYA16(image.data[src].toUShort(), image.data[src + 1].toUShort()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort()
    })

    fun toRgba16(image: ImageYA16) = ImageRGBA16(image.width, image.height, map16(image.width, image.height, 4) { i, out, at ->
        val src = i * 2
        val p = toRgba16(PixelYA16(image.data[src].toUShort(), image.data[src + 1].toUShort()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort(); out[at + 3] = p.alpha.toShort()
    })

    fun toRgba16(image: ImageRGB16) = ImageRGBA16(image.width, image.height, map16(image.width, image.height, 4) { i, out, at ->
        val src = i * 3
        val p = toRgba16(PixelRGB16(image.data[src].toUShort(), image.data[src + 1].toUShort(), image.data[src + 2].toUShort()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort(); out[at + 3] = p.alpha.toShort()
    })

    fun toRgba8(image: ImageRGB8) = ImageRGBA8(image.width, image.height, map8(image.width, image.height, 4) { i, out, at ->
        val src = i * 3
        val p = toRgba8(PixelRGB8(image.data[src].toUByte(), image.data[src + 1].toUByte(), image.data[src + 2].toUByte()))
        out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte(); out[at + 3] = p.alpha.toByte()
    })

    fun toRgbFloat(image: ImageRGB8) = ImageRGBF(image.width, image.height, mapFloat(image.width, image.height, 3) { i, out, at ->
        val src = i * 3
        val p = toRgbFloat(PixelRGB8(image.data[src].toUByte(), image.data[src + 1].toUByte(), image.data[src + 2].toUByte()))
        out[at] = p.red; out[at + 1] = p.green; out[at + 2] = p.blue
    })

    fun toRgb16(image: ImageRGB8) = ImageRGB16(image.width, image.height, map16(image.width, image.height, 3) { i, out, at ->
        val src = i * 3
        val p = toRgb16(PixelRGB8(image.data[src].toUByte(), image.data[src + 1].toUByte(), image.data[src + 2].toUByte()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort()
    })

    fun toRgba16(image: ImageRGB8) = ImageRGBA16(image.width, image.height, map16(image.width, image.height, 4) { i, out, at ->
        val src = i * 3
        val p = toRgba16(PixelRGB8(image.data[src].toUByte(), image.data[src + 1].toUByte(), image.data[src + 2].toUByte()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort(); out[at + 3] = p.alpha.toShort()
    })

    fun toRgba16(image: ImageRGBA8) = ImageRGBA16(image.width, image.height, map16(image.width, image.height, 4) { i, out, at ->
        val src = i * 4
        val p = toRgba16(PixelRGBA8(image.data[src].toUByte(), image.data[src + 1].toUByte(), image.data[src + 2].toUByte(), image.data[src + 3].toUByte()))
        out[at] = p.red.toShort(); out[at + 1] = p.green.toShort(); out[at + 2] = p.blue.toShort(); out[at + 3] = p.alpha.toShort()
    })
}
