// Text.Pandoc.ImageSize/Tests.ImageSize: John MacFarlane, GPL-2.0-or-later.
package io.heapy.kotdoc.images

import io.heapy.kotdoc.ast.Attr
import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.formats.HaskellNumber
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.nio.charset.CharacterCodingException
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class DimensionsTest {
    @TestFactory fun `document image sizes and dimension display match original Pandoc`(): Stream<DynamicTest> {
        val reader=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/dimensions.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val c=Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                val kind=c.getValue("kind").jsonPrimitive.content
                val dpi=c.getValue("dpi").jsonPrimitive.content.toBigInteger()
                if (kind=="dimension") {
                    val expected=Regex("width=([^,\\]]+)").find(c.getValue("latex").jsonPrimitive.content)?.groupValues?.get(1)
                    val dim=Dimensions.lengthToDim(c.getValue("width").jsonPrimitive.content)
                    val actual=when (dim) {
                        null -> null
                        is Dimension.Pixel -> Dimensions.showInInch(dim,dpi)+"in"
                        is Dimension.Percent -> Dimensions.showFl(dim.value/100)+"\\linewidth"
                        else -> dim.toString()
                    }
                    assertEquals(expected,actual)
                } else {
                    val bytes=c.getValue("hex").jsonPrimitive.content.hexToByteArray()
                    if ("exception" in c) {
                        assertFailsWith<CharacterCodingException> { DocumentImageSize.svgSize(bytes,dpi) }
                    } else {
                        val format=c.getValue("format").jsonPrimitive.content.takeUnless { it=="false" }
                        assertEquals(format,ImageHeaders.imageType(bytes)?.name?.lowercase())
                        if (format==kind) {
                            val actual=when(kind) {
                                "svg" -> DocumentImageSize.svgSize(bytes,dpi)
                                "eps" -> DocumentImageSize.epsSize(bytes)
                                "pdf" -> DocumentImageSize.pdfSize(bytes)
                                else -> error(kind)
                            }?.checkDpi()
                            val expected=c["size"]?.jsonArray?.map { it.jsonPrimitive.content.toBigInteger() }
                                ?.let { ImageSize(it[0],it[1],it[2],it[3]) }
                            assertEquals(expected,actual,c["error"]?.jsonPrimitive?.content)
                        }
                    }
                }
            }
        }.asStream().onClose { reader.close() }
    }

    @Test fun `original numUnit and lengthToDim examples`() {
        assertEquals(3.0 to "cm",Dimensions.numUnit("3cm"))
        assertEquals(3.0 to "cm",Dimensions.numUnit("3 cm"))
        assertEquals(3.5 to "",Dimensions.numUnit("3.5"))
        assertNull(Dimensions.numUnit("cm"))
        assertEquals(Dimension.Centimeter(3.0),Dimensions.lengthToDim("3 cm"))
        assertEquals(3.0 to "e2",Dimensions.numUnit("3e2"))
        assertNull(Dimensions.lengthToDim("3e2"))
        assertNull(Dimensions.numUnit(".5cm"))
        assertNull(Dimensions.numUnit("3.cm"))
        assertEquals("width",Direction.Width.toString())
        assertEquals("height",Direction.Height.toString())
    }
    @Test fun `scaling and flooring use distinct rounding and retain Integer magnitude`() {
        for ((factor,expected) in listOf(0.5 to 0,1.5 to 2,2.5 to 2,-0.5 to 0,-1.5 to -2,-2.5 to -2)) {
            assertEquals(Dimension.Pixel(expected.toBigInteger()),Dimensions.scaleDimension(factor,Dimension.Pixel(BigInteger.ONE)))
        }
        val huge=BigInteger.ONE.shiftLeft(100)
        assertEquals(Dimension.Pixel(huge),Dimensions.scaleDimension(1.0,Dimension.Pixel(huge)))
        assertEquals(huge,Dimensions.inPixel(Dimension.Pixel(huge),BigInteger.ZERO))
        assertEquals((-1).toBigInteger(),Dimensions.inPixel(Dimension.Inch(-0.1),BigInteger.ONE))
        assertEquals(BigInteger.ONE.shiftLeft(1024),HaskellNumber.floor(Double.POSITIVE_INFINITY))
        assertEquals(-BigInteger.ONE.shiftLeft(1024),HaskellNumber.floor(Double.NEGATIVE_INFINITY))
        assertEquals((-1).toBigInteger(),HaskellNumber.floor(-Double.MIN_VALUE))
        assertEquals(BigInteger.ZERO,HaskellNumber.floor(Double.MIN_VALUE))
        for (dim in listOf(Dimension.Centimeter(3.0),Dimension.Millimeter(3.0),Dimension.Inch(3.0),Dimension.Point(3.0),Dimension.Pica(3.0),Dimension.Percent(3.0),Dimension.Em(3.0))) {
            assertEquals(dim,Dimensions.scaleDimension(1.0,dim))
            assertEquals(Dimensions.inInch(dim)*2,Dimensions.inInch(Dimensions.scaleDimension(2.0,dim)))
        }
    }
    @Test fun `physical units preserve constants multiplication order and percent exceptions`() {
        assertEquals(2.54*0.3937007874,Dimensions.inInch(Dimension.Centimeter(2.54)))
        assertEquals(25.4*0.03937007874,Dimensions.inInch(Dimension.Millimeter(25.4)))
        assertEquals(1.0,Dimensions.inInch(Dimension.Point(72.0)))
        assertEquals(1.0,Dimensions.inInch(Dimension.Pica(6.0)))
        assertEquals(11.0/64,Dimensions.inInch(Dimension.Em(1.0)))
        assertEquals((64.0/11)*0.3937007874,Dimensions.inEm(Dimension.Centimeter(1.0)))
        assertEquals(72*0.3937007874,Dimensions.inPoints(Dimension.Centimeter(1.0)))
        assertEquals("",Dimensions.showInPixel(Dimension.Percent(50.0)))
        assertEquals("",Dimensions.showInInch(Dimension.Percent(50.0)))
        assertEquals("50.0%",Dimension.Percent(50.0).toString())
        assertEquals("1.0e-7%",Dimension.Percent(1e-7).toString())
        assertEquals("96",Dimensions.showInPixel(Dimension.Inch(1.0)))
    }
    @Test fun `desired size uses first exact attributes and pixel aspect ratio`() {
        val size=ImageSize(400.toBigInteger(),200.toBigInteger(),100.toBigInteger(),200.toBigInteger())
        fun attr(vararg kv: Pair<String,String>)=Attr(keyValues=kv.toList())
        assertEquals(288.0 to 72.0,Dimensions.desiredSizeInPoints(Attr(),size))
        assertEquals(72.0 to 36.0,Dimensions.desiredSizeInPoints(attr("width" to "96px"),size))
        assertEquals(144.0 to 72.0,Dimensions.desiredSizeInPoints(attr("height" to "1in"),size))
        assertEquals(72.0 to 144.0,Dimensions.desiredSizeInPoints(attr("width" to "1in","height" to "2in"),size))
        assertEquals(288.0 to 72.0,Dimensions.desiredSizeInPoints(attr("width" to "50%","height" to "100%"),size))
        assertNull(Dimensions.dimension(Direction.Width,attr("width" to "bad","width" to "3in")))
        assertNull(Dimensions.dimension(Direction.Width,attr("Width" to "3in")))
        assertTrue(Dimensions.desiredSizeInPoints(attr("width" to "0px"),ImageSize(BigInteger.ZERO,BigInteger.ZERO)).second.isNaN())
    }
    @Test fun `fixed formatting follows decimal ties and negative zero`() {
        for ((n,s) in listOf(1.234565 to "1.23456",1.234575 to "1.23458",0.000005 to "0",0.000015 to "0.00002",1.999995 to "2",-0.0 to "-0",-0.000001 to "-0")) assertEquals(s,Dimensions.showFl(n))
        assertEquals("NaN",Dimensions.showFl(Double.NaN))
        assertEquals("-Infinity",Dimensions.showFl(Double.NEGATIVE_INFINITY))
        assertEquals("2",HaskellDouble.showFixed(2.5,0))
        assertEquals("4",HaskellDouble.showFixed(3.5,-1))
        assertEquals("0.000",HaskellDouble.showFixed(0.0,3))
        assertEquals("-0.000",HaskellDouble.showFixed(-0.0,3))
    }
    @Test fun `dimension equality uses Haskell IEEE semantics`() {
        val constructors: List<(Double) -> Dimension> = listOf(Dimension::Centimeter,Dimension::Millimeter,
            Dimension::Inch,Dimension::Point,Dimension::Pica,Dimension::Percent,Dimension::Em)
        for (make in constructors) {
            val nan=make(Double.NaN)
            assertFalse(nan.equals(nan))
            assertFalse(nan==make(Double.NaN))
            assertEquals(make(0.0),make(-0.0))
            assertEquals(make(0.0).hashCode(),make(-0.0).hashCode())
        }
        assertNotEquals<Dimension>(Dimension.Inch(1.0),Dimension.Em(1.0))
    }
    @Test fun `SVG decoding and unbounded EPS values are checked directly`() {
        val svg="<svg width=\"30\" height=\"40\"/>".toByteArray()
        val bom=byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())
        assertEquals(30.toBigInteger(),DocumentImageSize.svgSize(bom+bom+svg)?.pxX)
        assertNull(DocumentImageSize.svgSize(bom+bom+bom+svg))
        val huge=BigInteger.ONE.shiftLeft(200)
        assertEquals(huge,DocumentImageSize.epsSize("%!PS EPSF\n%%BoundingBox: 0 0 $huge 1".toByteArray())?.pxX)
        assertEquals(BigInteger.ONE.shiftLeft(100),HaskellNumber.readInteger("(0x10000000000000000000000000)"))
        assertEquals(-2.0,HaskellNumber.readDouble(" ( - 0b10 ) "))
        assertNull(HaskellNumber.readDouble("-(2)"))
    }
}
