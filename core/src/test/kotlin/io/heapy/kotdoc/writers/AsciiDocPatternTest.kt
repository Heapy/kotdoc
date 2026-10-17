// Independent source demand expectations. Pandoc authors; GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
class AsciiDocPatternTest {
 @TestFactory fun sourcePatterns():List<DynamicTest> {
  val root=File("../tools/haskell-asciidoc-pattern-reference/fixtures")
  val expected=listOf("patterns","adjacent").flatMap{file->GZIPInputStream(File(root,"$file.jsonl.gz").inputStream()).bufferedReader().use{it.readLines()}}.associate{line->val row=Json.parseToJsonElement(line).jsonObject;row.getValue("name").jsonPrimitive.content to row}
  return patternCases().map{(name,make)->DynamicTest.dynamicTest(name){check(observePattern(name,make)==expected[name]){name}}}
 }
}
