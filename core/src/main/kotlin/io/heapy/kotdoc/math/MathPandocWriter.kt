// Text.TeXMath.Writers.Pandoc, © 2010–2013 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.ast.Attr
import io.heapy.kotdoc.ast.Inline

/** Complete finite-expression writer; unsupported formulas return Nothing. */
object MathPandocWriter {
    @Suppress("UNUSED_PARAMETER")
    fun writePandoc(display: DisplayType,expressions: List<MathExp>): List<Inline>? {
        val todo=ArrayDeque<()->Unit>();var result: List<Inline>?=null
        fun deliver(value: List<Inline>?,continuation: (List<Inline>?)->Unit) { todo.addLast {continuation(value)} }
        lateinit var exp: (MathTextType?,MathExp,(List<Inline>?)->Unit)->Unit
        lateinit var list: (MathTextType?,List<MathExp>,(List<Inline>?)->Unit)->Unit
        list={style,xs,k ->
            val spaced=addSpaces(xs);val out=mutableListOf<Inline>();var at=0
            lateinit var next: ()->Unit
            next={if(at==spaced.size) deliver(ownMathList(out),k) else {
                val e=spaced[at++];exp(style,e) {part->if(part==null) deliver(null,k) else {out.addAll(part);todo.addLast(next)}}
            }}
            todo.addLast(next)
        }
        exp={style,e,k ->todo.addLast {
            fun direct(value: List<Inline>?)=deliver(value,k)
            fun scripts(base: MathExp,sub: MathExp?,sup: MathExp?) {
                exp(style,base) {b ->
                    if(b==null) direct(null) else {
                        fun afterSub(s: List<Inline>?) {
                            if(s==null) direct(null) else if(sup==null) direct(b+if(sub==null) emptyList() else listOf(Inline.Subscript(s)))
                            else exp(style,sup) {u->direct(u?.let {b+(if(sub==null) emptyList() else listOf(Inline.Subscript(s)))+Inline.Superscript(it)})}
                        }
                        if(sub==null) afterSub(emptyList()) else exp(style,sub,::afterSub)
                    }
                }
            }
            when(e) {
                is MathExp.ENumber -> direct(renderStr(style,e.text))
                is MathExp.EIdentifier -> direct(renderStr(style ?: MathTextType.TextItalic,e.text))
                is MathExp.EMathOperator -> direct(renderStr(style,e.text))
                is MathExp.ESymbol -> direct(renderStr(style,e.text))
                is MathExp.EGrouped -> list(style,e.expressions,k)
                is MathExp.EStyled -> list(e.type,e.expressions,k)
                is MathExp.EText -> direct(renderStr(e.type,e.text))
                is MathExp.ESpace -> direct(listOf(Inline.Str(MathShared.getSpaceChars(e.width))))
                is MathExp.EDelimited -> list(style,e.parts.map {if(it is DelimitedPart.Middle) MathExp.ESymbol(MathSymbolType.Ord,it.text) else (it as DelimitedPart.Expression).expression}) {part->direct(part?.let {renderStr(style,e.open)+it+renderStr(style,e.close)})}
                is MathExp.ESub -> scripts(e.base,e.sub,null)
                is MathExp.ESuper -> scripts(e.base,null,e.sup)
                is MathExp.ESubsup -> scripts(e.base,e.sub,e.sup)
                is MathExp.EScaled -> exp(style,e.expression,k)
                is MathExp.EUnder -> if(e.convertible) scripts(e.base,e.under,null) else direct(null)
                is MathExp.EOver -> {
                    val b=if(e.base is MathExp.EGrouped && e.base.expressions.size==1) e.base.expressions[0] else e.base
                    val accent=e.over
                    if(b is MathExp.EIdentifier && b.text.codePointCount(0,b.text.length)==1 && accent is MathExp.ESymbol && accent.type==MathSymbolType.Accent && accent.text.codePointCount(0,accent.text.length)==1) {
                        val cp=accent.text.codePointAt(0)
                        val combining=when(cp) {
                            0x203e,0x0304->0x0304;0x00b4,0x0301->0x0301;0x0060,0x0300->0x0300
                            0x02d8,0x0306->0x0306;0x02c7,0x030c->0x030c;46,0x0307->0x0307
                            0x00b0,0x030a->0x030a;0x20d7->0x20d7;0x20d6->0x20d6
                            0x005e,0x0302->0x0302;126,0x0303->0x0303;else->null
                        }
                        direct(combining?.let {renderStr(style ?: MathTextType.TextItalic,b.text+String(Character.toChars(it)))})
                    } else if(e.convertible) scripts(e.base,null,e.over) else direct(null)
                }
                is MathExp.ESqrt,is MathExp.ERoot,is MathExp.EFraction,is MathExp.EUnderover,
                is MathExp.EPhantom,is MathExp.EBoxed,is MathExp.ECancel,is MathExp.EArray -> direct(null)
            }
        }}
        list(null,expressions) {result=it}
        while(todo.isNotEmpty()) todo.removeLast().invoke()
        return result
    }
    private fun renderStr(type: MathTextType?,text: String): List<Inline> {
        if(text.isEmpty()) return emptyList()
        fun str(s: String=text)=listOf(Inline.Str(s))
        return when(type) {
            null,MathTextType.TextNormal,MathTextType.TextSansSerif -> str()
            MathTextType.TextBold,MathTextType.TextSansSerifBold -> listOf(Inline.Strong(str()))
            MathTextType.TextItalic,MathTextType.TextSansSerifItalic -> listOf(Inline.Emph(str()))
            MathTextType.TextMonospace -> listOf(Inline.Code(Attr(),text))
            MathTextType.TextDoubleStruck,MathTextType.TextScript,MathTextType.TextFraktur -> str(MathUnicode.toUnicode(type,text))
            MathTextType.TextBoldItalic,MathTextType.TextSansSerifBoldItalic -> listOf(Inline.Strong(listOf(Inline.Emph(str()))))
            MathTextType.TextBoldScript,MathTextType.TextBoldFraktur -> listOf(Inline.Strong(str(MathUnicode.toUnicode(type,text))))
        }
    }
    private fun addSpaces(xs: List<MathExp>): List<MathExp> {
        val out=mutableListOf<MathExp>();var i=0
        fun space(cp: Int)=MathExp.EText(MathTextType.TextNormal,cp.toChar().toString())
        while(i<xs.size) {
            val e=xs[i++];val next=xs.getOrNull(i)
            when {
                e is MathExp.ESymbol && next is MathExp.ESpace -> {out.add(e);out.add(next);i++}
                e is MathExp.ESymbol && e.type !in listOf(MathSymbolType.Bin,MathSymbolType.Op,MathSymbolType.Rel,MathSymbolType.Open,MathSymbolType.Pun) -> out.add(e)
                e is MathExp.ESymbol && next is MathExp.ESymbol && next.type==MathSymbolType.Pun -> {out.add(e);out.add(next);i++}
                e is MathExp.ESymbol && next!=null -> when(e.type) {
                    MathSymbolType.Bin -> {out.add(space(0x2005));out.add(e);out.add(space(0x2005))}
                    MathSymbolType.Rel -> {out.add(space(0x2004));out.add(e);out.add(space(0x2004))}
                    MathSymbolType.Pun -> {out.add(e);out.add(space(0x2006))}
                    else->out.add(e)
                }
                e is MathExp.EMathOperator -> {out.add(e);out.add(space(0x2006))}
                else -> out.add(e)
            }
        }
        return out
    }
}
