package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.WriterOptions
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
private class ProbeList<T>(val length:Int,val sizeError:String?=null,val item:(Int)->T):AbstractList<T>(){override val size:Int get(){sizeError?.let{error(it)};return length};override fun get(index:Int)=item(index)}
private fun plain(s:String)=Block.Plain(listOf(Inline.Str(s)))
fun main(args:Array<String>){val expected=GZIPInputStream(File(args[0]).inputStream()).bufferedReader().use{it.readLines().associate{line->val r=Json.parseToJsonElement(line).jsonObject;r.getValue("name").jsonPrimitive.content to r}};var count=0
fun checkCase(name:String,document:()->Pandoc){val actual=try{val logs=mutableListOf<io.heapy.kotdoc.logging.LogMessage>();val out=AsciiDocWriter.write(WriterOptions(),document(),logs::add);buildJsonObject{put("name",name);put("output",out);put("logs",JsonArray(emptyList()))}.also{check(logs.isEmpty())}}catch(e:IllegalStateException){buildJsonObject{put("name",name);put("thrown",e.message?:"")}};check(actual==expected.getValue(name)){"$name expected=${expected[name]} actual=$actual"};count++}
fun note(bs:List<Block>)=Pandoc(blocks=listOf(Block.Para(listOf(Inline.Note(bs)))))
checkCase("note/root"){note(ProbeList(0,"note-root"){error("unreachable")})}
checkCase("note/head2"){note(ProbeList(2){if(it==0)error("note-head")else plain("tail")})}
// Para and BlockQuote fields are StrictData in the original. Their constructor
// fails when the lazy head expression is evaluated; model that at get(0).
checkCase("note/first-para-inline2"){note(ProbeList(2){if(it==0)error("inline-root")else plain("tail")})}
checkCase("note/tail"){note(ProbeList(1,"note-tail"){plain("x")})}
checkCase("note/multi-ignored-inline"){note(ProbeList(2){if(it==0)plain("x")else error("ignored-inline")})}
checkCase("note/first-quote-inner"){note(ProbeList(2){if(it==0)error("ignored-quote")else plain("tail")})}
checkCase("meta/ignored"){error("meta-root")}
checkCase("caption/ignored-short"){Pandoc(blocks=listOf(Block.Figure(Attr(),Caption(ProbeList(0,"ignored-short"){error("unreachable")},emptyList()),listOf(Block.Para(listOf(Inline.Str("body")))))))}
println("PASS $count source demand observations; code/ignored-attrid native-only (no deferred String carrier)")}
