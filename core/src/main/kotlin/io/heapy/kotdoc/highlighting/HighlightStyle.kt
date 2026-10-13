// Skylighting.Types: Copyright (c) 2016-2024 John MacFarlane.
// Translated from skylighting-core 0.15, BSD-3-Clause; see licenses/.
// JSON error paths follow aeson 2.2.3.0, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.ast.UnicodeCharacters
import io.heapy.kotdoc.ast.UnicodeTextOrder
import kotlinx.serialization.json.*
import java.util.Collections
import java.util.TreeMap

/** Aeson schema errors retain the path while nested fields are decoded. */
class HighlightJsonException(val reason: String, val path: List<String> = emptyList()) : IllegalArgumentException() {
    override val message: String get() = "Error in $" + path.joinToString("") { key ->
        val chars = key.codePoints().toArray()
        if (chars.isNotEmpty() && UnicodeCharacters.isAlpha(chars[0]) && chars.all { UnicodeCharacters.isAlphaNum(it) }) ".$key"
        else "['${key.replace("\\", "\\\\").replace("'", "\\'")}']"
    } + ": $reason"
    fun at(key: String) = HighlightJsonException(reason, listOf(key) + path)
}
private fun invalid(reason: String = "mempty"): Nothing = throw HighlightJsonException(reason)
private inline fun <T> field(name: String, decode: () -> T): T = try { decode() } catch (error: HighlightJsonException) { throw error.at(name) }
private fun jsonKind(v: JsonElement) = when (v) { JsonNull -> "Null"; is JsonObject -> "Object"; is JsonArray -> "Array"; is JsonPrimitive -> if (v.isString) "String" else if (v.booleanOrNull != null) "Boolean" else "Number" }
private fun objectMap(v: JsonElement): JsonObject = v as? JsonObject ?: invalid("parsing Map ~Text failed, expected Object, but encountered ${jsonKind(v)}")
private fun JsonObject.optionalColor(name: String): HighlightColor? = get(name)?.takeUnless { it == JsonNull }?.let { field(name) { HighlightColor.fromJson(it) } }
private fun JsonObject.optionalBool(name: String): Boolean = get(name)?.takeUnless { it == JsonNull }?.let { v -> field(name) {
    if (v is JsonPrimitive && !v.isString && v.booleanOrNull != null) v.boolean
    else invalid("expected Bool, but encountered ${jsonKind(v)}")
} } ?: false

data class HighlightColor(val red: UByte, val green: UByte, val blue: UByte) : Comparable<HighlightColor> {
    override fun compareTo(other: HighlightColor) = compareValuesBy(this, other, { it.red }, { it.green }, { it.blue })
    fun toJson() = JsonPrimitive("#" + listOf(red, green, blue).joinToString("") { it.toString(16).padStart(2, '0') })
    fun asDoubles() = Triple(red.toInt() / 255.0, green.toInt() / 255.0, blue.toInt() / 255.0)
    override fun toString() = "RGB $red $green $blue"
    companion object {
        fun fromInt(value: Long) = HighlightColor((value shr 16).toUByte(), (value shr 8).toUByte(), value.toUByte())
        fun fromDoubles(red: Double, green: Double, blue: Double): HighlightColor? =
            if (red >= 0 && green >= 0 && blue >= 0 && red <= 1 && green <= 1 && blue <= 1)
                HighlightColor(kotlin.math.floor(red * 255).toInt().toUByte(), kotlin.math.floor(green * 255).toInt().toUByte(), kotlin.math.floor(blue * 255).toInt().toUByte()) else null
        fun fromString(value: String): HighlightColor? {
            val chars = value.codePoints().toArray()
            if (chars.size !in listOf(7, 9) || chars[0] != '#'.code) return null
            val start = if (chars.size == 9) 3 else 1 // ARGB alpha characters are not validated.
            fun nibble(c: Int) = when (c) { in '0'.code..'9'.code -> c - '0'.code; in 'a'.code..'f'.code -> c - 'a'.code + 10; in 'A'.code..'F'.code -> c - 'A'.code + 10; else -> -1 }
            val channels = IntArray(3)
            for (i in channels.indices) {
                val a = nibble(chars[start + i * 2]); val b = nibble(chars[start + i * 2 + 1])
                if (a < 0) return null
                // reads Word8 permits whitespace after a single hexadecimal digit.
                channels[i] = if (b >= 0) a * 16 + b else if (haskellSpace(chars[start + i * 2 + 1]) || i == 2 && chars[start + i * 2 + 1] == ')'.code) a else return null
            }
            return HighlightColor(channels[0].toUByte(), channels[1].toUByte(), channels[2].toUByte())
        }
        fun fromJson(value: JsonElement): HighlightColor = if (value is JsonPrimitive && value.isString) fromString(value.content) ?: invalid() else invalid()
    }
}

internal fun haskellSpace(c: Int) = c in 9..13 || c in listOf(32, 160, 5760, 8239, 8287, 12288) || c in 8192..8202

enum class TokenType {
    KeywordTok, DataTypeTok, DecValTok, BaseNTok, FloatTok, ConstantTok, CharTok, SpecialCharTok,
    StringTok, VerbatimStringTok, SpecialStringTok, ImportTok, CommentTok, DocumentationTok,
    AnnotationTok, CommentVarTok, OtherTok, FunctionTok, VariableTok, ControlFlowTok, OperatorTok,
    BuiltInTok, ExtensionTok, PreprocessorTok, AttributeTok, RegionMarkerTok, InformationTok,
    WarningTok, AlertTok, ErrorTok, NormalTok;

    val wireName get() = name.removeSuffix("Tok")
    // Upstream defines toEncoding but leaves the generic toJSON method intact.
    fun toJson() = JsonPrimitive(name)
    fun encodingJson() = JsonPrimitive(wireName)
    companion object {
        fun fromWireName(value: String): TokenType? {
            val constructor = (value + "Tok").trim { haskellSpace(it.code) }
            return entries.firstOrNull { it.name == constructor }
        }
        fun fromJson(value: JsonElement): TokenType {
            if (value !is JsonPrimitive || !value.isString) invalid()
            return fromWireName(value.content) ?: invalid("Not a token type")
        }
    }
}

private fun shown(value: HighlightColor?) = value?.let { "Just ($it)" } ?: "Nothing"
private fun Boolean.shown() = if (this) "True" else "False"

data class TokenStyle(
    val color: HighlightColor? = null,
    val background: HighlightColor? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) : Comparable<TokenStyle> {
    override fun compareTo(other: TokenStyle) = compareValuesBy(this, other, { it.color }, { it.background }, { it.bold }, { it.italic }, { it.underline })
    fun toJson() = buildJsonObject {
        put("text-color", color?.toJson() ?: JsonNull); put("background-color", background?.toJson() ?: JsonNull)
        put("bold", bold); put("italic", italic); put("underline", underline)
    }
    override fun toString() = "TokenStyle {tokenColor = ${shown(color)}, tokenBackground = ${shown(background)}, tokenBold = ${bold.shown()}, tokenItalic = ${italic.shown()}, tokenUnderline = ${underline.shown()}}"
    companion object {
        val Default = TokenStyle()
        fun fromJson(value: JsonElement): TokenStyle {
            if (value !is JsonObject) invalid()
            return TokenStyle(value.optionalColor("text-color"), value.optionalColor("background-color"), value.optionalBool("bold"), value.optionalBool("italic"), value.optionalBool("underline"))
        }
    }
}

class HighlightStyle(
    tokenStyles: Map<TokenType, TokenStyle>,
    val defaultColor: HighlightColor? = null,
    val backgroundColor: HighlightColor? = null,
    val lineNumberColor: HighlightColor? = null,
    val lineNumberBackgroundColor: HighlightColor? = null,
) : Comparable<HighlightStyle> {
    val tokenStyles: Map<TokenType, TokenStyle> = Collections.unmodifiableMap(TreeMap<TokenType, TokenStyle>().apply { putAll(tokenStyles) })
    fun toJson() = buildJsonObject {
        put("text-styles", JsonObject(tokenStyles.mapKeys { it.key.wireName }.mapValues { it.value.toJson() }))
        put("background-color", backgroundColor?.toJson() ?: JsonNull); put("text-color", defaultColor?.toJson() ?: JsonNull)
        put("line-number-color", lineNumberColor?.toJson() ?: JsonNull); put("line-number-background-color", lineNumberBackgroundColor?.toJson() ?: JsonNull)
    }
    override fun toString() = "Style {tokenStyles = " + tokenStyles.entries.joinToString(",", "fromList [", "]") { "(${it.key},${it.value})" } +
        ", defaultColor = ${shown(defaultColor)}, backgroundColor = ${shown(backgroundColor)}, lineNumberColor = ${shown(lineNumberColor)}, lineNumberBackgroundColor = ${shown(lineNumberBackgroundColor)}}"
    override fun compareTo(other: HighlightStyle): Int {
        val a = tokenStyles.entries.toList(); val b = other.tokenStyles.entries.toList()
        for (i in 0..<minOf(a.size, b.size)) {
            val key = a[i].key.compareTo(b[i].key); if (key != 0) return key
            val style = a[i].value.compareTo(b[i].value); if (style != 0) return style
        }
        if (a.size != b.size) return a.size.compareTo(b.size)
        return compareValuesBy(this, other, { it.defaultColor }, { it.backgroundColor }, { it.lineNumberColor }, { it.lineNumberBackgroundColor })
    }
    override fun equals(other: Any?) = other is HighlightStyle && compareTo(other) == 0
    override fun hashCode() = listOf(tokenStyles, defaultColor, backgroundColor, lineNumberColor, lineNumberBackgroundColor).hashCode()
    companion object {
        fun fromJson(value: JsonElement): HighlightStyle {
            if (value !is JsonObject) invalid()
            val rawStyles = value["text-styles"] ?: invalid("key \"text-styles\" not found")
            val styles = field("text-styles") {
                objectMap(rawStyles).entries.sortedWith { a, b -> UnicodeTextOrder.compare(a.key, b.key) }.associate { (key, v) -> key to field(key) { TokenStyle.fromJson(v) } }
            }
            val editor = value["editor-colors"]?.takeUnless { it == JsonNull }?.let { v -> field("editor-colors") {
                objectMap(v).entries.sortedWith { a, b -> UnicodeTextOrder.compare(a.key, b.key) }.associate { (key, color) -> key to field(key) { HighlightColor.fromJson(color) } }
            } } ?: emptyMap()
            val background = value.optionalColor("background-color")
            val line = value.optionalColor("line-number-color")
            val normal = value.optionalColor("text-color")
            val lineBackground = value.optionalColor("line-number-background-color")
            val normalized = linkedMapOf<TokenType, TokenStyle>()
            styles.forEach { (key, style) -> normalized[TokenType.fromWireName(key) ?: TokenType.OtherTok] = style }
            return HighlightStyle(normalized, normal ?: styles["Normal"]?.color, background ?: editor["background-color"], line ?: editor["line-numbers"], lineBackground ?: editor["background-color"])
        }
        fun parseTheme(text: String) = fromJson(ThemeJson.parse(text))
    }
}
