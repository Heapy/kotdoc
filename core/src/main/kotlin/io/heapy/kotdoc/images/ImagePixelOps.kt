// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

@Suppress("UNUSED_PARAMETER")
internal object ImagePixelOps {
    fun componentCount(pixel: UByte): Int = 1
    fun pixelOpacity(pixel: UByte): UByte = 255u
    fun mixWith(f: (Int, UByte, UByte) -> UByte, left: UByte, right: UByte): UByte = f(0, left, right)
    fun mixWithAlpha(f: (Int, UByte, UByte) -> UByte, alpha: (UByte, UByte) -> UByte, left: UByte, right: UByte): UByte = mixWith(f, left, right)
    fun colorMap(f: (UByte) -> UByte, pixel: UByte): UByte = f(pixel)

    fun componentCount(pixel: UShort): Int = 1
    fun pixelOpacity(pixel: UShort): UShort = 65535u
    fun mixWith(f: (Int, UShort, UShort) -> UShort, left: UShort, right: UShort): UShort = f(0, left, right)
    fun mixWithAlpha(f: (Int, UShort, UShort) -> UShort, alpha: (UShort, UShort) -> UShort, left: UShort, right: UShort): UShort = mixWith(f, left, right)
    fun colorMap(f: (UShort) -> UShort, pixel: UShort): UShort = f(pixel)

    fun componentCount(pixel: UInt): Int = 1
    fun pixelOpacity(pixel: UInt): UInt = UInt.MAX_VALUE
    fun mixWith(f: (Int, UInt, UInt) -> UInt, left: UInt, right: UInt): UInt = f(0, left, right)
    fun mixWithAlpha(f: (Int, UInt, UInt) -> UInt, alpha: (UInt, UInt) -> UInt, left: UInt, right: UInt): UInt = mixWith(f, left, right)
    fun colorMap(f: (UInt) -> UInt, pixel: UInt): UInt = f(pixel)

    fun componentCount(pixel: Float): Int = 1
    fun pixelOpacity(pixel: Float): Float = 1f
    fun mixWith(f: (Int, Float, Float) -> Float, left: Float, right: Float): Float = f(0, left, right)
    fun mixWithAlpha(f: (Int, Float, Float) -> Float, alpha: (Float, Float) -> Float, left: Float, right: Float): Float = mixWith(f, left, right)
    fun colorMap(f: (Float) -> Float, pixel: Float): Float = f(pixel)

    fun componentCount(pixel: PixelYA8): Int = 2
    fun pixelOpacity(pixel: PixelYA8): UByte = pixel.alpha
    fun mixWith(f: (Int, UByte, UByte) -> UByte, left: PixelYA8, right: PixelYA8): PixelYA8 = PixelYA8(f(0, left.luma, right.luma), f(1, left.alpha, right.alpha))
    fun mixWithAlpha(f: (Int, UByte, UByte) -> UByte, alpha: (UByte, UByte) -> UByte, left: PixelYA8, right: PixelYA8): PixelYA8 = mixWith(f, left, right)
    fun colorMap(f: (UByte) -> UByte, pixel: PixelYA8): PixelYA8 = PixelYA8(f(pixel.luma), f(pixel.alpha))

    fun componentCount(pixel: PixelYA16): Int = 2
    fun pixelOpacity(pixel: PixelYA16): UShort = pixel.alpha
    fun mixWith(f: (Int, UShort, UShort) -> UShort, left: PixelYA16, right: PixelYA16): PixelYA16 = PixelYA16(f(0, left.luma, right.luma), f(1, left.alpha, right.alpha))
    fun mixWithAlpha(f: (Int, UShort, UShort) -> UShort, alpha: (UShort, UShort) -> UShort, left: PixelYA16, right: PixelYA16): PixelYA16 = PixelYA16(f(0, left.luma, right.luma), alpha(left.alpha, right.alpha))
    fun colorMap(f: (UShort) -> UShort, pixel: PixelYA16): PixelYA16 = PixelYA16(f(pixel.luma), f(pixel.alpha))

    fun componentCount(pixel: PixelRGB8): Int = 3
    fun pixelOpacity(pixel: PixelRGB8): UByte = 255u
    fun mixWith(f: (Int, UByte, UByte) -> UByte, left: PixelRGB8, right: PixelRGB8): PixelRGB8 = PixelRGB8(f(0, left.red, right.red), f(1, left.green, right.green), f(2, left.blue, right.blue))
    fun mixWithAlpha(f: (Int, UByte, UByte) -> UByte, alpha: (UByte, UByte) -> UByte, left: PixelRGB8, right: PixelRGB8): PixelRGB8 = mixWith(f, left, right)
    fun colorMap(f: (UByte) -> UByte, pixel: PixelRGB8): PixelRGB8 = PixelRGB8(f(pixel.red), f(pixel.green), f(pixel.blue))

    fun componentCount(pixel: PixelRGB16): Int = 3
    fun pixelOpacity(pixel: PixelRGB16): UShort = 65535u
    fun mixWith(f: (Int, UShort, UShort) -> UShort, left: PixelRGB16, right: PixelRGB16): PixelRGB16 = PixelRGB16(f(0, left.red, right.red), f(1, left.green, right.green), f(2, left.blue, right.blue))
    fun mixWithAlpha(f: (Int, UShort, UShort) -> UShort, alpha: (UShort, UShort) -> UShort, left: PixelRGB16, right: PixelRGB16): PixelRGB16 = mixWith(f, left, right)
    fun colorMap(f: (UShort) -> UShort, pixel: PixelRGB16): PixelRGB16 = PixelRGB16(f(pixel.red), f(pixel.green), f(pixel.blue))

    fun componentCount(pixel: PixelRGBF): Int = 3
    fun pixelOpacity(pixel: PixelRGBF): Float = 1f
    fun mixWith(f: (Int, Float, Float) -> Float, left: PixelRGBF, right: PixelRGBF): PixelRGBF = PixelRGBF(f(0, left.red, right.red), f(1, left.green, right.green), f(2, left.blue, right.blue))
    fun mixWithAlpha(f: (Int, Float, Float) -> Float, alpha: (Float, Float) -> Float, left: PixelRGBF, right: PixelRGBF): PixelRGBF = mixWith(f, left, right)
    fun colorMap(f: (Float) -> Float, pixel: PixelRGBF): PixelRGBF = PixelRGBF(f(pixel.red), f(pixel.green), f(pixel.blue))

    fun componentCount(pixel: PixelRGBA8): Int = 4
    fun pixelOpacity(pixel: PixelRGBA8): UByte = pixel.alpha
    fun mixWith(f: (Int, UByte, UByte) -> UByte, left: PixelRGBA8, right: PixelRGBA8): PixelRGBA8 = PixelRGBA8(f(0, left.red, right.red), f(1, left.green, right.green), f(2, left.blue, right.blue), f(3, left.alpha, right.alpha))
    fun mixWithAlpha(f: (Int, UByte, UByte) -> UByte, alpha: (UByte, UByte) -> UByte, left: PixelRGBA8, right: PixelRGBA8): PixelRGBA8 = PixelRGBA8(f(0, left.red, right.red), f(1, left.green, right.green), f(2, left.blue, right.blue), alpha(left.alpha, right.alpha))
    fun colorMap(f: (UByte) -> UByte, pixel: PixelRGBA8): PixelRGBA8 = PixelRGBA8(f(pixel.red), f(pixel.green), f(pixel.blue), f(pixel.alpha))

    fun componentCount(pixel: PixelRGBA16): Int = 4
    fun pixelOpacity(pixel: PixelRGBA16): UShort = pixel.alpha
    fun mixWith(f: (Int, UShort, UShort) -> UShort, left: PixelRGBA16, right: PixelRGBA16): PixelRGBA16 = PixelRGBA16(f(0, left.red, right.red), f(1, left.green, right.green), f(2, left.blue, right.blue), f(3, left.alpha, right.alpha))
    fun mixWithAlpha(f: (Int, UShort, UShort) -> UShort, alpha: (UShort, UShort) -> UShort, left: PixelRGBA16, right: PixelRGBA16): PixelRGBA16 = PixelRGBA16(f(0, left.red, right.red), f(1, left.green, right.green), f(2, left.blue, right.blue), alpha(left.alpha, right.alpha))
    fun colorMap(f: (UShort) -> UShort, pixel: PixelRGBA16): PixelRGBA16 = PixelRGBA16(f(pixel.red), f(pixel.green), f(pixel.blue), f(pixel.alpha))

    fun componentCount(pixel: PixelYCbCr8): Int = 3
    fun pixelOpacity(pixel: PixelYCbCr8): UByte = 255u
    fun mixWith(f: (Int, UByte, UByte) -> UByte, left: PixelYCbCr8, right: PixelYCbCr8): PixelYCbCr8 = PixelYCbCr8(f(0, left.luma, right.luma), f(1, left.cb, right.cb), f(2, left.cr, right.cr))
    fun mixWithAlpha(f: (Int, UByte, UByte) -> UByte, alpha: (UByte, UByte) -> UByte, left: PixelYCbCr8, right: PixelYCbCr8): PixelYCbCr8 = mixWith(f, left, right)
    fun colorMap(f: (UByte) -> UByte, pixel: PixelYCbCr8): PixelYCbCr8 = PixelYCbCr8(f(pixel.luma), f(pixel.cb), f(pixel.cr))

    fun componentCount(pixel: PixelYCbCrK8): Int = 4
    fun pixelOpacity(pixel: PixelYCbCrK8): UByte = 255u
    fun mixWith(f: (Int, UByte, UByte) -> UByte, left: PixelYCbCrK8, right: PixelYCbCrK8): PixelYCbCrK8 = PixelYCbCrK8(f(0, left.luma, right.luma), f(1, left.cb, right.cb), f(2, left.cr, right.cr), f(3, left.black, right.black))
    fun mixWithAlpha(f: (Int, UByte, UByte) -> UByte, alpha: (UByte, UByte) -> UByte, left: PixelYCbCrK8, right: PixelYCbCrK8): PixelYCbCrK8 = mixWith(f, left, right)
    fun colorMap(f: (UByte) -> UByte, pixel: PixelYCbCrK8): PixelYCbCrK8 = PixelYCbCrK8(f(pixel.luma), f(pixel.cb), f(pixel.cr), f(pixel.black))

    fun componentCount(pixel: PixelCMYK8): Int = 4
    fun pixelOpacity(pixel: PixelCMYK8): UByte = 255u
    fun mixWith(f: (Int, UByte, UByte) -> UByte, left: PixelCMYK8, right: PixelCMYK8): PixelCMYK8 = PixelCMYK8(f(0, left.cyan, right.cyan), f(1, left.magenta, right.magenta), f(2, left.yellow, right.yellow), f(3, left.black, right.black))
    fun mixWithAlpha(f: (Int, UByte, UByte) -> UByte, alpha: (UByte, UByte) -> UByte, left: PixelCMYK8, right: PixelCMYK8): PixelCMYK8 = mixWith(f, left, right)
    fun colorMap(f: (UByte) -> UByte, pixel: PixelCMYK8): PixelCMYK8 = PixelCMYK8(f(pixel.cyan), f(pixel.magenta), f(pixel.yellow), f(pixel.black))

    fun componentCount(pixel: PixelCMYK16): Int = 4
    fun pixelOpacity(pixel: PixelCMYK16): UShort = 65535u
    fun mixWith(f: (Int, UShort, UShort) -> UShort, left: PixelCMYK16, right: PixelCMYK16): PixelCMYK16 = PixelCMYK16(f(0, left.cyan, right.cyan), f(1, left.magenta, right.magenta), f(2, left.yellow, right.yellow), f(3, left.black, right.black))
    fun mixWithAlpha(f: (Int, UShort, UShort) -> UShort, alpha: (UShort, UShort) -> UShort, left: PixelCMYK16, right: PixelCMYK16): PixelCMYK16 = mixWith(f, left, right)
    fun colorMap(f: (UShort) -> UShort, pixel: PixelCMYK16): PixelCMYK16 = PixelCMYK16(f(pixel.cyan), f(pixel.magenta), f(pixel.yellow), f(pixel.black))

}
