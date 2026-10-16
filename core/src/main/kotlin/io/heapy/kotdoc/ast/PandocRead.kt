// Derived Read of pandoc-types 1.23.1.2, © 2006-2023 John MacFarlane; BSD-3-Clause.
// GHC 9.12.2 Read/Lex, © University of Glasgow 1994-2002; BSD-3-Clause.
// Data.Text: ©2008-2012 Bryan O'Sullivan, Duncan Coutts, Tom Harper;
// ©2021 Andrew Lelechenko. BSD-2-Clause; licenses/text-BSD-2-Clause.txt.
// Data.Map: ©Daan Leijen 2002, Andriy Palamarchuk 2008; BSD-style.
// containers original license retained at vendor/containers-0.7/LICENSE.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.ast.ReadShape.Root as R
import io.heapy.kotdoc.ast.ReadShape.Prim as P
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters

/** Original typed Read results, with an untouched Haskell-Char remainder. */
class ReadDictionary<A> internal constructor(private val shape: ReadShape) {
    @Suppress("UNCHECKED_CAST")
    fun readsPrec(precedence: Long, input: CharacterString): List<Pair<A,CharacterString>> = try {
        val parser=ReadCursor(input.points()); val value=parser.parse(shape,precedence) as A
        val result=value to parser.remaining()
        // ReadS is lazy; keep identical alternatives shared until indexed.
        object:AbstractList<Pair<A,CharacterString>>() {
            override val size=parser.multiplicity
            override fun get(index:Int):Pair<A,CharacterString> {
                if(index !in 0 until size) throw IndexOutOfBoundsException(index)
                return result
            }
        }
    } catch (_: NoTypedRead) { emptyList() }
    fun readsPrec(precedence: Long, input: String) = readsPrec(precedence,CharacterStringChars.fromString(input))
    fun readPrec(precedence: Long, input: CharacterString) = readsPrec(precedence,input)
    fun readPrec(precedence: Long, input: String) = readsPrec(precedence,input)
    fun readList(input: CharacterString) = ReadDictionary<List<A>>(ReadShape.Items(shape)).readsPrec(0,input)
    fun readList(input: String) = readList(CharacterStringChars.fromString(input))
    /** GHC list parser ignores enclosing precedence and resets each element to zero. */
    fun readListPrec(precedence: Long, input: CharacterString) = ReadDictionary<List<A>>(ReadShape.Items(shape)).readsPrec(precedence,input)
    fun readListPrec(precedence: Long, input: String) = readListPrec(precedence,CharacterStringChars.fromString(input))
    fun read(input: CharacterString): A {
        // Prelude.read only demands enough alternatives to distinguish zero/one/many.
        val parser=ReadCursor(input.points(),ambiguityLimit=2)
        @Suppress("UNCHECKED_CAST")
        val value=try { parser.parse(shape,0) as A } catch(_:NoTypedRead) {
            throw IllegalArgumentException("Prelude.read: no parse")
        }
        require(parser.remaining().points().all(ParserCharacters::isSpace)) { "Prelude.read: no parse" }
        require(parser.multiplicity==1) { "Prelude.read: ambiguous parse" }
        return value
    }
    fun read(input: String) = read(CharacterStringChars.fromString(input))
}

/** Concrete Definition dictionaries, both Builder Many dictionaries and aliases. */
object PandocRead {
    val Pandoc=ReadDictionary<io.heapy.kotdoc.ast.Pandoc>(ReadShape.Root.PANDOC)
    val Meta=ReadDictionary<Map<String,MetaValue>>(ReadShape.Root.META)
    val MetaValue=ReadDictionary<io.heapy.kotdoc.ast.MetaValue>(ReadShape.Root.METAVALUE)
    val Inline=ReadDictionary<io.heapy.kotdoc.ast.Inline>(ReadShape.Root.INLINE)
    val Block=ReadDictionary<io.heapy.kotdoc.ast.Block>(ReadShape.Root.BLOCK)
    val ListNumberStyle=ReadDictionary<io.heapy.kotdoc.ast.ListNumberStyle>(ReadShape.Root.STYLE)
    val ListNumberDelim=ReadDictionary<io.heapy.kotdoc.ast.ListNumberDelim>(ReadShape.Root.DELIM)
    val Format=ReadDictionary<io.heapy.kotdoc.ast.Format>(ReadShape.Root.FORMAT)
    val RowHeadColumns=ReadDictionary<Long>(ReadShape.Root.ROWHEAD)
    val Alignment=ReadDictionary<io.heapy.kotdoc.ast.Alignment>(ReadShape.Root.ALIGN)
    val ColWidth=ReadDictionary<io.heapy.kotdoc.ast.ColWidth>(ReadShape.Root.WIDTH)
    val Row=ReadDictionary<io.heapy.kotdoc.ast.Row>(ReadShape.Root.ROW)
    val TableHead=ReadDictionary<io.heapy.kotdoc.ast.TableHead>(ReadShape.Root.HEAD)
    val TableBody=ReadDictionary<io.heapy.kotdoc.ast.TableBody>(ReadShape.Root.BODY)
    val TableFoot=ReadDictionary<io.heapy.kotdoc.ast.TableFoot>(ReadShape.Root.FOOT)
    val Caption=ReadDictionary<io.heapy.kotdoc.ast.Caption>(ReadShape.Root.CAPTION)
    val Cell=ReadDictionary<io.heapy.kotdoc.ast.Cell>(ReadShape.Root.CELL)
    val RowSpan=ReadDictionary<Long>(ReadShape.Root.ROWSPAN)
    val ColSpan=ReadDictionary<Long>(ReadShape.Root.COLSPAN)
    val QuoteType=ReadDictionary<io.heapy.kotdoc.ast.QuoteType>(ReadShape.Root.QUOTE)
    val MathType=ReadDictionary<io.heapy.kotdoc.ast.MathType>(ReadShape.Root.MATH)
    val Citation=ReadDictionary<io.heapy.kotdoc.ast.Citation>(ReadShape.Root.CITATION)
    val CitationMode=ReadDictionary<io.heapy.kotdoc.ast.CitationMode>(ReadShape.Root.MODE)
    val Inlines=ReadDictionary<io.heapy.kotdoc.ast.Inlines>(ReadShape.Root.INLINES)
    val Blocks=ReadDictionary<io.heapy.kotdoc.ast.Blocks>(ReadShape.Root.BLOCKS)
    val Attr=ReadDictionary<io.heapy.kotdoc.ast.Attr>(ReadShape.Root.ATTR)
    val Target=ReadDictionary<io.heapy.kotdoc.ast.Target>(ReadShape.Root.TARGET)
    val ListAttributes=ReadDictionary<io.heapy.kotdoc.ast.ListAttributes>(ReadShape.Root.LISTATTR)
    val ColSpec=ReadDictionary<io.heapy.kotdoc.ast.ColSpec>(ReadShape.Root.COLSPEC)
    val ShortCaption=ReadDictionary<List<io.heapy.kotdoc.ast.Inline>>(ReadShape.Items(ReadShape.Root.INLINE))
    val Text=ReadDictionary<String>(ReadShape.Prim.TEXT)
    val Int=ReadDictionary<Long>(ReadShape.Prim.INT)
    val Double=ReadDictionary<kotlin.Double>(ReadShape.Prim.DOUBLE)
    val Bool=ReadDictionary<Boolean>(ReadShape.Prim.BOOL)
    val Char=ReadDictionary<kotlin.Int>(ReadShape.Prim.CHAR)
}

internal sealed interface ReadShape {
    enum class Root: ReadShape { PANDOC,META,METAVALUE,INLINE,BLOCK,STYLE,DELIM,FORMAT,ROWHEAD,ALIGN,WIDTH,ROW,HEAD,BODY,FOOT,CAPTION,CELL,ROWSPAN,COLSPAN,QUOTE,MATH,CITATION,MODE,INLINES,BLOCKS,ATTR,TARGET,LISTATTR,COLSPEC }
    enum class Prim: ReadShape { TEXT,INT,DOUBLE,BOOL,CHAR }
    data class Items(val element: ReadShape): ReadShape
    data class Tuple(val fields: List<ReadShape>): ReadShape
    data class Optional(val element: ReadShape): ReadShape
    data object Mapping: ReadShape
    data class Sequence(val element: ReadShape): ReadShape
}
private class NoTypedRead: RuntimeException(null,null,false,false)
private fun noRead(): Nothing = throw NoTypedRead()
private data class ReadSpec(val fields: List<ReadShape>,val record: List<String>?,val build:(List<Any?>)->Any)
private object ReadSchemas {
    private val roots=mutableMapOf<ReadShape.Root,MutableMap<String,ReadSpec>>()
    private val text=ReadShape.Prim.TEXT; private val int=ReadShape.Prim.INT
    private fun items(s:ReadShape)=ReadShape.Items(s)
    private fun spec(r:ReadShape.Root,name:String,vararg fields:ReadShape,record:List<String>?=null,build:(List<Any?>)->Any) {
        roots.getOrPut(r){mutableMapOf()}[name]=ReadSpec(fields.toList(),record,build)
    }
    @Suppress("UNCHECKED_CAST") private fun <A> List<Any?>.v(i:Int)=this[i] as A
    init {
        val il=items(R.INLINE); val bl=items(R.BLOCK)
        spec(R.PANDOC,"Pandoc",R.META,bl){Pandoc(it.v(0),it.v(1))}
        spec(R.META,"Meta",ReadShape.Mapping,record=listOf("unMeta")){it.v<Map<String,MetaValue>>(0)}
        spec(R.METAVALUE,"MetaMap",ReadShape.Mapping){MetaValue.MetaMap(it.v(0))}
        spec(R.METAVALUE,"MetaList",items(R.METAVALUE)){MetaValue.MetaList(it.v(0))}
        spec(R.METAVALUE,"MetaBool",P.BOOL){MetaValue.MetaBool(it.v(0))}
        spec(R.METAVALUE,"MetaString",text){MetaValue.MetaString(it.v(0))}
        spec(R.METAVALUE,"MetaInlines",il){MetaValue.MetaInlines(it.v(0))}
        spec(R.METAVALUE,"MetaBlocks",bl){MetaValue.MetaBlocks(it.v(0))}
        for(e in ListNumberStyle.entries) spec(R.STYLE,e.name){e}
        for(e in ListNumberDelim.entries) spec(R.DELIM,e.name){e}
        for(e in Alignment.entries) spec(R.ALIGN,e.name){e}
        for(e in QuoteType.entries) spec(R.QUOTE,e.name){e}
        for(e in MathType.entries) spec(R.MATH,e.name){e}
        for(e in CitationMode.entries) spec(R.MODE,e.name){e}
        spec(R.FORMAT,"Format",text){Format(it.v(0))}
        spec(R.ROWHEAD,"RowHeadColumns",int){it.v<Long>(0)}
        spec(R.ROWSPAN,"RowSpan",int){it.v<Long>(0)}
        spec(R.COLSPAN,"ColSpan",int){it.v<Long>(0)}
        spec(R.WIDTH,"ColWidth",P.DOUBLE){ColWidth.Width(it.v(0))}
        spec(R.WIDTH,"ColWidthDefault"){ColWidth.Default}
        spec(R.ROW,"Row",R.ATTR,items(R.CELL)){Row(it.v(0),it.v(1))}
        spec(R.HEAD,"TableHead",R.ATTR,items(R.ROW)){TableHead(it.v(0),it.v(1))}
        spec(R.FOOT,"TableFoot",R.ATTR,items(R.ROW)){TableFoot(it.v(0),it.v(1))}
        spec(R.BODY,"TableBody",R.ATTR,R.ROWHEAD,items(R.ROW),items(R.ROW)){TableBody(it.v(0),it.v(1),it.v(2),it.v(3))}
        spec(R.CELL,"Cell",R.ATTR,R.ALIGN,R.ROWSPAN,R.COLSPAN,bl){Cell(it.v(0),it.v(1),it.v(2),it.v(3),it.v(4))}
        spec(R.CAPTION,"Caption",ReadShape.Optional(il),bl){Caption(it.v(0),it.v(1))}
        spec(R.CITATION,"Citation",text,il,il,R.MODE,int,int,record=listOf("citationId","citationPrefix","citationSuffix","citationMode","citationNoteNum","citationHash")){Citation(it.v(0),it.v(1),it.v(2),it.v(3),it.v(4),it.v(5))}
        spec(R.INLINES,"Many",ReadShape.Sequence(R.INLINE),record=listOf("unMany")){Inlines(it.v(0))}
        spec(R.BLOCKS,"Many",ReadShape.Sequence(R.BLOCK),record=listOf("unMany")){Blocks(it.v(0))}
        spec(R.INLINE,"Str",text){Inline.Str(it.v(0))}
        spec(R.INLINE,"Emph",il){Inline.Emph(it.v(0))};spec(R.INLINE,"Underline",il){Inline.Underline(it.v(0))}
        spec(R.INLINE,"Strong",il){Inline.Strong(it.v(0))};spec(R.INLINE,"Strikeout",il){Inline.Strikeout(it.v(0))}
        spec(R.INLINE,"Superscript",il){Inline.Superscript(it.v(0))};spec(R.INLINE,"Subscript",il){Inline.Subscript(it.v(0))}
        spec(R.INLINE,"SmallCaps",il){Inline.SmallCaps(it.v(0))};spec(R.INLINE,"Quoted",R.QUOTE,il){Inline.Quoted(it.v(0),it.v(1))}
        spec(R.INLINE,"Cite",items(R.CITATION),il){Inline.Cite(it.v(0),it.v(1))}
        spec(R.INLINE,"Code",R.ATTR,text){Inline.Code(it.v(0),it.v(1))}
        spec(R.INLINE,"Space"){Inline.Space};spec(R.INLINE,"SoftBreak"){Inline.SoftBreak};spec(R.INLINE,"LineBreak"){Inline.LineBreak}
        spec(R.INLINE,"Math",R.MATH,text){Inline.Math(it.v(0),it.v(1))}
        spec(R.INLINE,"RawInline",R.FORMAT,text){Inline.RawInline(it.v<Format>(0),it.v<String>(1))}
        spec(R.INLINE,"Link",R.ATTR,il,R.TARGET){Inline.Link(it.v(0),it.v(1),it.v(2))}
        spec(R.INLINE,"Image",R.ATTR,il,R.TARGET){Inline.Image(it.v(0),it.v(1),it.v(2))}
        spec(R.INLINE,"Note",bl){Inline.Note(it.v(0))};spec(R.INLINE,"Span",R.ATTR,il){Inline.Span(it.v(0),it.v(1))}
        spec(R.BLOCK,"Plain",il){Block.Plain(it.v(0))};spec(R.BLOCK,"Para",il){Block.Para(it.v(0))}
        spec(R.BLOCK,"LineBlock",items(il)){Block.LineBlock(it.v(0))}
        spec(R.BLOCK,"CodeBlock",R.ATTR,text){Block.CodeBlock(it.v(0),it.v(1))}
        spec(R.BLOCK,"RawBlock",R.FORMAT,text){Block.RawBlock(it.v<Format>(0),it.v<String>(1))}
        spec(R.BLOCK,"BlockQuote",bl){Block.BlockQuote(it.v(0))}
        spec(R.BLOCK,"OrderedList",R.LISTATTR,items(bl)){Block.OrderedList(it.v(0),it.v(1))}
        spec(R.BLOCK,"BulletList",items(bl)){Block.BulletList(it.v(0))}
        spec(R.BLOCK,"DefinitionList",items(ReadShape.Tuple(listOf(il,items(bl))))){Block.DefinitionList(it.v(0))}
        spec(R.BLOCK,"Header",int,R.ATTR,il){Block.Header(it.v(0),it.v(1),it.v(2))}
        spec(R.BLOCK,"HorizontalRule"){Block.HorizontalRule}
        spec(R.BLOCK,"Table",R.ATTR,R.CAPTION,items(R.COLSPEC),R.HEAD,items(R.BODY),R.FOOT){Block.Table(it.v(0),it.v(1),it.v(2),it.v(3),it.v(4),it.v(5))}
        spec(R.BLOCK,"Figure",R.ATTR,R.CAPTION,bl){Block.Figure(it.v(0),it.v(1),it.v(2))}
        spec(R.BLOCK,"Div",R.ATTR,bl){Block.Div(it.v(0),it.v(1))}
    }
    fun find(root:ReadShape.Root,name:String)=roots[root]?.get(name) ?: noRead()
}

private class ReadCursor(private val points:IntArray,private val ambiguityLimit:Int=Int.MAX_VALUE) {
    private var at=0
    var multiplicity=1; private set
    fun remaining()=CharacterString(points.copyOfRange(at,points.size))
    private data class Task(val shape:ReadShape,val p:Long)
    @Suppress("UNCHECKED_CAST")
    private val parser=DeepRecursiveFunction<Task,Any?> { t ->
        val start=at; val originalMultiplicity=multiplicity
        try {
            when(val s=t.shape) {
                is ReadShape.Items -> {
                    // Char has parens (string +++ readListPrecDefault); the default
                    // also permits parentheses. A bracket list with n outer groups
                    // therefore has n+1 identical source parses, not one.
                    var groups=0
                    if(s.element==ReadShape.Prim.CHAR) while(symbol("(")) groups++
                    spaces()
                    val quoted=s.element==ReadShape.Prim.CHAR && peek()==34
                    val result=if(quoted) token().points!!.toList() else {
                        expect("["); val xs=mutableListOf<Any?>()
                        if(!symbol("]")) while(true) { xs+=callRecursive(Task(s.element,0)); if(symbol("]")) break; expect(",") }
                        xs
                    }
                    repeat(groups) { expect(")") }
                    if(s.element==ReadShape.Prim.CHAR && !quoted) {
                        val product=multiplicity.toLong()*(groups.toLong()+1)
                        if(ambiguityLimit==2) multiplicity=minOf(product,2).toInt()
                        else {
                            require(product<=Int.MAX_VALUE) { "Read result exceeds JVM List capacity" }
                            multiplicity=product.toInt()
                        }
                    }
                    result
                }
                is ReadShape.Tuple -> {
                    expect("("); val xs=mutableListOf<Any?>()
                    for((i,f) in s.fields.withIndex()) { if(i>0)expect(","); xs+=callRecursive(Task(f,0)) };expect(")")
                    if(xs.size==2) xs[0] to xs[1] else xs
                }
                is ReadShape.Optional -> when(ident()) { "Nothing"->null;"Just"->{if(t.p>10)noRead();callRecursive(Task(s.element,11))};else->noRead() }
                ReadShape.Mapping -> {
                    if(t.p>10)noRead(); expectIdent("fromList")
                    val pairs=callRecursive(Task(ReadShape.Items(ReadShape.Tuple(listOf(ReadShape.Prim.TEXT,ReadShape.Root.METAVALUE))),10)) as List<Pair<String,MetaValue>>
                    pairs.toMap().toSortedMap(UnicodeTextOrder)
                }
                is ReadShape.Sequence -> { if(t.p>10)noRead();expectIdent("fromList");callRecursive(Task(ReadShape.Items(s.element),10)) }
                is ReadShape.Prim -> when(s) {
                    ReadShape.Prim.INT -> number(false)
                    ReadShape.Prim.DOUBLE -> number(true)
                    ReadShape.Prim.BOOL -> when(ident()){ "True"->true;"False"->false;else->noRead() }
                    ReadShape.Prim.CHAR -> token().let { if(it.kind!="char")noRead();it.points!!.single() }
                    ReadShape.Prim.TEXT -> {
                        val before=at;val tok=token()
                        val chars=if(tok.kind=="string") tok.points!! else { at=before;(callRecursive(Task(ReadShape.Items(ReadShape.Prim.CHAR),t.p)) as List<Int>).toIntArray() }
                        TextChars.fromPoints(chars)
                    }
                }
                is ReadShape.Root -> when(s) {
                    ReadShape.Root.ATTR -> {
                        val xs=callRecursive(Task(ReadShape.Tuple(listOf(ReadShape.Prim.TEXT,ReadShape.Items(ReadShape.Prim.TEXT),ReadShape.Items(ReadShape.Tuple(listOf(ReadShape.Prim.TEXT,ReadShape.Prim.TEXT))))),t.p)) as List<Any?>
                        Attr(xs[0] as String,xs[1] as List<String>,xs[2] as List<Pair<String,String>>)
                    }
                    ReadShape.Root.TARGET -> (callRecursive(Task(ReadShape.Tuple(listOf(ReadShape.Prim.TEXT,ReadShape.Prim.TEXT)),t.p)) as Pair<String,String>).let { Target(it.first,it.second) }
                    ReadShape.Root.LISTATTR -> (callRecursive(Task(ReadShape.Tuple(listOf(ReadShape.Prim.INT,ReadShape.Root.STYLE,ReadShape.Root.DELIM)),t.p)) as List<Any?>).let { ListAttributes(it[0] as Long,it[1] as ListNumberStyle,it[2] as ListNumberDelim) }
                    ReadShape.Root.COLSPEC -> (callRecursive(Task(ReadShape.Tuple(listOf(ReadShape.Root.ALIGN,ReadShape.Root.WIDTH)),t.p)) as Pair<Alignment,ColWidth>).let { ColSpec(it.first,it.second) }
                    else -> {
                        // Derived record Read binds at 11; positional application binds at 10.
                        val spec=ReadSchemas.find(s,ident());if(spec.fields.isNotEmpty() && t.p>(if(spec.record!=null)11 else 10))noRead()
                        if(spec.record!=null)expect("{")
                        val xs=mutableListOf<Any?>()
                        for((i,f) in spec.fields.withIndex()) {
                            if(spec.record!=null) { if(i>0)expect(",");expectIdent(spec.record[i]);expect("=") }
                            xs+=callRecursive(Task(f,if(spec.record!=null)0 else 11))
                        }
                        if(spec.record!=null)expect("}")
                        spec.build(xs)
                    }
                }
            }
        } catch(_:NoTypedRead) {
            at=start;multiplicity=originalMultiplicity;expect("(");val value=callRecursive(Task(t.shape,0));expect(")");value
        }
    }
    fun parse(shape:ReadShape,p:Long)=parser(Task(shape,p))
    private data class Token(val kind:String,val text:String="",val points:IntArray?=null)
    private fun peek()=points.getOrNull(at)
    private fun take()=points.getOrNull(at++) ?: noRead()
    private fun spaces(){while(peek()?.let(ParserCharacters::isSpace)==true)at++}
    private fun ident()=token().let {if(it.kind!="ident")noRead();it.text}
    private fun expectIdent(name:String){if(ident()!=name)noRead()}
    private fun expect(text:String){if(token().let{it.kind!="punc"||it.text!=text})noRead()}
    private fun symbol(text:String):Boolean {val old=at;return try{expect(text);true}catch(_:NoTypedRead){at=old;false}}
    private fun isSymbol(c:Int)=when(RegexCharacters.category(c)){in 18..21,12,17->c!=39 && c!=34;11->c!=95;else->false} && c !in ",;()[]{}`".map(Char::code)
    private fun token():Token {
        spaces();val start=at;val cp=take()
        if(cp==34) {
            val chars=mutableListOf<Int>()
            while(true) {
                if(peek()==34){at++;break}
                if(peek()==92 && points.getOrNull(at+1)==38){at+=2;continue}
                if(peek()==92 && points.getOrNull(at+1)?.let(ParserCharacters::isSpace)==true){at+=2;spaces();if(take()!=92)noRead();continue}
                chars+=literal()
            }
            return Token("string",points=chars.toIntArray())
        }
        if(cp==39){val x=literal();if(x==39 && points.getOrNull(start+1)!=92)noRead();if(take()!=39)noRead();return Token("char",points=intArrayOf(x))}
        if(cp in ",;()[]{}`".map(Char::code))return Token("punc",cp.toChar().toString())
        if(isSymbol(cp)) {
            while(peek()?.let(::isSymbol)==true)at++
            val text=String(points,start,at-start)
            return Token(if(text in listOf("..","::","=","\\","|","<-","->","@","~","=>"))"punc" else "symbol",text)
        }
        if(ParserCharacters.isAlpha(cp)||cp==95){while(peek()?.let{ParserCharacters.isAlphaNum(it)||it==95||it==39}==true)at++;return Token("ident",String(points,start,at-start))}
        if(cp in 48..57) {
            if(cp==48 && peek() in listOf(98,66,111,79,120,88)) {
                val base=when(peek()){98,66->2;111,79->8;else->16};val prefix=at;at++
                val digits=at;while(digit(peek(),base)>=0)at++
                if(at>digits)return Token("number",String(points,start,at-start))
                at=prefix
            }
            while(digit(peek(),10)>=0)at++
            if(peek()==46 && digit(points.getOrNull(at+1),10)>=0){at++;while(digit(peek(),10)>=0)at++}
            if(peek()==101||peek()==69){val before=at;at++;if(peek()==43||peek()==45)at++;val digits=at;while(digit(peek(),10)>=0)at++;if(at==digits)at=before}
            return Token("number",String(points,start,at-start))
        }
        noRead()
    }
    private fun digit(cp:Int?,base:Int):Int {val d=when(cp){in 48..57->cp!!-48;in 65..70->cp!!-55;in 97..102->cp!!-87;else->-1};return if(d in 0..<base)d else -1}
    private fun number(floating:Boolean):Any {
        var tok=token();var negative=false
        if(tok.kind=="symbol" && tok.text=="-"){negative=true;tok=token()}
        val n:Any=if(floating && tok.kind=="ident" && tok.text in listOf("NaN","Infinity")) {
            if(tok.text=="NaN")Double.NaN else Double.POSITIVE_INFINITY
        } else {
            if(tok.kind!="number")noRead()
            val raw=tok.text
            if(raw.startsWith("0x",true)||raw.startsWith("0o",true)||raw.startsWith("0b",true)) {
                val base=when(raw[1].lowercaseChar()){'x'->16;'o'->8;else->2};val i=raw.substring(2).toBigInteger(base)
                if(floating)i.toDouble() else i.toLong()
            } else if(floating)raw.toDouble() else {if(raw.any{it in ".eE"})noRead();raw.toBigInteger().toLong()}
        }
        return if(!negative)n else if(floating)-(n as Double) else -(n as Long)
    }
    private fun literal():Int {
        val cp=take();if(cp!=92)return cp
        val e=take()
        when(e){97->return 7;98->return 8;102->return 12;110->return 10;114->return 13;116->return 9;118->return 11;92,34,39->return e;94->{val c=take();if(c !in 64..95)noRead();return c-64}}
        if(e in 48..57||e in listOf(111,79,120,88)) {
            val base=when(e){111,79->8;120,88->16;else->10};if(base==10)at--
            val start=at; val originalMultiplicity=multiplicity;var value=0L
            while(digit(peek(),base)>=0){value=(value*base+digit(take(),base)).coerceAtMost(0x110000)}
            if(at==start||value>0x10ffff)noRead();return value.toInt()
        }
        at--;val names=HaskellString.controlNames+listOf("SP","DEL")
        val name=names.sortedByDescending(String::length).firstOrNull { n->n.indices.all{points.getOrNull(at+it)==n[it].code} } ?: noRead()
        at+=name.length;return when(name){"SP"->32;"DEL"->127;else->names.indexOf(name)}
    }
}
