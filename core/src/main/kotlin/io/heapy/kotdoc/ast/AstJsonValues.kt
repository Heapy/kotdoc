// pandoc-types 1.23.1.2 Definition ToJSON; Copyright (c) 2006-2023 John MacFarlane, BSD-3-Clause.
// Aeson 2.2.3.0 Double ToJSON; full notices in licenses/.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.json.PrettyValue
import io.heapy.kotdoc.json.ScientificNumber
import java.util.ArrayDeque

/** Original ToJSON Values, separate from the binary ToEncoding writer and its number spelling. */
object AstJsonValues {
    private class Shape(val fields: List<Any?>, val build: (List<PrettyValue>) -> PrettyValue)
    private class Work(val value: Any?)
    private class Build(val shape: Shape)
    private fun array(vararg fields: Any?) = Shape(fields.toList()) { PrettyValue.Array(it) }
    private fun obj(vararg fields: Pair<String, Any?>) = Shape(fields.map { it.second }) { vs ->
        PrettyValue.Object(fields.mapIndexed { i, p -> p.first to vs[i] }.toMap())
    }
    private fun tag(name: String, vararg fields: Any?): Shape = Shape(fields.toList()) { vs ->
        PrettyValue.Object(buildMap {
            put("t",PrettyValue.Text(name))
            if (vs.size == 1) put("c",vs.single()) else if (vs.isNotEmpty()) put("c",PrettyValue.Array(vs))
        })
    }
    private fun shape(v: Any): Shape = when(v) {
        is Pandoc -> obj("pandoc-api-version" to listOf(1L,23L,1L,2L),"meta" to v.meta,"blocks" to v.blocks)
        is Map<*,*> -> {
            val entries=v.entries.toList()
            Shape(entries.map { it.value }) { vs -> PrettyValue.Object(entries.mapIndexed { i,e -> e.key as String to vs[i] }.toMap()) }
        }
        is List<*> -> Shape(v) { PrettyValue.Array(it) }
        is Pair<*,*> -> array(v.first,v.second)
        is Enum<*> -> tag(v.name)
        is Attr -> array(v.identifier,v.classes,v.keyValues)
        is Target -> array(v.url,v.title)
        is ListAttributes -> array(v.start,v.style,v.delimiter)
        is ColSpec -> array(v.alignment,v.width)
        is Caption -> array(v.short,v.long)
        is ColWidth.Width -> tag("ColWidth",v.value)
        ColWidth.Default -> tag("ColWidthDefault")
        is Row -> array(v.attr,v.cells)
        is Cell -> array(v.attr,v.alignment,v.rowSpan,v.colSpan,v.blocks)
        is TableHead -> array(v.attr,v.rows)
        is TableBody -> array(v.attr,v.rowHeadColumns,v.head,v.body)
        is TableFoot -> array(v.attr,v.rows)
        is Citation -> obj("citationId" to v.citationId,"citationPrefix" to v.citationPrefix,"citationSuffix" to v.citationSuffix,
            "citationMode" to v.citationMode,"citationNoteNum" to v.citationNoteNum,"citationHash" to v.citationHash)
        is Format -> Shape(emptyList()) { PrettyValue.Text(v.value) }
        is MetaValue.MetaMap -> tag("MetaMap",v.values)
        is MetaValue.MetaList -> tag("MetaList",v.values)
        is MetaValue.MetaBool -> tag("MetaBool",v.value)
        is MetaValue.MetaString -> tag("MetaString",v.value)
        is MetaValue.MetaInlines -> tag("MetaInlines",v.content)
        is MetaValue.MetaBlocks -> tag("MetaBlocks",v.content)
        is Inline.Str -> tag("Str",v.text)
        is Inline.Emph -> tag("Emph",v.content)
        is Inline.Underline -> tag("Underline",v.content)
        is Inline.Strong -> tag("Strong",v.content)
        is Inline.Strikeout -> tag("Strikeout",v.content)
        is Inline.Superscript -> tag("Superscript",v.content)
        is Inline.Subscript -> tag("Subscript",v.content)
        is Inline.SmallCaps -> tag("SmallCaps",v.content)
        is Inline.Quoted -> tag("Quoted",v.type,v.content)
        is Inline.Cite -> tag("Cite",v.citations,v.content)
        is Inline.Code -> tag("Code",v.attr,v.text)
        Inline.Space -> tag("Space")
        Inline.SoftBreak -> tag("SoftBreak")
        Inline.LineBreak -> tag("LineBreak")
        is Inline.Math -> tag("Math",v.type,v.text)
        is Inline.RawInline -> tag("RawInline",v.format,v.text)
        is Inline.Link -> tag("Link",v.attr,v.content,v.target)
        is Inline.Image -> tag("Image",v.attr,v.content,v.target)
        is Inline.Note -> tag("Note",v.blocks)
        is Inline.Span -> tag("Span",v.attr,v.content)
        is Block.Plain -> tag("Plain",v.content)
        is Block.Para -> tag("Para",v.content)
        is Block.LineBlock -> tag("LineBlock",v.lines)
        is Block.CodeBlock -> tag("CodeBlock",v.attr,v.text)
        is Block.RawBlock -> tag("RawBlock",v.format,v.text)
        is Block.BlockQuote -> tag("BlockQuote",v.blocks)
        is Block.OrderedList -> tag("OrderedList",v.attributes,v.items)
        is Block.BulletList -> tag("BulletList",v.items)
        is Block.DefinitionList -> tag("DefinitionList",v.items)
        is Block.Header -> tag("Header",v.level,v.attr,v.content)
        Block.HorizontalRule -> tag("HorizontalRule")
        is Block.Table -> tag("Table",v.attr,v.caption,v.columns,v.head,v.bodies,v.foot)
        is Block.Figure -> tag("Figure",v.attr,v.caption,v.blocks)
        is Block.Div -> tag("Div",v.attr,v.blocks)
        else -> error("Unsupported AST ToJSON value: ${v.javaClass}")
    }
    fun toValue(value: Any?): PrettyValue {
        val work=ArrayDeque<Any>();val results=ArrayDeque<PrettyValue>();work.addLast(Work(value))
        while(work.isNotEmpty()) when(val task=work.removeLast()) {
            is Build -> {
                val values=List(task.shape.fields.size) { results.removeLast() }.asReversed()
                results.addLast(task.shape.build(values))
            }
            is Work -> when(val v=task.value) {
                null -> results.addLast(PrettyValue.Null)
                is String -> results.addLast(PrettyValue.Text(v))
                is Boolean -> results.addLast(PrettyValue.Bool(v))
                is Long -> results.addLast(PrettyValue.Number(ScientificNumber(v)))
                is Double -> results.addLast(PrettyValue.fromDouble(v))
                else -> {
                    val s=shape(v);work.addLast(Build(s));s.fields.asReversed().forEach { work.addLast(Work(it)) }
                }
            }
        }
        return results.single()
    }
}
