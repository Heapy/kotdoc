// Text.Collate.Lang Binary instance: original six-tuple, binary 0.8.9.3/text 2.1.2.
// Complete original BSD notices are distributed in licenses/.
package io.heapy.kotdoc.language

import io.heapy.kotdoc.highlighting.BinaryShape
import io.heapy.kotdoc.highlighting.SyntaxBinary
import io.heapy.kotdoc.highlighting.SyntaxBinaryCodec
import io.heapy.kotdoc.highlighting.SyntaxBinaryResult

sealed class LangBinaryResult(remaining: ByteArray, val consumed: Long) {
    private val rest = remaining.copyOf()
    val remaining get() = rest.copyOf()
    class Success(val value: Lang, remaining: ByteArray, consumed: Long) : LangBinaryResult(remaining, consumed)
    class Failure(val error: String, remaining: ByteArray, consumed: Long) : LangBinaryResult(remaining, consumed)
}

/** Reuses the independently verified Data.Binary engine; no conversion subprocess. */
object LangBinary {
    private val text = SyntaxBinary.text.shape
    private fun product(fields: List<BinaryShape>, split: (Any?) -> List<Any?>, build: (List<Any?>) -> Any?) = BinaryShape.Product(fields, split, build)
    @Suppress("UNCHECKED_CAST")
    private fun list(element: BinaryShape) = BinaryShape.Sequence(element, { it as List<Any?> }, { it.toList() })
    private val optionalText = BinaryShape.Sum(listOf(
        product(emptyList(), { emptyList() }, { null }),
        product(listOf(text), { listOf(it) }, { it[0] }),
    ), { if (it == null) 0 else 1 }, strict = false)
    private val keyword = product(listOf(text, text), { (it as Pair<*, *>).let { p -> listOf(p.first, p.second) } }, { it[0] to it[1] })
    private val extension = product(listOf(text, list(keyword)), { (it as Pair<*, *>).let { p -> listOf(p.first, p.second) } }, { it[0] to it[1] })
    @Suppress("UNCHECKED_CAST")
    private val codec = SyntaxBinaryCodec<Lang>(product(listOf(text, optionalText, optionalText, list(text), list(extension), list(text)),
        { (it as Lang).let { l -> listOf(l.language, l.script, l.region, l.variants, l.extensions, l.privateUse) } },
        { Lang(it[0] as String, it[1] as String?, it[2] as String?, it[3] as List<String>, it[4] as List<Pair<String, List<Pair<String, String>>>>, it[5] as List<String>) }))

    fun encode(value: Lang): ByteArray = codec.encode(value)
    fun decode(bytes: ByteArray): LangBinaryResult = when (val result = codec.decode(bytes)) {
        is SyntaxBinaryResult.Success -> LangBinaryResult.Success(result.value, result.remaining, result.consumed)
        is SyntaxBinaryResult.Failure -> LangBinaryResult.Failure(result.error, result.remaining, result.consumed)
    }
}
