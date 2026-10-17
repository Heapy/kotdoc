// Port of pandoc-types Text.Pandoc.Definition metadata and SimpleFigure helpers.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

operator fun Pandoc.plus(other: Pandoc): Pandoc = Pandoc(meta + other.meta, blocks + other.blocks)

fun Pandoc.setMeta(key: String, value: MetaValue): Pandoc = copy(meta = meta + (key to value))
fun Pandoc.deleteMeta(key: String): Pandoc = copy(meta = meta - key)
fun Pandoc.setTitle(title: Inlines): Pandoc = setMeta("title", MetaValue.MetaInlines(title.values))
fun Pandoc.setAuthors(authors: List<Inlines>): Pandoc = setMeta("author", MetaValue.MetaList(authors.map { MetaValue.MetaInlines(it.values) }))
fun Pandoc.setDate(date: Inlines): Pandoc = setMeta("date", MetaValue.MetaInlines(date.values))

fun docTitle(meta: Map<String, MetaValue>): List<Inline> = PandocMetadata.docTitle { MetadataMap.fromMap(meta) }
fun docDate(meta: Map<String, MetaValue>): List<Inline> = PandocMetadata.docDate { MetadataMap.fromMap(meta) }
fun docAuthors(meta: Map<String, MetaValue>): List<List<Inline>> = PandocMetadata.docAuthors { MetadataMap.fromMap(meta) }

private fun textMetadata(value: MetaValue?): List<Inline> = when (value) {
    is MetaValue.MetaString -> listOf(Inline.Str(value.value))
    is MetaValue.MetaInlines -> value.content
    is MetaValue.MetaBlocks -> when (val block = value.content.singleOrNull()) {
        is Block.Plain -> block.content
        is Block.Para -> block.content
        else -> emptyList()
    }
    else -> emptyList()
}

/** Compatibility with the old Definition.SimpleFigure pattern, distinct from Builder.simpleFigure. */
data class SimpleFigure(val attr: Attr, val caption: List<Inline>, val target: Target) {
    fun toBlock(): Block.Para = Block.Para(listOf(Inline.Image(attr, caption, target.copy(title = "fig:" + target.title))))

    companion object {
        fun fromBlock(block: Block): SimpleFigure? {
            val image = (block as? Block.Para)?.content?.singleOrNull() as? Inline.Image ?: return null
            if (!image.target.title.startsWith("fig:")) return null
            return SimpleFigure(image.attr, image.content, image.target.copy(title = image.target.title.removePrefix("fig:")))
        }
    }
}
