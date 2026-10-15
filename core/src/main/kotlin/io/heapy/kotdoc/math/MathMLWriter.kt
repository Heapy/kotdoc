// Text.TeXMath.Writers.MathML, © 2009 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.xml.LegacyXmlElement

/** Full finite MathML writer, preserving XML.Light attribute/content order. */
object MathMLWriter {
    private class Spec(val expression: MathExp,val style: MathTextType?,val accent: Boolean=false)
    private class Plan(val children: List<Spec>,val build: (List<LegacyXmlElement>)->LegacyXmlElement)
    private class Finish(val plan: Plan)
    fun writeMathML(display: DisplayType,expressions: List<MathExp>): LegacyXmlElement {
        val es=expressions.map {MathShared.everywhereExp({e->MathShared.handleDownup(display,e)},it)}
        val todo=ArrayDeque<Any>();val values=ArrayDeque<LegacyXmlElement>();todo.addLast(Spec(MathExp.EGrouped(es),null))
        while(todo.isNotEmpty()) when(val task=todo.removeLast()) {
            is Spec -> {
                val plan=plan(task);todo.addLast(Finish(plan));plan.children.asReversed().forEach(todo::addLast)
            }
            is Finish -> {
                val children=arrayOfNulls<LegacyXmlElement>(task.plan.children.size)
                for(i in children.indices.reversed()) children[i]=values.removeLast()
                values.addLast(task.plan.build(children.map {checkNotNull(it)}))
            }
        }
        return node("math",listOf(values.single())).withAttribute("xmlns","http://www.w3.org/1998/Math/MathML")
            .withAttribute("display",if(display==DisplayType.DisplayBlock) "block" else "inline")
    }
    private fun node(name: String,children: List<LegacyXmlElement>)=LegacyXmlElement.node(name,children)
    private fun text(name: String,text: String)=LegacyXmlElement.textNode(name,text)
    private fun stretchy(form: String,e: LegacyXmlElement)=e.withAttribute("form",form).withAttribute("stretchy","true")
    private fun fence(form: String,e: LegacyXmlElement)=e.withAttribute("form",form).withAttribute("stretchy","false")
    private fun spaceWidth(width: MathRational): LegacyXmlElement {
        val number=HaskellDouble.showFixed(MathNumbers.toDouble(width),3)
        val trimmed=if(number.endsWith('0')) number.trimEnd('0').let {if(it.endsWith('.')) it+"0" else it} else number
        return LegacyXmlElement.node("mspace").withAttribute("width",trimmed+"em")
    }
    private fun variant(name: String,value: String,style: MathTextType?): LegacyXmlElement=when(style) {
        null -> text(name,value).let {if(MathShared.isUppercaseGreek(value)) it.withAttribute("mathvariant","normal") else it}
        MathTextType.TextNormal -> text(name,value).withAttribute("mathvariant","normal")
        MathTextType.TextBold -> {
            if(name=="mi" && !MathShared.isUppercaseGreek(value)) text(name,MathUnicode.toUnicode(MathTextType.TextBoldItalic,value)).withAttribute("mathvariant","bold-italic")
            else text(name,MathUnicode.toUnicode(style,value)).withAttribute("mathvariant","bold")
        }
        else -> text(name,MathUnicode.toUnicode(style,value)).withAttribute("mathvariant",MathShared.getMMLType(style))
    }
    private fun makeText(style: MathTextType,value: String): LegacyXmlElement {
        val t=text("mtext",MathUnicode.toUnicode(style,value)).withAttribute("mathvariant",MathShared.getMMLType(style))
        val leading=value.firstOrNull() in listOf(' ','\t');val trailing=value.lastOrNull() in listOf(' ','\t')
        if(!leading && !trailing) return t
        val space=spaceWidth(MathRational(1,3))
        return node("mrow",(if(leading) listOf(space) else emptyList())+t+(if(trailing) listOf(space) else emptyList()))
    }
    private fun insertFunctionApps(expressions: List<MathExp>): List<MathExp> {
        fun isApply(e: MathExp)=e is MathExp.ESymbol && e.text=="\u2061"
        if(expressions.size==2 && expressions[0] is MathExp.EMathOperator && isApply(expressions[1]))
            return listOf(expressions[0],MathExp.ESymbol(MathSymbolType.Pun,"\u2061"))
        val out=mutableListOf<MathExp>();var at=0
        while(at<expressions.size) {
            val e=expressions[at++]
            if(e is MathExp.EMathOperator && at<expressions.size) {
                if(isApply(expressions[at])) at++
                out.add(MathExp.EGrouped(listOf(e,MathExp.ESymbol(MathSymbolType.Pun,"\u2061"))))
            } else out.add(e)
        }
        return out
    }
    private fun plan(spec: Spec): Plan {
        val e=spec.expression;val tt=spec.style
        fun constant(e: LegacyXmlElement)=Plan(emptyList()) {e}
        fun one(e: MathExp,build: (LegacyXmlElement)->LegacyXmlElement)=Plan(listOf(Spec(e,tt))) {build(it.single())}
        fun exps(es: List<MathExp>)=insertFunctionApps(es).map {Spec(it,tt)}
        fun container(name: String,es: List<MathExp>)=Plan(exps(es)) {node(name,it)}
        fun accent(es: List<MathExp>,name: String,marks: List<MathExp>)=Plan(exps(es)+marks.map {Spec(it,tt,true)}) {node(name,it)}
        if(spec.accent && e is MathExp.ESymbol) {
            val isAccent=e.type==MathSymbolType.Accent || MathMLOperators.getMathMLOperator(e.text,FormType.FPostfix)?.properties?.contains("accent")==true
            return constant(text("mo",e.text).withAttribute("accent",if(isAccent) "true" else "false"))
        }
        return when(e) {
            is MathExp.ENumber -> constant(variant("mn",e.text,tt))
            is MathExp.EIdentifier -> constant(variant("mi",e.text,tt))
            is MathExp.EMathOperator -> constant(if(tt==null) text("mi",e.text).withAttribute("mathvariant","normal") else variant("mi",e.text,tt))
            is MathExp.EGrouped -> if(e.expressions.size==1) one(e.expressions[0]) {it} else container("mrow",e.expressions)
            is MathExp.EStyled -> Plan(listOf(Spec(MathExp.EGrouped(e.expressions),e.type))) {it.single()}
            is MathExp.EDelimited -> {
                val middle=e.parts.filterIsInstance<DelimitedPart.Expression>().map {Spec(it.expression,tt)}
                Plan(middle) {children->
                    val out=mutableListOf<LegacyXmlElement>();var at=0
                    if(e.open.isNotEmpty()) out.add(stretchy("prefix",variant("mo",e.open,tt)))
                    e.parts.forEach {part->out.add(if(part is DelimitedPart.Middle) stretchy("infix",variant("mo",part.text,tt)) else children[at++])}
                    if(e.close.isNotEmpty()) out.add(stretchy("postfix",variant("mo",e.close,tt)))
                    node("mrow",out)
                }
            }
            is MathExp.ESymbol -> constant(when(e.type) {
                MathSymbolType.Open -> fence("prefix",variant("mo",e.text,tt))
                MathSymbolType.Close -> fence("postfix",variant("mo",e.text,tt))
                MathSymbolType.Ord -> variant(if(e.text=="\u2061") "mo" else "mi",e.text,tt)
                else -> variant("mo",e.text,tt)
            })
            is MathExp.ESpace -> constant(spaceWidth(e.width))
            is MathExp.EFraction -> Plan(listOf(Spec(e.numerator,tt),Spec(e.denominator,tt))) {children->
                val fraction=node("mfrac",children)
                when(e.type) {
                    FractionType.NormalFrac -> fraction
                    FractionType.InlineFrac -> node("mstyle",listOf(fraction)).withAttribute("displaystyle","false")
                    FractionType.DisplayFrac -> node("mstyle",listOf(fraction)).withAttribute("displaystyle","true")
                    FractionType.NoLineFrac -> fraction.withAttribute("linethickness","0")
                }
            }
            is MathExp.ESub -> container("msub",listOf(e.base,e.sub))
            is MathExp.ESuper -> container("msup",listOf(e.base,e.sup))
            is MathExp.ESubsup -> container("msubsup",listOf(e.base,e.sub,e.sup))
            is MathExp.EUnder -> accent(listOf(e.base),"munder",listOf(e.under))
            is MathExp.EOver -> {
                val mark=if(e.over is MathExp.ESymbol && e.over.type==MathSymbolType.Accent && e.over.text=="\u20d7") MathExp.ESymbol(MathSymbolType.Accent,"\u2192") else e.over
                accent(listOf(e.base),"mover",listOf(mark))
            }
            is MathExp.EUnderover -> accent(listOf(e.base),"munderover",listOf(e.under,e.over))
            is MathExp.EPhantom -> container("mphantom",listOf(e.expression))
            is MathExp.EBoxed -> one(e.expression) {node("menclose",listOf(it)).withAttribute("notation","box")}
            is MathExp.ECancel -> one(e.expression) {node("menclose",listOf(it)).withAttribute("notation",when(e.stroke) {
                StrokeType.ForwardSlash -> "updiagonalstrike"
                StrokeType.BackSlash -> "downdiagonalstrike"
                StrokeType.XSlash -> "updiagonalstrike downdiagonalstrike"
            })}
            is MathExp.ESqrt -> one(e.expression) {node("msqrt",listOf(it))}
            is MathExp.ERoot -> Plan(listOf(Spec(e.base,tt),Spec(e.index,tt))) {node("mroot",it)}
            is MathExp.EScaled -> one(e.expression) {
                val percent=MathNumbers.roundToInt64(e.scale*MathRational(100)).toString()+"%"
                it.setAttribute("stretchy","true").withAttribute("maxsize",percent).withAttribute("minsize",percent)
            }
            is MathExp.EArray -> {
                val specs=e.rows.flatMap {row->row.flatMap(::exps)}
                Plan(specs) {children->
                    var at=0;val rl=MathShared.isRLSequence(e.alignments)
                    node("mtable",e.rows.map {row->node("mtr",row.mapIndexed {col,cell->
                        val count=insertFunctionApps(cell).size;val xs=children.subList(at,at+count);at+=count
                        val alignment=e.alignments.getOrElse(col) {MathAlignment.AlignCenter}
                        val align=when(alignment) {MathAlignment.AlignLeft->"left";MathAlignment.AlignCenter->"center";MathAlignment.AlignRight->"right"}
                        val style="text-align: $align"+if(rl && alignment!=MathAlignment.AlignCenter) "; padding-$align: 0" else ""
                        node("mtd",xs).withAttribute("style",style).withAttribute("columnalign",align)
                    })})
                }
            }
            is MathExp.EText -> constant(makeText(if(e.type==MathTextType.TextNormal) tt ?: e.type else e.type,e.text))
        }
    }
}
