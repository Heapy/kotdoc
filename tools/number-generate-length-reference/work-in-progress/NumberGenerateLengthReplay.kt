package io.heapy.kotdoc.crypto
import java.io.File
import kotlinx.serialization.json.*
object NumberGenerateLengthReplay {
 @JvmStatic fun main(args:Array<String>){var n=0;for(v in Json.parseToJsonElement(File(args[0]).readText()).jsonArray){val r=v.jsonObject;check(r.getValue("domain").jsonObject.getValue("defined").jsonPrimitive.boolean);val actual=NumberGenerateReplay.observe(r.getValue("request").jsonObject);check(actual==r["expected"]){"row$n request=${r["request"]} expected=${r["expected"]} actual=$actual"};n++};println("PASS $n original defined-buffer-length Number.Generate rows")}
}
