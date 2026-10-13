// Text.HTML.TagSoup.Type/Options: Copyright (c) 2006–2019 Neil Mitchell, BSD-3-Clause.
package io.heapy.kotdoc.html

/** TagSoup tokens preserve case, duplicate attributes, order, and unmatched tags. */
sealed interface HtmlTag {
    data class Open(val name: String, val attributes: List<Pair<String, String>> = emptyList()) : HtmlTag {
        fun attribute(name: String): String = attributes.firstOrNull { it.first == name }?.second ?: ""
    }
    data class Close(val name: String) : HtmlTag
    data class Text(val text: String) : HtmlTag
    data class Comment(val text: String) : HtmlTag
    data class Warning(val message: String) : HtmlTag
    data class Position(val row: Long, val column: Long) : HtmlTag

    fun mapStrings(transform: (String) -> String): HtmlTag = when (this) {
        is Open -> Open(transform(name), attributes.map { transform(it.first) to transform(it.second) })
        is Close -> Close(transform(name))
        is Text -> Text(transform(text))
        is Comment -> Comment(transform(text))
        is Warning -> Warning(transform(message))
        is Position -> this
    }
}

data class HtmlPosition(val row: Long = 1, val column: Long = 1) : Comparable<HtmlPosition> {
    fun advance(codePoint: Int): HtmlPosition = when (codePoint) {
        10 -> HtmlPosition(row + 1, 1)
        9 -> HtmlPosition(row, column + 8 - (column - 1).mod(8))
        else -> HtmlPosition(row, column + 1)
    }
    fun advance(text: String): HtmlPosition {
        var position = this
        text.codePoints().forEach { position = position.advance(it) }
        return position
    }
    fun toTag(): HtmlTag.Position = HtmlTag.Position(row, column)
    override fun compareTo(other: HtmlPosition): Int = compareValuesBy(this, other, { it.row }, { it.column })
}

data class HtmlParseOptions(
    val tagPosition: Boolean = false,
    val tagWarning: Boolean = false,
    val tagTextMerge: Boolean = true,
    val entityData: (String, Boolean) -> List<HtmlTag> = { name, semicolon ->
        val (text, warnings) = resolveEntity(HtmlEntities::lookupEntity, name, semicolon)
        listOf(HtmlTag.Text(text)) + warnings
    },
    val entityAttribute: (String, Boolean) -> Pair<String, List<HtmlTag>> = { name, semicolon ->
        resolveEntity(HtmlEntities::lookupEntity, name, semicolon)
    },
) {
    companion object {
        val Default = HtmlParseOptions()
        val Fast = Default.copy(tagTextMerge = false)
        fun entities(lookup: (String) -> String?): HtmlParseOptions = HtmlParseOptions(
            entityData = { name, semicolon ->
                val (text, warnings) = resolveEntity(lookup, name, semicolon)
                listOf(HtmlTag.Text(text)) + warnings
            },
            entityAttribute = { name, semicolon -> resolveEntity(lookup, name, semicolon) },
        )
    }
}

private fun resolveEntity(lookup: (String) -> String?, name: String, semicolon: Boolean): Pair<String, List<HtmlTag>> {
    val key = name + if (semicolon) ";" else ""
    return lookup(key)?.let { it to emptyList() }
        ?: ("&$key" to listOf(HtmlTag.Warning("Unknown entity: $name")))
}
