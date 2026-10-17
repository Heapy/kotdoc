// Text.Pandoc.Writers.AsciiDoc, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane. GNU GPL version 2 or later.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Alignment
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.readers.DokuWikiIdentifiers
import io.heapy.kotdoc.uri.*
import io.heapy.kotdoc.images.*
import io.heapy.kotdoc.formats.HaskellNumber

object AsciiDocWriter {
 fun write(options:WriterOptions,document:Pandoc,report:(LogMessage)->Unit={}):String=Session(options,false,report).document(document)
 fun writeLegacy(options:WriterOptions,document:Pandoc,report:(LogMessage)->Unit={}):String=Session(options,true,report).document(document)
 @Deprecated("Use write") fun writeAsciiDoctor(options:WriterOptions,document:Pandoc,report:(LogMessage)->Unit={}):String=write(options,document,report)
 private sealed interface Work {data class Blocks(val xs:List<Block>):Work;data class B(val b:Block):Work;data class Inlines(val xs:List<Inline>):Work;data class I(val i:Inline):Work}
 private sealed interface CaptionWork {data class Blocks(val values:List<Block>):CaptionWork;data class BlockValue(val value:Block):CaptionWork}
 private class Session(val options:WriterOptions,val legacy:Boolean,val report:(LogMessage)->Unit){
 private val chomp=AsciiDocChomp()

  val l=DocLayout.Text;var definitionMarker="::";var orderedLevel=0L;var bulletLevel=0L;var intraword=false;val autoIds=mutableSetOf<String>();var inList=false;var hasMath=false;var tableLevel=0L
  fun t(s:String)=l.literal(s)
  fun vertical(vararg ds:Doc<String>)=l.vcat(ds.toList())
  fun concat(ds:List<Doc<String>>)=l.hcat(ds)
  val renderer=DeepRecursiveFunction<Work,Doc<String>>{w->when(w){is Work.Blocks->{val parts=mutableListOf<Doc<String>>();for(value in AsciiDocPatternList(w.xs))parts.add(callRecursive(Work.B(value)));concat(parts)};is Work.B->renderBlock(w.b);is Work.Inlines->renderInlines(w.xs);is Work.I->renderInline(w.i)}}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.blocks(xs:List<Block>)=callRecursive(Work.Blocks(xs))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.block(b:Block)=callRecursive(Work.B(b))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inlines(xs:List<Inline>)=callRecursive(Work.Inlines(xs))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inline(i:Inline)=callRecursive(Work.I(i))
  fun document(d:Pandoc):String{
   var ctx=asciiDocMetadata(options,{renderer(Work.Blocks(it))},{chomp.apply(renderer(Work.Inlines(it)))},d.meta)
   val body=renderer(Work.Blocks(AsciiDocSections.make(d.blocks)))
   if(options.template==null)return l.render(if(options.wrapText==WrapOption.WrapAuto)options.columns else null,body)
   val fields=buildMap<String,Val<String>>{
    if("body" !in ctx.entries)put("body",Val.SimpleVal(body))
    if("toc" !in ctx.entries)put("toc",Val.BoolVal(options.tableOfContents))
    if("math" !in ctx.entries)put("math",Val.BoolVal(hasMath&&!legacy))
    if("titleblock" !in ctx.entries)put("titleblock",Val.BoolVal(asciiDocTitleblock(d.meta)))
   }
   ctx=Context(ctx.entries+fields)
   return l.render(if(options.wrapText==WrapOption.WrapAuto)options.columns else null,TemplateRenderer.Text.render(options.template,ctx))
  }
  fun escape(s:String):Doc<String>{val out=StringBuilder();var active=false
   for(c in s){if(c=='+'||c=='|'&&tableLevel>0){if(active){out.append("++");active=false};out.append(if(c=='+')"{plus}"else"{vbar}")}
    else if(c in "{+`*#_<>[]\\|"){if(!active){out.append("++");active=true};out.append(c)}
    else{if(active){out.append("++");active=false};out.append(c)}}
   if(active)out.append("++");return t(out.toString())
  }
  fun spacy(i:Inline,end:Boolean):Boolean=when(i){Inline.Space,Inline.LineBreak,Inline.SoftBreak->true;is Inline.Str->if(i.text.isEmpty())false else (if(end)i.text.codePointBefore(i.text.length)else i.text.codePointAt(0)).let{RegexCharacters.isPunctuation(it)||ParserCharacters.isSpace(it)};is Inline.Link,is Inline.Image->end;else->false}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.intra(i:Inline):Doc<String>{intraword=true;val d=inline(i);intraword=false;return d}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderInlines(xs:List<Inline>):Doc<String>{val saved=intraword;intraword=false;val result=mutableListOf<Doc<String>>()
   val values=AsciiDocPatternList(xs);var at=0
   while(values.hasAt(at)){
    val pair=values.hasAt(at+1)
    val y=values[at]
    if(pair&&!spacy(y,true)){val x=values[at+1];result.add(if(spacy(x,false))inline(y)else intra(y));result.add(intra(x));at+=2}
    else if(pair&&!spacy(values[at+1],false)){result.add(intra(y));at++}
    else{result.add(inline(y));at++}
   }
   intraword=saved;return concat(result)
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.imageArguments(attr:Attr,alt:List<Inline>,src:String,title:String):Doc<String>{
   val observed=AsciiDocPatternList(alt);val actual=if(observed.isEmpty()||asciiDocSingletonEquals(observed,Inline.Str("")))listOf(Inline.Str(dropExtension(src)))else observed
   val label=inlines(actual);val dims=listOf(Direction.Width,Direction.Height).mapNotNull{dir->Dimensions.dimension(dir,attr.copy(keyValues=AsciiDocPatternList(attr.keyValues)))?.let{dim->if(dim is Dimension.Percent)"scaledwidth=$dim"else"$dir="+Dimensions.showInPixel(dim,options.dpi.toBigInteger())}}
   return t(src+"[")+label+t((if(title.isEmpty())""else",title=\"$title\"")+(if(dims.isEmpty())""else","+dims.joinToString(","))+"]")
  }
  // System.FilePath.Posix.splitExtension/dropExtension 1.5.4.0, Neil Mitchell
  // (c)2005-2014, BSD-3-Clause. Only the pinned POSIX profile is used here.
  fun dropExtension(src:String):String{val slash=src.lastIndexOf('/');val dot=src.lastIndexOf('.');return if(dot>slash)src.take(dot)else src}
  fun commaWalk(xs:List<Inline>)=AsciiDocCommaWalk.inlines(xs)
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderInline(i:Inline):Doc<String> = when(i){
   is Inline.Emph->{val observed=AsciiDocPatternList(i.content);val strong=asciiDocSingleConstructor<Inline,Inline.Strong>(observed);if(strong!=null)inline(Inline.Strong(listOf(Inline.Emph(strong.content))))else{val d=inlines(observed);val marker=if(intraword)"__"else"_";t(marker)+d+t(marker)}}
   is Inline.Strong->{val d=inlines(i.content);val marker=if(intraword)"**"else"*";t(marker)+d+t(marker)}
   is Inline.Underline->t("[.underline]#")+inlines(i.content)+t("#")
   is Inline.Strikeout->t("[line-through]#")+inlines(i.content)+t("#")
   is Inline.Superscript->t("^")+inlines(i.content)+t("^")
   is Inline.Subscript->t("~")+inlines(i.content)+t("~")
   is Inline.SmallCaps->t("[smallcaps]#")+inlines(i.content)+t("#")
   is Inline.Quoted->{val d=inlines(i.content);if(i.type==QuoteType.SingleQuote)t(if(legacy)"`"else"'`")+d+t(if(legacy)"'"else"`'")else t(if(legacy)"``"else"\"`")+d+t(if(legacy)"''"else"`\"")}
   is Inline.Code->t("`")+(if(legacy)t(i.text.replace("`","\\'"))else escape(i.text))+t("`")
   is Inline.Str->escape(i.text)
   is Inline.Math->{hasMath=true;if(i.type==MathType.InlineMath)t("latexmath:["+(if(legacy)"$"+i.text+"$"else i.text)+"]")else vertical(l.cr+(if(inList)t("+")else l.blankline),t("[latexmath]"),t("++++"),t(if(legacy)"\\["+i.text+"\\]"else i.text),t("++++"))+l.cr}
   is Inline.RawInline->if(OrgCaseFold.fold(i.format.value)=="asciidoc")t(i.text)else{report(LogMessage.InlineNotRendered(i));l.empty}
   Inline.LineBreak->t(" +")+l.cr
   Inline.Space->l.space
   Inline.SoftBreak->if(options.wrapText==WrapOption.WrapPreserve)l.cr else l.space
   is Inline.Cite->inlines(i.content)
   is Inline.Link->{val label=inlines(commaWalk(i.content));val uri=NetworkURI.parseURI(i.target.url);val prefix=uri==null||CharacterStringChars.build(uri.scheme) !in listOf("http:","https:","ftp:","irc:","mailto:");val suffix=i.target.url.removePrefix("mailto:");val auto=asciiDocSingleConstructor<Inline,Inline.Str>(i.content)?.text?.let{PandocURI.escapeURI(it)==suffix}?:false
    if("--" in i.target.url){if(auto)t("link:++$suffix++[]")else t("link:++${i.target.url}++[")+label+t("]")}
    else if(auto)t(suffix)else t((if(prefix)"link:"else"")+i.target.url+"[")+label+t("]")}
   is Inline.Image->t("image:")+imageArguments(i.attr,i.content,i.target.url,i.target.title)
   is Inline.Note->{val cells=AsciiDocPatternList(i.blocks).iterator();val first=if(cells.hasNext())cells.next()else null;val b=if((first is Block.Para||first is Block.Plain)&&!cells.hasNext())first else null;if(b is Block.Plain)t("footnote:[")+inlines(b.content)+t("]")else if(b is Block.Para)t("footnote:[")+inlines(b.content)+t("]")else t("[multiblock footnote omitted]")}
   is Inline.Span->{val d=inlines(i.content);val marker=if(intraword)"##"else"#";if(asciiDocNull(i.attr.classes)&&i.attr.identifier.isEmpty())d else if(asciiDocSingletonEquals(i.attr.classes,"mark")&&i.attr.identifier.isEmpty())t(marker)+d+t(marker)else t("["+((if(i.attr.identifier.isEmpty())emptyList()else listOf("#"+i.attr.identifier))+asciiDocMap(i.attr.classes){".$it"}).joinToString(" ")+"]"+marker)+d+t(marker)}
  }

  val captionParts=java.util.IdentityHashMap<List<Inline>,List<Inline>>()
  fun captionPart(value:Inlines):Inlines {
   captionParts[value.values]?.let{return Inlines(it)}
   val adapted=asciiDocShapeMap(value.values){i->when(i){
   is Inline.Emph->i.copy(content=AsciiDocPatternList(i.content));is Inline.Strong->i.copy(content=AsciiDocPatternList(i.content))
   is Inline.Underline->i.copy(content=AsciiDocPatternList(i.content));is Inline.Strikeout->i.copy(content=AsciiDocPatternList(i.content))
   is Inline.Superscript->i.copy(content=AsciiDocPatternList(i.content));is Inline.Subscript->i.copy(content=AsciiDocPatternList(i.content));else->i
  }}
   captionParts[value.values]=adapted;captionParts[adapted]=adapted
   return Inlines(adapted)
  }
  fun joinInlines(parts:List<Inlines>,sep:Inlines=Inlines()):Inlines=parts.foldIndexed(Inlines()){i,a,b->a+(if(i==0)Inlines()else sep)+captionPart(b)}
  val captionRenderer=DeepRecursiveFunction<CaptionWork,Inlines>{work->when(work){
   is CaptionWork.Blocks->joinInlines(asciiDocWholeSpine(work.values).map{callRecursive(CaptionWork.BlockValue(it))},Builder.linebreak)
   is CaptionWork.BlockValue->when(val b=work.value){
    is Block.Plain->Inlines(AsciiDocPatternList(b.content))
    is Block.Para->Inlines(AsciiDocPatternList(b.content))
    is Block.Header->Inlines(AsciiDocPatternList(b.content))
    is Block.LineBlock->Inlines(AsciiDocPatternList(b.lines).flatMapIndexed{i,line->if(i==0)AsciiDocPatternList(line) else listOf(Inline.LineBreak)+AsciiDocPatternList(line)})
    is Block.CodeBlock->Builder.codeWith(b.attr,b.text)
    is Block.RawBlock->Inlines(listOf(Inline.RawInline(b.format,b.text)))
    is Block.BlockQuote->callRecursive(CaptionWork.Blocks(b.blocks))
    is Block.Div->callRecursive(CaptionWork.Blocks(b.blocks))
    is Block.Figure->callRecursive(CaptionWork.Blocks(b.blocks))
    is Block.OrderedList->joinInlines(asciiDocWholeSpine(b.items).map{callRecursive(CaptionWork.Blocks(it))})
    is Block.BulletList->joinInlines(asciiDocWholeSpine(b.items).map{callRecursive(CaptionWork.Blocks(it))})
    is Block.DefinitionList->joinInlines(asciiDocWholeSpine(b.items).map{(term,defs)->Inlines(AsciiDocPatternList(term))+Builder.str(":")+Builder.space+joinInlines(asciiDocWholeSpine(defs).map{callRecursive(CaptionWork.Blocks(it))})})
    is Block.Table->{val rows=asciiDocWholeSpine(b.head.rows)+asciiDocWholeSpine(b.bodies).flatMap{asciiDocWholeSpine(it.head)+asciiDocWholeSpine(it.body)}+asciiDocWholeSpine(b.foot.rows);joinInlines(rows.map{row->joinInlines(asciiDocWholeSpine(row.cells).map{callRecursive(CaptionWork.Blocks(it.blocks))})},Builder.linebreak)}
    Block.HorizontalRule->Inlines()
   }
  }}
  fun captionInlines(bs:List<Block>)=captionRenderer(CaptionWork.Blocks(bs)).values
  fun needsEscaping(s:String):Boolean{
   if(s.length>=2&&s.startsWith('[')&&s.endsWith(']'))return true
   val parser=ListParsing.anyOrderedListMarker(ExampleNumbers).flatMap{attr->val two=attr.delimiter==ListNumberDelim.Period&&(attr.style==ListNumberStyle.UpperAlpha||attr.style==ListNumberStyle.UpperRoman&&attr.start in listOf(1L,5L,10L,50L,100L,500L,1000L));val space=PandocTextCharacters.satisfy<ExampleNumbers>{it==32||it==9};if(two)space.then(space)else space}
   val prefix=String(s.codePoints().limit(10).toArray(),0,minOf(10,s.codePointCount(0,s.length)))
   return parser.run(ParseState(ParseInput(prefix),SourcePos("para start",1,1),ExampleNumbers())).success
  }
  fun displayFirst(b:Block):Boolean=when(b){is Block.Plain->(asciiDocFirst(b.content)as? Inline.Math)?.type==MathType.DisplayMath;is Block.Para->(asciiDocFirst(b.content)as? Inline.Math)?.type==MathType.DisplayMath;else->false}
  fun listBegin(xs:List<Block>):Doc<String>{val b=asciiDocFirst(xs);return if(!displayFirstNullable(b)&&(b is Block.Plain||b is Block.Para))l.empty else t("{blank}")}
  fun displayFirstNullable(b:Block?)=b!=null&&displayFirst(b)
  fun task(xs:List<Block>):List<Block>{
   val observed=AsciiDocPatternList(xs);val b=asciiDocFirst(observed)
   fun convert(xs:List<Inline>):List<Inline>{
    val values=AsciiDocPatternList(xs);val first=asciiDocFirst(values)
    if(first is Inline.Str&&first.text in listOf("☐","☒")){
     val cells=values.iterator();cells.next()
     if(cells.hasNext()&&cells.next()==Inline.Space)return values.mapFirst{Inline.RawInline(Format("asciidoc"),if(first.text=="☐")"[ ]"else"[x]")}
    }
    return values
   }
   return when(b){is Block.Plain->observed.mapFirst{b.copy(content=convert(b.content))};is Block.Para->observed.mapFirst{b.copy(content=convert(b.content))};else->observed}
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.listItem(xs:List<Block>,bullet:Boolean):Doc<String>{val level=if(bullet)bulletLevel else orderedLevel;if(bullet)bulletLevel++else orderedLevel++;val actual=if(bullet&&!legacy)task(xs)else xs;var continuation=false;var d=l.empty
   for(b in AsciiDocPatternList(actual)){val x=chomp.apply(block(b));when{
    b is Block.BulletList->{d+=if(continuation)l.blankline+x else l.cr+x;continuation=false}
    b is Block.OrderedList->{d+=if(continuation&&b.attributes.start==1L&&b.attributes.style==ListNumberStyle.DefaultStyle)l.blankline+x else l.cr+x;continuation=false}
    displayFirst(b)->d+=l.cr+x
    (b is Block.Para||b is Block.Plain)&&l.isEmpty(d)->d=x
    else->{d+=l.cr+t("+")+l.cr+x;continuation=true}
   }}
   if(bullet)bulletLevel=level else orderedLevel=level
   return t((if(bullet)"*"else".").repeat((level+1).coerceAtLeast(0).toInt())+" ")+listBegin(actual)+d+l.cr
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderBlock(b:Block):Doc<String> = when(b){
   is Block.Plain->inlines(b.content)+l.blankline
   is Block.Para->{val d=inlines(b.content);(if(needsEscaping(l.render(null,d)))t("{empty}")else l.empty)+d+l.blankline}
   is Block.LineBlock->vertical(t("[verse]"),t("--"),l.nowrap(concat(AsciiDocPatternList(b.lines).flatMapIndexed{n,line->(if(n==0)emptyList()else listOf(l.cr))+if(asciiDocNull(line))l.blankline else inlines(line)})),t("--"),l.blankline)
   is Block.RawBlock->if(OrgCaseFold.fold(b.format.value)=="asciidoc")t(b.text)else{report(LogMessage.BlockNotRendered(b));l.empty}
   Block.HorizontalRule->l.blankline+t("'''''")+l.blankline
   is Block.Header->{val contents=inlines(b.content);val id=DokuWikiIdentifiers.unique(AsciiDocIdentityLists.identifierInlines(b.content,options.isEnabled(ExtensionName.GfmAutoIdentifiers)||options.isEnabled(ExtensionName.AsciiIdentifiers)),autoIds,options.extensions);autoIds.add(id);val ident=if(b.attr.identifier.isEmpty()||options.isEnabled(ExtensionName.AutoIdentifiers)&&b.attr.identifier==id)l.empty else t("[[${b.attr.identifier}]]");vertical(ident,l.nowrap(t("=".repeat((b.level+1).coerceAtLeast(0).toInt()))+l.space+contents))+l.blankline}
   is Block.Figure->{val cap=if(asciiDocNull(b.caption.long))l.empty else t(".")+l.nowrap(inlines(captionInlines(b.caption.long)));l.vcat(asciiDocMap(b.blocks){element->val observed=(element as? Block.Plain)?.content?.let{AsciiDocPatternList(it)};val image=observed?.let{asciiDocSingleConstructor<Inline,Inline.Image>(it)};if(image==null)block(if(observed==null)element else Block.Plain(observed))else vertical(cap,if(b.attr.identifier.isEmpty())l.empty else t("[#${b.attr.identifier}]"),t("image::")+imageArguments(image.attr,image.content,image.target.url,image.target.title))+l.blankline})}
   is Block.CodeBlock->{val classes=AsciiDocPatternList(b.attr.classes).asSequence().toMutableList();val lineNumbers=classes.remove("numberLines");l.flush(if(classes.isEmpty()&&!lineNumbers)vertical(t("...."),t(b.text),t("...."))else vertical(t("["+(listOf(if(lineNumbers)"source%linesnum"else"source")+classes).joinToString(",")+"]"),t("----"),t(b.text),t("----")))+l.blankline}
   is Block.BlockQuote->{val contents=blocks(b.blocks);val nested=if(AsciiDocPatternList(b.blocks).any{it is Block.BlockQuote})vertical(t("--"),contents,t("--"))else contents;vertical(t("____"),chomp.apply(nested),t("____"))+l.blankline}
   is Block.BulletList->{val saved=inList;inList=true;val contents=concat(asciiDocMap(b.items){listItem(it,true)});inList=saved;contents+l.blankline}
   is Block.OrderedList->{val style=when(b.attributes.style){ListNumberStyle.DefaultStyle,ListNumberStyle.Example->emptyList();ListNumberStyle.Decimal->listOf("arabic");else->listOf(LaTeXReaderText.toLower(b.attributes.style.name))};val opts=style+if(b.attributes.start!=1L)listOf("start=${b.attributes.start}")else emptyList();val saved=inList;inList=true;val contents=concat(asciiDocMap(b.items){listItem(it,false)});inList=saved;vertical(if(opts.isEmpty())l.empty else t("["+opts.joinToString(", ")+"]"),contents)+l.blankline}
   is Block.DefinitionList->{val saved=inList;inList=true;val contents=concat(asciiDocMap(b.items){(label,defs)->val labelDoc=inlines(label);val marker=definitionMarker;definitionMarker=if(marker=="::")";;"else"::";val divider=l.cr+t("+")+l.cr;val documents=asciiDocMap(defs){ds->l.vcat(asciiDocMap(ds){chomp.apply(block(it))}.flatMapIndexed{n,d->(if(n==0)emptyList()else listOf(divider))+d})};definitionMarker=marker;labelDoc+t(marker)+l.cr+l.nest(2,l.vcat(documents.map{chomp.apply(it)}.flatMapIndexed{n,d->(if(n==0)emptyList()else listOf(divider))+d}))+l.cr});inList=saved;contents+l.blankline}
   is Block.Div->{val classHead=asciiDocFirst(b.attr.classes);val observedBlocks=AsciiDocPatternList(b.blocks);val first=asciiDocFirst(observedBlocks);if(classHead=="section"&&first is Block.Header)vertical(block(first.copy(attr=first.attr.copy(identifier=b.attr.identifier))),blocks(observedBlocks.dropFirst()))else{
    val name=classHead;val ident=if(b.attr.identifier.isEmpty())l.empty else t("[[${b.attr.identifier}]]");val content=if(name!=null&&(name in listOf("attention","caution","danger","error","hint","important","note","tip","warning")||LaTeXReaderText.toLower(name)=="sidebar")){
     val title=(first as? Block.Div)?.takeIf{asciiDocSingletonEquals(it.attr.classes,"title")};val titleBlocks=title?.blocks?:emptyList();val body=if(title==null)observedBlocks else observedBlocks.dropFirst();val titleDoc=if(asciiDocNull(titleBlocks)||LaTeXReaderText.toLower(asciiDocStringify(titleBlocks).trim{ParserCharacters.isSpace(it.code)})==name)l.empty else t(".")+blocks(titleBlocks);val bodyDoc=blocks(body);val fence=t(if(name=="sidebar")"****"else"====");vertical(t("["+LaTeXReaderText.toUpper(name)+"]"),chomp.apply(titleDoc),fence,chomp.apply(bodyDoc),fence)
    }else blocks(observedBlocks);vertical(ident,content,l.blankline)}}
   is Block.Table->table(b)
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.table(b:Block.Table):Doc<String>{
   val caption=captionInlines(b.caption.long);val captionDoc=inlines(caption);val headers by lazy{AsciiDocTables.adjustEmptyRows(b.head.rows)};val rows by lazy{AsciiDocTables.adjustEmptyRows(AsciiDocTables.bodiesToRows(b.bodies))};val footers by lazy{AsciiDocTables.adjustEmptyRows(b.foot.rows)}
   val aligns by lazy{asciiDocMap(b.columns){it.alignment}}
   val widths by lazy{asciiDocMap(b.columns){when(val width=it.width){ColWidth.Default->0.0;is ColWidth.Width->if(width.value>0)width.value else 0.0}}}
   val simple by lazy{widths.all{it==0.0}};val total by lazy{widths.sum()}
   val percentages by lazy{
    val relative=if(simple)widths else widths.map{it/total};val result=relative.map{HaskellNumber.floor(it*100)}
    if(!simple&&result.isNotEmpty()&&result.fold(java.math.BigInteger.ZERO,java.math.BigInteger::add)<100.toBigInteger())listOf(100.toBigInteger()-result.drop(1).fold(java.math.BigInteger.ZERO,java.math.BigInteger::add))+result.drop(1)else result
   }
   val totalWidth by lazy{HaskellNumber.floor(total*100)}
   fun align(a:Alignment)=when(a){Alignment.AlignLeft->"<";Alignment.AlignCenter->"^";Alignment.AlignRight->">";Alignment.AlignDefault->""}
   val optionsList by lazy{(if(AsciiDocTables.allRowsEmpty(headers))emptyList()else listOf("header"))+(if(AsciiDocTables.allRowsEmpty(footers))emptyList()else listOf("footer"))}
   val spec by lazy{t("["+(if(totalWidth.signum()==0)""else"width=\"$totalWidth%\",")+"cols=\""+aligns.zip(percentages).joinToString(","){(a,w)->align(a)+(if(w.signum()==0)""else"$w%") }+"\","+(if(optionsList.isEmpty())""else"options=\""+optionsList.joinToString(",")+"\",")+"]")}
   val parent=tableLevel;tableLevel=parent+1;val separator=t(if(parent==0L)"|"else"!")
   suspend fun DeepRecursiveScope<Work,Doc<String>>.makeCell(cell:Cell):Doc<String>{val bs=AsciiDocPatternList(cell.blocks);val cells=bs.iterator();val empty= !cells.hasNext();val head=if(empty)null else cells.next();val first=if((head is Block.Plain||head is Block.Para)&&!cells.hasNext())head else null;val content=when{empty->separator;first is Block.Plain->separator+chomp.apply(block(first));first is Block.Para->separator+chomp.apply(block(Block.Plain(first.content)));tableLevel==2L->{report(LogMessage.BlockNotRendered(b));separator};else->vertical(t("a")+separator,blocks(bs))};val span=(if(cell.colSpan>1)cell.colSpan.toString()else"")+(if(cell.rowSpan>1)".${cell.rowSpan}"else"");return t(span+(if(span.isEmpty())""else"+")+align(cell.alignment))+content}
   suspend fun DeepRecursiveScope<Work,Doc<String>>.makeRow(row:Row):Doc<String>{val cells=if(asciiDocNull(row.cells))List(percentages.size){Cell(Attr(),Alignment.AlignDefault,1,1,emptyList())}else row.cells;return l.hsep(asciiDocMap(cells){makeCell(it)})}
   val hs by lazy{AsciiDocTables.adjustHeaders(headers)};val fs by lazy{AsciiDocTables.adjustFooters(footers)}
   val bodyRows=asciiDocGenerated(sequence{
    if(!AsciiDocTables.allRowsEmpty(headers)){val headerRows=AsciiDocPatternList(hs);if(headerRows.hasAt(0))for(row in headerRows.dropFirst())yield(row)}
    for(row in AsciiDocPatternList(rows))yield(row)
    if(!AsciiDocTables.allRowsEmpty(footers))for(row in AsciiDocPatternList(asciiDocInit(fs)))yield(row)
   })
   val body=asciiDocMap(bodyRows){makeRow(it)};val head=asciiDocFirst(hs)?.let{makeRow(it)}?:l.empty;val foot=asciiDocLast(fs)?.let{makeRow(it)}?:l.empty;tableLevel=parent
   val width=(listOf(foot,head)+body).maxOf{l.offset(it)};val bodyDoc=if(width>(if(options.wrapText==WrapOption.WrapAuto)options.columns else 100000L))l.vsep(body)else l.vcat(body);val border=separator+t("===")
   return vertical(if(caption.isEmpty())l.empty else t(".")+captionDoc+l.cr,spec,border,if(AsciiDocTables.allRowsEmpty(headers))l.empty else head,bodyDoc,if(AsciiDocTables.allRowsEmpty(footers))l.empty else foot,border,l.blankline)
  }
 }
}
