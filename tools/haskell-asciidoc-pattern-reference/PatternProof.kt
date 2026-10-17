// Independent original writer controls. GPL-2.0-or-later; Pandoc authors.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.logging.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
private class Prefix<A>(val heads:List<()->A>,val tail:String?=null):AbstractList<A>(){
 override val size:Int get()=tail?.let{error(it)}?:heads.size
 override fun get(index:Int)=heads[index]()
 override fun iterator():Iterator<A> =object:Iterator<A>{var at=0;override fun hasNext():Boolean=if(at<heads.size)true else tail?.let{error(it)}?:false;override fun next():A=get(at++)}
}
private fun <A> partial(tail:String?,vararg heads:()->A):List<A> =Prefix(heads.toList(),tail)
fun patternCases():List<Pair<String,()->List<Block>>>{
 val s=Inline.Str("x");fun p(xs:List<Inline>)=Block.Para(xs)
 fun note(xs:List<Block>)=listOf(p(listOf(Inline.Note(xs))))
 fun emph(xs:List<Inline>)=listOf(p(listOf(Inline.Emph(xs))))
 fun figure(xs:List<Inline>)=listOf(Block.Figure(Attr(),Caption(null,emptyList()),listOf(Block.Plain(xs))))
 fun image(xs:List<Inline>)=listOf(p(listOf(Inline.Image(Attr(),xs,Target("x.png","")))))
 fun table(bs:List<Block>)=listOf(Block.Table(Attr(),Caption(null,emptyList()),listOf(ColSpec(Alignment.AlignDefault,ColWidth.Default)),TableHead(Attr(),emptyList()),listOf(TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(Cell(Attr(),Alignment.AlignDefault,1,1,bs)))))),TableFoot(Attr(),emptyList())))
 return listOf(
 "note/competing" to {note(partial("note-tail",{error("note-head")}))},
 "note/two-prefix" to {note(partial("unused-tail",{Block.Plain(listOf(s))},{error("second-head")}))},
 "note/para-two-prefix" to {note(partial("unused-tail",{p(listOf(s))},{error("second-head")}))},
 "note/wrong-head" to {note(partial("unused-tail",{Block.BlockQuote(emptyList())}))},
 "note/empty" to {note(emptyList())},"note/plain-single" to {note(listOf(Block.Plain(listOf(s))))},"note/para-single" to {note(listOf(p(listOf(s))))},
 "note/plain-tail" to {note(partial("note-tail",{Block.Plain(listOf(s))}))},
 "note/strict-body" to {note(partial("note-tail",{error("body-root")}))},
 "emph/competing" to {emph(partial("inline-tail",{error("inline-head")}))},
 "emph/strong-tail" to {emph(partial("inline-tail",{Inline.Strong(listOf(s))}))},
 "emph/strong-two" to {emph(partial("unused-tail",{Inline.Strong(listOf(s))},{error("second-head")}))},
 "emph/empty" to {emph(emptyList())},"emph/single-strong" to {emph(listOf(Inline.Strong(listOf(s))))},"emph/single-other" to {emph(listOf(s))},
 "figure/competing" to {figure(partial("image-tail",{error("image-head")}))},
 "figure/image-tail" to {figure(partial("image-tail",{Inline.Image(Attr(),listOf(s),Target("x.png",""))}))},
 "figure/image-two" to {figure(partial("unused-tail",{Inline.Image(Attr(),listOf(s),Target("x.png",""))},{error("second-head")}))},
 "figure/empty" to {figure(emptyList())},"figure/single-image" to {figure(listOf(Inline.Image(Attr(),listOf(s),Target("x.png",""))))},
 "alt/competing" to {image(partial("alt-tail",{error("alt-head")}))},"alt/empty-str-tail" to {image(partial("alt-tail",{Inline.Str("")}))},
 "alt/other-two" to {image(partial("unused-tail",{s},{error("second-head")}))},
 "alt/empty" to {image(emptyList())},"alt/empty-str" to {image(listOf(Inline.Str("")))},"alt/other" to {image(listOf(s))},
 "cell/competing" to {table(partial("cell-tail",{error("cell-head")}))},"cell/plain-tail" to {table(partial("cell-tail",{Block.Plain(listOf(s))}))},
 "cell/plain-two" to {table(partial("unused-tail",{Block.Plain(listOf(s))},{error("second-head")}))},"cell/wrong-head" to {table(partial("cell-tail",{Block.BlockQuote(emptyList())}))},
 "figure/body-competing" to {listOf(Block.Figure(Attr(),Caption(null,emptyList()),partial("block-tail",{error("block-head")})))},
 "figure/body-inline-before-tail" to {listOf(Block.Figure(Attr(),Caption(null,emptyList()),partial("body-tail",{Block.Plain(partial(null,{error("image-head")}))})))},
 "bullet/task-competing" to {listOf(Block.BulletList(listOf(partial("block-tail",{error("block-head")}))))},
 "ordered/competing" to {listOf(Block.OrderedList(ListAttributes(),listOf(partial("block-tail",{error("block-head")}))))},
 "section/second-head-before-tail" to {listOf(Block.Div(Attr("",listOf("section")),partial("unused-tail",{Block.Header(1,Attr(),listOf(Inline.Str("title")))},{error("second-head")})))},
 "title/wrong-class-ignored-tail" to {listOf(Block.Div(Attr("",listOf("note")),listOf(Block.Div(Attr("",partial("class-tail",{"other"})),listOf(p(listOf(Inline.Str("title"))))),p(listOf(Inline.Str("body"))))))},
 "span/classes-competing" to {listOf(p(listOf(Inline.Span(Attr("",partial("class-tail",{error("class-head")})),listOf(s)))))},
 "code/classes-competing" to {listOf(Block.CodeBlock(Attr("",partial("class-tail",{error("class-head")})),"x"))})
}
fun main(args:Array<String>){val expected=args.flatMap{file->GZIPInputStream(File(file).inputStream()).bufferedReader().use{it.readLines()}}.associate{line->val r=Json.parseToJsonElement(line).jsonObject;r.getValue("name").jsonPrimitive.content to r};var failures=0
 for((name,make)in patternCases()){
  val actual=observePattern(name,make)
  if(actual!=expected[name]){failures++;println("FAIL $name expected=${expected[name]} actual=$actual")}else println("PASS $name")
 };check(failures==0){"$failures mismatches"}
}

fun observePattern(name:String,make:()->List<Block>):JsonObject {
 return try{val logs=mutableListOf<LogMessage>();val output=AsciiDocWriter.write(WriterOptions(),Pandoc(blocks=make()),logs::add);buildJsonObject{put("name",name);put("output",output);put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))}}
  catch(e:IllegalStateException){buildJsonObject{put("name",name);put("thrown",e.message?:"")}}
}
