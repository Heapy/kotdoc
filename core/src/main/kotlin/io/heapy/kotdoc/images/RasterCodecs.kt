// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal object RasterStorages {
    data object Bytes : RasterStorage<UByte, ByteArray> {
        override fun length(data: ByteArray) = data.size
        override fun allocate(length: Long) = ByteArray(RasterVector.allocationLength(length))
        override fun copy(data: ByteArray, start: Int, length: Int) = data.copyOfRange(start, start + length)
        override fun get(data: ByteArray, index: Int): UByte = data[index].toUByte()
        override fun set(data: ByteArray, index: Int, value: UByte) { data[index] = value.toByte() }
        override fun bits(value: UByte): ULong = value.toULong()
        override fun fromBits(value: ULong): UByte = value.toUByte()
        override fun equal(a: UByte, b: UByte) = a == b
        override fun hash(value: UByte) = value.hashCode()
    }
    data object Shorts : RasterStorage<UShort, ShortArray> {
        override fun length(data: ShortArray) = data.size
        override fun allocate(length: Long) = ShortArray(RasterVector.allocationLength(length))
        override fun copy(data: ShortArray, start: Int, length: Int) = data.copyOfRange(start, start + length)
        override fun get(data: ShortArray, index: Int): UShort = data[index].toUShort()
        override fun set(data: ShortArray, index: Int, value: UShort) { data[index] = value.toShort() }
        override fun bits(value: UShort): ULong = value.toULong()
        override fun fromBits(value: ULong): UShort = value.toUShort()
        override fun equal(a: UShort, b: UShort) = a == b
        override fun hash(value: UShort) = value.hashCode()
    }
    data object Words : RasterStorage<UInt, IntArray> {
        override fun length(data: IntArray) = data.size
        override fun allocate(length: Long) = IntArray(RasterVector.allocationLength(length))
        override fun copy(data: IntArray, start: Int, length: Int) = data.copyOfRange(start, start + length)
        override fun get(data: IntArray, index: Int): UInt = data[index].toUInt()
        override fun set(data: IntArray, index: Int, value: UInt) { data[index] = value.toInt() }
        override fun bits(value: UInt): ULong = value.toULong()
        override fun fromBits(value: ULong): UInt = value.toUInt()
        override fun equal(a: UInt, b: UInt) = a == b
        override fun hash(value: UInt) = value.hashCode()
    }
    data object Floats : RasterStorage<Float, FloatArray> {
        override fun length(data: FloatArray) = data.size
        override fun allocate(length: Long) = FloatArray(RasterVector.allocationLength(length))
        override fun copy(data: FloatArray, start: Int, length: Int) = data.copyOfRange(start, start + length)
        override fun get(data: FloatArray, index: Int): Float = data[index]
        override fun set(data: FloatArray, index: Int, value: Float) { data[index] = value }
        override fun bits(value: Float): ULong = value.toRawBits().toUInt().toULong()
        override fun fromBits(value: ULong): Float = Float.fromBits(value.toInt())
        override fun equal(a: Float, b: Float) = a == b
        override fun hash(value: Float) = if (value == 0f) 0 else value.hashCode()
    }
}

internal object RasterCodecs {
    data object Y8 : RasterCodec<UByte, UByte, ByteArray> {
        override val name = "y8"
        override val components = 1
        override val storage = RasterStorages.Bytes
        override fun read(data: ByteArray, address: (Int) -> Int): UByte = data[address(0)].toUByte()
        override fun write(data: ByteArray, value: UByte, address: (Int) -> Int) {
            data[address(0)] = value.toByte()
        }
        override fun component(value: UByte, channel: Int): UByte = when (channel) {
            0 -> value
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UByte): UByte = read(0)
    }
    data object Y16 : RasterCodec<UShort, UShort, ShortArray> {
        override val name = "y16"
        override val components = 1
        override val storage = RasterStorages.Shorts
        override fun read(data: ShortArray, address: (Int) -> Int): UShort = data[address(0)].toUShort()
        override fun write(data: ShortArray, value: UShort, address: (Int) -> Int) {
            data[address(0)] = value.toShort()
        }
        override fun component(value: UShort, channel: Int): UShort = when (channel) {
            0 -> value
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UShort): UShort = read(0)
    }
    data object Y32 : RasterCodec<UInt, UInt, IntArray> {
        override val name = "y32"
        override val components = 1
        override val storage = RasterStorages.Words
        override fun read(data: IntArray, address: (Int) -> Int): UInt = data[address(0)].toUInt()
        override fun write(data: IntArray, value: UInt, address: (Int) -> Int) {
            data[address(0)] = value.toInt()
        }
        override fun component(value: UInt, channel: Int): UInt = when (channel) {
            0 -> value
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UInt): UInt = read(0)
    }
    data object YF : RasterCodec<Float, Float, FloatArray> {
        override val name = "yf"
        override val components = 1
        override val storage = RasterStorages.Floats
        override fun read(data: FloatArray, address: (Int) -> Int): Float = data[address(0)]
        override fun write(data: FloatArray, value: Float, address: (Int) -> Int) {
            data[address(0)] = value
        }
        override fun component(value: Float, channel: Int): Float = when (channel) {
            0 -> value
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> Float): Float = read(0)
    }
    data object YA8 : RasterCodec<PixelYA8, UByte, ByteArray> {
        override val name = "ya8"
        override val components = 2
        override val storage = RasterStorages.Bytes
        override fun read(data: ByteArray, address: (Int) -> Int): PixelYA8 = PixelYA8(data[address(0)].toUByte(), data[address(1)].toUByte())
        override fun write(data: ByteArray, value: PixelYA8, address: (Int) -> Int) {
            data[address(0)] = value.luma.toByte()
            data[address(1)] = value.alpha.toByte()
        }
        override fun component(value: PixelYA8, channel: Int): UByte = when (channel) {
            0 -> value.luma
            1 -> value.alpha
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UByte): PixelYA8 = PixelYA8(read(0), read(1))
    }
    data object YA16 : RasterCodec<PixelYA16, UShort, ShortArray> {
        override val name = "ya16"
        override val components = 2
        override val storage = RasterStorages.Shorts
        override fun read(data: ShortArray, address: (Int) -> Int): PixelYA16 = PixelYA16(data[address(0)].toUShort(), data[address(1)].toUShort())
        override fun write(data: ShortArray, value: PixelYA16, address: (Int) -> Int) {
            data[address(0)] = value.luma.toShort()
            data[address(1)] = value.alpha.toShort()
        }
        override fun component(value: PixelYA16, channel: Int): UShort = when (channel) {
            0 -> value.luma
            1 -> value.alpha
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UShort): PixelYA16 = PixelYA16(read(0), read(1))
    }
    data object RGB8 : RasterCodec<PixelRGB8, UByte, ByteArray> {
        override val name = "rgb8"
        override val components = 3
        override val storage = RasterStorages.Bytes
        override fun read(data: ByteArray, address: (Int) -> Int): PixelRGB8 = PixelRGB8(data[address(0)].toUByte(), data[address(1)].toUByte(), data[address(2)].toUByte())
        override fun write(data: ByteArray, value: PixelRGB8, address: (Int) -> Int) {
            data[address(0)] = value.red.toByte()
            data[address(1)] = value.green.toByte()
            data[address(2)] = value.blue.toByte()
        }
        override fun component(value: PixelRGB8, channel: Int): UByte = when (channel) {
            0 -> value.red
            1 -> value.green
            2 -> value.blue
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UByte): PixelRGB8 = PixelRGB8(read(0), read(1), read(2))
    }
    data object RGB16 : RasterCodec<PixelRGB16, UShort, ShortArray> {
        override val name = "rgb16"
        override val components = 3
        override val storage = RasterStorages.Shorts
        override fun read(data: ShortArray, address: (Int) -> Int): PixelRGB16 = PixelRGB16(data[address(0)].toUShort(), data[address(1)].toUShort(), data[address(2)].toUShort())
        override fun write(data: ShortArray, value: PixelRGB16, address: (Int) -> Int) {
            data[address(0)] = value.red.toShort()
            data[address(1)] = value.green.toShort()
            data[address(2)] = value.blue.toShort()
        }
        override fun component(value: PixelRGB16, channel: Int): UShort = when (channel) {
            0 -> value.red
            1 -> value.green
            2 -> value.blue
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UShort): PixelRGB16 = PixelRGB16(read(0), read(1), read(2))
    }
    data object RGBF : RasterCodec<PixelRGBF, Float, FloatArray> {
        override val name = "rgbf"
        override val components = 3
        override val storage = RasterStorages.Floats
        override fun read(data: FloatArray, address: (Int) -> Int): PixelRGBF = PixelRGBF(data[address(0)], data[address(1)], data[address(2)])
        override fun write(data: FloatArray, value: PixelRGBF, address: (Int) -> Int) {
            data[address(0)] = value.red
            data[address(1)] = value.green
            data[address(2)] = value.blue
        }
        override fun component(value: PixelRGBF, channel: Int): Float = when (channel) {
            0 -> value.red
            1 -> value.green
            2 -> value.blue
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> Float): PixelRGBF = PixelRGBF(read(0), read(1), read(2))
    }
    data object RGBA8 : RasterCodec<PixelRGBA8, UByte, ByteArray> {
        override val name = "rgba8"
        override val components = 4
        override val storage = RasterStorages.Bytes
        override fun read(data: ByteArray, address: (Int) -> Int): PixelRGBA8 = PixelRGBA8(data[address(0)].toUByte(), data[address(1)].toUByte(), data[address(2)].toUByte(), data[address(3)].toUByte())
        override fun write(data: ByteArray, value: PixelRGBA8, address: (Int) -> Int) {
            data[address(0)] = value.red.toByte()
            data[address(1)] = value.green.toByte()
            data[address(2)] = value.blue.toByte()
            data[address(3)] = value.alpha.toByte()
        }
        override fun component(value: PixelRGBA8, channel: Int): UByte = when (channel) {
            0 -> value.red
            1 -> value.green
            2 -> value.blue
            3 -> value.alpha
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UByte): PixelRGBA8 = PixelRGBA8(read(0), read(1), read(2), read(3))
    }
    data object RGBA16 : RasterCodec<PixelRGBA16, UShort, ShortArray> {
        override val name = "rgba16"
        override val components = 4
        override val storage = RasterStorages.Shorts
        override fun read(data: ShortArray, address: (Int) -> Int): PixelRGBA16 = PixelRGBA16(data[address(0)].toUShort(), data[address(1)].toUShort(), data[address(2)].toUShort(), data[address(3)].toUShort())
        override fun write(data: ShortArray, value: PixelRGBA16, address: (Int) -> Int) {
            data[address(0)] = value.red.toShort()
            data[address(1)] = value.green.toShort()
            data[address(2)] = value.blue.toShort()
            data[address(3)] = value.alpha.toShort()
        }
        override fun component(value: PixelRGBA16, channel: Int): UShort = when (channel) {
            0 -> value.red
            1 -> value.green
            2 -> value.blue
            3 -> value.alpha
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UShort): PixelRGBA16 = PixelRGBA16(read(0), read(1), read(2), read(3))
    }
    data object YCbCr8 : RasterCodec<PixelYCbCr8, UByte, ByteArray> {
        override val name = "ycbcr8"
        override val components = 3
        override val storage = RasterStorages.Bytes
        override fun read(data: ByteArray, address: (Int) -> Int): PixelYCbCr8 = PixelYCbCr8(data[address(0)].toUByte(), data[address(1)].toUByte(), data[address(2)].toUByte())
        override fun write(data: ByteArray, value: PixelYCbCr8, address: (Int) -> Int) {
            data[address(0)] = value.luma.toByte()
            data[address(1)] = value.cb.toByte()
            data[address(2)] = value.cr.toByte()
        }
        override fun component(value: PixelYCbCr8, channel: Int): UByte = when (channel) {
            0 -> value.luma
            1 -> value.cb
            2 -> value.cr
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UByte): PixelYCbCr8 = PixelYCbCr8(read(0), read(1), read(2))
    }
    data object YCbCrK8 : RasterCodec<PixelYCbCrK8, UByte, ByteArray> {
        override val name = "ycbcrk8"
        override val components = 4
        override val storage = RasterStorages.Bytes
        override fun read(data: ByteArray, address: (Int) -> Int): PixelYCbCrK8 = PixelYCbCrK8(data[address(0)].toUByte(), data[address(1)].toUByte(), data[address(2)].toUByte(), data[address(3)].toUByte())
        override fun write(data: ByteArray, value: PixelYCbCrK8, address: (Int) -> Int) {
            data[address(0)] = value.luma.toByte()
            data[address(1)] = value.cb.toByte()
            data[address(2)] = value.cr.toByte()
            data[address(3)] = value.black.toByte()
        }
        override fun component(value: PixelYCbCrK8, channel: Int): UByte = when (channel) {
            0 -> value.luma
            1 -> value.cb
            2 -> value.cr
            3 -> value.black
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UByte): PixelYCbCrK8 = PixelYCbCrK8(read(0), read(1), read(2), read(3))
    }
    data object CMYK8 : RasterCodec<PixelCMYK8, UByte, ByteArray> {
        override val name = "cmyk8"
        override val components = 4
        override val storage = RasterStorages.Bytes
        override fun read(data: ByteArray, address: (Int) -> Int): PixelCMYK8 = PixelCMYK8(data[address(0)].toUByte(), data[address(1)].toUByte(), data[address(2)].toUByte(), data[address(3)].toUByte())
        override fun write(data: ByteArray, value: PixelCMYK8, address: (Int) -> Int) {
            data[address(0)] = value.cyan.toByte()
            data[address(1)] = value.magenta.toByte()
            data[address(2)] = value.yellow.toByte()
            data[address(3)] = value.black.toByte()
        }
        override fun component(value: PixelCMYK8, channel: Int): UByte = when (channel) {
            0 -> value.cyan
            1 -> value.magenta
            2 -> value.yellow
            3 -> value.black
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UByte): PixelCMYK8 = PixelCMYK8(read(0), read(1), read(2), read(3))
    }
    data object CMYK16 : RasterCodec<PixelCMYK16, UShort, ShortArray> {
        override val name = "cmyk16"
        override val components = 4
        override val storage = RasterStorages.Shorts
        override fun read(data: ShortArray, address: (Int) -> Int): PixelCMYK16 = PixelCMYK16(data[address(0)].toUShort(), data[address(1)].toUShort(), data[address(2)].toUShort(), data[address(3)].toUShort())
        override fun write(data: ShortArray, value: PixelCMYK16, address: (Int) -> Int) {
            data[address(0)] = value.cyan.toShort()
            data[address(1)] = value.magenta.toShort()
            data[address(2)] = value.yellow.toShort()
            data[address(3)] = value.black.toShort()
        }
        override fun component(value: PixelCMYK16, channel: Int): UShort = when (channel) {
            0 -> value.cyan
            1 -> value.magenta
            2 -> value.yellow
            3 -> value.black
            else -> error("invalid pixel component")
        }
        override fun fromComponents(read: (Int) -> UShort): PixelCMYK16 = PixelCMYK16(read(0), read(1), read(2), read(3))
    }
}

internal object RasterBridge {
    fun from(image: ImageY8) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.Y8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: MutableImageY8) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.Y8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: ImageY16) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.Y16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: MutableImageY16) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.Y16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: ImageY32) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.Y32, RasterVector(RasterStorages.Words, image.data))
    fun from(image: MutableImageY32) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.Y32, RasterVector(RasterStorages.Words, image.data))
    fun from(image: ImageYF) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YF, RasterVector(RasterStorages.Floats, image.data))
    fun from(image: MutableImageYF) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YF, RasterVector(RasterStorages.Floats, image.data))
    fun from(image: ImageYA8) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YA8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: MutableImageYA8) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YA8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: ImageYA16) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YA16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: MutableImageYA16) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YA16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: ImageRGB8) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGB8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: MutableImageRGB8) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGB8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: ImageRGB16) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGB16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: MutableImageRGB16) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGB16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: ImageRGBF) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGBF, RasterVector(RasterStorages.Floats, image.data))
    fun from(image: MutableImageRGBF) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGBF, RasterVector(RasterStorages.Floats, image.data))
    fun from(image: ImageRGBA8) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGBA8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: MutableImageRGBA8) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGBA8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: ImageRGBA16) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGBA16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: MutableImageRGBA16) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.RGBA16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: ImageYCbCr8) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YCbCr8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: MutableImageYCbCr8) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YCbCr8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: ImageYCbCrK8) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YCbCrK8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: MutableImageYCbCrK8) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.YCbCrK8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: ImageCMYK8) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.CMYK8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: MutableImageCMYK8) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.CMYK8, RasterVector(RasterStorages.Bytes, image.data))
    fun from(image: ImageCMYK16) = RasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.CMYK16, RasterVector(RasterStorages.Shorts, image.data))
    fun from(image: MutableImageCMYK16) = MutableRasterImage(image.width.toLong(), image.height.toLong(), RasterCodecs.CMYK16, RasterVector(RasterStorages.Shorts, image.data))
}
