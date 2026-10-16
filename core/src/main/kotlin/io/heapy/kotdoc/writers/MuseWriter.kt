// Text.Pandoc.Writers.Muse, Pandoc 3.12.1.
// Copyright (C) 2017-2020 Alexander Krotov; GNU GPL version 2 or later.
// Narrow Shared.figureDiv behavior: Copyright (C) 2006-2024 John MacFarlane.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.math.PandocMath
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.readers.DokuWikiIdentifiers
import io.heapy.kotdoc.uri.PandocURI
import io.heapy.kotdoc.images.*
import io.heapy.kotdoc.formats.HaskellNumber

object MuseWriter {
 fun write(options:WriterOptions,document:Pandoc,report:(LogMessage)->Unit={}):String=Session(options,report).document(document)
 private data class Env(val options:WriterOptions,val top:Boolean=true,val inside:Boolean=false,val start:Boolean=true,val link:Boolean=false,val afterSpace:Boolean=false,val oneLine:Boolean=false,val asterisks:Boolean=false,val near:Boolean=false)
 private sealed interface Work {
  data class Blocks(val xs:List<Block>,val e:Env,val flat:Boolean=false):Work
  data class BlockValue(val b:Block,val e:Env):Work
  data class Inlines(val xs:List<Inline>,val e:Env,val prime:Boolean=false):Work
  data class InlineValue(val i:Inline,val e:Env):Work
  data class Notes(val e:Env):Work
 }
 private sealed interface CaptionWork {
  data class Blocks(val values:List<Block>):CaptionWork
  data class BlockValue(val value:Block):CaptionWork
 }
 private class Session(val options:WriterOptions,val report:(LogMessage)->Unit) {
  val l=DocLayout.Text;var notes=mutableListOf<List<Block>>();var noteNum=1L;val ids=mutableSetOf<String>();var tags=false
  val runtime=PandocStateContext(output={_,message->report(message)})
  val renderer=DeepRecursiveFunction<Work,Doc<String>> { w->when(w){
   is Work.Blocks->renderBlocks(w.xs,w.e,w.flat)
   is Work.BlockValue->renderBlock(w.b,w.e)
   is Work.Inlines->renderInlines(w.xs,w.e,w.prime)
   is Work.InlineValue->renderInline(w.i,w.e)
   is Work.Notes->currentNotes(w.e)
  }}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.blocks(xs:List<Block>,e:Env,flat:Boolean=false)=callRecursive(Work.Blocks(xs,e,flat))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.block(b:Block,e:Env)=callRecursive(Work.BlockValue(b,e))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inlines(xs:List<Inline>,e:Env,prime:Boolean=false)=callRecursive(Work.Inlines(xs,e,prime))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inline(i:Inline,e:Env)=callRecursive(Work.InlineValue(i,e))
  // Complete Shared.blockToInlines/blocksToInlines semantics on heap continuations.
  // Do not force this for grid tables: the original legacy caption is lazy.
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
  fun document(d:Pandoc):String {
   val e=Env(options);val context=WriterMetadata.metaToContext(options,TemplateValues.Text,{renderer(Work.Blocks(it,e))},{l.chomp(renderer(Work.Inlines(it,e)))},d.meta)
   val body=renderer(Work.Blocks(d.blocks,e));val tail=renderer(Work.Notes(e))
   val main=l.separated(body,tail);val ctx=WriterMetadata.defField("body",main,context,TemplateValues.Text.document)
   return l.render(if(options.wrapText==WrapOption.WrapAuto)options.columns else null,options.template?.let{TemplateRenderer.Text.render(it,ctx)}?:main)
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.currentNotes(e:Env):Doc<String> {
   val current=notes.toList();notes.clear();val start=noteNum;noteNum+=current.size
   return l.vsep(current.mapIndexed {i,n->l.hang(("[${start+i}] ").length.toLong(),t("[${start+i}] "),blocks(n,e.copy(inside=true,start=true,afterSpace=true)))+l.blankline})
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderBlocks(xs:List<Block>,env:Env,flat:Boolean):Doc<String> {
   val e=if(flat)env else env.copy(top=!env.inside,inside=true);val result=mutableListOf<Doc<String>>()
   for((i,b) in xs.withIndex()){
    var value:Doc<String>
    if(b is Block.Header && e.top && e.options.referenceLocation==ReferenceLocation.EndOfSection){val h=block(b,e);value=l.separated(currentNotes(e),h)}else value=block(b,e)
    if(e.top && e.options.referenceLocation==ReferenceLocation.EndOfBlock)value=l.separated(value,currentNotes(e))+l.blankline
    val next=xs.getOrNull(i+1)
    val n=when {b is Block.BulletList&&next is Block.BulletList->2L;b is Block.DefinitionList&&next is Block.DefinitionList->2L;b is Block.OrderedList&&next is Block.OrderedList&&style(b.attributes.style)==style(next.attributes.style)->2L;else->0L}
    result.add(value);result.add(l.blanklines(n))
   };return l.hcat(result)
  }
  fun style(s:ListNumberStyle)=if(s==ListNumberStyle.DefaultStyle)ListNumberStyle.Decimal else s
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderBlock(b:Block,e:Env):Doc<String> = when(b){
   is Block.Plain->inlines(b.content,e,true)
   is Block.Para->inlines(b.content,e,true)+l.blankline
   is Block.LineBlock->l.nowrap(l.vcat(b.lines.map{t("> ")+inlines(it,e.copy(oneLine=true))}))+l.blankline
   is Block.CodeBlock->l.above(l.above(l.above(t("<example>"),t(b.text)),t("</example>")),l.blankline)
   is Block.RawBlock->l.above(l.above(l.above(l.above(l.blankline,t("<literal style=\"${b.format.value}\">")),t(b.text)),t("</literal>")),l.blankline)
   is Block.BlockQuote->l.above(l.above(l.blankline+t("<quote>"),l.nest(0,blocks(b.blocks,e,true))),t("</quote>"))+l.blankline
   is Block.BulletList->{val docs=b.items.map{tags=false;l.hang(2,t("- "),blocks(it,e))};l.above(if(e.top)l.nest(1,l.vcat(docs))else l.vcat(docs),l.blankline)}
   is Block.OrderedList->{val markers=DocumentShared.orderedListMarkers(b.attributes.copy(delimiter=ListNumberDelim.Period)).take(b.items.size).toList();val docs=b.items.zip(markers).map{(bs,m)->l.hang(m.codePointCount(0,m.length).toLong()+1,t(m)+l.space,blocks(bs,e))};l.above(if(e.top)l.nest(1,l.vcat(docs))else l.vcat(docs),l.blankline)}
   is Block.DefinitionList->{val docs=b.items.map{(label,defs)->tags=false;val labelDoc=inlines(label,e.copy(oneLine=true,afterSpace=true),true);val width=l.render(null,labelDoc).split('\n').maxOfOrNull{it.codePointCount(0,it.length)}?.toLong()?:0;val desc=defs.map{l.hang(4,t(" :: "),blocks(it,e))};l.hang(width,l.nowrap(labelDoc),l.vcat(desc))};l.above(if(e.top)l.nest(1,l.vcat(docs))else l.vcat(docs),l.blankline)}
   is Block.Header->{val content=inlines(b.content,e.copy(oneLine=true),true);val auto=DokuWikiIdentifiers.unique(b.content,ids,e.options.extensions);ids.add(auto);val anchor=if(b.attr.identifier.isEmpty()||e.options.isEnabled(ExtensionName.AutoIdentifiers)&&b.attr.identifier==auto)l.empty else t("#${b.attr.identifier}")+l.cr;val header=if(e.top)t("*".repeat(b.level.coerceAtLeast(0).toInt()))+l.space else l.empty;l.blankline+l.above(anchor,l.nowrap(header+content))+l.blankline}
   Block.HorizontalRule->l.above(l.above(l.blankline,t("----")),l.blankline)
   is Block.Div->blocks(b.blocks,e,true)
   is Block.Figure->block(Block.Div(b.attr,b.blocks+if(b.caption.long.isEmpty())emptyList()else listOf(Block.Div(Attr(classes=listOf("caption")),b.caption.long))),e)
   is Block.Table->table(b,e)
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.table(b:Block.Table,e:Env):Doc<String> {
   val old=DocumentShared.toLegacyTable(b.copy(caption=Caption(null,emptyList())));val num=(listOf(old.alignments.size,old.widths.size,old.headers.size)+old.rows.map{it.size}).maxOrNull()?:0
   if(TableSimplicity.onlySimpleTableCells(listOf(old.headers)+old.rows)&&old.widths.all{it==0.0}&&num>1){
    val caption=captionInlines(b.caption.long);val cap=inlines(caption,e);val head=old.headers.map{blocks(it,e)};val rows=old.rows.map{r->r.map{blocks(it,e)}}
    val widths=List(maxOf(head.size,rows.maxOfOrNull{it.size}?:0)){i->(listOf(head)+rows).mapNotNull{it.getOrNull(i)}.maxOfOrNull{l.offset(it)}?:0}
    fun row(sep:String,ds:List<Doc<String>>)=l.hcat(ds.zip(widths).flatMapIndexed{i,(d,w)->(if(i==0)emptyList()else listOf(l.lblock(sep.length.toLong(),t(sep))))+l.lblock(w,d)})
    val noHeader=old.headers.all{it.isEmpty()};val headDoc=if(noHeader)l.empty else row(" || ",head)
    val body=l.vcat(old.rows.map{r->row(if(noHeader)" | " else " |  ",r.map{blocks(it,e)})})
    val all=l.above(l.above(headDoc,body),if(caption.isEmpty())l.empty else t("|+ ")+cap+t(" +|"))
    return l.above(if(e.top)l.nest(1,all)else all,l.blankline)
   }
   val bodies=if(b.head.rows.isEmpty())b.bodies else listOf(TableBody(Attr(),0,emptyList(),b.head.rows))+b.bodies
   val head=b.head.copy(rows=emptyList());val ann=AnnotatedTable.toTable(Attr(),Tables.emptyCaption,b.columns,head,bodies,b.foot)
   val cells=ann.head.rows.flatMap{it.cells}+ann.foot.rows.flatMap{it.cells}+ann.bodies.flatMap{body->body.head.flatMap{it.cells}+body.body.flatMap{it.head+it.body}}
   val docs=cells.map{blocks(it.cell.blocks,e)}.iterator()
   return GridTable.gridTable(e.options,{_,_->docs.next()},b.columns,head,bodies,b.foot)
  }
  fun escape(s:String)="<verbatim>"+s.replace("</verbatim>","<</verbatim><verbatim>/verbatim>")+"</verbatim>"
  fun shouldEscape(s:String,e:Env)=s.isEmpty()||s.any{it in "#*<=|"}||listOf("::","~~","[[",">>>").any{s.contains(it)}||(e.link&&']' in s)||containsNotes(s,'[',']')||containsNotes(s,'{','}')
  fun conditional(s:String,e:Env)=if(shouldEscape(s,e))escape(s)else s
  fun containsNotes(s:String,left:Char,right:Char):Boolean {for(i in s.indices)if(s[i]==left){var j=i+1;if(j<s.length&&s[j] in '1'..'9'){while(j<s.length&&s[j] in '0'..'9')j++;if(j<s.length&&s[j]==right)return true}};return false}
  fun spaceFirst(s:String)=s.isNotEmpty()&&ParserCharacters.isSpace(s.codePointAt(0))
  fun spaceLast(s:String)=s.isNotEmpty()&&ParserCharacters.isSpace(s.codePointBefore(s.length))
  fun startsSpace(xs:List<Inline>)=when(val x=xs.firstOrNull()){Inline.Space,Inline.SoftBreak->true;is Inline.Str->spaceFirst(x.text);else->false}
  fun endsSpace(xs:List<Inline>)=when(val x=xs.lastOrNull()){Inline.Space,Inline.SoftBreak->true;is Inline.Str->spaceLast(x.text);else->false}
  fun marker(s:String,f:(Int)->Boolean):Boolean {val p=s.codePoints().toArray();var i=0;while(i<p.size&&(p[i]==32||f(p[i])))i++;return i<p.size&&p[i]==46&&(i+1==p.size||ParserCharacters.isSpace(p[i+1]))}
  fun fixOrEscape(sp:Boolean,i:Inline):Boolean=when(i){Inline.Space,Inline.SoftBreak->true;is Inline.Str->{val s=i.text;when(s.firstOrNull()){'-'->if(s.length==1)sp else sp&&ParserCharacters.isSpace(s.codePointAt(1))||s.length>=4&&s.all{it=='-'};';'->if(s.length==1)!sp else !sp&&ParserCharacters.isSpace(s.codePointAt(1));'>'->s.length==1||ParserCharacters.isSpace(s.codePointAt(1));else->sp&&(marker(s,ParserCharacters::isDigit)||marker(s){it in 97..122}||marker(s){it in 65..90})||spaceFirst(s)}};else->false}
  fun replace(i:Inline)=when(i){is Inline.SmallCaps->Inline.Emph(i.content);is Inline.Code->i.copy(attr=i.attr.copy(keyValues=emptyList()));else->i}
  fun normalize(xs:List<Inline>):List<Inline> {
   val out=mutableListOf<Inline>()
   for(x in xs){if(x is Inline.Str&&x.text.isEmpty())continue;val a=out.lastOrNull();val merged:Inline?=when {
    a is Inline.Str&&x is Inline.Str->Inline.Str(a.text+x.text)
    a is Inline.Emph&&x is Inline.Emph->Inline.Emph(a.content+x.content)
    a is Inline.Strong&&x is Inline.Strong->Inline.Strong(a.content+x.content)
    a is Inline.Strikeout&&x is Inline.Strikeout->Inline.Strikeout(a.content+x.content)
    a is Inline.Superscript&&x is Inline.Superscript->Inline.Superscript(a.content+x.content)
    a is Inline.Subscript&&x is Inline.Subscript->Inline.Subscript(a.content+x.content)
    a is Inline.SmallCaps&&x is Inline.SmallCaps->Inline.SmallCaps(a.content+x.content)
    a is Inline.Code&&x is Inline.Code->Inline.Code(Attr(),a.text+x.text)
    a is Inline.RawInline&&x is Inline.RawInline&&a.format==x.format->a.copy(text=a.text+x.text)
    else->null
   };if(merged==null)out.add(x)else out[out.lastIndex]=merged};return out
  }
  fun preprocess(xs:List<Inline>):List<Inline> {val out=mutableListOf<Inline>();for(raw in xs){val x=replace(raw);when(x){is Inline.Math->out.addAll(PandocMath.texMathToInlines(runtime,x.type,x.text));is Inline.Cite->out.addAll(x.content);else->out.add(x)}};return normalize(out.mapIndexed{i,x->if((x==Inline.Space||x==Inline.SoftBreak)&&out.getOrNull(i+1)is Inline.Note)Inline.Str(" ")else x})}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderInlines(input:List<Inline>,env:Env,prime:Boolean):Doc<String> {
   var e=if(prime)env.copy(start=true,afterSpace=env.afterSpace||!env.top)else env
   val xs=preprocess(input);tags=false
   if(e.start&&xs.isEmpty())return t("<verbatim></verbatim>")
   e=e.copy(near=e.asterisks);val ds=mutableListOf<Doc<String>>()
   for((index,x)in xs.withIndex()){
    val next=xs.getOrNull(index+1);tags=tags||(next is Inline.Str&&(shouldEscape(next.text,e)||ParserCharacters.isAlphaNum(next.text.codePointAt(0))))
    val d=inline(x,e.copy(start=false,asterisks=false,near=e.near||(index==xs.lastIndex&&e.asterisks)))
    if(e.start&&fixOrEscape(e.afterSpace,x))ds.add(t("<verbatim></verbatim>"));ds.add(d)
    val nl=x==Inline.LineBreak||(x==Inline.SoftBreak&&e.options.wrapText==WrapOption.WrapPreserve)
    e=e.copy(start=nl,afterSpace=x==Inline.Space||!e.top&&nl,near=false)
   };return l.hcat(ds)
  }
  suspend fun DeepRecursiveScope<Work,Doc<String>>.emphasis(begin:String,end:String,xs:List<Inline>,e:Env):Doc<String> {val d=inlines(xs,e.copy(asterisks=begin.last()=='*'||end.first()=='*'));tags=end.last()!='>';return t(begin)+d+t(end)}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderInline(i:Inline,e:Env):Doc<String> = when(i){
   is Inline.Str->{val s=conditional(i.text.replace('\n',' '),e);tags=ParserCharacters.isAlphaNum(s.codePointBefore(s.length));t(s)}
   is Inline.Emph->{val nested=i.content.singleOrNull() as? Inline.Strong;val xs=normalize(nested?.content?:i.content);val bad=xs.isEmpty()||startsSpace(xs)||endsSpace(xs);if(nested!=null){if(tags)emphasis("<em>**","**</em>",xs,e)else if(bad)emphasis("*<strong>","</strong>*",xs,e)else emphasis("***","***",xs,e)}else if(tags||bad)emphasis("<em>","</em>",xs,e)else emphasis("*","*",xs,e)}
   is Inline.Strong->{val nested=i.content.singleOrNull() as? Inline.Emph;val xs=normalize(nested?.content?:i.content);val bad=xs.isEmpty()||startsSpace(xs)||endsSpace(xs);if(nested!=null){if(tags)emphasis("<strong>*","*</strong>",xs,e)else if(bad)emphasis("**<em>","</em>**",xs,e)else emphasis("***","***",xs,e)}else if(tags||bad)emphasis("<strong>","</strong>",xs,e)else emphasis("**","**",xs,e)}
   is Inline.Underline->{val d=inlines(i.content,e);if(e.options.isEnabled(ExtensionName.Amuse))t("_")+d+t("_")else inline(Inline.Emph(i.content),e)}
   is Inline.Strikeout->{val d=inlines(i.content,e);tags=false;t("<del>")+d+t("</del>")}
   is Inline.Superscript->{val d=inlines(i.content,e);tags=false;t("<sup>")+d+t("</sup>")}
   is Inline.Subscript->{val d=inlines(i.content,e);tags=false;t("<sub>")+d+t("</sub>")}
   is Inline.SmallCaps->throw io.heapy.kotdoc.errors.PandocError.PandocShouldNeverHappenError("SmallCaps should be expanded before normalization")
   is Inline.Quoted->{val d=inlines(i.content,e);tags=false;if(i.type==QuoteType.SingleQuote)t("‘")+d+t("’")else t("“")+d+t("”")}
   is Inline.Cite->throw io.heapy.kotdoc.errors.PandocError.PandocShouldNeverHappenError("Citations should be expanded before normalization")
   is Inline.Math->throw io.heapy.kotdoc.errors.PandocError.PandocShouldNeverHappenError("Math should be expanded before normalization")
   is Inline.Code->{val use=tags;tags=false;val s=i.text;if(use||s.isEmpty()||'=' in s||spaceFirst(s)||spaceLast(s))t("<code>"+s.replace("</code>","<</code><code>/code>")+"</code>")else t("=$s=")}
   is Inline.RawInline->{tags=false;t("<literal style=\"${i.format.value}\">${i.text}</literal>")}
   Inline.Space->{tags=false;l.space}
   Inline.SoftBreak->{tags=false;if(!e.oneLine&&e.options.wrapText==WrapOption.WrapPreserve)l.cr else l.space}
   Inline.LineBreak->{tags=false;t("<br>")+(if(e.oneLine)l.empty else l.cr)}
   is Inline.Link->{val single=(i.content.singleOrNull() as? Inline.Str)?.text;if(single!=null&&PandocURI.escapeURI(single)==i.target.url){tags=false;t("[[${escapeLink(single)}]]")}else{val d=inlines(i.content,e.copy(link=true));tags=false;t("[[${escapeLink(i.target.url)}][")+d+t("]]")}}
   is Inline.Image->{val alt=inlines(i.content,e.copy(link=true));val title=stripFig(i.target.title);val titleDoc=if(title.isEmpty()){if(i.content.isEmpty())l.empty else t("[")+alt+t("]")}else t("["+conditional(title,e.copy(link=true))+"]");val dim=Dimensions.dimension(Direction.Width,i.attr);val width=if(dim is Dimension.Percent&&e.options.isEnabled(ExtensionName.Amuse))" "+round(dim.value)else "";tags=false;t("[["+i.target.url.replace("]","%5D")+width+(if("align-left" in i.attr.classes)" l"else "")+(if("align-right" in i.attr.classes)" r"else "")+"]")+titleDoc+t("]")}
   is Inline.Note->{val n=noteNum+notes.size;notes.add(i.blocks);tags=false;t("[$n]")}
   is Inline.Span->{val d=inlines(i.content,e);val dir=i.attr.keyValues.firstOrNull{it.first=="dir"}?.second;val content=when(dir){"rtl"->t("<<<")+d+t(">>>");"ltr"->t(">>>")+d+t("<<<");else->d};val anchor=if(i.attr.identifier.isEmpty())l.empty else t("#${i.attr.identifier}")+l.space;tags=false;anchor+if(i.content.isEmpty()&&i.attr.identifier.isNotEmpty())l.empty else i.attr.classes.firstOrNull()?.let{t("<class name=\"$it\">")+content+t("</class>")}?:if(dir=="rtl"||dir=="ltr")content else t("<class>")+content+t("</class>")}
  }
  fun escapeLink(s:String):String {val ext=s.substringAfterLast('/').let{it.substringAfterLast('.',"")};return (if(ext in listOf("eps","gif","jpg","jpeg","pbm","png","tiff","xbm","xpm"))"URL:"else "")+s.replace("]","%5D")}
  fun stripFig(value:String):String {var s=value;while(s.startsWith("fig:"))s=s.removePrefix("fig:");return s}
  fun round(x:Double)=HaskellNumber.round(x).toString()
 }
}
