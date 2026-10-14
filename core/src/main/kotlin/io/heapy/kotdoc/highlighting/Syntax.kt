// Skylighting.Types 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.ast.UnicodeText
import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.highlighting.regex.RE
import java.util.Collections
import java.util.TreeSet

internal fun <A> syntaxList(values: Iterable<A>): List<A> = Collections.unmodifiableList(values.toList())
internal fun syntaxChars(values: Iterable<Int>): Set<Int> = if (values is BinaryAscSet<Int>) values else Collections.unmodifiableSet(TreeSet<Int>().apply {
    values.forEach { require(it in 0..0x10ffff) { "Not a Haskell Char: $it" }; add(it) }
})
internal fun <A> syntaxMap(values: Map<String, A>): Map<String, A> = if (values is BinaryAscMap<String, A> || values is ImmutableOrderedSyntaxMap) values else Collections.unmodifiableMap(values.toSortedMap(UnicodeTextOrder))
internal fun <A, B> syntaxMapValues(values: Map<String, A>, transform: (A) -> B): Map<String, B> =
    if (values is BinaryAscMap<String, A>) BinaryAscMap(values.entries.map { it.key to transform(it.value) }, UnicodeTextOrder)
    else if (values is ImmutableOrderedSyntaxMap) LazySyntaxMap(values.keys) { key -> transform(values.getValue(key)) }
    else values.mapValues { transform(it.value) }

/** Structural instances use source field order and Unicode scalar ordering. */
abstract class SyntaxValue<T : SyntaxValue<T>> : Comparable<T> {
    internal abstract val fields: List<Any?>
    final override fun compareTo(other: T): Int = syntaxCompare(fields, other.fields)
    final override fun equals(other: Any?): Boolean = other is SyntaxValue<*> && javaClass == other.javaClass && syntaxCompare(fields, other.fields) == 0
    final override fun hashCode(): Int {
        var hash = 1
        val stack = ArrayDeque<List<Any?>>()
        stack.addLast(fields)
        while (stack.isNotEmpty()) for (field in stack.removeLast().asReversed()) when (field) {
            is SyntaxValue<*> -> stack.addLast(field.fields)
            is Map<*, *> -> stack.addLast(field.entries.flatMap { listOf(it.key, it.value) })
            is Iterable<*> -> stack.addLast(field.toList())
            else -> hash = hash * 31 + (field?.hashCode() ?: 0)
        }
        return hash
    }
    final override fun toString(): String = syntaxShow(this)
    fun haskellShow(precedence: Int = 0): String = syntaxShow(this, precedence)
}

internal fun syntaxCompare(a: Any?, b: Any?): Int {
    // An explicit stack keeps deeply nested Rule children usable.
    val stack = ArrayDeque<Pair<Any?, Any?>>()
    stack.addLast(a to b)
    fun sequences(x: Iterable<*>, y: Iterable<*>) {
        val xs = x.toList(); val ys = y.toList()
        stack.addLast(xs.size to ys.size)
        for (i in (0..<minOf(xs.size, ys.size)).reversed()) stack.addLast(xs[i] to ys[i])
    }
    while (stack.isNotEmpty()) {
        val (x, y) = stack.removeLast()
        if (x === y) continue
        val cmp = when {
            x == null -> -1
            y == null -> 1
            x is SyntaxValue<*> && y is SyntaxValue<*> -> { sequences(x.fields, y.fields); 0 }
            x is WordSet<*> && y is WordSet<*> -> x.compareErased(y)
            x is Map<*, *> && y is Map<*, *> -> { sequences(x.entries.map { listOf(it.key, it.value) }, y.entries.map { listOf(it.key, it.value) }); 0 }
            x is Iterable<*> && y is Iterable<*> -> { sequences(x, y); 0 }
            x is String && y is String -> UnicodeTextOrder.compare(x, y)
            x is HaskellChars && y is HaskellChars -> { sequences(x.points, y.points); 0 }
            x is Int && y is Int -> x.compareTo(y)
            x is Long && y is Long -> x.compareTo(y)
            x is Boolean && y is Boolean -> x.compareTo(y)
            x is TokenType && y is TokenType -> x.compareTo(y)
            x is RE && y is RE -> x.compareTo(y)
            else -> error("Incomparable syntax fields: ${x.javaClass}, ${y.javaClass}")
        }
        if (cmp != 0) return cmp
    }
    return 0
}

/** Explicit dictionary for Haskell's Ord and FoldCase constraints. */
interface WordOperations<A> : Comparator<A> {
    fun foldCase(value: A): A
    fun haskellShow(value: A): String
}
object TextWordOperations : WordOperations<String> {
    override fun compare(a: String, b: String) = UnicodeTextOrder.compare(a, b)
    override fun foldCase(value: String) = UnicodeText.toCaseFold(value)
    override fun haskellShow(value: String) = HaskellString.quote(value)
}

sealed class WordSet<A>(words: Iterable<A>, val operations: WordOperations<A>) : Comparable<WordSet<A>> {
    val words: Set<A> = if (words is BinaryAscSet<A>) words else Collections.unmodifiableSet(TreeSet(operations).apply { addAll(words.toList()) })
    abstract val caseSensitive: Boolean
    class CaseSensitiveWords<A>(words: Iterable<A>, operations: WordOperations<A>) : WordSet<A>(words, operations) { override val caseSensitive = true }
    // The raw constructor deliberately does not fold the stored words.
    class CaseInsensitiveWords<A>(words: Iterable<A>, operations: WordOperations<A>) : WordSet<A>(words, operations) { override val caseSensitive = false }
    operator fun contains(word: A) = words.contains(if (caseSensitive) word else operations.foldCase(word))
    override fun compareTo(other: WordSet<A>): Int {
        val tag = (!caseSensitive).compareTo(!other.caseSensitive)
        if (tag != 0) return tag
        val a = words.iterator(); val b = other.words.iterator()
        while (a.hasNext() && b.hasNext()) {
            val cmp = operations.compare(a.next(), b.next()); if (cmp != 0) return cmp
        }
        return a.hasNext().compareTo(b.hasNext())
    }
    @Suppress("UNCHECKED_CAST")
    internal fun compareErased(other: WordSet<*>) = compareTo(other as WordSet<A>)
    override fun equals(other: Any?) = other is WordSet<*> && caseSensitive == other.caseSensitive && words == other.words
    override fun hashCode() = 31 * words.hashCode() + caseSensitive.hashCode()
    fun haskellShow(precedence: Int = 0): String {
        val name = if (caseSensitive) "CaseSensitiveWords" else "CaseInsensitiveWords"
        val text = "$name (fromList " + words.joinToString(",", "[", "]", transform = operations::haskellShow) + ")"
        return if (precedence > 10) "($text)" else text
    }
    override fun toString() = haskellShow()
    companion object {
        fun <A> make(caseSensitive: Boolean, words: Iterable<A>, operations: WordOperations<A>): WordSet<A> =
            if (caseSensitive) CaseSensitiveWords(words, operations) else CaseInsensitiveWords(words.map(operations::foldCase), operations)
        fun text(caseSensitive: Boolean, words: Iterable<String>) = make(caseSensitive, words, TextWordOperations)
    }
}

class ContextName private constructor(syntaxThunk: Lazy<String>, contextThunk: Lazy<String>) : SyntaxValue<ContextName>() {
    constructor(syntax: String, context: String) : this(lazyOf(syntax), lazyOf(context))
    val syntax: String by syntaxThunk
    val context: String by contextThunk
    override val fields get() = listOf(syntax, context)
    companion object {
        internal fun deferred(syntax: () -> String, context: () -> String) = ContextName(lazy(syntax), lazy(context))
    }
}
class KeywordAttr(val caseSensitive: Boolean, delimiters: Iterable<Int>) : SyntaxValue<KeywordAttr>() {
    val delimiters = syntaxChars(delimiters)
    override val fields get() = listOf(caseSensitive, delimiters)
}
sealed class KeywordWords : SyntaxValue<KeywordWords>() {
    class Unresolved(val name: String) : KeywordWords() { override val fields get() = listOf(0, name) }
    class Resolved(val words: WordSet<String>) : KeywordWords() { override val fields get() = listOf(1, words) }
}

sealed class Matcher : SyntaxValue<Matcher>() {
    class DetectChar(val char: kotlin.Int) : Matcher() { init { require(char in 0..0x10ffff) }; override val fields get() = listOf(0, char) }
    class Detect2Chars(val char: kotlin.Int, val char1: kotlin.Int) : Matcher() { init { require(char in 0..0x10ffff && char1 in 0..0x10ffff) }; override val fields get() = listOf(1, char, char1) }
    class AnyChar(chars: Iterable<kotlin.Int>) : Matcher() { val chars = syntaxChars(chars); override val fields get() = listOf(2, chars) }
    class RangeDetect(val char: kotlin.Int, val char1: kotlin.Int) : Matcher() { init { require(char in 0..0x10ffff && char1 in 0..0x10ffff) }; override val fields get() = listOf(3, char, char1) }
    class StringDetect(val text: String) : Matcher() { override val fields get() = listOf(4, text) }
    class WordDetect(val text: String) : Matcher() { override val fields get() = listOf(5, text) }
    class RegExpr(val regex: RE) : Matcher() { override val fields get() = listOf(6, regex) }
    class Keyword(val attributes: KeywordAttr, val words: KeywordWords) : Matcher() { override val fields get() = listOf(7, attributes, words) }
    object Int : Matcher() { override val fields get() = listOf(8) }
    object Float : Matcher() { override val fields get() = listOf(9) }
    object HlCOct : Matcher() { override val fields get() = listOf(10) }
    object HlCHex : Matcher() { override val fields get() = listOf(11) }
    object HlCStringChar : Matcher() { override val fields get() = listOf(12) }
    object HlCChar : Matcher() { override val fields get() = listOf(13) }
    class LineContinue(val char: kotlin.Int) : Matcher() { init { require(char in 0..0x10ffff) }; override val fields get() = listOf(14, char) }
    class IncludeRules(val context: ContextName) : Matcher() { override val fields get() = listOf(15, context) }
    object DetectSpaces : Matcher() { override val fields get() = listOf(16) }
    object DetectIdentifier : Matcher() { override val fields get() = listOf(17) }
}
sealed class ContextSwitch : SyntaxValue<ContextSwitch>() {
    object Pop : ContextSwitch() { override val fields get() = listOf(0) }
    class Push(val context: ContextName) : ContextSwitch() { override val fields get() = listOf(1, context) }
}
sealed class ListItem : SyntaxValue<ListItem>() {
    class Item(val text: String) : ListItem() { override val fields get() = listOf(0, text) }
    class IncludeList(val context: ContextName) : ListItem() { override val fields get() = listOf(1, context) }
}

class Rule(
    val matcher: Matcher,
    val attribute: TokenType,
    val includeAttribute: Boolean,
    wordDelimiters: Iterable<Int>,
    val dynamic: Boolean,
    val caseSensitive: Boolean,
    children: Iterable<Rule>,
    val lookahead: Boolean,
    val firstNonspace: Boolean,
    val column: Long?,
    contextSwitch: Iterable<ContextSwitch>,
) : SyntaxValue<Rule>() {
    val wordDelimiters = syntaxChars(wordDelimiters)
    val children = syntaxList(children)
    val contextSwitch = syntaxList(contextSwitch)
    override val fields get() = listOf(matcher, attribute, includeAttribute, wordDelimiters, dynamic, caseSensitive, children, lookahead, firstNonspace, column, contextSwitch)
    fun withMatcher(matcher: Matcher) = Rule(matcher, attribute, includeAttribute, wordDelimiters, dynamic, caseSensitive, children, lookahead, firstNonspace, column, contextSwitch)
}
class Context(
    val name: String,
    val syntax: String,
    rules: Iterable<Rule>,
    val attribute: TokenType,
    lineEmptyContext: Iterable<ContextSwitch>,
    lineEndContext: Iterable<ContextSwitch>,
    val fallthrough: Boolean,
    fallthroughContext: Iterable<ContextSwitch>,
    val dynamic: Boolean,
) : SyntaxValue<Context>() {
    val rules = syntaxList(rules)
    val lineEmptyContext = syntaxList(lineEmptyContext)
    val lineEndContext = syntaxList(lineEndContext)
    val fallthroughContext = syntaxList(fallthroughContext)
    override val fields get() = listOf(name, syntax, rules, attribute, lineEmptyContext, lineEndContext, fallthrough, fallthroughContext, dynamic)
    fun withRules(rules: Iterable<Rule>) = Context(name, syntax, rules, attribute, lineEmptyContext, lineEndContext, fallthrough, fallthroughContext, dynamic)
}
internal class HaskellChars(points: Iterable<Int>) {
    val points = syntaxList(points)
    val text get() = buildString { points.forEach { appendCodePoint(it) } }
    constructor(text: String) : this(text.codePoints().toArray().asIterable())
    override fun equals(other: Any?) = other is HaskellChars && points == other.points
    override fun hashCode() = points.hashCode()
}
class Syntax private constructor(
    val name: String,
    internal val filenameValue: HaskellChars,
    val shortname: String,
    lists: Map<String, List<ListItem>>,
    contexts: Map<String, Context>,
    val author: String,
    val version: String,
    val license: String,
    extensionValues: Iterable<HaskellChars>,
    val startingContext: String,
) : SyntaxValue<Syntax>() {
    constructor(name: String, filename: String, shortname: String, lists: Map<String, List<ListItem>>, contexts: Map<String, Context>,
        author: String, version: String, license: String, extensions: Iterable<String>, startingContext: String) :
        this(name, HaskellChars(filename), shortname, lists, contexts, author, version, license, extensions.map(::HaskellChars), startingContext)
    val filename get() = filenameValue.text
    val lists = syntaxMap(syntaxMapValues(lists) { syntaxList(it) })
    val contexts = syntaxMap(contexts)
    internal val extensionValues = syntaxList(extensionValues)
    val extensions = syntaxList(this.extensionValues.map { it.text })
    override val fields get() = listOf(name, filenameValue, shortname, lists, contexts, author, version, license, extensionValues, startingContext)
    fun withContexts(contexts: Map<String, Context>) = Syntax(name, filenameValue, shortname, lists, contexts, author, version, license, extensionValues, startingContext)
    internal companion object {
        fun fromBinary(name: String, filename: List<Int>, shortname: String, lists: Map<String, List<ListItem>>, contexts: Map<String, Context>,
            author: String, version: String, license: String, extensions: List<List<Int>>, startingContext: String) =
            Syntax(name, HaskellChars(filename), shortname, lists, contexts, author, version, license, extensions.map(::HaskellChars), startingContext)
    }
}
typealias SyntaxMap = Map<String, Syntax>

private class ShownChar(val point: Int)
private class ShownChars(val chars: Set<Int>)
private class ShownMaybe(val value: Long?)
private class ShowTask(val value: Any?, val precedence: Int = 0)
internal fun syntaxShow(value: Any?, precedence: Int = 0): String = buildString {
    val stack = ArrayDeque<Any>(); stack.addLast(ShowTask(value, precedence))
    fun schedule(parts: List<Any>) { parts.asReversed().forEach(stack::addLast) }
    fun app(name: String, p: Int, vararg fields: Any?) {
        val parts = mutableListOf<Any>(); if (p > 10 && fields.isNotEmpty()) parts.add("(")
        parts.add(name); fields.forEach { parts.add(" "); parts.add(ShowTask(it, 11)) }
        if (p > 10 && fields.isNotEmpty()) parts.add(")"); schedule(parts)
    }
    fun record(name: String, p: Int, names: List<String>, fields: List<Any?>) {
        val parts = mutableListOf<Any>(); if (p > 10) parts.add("(")
        parts.add("$name {"); names.forEachIndexed { i, n ->
            if (i != 0) parts.add(", "); parts.add("$n = "); parts.add(ShowTask(fields[i]))
        }; parts.add("}"); if (p > 10) parts.add(")"); schedule(parts)
    }
    fun sequence(values: Iterable<*>, start: String, end: String) {
        val parts = mutableListOf<Any>(start)
        values.forEachIndexed { i, v -> if (i != 0) parts.add(","); parts.add(ShowTask(v)) }; parts.add(end); schedule(parts)
    }
    while (stack.isNotEmpty()) when (val task = stack.removeLast()) {
        is String -> append(task)
        is ShowTask -> {
            val v = task.value; val p = task.precedence
            when (v) {
                null -> append("Nothing")
                is String -> append(HaskellString.quote(v))
                is HaskellChars -> append(HaskellString.quoteCodePoints(v.points.toIntArray()))
                is Boolean -> append(if (v) "True" else "False")
                is Int -> append(v)
                is Long -> if (v < 0 && p > 6) append("($v)") else append(v)
                is TokenType -> append(v.name)
                is RE -> append(v.haskellShow(p))
                is WordSet<*> -> append(v.haskellShow(p))
                is ShownChar -> {
                    val quoted = when (v.point) {
                        39 -> "\\'"; 34 -> "\""
                        else -> HaskellString.quote(buildString { appendCodePoint(v.point) }).drop(1).dropLast(1)
                    }
                    append("'$quoted'")
                }
                is ShownMaybe -> if (v.value == null) append("Nothing") else app("Just", p, v.value)
                is ShownChars -> {
                    val text = HaskellString.quoteCodePoints(v.chars.toIntArray())
                    if (p > 10) append('('); append("fromList $text"); if (p > 10) append(')')
                }
                is ContextName -> sequence(v.fields, "(", ")")
                is KeywordAttr -> record("KeywordAttr", p, listOf("keywordCaseSensitive", "keywordDelims"), listOf(v.caseSensitive, ShownChars(v.delimiters)))
                is KeywordWords.Unresolved -> app("Left", p, v.name)
                is KeywordWords.Resolved -> app("Right", p, v.words)
                is Matcher.DetectChar -> app("DetectChar", p, ShownChar(v.char))
                is Matcher.Detect2Chars -> app("Detect2Chars", p, ShownChar(v.char), ShownChar(v.char1))
                is Matcher.AnyChar -> app("AnyChar", p, ShownChars(v.chars))
                is Matcher.RangeDetect -> app("RangeDetect", p, ShownChar(v.char), ShownChar(v.char1))
                is Matcher.StringDetect -> app("StringDetect", p, v.text)
                is Matcher.WordDetect -> app("WordDetect", p, v.text)
                is Matcher.RegExpr -> app("RegExpr", p, v.regex)
                is Matcher.Keyword -> app("Keyword", p, v.attributes, v.words)
                is Matcher.LineContinue -> app("LineContinue", p, ShownChar(v.char))
                is Matcher.IncludeRules -> app("IncludeRules", p, v.context)
                is Matcher -> append(v.javaClass.simpleName)
                ContextSwitch.Pop -> append("Pop")
                is ContextSwitch.Push -> app("Push", p, v.context)
                is ListItem.Item -> app("Item", p, v.text)
                is ListItem.IncludeList -> app("IncludeList", p, v.context)
                is Rule -> record("Rule", p, listOf("rMatcher", "rAttribute", "rIncludeAttribute", "rWordDelimiters", "rDynamic", "rCaseSensitive", "rChildren", "rLookahead", "rFirstNonspace", "rColumn", "rContextSwitch"),
                    listOf(v.matcher, v.attribute, v.includeAttribute, ShownChars(v.wordDelimiters), v.dynamic, v.caseSensitive, v.children, v.lookahead, v.firstNonspace, ShownMaybe(v.column), v.contextSwitch))
                is Context -> record("Context", p, listOf("cName", "cSyntax", "cRules", "cAttribute", "cLineEmptyContext", "cLineEndContext", "cFallthrough", "cFallthroughContext", "cDynamic"), v.fields)
                is Syntax -> record("Syntax", p, listOf("sName", "sFilename", "sShortname", "sLists", "sContexts", "sAuthor", "sVersion", "sLicense", "sExtensions", "sStartingContext"), v.fields)
                is Map<*, *> -> {
                    val parts = mutableListOf<Any>(); if (p > 10) parts.add("("); parts.add("fromList [")
                    v.entries.forEachIndexed { i, e ->
                        if (i != 0) parts.add(","); parts.add("("); parts.add(ShowTask(e.key)); parts.add(","); parts.add(ShowTask(e.value)); parts.add(")")
                    }; parts.add("]"); if (p > 10) parts.add(")"); schedule(parts)
                }
                is Set<*> -> {
                    val parts = mutableListOf<Any>(); if (p > 10) parts.add("("); parts.add("fromList "); parts.add(ShowTask(v.toList())); if (p > 10) parts.add(")"); schedule(parts)
                }
                is Iterable<*> -> sequence(v, "[", "]")
                else -> error("Unknown syntax field: $v")
            }
        }
    }
}
