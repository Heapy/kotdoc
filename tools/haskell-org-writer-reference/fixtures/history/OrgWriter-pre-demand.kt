// Text.Pandoc.Writers.Org, Pandoc 3.12.1.
// Copyright (C) 2010-2015 Puneeth Chaganti, 2010-2025 John MacFarlane,
// 2016-2025 Albert Krewinkel. GNU GPL version 2 or later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.uri.PandocURI
object OrgWriter {
 fun write(options:WriterOptions,document:Pandoc,report:(LogMessage)->Unit={}):String=Session(options,report).document(document)
 private sealed interface Work {
  data class Blocks(val xs:List<Block>):Work
  data class B(val value:Block):Work
  data class Inlines(val xs:List<Inline>):Work
  data class I(val value:Inline):Work
 }
 private sealed interface CaptionWork {data class Blocks(val values:List<Block>):CaptionWork;data class BlockValue(val value:Block):CaptionWork}
 private class Session(val options:WriterOptions,val report:(LogMessage)->Unit){
  val l=DocLayout.Text;val notes=mutableListOf<List<Block>>();var noteNum=0L;var hasMath=false
  fun t(s:String)=l.literal(s)
  fun a(vararg ds:Doc<String>)=l.vcat(ds.toList())
  val renderer=DeepRecursiveFunction<Work,Doc<String>>{w->when(w){is Work.Blocks->l.vcat(w.xs.map{callRecursive(Work.B(it))});is Work.B->renderBlock(w.value);is Work.Inlines->renderInlines(w.xs);is Work.I->renderInline(w.value)}}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.blocks(xs:List<Block>)=callRecursive(Work.Blocks(xs))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.block(b:Block)=callRecursive(Work.B(b))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inlines(xs:List<Inline>)=callRecursive(Work.Inlines(xs))
  suspend fun DeepRecursiveScope<Work,Doc<String>>.inline(i:Inline)=callRecursive(Work.I(i))
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
  fun document(d:Pandoc):String{
   var ctx=WriterMetadata.metaToContext(options,TemplateValues.Text,{renderer(Work.Blocks(it))},{l.chomp(renderer(Work.Inlines(it)))},d.meta)
   val body=renderer(Work.Blocks(d.blocks));val renderedNotes=mutableListOf<Doc<String>>();var done=0
   while(done<notes.size){val batch=notes.drop(done);for((i,n)in batch.withIndex()){val marker="[fn:${done+i+1}] ";renderedNotes.add(l.hang(marker.length.toLong(),t(marker),renderer(Work.Blocks(n))))};done+=batch.size}
   val main=l.separated(body,l.vsep(renderedNotes))
   val fields=mapOf("body" to Val.SimpleVal(main),"math" to Val.BoolVal(hasMath),"option-special-strings" to Val.BoolVal(enabled(ExtensionName.SpecialStrings)),"options" to Val.MapVal(Context(buildMap{if(enabled(ExtensionName.SmartQuotes))put("'",Val.SimpleVal(t("t")));if(!enabled(ExtensionName.SpecialStrings))put("-",Val.SimpleVal(t("nil")))})))
   ctx=Context(ctx.entries+fields.filterKeys{it !in ctx.entries})
   return l.render(if(options.wrapText==WrapOption.WrapAuto)options.columns else null,options.template?.let{TemplateRenderer.Text.render(it,ctx)}?:main)
  }
  fun enabled(e:ExtensionName)=options.isEnabled(e)
  fun raw(f:Format)=OrgCaseFold.fold(f.value) in listOf("latex","tex","org")
  fun escape(s:String):Doc<String>{val out=mutableListOf<Doc<String>>();var start=0;for(i in s.indices)if(s[i]in "*#|"){if(i>start)out.add(t(s.substring(start,i)));out.add(l.afterBreak("\u200b"));out.add(t(s[i].toString()));start=i+1};if(start<s.length)out.add(t(s.substring(start)));return l.hcat(out)}
  fun special(s:String)=buildString{for(c in s)append(when(c){'\u00ad'->"\\-";'\u2013'->"--";'\u2014'->"---";'\u2019'->"'";'\u2026'->"...";else->c.toString()})}
  fun tight(items:List<List<Block>>):Boolean {val work=java.util.ArrayDeque<List<Block>>();items.forEach(work::addLast);while(work.isNotEmpty()){val xs=work.removeLast();when{xs.isEmpty()||xs.first()is Block.Plain->Unit;xs.size==1&&xs[0]is Block.BulletList->(xs[0]as Block.BulletList).items.forEach(work::addLast);xs.size==1&&xs[0]is Block.OrderedList->(xs[0]as Block.OrderedList).items.forEach(work::addLast);else->return false}};return true}
  fun task(xs:List<Block>):List<Block>{if(!enabled(ExtensionName.TaskLists))return xs;fun convert(is_:List<Inline>):List<Inline>{val first=is_.firstOrNull();return if(first is Inline.Str&&first.text in listOf("☐","☒")&&is_.getOrNull(1)==Inline.Space)listOf(Inline.Str(if(first.text=="☐")"[ ]"else"[X]"))+is_.drop(1)else is_};val first=xs.firstOrNull();return when(first){is Block.Plain->listOf(first.copy(content=convert(first.content)))+xs.drop(1);is Block.Para->listOf(first.copy(content=convert(first.content)))+xs.drop(1);else->xs}}
  fun properties(attr:Attr):Doc<String>{val kv=(if(attr.identifier.isEmpty())emptyList()else listOf("CUSTOM_ID" to attr.identifier))+(if(attr.classes.isEmpty())emptyList()else listOf("CLASS" to attr.classes.joinToString(" ")))+attr.keyValues;return t(":PROPERTIES:")+l.cr+l.vcat(kv.map{t(":"+it.first+": "+it.second)+l.cr})+l.cr+t(":END:")}
  fun attrHtml(attr:Attr):Doc<String>{val kv=(if(attr.classes.isEmpty())emptyList()else listOf("class" to attr.classes.joinToString(" ")))+attr.keyValues;return(if(attr.identifier.isEmpty())l.empty else t("#+name: "+attr.identifier)+l.cr)+(if(kv.isEmpty())l.empty else t("#+attr_html: "+kv.joinToString(" "){":"+it.first+" "+it.second})+l.cr)}
  fun admonition(s:String)=s in listOf("warning","important","tip","note","caution")
  fun greater(s:String)=LaTeXReaderText.toLower(s).let{it=="center"||it=="quote"||admonition(it)}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.div(attr:Attr,bs:List<Block>):Doc<String>{
   val drawers=attr.classes.filter{it=="drawer"};val other=attr.classes.filterNot{it=="drawer"}
   if(drawers.size==1&&other.isNotEmpty()){val contents=blocks(bs);val keys=l.vcat(attr.keyValues.map{t(":"+it.first+": "+it.second)});return a(t(":"+other.first()+":"),l.cr,keys,l.blankline,contents,l.blankline,t(":END:"),l.blankline)}
   val names=attr.classes.filter(::greater);if(names.isNotEmpty()){val name=names.first();val rest=attr.classes.filterNot(::greater)+names.drop(1);val head=bs.firstOrNull();val actual=if(admonition(name)&&head is Block.Div&&head.attr==Attr(classes=listOf("title")))bs.drop(1)else bs;val contents=blocks(actual);return a(l.blankline,attrHtml(attr.copy(classes=rest)),t("#+begin_"+name),l.chomp(contents),t("#+end_"+name),l.blankline)}
   val contents=blocks(bs);return a(l.blankline,if(attr.identifier.isEmpty())contents else a(t("<<"+attr.identifier+">>"),contents),l.blankline)
  }
  fun tags(xs:List<Inline>):List<String> = xs.asSequence().flatMap{Walk.inlineInInline.queryValues(it)}.filterIsInstance<Inline.Span>().mapNotNull{it.attr.keyValues.firstOrNull{it.first=="tag-name"}?.second}.toList()
  suspend fun DeepRecursiveScope<Work,Doc<String>>.item(xs:List<Block>,marker:String,counter:Long?=null):Doc<String>{val contents=blocks(task(xs));val first=xs.firstOrNull();val fixed=(if(first is Block.Plain||first is Block.Para)l.empty else l.cr)+l.chomp(contents);val cookie=counter?.let{l.space+t("[@$it]")}?:l.empty;return a(l.hang(marker.codePointCount(0,marker.length).toLong()+1,if(marker=="-")t("- ")else t(marker)+cookie+l.space,fixed),if(xs.isEmpty()||WriterShared.endsWithPlain(xs))l.cr else l.blankline)}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderBlock(b:Block):Doc<String> = when(b){
   is Block.Plain->inlines(b.content)
   is Block.Para->inlines(b.content)+l.blankline
   is Block.Div->when{
    b.attr.classes==listOf("cell","code")&&b.blocks.firstOrNull()is Block.CodeBlock->{val code=b.blocks.first()as Block.CodeBlock;blocks(listOf(code.copy(attr=code.attr.copy(classes=code.attr.classes+"code")))+b.blocks.drop(1))}
    b.attr.classes==listOf("output","execute_result")&&b.blocks.size==1&&b.blocks[0]is Block.CodeBlock->a(t("#+RESULTS:"),l.prefixed(": ",l.vcat((b.blocks[0]as Block.CodeBlock).text.split('\n').map(::t))))
    b.attr.identifier=="refs"&&enabled(ExtensionName.Citations)->l.empty
    else->div(b.attr,b.blocks)
   }
   is Block.LineBlock->{val stanzas=mutableListOf<List<List<Inline>>>();var at=0;while(at<b.lines.size){val start=at;while(at<b.lines.size&&b.lines[at].isNotEmpty())at++;stanzas.add(b.lines.subList(start,at));if(at<b.lines.size)at++};val ds=stanzas.map{lines->l.nowrap(l.hcat(lines.flatMapIndexed{i,x->(if(i==0)emptyList()else listOf(l.cr))+inlines(x)}))};a(l.blankline,t("#+begin_verse"),l.nest(2,l.hcat(ds.flatMapIndexed{i,d->(if(i==0)emptyList()else listOf(l.blankline))+d})),t("#+end_verse"))+l.blankline}
   is Block.RawBlock->if(OrgCaseFold.fold(b.format.value)=="html")a(l.blankline,t("#+begin_export html"),l.nest(2,t(b.text)),t("#+end_export"),l.blankline)else if(raw(b.format))t(b.text)else{report(LogMessage.BlockNotRendered(b));l.empty}
   Block.HorizontalRule->a(l.blankline,t("--------------"),l.blankline)
   is Block.Header->{val split=b.content.indexOfFirst{it is Inline.Span&&it.attr.keyValues.any{it.first=="tag-name"}}.let{if(it<0)b.content.size else it};val contents=inlines(b.content.take(split).dropLastWhile{it==Inline.Space});val h=t(if(b.level>999)" "else"*".repeat(if(b.level<=0)0 else b.level.toInt()))+t(" ")+contents;val names=tags(b.content.drop(split));val tag=if(names.isEmpty())""else":"+names.joinToString(":")+":";val padding=if(tag.isEmpty())l.empty else t(" ".repeat(maxOf(1L,options.columns-l.offset(h)-DisplayWidth.realLength(tag)).toInt())+tag);l.nowrap(h+padding)+(if(b.attr==Attr())l.empty else l.cr+properties(b.attr))+l.cr}
   is Block.CodeBlock->{val attr=b.attr;val start=attr.keyValues.firstOrNull{it.first=="startFrom"}?.second?.let{" "+it.trimEnd{ParserCharacters.isSpace(it.code)}}?:"";val numbers=if("numberLines" in attr.classes)(if("continuedSourceBlock" in attr.classes)" +n"else" -n")+start else"";val lang=attr.classes.firstOrNull{it !in listOf("example","code")};val args=attr.keyValues.filter{it.first !in listOf("startFrom","org-language")}.joinToString(""){" :"+it.first+" "+it.second};val begin=if(lang==null)"#+begin_example"+numbers else "#+begin_src "+(if("code" in attr.classes)"jupyter-"else"")+language(lang)+numbers+args;val end=if(lang==null)"#+end_example"else"#+end_src";val lines=b.text.split('\n').let{if(it.lastOrNull()=="")it.dropLast(1)else it}.let{if(b.text.isEmpty())emptyList()else it};val escaped=lines.joinToString(""){line->val n=line.indexOfFirst{it!=' '&&it!='\t'}.let{if(it<0)line.length else it};val code=line.substring(n);val rest=code.dropWhile{it==','};line.take(n)+(if(rest.startsWith("#+")||rest.startsWith("*"))","else"")+code+"\n"};a(if(attr.identifier.isEmpty())l.empty else t("#+name: "+attr.identifier),t(begin),t(escaped),t(end),l.blankline)}
   is Block.BlockQuote->{val contents=blocks(b.blocks);a(l.blankline,t("#+begin_quote"),l.chomp(contents),t("#+end_quote"),l.blankline)}
   is Block.BulletList->{val contents=b.items.map{item(it,"-")};a(if(tight(b.items))l.vcat(contents)else l.vsep(contents),l.blankline)}
   is Block.OrderedList->{val delim=if(b.attributes.delimiter==ListNumberDelim.TwoParens)ListNumberDelim.OneParen else b.attributes.delimiter;val markers=DocumentShared.orderedListMarkers(b.attributes.copy(style=ListNumberStyle.Decimal,delimiter=delim)).take(b.items.size).toList();val contents=b.items.zip(markers).mapIndexed{i,(x,marker)->item(x,marker,if(i==0&&b.attributes.start!=1L)b.attributes.start else null)};a(if(tight(b.items))l.vcat(contents)else l.vsep(contents),l.blankline)}
   is Block.DefinitionList->{val ds=b.items.map{(term,defs)->val label=inlines(term);val contents=l.vcat(defs.map{blocks(it)});a(l.hang(2,t("- "),label+t(" :: ")+contents),if(tight(defs))l.cr else l.blankline)};a(l.vcat(ds),l.blankline)}
   is Block.Figure->{val cap=if(b.caption.long.isEmpty())l.empty else t("#+caption: ")+inlines(captionInlines(b.caption.long));val content=blocks(b.blocks);a(cap,if(b.attr.identifier.isEmpty())l.empty else t("<<"+b.attr.identifier+">>"),content,l.blankline)}
   is Block.Table->{val old=DocumentShared.toLegacyTable(b.copy(caption=Caption(null,emptyList())));val cap=captionInlines(b.caption.long);val caption=inlines(cap);val headers=old.headers.map{blocks(it)};val rows=old.rows.map{row->row.map{blocks(it)}};val widths=List((listOf(headers.size)+rows.map{it.size}).maxOrNull()?:0){i->(listOf(headers)+rows).mapNotNull{it.getOrNull(i)}.maxOfOrNull{l.offset(it)}?:0L};fun row(ds:List<Doc<String>>):Doc<String> = l.vfill("| ")+l.hcat(ds.zip(widths).flatMapIndexed{i,(d,w)->(if(i==0)emptyList()else listOf(l.vfill(" | ")))+l.lblock(w,d)})+l.vfill(" |");
val border=t("|-"+widths.joinToString("-+-"){"-".repeat(it.toInt())}+"-|");a(if(old.headers.all{it.isEmpty()})l.empty else a(row(headers),border),l.vcat(rows.map(::row)),if(cap.isEmpty())l.empty else t("#+caption: ")+caption,l.blankline)}
  }
  fun language(s:String)=when(s){"c"->"C";"commonlisp"->"lisp";"r"->"R";"bash"->"sh";else->s}
  fun fix(xs:List<Inline>):List<Inline>{val out=mutableListOf<Inline>();var at=0;fun should(i:Inline):Boolean=when(i){is Inline.Note->true;is Inline.Str->i.text=="-"||i.text.length>1&&i.text.last()in ".)"&&i.text.dropLast(1).codePoints().allMatch(ParserCharacters::isDigit);else->false}
while(at<xs.size){val i=xs[at++];if((i==Inline.Space||i==Inline.SoftBreak)&&at<xs.size&&should(xs[at])){out.add(Inline.Str(" "));out.add(xs[at++])}else out.add(i)};return out}
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderInlines(xs:List<Inline>)=l.hcat(fix(xs).map{inline(it)})
  suspend fun DeepRecursiveScope<Work,Doc<String>>.renderInline(i:Inline):Doc<String> = when(i){
   is Inline.Str->escape(if(enabled(ExtensionName.Smart)||enabled(ExtensionName.SpecialStrings))special(i.text)else i.text)
   Inline.Space->l.space
   Inline.SoftBreak->if(options.wrapText==WrapOption.WrapPreserve)l.cr else l.space
   Inline.LineBreak->t("\\\\")+l.cr
   is Inline.Emph->t("/")+inlines(i.content)+t("/")
   is Inline.Underline->t("_")+inlines(i.content)+t("_")
   is Inline.Strong->t("*")+inlines(i.content)+t("*")
   is Inline.Strikeout->t("+")+inlines(i.content)+t("+")
   is Inline.Superscript->t("^{")+inlines(i.content)+t("}")
   is Inline.Subscript->t("_{")+inlines(i.content)+t("}")
   is Inline.SmallCaps->inlines(i.content)
   is Inline.Span->if(i.attr.identifier.isNotEmpty()&&i.attr.classes.isEmpty()&&i.attr.keyValues.isEmpty()&&i.content.isEmpty())t("<<"+i.attr.identifier+">>")else inlines(i.content)
   is Inline.Quoted->{val smart=enabled(ExtensionName.Smart)||enabled(ExtensionName.SmartQuotes);val single=i.type==QuoteType.SingleQuote;val open=if(smart)if(single)"'"else"\""else if(single)"‘"else"“";val close=if(smart)open else if(single)"’"else"”";t(open)+inlines(i.content)+t(close)}
   is Inline.Code->if('=' in i.text&&'~' !in i.text)t("~"+i.text+"~")else t("="+i.text+"=")
   is Inline.Math->{hasMath=true;if(i.type==MathType.InlineMath)t("\\("+i.text+"\\)")else t("\\["+i.text+"\\]")}
   is Inline.RawInline->if(OrgCaseFold.fold(i.format.value) in listOf("tex","latex")&&i.text.startsWith("\\begin"))l.cr+t(i.text)+l.cr else if(raw(i.format))t(i.text)else{report(LogMessage.InlineNotRendered(i));l.empty}
   is Inline.Link->{val auto=(i.content.singleOrNull()as? Inline.Str)?.text;if(auto!=null&&PandocURI.escapeURI(auto)==i.target.url)t("[["+escapeTarget(path(auto))+"]]")else{val descr=l.render(null,l.nowrap(inlines(i.content)));t("[["+escapeTarget(path(i.target.url))+"]["+escapeDescription(descr)+"]]")}}
   is Inline.Image->t("[["+escapeTarget(path(i.target.url))+"]]")
   is Inline.Note->{notes.add(i.blocks);noteNum++;t("[fn:$noteNum]")}
   is Inline.Cite->if(!enabled(ExtensionName.Citations))inlines(i.content)else{val items=i.citations.map{c->val pref=inlines(c.citationPrefix);val(info,suffix)=OrgCitationLocator.parse(c.citationSuffix);val suff=inlines(suffix);val loc=info?.raw?.replace("}","")?.replace("{","")?.replace('\u00a0',' ')?:"";l.hsep(listOf(pref,t("@"+c.citationId),t(loc),suff))};val style=if(i.citations.firstOrNull()?.citationMode==CitationMode.AuthorInText)"/t"else if(i.citations.size==1&&i.citations[0].citationMode==CitationMode.SuppressAuthor)"/na"else"";t("[cite$style:")+l.hcat(items.flatMapIndexed{n,d->(if(n==0)emptyList()else listOf(t("; ")))+d})+t("]")}
  }
  fun path(s:String):String{val colon=s.indexOf(':');val url=colon>=0&&s.take(colon).codePoints().allMatch{ParserCharacters.isAlphaNum(it)||it==46||it==45};return if(s.isEmpty()||s.startsWith('#')||url||listOf("/","./","../","file:").any(s::startsWith))s else"file:$s"}
  fun escapeTarget(s:String):String=buildString{var at=0;while(at<s.length){val c=s[at++];if(c=='\\'){val start=at-1;while(at<s.length&&s[at]=='\\')at++;val run=s.substring(start,at);if(at==s.length)append(run+run)else{val next=s[at++];append(run);if(next=='['||next==']')append(run+"\\");append(next)}}else{if(c=='['||c==']')append('\\');append(c)}}}
  fun escapeDescription(s:String):String{var value=s;while("]]" in value)value=value.replace("]]","]\u200b]");return if(value.endsWith(']'))value.dropLast(1)+"\u200b]"else value}
 }
}
