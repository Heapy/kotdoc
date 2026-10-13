// Default JPEG tables: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

internal object JpegDefaultTables {
    val lumaQuantizationTable: List<Short> = listOf(
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99,
    )
    val chromaQuantizationTable: List<Short> = listOf(
        17, 18, 24, 47, 99, 99, 99, 99,
        18, 21, 26, 66, 99, 99, 99, 99,
        24, 26, 56, 99, 99, 99, 99, 99,
        47, 66, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
    )
    val dcLumaHuffmanTable: List<List<Int>> = listOf(
        listOf(),
        listOf(0),
        listOf(1, 2, 3, 4, 5),
        listOf(6),
        listOf(7),
        listOf(8),
        listOf(9),
        listOf(10),
        listOf(11),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
    )
    val dcChromaHuffmanTable: List<List<Int>> = listOf(
        listOf(),
        listOf(0, 1, 2),
        listOf(3),
        listOf(4),
        listOf(5),
        listOf(6),
        listOf(7),
        listOf(8),
        listOf(9),
        listOf(10),
        listOf(11),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
    )
    val acLumaHuffmanTable: List<List<Int>> = listOf(
        listOf(),
        listOf(1, 2),
        listOf(3),
        listOf(0, 4, 17),
        listOf(5, 18, 33),
        listOf(49, 65),
        listOf(6, 19, 81, 97),
        listOf(7, 34, 113),
        listOf(20, 50, 129, 145, 161),
        listOf(8, 35, 66, 177, 193),
        listOf(21, 82, 209, 240),
        listOf(36, 51, 98, 114),
        listOf(),
        listOf(),
        listOf(130),
        listOf(
            9, 10, 22, 23, 24, 25, 26, 37, 38, 39, 40, 41, 42, 52, 53, 54,
            55, 56, 57, 58, 67, 68, 69, 70, 71, 72, 73, 74, 83, 84, 85, 86,
            87, 88, 89, 90, 99, 100, 101, 102, 103, 104, 105, 106, 115, 116, 117, 118,
            119, 120, 121, 122, 131, 132, 133, 134, 135, 136, 137, 138, 146, 147, 148, 149,
            150, 151, 152, 153, 154, 162, 163, 164, 165, 166, 167, 168, 169, 170, 178, 179,
            180, 181, 182, 183, 184, 185, 186, 194, 195, 196, 197, 198, 199, 200, 201, 202,
            210, 211, 212, 213, 214, 215, 216, 217, 218, 225, 226, 227, 228, 229, 230, 231,
            232, 233, 234, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250,
        ),
    )
    val acChromaHuffmanTable: List<List<Int>> = listOf(
        listOf(),
        listOf(0, 1),
        listOf(2),
        listOf(3, 17),
        listOf(4, 5, 33, 49),
        listOf(6, 18, 65, 81),
        listOf(7, 97, 113),
        listOf(19, 34, 50, 129),
        listOf(8, 20, 66, 145, 161, 177, 193),
        listOf(9, 35, 51, 82, 240),
        listOf(21, 98, 114, 209),
        listOf(10, 22, 36, 52),
        listOf(),
        listOf(225),
        listOf(37, 241),
        listOf(
            23, 24, 25, 26, 38, 39, 40, 41, 42, 53, 54, 55, 56, 57, 58, 67,
            68, 69, 70, 71, 72, 73, 74, 83, 84, 85, 86, 87, 88, 89, 90, 99,
            100, 101, 102, 103, 104, 105, 106, 115, 116, 117, 118, 119, 120, 121, 122, 130,
            131, 132, 133, 134, 135, 136, 137, 138, 146, 147, 148, 149, 150, 151, 152, 153,
            154, 162, 163, 164, 165, 166, 167, 168, 169, 170, 178, 179, 180, 181, 182, 183,
            184, 185, 186, 194, 195, 196, 197, 198, 199, 200, 201, 202, 210, 211, 212, 213,
            214, 215, 216, 217, 218, 226, 227, 228, 229, 230, 231, 232, 233, 234, 242, 243,
            244, 245, 246, 247, 248, 249, 250,
        ),
    )
    val dcLumaHuffmanTree: HuffmanTree by lazy { JpegHuffman.build(dcLumaHuffmanTable) }
    val dcChromaHuffmanTree: HuffmanTree by lazy { JpegHuffman.build(dcChromaHuffmanTable) }
    val acLumaHuffmanTree: HuffmanTree by lazy { JpegHuffman.build(acLumaHuffmanTable) }
    val acChromaHuffmanTree: HuffmanTree by lazy { JpegHuffman.build(acChromaHuffmanTable) }
}
