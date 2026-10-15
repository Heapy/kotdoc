// Text.TeXMath.Readers.TeX, © 2009 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.math.MathExp.*
import io.heapy.kotdoc.math.MathSymbolType.*
import io.heapy.kotdoc.math.MathTextType.*
import io.heapy.kotdoc.math.MathAlignment.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.*
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

private typealias TP<A> = Parsec<Unit,A>
sealed class MathTeXResult {
    class Success(expressions: List<MathExp>): MathTeXResult() {val expressions=ownMathList(expressions)}
    class Failure(val error: String): MathTeXResult()
}
object MathTeXReader {
    private fun <A> d(f:()->TP<A>): TP<A> = Parsec.defer(f)
    private fun <A> pure(a: A): TP<A> = Parsec.pure(a)
    private fun <A> zero(): TP<A> = Parsec.zero()
    private fun <A> choice(ps: List<TP<A>>): TP<A> = ps.asReversed().fold(zero()) {rest,p->p or rest}
    private fun char(c: Char)=Parsec.char<Unit>(c.code)
    private fun satisfy(f:(Int)->Boolean)=Parsec.satisfy<Unit>(f)
    private fun any()=satisfy {true}
    private fun one(s: String)=satisfy {it in s.map(Char::code)}
    private fun none(s: String)=satisfy {it !in s.map(Char::code)}
    private fun string(s: String)=Parsec.string<Unit>(s)
    private fun letter()=satisfy(ParserCharacters::isAlpha).label("letter")
    private fun digit()=satisfy(ParserCharacters::isDigit).label("digit")
    private fun space()=satisfy(ParserCharacters::isSpace).label("space")
    private fun spaces()=space().skipMany().label("white space")
    private fun text(xs: List<Int>)=TextChars.fromPoints(xs.toIntArray())
    private fun singleton(c: Int)=TextChars.fromPoints(intArrayOf(c))
    private fun show(v: Any?): String = when(v) {is Int->Parsec.showChar(v);is String->HaskellString.quote(v);is MathExp->v.show();else->v.toString()}
    private fun <A> not(p: TP<A>)=p.notFollowedBy(::show)
    private class Chain<A>(val head: A,val tail: Chain<A>?)
    private fun <A> list(chain: Chain<A>?): List<A> {val out=mutableListOf<A>();var c=chain;while(c!=null) {out.add(c.head);c=c.tail};return out}
    private fun <A,B> manyTill(p: TP<A>,end: TP<B>): TP<List<A>> {
        fun loop(): TP<Chain<A>?> = end.map {null as Chain<A>?} or p.flatMap {a->d {loop()}.map {Chain(a,it)}}
        return d {loop()}.map(::list)
    }
    private fun <A,B> sepBy1(p: TP<A>,sep: TP<B>): TP<List<A>> = p.flatMap {a->sep.then(p).many().map {listOf(a)+it}}
    private fun <A,B> sepBy(p: TP<A>,sep: TP<B>): TP<List<A>> = sepBy1(p,sep) or pure(emptyList())
    private fun <A,B> sepEndBy1(p: TP<A>,sep: TP<B>): TP<List<A>> {
        fun loop(): TP<Chain<A>> = p.flatMap {a->sep.then(d {loop()}.map {it as Chain<A>?} or pure(null)).map {Chain(a,it)} or pure(Chain(a,null))}
        return d {loop()}.map(::list)
    }
    private fun <A,B> sepEndBy(p: TP<A>,sep: TP<B>): TP<List<A>> = sepEndBy1(p,sep) or pure(emptyList())
    private fun <A> lexeme(p: TP<A>)=p.before(ignorable())
    private fun <A> braces(p: TP<A>)=lexeme(char('{').then(spaces()).then(p).before(spaces()).before(char('}')))
    private fun <A> brackets(p: TP<A>)=lexeme(char('[').then(spaces()).then(p).before(spaces()).before(char(']')))
    private fun symbol(s: String)=lexeme(string(s).attempt())
    private fun boundary(s: String): TP<Unit> {
        val cps=s.codePoints().toArray()
        if(cps.size==2 && cps[0]==92 && !ParserCharacters.isAlpha(cps[1])) return pure(Unit)
        return Parsec.state<Unit>().flatMap {state->letter().then(Parsec.modify<Unit> {it.copy(position=state.position)}).then(zero<Unit>().label("non-letter after $s")) or pure(Unit)}
    }
    private fun ctrl(s: String)=lexeme(string("\\$s").before(boundary("\\$s")).attempt())
    private fun anyCtrl()=lexeme(char('\\').then(satisfy { !ParserCharacters.isAlpha(it) }.map {listOf(it)} or satisfy(ParserCharacters::isAlpha).many1()).map {"\\"+text(it)}.attempt())
    private class Trie {var value: String?=null;val children=sortedMapOf<Int,Trie>()}
    private val tries=ConcurrentHashMap<List<String>,TP<String>>()
    private fun strings(words: List<String>): TP<String> = tries.computeIfAbsent(words.toList()) {keys->
        val root=Trie()
        for(key in keys.asReversed()) if(key.isNotEmpty()) {var n=root;key.codePoints().forEach {n=n.children.getOrPut(it,::Trie)};n.value=key}
        fun trie(n: Trie): TP<String> = (if(n.children.isEmpty()) zero() else any().flatMap {c->n.children[c]?.let {d {trie(it)}} ?: zero()}.attempt()) or (n.value?.let(::pure) ?: zero())
        Parsec.state<Unit>().flatMap {st->trie(root) or Parsec.modify<Unit> {it.copy(position=st.position)}.then(zero<String>().label(keys.joinToString(", ",transform=HaskellString::quote)))}
    }
    private fun commands(words: List<String>)=strings(words).flatMap {cmd->boundary(cmd).before(spaces()).map {cmd}}.attempt()
    private fun comment()=char('%').then(none("\n").skipMany()).before(char('\n').label("lf new-line").optional())
    private fun ignorable(): TP<Unit> = d {choice(listOf(comment(),ctrl("label").then(braces(none("}").skipMany())),ctrl("tag").then(char('*').optional()).then(braces(none("}").skipMany())),ctrl("nonumber").map {},space().many1().label("whitespace").map {},ctrl("allowbreak").map {})).skipMany()}
    private fun asGroup(xs: List<MathExp>): MathExp=xs.singleOrNull() ?: EGrouped(xs)
    private fun ungroup(e: MathExp)=if(e is EGrouped) e.expressions else listOf(e)
    private fun degroup(e: MathExp)=if(e is EGrouped && e.expressions.size==1) e.expressions[0] else e
    private val binomKeys=listOf("\\bangle","\\brace","\\brack","\\choose")
    private fun binom()=char('\\').lookAhead().then(commands(binomKeys))
    private fun manyExp(p: TP<MathExp>,required: Boolean=false): TP<MathExp> =
        (if(required) not(binom()).then(p).many1() else not(binom()).then(p).many()).flatMap {initial->
            binom().flatMap {cmd->p.many().map {rest->val (a,b)=when(cmd) {"\\choose"->"(" to ")";"\\brack"->"[" to "]";"\\brace"->"{" to "}";else->"⟨" to "⟩"};EDelimited(a,b,listOf(DelimitedPart.Expression(EFraction(FractionType.NoLineFrac,asGroup(initial),asGroup(rest))))) as MathExp}} or pure(asGroup(initial))
        }
    private fun inbraces(): TP<MathExp> = d {braces(ignorable().then(manyExp(expr())))}
    private fun inbrackets(): TP<MathExp> = d {brackets(manyExp(not(char(']')).then(expr())))}
    private fun texChar(): TP<MathExp> = none("\n\t\r \\{}").before(spaces()).map {if(ParserCharacters.isDigit(it)) ENumber(singleton(it)) else EIdentifier(singleton(it))}
    private fun texToken(): TP<MathExp> = d {texSymbol() or inbraces() or texChar()}
    private fun expr1(): TP<MathExp> = d {choice(listOf(inbraces(),letter().before(spaces()).map {EIdentifier(singleton(it)) as MathExp},number(),operator(),bareSubSup(),enclosure(),ctrl("hyperref").then(inbrackets().optional()).then(inbraces()),command(),lexeme(satisfy {it>=128}.map {ESymbol(MathTeXUnicode.getSymbolType(it),singleton(it)) as MathExp}),quote().map {EText(TextNormal,it) as MathExp} or textCommand().map {EText(TextNormal,it) as MathExp})).before(ignorable())}
    private fun number(): TP<MathExp> = lexeme(digit().many().flatMap {xs->char('.').then(digit().many1()).map {listOf(46)+it}.attempt().option(emptyList()).flatMap {ys->if(xs.isEmpty() && ys.isEmpty()) zero() else pure(ENumber(text(xs+ys)) as MathExp)}}.attempt())
    private fun primes(): TP<MathExp> = char('\'').many1().before(ignorable()).map {ESymbol(Pun,"⁗".repeat(it.size/4)+listOf("","′","″","‴")[it.size%4])}
    private fun limits(): TP<Boolean?> = ctrl("limits").map {true as Boolean?} or ctrl("nolimits").map {false as Boolean?} or pure(null)
    private fun convertible(e: MathExp)=when(e) {is EMathOperator->e.text in listOf("lim","liminf","limsup","inf","sup","min","max","Pr","det","gcd");is ESymbol->e.type==Op && e.text in listOf("∑","∏","∐","⋀","⋁","⋂","⋃","⨅","⨆");else->false}
    private fun underover(e: MathExp)=when(e) {is EOver->(e.over as? ESymbol)?.let {it.type==TOver && it.text in listOf("︷","⎴","⏞")}==true;is EUnder->(e.under as? ESymbol)?.let {it.type==TUnder && it.text in listOf("︸","⎵","⏟")}==true;else->false}
    private fun subSup(limits: Boolean?,convertible: Boolean,a: MathExp): TP<MathExp> {
        val sub=symbol("_").then(expr1());val sup=symbol("^").then(expr1()) or primes()
        return (sub.flatMap {b->sup.map {c->b to c}}.attempt() or sup.flatMap {c->sub.map {b->b to c}}).map {(b,c)->
            when {limits==true->EUnderover(false,a,b,c);limits==null && (convertible || convertible(a))->EUnderover(true,a,b,c);limits==null && underover(a)->EUnderover(false,a,b,c);else->ESubsup(a,b,c)} as MathExp
        }.attempt()
    }
    private fun singleScript(limits: Boolean?,convertible: Boolean,a: MathExp): TP<MathExp> =
        (one("^_").flatMap {c->spaces().then(expr()).map {c to it}} or primes().map {94 to it}).map {(c,b)->
            val cv=limits==null && (convertible || convertible(a));val over=limits==true || cv || limits==null && underover(a)
            if(c==94) {if(over) EOver(cv,a,b) else ESuper(a,b)} else {if(over) EUnder(cv,a,b) else ESub(a,b)}
        }.attempt()
    private fun bareSubSup(): TP<MathExp> = subSup(null,false,EIdentifier("")) or singleScript(null,false,EIdentifier(""))
    private fun expr(): TP<MathExp> = d {
        char('\\').lookAhead().then(choice(listOf("displaystyle","textstyle","scriptstyle","scriptscriptstyle").map(::ctrl))).optional().then(
            braces(operatorname()).attempt() or operatorname() or expr1().map {it to false}).flatMap {(a,cv)->limits().flatMap {lim->subSup(lim,cv,a) or singleScript(lim,cv,a) or pure(a)}}
    }
    private fun operatorName(e: MathExp): String? = (if(e is EGrouped) e.expressions else listOf(e)).map {operatorText(it,TextNormal)}.let {if(it.any {s->s==null}) null else it.joinToString("")}
    private fun operatorText(e: MathExp,style: MathTextType): String? {
        val todo=ArrayDeque<Pair<MathExp,MathTextType>>();todo.addLast(e to style)
        return buildString {
            while(todo.isNotEmpty()) {
                val (x,sty)=todo.removeLast()
                when(x) {
                    is EIdentifier->append(MathUnicode.toUnicode(sty,x.text))
                    is ENumber->append(MathUnicode.toUnicode(sty,x.text))
                    is EMathOperator->append(MathUnicode.toUnicode(sty,x.text))
                    is EText->append(MathUnicode.toUnicode(x.type,x.text))
                    is ESymbol->append(when(x.text) {"−"->"-";"′","ʹ"->"'";"″"->"''";"‴"->"'''";"⁗"->"''''";else->MathUnicode.toUnicode(sty,x.text)})
                    is ESpace->append(MathShared.getSpaceChars(x.width))
                    is EStyled->x.expressions.asReversed().forEach {todo.addLast(it to x.type)}
                    else->return null
                }
            }
        }
    }
    private fun operatorname(): TP<Pair<MathExp,Boolean>> = d {ctrl("operatorname").then(char('*').before(spaces()).map {true}.option(false)).flatMap {cv->(texSymbol() or braces(manyExp(expr1())) or texChar()).map {tok->(operatorName(tok)?.let(::EMathOperator) ?: EStyled(TextNormal,listOf(tok))) to cv}}}
    private fun operator(): TP<MathExp> = lexeme(strings(MathTeXCommands.operators.keys.toList())).map {MathTeXCommands.operators.getValue(it)}
    private fun basicEnclosure(): TP<MathExp> = lexeme(anyCtrl() or one("()[]|").map(::singleton)).flatMap {MathTeXCommands.enclosures[it]?.let(::pure) ?: zero()}.attempt()
    private fun fence(cmd: String): TP<String> = symbol(cmd).then(basicEnclosure() or symbol(".").map {ESymbol(Open,"") as MathExp}.attempt() or choice(listOf(symbol("<").map {ESymbol(Open,"⟨") as MathExp},symbol(">").map {ESymbol(Close,"⟩") as MathExp})).attempt()).flatMap {if(it is ESymbol && it.type in listOf(Open,Close)) pure(it.text) else zero()}
    private fun delimited(): TP<MathExp> = d {fence("\\left").attempt().flatMap {op->
        (fence("\\middle").map {listOf(DelimitedPart.Middle(it)) as List<DelimitedPart>} or manyExp(not(fence("\\right")).then(expr()),true).map {ungroup(it).map(DelimitedPart::Expression)}).attempt().many().flatMap {contents->(fence("\\right") or pure("")).map {EDelimited(op,it,contents.flatten()) as MathExp}}
    }}
    private fun implicitDelimited(): TP<MathExp> = d {ctrl("lVert").then(
        (fence("\\middle").map {listOf(DelimitedPart.Middle(it)) as List<DelimitedPart>} or manyExp(not(ctrl("rVert").map {"‖"}).then(expr()),true).map {ungroup(it).map(DelimitedPart::Expression)}).attempt().many()).before(ctrl("rVert")).map {EDelimited("‖","‖",it.flatten()) as MathExp}.attempt()}
    private fun enclosure(): TP<MathExp> = delimited() or implicitDelimited() or basicEnclosure()
    private fun command(): TP<MathExp> = d {anyCtrl().flatMap {c->
        Parsec.guard<Unit>(c!="\\end" && c!="\\operatorname").then(choice(listOf(textCommandExp(c),styled(c),colored(c),root(c),xspace(c),mathop(c),unary(c),binary(c),genfrac(c),substack(c),environment(c),if(c=="\\ensuremath") inbraces() else zero(),scaled(c),negated(c),siunitx(c),arrow(c),tSymbol(c))) or Parsec.unexpected("control sequence $c"))
    }.attempt()}
    private fun styled(c: String): TP<MathExp> = MathTeXCommands.styleOps[c]?.let {f->texToken().map {f(ungroup(it))}} ?: zero()
    private fun colored(c: String): TP<MathExp> = if(c=="\\color") inbraces().then(texToken()) else zero()
    private fun root(c: String): TP<MathExp> = if(c in listOf("\\sqrt","\\surd")) inbrackets().flatMap {a->texToken().map {ERoot(a,it) as MathExp}} or texToken().map {ESqrt(it)} else zero()
    private fun unary(c: String): TP<MathExp> = when(c) {
        "\\phantom"->texToken().map(::EPhantom);"\\boxed"->texToken().map(::EBoxed)
        "\\cancel","\\bcancel","\\xcancel"->texToken().map {ECancel(when(c) {"\\cancel"->StrokeType.ForwardSlash;"\\bcancel"->StrokeType.BackSlash;else->StrokeType.XSlash},it)}
        else->zero()
    }
    private fun binary(c: String): TP<MathExp> = if(c !in listOf("\\overset","\\stackrel","\\underset","\\frac","\\tfrac","\\dfrac","\\binom")) zero() else texToken().flatMap {a->texToken().map {b->when(c) {
        "\\overset","\\stackrel"->EOver(false,b,a);"\\underset"->EUnder(false,b,a)
        "\\binom"->EDelimited("(",")",listOf(DelimitedPart.Expression(EFraction(FractionType.NoLineFrac,a,b))))
        else->EFraction(when(c) {"\\tfrac"->FractionType.InlineFrac;"\\dfrac"->FractionType.DisplayFrac;else->FractionType.NormalFrac},a,b)
    }}}
    private fun genfrac(c: String): TP<MathExp> {
        if(c!="\\genfrac") return zero()
        val delim=(char('\\').then(any()) or any()).map(::singleton).option("")
        return (braces(delim) or delim).flatMap {a->(braces(delim) or delim).flatMap {b->
            (braces(string("0pt")).attempt().map {false} or texToken().map {true}).flatMap {bar->
                (braces(char('0')).attempt().map {true} or texToken().map {false}).flatMap {display->texToken().flatMap {x->texToken().map {y->EDelimited(a,b,listOf(DelimitedPart.Expression(EFraction(if(!bar) FractionType.NoLineFrac else if(display) FractionType.DisplayFrac else FractionType.NormalFrac,x,y)))) as MathExp}}}
            }
        }}
    }
    private fun substack(c: String): TP<MathExp> = if(c!="\\substack") zero() else braces(ignorable().then(sepEndBy(manyExp(expr()),endLine(false)))).map {EArray(listOf(AlignCenter),it.map {e->listOf(listOf(e))})}
    private fun scaled(c: String): TP<MathExp> = MathShared.getScalerValue(c)?.let {r->(basicEnclosure() or operator()).map {EScaled(r,it)}} ?: zero()
    private fun texSymbol(): TP<MathExp> = d {operator() or anyCtrl().flatMap {tSymbol(it) or negated(it)}.attempt()}
    private fun negated(c: String): TP<MathExp> = if(c!="\\not") zero() else (texSymbol() or texChar()).flatMap {s->
        fun neg(t: String)=mathTeXNegations[t] ?: (t+"̸")
        when(s) {is ESymbol->if(s.type==Rel) pure(ESymbol(Rel,neg(s.text))) else zero();is EText->pure(EText(s.type,neg(s.text)));is ENumber->pure(ENumber(neg(s.text)));is EIdentifier->pure(EIdentifier(neg(s.text)));else->zero()}
    }
    private fun tSymbol(c: String): TP<MathExp> = d {
        val v=MathTeXCommands.symbols[c]
        when {
            v is ESymbol && v.type in listOf(Accent,TUnder,TOver)->texToken().map {if(v.type==TUnder) EUnder(false,it,v) else EOver(false,it,v)}
            v is ESymbol && v.type==Alpha->pure(EIdentifier(v.text))
            v!=null->pure(v)
            c in listOf("\\mod","\\bmod","\\pmod","\\pod")->expr().map {x->
                val items=when(c) {
                    "\\mod"->listOf(ESpace(MathRational(8,18)),EMathOperator("mod"),ESpace(MathRational(4,18)),degroup(x))
                    "\\bmod"->listOf(ESpace(MathRational(4,18)),EMathOperator("mod"),ESpace(MathRational(4,18)),degroup(x))
                    "\\pmod"->listOf(ESpace(MathRational(4,18)),ESymbol(Open,"("),EMathOperator("mod"),ESpace(MathRational(4,18)),degroup(x),ESymbol(Close,")"))
                    else->listOf(ESpace(MathRational(4,18)),ESymbol(Open,"("),degroup(x),ESymbol(Close,")"))
                };EGrouped(items)
            }
            else->zero()
        }
    }
    private fun xspace(c: String): TP<MathExp> = when(c) {
        "\\enspace"->pure(ESpace(MathRational(1,2)))
        "\\mspace"->braces(digit().many1().map(::text).before(lexeme(string("mu")))).map {ESpace(MathRational(BigInteger(it),BigInteger.valueOf(18)))}
        "\\hspace"->braces(char('-').map {-1}.option(1).flatMap {neg->digit().many1().map(::text).option("").flatMap {a->char('.').then(digit().many1()).map(::text).option("").flatMap {b->
            val den=BigInteger.TEN.pow(b.length);val n=MathRational(((a.ifEmpty {"0"}.toBigInteger()*den)+b.ifEmpty {"0"}.toBigInteger())*neg.toBigInteger(),den)
            lexeme(choice(listOf(string("em").map {MathRational(1)},string("pt").map {MathRational(1,12)},string("in").map {MathRational(6)},string("cm").map {MathRational(50,21)}))).map {ESpace(n*it) as MathExp}
        }}})
        else->zero()
    }
    private fun mathop(c: String): TP<MathExp> {
        val ty=when(c) {"\\mathop"->Op;"\\mathrel"->Rel;"\\mathbin"->Bin;"\\mathord"->Ord;"\\mathopen"->Open;"\\mathclose"->Close;"\\mathpunct"->Pun;else->return zero()}
        return (inbraces() or expr1()).map {e->val es=ungroup(e);val x=es.singleOrNull();when(x) {
            is ESymbol->ESymbol(ty,x.text);is EIdentifier->ESymbol(ty,x.text)
            is EText->if(x.type==TextNormal) ESymbol(ty,x.text) else EStyled(x.type,listOf(ESymbol(ty,x.text)))
            else->if(ty==Op) EMathOperator(es.mapNotNull {operatorName(it)}.joinToString("")) else EGrouped(es)
        }}
    }
    private fun endLine(ams: Boolean): TP<Int> = if(ams) lexeme(string("\\\\").before(comment().skipMany()).before(inbrackets().optional()).map {10}.attempt()) else symbol("\\\\").before(inbrackets().optional()).map {10}.attempt()
    private fun arrayIgnore(): TP<Unit> = d {ignorable().before(ctrl("hline").then(arrayIgnore()).attempt().optional())}
    private fun arrayLine(): TP<List<List<MathExp>>> = sepBy1(arrayIgnore().then(manyExp(not(ctrl("end").map {10} or endLine(false)).attempt().then(expr()).before(arrayIgnore()))).map(::ungroup),symbol("&"))
    private fun skipBraces(): TP<Unit> = d {char('{').then(manyTill(skipBraces() or none("}").map {},char('}'))).map {}}
    private fun alignments(): TP<List<MathAlignment>> = d {braces(choice(listOf(
        one("pmb").before(skipBraces()).map {listOf(AlignLeft)}.attempt(),
        letter().map {listOf(when(it) {108->AlignLeft;114->AlignRight;else->AlignCenter})},
        char('|').map {emptyList()},one(" \t").map {emptyList()},(char('@') or char('!')).before(inbraces()).map {emptyList()},(char('>') or char('<')).before(skipBraces()).map {emptyList()},
        char('*').then(braces(digit().many1())).map(::text).flatMap {n->alignments().map {cols->val count=n.toBigInteger().toLong();if(count<=0) emptyList() else List(Math.toIntExact(count)) {cols}.flatten()}}
    )).many()).map {it.flatten()}}
    private val envNames=listOf("Bmatrix","Vmatrix","align","alignat","aligned","alignedat","array","bmatrix","cases","eqnarray","equation","flalign","flaligned","gather","gathered","matrix","multline","multlined","pmatrix","smallmatrix","split","vmatrix")
    private fun environment(c: String): TP<MathExp> {
        if(c!="\\begin") return zero()
        return braces(strings(envNames).before(char('*').optional())).before(spaces()).flatMap {name->environmentBody(name).before(spaces()).before(ctrl("end")).before(braces(string(name).before(char('*').optional()))).before(spaces())}
    }
    private fun environmentBody(name: String): TP<MathExp> {
        if(name=="array") return alignments().flatMap {als->sepEndBy1(arrayLine(),endLine(false)).map {EArray(als,it)}}
        if(name in listOf("alignat","alignedat")) return inbraces().before(spaces()).then(environmentBody("align"))
        if(name=="equation") return not(ctrl("end").map {10}).then(manyExp(not(endLine(false)).then(expr())))
        val required=name in listOf("eqnarray","align","aligned","flalign","flaligned","split","cases")
        val rows=if(required) sepEndBy1(arrayLine(),endLine(name!="eqnarray")) else sepEndBy(arrayLine(),endLine(true))
        return rows.map {rs->
            val n=if(name in listOf("eqnarray","align","aligned","flalign","flaligned","split")) rs.maxOf {it.size} else rs.firstOrNull()?.size ?: 0
            val cycle=when(name) {"eqnarray"->listOf(AlignRight,AlignCenter,AlignLeft);"align","aligned","split"->listOf(AlignRight,AlignLeft);"flalign","flaligned"->listOf(AlignLeft,AlignRight);"cases"->listOf(AlignLeft);else->listOf(AlignCenter)}
            val arr=EArray(List(n) {cycle[it%cycle.size]},rs)
            val pair=when(name) {"pmatrix"->"(" to ")";"bmatrix"->"[" to "]";"Bmatrix"->"{" to "}";"vmatrix"->"∣" to "∣";"Vmatrix"->"∥" to "∥";"cases"->"{" to "";else->null}
            if(pair==null) arr else EDelimited(pair.first,pair.second,listOf(DelimitedPart.Expression(arr)))
        }
    }
    private fun quote(): TP<String> = choice(listOf("``" to "“","''" to "”","'" to "’","`" to "‘","~" to " ").map {(a,b)->string(a).map {b}.attempt()})
    private fun tok(): TP<Int> = char('{').then(spaces()).then(any()).before(spaces()).before(char('}')).attempt() or any()
    private val accentCommands=mapOf("\\`" to "grave","\\'" to "acute","\\^" to "circ","\\~" to "tilde","\\\"" to "umlaut","\\." to "dot","\\=" to "macron","\\c" to "cedilla","\\v" to "hacek","\\u" to "breve")
    private val textKeys=(mathTeXTextLiterals.keys+accentCommands.keys+"\\char").sortedWith(io.heapy.kotdoc.ast.UnicodeTextOrder)
    private fun textCommand(): TP<String> = d {commands(textKeys).before(char('{').then(spaces()).before(char('}')).attempt().optional()).flatMap {cmd->
        when {
            cmd=="\\char"->char('`').then(any()).map(::singleton).attempt()
            cmd in accentCommands->{val name=accentCommands.getValue(cmd);val accent=tok().map {mathTeXAccents.getValue(name)[it] ?: singleton(it)}; (if(name in listOf("umlaut","dot","macron","cedilla","hacek","breve")) accent.attempt() else accent).option(cmd.drop(1))}
            else->pure(mathTeXTextLiterals.getValue(cmd))
        }
    }}
    private fun textual(): TP<String> = (none("`'-~\u0024{}\\ \t").many1().map(::text) or one(" \t\n").many1().map {" "} or choice(listOf(string("---").map {"—"}.attempt(),string("--").map {"–"}.attempt(),string("-").attempt(),quote())) or textCommand()).label("text")
    private fun textSpace(): TP<MathExp> = char('\\').then(choice(listOf(' ' to MathRational(2,9),'!' to MathRational(-1,6),',' to MathRational(1,6),':' to MathRational(2,9),';' to MathRational(5,18),'>' to MathRational(2,9)).map {(c,r)->char(c).map {ESpace(r) as MathExp}})).attempt()
    private fun innerMath(): TP<MathExp> = choice(listOf("$" to "$","$$" to "$$","\\(" to "\\)","\\[" to "\\]").map {(a,b)->string(a).attempt().then(manyExp(expr())).before(string(b))})
    private fun textContents(op:(String)->MathExp): TP<List<MathExp>> {
        fun chunk(): TP<MathExp> = d {textual().many1().map {op(it.joinToString(""))} or textSpace() or char('{').then(manyTill(chunk(),char('}'))).map(::asGroup) or innerMath()}
        return char('{').then(manyTill(chunk(),char('}')))
    }
    private fun textCommandExp(c: String): TP<MathExp> {
        val op=MathTeXCommands.textOps[c] ?: return zero()
        return (textContents(op) or (textCommand().map(op) or textSpace() or none("\n\t\r \\{}").map {op(singleton(it))}).map {listOf(it)}).before(spaces()).map {if(it.isEmpty()) op("") else asGroup(it)}
    }
    private fun opts()=inbrackets().skipMany()
    private fun siunitx(c: String): TP<MathExp> = when(c) {
        "\\si","\\unit"->si();"\\SI","\\qty"->siValue();"\\SIrange","\\qtyrange"->siRange(true);"\\numrange"->siRange(false);"\\numlist"->siList();"\\num"->siNumber();"\\ang"->siAngle();else->zero()
    }
    private fun si(): TP<MathExp> = d {siUnit() or braces(manyExp(siUnit() or expr()))}
    private fun siNumber(): TP<MathExp> = d {opts().then(braces(siNum()))}
    private fun siNum(): TP<MathExp> = d {numPart().many().map {asGroup(it.flatten())}}
    private fun decimalNum(): TP<List<MathExp>> =
        (char('+').map {""} or char('-').map {"−"}).option("").flatMap {prefix->satisfy {ParserCharacters.isDigit(it) || it==46}.many1().map {prefix+text(it)}.flatMap {base->
            char('(').then(satisfy {ParserCharacters.isDigit(it) || it==46}.many1()).before(char(')')).map(::text).option("").map {unc->
                val suffix=if(unc.isEmpty()) "" else {
                    val at=base.indexOf('.');val x=if(at<0) -1 else base.length-at-1;val y=unc.length
                    " ± "+if(x==0) unc else if(x>y) "0."+"0".repeat(x-y)+unc.trimEnd('0') else unc.take(y-x)+(unc.drop((y-x).coerceAtLeast(0)).trimEnd('0').let {if(it.isEmpty()) "" else ".$it"})
                };listOf(ENumber(base+suffix))
            }
        }}
    private fun numPart(): TP<List<MathExp>> = d {choice(listOf(decimalNum(),char(',').map {listOf(ENumber("."))},string("+-").attempt().map {listOf(EText(TextNormal," ± "))},char('i').map {listOf(EIdentifier("i"))},char('e').then(decimalNum()).map {listOf(ESymbol(Rel," × "),ESuper(ENumber("10"),asGroup(it)))},char('x').map {listOf(ESymbol(Rel," × "))},char(' ').many1().map {emptyList()}))}
    private fun gap(e: MathExp?): MathExp?=e?.let {ESpace(MathRational(4,18))}
    private fun siValue(): TP<MathExp> = d {opts().then(siNumber()).flatMap {value->inbrackets().map {it.takeUnless {e->e==EGrouped(emptyList())}}.option(null).flatMap {prefix->si().map {it as MathExp?}.option(null).map {unit->EGrouped(listOfNotNull(prefix,gap(prefix),value,gap(unit),unit))}}}}
    private fun siRange(units: Boolean): TP<MathExp> = d {opts().then(siNumber()).flatMap {start->brackets(expr()).map {it as MathExp?}.option(null).flatMap {a->siNumber().flatMap {stop->brackets(expr()).map {it as MathExp?}.option(null).flatMap {b->
        (if(units) si().map {it as MathExp?}.option(null) else pure(null)).map {unit->EGrouped(listOfNotNull(a,gap(a),start,gap(unit),unit,EText(TextNormal,"–"),b,gap(b),stop,gap(unit),unit)) as MathExp}
    }}}}}
    private fun siList(): TP<MathExp> = d {opts().then(braces(sepBy(siNum(),spaces().then(char(';')).before(spaces())))).map {xs->
        if(xs.size<=1) asGroup(xs) else EGrouped(buildList {xs.dropLast(1).forEachIndexed {i,e->if(i>0) add(EText(TextNormal,", "));add(e)};add(EText(TextNormal,", & "));add(xs.last())})
    }}
    private fun siAngle(): TP<MathExp> = d {opts().then(braces(sepBy(siNum(),spaces().then(char(';')).before(spaces())))).map {xs->
        EGrouped(xs.take(3).flatMapIndexed {i,e->if(e==EGrouped(emptyList())) emptyList() else listOf(e,EText(TextNormal,listOf("°","′","″")[i]))})
    }}
    private fun siUnit(): TP<MathExp> = d {anyCtrl().map {it.trimStart('\\')}.flatMap {name->
        when(name) {
            "square","cubic"->siUnit().map {ESuper(it,ENumber(if(name=="square") "2" else "3")) as MathExp}
            "raisetothe"->expr().flatMap {n->siUnit().map {ESuper(it,n)}}
            else->{val il=MathTeXCommands.siUnitMap[name];if(il==null) Parsec.fail("not an siunit unit command") else choice(listOf(ctrl("squared").map {ESuper(il,ENumber("2")) as MathExp},ctrl("cubed").map {ESuper(il,ENumber("3")) as MathExp},ctrl("tothe").then(expr()).map {ESuper(il,it) as MathExp})).option(il)}
        }
    }.attempt()}
    private fun arrow(c: String): TP<MathExp> = if(c !in listOf("\\xleftarrow","\\xrightarrow")) zero() else inbrackets().map {it as MathExp?}.option(null).flatMap {under->texToken().map {over->val a=ESymbol(Op,if(c=="\\xleftarrow") "←" else "→");if(under==null) EOver(false,a,over) else EUnderover(false,a,under,over)}}
    private fun fixedList(xs: List<MathExp>): List<MathExp> {
        val out=mutableListOf<MathExp>()
        for(e in xs) {
            var current=e;val previous=out.lastOrNull()
            if(e is ESymbol && e.type==Bin && (previous==null || previous is ESymbol && previous.type in listOf(Bin,Op,Rel,Open,Pun))) current=ESymbol(Ord,e.text)
            else if(e is ESymbol && e.type in listOf(Rel,Close,Pun) && previous is ESymbol && previous.type==Bin) out[out.lastIndex]=ESymbol(Ord,previous.text)
            out.add(current)
        };return out
    }
    private fun fixed(e: MathExp): MathExp=when(e) {
        is EGrouped->EGrouped(fixedList(e.expressions));is EStyled->EStyled(e.type,fixedList(e.expressions));is EArray->EArray(e.alignments,e.rows.map {row->row.map(::fixedList)})
        is EDelimited->{val out=mutableListOf<DelimitedPart>();for(p in e.parts) {
            val prev=out.lastOrNull();val x=(p as? DelimitedPart.Expression)?.expression;val before=(prev as? DelimitedPart.Expression)?.expression
            if(x is ESymbol && x.type==Bin && (prev==null || prev is DelimitedPart.Middle || before is ESymbol && before.type in listOf(Bin,Op,Rel,Open,Pun))) out.add(DelimitedPart.Expression(ESymbol(Ord,x.text)))
            else {if(x is ESymbol && x.type in listOf(Rel,Close,Pun) && before is ESymbol && before.type==Bin) out[out.lastIndex]=DelimitedPart.Expression(ESymbol(Ord,before.text));out.add(p)}
        };EDelimited(e.open,e.close,out)}
        else->e
    }
    private fun error(input: String,e: ParseError): String {
        val lines=input.split('\n').let {if(it.lastOrNull()=="") it.dropLast(1) else it};val line=lines.getOrNull(e.position.line.toInt()-1) ?: "";val offset=maxOf(0,e.position.column-20).toInt()
        val snippet=singletonSequence(line).drop(offset).take(40).joinToString("") {singleton(it)}
        fun texts(kind: MessageKind)=e.messages.filter {it.kind==kind}.map {it.text}.filter(String::isNotEmpty).distinct()
        fun many(prefix: String,xs: List<String>): String {if(xs.isEmpty()) return "";val text=if(xs.size==1) xs[0] else xs.dropLast(1).joinToString(", ")+" or "+xs.last();return if(prefix.isEmpty()) text else "$prefix $text"}
        val sys=e.messages.firstOrNull {it.kind==MessageKind.SysUnExpect};val us=e.messages.filter {it.kind==MessageKind.UnExpect}
        val messages=if(e.messages.isEmpty()) "unknown" else listOf(if(us.isNotEmpty() || sys==null) "" else "unexpected "+sys.text.ifEmpty {"eof"},many("unexpected",texts(MessageKind.UnExpect)),many("expecting",texts(MessageKind.Expect)),many("",texts(MessageKind.Message))).filter(String::isNotEmpty).distinct().joinToString("") {"\n$it"}
        return snippet+"\n"+" ".repeat((e.position.column-offset-1).coerceAtLeast(0).toInt())+"^"+messages
    }
    private fun singletonSequence(s: String)=s.codePoints().toArray().asSequence()
    fun readTeX(input: String): MathTeXResult {
        val original=TextChars.fromString(input);val definitions=MathMacros.parseMacroDefinitions(original);val text=MathMacros.applyMacros(definitions.macros,definitions.rest)
        val result=ignorable().then(manyExp(expr())).before(Parsec.eof<Unit>()).map(::ungroup).run(ParseState(ParseInput(text),SourcePos("formula"),Unit))
        return if(result.success) MathTeXResult.Success(fixedList(checkNotNull(result.value).map {MathShared.everywhereExp(::fixed,it)})) else MathTeXResult.Failure(error(original,result.error))
    }
}
