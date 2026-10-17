package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.logging.*
import kotlinx.serialization.json.*
fun main(){generateSequence(::readLine).forEach{line->val q=Json.parseToJsonElement(line).jsonObject;val o=q["options"]?.jsonObject?:JsonObject(emptyMap());val opts=WriterOptions(wrapText=o["wrap"]?.jsonPrimitive?.content?.let(WrapOption::valueOf)?:WrapOption.WrapAuto,columns=o["columns"]?.jsonPrimitive?.long?:72,extensions=o["extensions"]?.let(Extensions::fromJson)?:Extensions.Empty,tableOfContents=o["toc"]?.jsonPrimitive?.boolean?:false,dpi=o["dpi"]?.jsonPrimitive?.long?:96,variables=o["variables"]?.let{(TemplateValues.Text.fromJson(it)as Val.MapVal).context}?:Context(),template=q["template"]?.let{(TemplateCompiler.Text.compile("oracle.asciidoc",it.jsonPrimitive.content)as CompiledTemplate.Success).template});val logs=mutableListOf<LogMessage>();val doc=PandocJson.decode(q.getValue("document"));val out=if(q["legacy"]?.jsonPrimitive?.boolean==true)AsciiDocWriter.writeLegacy(opts,doc,logs::add)else AsciiDocWriter.write(opts,doc,logs::add);println(buildJsonObject{put("name",q.getValue("name"));put("output",out);put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))})}}
