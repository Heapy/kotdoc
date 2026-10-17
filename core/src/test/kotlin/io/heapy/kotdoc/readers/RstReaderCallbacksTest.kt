// Independent expectations from pinned Pandoc RST tests/module; GPL-2.0-or-later.
package io.heapy.kotdoc.readers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.logging.Logging
import kotlinx.serialization.json.*
import java.io.File
import java.time.LocalDate
class RstReaderCallbacksTest {
 private fun timestamp(q:JsonObject):RstDateFormat.Timestamp{
  q["utc"]?.jsonArray?.let{return RstDateFormat.Timestamp(io.heapy.kotdoc.resources.PandocUtcTime(it[0].jsonPrimitive.content.toBigInteger(),it[1].jsonPrimitive.content.toBigInteger()))}
  val raw=q["timestamp"]?.jsonPrimitive?.content?:return RstDateFormat.epoch
  val parts=raw.split(' ');val match=Regex("([+-]?[0-9]+)-([0-9]+)-([0-9]+)").matchEntire(parts[0])!!;val day=RstDateFormat.fromGregorian(java.math.BigInteger(match.groupValues[1]),match.groupValues[2].toInt(),match.groupValues[3].toInt());val time=parts[1].split(':');val sec=time[2].split('.');val seconds=java.math.BigInteger(time[0])*3600.toBigInteger()+java.math.BigInteger(time[1])*60.toBigInteger()+java.math.BigInteger(sec[0]);val pico=sec.getOrNull(1)?.padEnd(12,'0')?.toBigInteger()?:java.math.BigInteger.ZERO
  return RstDateFormat.Timestamp(io.heapy.kotdoc.resources.PandocUtcTime(day,seconds*java.math.BigInteger.TEN.pow(12)+pico))
 }
 private fun jsonEqual(a:JsonElement,b:JsonElement):Boolean{
  val pending=java.util.ArrayDeque<Pair<JsonElement,JsonElement>>();pending.add(a to b)
  while(pending.isNotEmpty()){val(x,y)=pending.removeLast()
   when{
    x is JsonObject&&y is JsonObject->{if(x.keys!=y.keys)return false;for(k in x.keys)pending.add(x.getValue(k)to y.getValue(k))}
    x is JsonArray&&y is JsonArray->{if(x.size!=y.size)return false;for(i in x.indices)pending.add(x[i]to y[i])}
    x is JsonPrimitive&&y is JsonPrimitive->{if(!x.isString&&!y.isString&&x!==JsonNull&&y!==JsonNull&&x.content!="true"&&x.content!="false"&&y.content!="true"&&y.content!="false"){if(x.content.toBigDecimal().compareTo(y.content.toBigDecimal())!=0)return false}else if(x!=y)return false}
    else->return false
   }
  };return true
 }
 @org.junit.jupiter.api.TestFactory fun originalAndSourceCases():List<org.junit.jupiter.api.DynamicTest> = buildList {
  val fixtures=listOf("callback-observations.jsonl")
  for(file in fixtures){
   val stream=checkNotNull(javaClass.getResourceAsStream("/io/heapy/kotdoc/rst-reader/"+file))
   stream.bufferedReader(Charsets.UTF_8).useLines{lines->lines.forEach{line->
    val record=Json.parseToJsonElement(line).jsonObject
    val name=(record["request"]?.jsonObject?:record).getValue("name").jsonPrimitive.content
    add(org.junit.jupiter.api.DynamicTest.dynamicTest("$file/$name"){verify(record)})
   }}
  }
 }
 private fun verify(record:JsonObject){
  if(record["request"]==null){
   val actual=Json.parseToJsonElement(PandocJson.encode(RstReader.read(ReaderOptions(standalone=true),record.getValue("input").jsonPrimitive.content)))
   org.junit.jupiter.api.Assertions.assertTrue(jsonEqual(actual,record.getValue("expected")),"original AST mismatch")
   return
  }
  val q=record.getValue("request").jsonObject;val e=record.getValue("expected").jsonObject
  val input=q["input"]?.jsonPrimitive?.content?:""
val actual:JsonElement=when(q["kind"]?.jsonPrimitive?.content?:"reader"){
 "flatten","flatten-deep"->{val bs=if(q["kind"]?.jsonPrimitive?.content=="flatten")PandocJson.decode(input).blocks else {var b:Block=Block.Para(listOf(Inline.Str("x")));repeat(input.toInt()){b=Block.Div(Attr(),listOf(b))};listOf(b)};Json.parseToJsonElement(PandocJson.encode(Pandoc(blocks=listOf(Block.Plain(RstBlockInlines.convert(bs)))))).jsonObject.getValue("blocks").jsonArray[0].jsonObject.getValue("c")}
 "date"->JsonPrimitive(RstDateFormat.format(input,timestamp(q)))
 "unicode"->JsonPrimitive(RstReaderPure.unicodeTransform(input))
 "chunks"->JsonArray(RstReaderPure.chunks(input).map(::JsonPrimitive))
 "col-spans"->RstReaderPure.colSpans(q.getValue("indices").jsonArray.map{it.jsonPrimitive.int},q.getValue("spans").jsonArray.map{it.jsonPrimitive.int})?.let{JsonArray(it.map(::JsonPrimitive))}?:JsonNull
 "widths"->JsonArray(RstTableAdapter.widthsFromIndices(q.getValue("options").jsonObject.getValue("columns").jsonPrimitive.long,q.getValue("indices").jsonArray.map{it.jsonPrimitive.int}).map(::JsonPrimitive))
 else->{val files=q["files"]?.jsonArray?.associate{val a=it.jsonArray;a[0].jsonPrimitive.content to a[1].jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()}?:emptyMap()
 val events=mutableListOf<String>()
 val effects=object:RstReaderEffects{
 override fun readFile(path:String):ByteArray {events+="readFile:$path";return (if(q["fetchProfile"]?.jsonPrimitive?.boolean==true)files.entries.firstOrNull{io.heapy.kotdoc.templates.PartialPaths.System.canonical(it.key)==io.heapy.kotdoc.templates.PartialPaths.System.canonical(path)}?.value else files[path.removePrefix("./")])?:throw PandocError.PandocResourceNotFound(path)}
 override fun fetchItem(url:String,runtime:io.heapy.kotdoc.state.PandocState):ByteArray {if(q["fetchProfile"]?.jsonPrimitive?.boolean==true)return RstFetch.fetch(url,runtime,::readFile){events+="openURL:$it";throw PandocError.PandocResourceNotFound(it)};val bytes=files[url.removePrefix("./")]?:throw PandocError.PandocResourceNotFound(url);runtime.report(io.heapy.kotdoc.logging.LogMessage.LoadedResource(url,url));return bytes}
 override fun getTimestamp():io.heapy.kotdoc.resources.PandocUtcTime {events+="timestamp";return q["environment"]?.jsonArray?.firstOrNull{it.jsonArray[0].jsonPrimitive.content=="SOURCE_DATE_EPOCH"}?.jsonArray?.get(1)?.jsonPrimitive?.content?.let{io.heapy.kotdoc.resources.safeReadInteger(it)}?.let{io.heapy.kotdoc.resources.PandocUtcTime.fromPosixPicoseconds(it*java.math.BigInteger.TEN.pow(12))}?:timestamp(q).utc}
 }
 val options=(q["options"]?.let(ReaderOptions::fromJson)?:ReaderOptions()).copy(extensions=Extensions.read(q["extensions"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList()))
 val sources=q["chunks"]?.jsonArray?.let{chunks->Sources(chunks.map{c->val a=c.jsonArray;SourceChunk(SourcePos(a[0].jsonPrimitive.content,a[1].jsonPrimitive.long,a[2].jsonPrimitive.long),a[3].jsonPrimitive.content)})}?:Sources.fromText(input)
 val runtime=PandocStateContext()
 q["resourcePath"]?.jsonArray?.let{runtime.setResourcePath(it.map{x->io.heapy.kotdoc.layout.CharacterStringChars.fromString(x.jsonPrimitive.content)})}
 q["sourceURL"]?.jsonPrimitive?.content?.let{runtime.setInputFiles(listOf(io.heapy.kotdoc.layout.CharacterStringChars.fromString(it)))}
 q["media"]?.jsonArray?.forEach{val a=it.jsonArray;runtime.insertMedia(io.heapy.kotdoc.layout.CharacterStringChars.fromString(a[0].jsonPrimitive.content),null,a[1].jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray())}
 try{val doc=RstReader.read(options,sources,effects,runtime);buildJsonObject{if(e["showDocument"]!=null)put("showDocument",AstShow.show(doc))else put("document",Json.parseToJsonElement(PandocJson.encode(doc)));put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog())));put("events",JsonArray(events.map(::JsonPrimitive)))}}
 catch(error:PandocError){buildJsonObject{put("error",error.renderError());put("showError",error.toString());put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog())));put("events",JsonArray(events.map(::JsonPrimitive)))}}
 }
 }
  val expected=e["result"]?:JsonObject(e.filterKeys{it!="name"})
  org.junit.jupiter.api.Assertions.assertTrue(jsonEqual(actual,expected),"actual: $actual\nexpected: $expected")
 }
}
