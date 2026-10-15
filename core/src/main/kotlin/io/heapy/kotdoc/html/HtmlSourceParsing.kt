// Text.Pandoc.Readers.HTML public Sources parsers, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original source and full notices in upstream/pandoc and licenses/.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.parsing.*

object HtmlSourceParsing {
    private fun <U> char(c: Char)=SourceParsers.char<U>(c.code)
    private fun <U,A,B> till(p: Parsec<U,A>,end: Parsec<U,B>): Parsec<U,List<A>> {
        class Link<A>(val value: A,val next: Link<A>?)
        fun go(): Parsec<U,Link<A>?> = end.map<Link<A>?> { null } or p.flatMap { x -> Parsec.defer { go() }.map<Link<A>?> { Link(x,it) } }
        return Parsec.defer { go() }.map { n -> buildList { var node=n;while(node!=null) {add(node.value);node=node.next} } }
    }
    private fun points(xs: List<Int>)=buildString { xs.forEach(::appendCodePoint) }
    private fun <U,A> count(n: Long,p: Parsec<U,A>): Parsec<U,List<A>> {
        class Link<A>(val value: A,val next: Link<A>?)
        fun go(left: Long): Parsec<U,Link<A>?> = if(left<=0)Parsec.pure(null) else p.flatMap { x -> Parsec.defer { go(left-1) }.map { Link(x,it) } }
        return Parsec.defer { go(n) }.map { first -> buildList { var node=first;while(node!=null) { add(node.value);node=node.next } } }
    }
    fun <U> htmlTag(test: (HtmlTag)->Boolean,options: ReaderOptionsAccess<U>): Parsec<U,Pair<HtmlTag,String>> = char<U>('<').lookAhead().then(Parsec.state<U>()).flatMap { initial ->
        val input=(initial.input.stream as Sources).sourcesToText()
        val tags=HtmlRendering.canonicalize(TagSoup.parseTextTags(input,HtmlParseOptions.Default.copy(tagPosition=true)))
        val next=tags.getOrNull(1)
        if(tags.firstOrNull() !is HtmlTag.Position || next==null || !test(next))Parsec.zero() else {
            val end=tags.drop(2).firstOrNull { it is HtmlTag.Position } as? HtmlTag.Position ?: HtmlTag.Position(input.count { it=='\n' }.toLong()+1,input.substringAfterLast('\n').codePointCount(0,input.substringAfterLast('\n').length).toLong()+1)
            val endpos=if(end.row==1L)initial.position.copy(column=initial.position.column+end.column-1) else initial.position.copy(line=initial.position.line+end.row-1,column=end.column)
            val endAngle=char<U>('>').then(Parsec.state<U>()).flatMap { Parsec.guard<U>(it.position>=endpos) }.attempt()
            fun handle(name: String): Parsec<U,Pair<HtmlTag,String>> = Parsec.guard<U>((isName(name) || name.startsWith('?')) && name.isNotEmpty() && !name.endsWith(':')).then(char<U>('<')).then(till(SourceParsers.anyChar<U>(),endAngle)).map { next to ("<"+points(it)+">") }
            when(next) {
                is HtmlTag.Comment->if(!input.startsWith("<!--"))Parsec.fail("bogus comment mode, HTML5 parse error") else
                    SourceParsers.string<U>("<!--").then(count(next.text.codePointCount(0,next.text.length).toLong(),SourceParsers.anyChar<U>())).then(SourceParsers.string<U>("-->")).then(Parsec.state<U>()).map { next to if(options.extractReaderOptions(it.user).stripComments)"" else "<!--${next.text}-->" }
                is HtmlTag.Open->Parsec.guard<U>(next.name.startsWith('?') || next.attributes.all { isName(it.first) }).then(handle(next.name))
                is HtmlTag.Close->handle(next.name)
                else->Parsec.zero()
            }
        }
    }.attempt()
    fun <U> htmlInBalanced(test: (HtmlTag)->Boolean): Parsec<U,String> = char<U>('<').lookAhead().then(Parsec.state<U>()).flatMap { initial ->
        val tags=HtmlRendering.canonicalize(TagSoup.parseTextTags((initial.input.stream as Sources).sourcesToText(),HtmlParseOptions.Default.copy(tagPosition=true,tagWarning=true)))
        val start=tags.getOrNull(0) as? HtmlTag.Position;val open=tags.getOrNull(1) as? HtmlTag.Open
        if(start==null || open==null || !test(open) || tags.drop(1).firstOrNull() is HtmlTag.Warning)Parsec.zero() else {
            val balanced=htmlInBalancedTags(open.name,tags.drop(1));val end=balanced.dropLast(1).lastOrNull() as? HtmlTag.Position
            if(balanced.lastOrNull() !is HtmlTag.Close || end==null)Parsec.zero() else {
                // anyLine's character-by-character path has the same results
                // here: the requested lines necessarily end before this close.
                val line=till(SourceParsers.anyChar<U>(),char<U>('\n')).map(::points)
                count(end.row-start.row,line).flatMap { lines -> count(end.column-start.column,SourceParsers.anyChar<U>()).flatMap { chars ->
                    SourceParsers.satisfy<U> { it!=62 }.many().before(char<U>('>')).map { lines.joinToString("") { it+"\n" }+points(chars)+points(it)+">" }
                } }
            }
        }
    }.attempt()
    fun htmlInBalancedTags(name: String,tags: List<HtmlTag>): List<HtmlTag> {
        var depth=0
        for(i in tags.indices)when(tags[i]) {
            HtmlTag.Open(name)->depth++
            HtmlTag.Close(name)->{if(depth==1)return tags.take(i+1);depth--}
            else->{val t=tags[i];if(t is HtmlTag.Open && t.name==name)depth++}
        }
        return emptyList()
    }
    private fun isName(text: String): Boolean {
        if(text.isEmpty())return false
        val cs=text.codePoints().toArray()
        return ParserCharacters.isAlpha(cs[0]) && cs.drop(1).all { ParserCharacters.isAlphaNum(it) || it in intArrayOf(58,45,95) }
    }
}
