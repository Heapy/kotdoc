// Independent primitive-effect observations; GPL-2.0-or-later.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.readers.OrgReader
import io.heapy.kotdoc.readers.RstDateFormat
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.logging.Logging
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream
import java.io.FileInputStream
import java.math.BigInteger
private class ObservedRuntime(val events:MutableList<String>,q:JsonObject,state:PandocState=PandocStateContext(output={_,_->events.add("logOutput")})):PandocDataRuntime,PandocState by state {
 override val filePaths=PartialPaths.Posix
 private val files=q["files"]?.jsonArray?.associate{f->val o=f.jsonObject;filePaths.canonical(o.getValue("path").jsonPrimitive.content) to o.getValue("bytes").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()}?:emptyMap()
 private fun text(p:CharacterString)=CharacterStringChars.build(p)
 private fun bytes(p:CharacterString)=files[filePaths.canonical(text(p))]?:throw PandocError.PandocResourceNotFound(text(p))
 override fun readFileStrict(path:CharacterString):ByteArray{events.add("readFileStrict:"+text(path));return bytes(path)}
 override fun readFileLazy(path:CharacterString):ByteArray{events.add("readFileLazy:"+text(path));return bytes(path)}
 override fun fileExists(path:CharacterString):Boolean{events.add("fileExists:"+text(path));return filePaths.canonical(text(path)) in files}
 override fun getDataFileName(path:CharacterString):CharacterString{events.add("getDataFileName:"+text(path));return CharacterStringChars.fromString("data/"+text(path))}
 override fun lookupEnv(name:String):String?{events.add("lookupEnv:"+name);return if(name=="USER")"pandoc-user"else null}
 override fun getCurrentTime():PandocUtcTime{events.add("getCurrentTime");return PandocUtcTime(BigInteger.valueOf(40587),BigInteger.ZERO)}
 fun zonedDay():String{val time=PandocData.getTimestamp(this);events.add("getCurrentTimeZone");return RstDateFormat.format("%F",RstDateFormat.Timestamp(time))}
}
class OrgReaderEffectTest {
 @org.junit.jupiter.api.TestFactory fun cases()=orgRows("effects.jsonl.gz").mapIndexed{i,row->org.junit.jupiter.api.DynamicTest.dynamicTest("effect/$i"){verify(row)}}
 private fun verify(row:JsonObject){val q=row.getValue("request").jsonObject;val events=mutableListOf<String>();val runtime=ObservedRuntime(events,q);val options=ReaderOptions(extensions=Extensions.from(q.getValue("extensions").jsonArray.map{Extension.read(it.jsonPrimitive.content)}))
 val result=try{val d=OrgReader.read(options,q.getValue("input").jsonPrimitive.content,runtime,runtime::zonedDay);buildJsonObject{put("document",Json.parseToJsonElement(PandocJson.encode(d)));put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog())))}}catch(e:PandocError){buildJsonObject{put("error",e.renderError());put("showError",e.toString())}}
 val actual=buildJsonObject{put("events",JsonArray(events.map(::JsonPrimitive)));put("result",result)}
org.junit.jupiter.api.Assertions.assertTrue(orgJsonEqual(actual,row.getValue("expected")),"request: $q\nactual: $actual\nexpected: ${row.getValue("expected")}")
 }
}
