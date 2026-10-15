// Text.TeXMath.Readers.TeX.Macros, © 2010 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.math

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.*

/** Opaque parsed macro; expansion keeps captured raw Chars separate from Text. */
class MathMacro internal constructor(val definition: String,internal val spec: MathMacros.Spec) {
    override fun toString()="Macro "+HaskellString.quote(definition)
}
class MathMacroDefinitions(macros: List<MathMacro>,val rest: String) {val macros=ownMathList(macros)}

object MathMacros {
    internal enum class Kind { Command, Operator, Environment }
    internal class Spec(val kind: Kind,val name: CharacterString,val body: CharacterString,val closer: CharacterString,val required: Int,val default: CharacterString?,val star: Boolean=false)
    private fun raw(s: String)=CharacterStringChars.fromString(s)
    private fun cp(c: Int)=CharacterString(intArrayOf(c))
    private fun concat(xs: Iterable<CharacterString>)=CharacterStringChars.concat(xs)
    private fun join(vararg xs: CharacterString)=concat(xs.asList())
    private fun text(s: CharacterString)=TextChars.fromPoints(s.points())
    private fun <U,A> pure(a: A): Parsec<U,A> = Parsec.pure(a)
    private fun <U> char(c: Char): Parsec<U,Int> = Parsec.char(c.code)
    private fun <U> literal(s: String): Parsec<U,CharacterString> = literal(raw(s))
    private fun <U> literal(s: CharacterString): Parsec<U,CharacterString> = Parsec.primitive {initial->
        var input=initial.input;var position=initial.position;var consumed=false;var error: ParseError?=null
        for(c in s.points()) {
            if(input.point!=c) {
                error=ParseError(initial.position,listOf(ParseMessage(MessageKind.Expect,HaskellString.quoteCodePoints(s.points())),ParseMessage(MessageKind.SysUnExpect,input.point?.let {HaskellString.quoteCodePoints(intArrayOf(it))} ?: "")))
                break
            }
            consumed=true;input=input.tail();position=position.advance(c)
        }
        if(error!=null) ParseReply(false,null,initial,consumed,error)
        else {val next=initial.copy(input=input,position=position);ParseReply(true,s,next,consumed,ParseError(position))}
    }
    private fun <U> any(): Parsec<U,CharacterString> = Parsec.satisfy<U> {true}.map(::cp)
    private fun <U> letter(): Parsec<U,Int> = Parsec.satisfy<U>(ParserCharacters::isAlpha).label("letter")
    private fun <U> newline(): Parsec<U,Int> = char<U>('\n').label("lf new-line")
    private fun <U> spaces(): Parsec<U,Unit> = Parsec.satisfy<U>(ParserCharacters::isSpace).label("space").skipMany().label("white space")
    private fun <U> comment(): Parsec<U,Unit> = char<U>('%').then(newline<U>().notFollowedBy(Parsec.Companion::showChar).then(any()).skipMany()).before(newline())
    private fun <U> skipComment(): Parsec<U,Unit> = comment<U>().skipMany()
    private fun <U> skipSpaceComments(): Parsec<U,Unit> = spaces<U>().then(comment<U>().before(spaces()).skipMany())
    private fun <U,A> choice(ps: List<Parsec<U,A>>): Parsec<U,A> = ps.asReversed().fold(Parsec.zero()) {rest,p->p or rest}
    private class Chain<A>(val value: A,val tail: Chain<A>?)
    private fun <A> values(chain: Chain<A>?): List<A> {val out=mutableListOf<A>();var c=chain;while(c!=null) {out.add(c.value);c=c.tail};return out}
    private fun <U,A,B> manyTill(p: Parsec<U,A>,end: Parsec<U,B>): Parsec<U,List<A>> {
        fun loop(): Parsec<U,Chain<A>?> = end.map {null as Chain<A>?} or p.flatMap {a->Parsec.defer {loop()}.map {Chain(a,it)}}
        return Parsec.defer {loop()}.map(::values)
    }
    private fun <U,A> count(n: Int,p: Parsec<U,A>): Parsec<U,List<A>> =
        if(n<=0) pure(emptyList()) else p.flatMap {a->Parsec.defer {count(n-1,p)}.map {listOf(a)+it}}
    private fun <U,A> sepEndBy(p: Parsec<U,A>,sep: Parsec<U,Unit>): Parsec<U,List<A>> {
        fun loop(): Parsec<U,Chain<A>?> = p.flatMap {a->sep.then(Parsec.defer {loop()}).map {Chain(a,it) as Chain<A>?} or pure(Chain(a,null))} or pure(null)
        return Parsec.defer {loop()}.map(::values)
    }
    private fun <U> ctrlseq(): Parsec<U,CharacterString> = char<U>('\\').then(
        letter<U>().many1().map {CharacterString(it.toIntArray())} or any()).map {join(raw("\\"),it)}
    private fun <U> escaped(chars: String): Parsec<U,CharacterString> = char<U>('\\').then(Parsec.satisfy<U> {it in chars.map(Char::code)}).map {join(raw("\\"),cp(it))}.attempt()
    private fun <U> braces(): Parsec<U,CharacterString> = Parsec.defer {
        char<U>('{').then(manyTill(skipComment<U>().then(
            Parsec.defer {braces<U>()}.map {join(raw("{"),it,raw("}"))} or escaped("{}") or any()),
            skipComment<U>().then(char<U>('}')).attempt())).map(::concat).attempt()
    }
    private fun <U> brackets(): Parsec<U,CharacterString> = char<U>('[').then(skipSpaceComments<U>()).then(
        manyTill(skipComment<U>().then(escaped<U>("[]") or any()),skipSpaceComments<U>().then(char<U>(']')).attempt())).map(::concat).attempt()
    private fun <U> numArgs(): Parsec<U,Int> = skipSpaceComments<U>().then(char<U>('[')).then(skipSpaceComments<U>()).then(
        Parsec.satisfy<U>(ParserCharacters::isDigit).label("digit")).before(skipSpaceComments<U>()).before(char<U>(']')).map {it-48}.attempt().option(0)
    private fun <U> optArg(): Parsec<U,CharacterString?> = brackets<U>().map {it as CharacterString?}.option(null)
    private fun <U> body(): Parsec<U,CharacterString> = braces<U>() or ctrlseq()
    private fun allLetters(s: CharacterString)=s.points().all(ParserCharacters::isAlpha)
    private fun make(kind: Kind,name: CharacterString,n: Int,default: CharacterString?,body: CharacterString,closer: CharacterString=raw(""),star: Boolean=false): MathMacro {
        val number=if(n>0) raw("[$n]") else raw("")
        val optional=default?.let {join(raw("["),it,raw("]"))} ?: raw("")
        val def=when(kind) {
            Kind.Command -> join(raw("\\newcommand{"),name,raw("}"),number,optional,raw("{"),body,raw("}"))
            Kind.Operator -> join(raw("\\DeclareMathOperator"+(if(star) "*" else "")+"{"),name,raw("}{"),body,raw("}"))
            Kind.Environment -> join(raw("\\newenvironment{"),name,raw("}"),number,optional,raw("%\n{"),body,raw("}%\n{"),closer,raw("}"))
        }
        val target=if(kind==Kind.Environment) name else CharacterString(name.points().drop(1).toIntArray())
        return MathMacro(text(def),Spec(kind,target,body,closer,n-(if(default==null) 0 else 1),default,star))
    }
    private fun <U> newcommand(): Parsec<U,MathMacro> = char<U>('\\').then(
        literal<U>("newcommand").attempt() or literal<U>("renewcommand").attempt() or literal("providecommand"))
        .before(char<U>('*').optional()).before(skipSpaceComments<U>()).then(body<U>()).flatMap {name->
            Parsec.guard<U>(name.points().firstOrNull()==92).then(numArgs<U>()).before(skipSpaceComments<U>()).flatMap {n->
                (if(n>0) optArg<U>() else pure(null)).before(skipSpaceComments<U>()).flatMap {default->body<U>().map {make(Kind.Command,name,n,default,it)}}
            }
        }.attempt()
    private fun <U> declareOperator(): Parsec<U,MathMacro> = literal<U>("\\DeclareMathOperator").then(skipSpaceComments<U>()).then(
        literal<U>("*").map {true}.option(false)).before(skipSpaceComments<U>()).flatMap {star->body<U>().flatMap {name->
            Parsec.guard<U>(name.points().firstOrNull()==92).then(skipSpaceComments<U>()).then(body<U>()).map {make(Kind.Operator,name,0,null,it,star=star)}
        }}.attempt()
    private fun <U> newenvironment(): Parsec<U,MathMacro> = char<U>('\\').then(literal<U>("re").optional()).then(literal<U>("newenvironment"))
        .before(char<U>('*').optional()).before(skipSpaceComments<U>()).then(body<U>()).flatMap {name->
            numArgs<U>().before(skipSpaceComments<U>()).flatMap {n->
                (if(n>0) optArg<U>().before(skipSpaceComments<U>()) else pure(null)).flatMap {default->
                    body<U>().before(skipSpaceComments<U>()).flatMap {opener->body<U>().map {closer->make(Kind.Environment,name,n,default,opener,closer)}}
                }
            }
        }.attempt()
    fun <U> pMacroDefinition(): Parsec<U,MathMacro> = newcommand<U>() or declareOperator() or newenvironment()
    fun parseMacroDefinitions(input: String): MathMacroDefinitions {
        val s=TextChars.fromString(input)
        val parser=skipSpaceComments<Unit>().then(sepEndBy(pMacroDefinition<Unit>(),skipSpaceComments())).flatMap {ms->Parsec.state<Unit>().map {MathMacroDefinitions(ms.asReversed(),it.input.text.substring(it.input.index))}}
        val result=parser.run(ParseState(ParseInput(s),SourcePos("input"),Unit))
        return if(result.success) checkNotNull(result.value) else MathMacroDefinitions(emptyList(),s)
    }
    private fun substitute(arguments: List<CharacterString>,input: CharacterString): CharacterString {
        val points=input.points();val out=mutableListOf<CharacterString>();var at=0
        while(at<points.size) {
            if(at+1<points.size && points[at]==35 && points[at+1] in 49..57) {
                val n=points[at+1]-49
                out.add(if(n<arguments.size) arguments[n] else CharacterString(intArrayOf(35,points[at+1])));at+=2
            } else if(at+1<points.size && points[at]==92 && points[at+1]==35) {out.add(raw("\\#"));at+=2}
            else out.add(cp(points[at++]))
        }
        return concat(out)
    }
    private fun <U> arguments(spec: Spec): Parsec<U,List<CharacterString>> =
        (if(spec.default==null) pure<U,CharacterString?>(null) else optArg<U>().map {it ?: spec.default}).flatMap {optional->
            count(spec.required,skipSpaceComments<U>().then(body<U>() or any())).map {args->if(optional==null) args else listOf(optional)+args}
        }
    private fun <U> expand(spec: Spec): Parsec<U,CharacterString> {
        if(spec.kind==Kind.Environment) {
            fun env(): Parsec<U,CharacterString> = literal<U>("\\begin").then(skipSpaceComments<U>()).then(char<U>('{')).then(literal<U>(spec.name))
                .before(skipSpaceComments<U>()).before(char<U>('}')).then(arguments<U>(spec)).flatMap {args->
                    val end=literal<U>("\\end").then(skipSpaceComments<U>()).then(char<U>('{')).then(literal<U>(spec.name)).before(char<U>('}')).attempt()
                    manyTill(Parsec.defer {env()} or any(),end).map {substitute(args,join(spec.body,concat(it),spec.closer))}
                }.attempt()
            return Parsec.defer {env()}
        }
        val guard=if(allLetters(spec.name)) letter<U>().notFollowedBy(Parsec.Companion::showChar) else pure(Unit)
        val start=char<U>('\\').then(literal<U>(spec.name)).before(guard).before(skipSpaceComments<U>())
        return if(spec.kind==Kind.Operator) start.map {join(raw("\\operatorname"+(if(spec.star) "*" else "")+"{"),spec.body,raw("}"))}.attempt()
        else start.then(arguments<U>(spec)).map {substitute(it,join(raw("{"),spec.body,raw("}")))}.attempt()
    }
    fun applyMacros(macros: List<MathMacro>,input: String): String {
        val original=TextChars.fromString(input);if(macros.isEmpty()) return original
        var current=original
        val macro=choice(macros.map {expand<Boolean>(it.spec).map(::text)}).before(Parsec.modify<Boolean> {it.copy(user=true)})
        val ctrl=ctrlseq<Boolean>().map(::text)
        val chunk=Parsec.satisfy<Boolean> {it!=92 && it!=37}.many1().map {TextChars.fromPoints(it.toIntArray())}
        val token=skipComment<Boolean>().then(macro or ctrl or chunk or any<Boolean>().map(::text)).attempt()
        val parser=token.many().flatMap {parts->Parsec.state<Boolean>().map {state->(parts.joinToString("")+state.input.text.substring(state.input.index)) to state.user}}
        var remaining=2L*macros.size+1
        while(remaining-->0) {
            val r=parser.run(ParseState(ParseInput(current),SourcePos("input"),false))
            if(!r.success) return original
            val (next,expanded)=checkNotNull(r.value)
            if(!expanded) return next
            current=next
        }
        return original
    }
}
