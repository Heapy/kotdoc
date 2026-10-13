// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal data class ImageY32(val width: Int, val height: Int, val data: IntArray)

internal sealed interface ColorPlane
internal sealed interface RgbPlane : ColorPlane
internal sealed interface RgbaPlane : ColorPlane
internal sealed interface YaPlane : ColorPlane
internal sealed interface YCbCrPlane : ColorPlane
internal sealed interface CmykPlane : ColorPlane
internal data object PlaneRed : RgbPlane, RgbaPlane
internal data object PlaneGreen : RgbPlane, RgbaPlane
internal data object PlaneBlue : RgbPlane, RgbaPlane
internal data object PlaneAlpha : RgbaPlane, YaPlane
internal data object PlaneLuma : YaPlane, YCbCrPlane
internal data object PlaneCb : YCbCrPlane
internal data object PlaneCr : YCbCrPlane
internal data object PlaneCyan : CmykPlane
internal data object PlaneMagenta : CmykPlane
internal data object PlaneYellow : CmykPlane
internal data object PlaneBlack : CmykPlane

internal object ImagePlanes {
    fun toComponentIndex(plane: CmykPlane): Int = when (plane) { PlaneCyan -> 0; PlaneMagenta -> 1; PlaneYellow -> 2; PlaneBlack -> 3 }
    fun toComponentIndex(plane: YCbCrPlane): Int = when (plane) { PlaneLuma -> 0; PlaneCb -> 1; PlaneCr -> 2 }
    fun toComponentIndex(plane: YaPlane): Int = when (plane) { PlaneLuma -> 0; PlaneAlpha -> 1 }
    fun toComponentIndex(plane: RgbaPlane): Int = when (plane) { PlaneRed -> 0; PlaneGreen -> 1; PlaneBlue -> 2; PlaneAlpha -> 3 }
    fun toComponentIndex(plane: RgbPlane): Int = when (plane) { PlaneRed -> 0; PlaneGreen -> 1; PlaneBlue -> 2 }
    private fun check(component: Long, count: Int) {
        if (component >= count) error("extractComponent : invalid component index ($component, max:$count)")
    }
    private fun size(width: Int, height: Int) = Math.toIntExact(width.toLong() * height)
    private fun extract(component: Long, count: Int, width: Int, height: Int, data: ByteArray): ByteArray {
        check(component, count)
        return ByteArray(size(width, height)) { data[Math.toIntExact(component + it.toLong() * count)] }
    }
    private fun extract(component: Long, count: Int, width: Int, height: Int, data: ShortArray): ShortArray {
        check(component, count)
        return ShortArray(size(width, height)) { data[Math.toIntExact(component + it.toLong() * count)] }
    }
    private fun extract(component: Long, count: Int, width: Int, height: Int, data: IntArray): IntArray {
        check(component, count)
        return IntArray(size(width, height)) { data[Math.toIntExact(component + it.toLong() * count)] }
    }
    private fun extract(component: Long, count: Int, width: Int, height: Int, data: FloatArray): FloatArray {
        check(component, count)
        return FloatArray(size(width, height)) { data[Math.toIntExact(component + it.toLong() * count)] }
    }

    fun unsafeExtractComponent(component: Long, image: ImageY8) = ImageY8(image.width, image.height, extract(component, 1, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageY16) = ImageY16(image.width, image.height, extract(component, 1, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageY32) = ImageY32(image.width, image.height, extract(component, 1, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageYF) = ImageYF(image.width, image.height, extract(component, 1, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageYA8) = ImageY8(image.width, image.height, extract(component, 2, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageYA16) = ImageY16(image.width, image.height, extract(component, 2, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageRGB8) = ImageY8(image.width, image.height, extract(component, 3, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageRGB16) = ImageY16(image.width, image.height, extract(component, 3, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageRGBF) = ImageYF(image.width, image.height, extract(component, 3, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageRGBA8) = ImageY8(image.width, image.height, extract(component, 4, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageRGBA16) = ImageY16(image.width, image.height, extract(component, 4, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageYCbCr8) = ImageY8(image.width, image.height, extract(component, 3, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageYCbCrK8) = ImageY8(image.width, image.height, extract(component, 4, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageCMYK8) = ImageY8(image.width, image.height, extract(component, 4, image.width, image.height, image.data))

    fun unsafeExtractComponent(component: Long, image: ImageCMYK16) = ImageY16(image.width, image.height, extract(component, 4, image.width, image.height, image.data))

    fun extractComponent(plane: YaPlane, image: ImageYA8) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: YaPlane, image: ImageYA16) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: RgbPlane, image: ImageRGB8) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: RgbPlane, image: ImageRGB16) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: RgbPlane, image: ImageRGBF) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: RgbaPlane, image: ImageRGBA8) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: RgbaPlane, image: ImageRGBA16) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: YCbCrPlane, image: ImageYCbCr8) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: CmykPlane, image: ImageCMYK8) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)

    fun extractComponent(plane: CmykPlane, image: ImageCMYK16) = unsafeExtractComponent(toComponentIndex(plane).toLong(), image)
}
