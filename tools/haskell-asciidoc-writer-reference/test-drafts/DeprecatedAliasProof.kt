// Original Pandoc expectation; GPL-2.0-or-later, Pandoc authors.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.PandocJson
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.logging.LogMessage
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
@Suppress("DEPRECATION")
fun main(args:Array<String>){
 val rows=GZIPInputStream(File(args[0]).inputStream()).bufferedReader().use{it.readLines().map{line->Json.parseToJsonElement(line).jsonObject}}
 val modern=rows.filter{!it.getValue("legacy").jsonPrimitive.boolean};check(modern.isNotEmpty())
 for(r in modern){val logs=mutableListOf<LogMessage>();val actual=AsciiDocWriter.writeAsciiDoctor(WriterOptions(),PandocJson.decode(r.getValue("document")),logs::add);check(actual==r.getValue("actual").jsonPrimitive.content);check(logs.isEmpty());println("PASS deprecated library alias / "+r.getValue("name"))}
}
