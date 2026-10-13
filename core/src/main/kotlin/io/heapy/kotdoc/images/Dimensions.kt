// Text.Pandoc.ImageSize: Copyright (C) 2011–2024 John MacFarlane.
// Translated from Pandoc 3.12.1, GPL-2.0-or-later.
package io.heapy.kotdoc.images

import io.heapy.kotdoc.ast.Attr
import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.formats.HaskellNumber
import java.math.BigInteger

sealed class Dimension {
    data class Pixel(val value: BigInteger) : Dimension()
    data class Centimeter(val value: Double) : Dimension()
    data class Millimeter(val value: Double) : Dimension()
    data class Inch(val value: Double) : Dimension()
    data class Point(val value: Double) : Dimension()
    data class Pica(val value: Double) : Dimension()
    data class Percent(val value: Double) : Dimension()
    data class Em(val value: Double) : Dimension()

    // Haskell-derived Eq uses IEEE (==): NaN is unequal even to itself, while
    // signed zeros compare equal. Kotlin data-class Double equality differs.
    final override fun equals(other: Any?): Boolean = when (this) {
        is Pixel -> other is Pixel && value == other.value
        is Centimeter -> other is Centimeter && value == other.value
        is Millimeter -> other is Millimeter && value == other.value
        is Inch -> other is Inch && value == other.value
        is Point -> other is Point && value == other.value
        is Pica -> other is Pica && value == other.value
        is Percent -> other is Percent && value == other.value
        is Em -> other is Em && value == other.value
    }
    final override fun hashCode(): Int {
        fun number(value: Double) = if (value == 0.0) 0 else value.hashCode()
        return when (this) {
            is Pixel -> value.hashCode()
            is Centimeter -> number(value)
            is Millimeter -> number(value)
            is Inch -> number(value)
            is Point -> number(value)
            is Pica -> number(value)
            is Percent -> number(value)
            is Em -> number(value)
        }
    }

    final override fun toString(): String = when (this) {
        is Pixel -> "${value}px"
        is Centimeter -> Dimensions.showFl(value) + "cm"
        is Millimeter -> Dimensions.showFl(value) + "mm"
        is Inch -> Dimensions.showFl(value) + "in"
        is Point -> Dimensions.showFl(value) + "pt"
        is Pica -> Dimensions.showFl(value) + "pc"
        is Percent -> HaskellDouble.show(value) + "%"
        is Em -> Dimensions.showFl(value) + "em"
    }
}
enum class Direction {
    Width, Height;
    override fun toString(): String = if (this == Width) "width" else "height"
}

/** WriterOptions integration supplies writerDpi; the underlying library default is 96. */
object Dimensions {
    fun showFl(value: Double): String = HaskellDouble.showFixed(value,5).trimEnd('0').removeSuffix(".")

    fun numUnit(text: String): Pair<Double, String>? {
        val n = text.indexOfFirst { it !in '0'..'9' && it != '.' }.let { if (it < 0) text.length else it }
        val value = HaskellNumber.readDouble(text.take(n)) ?: return null
        return value to text.drop(n).trimStart(HaskellNumber::isSpace)
    }
    fun lengthToDim(text: String): Dimension? {
        val (value,unit) = numUnit(text) ?: return null
        return when (unit) {
            "cm" -> Dimension.Centimeter(value)
            "mm" -> Dimension.Millimeter(value)
            "in", "inch" -> Dimension.Inch(value)
            "%" -> Dimension.Percent(value)
            "px", "" -> Dimension.Pixel(HaskellNumber.floor(value))
            "pt" -> Dimension.Point(value)
            "pc" -> Dimension.Pica(value)
            "em" -> Dimension.Em(value)
            else -> null
        }
    }
    fun dimension(direction: Direction, attr: Attr): Dimension? =
        attr.keyValues.firstOrNull { it.first == direction.toString() }?.second?.let(::lengthToDim)

    fun inInch(dim: Dimension, dpi: BigInteger = 96.toBigInteger()): Double = when (dim) {
        is Dimension.Pixel -> dim.value.toDouble() / dpi.toDouble()
        is Dimension.Centimeter -> dim.value * 0.3937007874
        is Dimension.Millimeter -> dim.value * 0.03937007874
        is Dimension.Inch -> dim.value
        is Dimension.Point -> dim.value / 72
        is Dimension.Pica -> dim.value / 6
        is Dimension.Percent -> 0.0
        is Dimension.Em -> dim.value * (11.0/64)
    }
    fun inPoints(dim: Dimension, dpi: BigInteger = 96.toBigInteger()): Double = 72 * inInch(dim,dpi)
    fun inEm(dim: Dimension, dpi: BigInteger = 96.toBigInteger()): Double = (64.0/11) * inInch(dim,dpi)
    fun inPixel(dim: Dimension, dpi: BigInteger = 96.toBigInteger()): BigInteger =
        if (dim is Dimension.Pixel) dim.value else HaskellNumber.floor(dpi.toDouble()*inInch(dim,dpi))
    fun showInInch(dim: Dimension, dpi: BigInteger = 96.toBigInteger()): String =
        if (dim is Dimension.Percent) "" else showFl(inInch(dim,dpi))
    fun showInPixel(dim: Dimension, dpi: BigInteger = 96.toBigInteger()): String =
        if (dim is Dimension.Percent) "" else inPixel(dim,dpi).toString()
    fun scaleDimension(factor: Double, dim: Dimension): Dimension = when (dim) {
        is Dimension.Pixel -> Dimension.Pixel(HaskellNumber.round(factor*dim.value.toDouble()))
        is Dimension.Centimeter -> Dimension.Centimeter(factor*dim.value)
        is Dimension.Millimeter -> Dimension.Millimeter(factor*dim.value)
        is Dimension.Inch -> Dimension.Inch(factor*dim.value)
        is Dimension.Point -> Dimension.Point(factor*dim.value)
        is Dimension.Pica -> Dimension.Pica(factor*dim.value)
        is Dimension.Percent -> Dimension.Percent(factor*dim.value)
        is Dimension.Em -> Dimension.Em(factor*dim.value)
    }
    fun desiredSizeInPoints(attr: Attr, size: ImageSize, dpi: BigInteger = 96.toBigInteger()): Pair<Double, Double> {
        fun get(dir: Direction): Double? = dimension(dir,attr)?.takeUnless { it is Dimension.Percent }?.let { inPoints(it,dpi) }
        val w = get(Direction.Width)
        val h = get(Direction.Height)
        val ratio = size.pxX.toDouble()/size.pxY.toDouble()
        return when {
            w != null && h != null -> w to h
            w != null -> w to (w/ratio)
            h != null -> (h*ratio) to h
            else -> size.sizeInPoints()
        }
    }
}
