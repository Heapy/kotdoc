package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import io.heapy.kotdoc.layout.TextChars
import java.nio.file.Path
import kotlin.io.path.readLines
private val builderTrace=object:GenericMonoid<String>{override fun empty()="e";override fun append(left:()->String,right:()->String)="("+left()+">"+right()+")"}
internal fun builderExecute(r:JsonObject):JsonElement=try {
 fun raw(name:String)=r.getValue(name).jsonPrimitive.content
 fun text(name:String)=TextChars.fromPoints(r.getValue(name).jsonArray.map{it.jsonPrimitive.int}.toIntArray())
 fun ils()=PandocBuilders.fromInlines(Inlines(PandocRead.Inline.readList(raw("inlines")).single().first))
 fun bs()=PandocBuilders.fromBlocks(Blocks(PandocRead.Block.readList(raw("blocks")).single().first))
 fun attr()=PandocBuilders.attr{PandocRead.Attr.read(raw("attr"))}
 fun listAttr()=PandocBuilders.listAttributes{PandocRead.ListAttributes.read(raw("listAttributes"))}
 fun txt()=text("points")
 fun url()=text("url")
 fun title()=text("title")
 fun fmt()=text("format")
 fun caption()=GenericAstData.named<Caption>("Caption").toCanonical(PandocRead.Caption.read(raw("caption"))) as GenericNode
 fun <A> result(value:Many<A>,show:()->String)=buildJsonObject{put("value",show());put("shape",value.foldMap({"x"},builderTrace))}
 fun inlines(value:BuilderInlines)=result(value.source){AstShow.show(PandocBuilders.toInlines(value).values)}
 fun blocks(value:BuilderBlocks)=result(value.source){AstShow.show(PandocBuilders.toBlocks(value).values)}
 fun items():List<BuilderBlocks> = ReadDictionary<List<List<Block>>>(ReadShape.Items(ReadShape.Items(ReadShape.Root.BLOCK))).read(raw("items")).map{PandocBuilders.fromBlocks(Blocks(it))}
 fun lines():List<BuilderInlines> = ReadDictionary<List<List<Inline>>>(ReadShape.Items(ReadShape.Items(ReadShape.Root.INLINE))).read(raw("lines")).map{PandocBuilders.fromInlines(Inlines(it))}
 fun definitions():List<GenericTuple2<BuilderInlines,List<BuilderBlocks>>> {
  val shape=ReadShape.Items(ReadShape.Tuple(listOf(ReadShape.Items(ReadShape.Root.INLINE),ReadShape.Items(ReadShape.Items(ReadShape.Root.BLOCK)))))
  return ReadDictionary<List<Pair<List<Inline>,List<List<Block>>>>>(shape).read(raw("definitions")).map{(a,b)->GenericTuple2({PandocBuilders.fromInlines(Inlines(a))},{b.map{PandocBuilders.fromBlocks(Blocks(it))}})}
 }
 when(raw("operation")){
 "moduleExample"->{
  fun txt(value:String)=PandocBuilders.text{value}
  fun i(value:BuilderInlines)=value.source.fmap{GenericAstData.named<Inline>("Inline").fromCanonical(it())}
  fun b(value:BuilderBlocks)=value.source.fmap{GenericAstData.named<Block>("Block").fromCanonical(it())}
  fun wrap(value:Many<Inline>)=BuilderInlines(value.fmap{GenericAstData.named<Inline>("Inline").toCanonical(it()) as GenericNode})
  val phrase=wrap(ManyInlines.append({i(txt("And "))}){ManyInlines.append({i(PandocBuilders.emph{txt("another")})}){i(txt("."))}})
  val first=b(PandocBuilders.para{txt("This is the first paragraph")});val second=b(PandocBuilders.para{phrase})
  val itemOne=ManyBlocks.append({b(PandocBuilders.para{txt("item one")})}){b(PandocBuilders.para{txt("continuation")})}
  val itemTwoInline=wrap(ManyInlines.append({i(txt("item two and a "))}){i(PandocBuilders.link({"/url"},{"go to url"}){txt("link")})})
  val itemTwo=b(PandocBuilders.plain{itemTwoInline})
  fun source(value:Many<Block>)=BuilderBlocks(value.fmap{GenericAstData.named<Block>("Block").toCanonical(it()) as GenericNode})
  val bullet=b(PandocBuilders.bulletList{listOf(source(itemOne),source(itemTwo))})
  val doc=PandocMetadata.doc{ManyBlocks.append({first}){ManyBlocks.append({second}){bullet}}}
  JsonPrimitive(AstShow.show(PandocMetadata.setTitle({i(txt("My title"))}){doc}().toAst()))
 }
 "nullAttr"->JsonPrimitive(AstShow.show(PandocBuilders.nullAttr().value()))
 "text"->inlines(PandocBuilders.text(::txt))
 "str"->inlines(PandocBuilders.str(::txt))
 "emph"->inlines(PandocBuilders.emph(::ils))
 "underline"->inlines(PandocBuilders.underline(::ils))
 "strong"->inlines(PandocBuilders.strong(::ils))
 "strikeout"->inlines(PandocBuilders.strikeout(::ils))
 "superscript"->inlines(PandocBuilders.superscript(::ils))
 "subscript"->inlines(PandocBuilders.subscript(::ils))
 "smallcaps"->inlines(PandocBuilders.smallcaps(::ils))
 "singleQuoted"->inlines(PandocBuilders.singleQuoted(::ils))
 "doubleQuoted"->inlines(PandocBuilders.doubleQuoted(::ils))
 "cite"->inlines(PandocBuilders.cite({PandocRead.Citation.readList(raw("citations")).single().first.map{GenericAstData.named<Citation>("Citation").toCanonical(it) as GenericNode}},::ils))
 "codeWith"->inlines(PandocBuilders.codeWith(attr(),::txt))
 "code"->inlines(PandocBuilders.code(::txt))
 "space"->inlines(PandocBuilders.space)
 "softbreak"->inlines(PandocBuilders.softbreak)
 "linebreak"->inlines(PandocBuilders.linebreak)
 "math"->inlines(PandocBuilders.math(::txt))
 "displayMath"->inlines(PandocBuilders.displayMath(::txt))
 "rawInline"->inlines(PandocBuilders.rawInline(::fmt,::txt))
 "linkWith"->inlines(PandocBuilders.linkWith(attr(),::url,::title,::ils))
 "link"->inlines(PandocBuilders.link(::url,::title,::ils))
 "imageWith"->inlines(PandocBuilders.imageWith(attr(),::url,::title,::ils))
 "image"->inlines(PandocBuilders.image(::url,::title,::ils))
 "note"->inlines(PandocBuilders.note(::bs))
 "spanWith"->inlines(PandocBuilders.spanWith(attr(),::ils))
 "trimInlines"->inlines(PandocBuilders.trimInlines(::ils))
 "para"->blocks(PandocBuilders.para(::ils))
 "plain"->blocks(PandocBuilders.plain(::ils))
 "lineBlock"->blocks(PandocBuilders.lineBlock(::lines))
 "codeBlockWith"->blocks(PandocBuilders.codeBlockWith(attr(),::txt))
 "codeBlock"->blocks(PandocBuilders.codeBlock(::txt))
 "rawBlock"->blocks(PandocBuilders.rawBlock(::fmt,::txt))
 "blockQuote"->blocks(PandocBuilders.blockQuote(::bs))
 "orderedListWith"->blocks(PandocBuilders.orderedListWith(listAttr(),::items))
 "orderedList"->blocks(PandocBuilders.orderedList(::items))
 "bulletList"->blocks(PandocBuilders.bulletList(::items))
 "definitionList"->blocks(PandocBuilders.definitionList(::definitions))
 "headerWith"->blocks(PandocBuilders.headerWith(attr(),{r.getValue("level").jsonPrimitive.long},::ils))
 "header"->blocks(PandocBuilders.header({r.getValue("level").jsonPrimitive.long},::ils))
 "horizontalRule"->blocks(PandocBuilders.horizontalRule)
 "caption","simpleCaption","emptyCaption"->{val value=when(raw("operation")){"emptyCaption"->PandocBuilders.emptyCaption;"simpleCaption"->PandocBuilders.simpleCaption(::bs);else->PandocBuilders.caption({val xs=ReadDictionary<List<Inline>?>(ReadShape.Optional(ReadShape.Items(ReadShape.Root.INLINE))).read(raw("short"));if(xs==null)GenericMaybe.None else GenericMaybe.Some{xs.map{GenericAstData.named<Inline>("Inline").toCanonical(it) as GenericNode}}},::bs)};JsonPrimitive(AstShow.show(GenericAstData.named<Caption>("Caption").fromCanonical(value())))}
 "figureWith"->blocks(PandocBuilders.figureWith(attr(),::caption,::bs))
 "figure"->blocks(PandocBuilders.figure(::caption,::bs))
 "simpleFigureWith"->blocks(PandocBuilders.simpleFigureWith(attr(),::ils,::url,::title))
 "simpleFigure"->blocks(PandocBuilders.simpleFigure(::ils,::url,::title))
 "divWith"->blocks(PandocBuilders.divWith(attr(),::bs))
 "split"->{val input=Many.fromList(r.getValue("values").jsonArray.map{it.jsonPrimitive.long});val split=ManySplit.split(r.getValue("index").jsonPrimitive.long,input.tree.get());val a=Many.fromTree{split.first};val b=Many.fromTree{split.second};buildJsonObject{put("left",result(a){AstShow.show(a.toSourceList())});put("right",result(b){AstShow.show(b.toSourceList())})}}
 else->error("operation")
 }
}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){val h=Path.of("tools/haskell-types-builder-reference");val requests=h.resolve("requests.jsonl").readLines();val expected=h.resolve("responses.jsonl").readLines();check(requests.size==expected.size);requests.zip(expected).forEachIndexed{i,(r,e)->val actual=builderExecute(Json.parseToJsonElement(r).jsonObject);check(actual==Json.parseToJsonElement(e)){"$i: $actual != $e"}};println("${requests.size} original Builder responses and Seq shapes PASS")}
