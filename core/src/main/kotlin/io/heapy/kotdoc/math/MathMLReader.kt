// Text.TeXMath.Readers.MathML 0.13.3.1, © 2014 Matthew Pickering.
// GPL-2.0-or-later; unchanged source and full notice in vendor/texmath-0.13.3.1.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.UnicodeText
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.math.MathExp.*
import io.heapy.kotdoc.math.MathSymbolType.*
import io.heapy.kotdoc.xml.*
import kotlinx.serialization.json.*
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

sealed class MathMLResult {
    class Success(expressions: List<MathExp>) : MathMLResult() { val expressions = ownMathList(expressions) }
    class Failure(val error: String) : MathMLResult()
}

/** Exact MathML entity table; epsilon/varepsilon intentionally follow texmath. */
object MathMLEntities {
    val entries: Map<String, String> by lazy {
        Collections.unmodifiableMap(Json.parseToJsonElement(checkNotNull(javaClass.getResourceAsStream("/io/heapy/kotdoc/mathml-entities.json"))
            .bufferedReader().use { it.readText() }).jsonObject.mapValues { it.value.jsonPrimitive.content })
    }
    fun getUnicode(entity: String): String? = entries[TextChars.fromString(entity)]
}

object MathMLReader {
    fun readMathML(input: String): MathMLResult = try {
        val root = LegacyXmlInput.parseXMLDoc(TextChars.fromString(input)) ?: throw MmlError("Invalid XML")
        if (name(root) != "math") throw MmlError("Root must be math element")
        val e = Session().read(root)
        MathMLResult.Success((if (e is EGrouped) e.expressions else listOf(e)).map(MathShared::fixTree))
    } catch (error: MmlError) { MathMLResult.Failure(error.detail) }

    private class MmlError(val detail: String) : RuntimeException(null, null, false, false)
    private data class State(val attrs: List<LegacyXmlAttribute> = emptyList(), val position: FormType? = null,
        val accent: Boolean = false, val style: MathTextType = MathTextType.TextNormal)
    private typealias Constructor = (MathExp, MathExp) -> MathExp
    private sealed interface Ir<out A> {
        class Value<A>(val value: A) : Ir<A>
        class Stretchy(val type: MathSymbolType, val constructor: (String) -> MathExp, val text: String) : Ir<Nothing>
        class Trailing(val constructor: Constructor, val expression: MathExp) : Ir<Nothing>
    }
    private class Session {
        private var state = State()
        private val work = ArrayDeque<() -> Unit>()
        private val space = IdentityHashMap<LegacyXmlElement, Boolean>()

        fun read(root: LegacyXmlElement): MathExp {
            var completed: Result<MathExp>? = null
            val action: suspend () -> MathExp = { row(root) }
            work.addLast { action.startCoroutine(object : Continuation<MathExp> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<MathExp>) { completed = result }
            }) }
            while (work.isNotEmpty()) work.removeLast().invoke()
            return checkNotNull(completed).getOrThrow()
        }

        private suspend fun <A> local(change: (State) -> State, action: suspend () -> A): A {
            val saved = state; state = change(saved)
            return try { action() } finally { state = saved }
        }
        private fun attr(key: String, e: LegacyXmlElement): String? {
            val n = LegacyXmlName(key)
            return e.attributes.firstOrNull { it.name == n }?.value?.let(CharacterStringChars::build)
                ?: if (name(e) == "mfenced" && key in listOf("open", "close", "separators")) null
                else state.attrs.firstOrNull { it.name == LegacyXmlName(key.lowercase()) }?.value?.let(CharacterStringChars::build)
        }
        private fun getString(e: LegacyXmlElement): String {
            val text = buildString {
                for (c in e.content) when (c) {
                    is LegacyXmlText -> append(CharacterStringChars.build(c.text))
                    is LegacyXmlCRef -> {
                        val s = CharacterStringChars.build(c.reference); append(MathMLEntities.getUnicode(s) ?: s)
                    }
                    else -> Unit
                }
            }.trim { it == ' ' || it == '\t' || it == '\n' }
            return MathUnicode.fromUnicode(state.style, text)
        }
        // Queue both descent and resumption: deeply nested ReaderT scopes retain
        // their original order without consuming the JVM call stack or a thread.
        private suspend fun expr(e: LegacyXmlElement): List<Ir<MathExp>> = suspendCoroutine { caller ->
            val action: suspend () -> List<Ir<MathExp>> = {
                local({ s -> s.copy(attrs = e.attributes.map { a ->
                    if (CharacterStringChars.build(a.name.local) == "accentunder") LegacyXmlAttribute(LegacyXmlName("accent"), a.value) else a
                } + s.attrs) }) { expr0(e) }
            }
            work.addLast { action.startCoroutine(object : Continuation<List<Ir<MathExp>>> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<List<Ir<MathExp>>>) {
                    work.addLast { caller.resumeWith(result) }
                }
            }) }
        }
        private suspend fun expr0(e: LegacyXmlElement): List<Ir<MathExp>> {
            fun one(v: MathExp) = listOf(Ir.Value(v))
            return when (name(e)) {
                "mi" -> {
                    val s = getString(e); val operator = EMathOperator(s)
                    val base = if (MathShared.getOperator(operator) != null) operator else EIdentifier(s)
                    val tt = attr("mathvariant", e)?.let(MathShared::getTextType)
                    one(if (tt == null || tt == state.style) base else EStyled(tt, listOf(base)))
                }
                "mn" -> one(ENumber(getString(e)))
                "mo" -> listOf(op(e))
                "mtext" -> {
                    val tt = attr("mathvariant", e)?.let(MathShared::getTextType) ?: MathTextType.TextNormal
                    val s = getString(e); val points = s.codePoints().toArray()
                    val width = if (tt == MathTextType.TextNormal && points.size == 1) MathShared.getSpaceWidth(points[0]) else null
                    one(if (width == null) EText(tt, s) else ESpace(width))
                }
                "ms" -> one(EText(attr("mathvariant", e)?.let(MathShared::getTextType) ?: MathTextType.TextNormal,
                    (attr("lquote", e) ?: "“") + getString(e) + (attr("rquote", e) ?: "”")))
                "mspace" -> one(ESpace(width(attr("width", e) ?: "0.0em")))
                "mrow", "mpadded" -> one(row(e))
                "mstyle" -> {
                    val tt = attr("mathvariant", e)?.let(MathShared::getTextType) ?: MathTextType.TextNormal
                    val original = state.style
                    val body = local({ it.copy(style = tt, attrs = it.attrs.filterNot { a -> a.name == LegacyXmlName("mathvariant") }) }) { row(e) }
                    one(if (original == tt) body else EStyled(tt, listOf(body)))
                }
                "mfrac" -> {
                    val cs = args(e, 2); val a = safeExpr(cs[0]); val b = safeExpr(cs[1])
                    val line = attr("linethickness", e)
                    one(EFraction(if (line != null && thickness(line) == MathRational(0)) FractionType.NoLineFrac else FractionType.NormalFrac, a, b))
                }
                "msqrt" -> one(ESqrt(row(e)))
                "mroot" -> { val cs = args(e, 2); val base = safeExpr(cs[0]); val index = safeExpr(cs[1]); one(ERoot(index, base)) }
                "merror", "maligngroup", "malignmark" -> one(MathShared.empty)
                "mphantom" -> one(EPhantom(row(e)))
                "mfenced" -> one(fenced(e))
                "menclose" -> one(when (attr("notation", e)) {
                    "box" -> EBoxed(row(e))
                    "updiagonalstrike" -> ECancel(StrokeType.ForwardSlash, row(e))
                    "downdiagonalstrike" -> ECancel(StrokeType.BackSlash, row(e))
                    "updiagonalstrike downdiagonalstrike" -> ECancel(StrokeType.XSlash, row(e))
                    else -> row(e)
                })
                "msub", "msup" -> { val cs = args(e, 2); reorderScripts(cs[0], cs[1], if (name(e) == "msub") ::ESub else ::ESuper) }
                "msubsup" -> { val cs = args(e, 3); one(ESubsup(safeExpr(cs[0]), postfix(cs[1]), postfix(cs[2]))) }
                "munder" -> { val cs = args(e, 2); one(EUnder(false, safeExpr(cs[0]), postfix(cs[1]))) }
                "mover" -> { val cs = args(e, 2); one(EOver(false, safeExpr(cs[0]), postfix(cs[1]))) }
                "munderover" -> { val cs = args(e, 3); one(EUnderover(false, safeExpr(cs[0]), postfix(cs[1]), postfix(cs[2]))) }
                "mtable" -> one(table(e))
                "maction" -> {
                    val selection = attr("selection", e)?.let { HaskellNumber.readInteger(it)?.toLong() } ?: 1L
                    val dropped = (selection - 1).coerceAtLeast(0)
                    val selected = if (dropped < children(e).size) children(e)[dropped.toInt()] else null
                    one(safeExpr(selected ?: throw MmlError("Selection out of range")))
                }
                "semantics" -> {
                    val cs = children(e); if (cs.isEmpty()) throw MmlError("")
                    val first = safeExpr(cs[0])
                    if (!MathShared.isEmpty(first)) one(first) else {
                        // mapM evaluates every candidate, even after the first succeeds.
                        val alternatives = cs.drop(1).map { annotation ->
                            if (attr("encoding", annotation) in listOf("application/mathml-presentation+xml", "MathML-Presentation")) row(annotation) else null
                        }
                        one(alternatives.firstOrNull { it != null } ?: MathShared.empty)
                    }
                }
                "mmultiscripts" -> one(multiscripts(e))
                else -> throw MmlError("Unexpected element " + errorElement(e))
            }
        }
        private fun op(e: LegacyXmlElement): Ir<MathExp> {
            val inferred = when (attr("form", e)) { "infix" -> FormType.FInfix; "prefix" -> FormType.FPrefix; "postfix" -> FormType.FPostfix; else -> null }
                ?: state.position ?: throw MmlError("Did not find an inferred position")
            val s = getString(e); val operator = MathMLOperators.getMathMLOperator(s, inferred)
            val props = listOf("fence", "accent", "stretchy").filter { p -> attr(p, e)?.let { it == "true" } ?: (operator?.properties?.contains(p) == true) }
            val position = when (operator?.form ?: inferred) { FormType.FPrefix -> Open; FormType.FPostfix -> Close; FormType.FInfix -> Op }
            val points = s.codePoints().toArray()
            val constructor: (String) -> MathExp = when {
                "fence" in props -> { t -> ESymbol(position, t) }
                "accent" in props -> { t -> ESymbol(Accent, t) }
                points.size == 1 -> { t -> ESymbol(MathTeXUnicode.getSymbolType(points[0]), t) }
                operator != null -> { t -> ESymbol(Ord, t) }
                else -> ::EMathOperator
            }
            return if ("stretchy" in props && !state.accent) Ir.Stretchy(position, constructor, s) else Ir.Value(constructor(s))
        }
        private fun spacelike(e: LegacyXmlElement): Boolean {
            val todo = ArrayDeque<Pair<LegacyXmlElement, Boolean>>()
            todo.addLast(e to false)
            while (todo.isNotEmpty()) {
                val (node, finish) = todo.removeLast()
                if (space.containsKey(node)) continue
                val tag = name(node)
                when {
                    tag in listOf("mtext", "mspace", "maligngroup", "malignmark") -> space[node] = true
                    tag !in listOf("mrow", "mstyle", "mphantom", "mpadded") -> space[node] = false
                    finish -> space[node] = children(node).all { checkNotNull(space[it]) }
                    else -> {
                        todo.addLast(node to true)
                        children(node).asReversed().forEach { todo.addLast(it to false) }
                    }
                }
            }
            return checkNotNull(space[e])
        }
        private suspend fun row(e: LegacyXmlElement): MathExp {
            val cs = children(e); val front = cs.takeWhile(::spacelike); val tail = cs.drop(front.size).takeLastWhile(::spacelike)
            val body = cs.subList(front.size, cs.size - tail.size)
            val first = front.flatMap { expr(it) }
            val middle = local({ it.copy(position = null) }) {
                var previous: FormType? = null
                body.flatMapIndexed { i, child ->
                    val pos = if (i == body.lastIndex) if (previous == null) FormType.FInfix else FormType.FPostfix
                        else if (previous == null) FormType.FPrefix else FormType.FInfix
                    previous = pos; local({ it.copy(position = pos) }) { expr(child) }
                }
            }
            val end = local({ it.copy(position = null) }) { tail.flatMap { expr(it) } }
            return mkExp(first + middle + end)
        }
        private suspend fun safeExpr(e: LegacyXmlElement) = mkExp(expr(e))
        private suspend fun postfix(e: LegacyXmlElement) = local({ it.copy(position = FormType.FPostfix, accent = true) }) { safeExpr(e) }
        private suspend fun reorderScripts(base: LegacyXmlElement, sub: LegacyXmlElement, constructor: Constructor): List<Ir<MathExp>> {
            val es = expr(base); val script = postfix(sub); val stretch = es.singleOrNull() as? Ir.Stretchy
            return when (stretch?.type) {
                Open -> listOf(stretch, Ir.Value(constructor(MathShared.empty, script)))
                Close -> listOf(Ir.Trailing(constructor, script), stretch)
                null -> listOf(Ir.Value(constructor(mkExp(es), script)))
                else -> listOf(stretch, Ir.Value(ESub(MathShared.empty, script)))
            }
        }
        private suspend fun fenced(e: LegacyXmlElement): MathExp {
            val open = attr("open", e) ?: "("; val close = attr("close", e) ?: ")"; val separators = (attr("separators", e) ?: ",").codePoints().toArray()
            val expanded = mutableListOf<LegacyXmlElement>()
            for ((i, child) in children(e).withIndex()) {
                if (i > 0 && separators.isNotEmpty()) expanded.add(LegacyXmlElement.textNode("mo", TextChars.fromPoints(intArrayOf(separators[minOf(i - 1, separators.lastIndex)]))))
                expanded.add(child)
            }
            return safeExpr(LegacyXmlElement.node("mrow", buildList {
                if (open.isNotEmpty()) add(LegacyXmlElement.textNode("mo", open))
                add(LegacyXmlElement.node("mrow", expanded))
                if (close.isNotEmpty()) add(LegacyXmlElement.textNode("mo", close))
            }))
        }
        private suspend fun multiscripts(e: LegacyXmlElement): MathExp {
            val cs = children(e); val at = cs.indexOfFirst { name(it) == "mprescripts" }.let { if (it < 0) cs.size else it }
            suspend fun part(x: LegacyXmlElement) = when (name(x)) { "none" -> MathShared.empty; "mrow" -> row(x); else -> safeExpr(x) }
            fun group(xs: List<MathExp>) = xs.singleOrNull() ?: EGrouped(xs)
            fun scripts(base: MathExp, xs: List<MathExp>): MathExp {
                val subs = xs.filterIndexed { i, _ -> i % 2 == 0 }; val sups = xs.filterIndexed { i, _ -> i % 2 == 1 }
                return when { subs.isEmpty() && sups.isEmpty() -> base; sups.isEmpty() -> ESub(base, group(subs)); subs.isEmpty() -> ESuper(base, group(sups)); else -> ESubsup(base, group(subs), group(sups)) }
            }
            val post = cs.take(at).map { part(it) }; val pres = cs.drop(at + 1).map { part(it) }
            return EGrouped(listOf(scripts(MathShared.empty, pres), scripts(post.firstOrNull() ?: MathShared.empty, post.drop(1))))
        }
        private suspend fun table(e: LegacyXmlElement): MathExp {
            fun alignment(s: String?) = when (s) { "left" -> MathAlignment.AlignLeft; "right" -> MathAlignment.AlignRight; else -> MathAlignment.AlignCenter }
            val default = alignment(attr("columnalign", e))
            val rows = children(e).map { r ->
                val a = attr("columnalign", r)?.let(::alignment) ?: default
                val cells = when (name(r)) { "mtr" -> children(r); "mlabeledtr" -> children(r).drop(1); else -> throw MmlError("Invalid Element: Only expecting mtr elements " + errorElement(r)) }
                cells.map { c ->
                    val ca = attr("columnalign", c)?.let(::alignment) ?: a
                    if (name(c) != "mtd") throw MmlError("Invalid Element: Only expecting mtd elements " + errorElement(c))
                    ca to listOf(row(c))
                }
            }
            val count = rows.maxOfOrNull { it.size } ?: 0
            val alignments = List(count) { i ->
                val values = rows.mapNotNull { it.getOrNull(i)?.first }
                if (values.distinct().size == 1) values[0] else MathAlignment.AlignCenter
            }
            return EArray(alignments, rows.map { r -> r.map { it.second } + List(count - r.size) { emptyList() } })
        }
    }
    private fun name(e: LegacyXmlElement) = UnicodeText.toLower(CharacterStringChars.build(e.name.local))
    private fun children(e: LegacyXmlElement) = e.content.filterIsInstance<LegacyXmlElement>()
    private fun errorElement(e: LegacyXmlElement) = name(e) + (e.line?.let { " line $it" } ?: "")
    private fun args(e: LegacyXmlElement, count: Int): List<LegacyXmlElement> = children(e).also {
        if (it.size != count) throw MmlError("Incorrect number of arguments for " + errorElement(e))
    }
    private fun width(s: String): MathRational {
        val names = listOf("veryverythinmathspace", "verythinmathspace", "thinmathspace", "mediummathspace", "thickmathspace", "verythickmathspace", "veryverythickmathspace")
        val negative = s.startsWith("negative"); val at = names.indexOf(if (negative) s.drop(8) else s)
        return if (at >= 0) MathRational((at + 1) * if (negative) -1L else 1L, 18) else MathShared.readLength(s) ?: MathRational(0)
    }
    private fun thickness(s: String) = when (s) { "thin" -> MathRational(3, 18); "medium" -> MathRational(1, 2); "thick" -> MathRational(1); else -> MathShared.readLength(s) ?: MathRational(1, 2) }
    private fun mkExp(es: List<Ir<MathExp>>): MathExp {
        val parts = toDelimited(matchNesting(es))
        return when {
            parts.isEmpty() -> MathShared.empty
            parts.any { it is DelimitedPart.Middle } -> if (parts.size == 1) ESymbol(Ord, (parts[0] as DelimitedPart.Middle).text) else EDelimited("", "", parts)
            parts.size == 1 -> (parts[0] as DelimitedPart.Expression).expression
            else -> EGrouped(parts.map { (it as DelimitedPart.Expression).expression })
        }
    }
    private fun toDelimited(es: List<Ir<DelimitedPart>>): List<DelimitedPart> {
        val one = es.singleOrNull() as? Ir.Stretchy
        if (one != null) return listOf(DelimitedPart.Expression(one.constructor(one.text)))
        return es.map { (it as? Ir.Value)?.value ?: error("removeIR, should only be ever called on processed lists") }
    }
    private fun removeStretch(es: List<Ir<MathExp>>): List<Ir<DelimitedPart>> {
        val one = es.singleOrNull() as? Ir.Stretchy
        if (one != null) return listOf(Ir.Value(DelimitedPart.Expression(one.constructor(one.text))))
        return es.map { when (it) { is Ir.Value -> Ir.Value(DelimitedPart.Expression(it.value)); is Ir.Stretchy -> Ir.Value(DelimitedPart.Middle(it.text)); is Ir.Trailing -> it } }
    }
    private fun trailing(open: Ir.Stretchy?, close: Ir.Stretchy?, es: List<Ir<DelimitedPart>>): MathExp {
        var end = es.size
        while (end > 0 && es[end - 1] is Ir.Trailing) end--
        var result = if (end == 0) when {
            open == null && close == null -> MathShared.empty
            close == null -> checkNotNull(open).constructor(open.text)
            open == null -> close.constructor(close.text)
            else -> EGrouped(listOf(open.constructor(open.text), close.constructor(close.text)))
        } else EDelimited(open?.text ?: "", close?.text ?: "", toDelimited(es.subList(0, end)))
        for (i in end until es.size) {
            val script = es[i] as Ir.Trailing
            result = script.constructor(result, script.expression)
        }
        return result
    }
    private fun matchNesting(es: List<Ir<MathExp>>): List<Ir<DelimitedPart>> {
        val work = ArrayDeque<() -> Unit>()
        val results = ArrayDeque<List<Ir<DelimitedPart>>>()
        fun visit(xs: List<Ir<MathExp>>) {
            work.addLast {
                val at = xs.indexOfFirst { it is Ir.Stretchy && it.type in listOf(Open, Close) }
                if (at < 0) results.addLast(removeStretch(xs)) else {
                    val fence = xs[at] as Ir.Stretchy
                    val prefix = xs.subList(0, at)
                    if (fence.type == Close) {
                        work.addLast {
                            val rest = results.removeLast(); val body = results.removeLast()
                            results.addLast(listOf(Ir.Value(DelimitedPart.Expression(trailing(null, fence, body)))) + rest)
                        }
                        visit(xs.subList(at + 1, xs.size))
                        visit(prefix)
                    } else {
                        var depth = 0; var end = at + 1
                        while (end < xs.size) {
                            val x = xs[end]
                            if (x is Ir.Stretchy) {
                                if (x.type == Close && depth == 0) break
                                if (x.type == Open) depth++ else if (x.type == Close) depth--
                            }
                            end++
                        }
                        val close = xs.getOrNull(end) as? Ir.Stretchy
                        work.addLast {
                            val rest = results.removeLast(); val body = results.removeLast()
                            results.addLast(removeStretch(prefix) + Ir.Value(DelimitedPart.Expression(trailing(fence, close, body))) + rest)
                        }
                        visit(xs.subList(minOf(end + 1, xs.size), xs.size))
                        visit(xs.subList(at + 1, end))
                    }
                }
            }
        }
        visit(es)
        while (work.isNotEmpty()) work.removeLast().invoke()
        return results.single()
    }
}
