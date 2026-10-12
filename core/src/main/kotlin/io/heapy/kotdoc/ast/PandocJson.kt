// Port of pandoc-types Text.Pandoc.Definition JSON instances.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*

/** Pandoc's tagged JSON protocol, not Kotlin's default polymorphic encoding. */
object PandocJson {
    val apiVersion: List<Int> = listOf(1, 23, 1, 2)

    fun decode(input: String): Pandoc = decode(Json.parseToJsonElement(input))
    fun encode(document: Pandoc): String = toJson(document).toString()

    fun decode(json: JsonElement): Pandoc {
        val obj = json.jsonObject
        val version = obj.required("pandoc-api-version").jsonArray.map { it.int() }
        require(version.size >= 2 && version.take(2) == apiVersion.take(2)) {
            "Incompatible pandoc-api-version: $version; expected ${apiVersion.take(2)}"
        }
        return Pandoc(decodeMeta(obj.required("meta")), blocks(obj.required("blocks")))
    }

    fun toJson(document: Pandoc): JsonObject = buildJsonObject {
        put("pandoc-api-version", arr(documentVersion()))
        put("meta", encodeMeta(document.meta))
        put("blocks", list(document.blocks, ::encodeBlock))
    }

    private fun documentVersion() = apiVersion.map(::JsonPrimitive)

    fun decodeMeta(json: JsonElement): Map<String, MetaValue> =
        json.jsonObject.mapValues { decodeMetaValue(it.value) }

    fun encodeMeta(meta: Map<String, MetaValue>): JsonObject =
        JsonObject(meta.toSortedMap().mapValues { encodeMetaValue(it.value) })

    fun decodeMetaValue(json: JsonElement): MetaValue = when (val t = json.tag()) {
        "MetaMap" -> MetaValue.MetaMap(decodeMeta(json.content()))
        "MetaList" -> MetaValue.MetaList(json.content().jsonArray.map(::decodeMetaValue))
        "MetaBool" -> MetaValue.MetaBool(json.content().bool())
        "MetaString" -> MetaValue.MetaString(json.content().string())
        "MetaInlines" -> MetaValue.MetaInlines(inlines(json.content()))
        "MetaBlocks" -> MetaValue.MetaBlocks(blocks(json.content()))
        else -> error("Unknown metadata constructor: $t")
    }

    fun encodeMetaValue(value: MetaValue): JsonElement = when (value) {
        is MetaValue.MetaMap -> tagged("MetaMap", encodeMeta(value.values))
        is MetaValue.MetaList -> tagged("MetaList", list(value.values, ::encodeMetaValue))
        is MetaValue.MetaBool -> tagged("MetaBool", JsonPrimitive(value.value))
        is MetaValue.MetaString -> tagged("MetaString", str(value.value))
        is MetaValue.MetaInlines -> tagged("MetaInlines", list(value.content, ::encodeInline))
        is MetaValue.MetaBlocks -> tagged("MetaBlocks", list(value.content, ::encodeBlock))
    }

    fun decodeInline(json: JsonElement): Inline {
        val t = json.tag()
        // Nullary constructors have no contents field at all.
        when (t) {
            "Space" -> return Inline.Space
            "SoftBreak" -> return Inline.SoftBreak
            "LineBreak" -> return Inline.LineBreak
        }
        val c = json.content()
        return when (t) {
            "Str" -> Inline.Str(c.string())
            "Emph" -> Inline.Emph(inlines(c))
            "Underline" -> Inline.Underline(inlines(c))
            "Strong" -> Inline.Strong(inlines(c))
            "Strikeout" -> Inline.Strikeout(inlines(c))
            "Superscript" -> Inline.Superscript(inlines(c))
            "Subscript" -> Inline.Subscript(inlines(c))
            "SmallCaps" -> Inline.SmallCaps(inlines(c))
            "Quoted" -> c.tuple(2).let { Inline.Quoted(enum(it[0]), inlines(it[1])) }
            "Cite" -> c.tuple(2).let { Inline.Cite(it[0].jsonArray.map(::decodeCitation), inlines(it[1])) }
            "Code" -> c.tuple(2).let { Inline.Code(decodeAttr(it[0]), it[1].string()) }
            "Math" -> c.tuple(2).let { Inline.Math(enum(it[0]), it[1].string()) }
            "RawInline" -> c.tuple(2).let { Inline.RawInline(it[0].string(), it[1].string()) }
            "Link" -> c.tuple(3).let { Inline.Link(decodeAttr(it[0]), inlines(it[1]), decodeTarget(it[2])) }
            "Image" -> c.tuple(3).let { Inline.Image(decodeAttr(it[0]), inlines(it[1]), decodeTarget(it[2])) }
            "Note" -> Inline.Note(blocks(c))
            "Span" -> c.tuple(2).let { Inline.Span(decodeAttr(it[0]), inlines(it[1])) }
            else -> error("Unknown inline constructor: $t")
        }
    }

    fun encodeInline(value: Inline): JsonElement = when (value) {
        is Inline.Str -> tagged("Str", str(value.text))
        is Inline.Emph -> tagged("Emph", list(value.content, ::encodeInline))
        is Inline.Underline -> tagged("Underline", list(value.content, ::encodeInline))
        is Inline.Strong -> tagged("Strong", list(value.content, ::encodeInline))
        is Inline.Strikeout -> tagged("Strikeout", list(value.content, ::encodeInline))
        is Inline.Superscript -> tagged("Superscript", list(value.content, ::encodeInline))
        is Inline.Subscript -> tagged("Subscript", list(value.content, ::encodeInline))
        is Inline.SmallCaps -> tagged("SmallCaps", list(value.content, ::encodeInline))
        is Inline.Quoted -> tagged("Quoted", arr(tagged(value.type.name), list(value.content, ::encodeInline)))
        is Inline.Cite -> tagged("Cite", arr(list(value.citations, ::encodeCitation), list(value.content, ::encodeInline)))
        is Inline.Code -> tagged("Code", arr(encodeAttr(value.attr), str(value.text)))
        Inline.Space -> tagged("Space")
        Inline.SoftBreak -> tagged("SoftBreak")
        Inline.LineBreak -> tagged("LineBreak")
        is Inline.Math -> tagged("Math", arr(tagged(value.type.name), str(value.text)))
        is Inline.RawInline -> tagged("RawInline", arr(str(value.format), str(value.text)))
        is Inline.Link -> tagged("Link", arr(encodeAttr(value.attr), list(value.content, ::encodeInline), encodeTarget(value.target)))
        is Inline.Image -> tagged("Image", arr(encodeAttr(value.attr), list(value.content, ::encodeInline), encodeTarget(value.target)))
        is Inline.Note -> tagged("Note", list(value.blocks, ::encodeBlock))
        is Inline.Span -> tagged("Span", arr(encodeAttr(value.attr), list(value.content, ::encodeInline)))
    }

    fun decodeBlock(json: JsonElement): Block {
        val t = json.tag()
        if (t == "HorizontalRule") return Block.HorizontalRule
        val c = json.content()
        return when (t) {
            "Plain" -> Block.Plain(inlines(c))
            "Para" -> Block.Para(inlines(c))
            "LineBlock" -> Block.LineBlock(c.jsonArray.map(::inlines))
            "CodeBlock" -> c.tuple(2).let { Block.CodeBlock(decodeAttr(it[0]), it[1].string()) }
            "RawBlock" -> c.tuple(2).let { Block.RawBlock(it[0].string(), it[1].string()) }
            "BlockQuote" -> Block.BlockQuote(blocks(c))
            "OrderedList" -> c.tuple(2).let { Block.OrderedList(decodeListAttributes(it[0]), it[1].jsonArray.map(::blocks)) }
            "BulletList" -> Block.BulletList(c.jsonArray.map(::blocks))
            "DefinitionList" -> Block.DefinitionList(c.jsonArray.map { entry ->
                entry.tuple(2).let { inlines(it[0]) to it[1].jsonArray.map(::blocks) }
            })
            "Header" -> c.tuple(3).let { Block.Header(it[0].int(), decodeAttr(it[1]), inlines(it[2])) }
            "Table" -> c.tuple(6).let {
                Block.Table(decodeAttr(it[0]), decodeCaption(it[1]), it[2].jsonArray.map(::decodeColSpec),
                    decodeTableHead(it[3]), it[4].jsonArray.map(::decodeTableBody), decodeTableFoot(it[5]))
            }
            "Figure" -> c.tuple(3).let { Block.Figure(decodeAttr(it[0]), decodeCaption(it[1]), blocks(it[2])) }
            "Div" -> c.tuple(2).let { Block.Div(decodeAttr(it[0]), blocks(it[1])) }
            else -> error("Unknown block constructor: $t")
        }
    }

    fun encodeBlock(value: Block): JsonElement = when (value) {
        is Block.Plain -> tagged("Plain", list(value.content, ::encodeInline))
        is Block.Para -> tagged("Para", list(value.content, ::encodeInline))
        is Block.LineBlock -> tagged("LineBlock", list(value.lines) { list(it, ::encodeInline) })
        is Block.CodeBlock -> tagged("CodeBlock", arr(encodeAttr(value.attr), str(value.text)))
        is Block.RawBlock -> tagged("RawBlock", arr(str(value.format), str(value.text)))
        is Block.BlockQuote -> tagged("BlockQuote", list(value.blocks, ::encodeBlock))
        is Block.OrderedList -> tagged("OrderedList", arr(encodeListAttributes(value.attributes), list(value.items) { list(it, ::encodeBlock) }))
        is Block.BulletList -> tagged("BulletList", list(value.items) { list(it, ::encodeBlock) })
        is Block.DefinitionList -> tagged("DefinitionList", list(value.items) { (term, definitions) ->
            arr(list(term, ::encodeInline), list(definitions) { list(it, ::encodeBlock) })
        })
        is Block.Header -> tagged("Header", arr(JsonPrimitive(value.level), encodeAttr(value.attr), list(value.content, ::encodeInline)))
        Block.HorizontalRule -> tagged("HorizontalRule")
        is Block.Table -> tagged("Table", arr(encodeAttr(value.attr), encodeCaption(value.caption), list(value.columns, ::encodeColSpec),
            encodeTableHead(value.head), list(value.bodies, ::encodeTableBody), encodeTableFoot(value.foot)))
        is Block.Figure -> tagged("Figure", arr(encodeAttr(value.attr), encodeCaption(value.caption), list(value.blocks, ::encodeBlock)))
        is Block.Div -> tagged("Div", arr(encodeAttr(value.attr), list(value.blocks, ::encodeBlock)))
    }

    fun decodeAttr(json: JsonElement): Attr = json.tuple(3).let {
        Attr(it[0].string(), it[1].jsonArray.map { v -> v.string() }, it[2].jsonArray.map { pair ->
            pair.tuple(2).let { p -> p[0].string() to p[1].string() }
        })
    }
    fun encodeAttr(value: Attr): JsonElement = arr(str(value.identifier), list(value.classes, ::str),
        list(value.keyValues) { (key, v) -> arr(str(key), str(v)) })

    fun decodeTarget(json: JsonElement): Target = json.tuple(2).let { Target(it[0].string(), it[1].string()) }
    fun encodeTarget(value: Target): JsonElement = arr(str(value.url), str(value.title))

    fun decodeListAttributes(json: JsonElement): ListAttributes = json.tuple(3).let { ListAttributes(it[0].int(), enum(it[1]), enum(it[2])) }
    fun encodeListAttributes(value: ListAttributes): JsonElement = arr(JsonPrimitive(value.start), tagged(value.style.name), tagged(value.delimiter.name))

    fun decodeCitation(json: JsonElement): Citation = json.jsonObject.let {
        Citation(it.required("citationId").string(), inlines(it.required("citationPrefix")), inlines(it.required("citationSuffix")),
            enum(it.required("citationMode")), it.required("citationNoteNum").int(), it.required("citationHash").int())
    }
    fun encodeCitation(value: Citation): JsonElement = buildJsonObject {
        put("citationId", str(value.citationId))
        put("citationPrefix", list(value.citationPrefix, ::encodeInline))
        put("citationSuffix", list(value.citationSuffix, ::encodeInline))
        put("citationMode", tagged(value.citationMode.name))
        put("citationNoteNum", JsonPrimitive(value.citationNoteNum))
        put("citationHash", JsonPrimitive(value.citationHash))
    }

    fun decodeColWidth(json: JsonElement): ColWidth = when (val t = json.tag()) {
        "ColWidthDefault" -> ColWidth.Default
        "ColWidth" -> ColWidth.Width(json.content().number())
        else -> error("Unknown column width constructor: $t")
    }
    fun encodeColWidth(value: ColWidth): JsonElement = when (value) {
        ColWidth.Default -> tagged("ColWidthDefault")
        is ColWidth.Width -> tagged("ColWidth", JsonPrimitive(value.value))
    }
    fun decodeColSpec(json: JsonElement): ColSpec = json.tuple(2).let { ColSpec(enum(it[0]), decodeColWidth(it[1])) }
    fun encodeColSpec(value: ColSpec): JsonElement = arr(tagged(value.alignment.name), encodeColWidth(value.width))

    fun decodeCaption(json: JsonElement): Caption = json.tuple(2).let { Caption(it[0].takeUnless { v -> v == JsonNull }?.let(::inlines), blocks(it[1])) }
    fun encodeCaption(value: Caption): JsonElement = arr(value.short?.let { list(it, ::encodeInline) } ?: JsonNull, list(value.long, ::encodeBlock))

    fun decodeRow(json: JsonElement): Row = json.tuple(2).let { Row(decodeAttr(it[0]), it[1].jsonArray.map(::decodeCell)) }
    fun encodeRow(value: Row): JsonElement = arr(encodeAttr(value.attr), list(value.cells, ::encodeCell))
    fun decodeCell(json: JsonElement): Cell = json.tuple(5).let { Cell(decodeAttr(it[0]), enum(it[1]), it[2].int(), it[3].int(), blocks(it[4])) }
    fun encodeCell(value: Cell): JsonElement = arr(encodeAttr(value.attr), tagged(value.alignment.name), JsonPrimitive(value.rowSpan), JsonPrimitive(value.colSpan), list(value.blocks, ::encodeBlock))
    fun decodeTableHead(json: JsonElement): TableHead = json.tuple(2).let { TableHead(decodeAttr(it[0]), it[1].jsonArray.map(::decodeRow)) }
    fun encodeTableHead(value: TableHead): JsonElement = arr(encodeAttr(value.attr), list(value.rows, ::encodeRow))
    fun decodeTableBody(json: JsonElement): TableBody = json.tuple(4).let { TableBody(decodeAttr(it[0]), it[1].int(), it[2].jsonArray.map(::decodeRow), it[3].jsonArray.map(::decodeRow)) }
    fun encodeTableBody(value: TableBody): JsonElement = arr(encodeAttr(value.attr), JsonPrimitive(value.rowHeadColumns), list(value.head, ::encodeRow), list(value.body, ::encodeRow))
    fun decodeTableFoot(json: JsonElement): TableFoot = json.tuple(2).let { TableFoot(decodeAttr(it[0]), it[1].jsonArray.map(::decodeRow)) }
    fun encodeTableFoot(value: TableFoot): JsonElement = arr(encodeAttr(value.attr), list(value.rows, ::encodeRow))

    private fun blocks(json: JsonElement): List<Block> = json.jsonArray.map(::decodeBlock)
    private fun inlines(json: JsonElement): List<Inline> = json.jsonArray.map(::decodeInline)
    private fun tagged(tag: String, content: JsonElement? = null): JsonObject = buildJsonObject {
        put("t", tag)
        if (content != null) put("c", content)
    }
    private fun JsonObject.required(key: String): JsonElement = get(key) ?: error("Missing JSON field: $key")
    private fun JsonElement.tag(): String = jsonObject.required("t").string()
    private fun JsonElement.content(): JsonElement = jsonObject.required("c")
    private fun JsonElement.tuple(size: Int): JsonArray = jsonArray.also {
        require(it.size == size) { "Expected $size tuple elements, found ${it.size}" }
    }
    private fun JsonElement.string(): String = jsonPrimitive.also {
        require(it.isString) { "Expected JSON string, found $it" }
    }.content
    private fun JsonElement.int(): Int = jsonPrimitive.also {
        require(!it.isString) { "Expected JSON integer, found $it" }
    }.int
    private fun JsonElement.bool(): Boolean = jsonPrimitive.also {
        require(!it.isString) { "Expected JSON boolean, found $it" }
    }.boolean
    private fun JsonElement.number(): Double = jsonPrimitive.also {
        require(!it.isString) { "Expected JSON number, found $it" }
    }.double.also { require(it.isFinite()) { "Expected finite JSON number" } }
    inline fun <reified E : Enum<E>> decodeEnum(json: JsonElement): E {
        val tag = json.jsonObject.getValue("t").jsonPrimitive
        require(tag.isString) { "Expected string constructor tag" }
        return enumValueOf(tag.content)
    }
    fun encodeEnum(value: Enum<*>): JsonElement = tagged(value.name)
    private inline fun <reified E : Enum<E>> enum(json: JsonElement): E = decodeEnum(json)
    private fun str(value: String): JsonPrimitive = JsonPrimitive(value)
    private fun arr(vararg values: JsonElement): JsonArray = JsonArray(values.toList())
    private fun arr(values: List<JsonElement>): JsonArray = JsonArray(values)
    private fun <T> list(values: List<T>, encode: (T) -> JsonElement): JsonArray = JsonArray(values.map(encode))
}
