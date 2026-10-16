// Original predicates: Copyright (c) 2006-2023 John MacFarlane.
// BSD-3-Clause; see licenses/pandoc-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Expected values execute Data.Generics, independently of both Walk implementations. */
class OriginalTypesPropertiesTest {
    // This oracle pins text-2.1.2. The separate UnicodeText API deliberately
    // implements text-2.1.3, whose new Unicode mappings differ for U+019B/U+1C8A.
    private val upper = Json.parseToJsonElement(checkNotNull(javaClass.getResource(
        "/io/heapy/kotdoc/text-2.1.2-case.json")).readText()).jsonObject.getValue("upper").jsonObject
        .mapKeys { it.key.toInt(16) }.mapValues { it.value.jsonPrimitive.content }
    private fun sourceUpper(text: String) = buildString {
        text.codePoints().forEach { cp -> upper[cp]?.let(::append) ?: appendCodePoint(cp) }
    }
    private fun whitespace(x: Inline) = x == Inline.Space || x == Inline.LineBreak || x == Inline.SoftBreak || x == Inline.Str("")
    private fun inlines(xs: List<Inline>) = if (xs.all(::whitespace)) emptyList() else xs
    private fun blocks(xs: List<Block>): List<Block> = when (val x = xs.singleOrNull()) {
        is Block.CodeBlock -> emptyList()
        is Block.BlockQuote -> x.blocks
        is Block.Div -> x.blocks
        else -> xs
    }
    // Generic Data traverses cons tails too. Upstream p_walkList explicitly folds
    // the list function before giving it to Walk; it is not a whole-list map.
    private fun <A> suffixFold(xs: List<A>, f: (List<A>) -> List<A>): List<A> =
        xs.asReversed().fold(emptyList()) { tail, head -> f(listOf(head) + tail) }

    @TestFactory fun originalWalkPredicates(): List<DynamicTest> {
        val stream = javaClass.getResourceAsStream("/pandoc-types/properties.jsonl.gz")!!
        val rows = GZIPInputStream(stream).bufferedReader().use { it.readLines() }
        return rows.flatMap { line ->
            val r = Json.parseToJsonElement(line).jsonObject
            val d = PandocJson.decode(r.getValue("input"))
            val id = "seed=${r["seed"]} size=${r["size"]}"
            fun transformed(name: String, visitor: PandocVisitor) = DynamicTest.dynamicTest("p_walk $name $id") {
                assertEquals(PandocJson.decode(r.getValue(name)), visitor.walk(d))
            }
            fun queried(name: String, actual: () -> JsonElement) = DynamicTest.dynamicTest("p_query $name $id") {
                assertEquals(r.getValue(name), actual())
            }
            listOf(
                transformed("inlineTrans", object : PandocVisitor() {
                    override fun transformInline(value: Inline): Inline = when (value) {
                        is Inline.Str -> Inline.Str(sourceUpper(value.text))
                        is Inline.Emph -> Inline.Strong(value.content)
                        else -> value
                    }
                }),
                transformed("blockTrans", object : PandocVisitor() {
                    override fun transformBlock(value: Block): Block = when (value) {
                        is Block.Plain -> Block.Para(value.content)
                        is Block.BlockQuote -> Block.Div(Attr(classes = listOf("special")), value.blocks)
                        else -> value
                    }
                }),
                transformed("metaValueTrans", object : PandocVisitor() {
                    override fun transformMetaValue(value: MetaValue): MetaValue = when (value) {
                        is MetaValue.MetaBool -> MetaValue.MetaBool(!value.value)
                        is MetaValue.MetaString -> MetaValue.MetaString(sourceUpper(value.value))
                        else -> value
                    }
                }),
                transformed("metaTrans", object : PandocVisitor() {
                    override fun transformMeta(value: Map<String, MetaValue>) = value.mapKeys { sourceUpper(it.key) }
                }),
                transformed("inlinesTrans", object : PandocVisitor() {
                    override fun transformInlines(value: List<Inline>) = suffixFold(value, ::inlines)
                }),
                transformed("blocksTrans", object : PandocVisitor() {
                    override fun transformBlocks(value: List<Block>) = suffixFold(value, ::blocks)
                }),
                queried("inlineQuery") {
                    val out = StringBuilder()
                    object : PandocVisitor() { override fun visitInline(value: Inline) { if (value is Inline.Str) out.append(value.text) } }.walk(d)
                    JsonPrimitive(out.toString())
                },
                queried("blockQuery") {
                    val out = mutableListOf<JsonElement>()
                    object : PandocVisitor() { override fun visitBlock(value: Block) { if (value is Block.Header) out.add(JsonPrimitive(value.level)) } }.walk(d)
                    JsonArray(out)
                },
                queried("metaValueQuery") {
                    val out = StringBuilder()
                    object : PandocVisitor() { override fun visitMetaValue(value: MetaValue) { if (value is MetaValue.MetaString) out.append(value.value) } }.walk(d)
                    JsonPrimitive(out.toString())
                },
                queried("metaQuery") {
                    var n = 0L
                    object : PandocVisitor() { override fun visitMeta(value: Map<String, MetaValue>) { n += value.size } }.walk(d)
                    JsonPrimitive(n)
                },
                queried("inlinesQuery") {
                    var n = 0L
                    object : PandocVisitor() { override fun visitInlines(value: List<Inline>) { val k = value.size.toLong(); n += k * (k + 1) / 2 } }.walk(d)
                    JsonPrimitive(n)
                },
                queried("blocksQuery") {
                    var n = 0L
                    object : PandocVisitor() { override fun visitBlocks(value: List<Block>) { val k = value.size.toLong(); n += k * (k + 1) / 2 } }.walk(d)
                    JsonPrimitive(n)
                },
            )
        }
    }
}
