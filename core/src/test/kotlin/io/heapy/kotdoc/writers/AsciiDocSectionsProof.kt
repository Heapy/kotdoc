// Pinned Pandoc/source oracle checks. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
internal object AsciiDocSectionsProof {
fun run(args:Array<String>){var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.forEach{line->val r=Json.parseToJsonElement(line).jsonObject;val q=r.getValue("request").jsonObject;val d=PandocJson.decode(q.getValue("document"));val result=AsciiDocSections.make(d.blocks);check(PandocJson.toJson(d.copy(blocks=result))==r.getValue("expected").jsonObject.getValue("document")){q.getValue("name").toString()};if(q.getValue("name").jsonPrimitive.content.startsWith("Tests.Shared/"))check(AsciiDocSections.make(result)==result){"original explicit idempotence"};count++}};println("PASS $count original False/Nothing section cases + idempotence")}
}
