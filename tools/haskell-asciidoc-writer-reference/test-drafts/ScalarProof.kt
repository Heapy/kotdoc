package io.heapy.kotdoc.writers
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.layout.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
fun main(args:Array<String>){val cls=Class.forName("io.heapy.kotdoc.writers.AsciiDocWriter\$Session");val session=cls.declaredConstructors.single().apply{isAccessible=true}.newInstance(WriterOptions(),false,{_:LogMessage->Unit});val level=cls.getDeclaredField("tableLevel").apply{isAccessible=true};val escape=cls.getDeclaredMethod("escape",String::class.java).apply{isAccessible=true};val needs=cls.getDeclaredMethod("needsEscaping",String::class.java).apply{isAccessible=true};var count=0
GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.forEach{line->val row=Json.parseToJsonElement(line).jsonObject;val q=row.getValue("request").jsonObject;level.setLong(session,if(q["table"]?.jsonPrimitive?.boolean==true)1 else 0);val input=q.getValue("input").jsonPrimitive.content;val actual=if(q.getValue("operation").jsonPrimitive.content=="needs")JsonPrimitive(needs.invoke(session,input)as Boolean)else{@Suppress("UNCHECKED_CAST")val d=escape.invoke(session,input)as Doc<String>;JsonPrimitive(DocLayout.Text.render(null,d))};check(actual==row.getValue("expected").jsonObject.getValue("result")){q.getValue("name").toString()};count++}}
println("PASS $count original scalar observations")}
