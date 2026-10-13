// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal object ImageHdr {
    fun gammaCorrection(gamma: () -> Float, image: RasterImage<PixelRGBF, Float, FloatArray>): RasterImage<PixelRGBF, Float, FloatArray> {
        val exponent = lazy { 1f / gamma() }
        return RasterOps.pixelMapLazy(image, RasterCodecs.RGBF) { value ->
            val p = value()
            fun correct(v: Float) = SystemFloatMath.pow(v, exponent.value)
            PixelRGBF(correct(p.red), correct(p.green), correct(p.blue))
        }
    }
    fun toneMapping(exposure: () -> Float, image: RasterImage<PixelRGBF, Float, FloatArray>) = RasterImage(image.width, image.height, RasterCodecs.RGBF, lazy {
        val src = image.vector
        val coefficient = lazy {
            val e = exposure()
            val maxBrightness = RasterOps.pixelFold(image, 0f) { maximum, _, _, pixel ->
                val luma = ImageLuma.computeLuma(pixel)
                // Original Ord Float max selects its right operand on <=,
                // ignores a NaN candidate, and can preserve negative zero.
                if (maximum <= luma) luma else maximum
            }
            e * (e / maxBrightness + 1f) / (e + 1f)
        }
        val data = FloatArray(RasterVector.allocationLength(src.length))
        for (i in data.indices) data[i] = src[i.toLong()] * coefficient.value
        RasterVector(RasterStorages.Floats, data)
    })
}
