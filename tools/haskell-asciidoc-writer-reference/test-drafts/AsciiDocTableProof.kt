// Pinned Pandoc/source oracle checks. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream
import java.io.File
internal object AsciiDocTableProof {
fun run(args:Array<String>){var count=0
 GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.forEach{line->val record=Json.parseToJsonElement(line).jsonObject;val q=record.getValue("request").jsonObject;val rows=q.getValue("rows").jsonArray.map(PandocJson::decodeRow);val result=when(q.getValue("operation").jsonPrimitive.content){"headers"->AsciiDocTables.adjustHeaders(rows);"footers"->AsciiDocTables.adjustFooters(rows);else->AsciiDocTables.adjustEmptyRows(rows)};val actual=JsonArray(result.map(PandocJson::encodeRow));check(actual==record.getValue("expected").jsonObject.getValue("rows")){q.getValue("name").toString()};count++}}
 println("PASS $count original AsciiDoc table-helper observations")
}
}
