package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.readLines
internal fun builderDemandExecute(probe:String):JsonElement=try {
 fun <A> bad(name:String):A=error(name)
 val parts=probe.split('/');val fault=if(parts.size==3)parts[1]else""
 fun <A> arg(name:String,value:()->A):A=if(name==fault)bad(name)else value()
 fun ils()=arg("inlines"){PandocBuilders.fromInlines(Inlines(listOf(Inline.Str("a"))))}
 fun bs()=arg("blocks"){PandocBuilders.fromBlocks(Blocks(listOf(Block.Plain(emptyList()))))}
 fun txt()=arg("text"){"a"}
 fun fmt()=arg("format"){"a"}
 fun url()=arg("url"){"a"}
 fun title()=arg("title"){"a"}
 fun attr()=PandocBuilders.attr{arg("attr"){Attr()}}
 fun listAttr()=PandocBuilders.listAttributes{arg("attributes"){ListAttributes()}}
 fun items()=arg("items"){listOf(bs())}
 fun lines()=arg("lines"){listOf(ils())}
 fun caption()=arg("caption"){PandocBuilders.emptyCaption()}
 fun defs()=arg("definitions"){listOf(GenericTuple2(::ils){listOf(bs())})}
 fun root(value:GenericNode)="root"
 fun observe(input:BuilderInlines)=if(parts.last()=="length")input.source.length.toString()else root(input.source.toSourceList()[0])
 fun observeBlock(input:BuilderBlocks)=if(parts.last()=="length")input.source.length.toString()else root(input.source.toSourceList()[0])
 @Suppress("UNCHECKED_CAST") fun definition(node:GenericNode)=node.fields[0].value() as List<GenericTuple2<List<GenericNode>,List<List<GenericNode>>>>
 fun inlineValues(xs:List<GenericNode>)=BuilderStreams.map({xs}){GenericAstData.named<Inline>("Inline").fromCanonical(it())}
 fun blockValues(xs:List<GenericNode>)=BuilderStreams.map({xs}){GenericAstData.named<Block>("Block").fromCanonical(it())}
 val result=when(parts[0]){
 "trim-partial-node-length","trim-partial-node-prefix","trim-partial-node-two","trim-partial-node-demand"->{
  fun node(name:String,text:String="a")=BuilderStreams.nodes("Inline",name,if(name=="Str")listOf(BuilderStreams.text{text})else emptyList())
  fun pair(a:()->GenericNode,b:()->GenericNode)=ManyCell.Node<GenericNode>(2){listOf(ManyCell.Leaf(a),ManyCell.Leaf(b))}
  val a=pair({node("Str")},{node("Space")})
  val higher=ManyCell.Node<GenericNode>(4){GenericList.cons({a},{GenericList.cons({bad<ManyCell<GenericNode>>("node")},{GenericList.empty()})})}
  val middle=ManyTree.Deep({listOf(pair({node("Space")},{node("Space")}))},{ManyTree.Single(higher)},{listOf(pair({node("Str","x")},{node("Space")}))},8)
  val input=BuilderInlines(Many.fromTree{ManyTree.Deep({listOf(ManyCell.Leaf{node("Space")})},{middle},{listOf(ManyCell.Leaf{node("Str","z")})},10)})
  val result=PandocBuilders.trimInlines{input}
  if(parts[0].endsWith("length"))result.source.length.toString()else {val count=if(parts[0].endsWith("two"))2 else if(parts[0].endsWith("demand"))3 else 1;val xs=result.source.toSourceList();AstShow.show(List(count){GenericAstData.named<Inline>("Inline").fromCanonical(xs[it])})}
 }
 "trim-partial-single-length","trim-partial-single-prefix","trim-partial-single-two","trim-partial-single-demand"->{
  fun node(name:String,text:String="a")=BuilderStreams.nodes("Inline",name,if(name=="Str")listOf(BuilderStreams.text{text})else emptyList())
  val n=ManyCell.Node<GenericNode>(2){listOf(ManyCell.Leaf{node("Str")},ManyCell.Leaf{node("Space")})}
  val middle=ManyTree.Deep({listOf(n)},{ManyTree.Empty<GenericNode>()},{GenericList.cons({bad<ManyCell<GenericNode>>("single")},{GenericList.empty()})},4)
  val input=BuilderInlines(Many.fromTree{ManyTree.Deep({listOf(ManyCell.Leaf{node("Space")})},{middle},{listOf(ManyCell.Leaf{node("Str","z")})},6)})
  val result=PandocBuilders.trimInlines{input}
  if(parts[0].endsWith("length"))result.source.length.toString()else {val count=if(parts[0].endsWith("two"))2 else if(parts[0].endsWith("demand"))3 else 1;val xs=result.source.toSourceList();AstShow.show(List(count){GenericAstData.named<Inline>("Inline").fromCanonical(xs[it])})}
 }
 "single-root"->"root".also{val cell=ManyThunk<ManyCell<GenericNode>>{bad("single")};ManyTree.Single(cell::get){cell.get().size}}
 "single-size"->{val cell=ManyThunk<ManyCell<GenericNode>>{bad("single")};ManyTree.Single(cell::get){cell.get().size}.size.toString()}
 "single-leaf-size"->{val cell=ManyCell.Leaf<GenericNode>{bad("elem")};ManyTree.Single({cell}){cell.size}.size.toString()}
 "trim-shared-large","trim-shared-max"->{val count=if(parts[0].endsWith("max"))Long.MAX_VALUE else 1L shl 40;val input=BuilderInlines(Many.fromTree{ManyRepetition.replicate(count,ManyCell.Leaf{BuilderStreams.nodes("Inline","Str",listOf(BuilderStreams.text{"a"}))})});PandocBuilders.trimInlines{input}.source.length.toString()}
 "trim-deep-spaces"->{val space=BuilderStreams.nodes("Inline","Space",emptyList());val soft=BuilderStreams.nodes("Inline","SoftBreak",emptyList());val str=BuilderStreams.nodes("Inline","Str",listOf(BuilderStreams.text{"a"}));val input=BuilderInlines(Many.fromList(List(50000){space}+str+List(50000){soft}));PandocBuilders.trimInlines{input}.source.length.toString()}
 "trim-nested-constructor"->{var node=BuilderStreams.nodes("Inline","Str",listOf(BuilderStreams.text{"a"}));repeat(4096){val child=node;node=BuilderStreams.nodes("Inline","Emph",listOf(BuilderStreams.list("Inline"){listOf(child)}))};root(PandocBuilders.trimInlines{BuilderInlines(Many.singleton{node})}.source.toSourceList()[0])}
 "trim-partial-middle"->{fun leaf(text:String)=ManyCell.Leaf{BuilderStreams.nodes("Inline","Str",listOf(BuilderStreams.text{text}))}
 val input=BuilderInlines(Many.fromTree{ManyTree.Deep({listOf(leaf("a"))},{bad("middle")},{listOf(leaf("b"))},3)});PandocBuilders.trimInlines{input}.source.length.toString()}
 "text"->observe(PandocBuilders.text(::txt))
 "str"->observe(PandocBuilders.str(::txt))
 "emph"->observe(PandocBuilders.emph(::ils))
 "underline"->observe(PandocBuilders.underline(::ils))
 "strong"->observe(PandocBuilders.strong(::ils))
 "strikeout"->observe(PandocBuilders.strikeout(::ils))
 "superscript"->observe(PandocBuilders.superscript(::ils))
 "subscript"->observe(PandocBuilders.subscript(::ils))
 "smallcaps"->observe(PandocBuilders.smallcaps(::ils))
 "singleQuoted"->observe(PandocBuilders.singleQuoted(::ils))
 "doubleQuoted"->observe(PandocBuilders.doubleQuoted(::ils))
 "trimInlines"->observe(PandocBuilders.trimInlines(::ils))
 "cite"->observe(PandocBuilders.cite({arg("citations"){emptyList()}},::ils))
 "codeWith"->observe(PandocBuilders.codeWith(attr(),::txt))
 "code"->observe(PandocBuilders.code(::txt))
 "space"->observe(PandocBuilders.space)
 "softbreak"->observe(PandocBuilders.softbreak)
 "linebreak"->observe(PandocBuilders.linebreak)
 "math"->observe(PandocBuilders.math(::txt))
 "displayMath"->observe(PandocBuilders.displayMath(::txt))
 "rawInline"->observe(PandocBuilders.rawInline(::fmt,::txt))
 "linkWith"->observe(PandocBuilders.linkWith(attr(),::url,::title,::ils))
 "link"->observe(PandocBuilders.link(::url,::title,::ils))
 "imageWith"->observe(PandocBuilders.imageWith(attr(),::url,::title,::ils))
 "image"->observe(PandocBuilders.image(::url,::title,::ils))
 "note"->observe(PandocBuilders.note(::bs))
 "spanWith"->observe(PandocBuilders.spanWith(attr(),::ils))
 "para"->observeBlock(PandocBuilders.para(::ils))
 "plain"->observeBlock(PandocBuilders.plain(::ils))
 "lineBlock"->observeBlock(PandocBuilders.lineBlock(::lines))
 "codeBlockWith"->observeBlock(PandocBuilders.codeBlockWith(attr(),::txt))
 "codeBlock"->observeBlock(PandocBuilders.codeBlock(::txt))
 "rawBlock"->observeBlock(PandocBuilders.rawBlock(::fmt,::txt))
 "blockQuote"->observeBlock(PandocBuilders.blockQuote(::bs))
 "orderedListWith"->observeBlock(PandocBuilders.orderedListWith(listAttr(),::items))
 "orderedList"->observeBlock(PandocBuilders.orderedList(::items))
 "bulletList"->observeBlock(PandocBuilders.bulletList(::items))
 "definitionList"->observeBlock(PandocBuilders.definitionList(::defs))
 "headerWith"->observeBlock(PandocBuilders.headerWith(attr(),{arg("level"){1L}},::ils))
 "header"->observeBlock(PandocBuilders.header({arg("level"){1L}},::ils))
 "horizontalRule"->observeBlock(PandocBuilders.horizontalRule)
 "figureWith"->observeBlock(PandocBuilders.figureWith(attr(),::caption,::bs))
 "figure"->observeBlock(PandocBuilders.figure(::caption,::bs))
 "simpleFigureWith"->observeBlock(PandocBuilders.simpleFigureWith(attr(),::ils,::url,::title))
 "simpleFigure"->observeBlock(PandocBuilders.simpleFigure(::ils,::url,::title))
 "divWith"->observeBlock(PandocBuilders.divWith(attr(),::bs))
 "caption"->when(parts[1]){
  "short-root"->root(PandocBuilders.caption({bad("short")},::bs)())
  "blocks-root"->root(PandocBuilders.caption({GenericMaybe.None},{bad("blocks")})())
  "short-payload"->root(PandocBuilders.caption({GenericMaybe.Some{bad<List<GenericNode>>("payload")}},::bs)())
  else->root(PandocBuilders.caption({GenericMaybe.None},{BuilderBlocks(Many.singleton{bad("block")})})())
 }
 "simpleCaption"->root(PandocBuilders.simpleCaption{bad("blocks")}())
 "emptyCaption"->root(PandocBuilders.emptyCaption())
 "nullAttr"->"root".also{PandocBuilders.nullAttr().value()}
 "attr-fields-lazy"->{val data=GenericDatas.tuple3(GenericDatas.text,GenericDatas.list(GenericDatas.text),GenericDatas.list(GenericDatas.tuple2(GenericDatas.text,GenericDatas.text)));val value=GenericValue(data){GenericTuple3<String,List<String>,List<GenericTuple2<String,String>>>({bad("id")},{bad("classes")},{bad("properties")})};root(PandocBuilders.codeWith(value){"a"}.source.toSourceList()[0])}
 "link-target-fields-lazy"->root(PandocBuilders.link({bad("url")},{bad("title")},::ils).source.toSourceList()[0])
 "link-target-first","link-target-second"->{val first=parts[0].endsWith("first");val value=PandocBuilders.link({if(first)"a"else bad("url")},{if(first)bad("title")else"a"},::ils).source.toSourceList()[0];val target=value.fields[2].value() as GenericTuple2<*,*>;(if(first)target.first else target.second) as String}
 "definition-head-root"->root(PandocBuilders.definitionList{GenericList.cons({bad("pair")},{GenericList.empty()})}.source.toSourceList()[0])
 "definition-term"->{val node=PandocBuilders.definitionList{listOf(GenericTuple2(::ils){bad<List<BuilderBlocks>>("defs")})}.source.toSourceList()[0];AstShow.show(inlineValues(definition(node)[0].first))}
 "definition-defs"->{val node=PandocBuilders.definitionList{listOf(GenericTuple2<BuilderInlines,List<BuilderBlocks>>({bad("term")}){listOf(bs())})}.source.toSourceList()[0];AstShow.show(BuilderStreams.map({definition(node)[0].second}){blockValues(it())})}
 "definition-tail"->{val node=PandocBuilders.definitionList{GenericList.cons({GenericTuple2(::ils){emptyList<BuilderBlocks>()}},{bad("tail")})}.source.toSourceList()[0];val pair=definition(node)[0];AstShow.show(listOf(inlineValues(pair.first) to BuilderStreams.map({pair.second}){blockValues(it())}))}
 "lineBlock-unused-line"->root(PandocBuilders.lineBlock{GenericList.cons({bad("line")},{GenericList.empty()})}.source.toSourceList()[0])
 "bulletList-unused-item"->root(PandocBuilders.bulletList{GenericList.cons({bad("item")},{GenericList.empty()})}.source.toSourceList()[0])
 "note-unused-block"->root(PandocBuilders.note{BuilderBlocks(Many.singleton{bad("block")})}.source.toSourceList()[0])
 "emph-unused-inline"->root(PandocBuilders.emph{BuilderInlines(Many.singleton{bad("inline")})}.source.toSourceList()[0])
 "plain-unused-inline"->root(PandocBuilders.plain{BuilderInlines(Many.singleton{bad("inline")})}.source.toSourceList()[0])
 else->error("probe:$probe")
 };JsonPrimitive(result)
}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){val lines=Path.of("tools/haskell-types-builder-reference/demand-native.jsonl").readLines();for((name,value)in lines.chunked(2)){val probe=Json.parseToJsonElement(name).jsonPrimitive.content;val actual=builderDemandExecute(probe);check(actual==Json.parseToJsonElement(value)){"$probe: $actual != $value"}};println("${lines.size/2} original Builder demand responses PASS")}
