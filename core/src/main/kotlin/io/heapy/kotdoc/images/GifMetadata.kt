// Codec.Picture.Gif / InternalHelper: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

/**
 * The forced portion of JuicyPixels' decodeGifWithMetadata when only metadata
 * is consumed (as in Pandoc). Every block is parsed, including later frames,
 * but LZW pixels, palette substitution and animation composition stay lazy in
 * the original. This API deliberately does not claim to decode pixel images.
 */
object GifMetadata {
    sealed interface Result {
        data class Success(val size: ImageSize) : Result
        data class Failure(val message: String) : Result
    }

    fun read(bytes: ByteArray): Result = try {
        val input = Input(bytes)
        val signature = input.take(6).toString(Charsets.ISO_8859_1)
        if (signature != "GIF87a" && signature != "GIF89a") {
            throw InvalidGif("Invalid Gif signature : $signature")
        }
        val width = input.word16()
        val height = input.word16()
        val screenFlags = input.word8()
        input.skip(2) // Background index and aspect ratio do not affect metadata.
        input.palette(screenFlags)
        var hasImage = false
        while (true) {
            when (val block = input.word8()) {
                0x3b -> break // runGetStrict discards trailing bytes.
                0x2c -> {
                    input.skip(8) // Left, top, local width, local height.
                    input.palette(input.word8())
                    input.word8() // LZW root size is read but not validated.
                    input.dataBlocks()
                    hasImage = true
                }
                0x21 -> when (input.word8()) {
                    // GraphicControlExtension.get ignores the size and
                    // terminator values and always consumes six bytes.
                    0xf9 -> input.skip(6)
                    0x01, 0xff -> {
                        input.skip(input.word8())
                        input.dataBlocks()
                    }
                    // Comments and unknown extensions are sub-block streams.
                    else -> input.dataBlocks()
                }
                else -> throw InvalidGif("Unrecognized gif block $block @${input.position}")
            }
        }
        if (hasImage) Result.Success(ImageSize(width.toBigInteger(), height.toBigInteger()))
        else Result.Failure("No image in gif file")
    } catch (error: InvalidGif) {
        Result.Failure(error.message!!)
    }

    private class InvalidGif(message: String) : RuntimeException(message)

    private class Input(private val bytes: ByteArray) {
        var position: Int = 0
            private set

        fun word8(): Int {
            requireBytes(1)
            return bytes[position++].toInt() and 255
        }

        fun word16(): Int = word8() or (word8() shl 8)

        fun skip(count: Int) {
            requireBytes(count)
            position += count
        }

        fun take(count: Int): ByteArray {
            requireBytes(count)
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        fun palette(flags: Int) {
            if (flags and 0x80 != 0) skip(3 * (1 shl ((flags and 7) + 1)))
        }

        fun dataBlocks() {
            while (true) {
                val count = word8()
                if (count == 0) return
                skip(count)
            }
        }

        private fun requireBytes(count: Int) {
            if (count > bytes.size - position) throw InvalidGif("not enough bytes")
        }
    }
}
