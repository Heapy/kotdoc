// Text.Pandoc.Writers.RST, Copyright (C) 2006-2024 John MacFarlane.
// GNU GPL version 2 or above; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Target
import io.heapy.kotdoc.ast.Alignment
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.images.*
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.uri.PandocURI

object RstWriter {
 fun write(options:WriterOptions,document:Pandoc,report:(LogMessage)->Unit={}):String=Session(options,report).document(document)
 private fun children(i:Inline):List<Inline> = when(i){
  is Inline.Emph->i.content;is Inline.Underline->i.content;is Inline.Strong->i.content;is Inline.Strikeout->i.content;is Inline.Superscript->i.content;is Inline.Subscript->i.content;is Inline.SmallCaps->i.content;is Inline.Quoted->i.content;is Inline.Cite->i.content;is Inline.Span->i.content;is Inline.Link->i.content;is Inline.Image->i.content;else->listOf(i)
 }
 private fun set(i:Inline,xs:List<Inline>):Inline=when(i){
  is Inline.Emph->i.copy(content=xs);is Inline.Underline->i.copy(content=xs);is Inline.Strong->i.copy(content=xs);is Inline.Strikeout->i.copy(content=xs);is Inline.Superscript->i.copy(content=xs);is Inline.Subscript->i.copy(content=xs);is Inline.SmallCaps->i.copy(content=xs);is Inline.Quoted->i.copy(content=xs);is Inline.Cite->i.copy(content=xs);is Inline.Span->i.copy(content=xs);is Inline.Link->i.copy(content=xs);is Inline.Image->i.copy(content=xs);else->i
 }
 /** Exported upstream flatten contract; deliberately one level, after bottom-up walk. */
 fun flatten(outer:Inline):List<Inline>{
  val contents=children(outer);if(contents.isEmpty())return listOf(outer)
  val result=mutableListOf<Inline>()
  fun append(xs:List<Inline>){val last=result.lastOrNull();if(last!=null&&AstComparison.Inline.equal(set(last,emptyList()),set(outer,emptyList())))result[result.lastIndex]=set(last,children(last)+xs)else result.add(set(outer,xs))}
  for(i in contents)when {
   outer is Inline.Quoted||i is Inline.Quoted->append(listOf(i))
   outer is Inline.Span&&outer.attr.keyValues.isEmpty()||i is Inline.Span&&i.attr.keyValues.isEmpty()->append(listOf(i))
   outer is Inline.Link&&i is Inline.Image->append(listOf(i))
   i is Inline.Link||outer is Inline.Emph&&i is Inline.Strong->result.add(i)
   else->append(children(i))
  };return result
 }
 private fun complex(value:Inline):Boolean {var i=value;while(true)when(i){is Inline.Emph,is Inline.Underline,is Inline.Strong,is Inline.SmallCaps,is Inline.Strikeout,is Inline.Superscript,is Inline.Subscript,is Inline.Link,is Inline.Image,is Inline.Code,is Inline.Math->return true;is Inline.Cite,is Inline.Span->i=children(i).firstOrNull()?:return false;else->return false}}
 private val flagRanges by lazy {checkNotNull(RstWriter::class.java.getResource("/io/heapy/kotdoc/rst-writer-flags.tsv")).readText().lineSequence().filter(String::isNotEmpty).map{it.split('\t').map(String::toInt)}.toList()}
 internal fun flags(c:Int):Int {val ranges=flagRanges;var low=0;var high=ranges.lastIndex;while(low<=high){val mid=(low+high)ushr 1;if(ranges[mid][0]<=c)low=mid+1 else high=mid-1};return ranges[high][1]}
 private fun space(c:Int)=flags(c)and 2!=0
 private fun transform(xs:List<Inline>):List<Inline>{
  val flattened=xs.flatMap(::flatten).flatMap {i->if(complex(i)){val c=children(i);(if(c.firstOrNull()==Inline.Space)listOf(Inline.Space)else emptyList())+set(i,WriterShared.stripLeadingTrailingSpace(c))+(if(c.lastOrNull()==Inline.Space)listOf(Inline.Space)else emptyList())}else listOf(i)}
  val noMathSpace=mutableListOf<Inline>();var at=0;var firstDisplay=false
  while(at<flattened.size){val i=flattened[at++];noMathSpace.add(i);if(!firstDisplay&&i is Inline.Math&&i.type==MathType.DisplayMath){firstDisplay=true;while(flattened.getOrNull(at)==Inline.Space)at++}}
  val filtered=noMathSpace.filterNot {i->when(i){is Inline.Str->i.text.isEmpty();is Inline.Emph,is Inline.Underline,is Inline.Strong,is Inline.Strikeout,is Inline.Superscript,is Inline.Subscript,is Inline.SmallCaps,is Inline.Quoted,is Inline.Cite,is Inline.Span->children(i).isEmpty();is Inline.Link->i.content.isEmpty()&&i.target==Target("","");is Inline.Image->i.content.isEmpty()&&i.target==Target("","");else->false}}
  fun after(i:Inline)=when(i){Inline.Space,Inline.SoftBreak,Inline.LineBreak->true;is Inline.Str->i.text.codePoints().findFirst().orElse(-1).let{it>=0&&(space(it)||it<65536&&it.toChar() in "-.,:;!?\\/'\")]}>–—")};else->false}
  fun before(i:Inline)=when(i){Inline.Space,Inline.SoftBreak,Inline.LineBreak->true;is Inline.Str->if(i.text.isEmpty())false else i.text.codePointBefore(i.text.length).let{space(it)||it<65536&&it.toChar() in "-:/'\"<([{–—"};else->false}
  fun surround(a:Inline,b:Inline):Boolean{if(a !is Inline.Str||b !is Inline.Str||a.text.isEmpty()||b.text.isEmpty())return false;return(a.text.last()to b.text.first())in listOf('\'' to '\'','"' to '"','<' to '>','[' to ']','{' to '}')}
  val out=mutableListOf<Inline>();at=0
  while(at<filtered.size){val a=filtered[at];val b=filtered.getOrNull(at+1);val c=filtered.getOrNull(at+2);if(b!=null&&c!=null&&complex(b)&&surround(a,c)){out.add(a);out.add(b);at+=2;continue};out.add(a);if(b!=null&&(complex(a)&&!after(b)||complex(b)&&!before(a)))out.add(Inline.RawInline(Format("rst"),"\\ "));at++};return out
 }
 private val normalizer=object:PandocVisitor(){override fun transformInlines(value:List<Inline>)=transform(value)}
 private sealed interface Work {
  data class Blocks(val xs:List<Block>,val top:Boolean=false):Work
  data class BlockValue(val value:Block):Work
  data class Inlines(val xs:List<Inline>,val normalize:Boolean=true):Work
  data class InlineValue(val value:Inline):Work
 }
 private sealed interface CaptionWork {
  data class Blocks(val values:List<Block>):CaptionWork
  data class BlockValue(val value:Block):CaptionWork
 }
 private data class Picture(val label:List<Inline>,val attr:Attr,val source:String,val title:String,val target:String?)
 private class Session(var options:WriterOptions,val report:(LogMessage)->Unit){
  val l=DocLayout.Text;val notes=mutableListOf<List<Block>>();val links=mutableListOf<Pair<List<Inline>,Target>>();val pictures=mutableListOf<Picture>();var hasMath=false;var rawTex=false;var top=true;var imageId=1L
  fun joinInlines(parts:List<Inlines>,sep:Inlines=Inlines()):Inlines=parts.foldIndexed(Inlines()){i,a,b->a+(if(i==0)Inlines()else sep)+b}
  val captionRenderer=DeepRecursiveFunction<CaptionWork,Inlines>{work->when(work){
   is CaptionWork.Blocks->joinInlines(work.values.map{callRecursive(CaptionWork.BlockValue(it))},Builder.linebreak)
   is CaptionWork.BlockValue->when(val b=work.value){
    is Block.Plain->Inlines(b.content)
    is Block.Para->Inlines(b.content)
    is Block.Header->Inlines(b.content)
    is Block.LineBlock->Inlines(b.lines.flatMapIndexed{i,line->if(i==0)line else listOf(Inline.LineBreak)+line})
    is Block.CodeBlock->Builder.codeWith(b.attr,b.text)
    is Block.RawBlock->Inlines(listOf(Inline.RawInline(b.format,b.text)))
    is Block.BlockQuote->callRecursive(CaptionWork.Blocks(b.blocks))
    is Block.Div->callRecursive(CaptionWork.Blocks(b.blocks))
    is Block.Figure->callRecursive(CaptionWork.Blocks(b.blocks))
    is Block.OrderedList->joinInlines(b.items.map{callRecursive(CaptionWork.Blocks(it))})
    is Block.BulletList->joinInlines(b.items.map{callRecursive(CaptionWork.Blocks(it))})
    is Block.DefinitionList->joinInlines(b.items.map{(term,defs)->Inlines(term)+Builder.str(":")+Builder.space+joinInlines(defs.map{callRecursive(CaptionWork.Blocks(it))})})
    is Block.Table->{val rows=b.head.rows+b.bodies.flatMap{it.head+it.body}+b.foot.rows;joinInlines(rows.map{row->joinInlines(row.cells.map{callRecursive(CaptionWork.Blocks(it.blocks))})},Builder.linebreak)}
    Block.HorizontalRule->Inlines()
   }
  }}
  fun captionInlines(bs:List<Block>)=captionRenderer(CaptionWork.Blocks(bs)).values
  fun t(s:String)=l.literal(s)
  fun a(vararg docs:Doc<String>)=l.vcat(docs.toList())
  fun sep(vararg docs:Doc<String>)=l.vsep(docs.toList())
  val renderer=DeepRecursiveFunction<Work,Doc<String>>{w->when(w){is Work.Blocks->renderBlocks(w.xs,w.top);is Work.BlockValue->renderBlock(w.value);is Work.Inlines->l.hcat((if(w.normalize)normalizer.walkInlines(w.xs)else w.xs).map{callRecursive(Work.InlineValue(it))});is Work.InlineValue->renderInline(w.value)}}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.blocks(xs:List<Block>,top:Boolean=false)=callRecursive(Work.Blocks(xs,top))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inlineList(xs:List<Inline>,normalize:Boolean=true)=callRecursive(Work.Inlines(xs,normalize))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inline(i:Inline)=callRecursive(Work.InlineValue(i))
  fun docInlines(xs:List<Inline>,normalize:Boolean=true)=renderer(Work.Inlines(xs,normalize))
  fun metaInlines(meta:Map<String,MetaValue>,key:String):List<Inline> = when(val v=meta[key]){is MetaValue.MetaInlines->v.content;is MetaValue.MetaString->listOf(Inline.Str(v.value));is MetaValue.MetaBlocks->when(val b=v.content.singleOrNull()){is Block.Plain->b.content;is Block.Para->b.content;else->emptyList()};else->emptyList()}
  fun normalizeHeadings(bs:List<Block>):List<Block>{val levels=mutableListOf<Long>();return bs.map{b->if(b is Block.Header){while(levels.lastOrNull()?.let{it>=b.level}==true)levels.removeAt(levels.lastIndex);levels.add(b.level);b.copy(level=levels.size.toLong())}else b}}
  fun bordered(d:Doc<String>,c:Char):Doc<String>{val n=l.offset(d);return if(n>0)a(t(c.toString().repeat(n.toInt())),d,t(c.toString().repeat(n.toInt())))else l.empty}
  fun document(d:Pandoc):String {
   val title=metaInlines(d.meta,"title");val titleDoc=if(title.isEmpty())l.empty else a(bordered(docInlines(title),'='),bordered(docInlines(metaInlines(d.meta,"subtitle")),'-'))
   var ctx=WriterMetadata.metaToContext(options,TemplateValues.Text,{renderer(Work.Blocks(it))},{l.chomp(docInlines(it))},d.meta)
   val body=renderer(Work.Blocks(if(options.template!=null)normalizeHeadings(d.blocks)else d.blocks,true))
   val noteDocs=notes.toList().mapIndexed{i,bs->a(l.nowrap(t(".. [${i+1}]")),l.nest(3,renderer(Work.Blocks(bs))))}
   val refs=links.toList().map{(label,target)->val labelDoc=docInlines(label,false);l.nowrap(t(".. _")+(if(':' in l.render(null,labelDoc))t("`")+labelDoc+t("`")else labelDoc)+t(": ")+t(target.url))}
   val pics=pictures.toList().map(::picture)
   val main=l.vsep(listOf(body,l.vsep(noteDocs),l.vcat(refs),l.vcat(pics)))
   fun field(name:String,v:Doc<String>){ctx=WriterMetadata.defField(name,v,ctx,TemplateValues.Text.document)}
   fun bool(name:String,v:Boolean){ctx=WriterMetadata.defField(name,v,ctx,TemplateValues.Text.boolean)}
   field("body",main);bool("toc",options.tableOfContents);field("toc-depth",t(options.tocDepth.toString()));bool("number-sections",options.numberSections);bool("math",hasMath);field("titleblock",t(l.render(null,titleDoc)));bool("rawtex",rawTex)
   return l.render(if(options.wrapText==WrapOption.WrapAuto)options.columns else null,options.template?.let{TemplateRenderer.Text.render(it,ctx)}?:main)
  }
  fun dimensions(attr:Attr):Doc<String>{fun dim(dir:Direction):Doc<String>{val d=Dimensions.dimension(dir,attr)?:return l.empty;return if(d is Dimension.Percent&&dir==Direction.Height)l.empty else t(":$dir: $d")};return a(l.cr+(if(attr.identifier.isEmpty())l.empty else t(":name: ${attr.identifier}")),dim(Direction.Width),dim(Direction.Height))}
  fun picture(p:Picture):Doc<String>{val label=docInlines(p.label,false);val cls=when(p.attr.classes){emptyList<String>()->l.empty;listOf("align-top")->t(":align: top");listOf("align-middle")->t(":align: middle");listOf("align-bottom")->t(":align: bottom");listOf("align-center"),listOf("align-right"),listOf("align-left")->l.empty;else->t(":class: "+p.attr.classes.joinToString(" "))};return l.nowrap(a(t(".. |")+label+t("| image:: ${p.source}"),l.hang(3,l.empty,a(cls,dimensions(p.attr))),p.target?.let{t("   :target: $it")}?:l.empty))}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderBlocks(xs:List<Block>,isTop:Boolean):Doc<String>{val old=top;top=isTop;val docs=mutableListOf<Doc<String>>();var at=0;while(at<xs.size){val b=xs[at++];docs.add(callRecursive(Work.BlockValue(b)));val next=xs.getOrNull(at);if(next is Block.BlockQuote&&b !is Block.Plain&&b !is Block.Header&&b !is Block.LineBlock&&b!=Block.HorizontalRule&&(b !is Block.Para||(b.content.singleOrNull()as?Inline.Image)?.target?.title?.startsWith("fig:")==true)){docs.add(t("..\n\n"));docs.add(callRecursive(Work.BlockValue(next)));at++}};top=old;return l.vcat(docs)}
  fun tight(items:List<List<Block>>):Boolean {val pending=java.util.ArrayDeque<List<Block>>();items.forEach{pending.add(it)};while(pending.isNotEmpty()){val bs=pending.removeLast();if(bs.isEmpty()||bs.first()is Block.Plain)continue;when(val b=bs.singleOrNull()){is Block.BulletList->b.items.forEach{pending.add(it)};is Block.OrderedList->b.items.forEach{pending.add(it)};else->return false}};return true}
  fun endsList(xs:List<Block>)=xs.lastOrNull().let{it is Block.BulletList||it is Block.OrderedList}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.item(xs:List<Block>,marker:String):Doc<String>{val contents=blocks(xs);val fixed=if(xs.firstOrNull()is Block.BlockQuote)sep(t(".."),contents)else contents;return a(l.hang(marker.codePointCount(0,marker.length).toLong(),t(marker),fixed),if(xs.isEmpty()||WriterShared.endsWithPlain(xs)&&!endsList(xs))l.cr else l.blankline)}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.lines(xs:List<List<Inline>>)=l.vcat(xs.map{l.hang(2,t("| "),inlineList(it))})+l.blankline
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderBlock(b:Block):Doc<String> =when(b){
   is Block.Plain->inlineList(b.content)
   is Block.Para->if(Inline.LineBreak in b.content){val xs=mutableListOf<MutableList<Inline>>(mutableListOf());var at=0;xs.clear();while(at<b.content.size){val line=mutableListOf<Inline>();while(at<b.content.size&&b.content[at]!=Inline.LineBreak)line.add(b.content[at++]);xs.add(line);while(b.content.getOrNull(at)==Inline.LineBreak)at++};lines(xs)}else inlineList(b.content)+l.blankline
   is Block.LineBlock->lines(b.lines)
   is Block.RawBlock->{val f=if(b.format==Format("tex"))"latex"else b.format.value;if(b.format==Format("rst"))t(b.text)else a(sep(l.blankline+t(".. raw:: "+LaTeXReaderText.toLower(f)),l.nest(3,t(b.text))),l.blankline)}
   Block.HorizontalRule->a(l.blankline,t("--------------"),l.blankline)
   is Block.Header->{val content=inlineList(b.content);if(top){val auto=LaTeXReaderText.uniqueIdent(b.content,emptySet(),options.extensions);val id=b.attr.identifier;val anchor=if(id.isEmpty()||id==auto)l.empty else a(t(".. _"+(if(':' in id||id.startsWith('_'))"`$id`"else id)+":"),l.blankline);val c=if(b.level>5)' 'else"=-~^'"[(b.level-1).toInt()];l.nowrap(a(anchor,content,t(c.toString().repeat(l.offset(content).toInt())),l.blankline))}else a(l.nowrap(l.hang(3,t(".. "),a(t("rubric:: ")+content,if(b.attr.identifier.isEmpty())l.empty else t(":name: "+b.attr.identifier),if(b.attr.classes.isEmpty())l.empty else t(":class: "+b.attr.classes.joinToString(" "))))),l.blankline)}
   is Block.CodeBlock->{val cls=b.attr.classes;if("haskell" in cls&&"literate" in cls&&options.isEnabled(ExtensionName.LiterateHaskell))a(l.prefixed("> ",t(b.text)),l.blankline)else{val lang=cls.firstOrNull{it !in listOf("sourceCode","literate","numberLines","number-lines","example")};val number=if("numberLines" in cls)t("   :number-lines:"+(b.attr.keyValues.firstOrNull{it.first=="startFrom"}?.second?.let{" $it"}?:""))else l.empty;val start=if(lang==null)t("::")else a(l.blankline+t(".. code:: $lang"),number);a(sep(start,l.nest(3,t(b.text))),l.blankline)}}
   is Block.BlockQuote->l.nest(3,blocks(b.blocks))+l.blankline
   is Block.Div->{if(b.attr==Attr(classes=listOf("title")))l.empty else{val content=blocks(b.blocks);val cls=b.attr.classes;val directive=if(cls.firstOrNull()in listOf("attention","caution","danger","error","hint","important","note","tip","warning","admonition"))t(".. ${cls.first()}::")else t(".. container::")+l.space+t(cls.filter{it!="container"}.joinToString(" "));a(l.blankline,l.blankline+directive,if(b.attr.identifier.isEmpty())l.blankline else a(t("   :name: ${b.attr.identifier}"),l.blankline),l.nest(3,if(b.blocks.firstOrNull()is Block.BlockQuote)sep(t(".."),content)else content),l.blankline)}}
   is Block.BulletList->{val docs=b.items.map{item(it,"- ")};a(l.blankline,if(tight(b.items))l.vcat(docs)else l.vsep(docs),l.blankline)}
   is Block.OrderedList->{val attrs=b.attributes;val markers=if(attrs.start==1L&&attrs.style==ListNumberStyle.DefaultStyle&&attrs.delimiter==ListNumberDelim.DefaultDelim)List(b.items.size){"#."}else DocumentShared.orderedListMarkers(attrs).take(b.items.size).toList();val width=markers.maxOfOrNull{it.codePointCount(0,it.length)}?:0;val docs=b.items.zip(markers).map{(bs,m)->item(bs,m+" ".repeat(width-m.codePointCount(0,m.length))+" ")};a(l.blankline,if(tight(b.items))l.vcat(docs)else l.vsep(docs),l.blankline)}
   is Block.DefinitionList->{val docs=b.items.map{(label,defs)->val labelDoc=inlineList(label);val content=l.vcat(defs.map{blocks(it)});val fixed=if(defs.firstOrNull()?.firstOrNull()is Block.BlockQuote)sep(t(".."),content)else content;a(l.nowrap(labelDoc),l.nest(3,l.nestle(fixed)),if(tight(defs))l.cr else l.blankline)};a(l.blankline,l.vcat(docs),l.blankline)}
   is Block.Figure->{val image=when(val p=b.blocks.singleOrNull()){is Block.Para->p.content.singleOrNull()as?Inline.Image;is Block.Plain->p.content.singleOrNull()as?Inline.Image;else->null};if(image!=null){val description=inlineList(image.content);val capt=blocks(b.caption.long);val dims=dimensions(image.attr);val alt=if(image.content.isEmpty()){if(image.target.title.isEmpty())l.empty else t(":alt: "+image.target.title)}else t(":alt: ")+description;val name=if(b.attr.identifier.isEmpty())l.empty else t("name: "+b.attr.identifier);val cls=image.attr.classes;val align=when(cls){emptyList<String>()->l.empty;listOf("align-right")->t(":align: right");listOf("align-left")->t(":align: left");listOf("align-center")->t(":align: center");else->t(":figclass: "+cls.joinToString(" "))};a(l.hang(3,t(".. "),sep(a(t("figure::")+l.space+t(image.target.url),name,alt,align,dims),capt)),l.blankline)}else a(l.blankline,t(".. container:: float")+l.space+t(b.attr.classes.filter{it!="container"}.joinToString(" ")),if(b.attr.identifier.isEmpty())l.blankline else a(t("   :name: "+b.attr.identifier),l.blankline),l.nest(3,blocks(b.blocks)),l.blankline)}
   is Block.Table->table(b)
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.register(i:Inline.Image,target:String?):Doc<String>{val old=pictures.lastOrNull{AstComparison.list(AstComparison.Inline).equal(it.label,i.content)};fun name():List<Inline>{val n=imageId++;return listOf(Inline.Str("image$n"))};val label=if(old!=null&&old.attr==i.attr&&old.source==i.target.url&&old.title==i.target.title&&old.target==target)i.content else{val label=if(old!=null||i.content.isEmpty()||i.content==listOf(Inline.Str("")))name()else i.content;pictures.add(Picture(label,i.attr,i.target.url,i.target.title,target));label};return inlineList(label,false)}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderInline(i:Inline):Doc<String> =when(i){
   is Inline.Str->{val text=escape(options,i.text);t(if(options.isEnabled(ExtensionName.Smart))WriterShared.unsmartify(options,text)else text)}
   is Inline.Emph->t("*")+inlineList(i.content,false)+t("*")
   is Inline.Underline->inline(Inline.Emph(i.content))
   is Inline.Strong->t("**")+inlineList(i.content,false)+t("**")
   is Inline.Strikeout->t("[STRIKEOUT:")+inlineList(i.content,false)+t("]")
   is Inline.Superscript->t(":sup:`")+inlineList(i.content,false)+t("`")
   is Inline.Subscript->t(":sub:`")+inlineList(i.content,false)+t("`")
   is Inline.SmallCaps->inlineList(i.content,false)
   is Inline.Cite->inlineList(i.content,false)
   is Inline.Span->{val content=inlineList(i.content,false);val role=if(i.attr==Attr(classes=listOf("mark")))"mark"else i.attr.keyValues.firstOrNull{it.first=="role"}?.second;if(role==null)content else t(":$role:`")+content+t("`")}
   is Inline.Quoted->{val smart=options.isEnabled(ExtensionName.Smart);val single=i.type==QuoteType.SingleQuote;val start=if(smart){if(single)"'"else"\""}else if(single)"‘"else"“";val end=if(smart)start else if(single)"’"else"”";t(start)+inlineList(i.content,false)+t(end)}
   is Inline.Code->{val role=if(i.attr.classes==listOf("interpreted-text")&&i.attr.keyValues.size==1&&i.attr.keyValues[0].first=="role")i.attr.keyValues[0].second else null;if(role!=null)t(":$role:`${i.text}`")else{val text=i.text.trim{it in " \r\n\t"};if('`' in i.text)t(":literal:`"+escape(options,text)+"`")else t("``$text``")}}
   is Inline.Math->{hasMath=true;if(i.type==MathType.InlineMath)t(":math:`${i.text}`")else if('\n' in i.text)a(l.blankline,t(".. math::"),l.blankline,l.nest(3,t(i.text)),l.blankline)else a(l.blankline,t(".. math:: ${i.text}"),l.blankline)}
   is Inline.RawInline->when{ i.format==Format("rst")->t(i.text);i.format==Format("latex")||i.format==Format("tex")->{rawTex=true;t(":raw-latex:`${i.text}`")};else->{report(LogMessage.InlineNotRendered(i));l.empty}}
   Inline.LineBreak->l.cr
   Inline.Space->l.space
   Inline.SoftBreak->if(options.wrapText==WrapOption.WrapPreserve)l.cr else l.space
   is Inline.Link->{val str=(i.content.singleOrNull()as?Inline.Str)?.text;val src=i.target.url;if(str!=null&&PandocURI.isURI(src)&&src==PandocURI.escapeURI(if(src.startsWith("mailto:"))"mailto:$str"else str))t(src.removePrefix("mailto:"))else{val image=i.content.singleOrNull()as?Inline.Image;if(image!=null)t("|")+register(image,src)+t("|")else{val label=inlineList(Inlines(i.content).trim().values,false);if(options.referenceLinks){val old=links.lastOrNull{AstComparison.list(AstComparison.Inline).equal(it.first,i.content)};if(old==null){links.add(i.content to i.target);t("`")+label+t("`_")}else if(old.second==i.target)t("`")+label+t("`_")else t("`")+label+t(" <$src>`__")}else t("`")+label+t(" <$src>`__")}}}
   is Inline.Image->t("|")+register(i,null)+t("|")
   is Inline.Note->{val n=notes.size+1;notes.add(i.blocks);t(" [$n]_ ".dropLast(1))}
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.grid(b:Block.Table):Doc<String>{val cols=b.columns.map{it.copy(alignment=Alignment.AlignDefault)};val ann=AnnotatedTable.toTable(Attr(),Tables.emptyCaption,cols,b.head,b.bodies,b.foot);val cells=ann.head.rows.flatMap{it.cells}+ann.foot.rows.flatMap{it.cells}+ann.bodies.flatMap{body->body.head.flatMap{it.cells}+body.body.flatMap{it.head+it.body}};val docs=cells.map{blocks(it.cell.blocks)}.iterator();return GridTable.gridTable(options,{_,_->docs.next()},cols,b.head,b.bodies,b.foot)}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.table(b:Block.Table):Doc<String>{
   val old=DocumentShared.toLegacyTable(b.copy(caption=Caption(null,emptyList()))).copy(caption=captionInlines(b.caption.long));val caption=inlineList(old.caption)
   val hasRowSpan=(b.head.rows+b.bodies.flatMap{it.head+it.body}+b.foot.rows).any{r->r.cells.any{it.rowSpan>1}}
   val simple=old.widths.all{it==0.0}&&old.widths.size>1&&!hasRowSpan
   val doc=when{options.listTables->{val cap=inlineList(old.caption);val rows=if(old.headers.all{it.isEmpty()})old.rows else listOf(old.headers)+old.rows;val content=l.vcat(rows.map{row->l.hang(2,t("* "),l.vcat(row.map{item(it,"- ")}))});val opts=mutableListOf<Pair<String,Doc<String>>>();if(old.widths.isNotEmpty()&&!old.widths.all{it==0.0})opts.add("widths" to t(old.widths.joinToString(" "){io.heapy.kotdoc.formats.HaskellNumber.round(it*options.columns.toDouble()).toLong().toString()}));if(!old.headers.all{it.isEmpty()})opts.add("header-rows" to t("1"));val fields=l.vcat(opts.map{(k,v)->t(":$k: ")+v});t(".. list-table::")+(if(l.isEmpty(cap))l.empty else t(" ")+cap)+l.cr+l.nest(3,a(fields,l.blankline,content,l.blankline))};simple->{val s=simpleTable(b);if(l.offset(s)>options.columns)grid(b)else s};else->grid(b)}
   return a(l.blankline,if(old.caption.isEmpty()||options.listTables)doc else a(l.blankline+t(".. table:: ")+caption,l.blankline,l.nest(3,doc)),l.blankline)
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.simpleTable(b:Block.Table):Doc<String>{
   suspend fun DeepRecursiveScope<Work,Doc<String>>.row(r:Row):List<Pair<Doc<String>,Long?>>{val out=mutableListOf<Pair<Doc<String>,Long?>>();for(c in r.cells){val d=blocks(c.blocks);out.add(d to c.colSpan);if(c.colSpan>1)repeat((c.colSpan-1).toInt()){out.add(l.empty to null)}};return out}
   suspend fun DeepRecursiveScope<Work,Doc<String>>.rows(rs:List<Row>):List<List<Pair<Doc<String>,Long?>>>{val out=rs.map{row(it)}.toMutableList();if(out.isNotEmpty()&&out[0].isNotEmpty()&&l.isEmpty(out[0][0].first))out[0]=out[0].toMutableList().also{it[0]=t("\\ ")to it[0].second};return out}
   val noHead=b.head.rows.all{it.cells.all{c->c.blocks.isEmpty()}};val heads=if(noHead)emptyList()else rows(b.head.rows);val body=rows(b.bodies.flatMap{it.head+it.body}+b.foot.rows);val all=heads+body;val n=all.maxOfOrNull{it.size}?:0;val widths=List(n){i->all.mapNotNull{it.getOrNull(i)}.maxOfOrNull{l.offset(it.first)}?:0L}
   val line=l.nowrap(l.hsep(widths.map{t("=".repeat(it.toInt()))}))
   fun renderRows(rs:List<List<Pair<Doc<String>,Long?>>>):Doc<String> = l.vcat(rs.flatMap{r->val doc=l.hcat(r.zip(widths).flatMapIndexed{i,(p,w)->(if(i==0)emptyList()else listOf(l.lblock(1,t(" "))))+l.lblock(w,p.first)});if(r.any{(it.second?:0)>1}){var at=0;val dash=mutableListOf<Doc<String>>();for((_,span)in r)if(span!=null){val ws=widths.drop(at).take(span.toInt());at+=ws.size;dash.add(t("-".repeat((ws.sum()+maxOf(0,ws.size-1)).toInt())))};listOf(doc,l.hcat(dash.flatMapIndexed{i,d->(if(i==0)emptyList()else listOf(l.lblock(1,t(" "))))+d}))}else listOf(doc)})
   return a(if(noHead)l.empty else a(line,renderRows(heads)),line,renderRows(body),line)
  }
 }
 private fun escape(options:WriterOptions,text:String):String {
  val smart=options.isEnabled(ExtensionName.Smart);val p=text.codePoints().toArray();var at=0;var canStart=true;val out=StringBuilder()
  fun punctuation(c:Int,opening:Boolean)=c>=128&&flags(c)and(if(opening)4 else 8)!=0
  fun follow(c:Int)=c<128&&c.toChar() in "-.,:;!?'\")]}>"||space(c)||punctuation(c,true)
  fun precede(c:Int)=c<128&&c.toChar() in "-:/'\"<([{ "||space(c)||punctuation(c,false)
  while(at<p.size){val c=p[at];val next=p.getOrNull(at+1);val special=c in intArrayOf(42,95,124,96);when{
   c==92->{out.append('\\').appendCodePoint(c);canStart=false}
   smart&&(c==39||c==34)->{out.append('\\').appendCodePoint(c);canStart=true}
   smart&&(c==45&&next==45||c==46&&next==46&&p.getOrNull(at+2)==46)->{out.append('\\').appendCodePoint(c);canStart=false}
   next==null&&special->{out.append('\\').appendCodePoint(c)}
   precede(c)->{out.appendCodePoint(c);canStart=true}
   special&&next!=null&&(!canStart&&follow(next)||canStart&&!space(next))->{out.append('\\').appendCodePoint(c);canStart=false}
   c==95&&next!=null&&flags(next)and 1==0->{out.append('\\').appendCodePoint(c);canStart=false}
   else->{out.appendCodePoint(c);canStart=false}
  };at++};return out.toString()
 }
}
