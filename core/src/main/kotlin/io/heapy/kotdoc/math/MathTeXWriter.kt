// Text.TeXMath.Writers.TeX 0.13.3.1, © 2014 Matthew Pickering.
// GPL-2.0-or-later; unchanged source and full notice in vendor/texmath-0.13.3.1.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.math.MathExp.*
import io.heapy.kotdoc.math.MathSymbolType.*
import java.math.BigInteger

/** The full expression-to-TeX writer, including package and convertible scopes. */
object MathTeXWriter {
    fun writeTeX(expressions: List<MathExp>): String = writeTeXWith(defaultMathEnvironment, expressions)
    fun writeTeXWith(environment: List<String>, expressions: List<MathExp>): String {
        val tokens = Session(environment.map(TextChars::fromString)).run(expressions)
        return MathTeXRenderer.renderTeX(MathTeX.Grouped(tokens)).drop(1).dropLast(1)
    }
    fun addLaTeXEnvironment(display: DisplayType, input: String): String {
        val text = TextChars.fromString(input)
        return if (display == DisplayType.DisplayInline) "\\($text\\)" else "\\[$text\\]"
    }

    private class Session(val env: List<String>) {
        val amsmath = "amsmath" in env
        private val work = ArrayDeque<() -> Unit>()
        fun run(es: List<MathExp>): List<MathTeX> {
            val output = mutableListOf<MathTeX>()
            schedule(output, false) { removeOuterGroup(es).forEach(::expr) }
            while (work.isNotEmpty()) work.removeLast().invoke()
            return output
        }
        private fun schedule(output: MutableList<MathTeX>, convertible: Boolean, action: Plan.() -> Unit) {
            val plan = Plan(output, convertible); plan.action()
            plan.steps.asReversed().forEach(work::addLast)
        }
        // Source WriterT operations become ordered steps. Group snapshots happen
        // after their child steps, and every expression descent is queued.
        private inner class Plan(val output: MutableList<MathTeX>, val convertible: Boolean) {
            val steps = mutableListOf<() -> Unit>()
            fun tell(tokens: List<MathTeX>) { steps.add { output.addAll(tokens) } }
            fun tell(token: MathTeX) = tell(listOf(token))
            fun command(s: String) = tell(MathTeX.ControlSeq(s))
            fun token(c: Char) = tell(MathTeX.Token(c.code))
            fun literal(s: String) = tell(MathTeX.Literal(s))
            fun space() = tell(MathTeX.Space)
            fun expr(e: MathExp) { steps.add { schedule(output, convertible) { writeExp(e) } } }
            fun group(action: Plan.() -> Unit) {
                val inner = mutableListOf<MathTeX>(); val plan = Plan(inner, convertible); plan.action()
                steps.addAll(plan.steps); steps.add { output.add(MathTeX.Grouped(inner)) }
            }
            fun withConvertible(action: Plan.() -> Unit) {
                val plan = Plan(output, true); plan.action(); steps.addAll(plan.steps)
            }
            fun maybeGroup(condition: Boolean, action: Plan.() -> Unit) { if (condition) group(action) else action() }
            fun converted(condition: Boolean, action: Plan.() -> Unit) { if (condition) withConvertible(action) else action() }
            fun tex(s: String) = tell(MathTeXUnicode.getTeXMath(s, env))
            fun writeExp(e: MathExp) {
                when (e) {
                    is ENumber -> tex(e.text)
                    is EGrouped -> group { e.expressions.forEach(::expr) }
                    is EDelimited -> delimited(e)
                    is EIdentifier -> {
                        val ts = MathTeXUnicode.getTeXMath(e.text, env)
                        when (ts.size) { 0 -> Unit; 1 -> tell(ts); else -> tell(MathTeX.Grouped(ts)) }
                    }
                    is EMathOperator -> {
                        val operator = MathShared.getOperator(e)
                        if (operator != null) tell(operator) else {
                            command("\\operatorname"); if (convertible) token('*')
                            tell(MathTeX.Grouped(MathTeXUnicode.getTeXMath(e.text, env)))
                        }
                    }
                    is ESymbol -> when {
                        e.type == Ord && e.text.codePointCount(0, e.text.length) == 1 && e.text.codePointAt(0) in 0x2061..0x2064 -> Unit
                        e.type == Ord && e.text == "′" -> token('\'')
                        else -> {
                            if (e.type == Bin || e.type == Rel) space()
                            val ts = MathTeXUnicode.getTeXMath(e.text, env)
                            if (e.text.codePointCount(0, e.text.length) > 1 && e.type in listOf(Bin, Rel, Op)) {
                                command("\\math" + e.type.name.lowercase())
                                tell(MathTeX.Grouped(listOf(MathTeX.ControlSeq("\\text"), MathTeX.Grouped(ts))))
                            } else tell(ts)
                            if (e.type == Bin || e.type == Rel) space()
                        }
                    }
                    is ESpace -> command(spaceCommand(amsmath, e.width))
                    is EFraction -> {
                        command(when (e.type) { FractionType.NormalFrac -> "\\frac"; FractionType.DisplayFrac -> "\\dfrac"; FractionType.InlineFrac -> "\\tfrac"; FractionType.NoLineFrac -> "\\binom" })
                        group { expr(e.numerator) }; group { expr(e.denominator) }
                    }
                    is ESub -> { maybeGroup(isFancy(e.base)) { expr(e.base) }; token('_'); group { expr(e.sub) } }
                    is ESuper -> { maybeGroup(isFancy(e.base)) { expr(e.base) }; superscript(e.sup) }
                    is ESubsup -> { maybeGroup(isFancy(e.base)) { expr(e.base) }; token('_'); group { expr(e.sub) }; superscript(e.sup) }
                    is EOver -> {
                        val arrow = xarrow(e.base)
                        if (arrow != null && amsmath) { command(arrow); group { expr(e.over) } }
                        else script(MathPosition.Over, e.convertible, e.base, e.over)
                    }
                    is EUnder -> script(MathPosition.Under, e.convertible, e.base, e.under)
                    is EUnderover -> when {
                        e.under is ESymbol && e.under.type == Accent -> expr(EUnder(e.convertible, EOver(false, e.base, e.over), e.under))
                        e.over is ESymbol && e.over.type == Accent -> expr(EOver(e.convertible, EUnder(false, e.base, e.under), e.over))
                        else -> {
                            val arrow = xarrow(e.base)
                            when {
                                arrow != null && amsmath -> {
                                    command(arrow); token('['); group { expr(e.under) }; token(']'); group { expr(e.over) }
                                }
                                arrow == null && isOperator(e.base) -> {
                                    maybeGroup(isFancy(e.base)) { converted(e.convertible) { expr(e.base) } }
                                    if (!e.convertible) command("\\limits")
                                    token('_'); group { substack(e.under) }; token('^'); group { substack(e.over) }
                                }
                                else -> expr(EUnder(e.convertible, EOver(e.convertible, e.base, e.over), e.under))
                            }
                        }
                    }
                    is ESqrt -> { command("\\sqrt"); group { expr(e.expression) } }
                    is ERoot -> { command("\\sqrt"); token('['); expr(e.index); token(']'); group { expr(e.base) } }
                    is EPhantom -> { command("\\phantom"); group { expr(e.expression) } }
                    is EBoxed -> if (amsmath) { command("\\boxed"); group { expr(e.expression) } } else expr(e.expression)
                    is ECancel -> {
                        command(when (e.stroke) { StrokeType.ForwardSlash -> "\\cancel"; StrokeType.BackSlash -> "\\bcancel"; StrokeType.XSlash -> "\\xcancel" })
                        group { expr(e.expression) }
                    }
                    is EScaled -> {
                        val child = e.expression
                        if (child is ESymbol && child.type in listOf(Open, Close)) MathShared.getScalerCommand(e.scale)?.let(::command)
                        expr(child)
                    }
                    is EText -> {
                        val ts = e.text.codePoints().toArray().map { c -> MathTeXRenderer.escapeLaTeX(c).let {
                            if (it is MathTeX.Literal && it.text == "\\ ") MathTeX.Literal(" ") else it
                        } }
                        if (ts.isNotEmpty()) tell(textCommand(e.type, MathTeX.Grouped(ts)))
                    }
                    is EStyled -> {
                        val inner = e.expressions.singleOrNull() as? EStyled
                        val special = e.type == MathTextType.TextNormal && inner?.type == MathTextType.TextBold
                        val type = if (special) MathTextType.TextBold else e.type
                        val contents = if (special) checkNotNull(inner).expressions else e.expressions
                        command(if (special) "\\mathbf" else MathShared.getLaTeXTextCommand(env, type))
                        group { contents.forEach { expr(fromUnicodeExp(type, it)) } }
                    }
                    is EArray -> table(if (amsmath && MathShared.isRLSequence(e.alignments)) "aligned"
                        else if (amsmath && e.alignments.all { it == MathAlignment.AlignCenter }) "matrix" else "array",
                        if (amsmath && (MathShared.isRLSequence(e.alignments) || e.alignments.all { it == MathAlignment.AlignCenter })) emptyList() else e.alignments, e.rows)
                }
            }
            private fun superscript(e: MathExp) {
                val primes = MathShared.toPrimes(e)
                if (primes != null) repeat(primes.length) { token('\'') } else { token('^'); group { expr(e) } }
            }
            private fun delimited(e: EDelimited) {
                val single = (e.parts.singleOrNull() as? DelimitedPart.Expression)?.expression
                if (single is EFraction && single.type == FractionType.NoLineFrac) {
                    val cmd = when (e.open to e.close) { "(" to ")" -> "\\choose"; "[" to "]" -> "\\brack"; "{" to "}" -> "\\brace"; "⟨" to "⟩" -> "\\bangle"; else -> null }
                    if (cmd != null) binom(cmd, single.numerator, single.denominator)
                    else expr(EDelimited(e.open, e.close, listOf(DelimitedPart.Expression(EArray(listOf(MathAlignment.AlignCenter), listOf(listOf(listOf(single.numerator)), listOf(listOf(single.denominator))))))))
                } else if (single is EArray) {
                    val matrix = if (!amsmath) null else when (e.open to e.close) {
                        "{" to "" -> "cases".takeIf { single.alignments == listOf(MathAlignment.AlignLeft, MathAlignment.AlignLeft) }
                        "(" to ")" -> "pmatrix".takeIf { single.alignments.all { it == MathAlignment.AlignCenter } }
                        "[" to "]" -> "bmatrix".takeIf { single.alignments.all { it == MathAlignment.AlignCenter } }
                        "{" to "}" -> "Bmatrix".takeIf { single.alignments.all { it == MathAlignment.AlignCenter } }
                        "∣" to "∣" -> "vmatrix".takeIf { single.alignments.all { it == MathAlignment.AlignCenter } }
                        "∥" to "∥" -> "Vmatrix".takeIf { single.alignments.all { it == MathAlignment.AlignCenter } }
                        else -> null
                    }
                    if (matrix != null) table(matrix, emptyList(), single.rows)
                    else { delim(0, e.open); expr(single); delim(2, e.close) }
                } else if (e.parts.all(::standardHeight) && e.open in listOf("(", "[", "|") && e.close in listOf(")", "]", "|")) {
                    tex(e.open); e.parts.forEach { expr((it as DelimitedPart.Expression).expression) }; tex(e.close)
                } else {
                    delim(0, e.open)
                    e.parts.forEach { when (it) { is DelimitedPart.Middle -> delim(1, it.text); is DelimitedPart.Expression -> expr(it.expression) } }
                    delim(2, e.close)
                }
            }
            private fun binom(cmd: String, x: MathExp, y: MathExp) {
                if (amsmath) {
                    when (cmd) {
                        "\\choose" -> command("\\binom")
                        "\\brack" -> genFrac("[", "]")
                        "\\brace" -> genFrac("\\{", "\\}")
                        "\\bangle" -> genFrac("\\langle", "\\rangle")
                        else -> error("writeBinom: unknown cmd")
                    }
                    group { expr(x) }; group { expr(y) }
                } else group { expr(x); command(cmd); expr(y) }
            }
            private fun genFrac(open: String, close: String) {
                command("\\genfrac")
                listOf(open, close, "0pt").forEach { tell(MathTeX.Grouped(listOf(MathTeX.Literal(it)))) }
                tell(MathTeX.Grouped(emptyList()))
            }
            private fun table(name: String, aligns: List<MathAlignment>, rows: List<List<List<MathExp>>>) {
                command("\\begin"); tell(MathTeX.Grouped(listOf(MathTeX.Literal(name))))
                if (aligns.isNotEmpty()) tell(MathTeX.Grouped(listOf(MathTeX.Literal(aligns.joinToString("") { when (it) {
                    MathAlignment.AlignLeft -> "l"; MathAlignment.AlignCenter -> "c"; MathAlignment.AlignRight -> "r"
                } }))))
                token('\n')
                rows.forEachIndexed { rowAt, row ->
                    row.forEachIndexed { i, cell -> if (i > 0) { space(); token('&'); space() }; cell.forEach(::expr) }
                    if (rowAt < rows.lastIndex) { space(); literal("\\\\") }
                    token('\n')
                }
                command("\\end"); tell(MathTeX.Grouped(listOf(MathTeX.Literal(name))))
            }
            private fun delim(position: Int, text: String) {
                val ts = MathTeXUnicode.getTeXMath(text, env)
                val valid = delimiterTexts.map { MathTeXUnicode.getTeXMath(it, env) }.filter { it.isNotEmpty() }.any { it == ts }
                val fence = if (valid) ts else MathTeXUnicode.getTeXMath(".", env)
                when (position) {
                    0 -> { command("\\left"); tell(fence); space(); if (!valid) tell(ts) }
                    1 -> if (valid) { space(); command("\\middle"); tell(ts); space() } else tell(ts)
                    else -> { space(); command("\\right"); tell(fence); if (!valid) tell(ts) }
                }
            }
            private fun script(position: MathPosition, convert: Boolean, base: MathExp, script: MathExp) {
                val cmd = if (script is ESymbol && script.type in listOf(Accent, TOver, TUnder))
                    MathShared.getDiacriticalCommand(position, script.text)?.let { if (isNarrow(base)) narrowDiacritical(it) else it } else null
                when {
                    cmd != null -> { command(cmd); group { expr(base) } }
                    isOperator(base) -> {
                        maybeGroup(isFancy(base)) { converted(convert) { expr(base) } }
                        if (!convert) command("\\limits")
                        token(if (position == MathPosition.Over) '^' else '_'); group { substack(script) }
                    }
                    position == MathPosition.Over && script == ESymbol(Accent, "̿") -> {
                        command("\\overline"); literal("{"); command("\\overline"); group { expr(base) }; literal("}")
                    }
                    else -> { command(if (position == MathPosition.Over) "\\overset" else "\\underset"); group { expr(script) }; group { expr(base) } }
                }
            }
            private fun substack(e: MathExp) {
                if (e is EArray && e.alignments == listOf(MathAlignment.AlignCenter) && amsmath) {
                    command("\\substack")
                    group { e.rows.forEachIndexed { i, row ->
                        if (i > 0) { space(); literal("\\\\"); space() }
                        row.forEach { removeOuterGroup(it).forEach(::expr) }
                    } }
                } else expr(e)
            }
        }
    }
    private val delimiterTexts = listOf(".", "(", ")", "[", "]", "|", "‖", "{", "}", "⌉", "⌈", "〈", "〉", "⌋", "⌊", "⌜", "⌝")
    private fun standardHeight(part: DelimitedPart): Boolean = part is DelimitedPart.Expression && when (val e = part.expression) {
        is EIdentifier, is ENumber, is ESpace -> true
        is ESymbol -> e.type in listOf(Ord, Op, Bin, Rel, Pun)
        else -> false
    }
    private fun isFancy(e: MathExp) = e is ESub || e is ESuper || e is ESubsup || e is EOver || e is EUnder || e is EUnderover || e is ESqrt || e is ERoot || e is EPhantom
    private fun isOperator(e: MathExp) = e is EMathOperator || e is ESymbol && e.type == Op
    private fun xarrow(e: MathExp) = if (e is ESymbol && e.type == Op) when (e.text) { "←" -> "\\xleftarrow"; "→" -> "\\xrightarrow"; else -> null } else null
    private fun isNarrow(e: MathExp): Boolean {
        var current = e
        while (true) {
            val text = when (current) {
                is EIdentifier -> current.text; is ENumber -> current.text; is ESymbol -> current.text
                is EGrouped -> { current = current.expressions.singleOrNull() ?: return false; continue }
                is EStyled -> { current = current.expressions.singleOrNull() ?: return false; continue }
                else -> return false
            }
            return text.codePointCount(0, text.length) == 1
        }
    }
    private fun narrowDiacritical(cmd: String) = when (cmd) { "\\overrightarrow" -> "\\vec"; "\\widetilde" -> "\\tilde"; "\\widehat" -> "\\hat"; else -> cmd }
    private fun removeOuterGroup(es: List<MathExp>) = (es.singleOrNull() as? EGrouped)?.expressions ?: es
    private fun fromUnicodeExp(type: MathTextType, e: MathExp) = MathShared.everywhereExp({ x ->
        fun f(s: String) = MathUnicode.fromUnicode(type, s)
        when (x) {
            is ENumber -> ENumber(f(x.text)); is EIdentifier -> EIdentifier(f(x.text)); is EMathOperator -> EMathOperator(f(x.text))
            is ESymbol -> ESymbol(x.type, f(x.text)); is EText -> EText(x.type, f(x.text))
            is EDelimited -> EDelimited(f(x.open), f(x.close), x.parts.map { if (it is DelimitedPart.Middle) DelimitedPart.Middle(f(it.text)) else it })
            else -> x
        }
    }, e)
    private fun spaceCommand(amsmath: Boolean, width: MathRational): String {
        val (whole, rest) = (width.numerator * BigInteger.valueOf(18)).divideAndRemainder(width.denominator)
        val n = (if (rest.signum() < 0) whole - BigInteger.ONE else whole).toLong()
        return when (n) { -3L -> "\\!"; 0L -> ""; 3L -> "\\,"; 4L -> "\\ "; 5L -> "\\;"; 18L -> "\\quad"; 36L -> "\\qquad"; else -> if (amsmath) "\\mspace{${n}mu}" else "{\\mskip ${n}mu}" }
    }
    private fun textCommand(type: MathTextType, x: MathTeX): List<MathTeX> {
        fun cmd(s: String) = MathTeX.ControlSeq(s)
        fun nested(s: String, ts: List<MathTeX>) = listOf(cmd(s), MathTeX.Grouped(ts))
        return when (type) {
            MathTextType.TextNormal -> listOf(cmd("\\text"), x)
            MathTextType.TextItalic -> listOf(cmd("\\textit"), x)
            MathTextType.TextBold -> listOf(cmd("\\textbf"), x)
            MathTextType.TextMonospace -> listOf(cmd("\\texttt"), x)
            MathTextType.TextBoldItalic -> nested("\\textit", listOf(cmd("\\textbf"), x))
            MathTextType.TextSansSerif -> listOf(cmd("\\textsf"), x)
            MathTextType.TextSansSerifBold -> nested("\\textbf", listOf(cmd("\\textsf"), x))
            MathTextType.TextSansSerifItalic -> nested("\\textit", listOf(cmd("\\textsf"), x))
            MathTextType.TextSansSerifBoldItalic -> nested("\\textbf", nested("\\textit", listOf(cmd("\\textsf"), x)))
            else -> listOf(cmd("\\text"), x)
        }
    }
}
