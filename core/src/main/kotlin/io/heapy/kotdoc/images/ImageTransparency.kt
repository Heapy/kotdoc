// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal object ImageTransparency {
    fun dropTransparency(pixel: PixelYA8) = pixel.luma
    fun dropTransparency(pixel: PixelYA16) = pixel.luma
    fun dropTransparency(pixel: PixelRGBA8) = PixelRGB8(pixel.red, pixel.green, pixel.blue)
    fun dropTransparency(pixel: PixelRGBA16) = PixelRGB16(pixel.red, pixel.green, pixel.blue)
    fun pixelOpacity(pixel: PixelYA8) = ImagePixelOps.pixelOpacity(pixel)
    fun pixelOpacity(pixel: PixelYA16) = ImagePixelOps.pixelOpacity(pixel)
    fun pixelOpacity(pixel: PixelRGBA8) = ImagePixelOps.pixelOpacity(pixel)
    fun pixelOpacity(pixel: PixelRGBA16) = ImagePixelOps.pixelOpacity(pixel)
    @Deprecated("please use 'pixelOpacity' instead")
    fun getTransparency(pixel: PixelYA8) = pixel.alpha
    @Deprecated("please use 'pixelOpacity' instead")
    fun getTransparency(pixel: PixelYA16) = pixel.alpha
    @Deprecated("please use 'pixelOpacity' instead")
    fun getTransparency(pixel: PixelRGBA8) = pixel.alpha
    @Deprecated("please use 'pixelOpacity' instead")
    fun getTransparency(pixel: PixelRGBA16) = pixel.alpha

    private fun drop(width: Int, height: Int, input: ByteArray, stride: Int): ByteArray {
        val outStride = stride - 1
        val data = ByteArray(Math.toIntExact(width.toLong() * height * outStride))
        var pixel = 0
        for (y in 0..<height) for (x in 0..<width) {
            for (c in 0..<outStride) data[pixel * outStride + c] = input[pixel * stride + c]
            pixel++
        }
        return data
    }
    private fun drop(width: Int, height: Int, input: ShortArray, stride: Int): ShortArray {
        val outStride = stride - 1
        val data = ShortArray(Math.toIntExact(width.toLong() * height * outStride))
        var pixel = 0
        for (y in 0..<height) for (x in 0..<width) {
            for (c in 0..<outStride) data[pixel * outStride + c] = input[pixel * stride + c]
            pixel++
        }
        return data
    }
    fun dropAlphaLayer(image: ImageYA8) = ImageY8(image.width, image.height, drop(image.width, image.height, image.data, 2))
    fun dropAlphaLayer(image: ImageYA16) = ImageY16(image.width, image.height, drop(image.width, image.height, image.data, 2))
    fun dropAlphaLayer(image: ImageRGBA8) = ImageRGB8(image.width, image.height, drop(image.width, image.height, image.data, 4))
    fun dropAlphaLayer(image: ImageRGBA16) = ImageRGB16(image.width, image.height, drop(image.width, image.height, image.data, 4))
}
