// Text.HTML.TagSoup.Specification/Implementation: Copyright (c) 2006–2019 Neil Mitchell.
// Kotlin translation under BSD-3-Clause; see licenses/tagsoup-BSD-3-Clause.txt.
package io.heapy.kotdoc.html

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UCharacterCategory
import io.heapy.kotdoc.formats.HaskellString

object TagSoup {
    /** Finite Haskell String semantics. Numeric surrogate references are preserved. */
    fun parseTags(input: String, options: HtmlParseOptions = HtmlParseOptions.Default): List<HtmlTag> =
        Output(Tokenizer(input, options.tagPosition).parse(), options).parse()

    /** Pandoc uses Data.Text, whose conversion of surrogate code points yields U+FFFD. */
    fun parseTextTags(input: String, options: HtmlParseOptions = HtmlParseOptions.Default): List<HtmlTag> =
        Output(Tokenizer(scalarText(input), options.tagPosition).parse(), options, textMode = true).parse()

    fun innerText(tags: Iterable<HtmlTag>): String = buildString {
        tags.forEach { if (it is HtmlTag.Text) append(it.text) }
    }

}

private fun scalarText(text: String): String = buildString {
    text.codePoints().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) }
}

private enum class Kind { CHAR, TAG, SHUT, ATT_NAME, ATT_VALUE, END, END_CLOSE, COMMENT, COMMENT_END,
    ENTITY_NAME, ENTITY_NUM, ENTITY_HEX, ENTITY_END, WARN, POS }
private data class Out(val kind: Kind, val text: String = "", val position: HtmlPosition = HtmlPosition(), val semicolon: Boolean = false)
private enum class TagType { NORMAL, XML, DECL, SCRIPT }
private enum class State { DATA, TAG_OPEN, XML_OPEN, XML_CLOSE, CLOSE_OPEN, TAG_NAME, BEFORE_NAME, NAME,
    AFTER_NAME, BEFORE_VALUE, DOUBLE_VALUE, SINGLE_VALUE, UNQUOTED_VALUE, AFTER_VALUE, SELF_CLOSE,
    BOGUS, DECL_OPEN, COMMENT_START, COMMENT_START_DASH, COMMENT, COMMENT_END_DASH, COMMENT_END,
    COMMENT_END_BANG, COMMENT_END_SPACE, CDATA, SCRIPT }

/** Iterative translation: transitions do not consume JVM stack for long input. */
private class Tokenizer(private val input: String, private val positions: Boolean) {
    private var offset = 0
    private var position = HtmlPosition()
    private var state = State.DATA
    private var type = TagType.NORMAL
    private val output = mutableListOf<Out>()
    private val eof get() = offset == input.length
    private val cp get() = if (eof) 0 else input.codePointAt(offset)
    private val char get() = if (cp <= 0xffff) cp.toChar() else '\u0000'
    private fun next() {
        check(!eof)
        if (positions) position = position.advance(cp)
        offset += Character.charCount(cp)
    }
    private fun move(state: State, consume: Boolean = true) {
        if (consume) next()
        this.state = state
    }
    private fun emit(kind: Kind) { output.add(Out(kind)) }
    private fun chars(text: String) { text.codePoints().forEach { output.add(Out(Kind.CHAR, String(Character.toChars(it)))) } }
    private fun current() { output.add(Out(Kind.CHAR, String(Character.toChars(cp)))) }
    private fun pos() { if (positions) output.add(Out(Kind.POS, position = position)) }
    private fun seen(text: String) { output.add(Out(Kind.WARN, "Unexpected " + HaskellString.quote(text))) }
    private fun want(text: String) { output.add(Out(Kind.WARN, "Expected " + HaskellString.quote(text))) }
    private fun wantEnd() { want(if (type == TagType.XML) "?>" else ">") }
    private fun white() = char in " \t\n\u000c\r" && !eof
    private fun alpha(point: Int = cp) = UCharacter.isLetter(point)
    private fun alphaNum(point: Int) = alpha(point) || when (UCharacter.getType(point)) {
        UCharacterCategory.DECIMAL_DIGIT_NUMBER.toInt(), UCharacterCategory.LETTER_NUMBER.toInt(),
        UCharacterCategory.OTHER_NUMBER.toInt() -> true
        else -> false
    }
    private fun digit(hex: Boolean, point: Int = cp) = point in 48..57 || hex && (point in 65..70 || point in 97..102)
    private fun scriptAt(start: Int): Boolean {
        var cursor = start
        for (expected in "script") {
            if (cursor == input.length) return false
            val point = input.codePointAt(cursor)
            if (UCharacter.toLowerCase(point) != expected.code) return false
            cursor += Character.charCount(point)
        }
        return cursor == input.length || input[cursor] in " \t\n\u000c\r/>?"
    }
    private fun consume(text: String) { repeat(text.length) { next() } }
    private fun tagEnd() {
        next(); pos()
        if (type == TagType.XML) want("?>")
        emit(Kind.END)
        state = if (type == TagType.SCRIPT) State.SCRIPT else State.DATA
    }
    private fun endComment(consume: Boolean) {
        emit(Kind.COMMENT_END)
        move(State.DATA, consume)
    }
    /** Called immediately after &, leaving state as the continuation. No position tokens in references. */
    private fun reference(attribute: Boolean, quote: Char? = null) {
        if (eof || char in "\t\n\u000c <&" || char == quote) { chars("&"); return }
        if (char == '#') {
            val originalOffset = offset
            val originalPosition = position
            next()
            val hex = char in "xX" && !eof
            if (hex) next()
            if (!digit(hex) || eof) {
                offset = originalOffset; position = originalPosition
                seen("&"); chars("&"); return
            }
            emit(if (hex) Kind.ENTITY_HEX else Kind.ENTITY_NUM)
            while (!eof && digit(hex)) { current(); next() }
            val semi = !eof && char == ';'
            output.add(Out(Kind.ENTITY_END, semicolon = semi))
            if (semi) next() else want(";")
        } else if (alpha()) {
            emit(Kind.ENTITY_NAME)
            do { current(); next() } while (!eof && (alphaNum(cp) || char in ":-_"))
            val semi = !eof && char == ';'
            output.add(Out(Kind.ENTITY_END, semicolon = semi))
            if (semi) next() else if (!attribute) want(";")
        } else { seen("&"); chars("&") }
    }

    fun parse(): List<Out> {
        while (true) {
            when (state) {
                State.TAG_OPEN, State.XML_OPEN, State.CLOSE_OPEN, State.DECL_OPEN, State.SCRIPT -> Unit
                else -> pos()
            }
            when (state) {
                State.DATA -> when {
                    char == '&' -> { next(); reference(false) }
                    char == '<' -> move(State.TAG_OPEN)
                    eof -> return output
                    else -> { current(); next() }
                }
                State.TAG_OPEN -> when {
                    char == '!' -> move(State.DECL_OPEN)
                    char == '/' -> move(State.CLOSE_OPEN)
                    alpha() -> { type = if (scriptAt(offset)) TagType.SCRIPT else TagType.NORMAL; emit(Kind.TAG); current(); move(State.TAG_NAME) }
                    char == '>' -> { seen("<>"); chars("<>"); move(State.DATA) }
                    char == '?' -> move(State.XML_OPEN)
                    else -> { seen("<"); chars("<"); move(State.DATA, false) }
                }
                State.XML_OPEN -> if (alpha()) {
                    type = TagType.XML; emit(Kind.TAG); chars("?"); current(); move(State.TAG_NAME)
                } else { seen("<?"); chars("<?"); move(State.DATA, false) }
                State.XML_CLOSE -> if (char == '>') { emit(Kind.END); move(State.DATA) }
                    else { seen("?"); move(State.BEFORE_NAME, false) }
                State.CLOSE_OPEN -> when {
                    alpha() || char in "?!" -> { type = TagType.NORMAL; emit(Kind.SHUT); current(); move(State.TAG_NAME) }
                    char == '>' -> { seen("</>"); chars("</>"); move(State.DATA) }
                    eof -> { chars("</"); move(State.DATA, false) }
                    else -> { want("tag name"); emit(Kind.COMMENT); move(State.BOGUS, false) }
                }
                State.TAG_NAME -> when {
                    white() -> move(State.BEFORE_NAME)
                    char == '/' -> move(State.SELF_CLOSE)
                    char == '>' -> tagEnd()
                    char == '?' && type == TagType.XML -> move(State.XML_CLOSE)
                    eof -> { wantEnd(); move(State.DATA, false) }
                    else -> { current(); next() }
                }
                State.BEFORE_NAME -> when {
                    white() -> next()
                    char == '/' -> move(State.SELF_CLOSE)
                    char == '>' -> tagEnd()
                    char == '?' && type == TagType.XML -> move(State.XML_CLOSE)
                    type != TagType.NORMAL && char in "\"'" -> move(State.BEFORE_VALUE, false)
                    eof -> { wantEnd(); move(State.DATA, false) }
                    else -> { if (char in "\"'<=") seen(char.toString()); emit(Kind.ATT_NAME); current(); move(State.NAME) }
                }
                State.NAME -> when {
                    white() -> move(State.AFTER_NAME)
                    char == '/' -> move(State.SELF_CLOSE)
                    char == '=' -> move(State.BEFORE_VALUE)
                    char == '>' -> tagEnd()
                    char == '?' && type == TagType.XML -> move(State.XML_CLOSE)
                    eof -> { wantEnd(); move(State.DATA, false) }
                    else -> { if (char in "\"'<") seen(char.toString()); current(); next() }
                }
                State.AFTER_NAME -> when {
                    white() -> next()
                    char == '/' -> move(State.SELF_CLOSE)
                    char == '=' -> move(State.BEFORE_VALUE)
                    char == '>' -> tagEnd()
                    char == '?' && type == TagType.XML -> move(State.XML_CLOSE)
                    type != TagType.NORMAL && char in "\"'" -> { emit(Kind.ATT_VALUE); move(State.BEFORE_VALUE, false) }
                    eof -> { wantEnd(); move(State.DATA, false) }
                    else -> { if (char in "\"'<") seen(char.toString()); emit(Kind.ATT_NAME); current(); move(State.NAME) }
                }
                State.BEFORE_VALUE -> when {
                    white() -> next()
                    char == '"' -> { emit(Kind.ATT_VALUE); move(State.DOUBLE_VALUE) }
                    char == '\'' -> { emit(Kind.ATT_VALUE); move(State.SINGLE_VALUE) }
                    char == '&' -> { emit(Kind.ATT_VALUE); move(State.UNQUOTED_VALUE, false) }
                    char == '>' -> { seen("="); tagEnd() }
                    char == '?' && type == TagType.XML -> move(State.XML_CLOSE)
                    eof -> { wantEnd(); move(State.DATA, false) }
                    else -> { if (char in "<=") seen(char.toString()); emit(Kind.ATT_VALUE); current(); move(State.UNQUOTED_VALUE) }
                }
                State.DOUBLE_VALUE, State.SINGLE_VALUE -> {
                    val quote = if (state == State.DOUBLE_VALUE) '"' else '\''
                    when {
                        char == quote -> move(State.AFTER_VALUE)
                        char == '&' -> { next(); reference(true, quote) }
                        eof -> { want(quote.toString()); move(State.DATA, false) }
                        else -> { current(); next() }
                    }
                }
                State.UNQUOTED_VALUE -> when {
                    white() -> move(State.BEFORE_NAME)
                    char == '&' -> { next(); reference(true) }
                    char == '>' -> tagEnd()
                    char == '?' && type == TagType.XML -> move(State.XML_CLOSE)
                    eof -> { wantEnd(); move(State.DATA, false) }
                    else -> { if (char in "\"'<=") seen(char.toString()); current(); next() }
                }
                State.AFTER_VALUE -> when {
                    white() -> move(State.BEFORE_NAME)
                    char == '/' -> move(State.SELF_CLOSE)
                    char == '>' -> tagEnd()
                    char == '?' && type == TagType.XML -> move(State.XML_CLOSE)
                    eof -> move(State.DATA, false)
                    else -> { seen(String(Character.toChars(cp))); move(State.BEFORE_NAME, false) }
                }
                State.SELF_CLOSE -> when {
                    type == TagType.XML -> { seen("/"); move(State.BEFORE_NAME, false) }
                    char == '>' -> { emit(Kind.END_CLOSE); move(State.DATA) }
                    eof -> { want(">"); move(State.DATA, false) }
                    else -> { seen("/"); move(State.BEFORE_NAME, false) }
                }
                State.BOGUS -> when {
                    char == '>' -> endComment(true)
                    eof -> endComment(false)
                    else -> { current(); next() }
                }
                State.DECL_OPEN -> when {
                    input.startsWith("--", offset) -> { consume("--"); emit(Kind.COMMENT); move(State.COMMENT_START, false) }
                    alpha() -> { type = TagType.DECL; emit(Kind.TAG); chars("!"); current(); move(State.TAG_NAME) }
                    input.startsWith("[CDATA[", offset) -> { consume("[CDATA["); move(State.CDATA, false) }
                    else -> { want("tag name"); emit(Kind.COMMENT); move(State.BOGUS, false) }
                }
                State.COMMENT_START -> when {
                    char == '-' -> move(State.COMMENT_START_DASH)
                    char == '>' -> { seen("<!-->"); endComment(true) }
                    eof -> { want("-->"); endComment(false) }
                    else -> { current(); move(State.COMMENT) }
                }
                State.COMMENT_START_DASH -> when {
                    char == '-' -> move(State.COMMENT_END)
                    char == '>' -> { seen("<!--->"); endComment(true) }
                    eof -> { want("-->"); endComment(false) }
                    else -> { chars("-"); current(); move(State.COMMENT) }
                }
                State.COMMENT -> when {
                    char == '-' -> move(State.COMMENT_END_DASH)
                    eof -> { want("-->"); endComment(false) }
                    else -> { current(); next() }
                }
                State.COMMENT_END_DASH -> when {
                    char == '-' -> move(State.COMMENT_END)
                    eof -> { want("-->"); endComment(false) }
                    else -> { chars("-"); current(); move(State.COMMENT) }
                }
                State.COMMENT_END -> when {
                    char == '>' -> endComment(true)
                    char == '-' -> { want("-->"); chars("-"); next() }
                    white() -> { seen("--"); chars("--"); current(); move(State.COMMENT_END_SPACE) }
                    char == '!' -> { seen("!"); move(State.COMMENT_END_BANG) }
                    eof -> { want("-->"); endComment(false) }
                    else -> { seen("--"); chars("--"); current(); move(State.COMMENT) }
                }
                State.COMMENT_END_BANG -> when {
                    char == '>' -> endComment(true)
                    char == '-' -> { chars("--!"); move(State.COMMENT_END_DASH) }
                    eof -> { want("-->"); endComment(false) }
                    else -> { chars("--!"); current(); move(State.COMMENT) }
                }
                State.COMMENT_END_SPACE -> when {
                    char == '>' -> endComment(true)
                    char == '-' -> move(State.COMMENT_END_DASH)
                    white() -> { current(); next() }
                    eof -> { want("-->"); endComment(false) }
                    else -> { current(); move(State.COMMENT) }
                }
                State.CDATA -> when {
                    input.startsWith("]]>", offset) -> { consume("]]>"); move(State.DATA, false) }
                    eof -> move(State.DATA, false)
                    else -> { current(); next() }
                }
                State.SCRIPT -> when {
                    input.startsWith("</", offset) && scriptAt(offset + 2) -> move(State.DATA, false)
                    eof -> return output
                    else -> { pos(); current(); next() }
                }
            }
        }
    }
}

private class Output(private val tokens: List<Out>, private val options: HtmlParseOptions, private val textMode: Boolean = false) {
    private var offset = 0
    private var position = HtmlPosition()
    private val pending = mutableListOf<HtmlTag>()
    private val result = mutableListOf<HtmlTag>()
    private val token get() = tokens.getOrNull(offset)
    private val kind get() = token?.kind
    private fun add(target: MutableList<HtmlTag>, tag: HtmlTag, at: HtmlPosition = position) {
        if (options.tagPosition) target.add(at.toTag())
        target.add(if (textMode) tag.mapStrings(::scalarText) else tag)
    }
    private fun warning(text: String, at: HtmlPosition) {
        if (options.tagWarning) add(result, HtmlTag.Warning(text), at)
    }
    private fun skip(expected: Kind) {
        check(kind == null || kind == expected) { "Expected $expected, found $token" }
        if (kind != null) offset++
    }
    private fun numeric(hex: Boolean, digits: String): String =
        (HtmlEntities.lookupNumericEntity((if (hex) "x" else "") + digits) ?: "?").let { if (textMode) scalarText(it) else it }
    private fun chars(entities: Boolean = false): String = buildString {
        while (true) {
            when (kind) {
                Kind.CHAR -> { append(token!!.text); offset++ }
                Kind.POS -> { position = token!!.position; offset++ }
                Kind.WARN -> { if (options.tagWarning) add(pending, HtmlTag.Warning(token!!.text)); offset++ }
                Kind.ENTITY_NAME if entities -> {
                    offset++
                    val name = this@Output.chars()
                    val semicolon = checkNotNull(token).semicolon
                    skip(Kind.ENTITY_END)
                    val (text, warnings) = options.entityAttribute(name, semicolon)
                    append(if (textMode) scalarText(text) else text)
                    warnings.forEach { add(pending, it) }
                }
                in setOf(Kind.ENTITY_NUM, Kind.ENTITY_HEX) if entities -> {
                    val hex = kind == Kind.ENTITY_HEX
                    offset++
                    append(numeric(hex, this@Output.chars()))
                    skip(Kind.ENTITY_END)
                }
                else -> break
            }
        }
    }
    private fun attributes(): List<Pair<String, String>> = buildList {
        while (true) when (kind) {
            Kind.ATT_NAME -> {
                offset++
                val name = this@Output.chars()
                val value = if (kind == Kind.ATT_VALUE) { offset++; chars(true) } else ""
                add(name to value)
            }
            Kind.ATT_VALUE -> { offset++; add("" to chars(true)) }
            else -> return@buildList
        }
    }

    fun parse(): List<HtmlTag> {
        while (true) {
            if (options.tagWarning) result.addAll(pending)
            pending.clear()
            val start = position
            when (kind) {
                Kind.POS -> { position = token!!.position; offset++ }
                Kind.CHAR -> add(result, HtmlTag.Text(chars()), start)
                Kind.TAG, Kind.SHUT -> {
                    val closing = kind == Kind.SHUT
                    offset++
                    val name = this@Output.chars()
                    val attributes = attributes()
                    val selfClosing = kind == Kind.END_CLOSE
                    if (closing) {
                        add(result, HtmlTag.Close(name), start)
                        if (attributes.isNotEmpty()) warning("Unexpected attributes in close tag", start)
                        if (selfClosing) warning("Unexpected self-closing in close tag", start)
                    } else {
                        add(result, HtmlTag.Open(name, attributes), start)
                        if (selfClosing) add(result, HtmlTag.Close(name), start)
                    }
                    skip(if (selfClosing) Kind.END_CLOSE else Kind.END)
                }
                Kind.COMMENT -> { offset++; add(result, HtmlTag.Comment(chars()), start); skip(Kind.COMMENT_END) }
                Kind.ENTITY_NAME -> {
                    offset++
                    val name = this@Output.chars()
                    val semicolon = checkNotNull(token).semicolon
                    for (tag in options.entityData(name, semicolon)) {
                        if (options.tagWarning || tag !is HtmlTag.Warning) add(result, tag, start)
                    }
                    skip(Kind.ENTITY_END)
                }
                Kind.ENTITY_NUM, Kind.ENTITY_HEX -> {
                    val hex = kind == Kind.ENTITY_HEX
                    offset++
                    add(result, HtmlTag.Text(numeric(hex, this@Output.chars())), start)
                    skip(Kind.ENTITY_END)
                }
                Kind.WARN -> { warning(token!!.text, start); offset++ }
                null -> return if (options.tagTextMerge) mergeText(result) else result
                else -> error("Unexpected TagSoup intermediate token: $token")
            }
        }
    }
}

/** Warnings do not break a text run; they follow the entire merged run in original order. */
private fun mergeText(tags: List<HtmlTag>): List<HtmlTag> = buildList {
    var offset = 0
    while (offset < tags.size) {
        val tag = tags[offset++]
        if (tag !is HtmlTag.Text) { add(tag); continue }
        val text = StringBuilder(tag.text)
        val warnings = mutableListOf<HtmlTag>()
        while (offset < tags.size) {
            val next = tags[offset]
            when {
                next is HtmlTag.Text -> { text.append(next.text); offset++ }
                next is HtmlTag.Warning -> { warnings.add(next); offset++ }
                next is HtmlTag.Position && tags.getOrNull(offset + 1) is HtmlTag.Text -> {
                    text.append((tags[offset + 1] as HtmlTag.Text).text); offset += 2
                }
                next is HtmlTag.Position && tags.getOrNull(offset + 1) is HtmlTag.Warning -> {
                    warnings.add(next); warnings.add(tags[offset + 1]); offset += 2
                }
                else -> break
            }
        }
        add(HtmlTag.Text(text.toString()))
        addAll(warnings)
    }
}
