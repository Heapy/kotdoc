// Text.Collate.Lang, unicode-collation 0.1.3.7, © 2021 John MacFarlane.
// BSD-2-Clause; complete original notice: licenses/Unicode-collation-BSD-2-Clause.txt.
package io.heapy.kotdoc.language

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.json.PrettySort
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.*
import java.util.Collections

sealed interface LangParseResult {
    data class Success(val value: Lang) : LangParseResult
    data class Failure(val error: String) : LangParseResult
}

/** The original six-field record; direct construction does not validate a tag. */
class Lang(
    language: String,
    script: String? = null,
    region: String? = null,
    variants: List<String> = emptyList(),
    extensions: List<Pair<String, List<Pair<String, String>>>> = emptyList(),
    privateUse: List<String> = emptyList(),
) : Comparable<Lang> {
    val language = TextChars.fromString(language)
    val script = script?.let(TextChars::fromString)
    val region = region?.let(TextChars::fromString)
    val variants = immutable(variants.map(TextChars::fromString))
    val extensions = immutable(extensions.map { (key, values) ->
        TextChars.fromString(key) to immutable(values.map { (name, value) ->
            TextChars.fromString(name) to TextChars.fromString(value)
        })
    })
    val privateUse = immutable(privateUse.map(TextChars::fromString))

    fun copy(
        language: String = this.language, script: String? = this.script,
        region: String? = this.region, variants: List<String> = this.variants,
        extensions: List<Pair<String, List<Pair<String, String>>>> = this.extensions,
        privateUse: List<String> = this.privateUse,
    ) = Lang(language, script, region, variants, extensions, privateUse)

    fun renderLang(): String = buildString {
        append(language)
        script?.let { append('-'); append(it) }
        region?.let { append('-'); append(it) }
        variants.forEach { append('-'); append(it) }
        extensions.forEach { (singleton, values) ->
            append('-'); append(singleton)
            values.forEach { (key, value) ->
                append('-'); append(key)
                if (value.isNotEmpty()) { append('-'); append(value) }
            }
        }
        if (privateUse.isNotEmpty()) {
            append("-x")
            privateUse.forEach { append('-'); append(it) }
        }
    }

    fun show(precedence: Long = 0): String {
        fun optional(value: String?) = if (value == null) "Nothing" else "Just ${HaskellString.quote(value)}"
        fun strings(values: List<String>) = values.joinToString(",", "[", "]", transform = HaskellString::quote)
        val ext = extensions.joinToString(",", "[", "]") { (key, values) ->
            "(${HaskellString.quote(key)}," + values.joinToString(",", "[", "]") { (k, v) ->
                "(${HaskellString.quote(k)},${HaskellString.quote(v)})"
            } + ")"
        }
        val body = "Lang {langLanguage = ${HaskellString.quote(language)}, langScript = ${optional(script)}, " +
            "langRegion = ${optional(region)}, langVariants = ${strings(variants)}, " +
            "langExtensions = $ext, langPrivateUse = ${strings(privateUse)}}"
        return if (precedence > 10) "($body)" else body
    }
    override fun toString(): String = show()
    override fun equals(other: Any?) = other is Lang && language == other.language && script == other.script &&
        region == other.region && variants == other.variants && extensions == other.extensions && privateUse == other.privateUse
    override fun hashCode(): Int = listOf(language, script, region, variants, extensions, privateUse).hashCode()
    override fun compareTo(other: Lang): Int {
        return UnicodeTextOrder.compare(language, other.language).takeIf { it != 0 }
            ?: nullableCompare(script, other.script).takeIf { it != 0 }
            ?: nullableCompare(region, other.region).takeIf { it != 0 }
            ?: listCompare(variants, other.variants, UnicodeTextOrder::compare).takeIf { it != 0 }
            ?: listCompare(extensions, other.extensions) { a, b ->
                UnicodeTextOrder.compare(a.first, b.first).takeIf { it != 0 }
                    ?: listCompare(a.second, b.second) { x, y ->
                        UnicodeTextOrder.compare(x.first, y.first).takeIf { it != 0 } ?: UnicodeTextOrder.compare(x.second, y.second)
                    }
            }.takeIf { it != 0 }
            ?: listCompare(privateUse, other.privateUse, UnicodeTextOrder::compare)
    }
    fun minimum(other: Lang) = if (this <= other) this else other
    fun maximum(other: Lang) = if (this <= other) other else this

    /** Script outranks region, which outranks collation; ties retain the first entry. */
    fun <A> lookupLang(candidates: Iterable<Pair<Lang, A>>): Pair<Lang, A>? {
        fun collation(l: Lang) = l.extensions.firstOrNull { it.first == "u" }?.second?.firstOrNull { it.first == "co" }?.second
        fun match(candidate: String?, requested: String?): Int? = when {
            candidate == null -> if (requested == null) 1 else 0
            candidate == requested -> 1
            else -> null
        }
        val ranked = mutableListOf<Pair<Int, Pair<Lang, A>>>()
        for (entry in candidates) {
            val candidate = entry.first
            if (candidate.language != language) continue
            val s = match(candidate.script, script) ?: continue
            val r = match(candidate.region, region) ?: continue
            val c = match(collation(candidate), collation(this)) ?: continue
            val rank = (s shl 2) or (r shl 1) or c
            ranked.add(rank to entry)
        }
        // The original sortOn (Down . fst) uses GHC's stable natural-run sort.
        return PrettySort.sort(ranked) { a, b -> a.first < b.first }.firstOrNull()?.second
    }

    companion object {
        fun parseLang(text: String): LangParseResult = LangParser.parse(TextChars.fromString(text))
        /** IsString's original fallback for a failed parse. */
        fun fromString(text: String): Lang = when (val result = parseLang(text)) {
            is LangParseResult.Success -> result.value
            is LangParseResult.Failure -> Lang("und")
        }
        fun fromString(text: CharacterString): Lang = fromString(TextChars.fromPoints(text.points()))
        private fun <A> immutable(values: List<A>): List<A> = Collections.unmodifiableList(values.toList())
        private fun nullableCompare(a: String?, b: String?): Int = when {
            a == null -> if (b == null) 0 else -1
            b == null -> 1
            else -> UnicodeTextOrder.compare(a, b)
        }
        private fun <A> listCompare(a: List<A>, b: List<A>, compare: (A, A) -> Int): Int {
            for (i in 0..<minOf(a.size, b.size)) compare(a[i], b[i]).let { if (it != 0) return it }
            return a.size.compareTo(b.size)
        }
    }
}

/** Original Parsec [Text] grammar, using the shared consumed/empty interpreter. */
private object LangParser {
    private data class Cursor(val tokens: List<String>, val at: Int = 0)
    private fun token(predicate: (String) -> Boolean): Parsec<Cursor, String> = Parsec.primitive { state ->
        val text = state.user.tokens.getOrNull(state.user.at)
        if (text != null && predicate(text)) {
            val next = state.copy(user = state.user.copy(at = state.user.at + 1),
                position = state.position.copy(column = state.position.column + text.codePointCount(0, text.length)))
            ParseReply(true, text, next, true, ParseError(next.position))
        } else ParseReply(false, null, state, false, ParseError(state.position,
            listOf(ParseMessage(MessageKind.SysUnExpect, text ?: ""))))
    }
    private fun alpha(c: Int) = c in 65..90 || c in 97..122
    private fun alphaNum(c: Int) = alpha(c) || c in 48..57
    private fun lengthBetween(text: String, low: Int, high: Int) = text.codePointCount(0, text.length) in low..high
    private fun alphas(n: Int) = token { it.codePoints().allMatch(::alpha) && lengthBetween(it, n, n) }
    private fun alphaRange(low: Int, high: Int) = token { it.codePoints().allMatch(::alpha) && lengthBetween(it, low, high) }
    private fun alphaNumRange(low: Int, high: Int) = token { it.codePoints().allMatch(::alphaNum) && lengthBetween(it, low, high) }
    private fun <A> optional(p: Parsec<Cursor, A>) = p.map<A?> { it }.option(null)
    private fun <A> countBetween(low: Int, high: Int, p: Parsec<Cursor, A>): Parsec<Cursor, List<A>> {
        fun loop(n: Int): Parsec<Cursor, List<A>> = p.flatMap { value ->
            if (n >= high) Parsec.pure(listOf(value))
            else Parsec.defer { loop(n + 1) }.map { listOf(value) + it }
        } or if (n > low) Parsec.pure(emptyList()) else Parsec.zero()
        return loop(1).attempt()
    }
    private val extlang = countBetween(1, 3, alphas(3).map { it.lowercase() }).map { it.joinToString("-") }
    private val language = alphaRange(2, 3).map { it.lowercase() }.flatMap { base ->
        optional(extlang).map { extra -> if (extra == null) base else "$base-$extra" }
    } or alphaRange(4, 8).map { it.lowercase() }
    private val script = alphas(4).map { it.lowercase().replaceFirstChar(Char::uppercaseChar) }
    private val region = alphas(2).map { it.uppercase() } or token { it.codePoints().allMatch(ParserCharacters::isDigit) && lengthBetween(it, 3, 3) }
    private val variant = (alphaNumRange(5, 8) or token {
        it.codePoints().allMatch(::alphaNum) && lengthBetween(it, 4, 4) && ParserCharacters.isDigit(it.codePointAt(0))
    }).map { it.lowercase() }
    private val keyword = alphas(2).flatMap { key -> alphaNumRange(3, 8).many().map { key to it.joinToString("-") } }
    private val extension = token { lengthBetween(it, 1, 1) && it.codePoints().allMatch(::alphaNum) }.map { it.lowercase() }.flatMap { singleton ->
        alphaNumRange(3, 8).map { it.lowercase() }.many().flatMap { attrs ->
            keyword.many().map { singleton to (attrs.map { it to "" } + it) }
        }
    }
    private val privateUse = token { it.lowercase() == "x" }.then(alphaNumRange(1, 8).many1())
    private val parser = language.label("language").flatMap { l ->
        optional(script.label("script")).flatMap { s ->
            optional(region.label("region")).flatMap { r ->
                variant.many().label("variant").flatMap { v ->
                    extension.many().label("extension").flatMap { e ->
                        privateUse.label("private use").option(emptyList()).map { p -> Lang(l, s, r, v, e, p) }
                    }
                }
            }
        }
    }
    fun parse(text: String): LangParseResult {
        val cps = text.codePoints().toArray()
        val end = cps.indexOfFirst(ParserCharacters::isSpace).let { if (it < 0) cps.size else it }
        val prefix = String(cps, 0, end)
        val result = parser.run(ParseState(ParseInput(), SourcePos("lang"), Cursor(prefix.split('-', '_'))))
        return if (result.success) LangParseResult.Success(checkNotNull(result.value)) else LangParseResult.Failure(result.error.toString())
    }
}
