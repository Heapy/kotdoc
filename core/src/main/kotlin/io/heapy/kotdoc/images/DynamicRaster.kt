// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal interface DynamicRasterVisitor<R> {
    fun <P : Any, C : Any, A : Any> visit(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>): R
}
internal interface DynamicRasterTransform {
    fun <P : Any, C : Any, A : Any> transform(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>): RasterImage<P, C, A>
}

internal sealed class DynamicRaster {
    abstract val tag: String
    abstract fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>): R
    abstract fun dynamicPixelMap(transform: DynamicRasterTransform): DynamicRaster
    fun force() = dynamicMap(object : DynamicRasterVisitor<Unit> {
        override fun <P : Any, C : Any, A : Any> visit(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>) { image().vector }
    })
    class Y8(val image: Lazy<RasterImage<UByte, UByte, ByteArray>>) : DynamicRaster() {
        override val tag = "y8"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.Y8) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = Y8(lazy { transform.transform(RasterCodecs.Y8) { image.value } })
        override fun equals(other: Any?) = other is Y8 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class Y16(val image: Lazy<RasterImage<UShort, UShort, ShortArray>>) : DynamicRaster() {
        override val tag = "y16"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.Y16) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = Y16(lazy { transform.transform(RasterCodecs.Y16) { image.value } })
        override fun equals(other: Any?) = other is Y16 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class Y32(val image: Lazy<RasterImage<UInt, UInt, IntArray>>) : DynamicRaster() {
        override val tag = "y32"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.Y32) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = Y32(lazy { transform.transform(RasterCodecs.Y32) { image.value } })
        override fun equals(other: Any?) = other is Y32 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class YF(val image: Lazy<RasterImage<Float, Float, FloatArray>>) : DynamicRaster() {
        override val tag = "yf"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.YF) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = YF(lazy { transform.transform(RasterCodecs.YF) { image.value } })
        override fun equals(other: Any?) = other is YF && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class YA8(val image: Lazy<RasterImage<PixelYA8, UByte, ByteArray>>) : DynamicRaster() {
        override val tag = "ya8"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.YA8) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = YA8(lazy { transform.transform(RasterCodecs.YA8) { image.value } })
        override fun equals(other: Any?) = other is YA8 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class YA16(val image: Lazy<RasterImage<PixelYA16, UShort, ShortArray>>) : DynamicRaster() {
        override val tag = "ya16"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.YA16) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = YA16(lazy { transform.transform(RasterCodecs.YA16) { image.value } })
        override fun equals(other: Any?) = other is YA16 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class RGB8(val image: Lazy<RasterImage<PixelRGB8, UByte, ByteArray>>) : DynamicRaster() {
        override val tag = "rgb8"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.RGB8) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = RGB8(lazy { transform.transform(RasterCodecs.RGB8) { image.value } })
        override fun equals(other: Any?) = other is RGB8 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class RGB16(val image: Lazy<RasterImage<PixelRGB16, UShort, ShortArray>>) : DynamicRaster() {
        override val tag = "rgb16"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.RGB16) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = RGB16(lazy { transform.transform(RasterCodecs.RGB16) { image.value } })
        override fun equals(other: Any?) = other is RGB16 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class RGBF(val image: Lazy<RasterImage<PixelRGBF, Float, FloatArray>>) : DynamicRaster() {
        override val tag = "rgbf"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.RGBF) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = RGBF(lazy { transform.transform(RasterCodecs.RGBF) { image.value } })
        override fun equals(other: Any?) = other is RGBF && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class RGBA8(val image: Lazy<RasterImage<PixelRGBA8, UByte, ByteArray>>) : DynamicRaster() {
        override val tag = "rgba8"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.RGBA8) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = RGBA8(lazy { transform.transform(RasterCodecs.RGBA8) { image.value } })
        override fun equals(other: Any?) = other is RGBA8 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class RGBA16(val image: Lazy<RasterImage<PixelRGBA16, UShort, ShortArray>>) : DynamicRaster() {
        override val tag = "rgba16"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.RGBA16) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = RGBA16(lazy { transform.transform(RasterCodecs.RGBA16) { image.value } })
        override fun equals(other: Any?) = other is RGBA16 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class YCbCr8(val image: Lazy<RasterImage<PixelYCbCr8, UByte, ByteArray>>) : DynamicRaster() {
        override val tag = "ycbcr8"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.YCbCr8) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = YCbCr8(lazy { transform.transform(RasterCodecs.YCbCr8) { image.value } })
        override fun equals(other: Any?) = other is YCbCr8 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class CMYK8(val image: Lazy<RasterImage<PixelCMYK8, UByte, ByteArray>>) : DynamicRaster() {
        override val tag = "cmyk8"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.CMYK8) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = CMYK8(lazy { transform.transform(RasterCodecs.CMYK8) { image.value } })
        override fun equals(other: Any?) = other is CMYK8 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
    class CMYK16(val image: Lazy<RasterImage<PixelCMYK16, UShort, ShortArray>>) : DynamicRaster() {
        override val tag = "cmyk16"
        override fun <R> dynamicMap(visitor: DynamicRasterVisitor<R>) = visitor.visit(RasterCodecs.CMYK16) { image.value }
        override fun dynamicPixelMap(transform: DynamicRasterTransform) = CMYK16(lazy { transform.transform(RasterCodecs.CMYK16) { image.value } })
        override fun equals(other: Any?) = other is CMYK16 && image.value == other.image.value
        override fun hashCode() = tag.hashCode() * 31 + image.value.hashCode()
    }
}

internal typealias RasterRgbPalette = RasterImage<PixelRGB8, UByte, ByteArray>

/** Palette size and vector are strict; their contents need not match the size. */
internal class RasterPalette<P : Any, C : Any, A : Any>(val size: Long, val codec: RasterCodec<P, C, A>, val vector: RasterVector<C, A>) {
    fun palettedAsImage() = RasterImage(size, 1, codec, vector)
}

internal sealed interface PalettedRaster {
    fun palettedToTrueColor(): DynamicRaster
    class TrueColorImage(image: () -> DynamicRaster) : PalettedRaster {
        private val image = lazy(image)
        override fun palettedToTrueColor() = image.value
    }
    class Y8(val indices: Lazy<RasterImage<UByte, UByte, ByteArray>>, val palette: Lazy<RasterPalette<UByte, UByte, ByteArray>>) : PalettedRaster {
        override fun palettedToTrueColor() = DynamicRaster.Y8(lazy {
            RasterOps.pixelMapLazy(indices.value, RasterCodecs.Y8) { index ->
                val at = index().toLong() * 1
                RasterOps.readAt(RasterCodecs.Y8, palette.value.vector, at, false)
            }
        })
    }
    class RGB8(val indices: Lazy<RasterImage<UByte, UByte, ByteArray>>, val palette: Lazy<RasterPalette<PixelRGB8, UByte, ByteArray>>) : PalettedRaster {
        override fun palettedToTrueColor() = DynamicRaster.RGB8(lazy {
            RasterOps.pixelMapLazy(indices.value, RasterCodecs.RGB8) { index ->
                val at = index().toLong() * 3
                RasterOps.readAt(RasterCodecs.RGB8, palette.value.vector, at, false)
            }
        })
    }
    class RGBA8(val indices: Lazy<RasterImage<UByte, UByte, ByteArray>>, val palette: Lazy<RasterPalette<PixelRGBA8, UByte, ByteArray>>) : PalettedRaster {
        override fun palettedToTrueColor() = DynamicRaster.RGBA8(lazy {
            RasterOps.pixelMapLazy(indices.value, RasterCodecs.RGBA8) { index ->
                val at = index().toLong() * 4
                RasterOps.readAt(RasterCodecs.RGBA8, palette.value.vector, at, false)
            }
        })
    }
    class RGB16(val indices: Lazy<RasterImage<UByte, UByte, ByteArray>>, val palette: Lazy<RasterPalette<PixelRGB16, UShort, ShortArray>>) : PalettedRaster {
        override fun palettedToTrueColor() = DynamicRaster.RGB16(lazy {
            RasterOps.pixelMapLazy(indices.value, RasterCodecs.RGB16) { index ->
                val at = index().toLong() * 3
                RasterOps.readAt(RasterCodecs.RGB16, palette.value.vector, at, false)
            }
        })
    }
}
