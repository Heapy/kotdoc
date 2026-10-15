// Text.TeXMath.Shared, © 2014 Matthew Pickering; GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.layout.TextChars
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object MathShared {
    val empty: MathExp = MathExp.EGrouped(emptyList())
    fun isEmpty(e: MathExp)=e is MathExp.EGrouped && e.expressions.isEmpty()
    fun mapExpChildren(goE: (MathExp)->MathExp,goL: (List<MathExp>)->List<MathExp>,e: MathExp): MathExp=when(e) {
        is MathExp.ENumber -> e
        is MathExp.EGrouped -> MathExp.EGrouped(goL(e.expressions))
        is MathExp.EDelimited -> MathExp.EDelimited(e.open,e.close,e.parts.map { if(it is DelimitedPart.Expression) DelimitedPart.Expression(goE(it.expression)) else it })
        is MathExp.EIdentifier -> e
        is MathExp.EMathOperator -> e
        is MathExp.ESymbol -> e
        is MathExp.ESpace -> e
        is MathExp.ESub -> MathExp.ESub(goE(e.base),goE(e.sub))
        is MathExp.ESuper -> MathExp.ESuper(goE(e.base),goE(e.sup))
        is MathExp.ESubsup -> MathExp.ESubsup(goE(e.base),goE(e.sub),goE(e.sup))
        is MathExp.EOver -> MathExp.EOver(e.convertible,goE(e.base),goE(e.over))
        is MathExp.EUnder -> MathExp.EUnder(e.convertible,goE(e.base),goE(e.under))
        is MathExp.EUnderover -> MathExp.EUnderover(e.convertible,goE(e.base),goE(e.under),goE(e.over))
        is MathExp.EPhantom -> MathExp.EPhantom(goE(e.expression))
        is MathExp.EBoxed -> MathExp.EBoxed(goE(e.expression))
        is MathExp.ECancel -> MathExp.ECancel(e.stroke,goE(e.expression))
        is MathExp.EFraction -> MathExp.EFraction(e.type,goE(e.numerator),goE(e.denominator))
        is MathExp.ERoot -> MathExp.ERoot(goE(e.index),goE(e.base))
        is MathExp.ESqrt -> MathExp.ESqrt(goE(e.expression))
        is MathExp.EScaled -> MathExp.EScaled(e.scale,goE(e.expression))
        is MathExp.EArray -> MathExp.EArray(e.alignments,e.rows.map { row -> row.map(goL) })
        is MathExp.EText -> e
        is MathExp.EStyled -> MathExp.EStyled(e.type,goL(e.expressions))
    }
    private class Visit(val e: MathExp,val finish: Boolean=false,val count: Int=0)
    private fun walk(root: MathExp,f: (MathExp)->MathExp,list: (List<MathExp>)->List<MathExp>): MathExp {
        val todo=ArrayDeque<Visit>();val values=ArrayDeque<MathExp>();todo.addLast(Visit(root))
        while(todo.isNotEmpty()) {
            val task=todo.removeLast()
            if(!task.finish) {
                val children=mutableListOf<MathExp>()
                mapExpChildren({children.add(it);it},{children.addAll(it);it},task.e)
                todo.addLast(Visit(task.e,true,children.size));children.asReversed().forEach { todo.addLast(Visit(it)) }
            } else {
                val done=arrayOfNulls<MathExp>(task.count)
                for(i in task.count-1 downTo 0) done[i]=values.removeLast()
                var at=0
                val mapped=mapExpChildren({checkNotNull(done[at++])},{xs->list(List(xs.size) {checkNotNull(done[at++])})},task.e)
                values.addLast(f(mapped))
            }
        }
        return values.single()
    }
    fun everywhereExp(f: (MathExp)->MathExp,e: MathExp)=walk(e,f,{it})
    fun everywhereExpList(f: (List<MathExp>)->List<MathExp>,xs: List<MathExp>): List<MathExp> {
        fun tails(xs: List<MathExp>): List<MathExp> {
            var out=f(emptyList())
            for(e in xs.asReversed()) out=f(listOf(e)+out)
            return out
        }
        return (walk(MathExp.EGrouped(xs),{it},::tails) as MathExp.EGrouped).expressions
    }
    fun fixTree(e: MathExp): MathExp {
        val filtered=walk(e,{it},{xs->xs.filterNot(::isEmpty)})
        return everywhereExp({x->when {
            x is MathExp.EDelimited && x.parts.size==1 && x.parts[0] is DelimitedPart.Expression -> {
                val inner=(x.parts[0] as DelimitedPart.Expression).expression
                if(inner is MathExp.EDelimited && inner.open.isEmpty() && inner.close.isEmpty()) MathExp.EDelimited(x.open,x.close,inner.parts)
                else if(x.open.isEmpty() && x.close.isEmpty()) inner else x
            }
            x is MathExp.EDelimited && x.open.isEmpty() && x.close.isEmpty() && x.parts.size==1 -> MathExp.ESymbol(MathSymbolType.Ord,(x.parts[0] as DelimitedPart.Middle).text)
            x is MathExp.EGrouped && x.expressions.size==1 -> x.expressions[0]
            else -> x
        }},filtered)
    }
    fun handleDownup(display: DisplayType,e: MathExp): MathExp=when(e) {
        is MathExp.EUnder -> if(!e.convertible) e else if(display==DisplayType.DisplayInline) MathExp.ESub(e.base,e.under) else MathExp.EUnder(false,e.base,e.under)
        is MathExp.EOver -> if(!e.convertible) e else if(display==DisplayType.DisplayInline) MathExp.ESuper(e.base,e.over) else MathExp.EOver(false,e.base,e.over)
        is MathExp.EUnderover -> if(!e.convertible) e else if(display==DisplayType.DisplayInline) MathExp.ESubsup(e.base,e.under,e.over) else MathExp.EUnderover(false,e.base,e.under,e.over)
        else -> e
    }
    fun isUppercaseGreek(text: String): Boolean=TextChars.fromString(text).let { it.isNotEmpty() && it.codePoints().allMatch { cp->cp in 0x391..0x3a9 } }
    fun toPrimes(e: MathExp): String? {
        if(e !is MathExp.ESymbol || e.type !in listOf(MathSymbolType.Pun,MathSymbolType.Ord)) return null
        return buildString { for(cp in e.text.codePoints().toArray()) append(when(cp) {39,0x2032->"'";0x2033->"''";0x2034->"'''";0x2057->"''''";else->return null}) }
    }
    fun isRLSequence(alignments: List<MathAlignment>)=alignments.isNotEmpty() && alignments.size%2==0 && alignments.withIndex().all { (i,a)->a==if(i%2==0) MathAlignment.AlignRight else MathAlignment.AlignLeft }
    fun getSpaceWidth(character: Int): MathRational?=when(character) {
        32,0xa0,0x2005,0x205f->MathRational(4,18)
        0x2000,0x2002->MathRational(1,2)
        0x2001,0x2003->MathRational(1)
        0x2004,0x2007->MathRational(1,3)
        0x2006,0x2008,0x2009,0x202f->MathRational(1,6)
        0x200a->MathRational(1,9)
        0x200b->MathRational(0)
        else->null
    }
    fun getSpaceChars(width: MathRational): String {
        val (whole,remainder)=width.numerator.divideAndRemainder(width.denominator)
        val count=whole.toLong()
        if(count<0) return "\u200b"
        var fraction=MathRational(remainder,width.denominator)
        val out=StringBuilder()
        while(fraction>MathRational(1,2)) { out.append('\u2000');fraction-=MathRational(1,2) }
        out.append(when {
            fraction<=MathRational(2,18)->'\u200a'
            fraction<=MathRational(3,18)->'\u2006'
            fraction<=MathRational(4,18)->'\u00a0'
            fraction<=MathRational(5,18)->'\u2005'
            fraction<=MathRational(7,18)->'\u2004'
            else->'\u2000'
        })
        repeat(Math.toIntExact(count)) {out.append('\u2001')}
        return out.toString()
    }
    fun readLength(input: String): MathRational? {
        val s=TextChars.fromString(input);val match=Regex("^-?[0-9]+(?:\\.[0-9]+)?").find(s) ?: return null
        val multiplier=units[s.substring(match.value.length)] ?: return null
        val number=match.value.toDouble()*18.0
        // GHC's integerDecodeDouble retains Infinity's hidden mantissa bit.
        val rounded=if(number.isInfinite()) BigInteger.ONE.shiftLeft(1024)*BigInteger.valueOf(if(number<0) -1 else 1)
            else BigDecimal(number).setScale(0,RoundingMode.HALF_EVEN).toBigIntegerExact()
        return MathRational(rounded,BigInteger.valueOf(18))*multiplier
    }
    private val textTypes=mapOf(
        MathTextType.TextNormal to ("normal" to "\\mathrm"),
        MathTextType.TextBold to ("bold" to "\\mathbf"),
        MathTextType.TextItalic to ("italic" to "\\mathit"),
        MathTextType.TextMonospace to ("monospace" to "\\mathtt"),
        MathTextType.TextSansSerif to ("sans-serif" to "\\mathsf"),
        MathTextType.TextDoubleStruck to ("double-struck" to "\\mathbb"),
        MathTextType.TextScript to ("script" to "\\mathcal"),
        MathTextType.TextFraktur to ("fraktur" to "\\mathfrak"),
        MathTextType.TextBoldItalic to ("bold-italic" to "\\mathbfit"),
        MathTextType.TextSansSerifBold to ("bold-sans-serif" to "\\mathbfsfup"),
        MathTextType.TextSansSerifBoldItalic to ("sans-serif-bold-italic" to "\\mathbfsfit"),
        MathTextType.TextBoldScript to ("bold-script" to "\\mathbfscr"),
        MathTextType.TextBoldFraktur to ("bold-fraktur" to "\\mathbffrak"),
        MathTextType.TextSansSerifItalic to ("sans-serif-italic" to "\\mathsfit")
    )
    val diacriticals: List<Pair<String,String>> = ownMathList(listOf(
        "´" to "\\acute",
        "́" to "\\acute",
        "`" to "\\grave",
        "̀" to "\\grave",
        "˘" to "\\breve",
        "̆" to "\\breve",
        "ˇ" to "\\check",
        "̌" to "\\check",
        "̇" to "\\dot",
        "̈" to "\\ddot",
        "⃛" to "\\dddot",
        "⃜" to "\\ddddot",
        "°" to "\\mathring",
        "̊" to "\\mathring",
        "⃗" to "\\overrightarrow",
        "⃖" to "\\overleftarrow",
        "^" to "\\hat",
        "ˆ" to "\\widehat",
        "̂" to "\\widehat",
        "˜" to "\\widetilde",
        "̃" to "\\widetilde",
        "̄" to "\\bar",
        "‾" to "\\bar",
        "⏞" to "\\overbrace",
        "⎴" to "\\overbracket",
        "¯" to "\\overline",
        "̅" to "\\overline",
        "⏟" to "\\underbrace",
        "⎵" to "\\underbracket",
        "̲" to "\\underline",
        "_" to "\\underline",
        "̳" to "\\underbar"
    ))
    private val diaMap=diacriticals.toMap()
    private val operators=mapOf("arccos" to "\\arccos","arcsin" to "\\arcsin","arctan" to "\\arctan","arg" to "\\arg","cos" to "\\cos","cosh" to "\\cosh","cot" to "\\cot","coth" to "\\coth","csc" to "\\csc","deg" to "\\deg","det" to "\\det","dim" to "\\dim","exp" to "\\exp","gcd" to "\\gcd","hom" to "\\hom","inf" to "\\inf","ker" to "\\ker","lg" to "\\lg","lim" to "\\lim","liminf" to "\\liminf","limsup" to "\\limsup","ln" to "\\ln","log" to "\\log","max" to "\\max","min" to "\\min","Pr" to "\\Pr","sec" to "\\sec","sin" to "\\sin","sinh" to "\\sinh","sup" to "\\sup","tan" to "\\tan","tanh" to "\\tanh")
    val scalers: List<Pair<String,MathRational>> = ownMathList(listOf(
        "\\bigg" to MathRational(12,5),"\\biggl" to MathRational(12,5),"\\biggr" to MathRational(12,5),
        "\\Bigg" to MathRational(3),"\\Biggl" to MathRational(3),"\\Biggr" to MathRational(3),
        "\\big" to MathRational(6,5),"\\bigl" to MathRational(6,5),"\\bigr" to MathRational(6,5),
        "\\Big" to MathRational(9,5),"\\Bigl" to MathRational(9,5),"\\Bigr" to MathRational(9,5)))
    private val sortedScalers=scalers.sortedWith(compareBy<Pair<String,MathRational>> { it.second }.thenBy { it.first })
    fun getScalerCommand(width: MathRational)=sortedScalers.firstOrNull {it.second>=width}?.first
    fun getScalerValue(command: String)=scalers.firstOrNull {it.first==TextChars.fromString(command)}?.second
    fun getMMLType(type: MathTextType)=textTypes.getValue(type).first
    fun getTextType(variant: String)=textTypes.entries.firstOrNull {it.value.first==TextChars.fromString(variant)}?.key ?: MathTextType.TextNormal
    fun getLaTeXTextCommand(environment: List<String>,type: MathTextType): String {
        val cmd=textTypes.getValue(type).second
        if("unicode-math" in environment.map(TextChars::fromString)) return cmd
        return when(cmd) {
            "\\mathbfit","\\mathbfsfup","\\mathbfsfit"->"\\mathbf"
            "\\mathbfscr"->"\\mathcal"
            "\\mathbffrak"->"\\mathfrak"
            "\\mathsfit"->"\\mathsf"
            else->cmd
        }
    }
    fun getDiacriticalCommand(position: MathPosition,symbol: String): String? {
        val cmd=diaMap[TextChars.fromString(symbol)] ?: return null
        val below=cmd in listOf("\\underbrace","\\underline","\\underbar","\\underbracket")
        return cmd.takeIf {below==(position==MathPosition.Under)}
    }
    fun getOperator(e: MathExp): MathTeX?=if(e is MathExp.EMathOperator) operators[e.text]?.let(MathTeX::ControlSeq) else null
    private val units=mapOf("pt" to MathRational(1,10),"mm" to MathRational(2845,10000),"cm" to MathRational(2845,1000),"in" to MathRational(7227,1000),"ex" to MathRational(43,100),"em" to MathRational(1),"mu" to MathRational(1,18),"dd" to MathRational(107,1000),"bp" to MathRational(1004,10000),"pc" to MathRational(12,10))
}
