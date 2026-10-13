// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal data class MutableImageY8(val width: Int, val height: Int, val data: ByteArray)
internal data class MutableImageY16(val width: Int, val height: Int, val data: ShortArray)
internal data class MutableImageY32(val width: Int, val height: Int, val data: IntArray)
internal data class MutableImageYF(val width: Int, val height: Int, val data: FloatArray)
internal data class MutableImageYA8(val width: Int, val height: Int, val data: ByteArray)
internal data class MutableImageYA16(val width: Int, val height: Int, val data: ShortArray)
internal data class MutableImageRGB8(val width: Int, val height: Int, val data: ByteArray)
internal data class MutableImageRGB16(val width: Int, val height: Int, val data: ShortArray)
internal data class MutableImageRGBF(val width: Int, val height: Int, val data: FloatArray)
internal data class MutableImageRGBA8(val width: Int, val height: Int, val data: ByteArray)
internal data class MutableImageRGBA16(val width: Int, val height: Int, val data: ShortArray)
internal data class MutableImageYCbCr8(val width: Int, val height: Int, val data: ByteArray)
internal data class MutableImageYCbCrK8(val width: Int, val height: Int, val data: ByteArray)
internal data class MutableImageCMYK8(val width: Int, val height: Int, val data: ByteArray)
internal data class MutableImageCMYK16(val width: Int, val height: Int, val data: ShortArray)

internal object ImageMutable {
    private fun length(width: Int, height: Int, components: Int): Int {
        val count = width.toLong() * height * components
        if (count < 0) throw ImagePixelException("negative length $count")
        return Math.toIntExact(count)
    }
    fun thawImage(image: ImageY8) = MutableImageY8(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageY8) = MutableImageY8(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageY8) = ImageY8(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageY8) = ImageY8(image.width, image.height, image.data)
    fun newMutableY8(width: Int, height: Int) = MutableImageY8(width, height, ByteArray(length(width, height, 1)))
    fun createMutableImage(width: Int, height: Int, background: UByte): MutableImageY8 {
        val image = newMutableY8(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 1
        }
        return image
    }

    fun thawImage(image: ImageY16) = MutableImageY16(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageY16) = MutableImageY16(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageY16) = ImageY16(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageY16) = ImageY16(image.width, image.height, image.data)
    fun newMutableY16(width: Int, height: Int) = MutableImageY16(width, height, ShortArray(length(width, height, 1)))
    fun createMutableImage(width: Int, height: Int, background: UShort): MutableImageY16 {
        val image = newMutableY16(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 1
        }
        return image
    }

    fun thawImage(image: ImageY32) = MutableImageY32(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageY32) = MutableImageY32(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageY32) = ImageY32(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageY32) = ImageY32(image.width, image.height, image.data)
    fun newMutableY32(width: Int, height: Int) = MutableImageY32(width, height, IntArray(length(width, height, 1)))
    fun createMutableImage(width: Int, height: Int, background: UInt): MutableImageY32 {
        val image = newMutableY32(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 1
        }
        return image
    }

    fun thawImage(image: ImageYF) = MutableImageYF(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageYF) = MutableImageYF(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageYF) = ImageYF(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageYF) = ImageYF(image.width, image.height, image.data)
    fun newMutableYF(width: Int, height: Int) = MutableImageYF(width, height, FloatArray(length(width, height, 1)))
    fun createMutableImage(width: Int, height: Int, background: Float): MutableImageYF {
        val image = newMutableYF(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 1
        }
        return image
    }

    fun thawImage(image: ImageYA8) = MutableImageYA8(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageYA8) = MutableImageYA8(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageYA8) = ImageYA8(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageYA8) = ImageYA8(image.width, image.height, image.data)
    fun newMutableYA8(width: Int, height: Int) = MutableImageYA8(width, height, ByteArray(length(width, height, 2)))
    fun createMutableImage(width: Int, height: Int, background: PixelYA8): MutableImageYA8 {
        val image = newMutableYA8(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 2
        }
        return image
    }

    fun thawImage(image: ImageYA16) = MutableImageYA16(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageYA16) = MutableImageYA16(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageYA16) = ImageYA16(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageYA16) = ImageYA16(image.width, image.height, image.data)
    fun newMutableYA16(width: Int, height: Int) = MutableImageYA16(width, height, ShortArray(length(width, height, 2)))
    fun createMutableImage(width: Int, height: Int, background: PixelYA16): MutableImageYA16 {
        val image = newMutableYA16(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 2
        }
        return image
    }

    fun thawImage(image: ImageRGB8) = MutableImageRGB8(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageRGB8) = MutableImageRGB8(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageRGB8) = ImageRGB8(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageRGB8) = ImageRGB8(image.width, image.height, image.data)
    fun newMutableRGB8(width: Int, height: Int) = MutableImageRGB8(width, height, ByteArray(length(width, height, 3)))
    fun createMutableImage(width: Int, height: Int, background: PixelRGB8): MutableImageRGB8 {
        val image = newMutableRGB8(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 3
        }
        return image
    }

    fun thawImage(image: ImageRGB16) = MutableImageRGB16(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageRGB16) = MutableImageRGB16(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageRGB16) = ImageRGB16(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageRGB16) = ImageRGB16(image.width, image.height, image.data)
    fun newMutableRGB16(width: Int, height: Int) = MutableImageRGB16(width, height, ShortArray(length(width, height, 3)))
    fun createMutableImage(width: Int, height: Int, background: PixelRGB16): MutableImageRGB16 {
        val image = newMutableRGB16(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 3
        }
        return image
    }

    fun thawImage(image: ImageRGBF) = MutableImageRGBF(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageRGBF) = MutableImageRGBF(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageRGBF) = ImageRGBF(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageRGBF) = ImageRGBF(image.width, image.height, image.data)
    fun newMutableRGBF(width: Int, height: Int) = MutableImageRGBF(width, height, FloatArray(length(width, height, 3)))
    fun createMutableImage(width: Int, height: Int, background: PixelRGBF): MutableImageRGBF {
        val image = newMutableRGBF(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 3
        }
        return image
    }

    fun thawImage(image: ImageRGBA8) = MutableImageRGBA8(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageRGBA8) = MutableImageRGBA8(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageRGBA8) = ImageRGBA8(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageRGBA8) = ImageRGBA8(image.width, image.height, image.data)
    fun newMutableRGBA8(width: Int, height: Int) = MutableImageRGBA8(width, height, ByteArray(length(width, height, 4)))
    fun createMutableImage(width: Int, height: Int, background: PixelRGBA8): MutableImageRGBA8 {
        val image = newMutableRGBA8(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 4
        }
        return image
    }

    fun thawImage(image: ImageRGBA16) = MutableImageRGBA16(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageRGBA16) = MutableImageRGBA16(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageRGBA16) = ImageRGBA16(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageRGBA16) = ImageRGBA16(image.width, image.height, image.data)
    fun newMutableRGBA16(width: Int, height: Int) = MutableImageRGBA16(width, height, ShortArray(length(width, height, 4)))
    fun createMutableImage(width: Int, height: Int, background: PixelRGBA16): MutableImageRGBA16 {
        val image = newMutableRGBA16(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 4
        }
        return image
    }

    fun thawImage(image: ImageYCbCr8) = MutableImageYCbCr8(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageYCbCr8) = MutableImageYCbCr8(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageYCbCr8) = ImageYCbCr8(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageYCbCr8) = ImageYCbCr8(image.width, image.height, image.data)
    fun newMutableYCbCr8(width: Int, height: Int) = MutableImageYCbCr8(width, height, ByteArray(length(width, height, 3)))
    fun createMutableImage(width: Int, height: Int, background: PixelYCbCr8): MutableImageYCbCr8 {
        val image = newMutableYCbCr8(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 3
        }
        return image
    }

    fun thawImage(image: ImageYCbCrK8) = MutableImageYCbCrK8(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageYCbCrK8) = MutableImageYCbCrK8(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageYCbCrK8) = ImageYCbCrK8(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageYCbCrK8) = ImageYCbCrK8(image.width, image.height, image.data)
    fun newMutableYCbCrK8(width: Int, height: Int) = MutableImageYCbCrK8(width, height, ByteArray(length(width, height, 4)))
    fun createMutableImage(width: Int, height: Int, background: PixelYCbCrK8): MutableImageYCbCrK8 {
        val image = newMutableYCbCrK8(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 4
        }
        return image
    }

    fun thawImage(image: ImageCMYK8) = MutableImageCMYK8(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageCMYK8) = MutableImageCMYK8(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageCMYK8) = ImageCMYK8(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageCMYK8) = ImageCMYK8(image.width, image.height, image.data)
    fun newMutableCMYK8(width: Int, height: Int) = MutableImageCMYK8(width, height, ByteArray(length(width, height, 4)))
    fun createMutableImage(width: Int, height: Int, background: PixelCMYK8): MutableImageCMYK8 {
        val image = newMutableCMYK8(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 4
        }
        return image
    }

    fun thawImage(image: ImageCMYK16) = MutableImageCMYK16(image.width, image.height, image.data.copyOf())
    fun unsafeThawImage(image: ImageCMYK16) = MutableImageCMYK16(image.width, image.height, image.data)
    fun freezeImage(image: MutableImageCMYK16) = ImageCMYK16(image.width, image.height, image.data.copyOf())
    fun unsafeFreezeImage(image: MutableImageCMYK16) = ImageCMYK16(image.width, image.height, image.data)
    fun newMutableCMYK16(width: Int, height: Int) = MutableImageCMYK16(width, height, ShortArray(length(width, height, 4)))
    fun createMutableImage(width: Int, height: Int, background: PixelCMYK16): MutableImageCMYK16 {
        val image = newMutableCMYK16(width, height)
        var index = 0L
        for (y in 0..<height) for (x in 0..<width) {
            ImagePixelAccess.unsafeWritePixel(image.data, index, background)
            index += 4
        }
        return image
    }

}
