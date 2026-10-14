// Generated Skylighting.Syntax 0.15, Copyright (c) John MacFarlane and
// individual syntax authors. BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.GZIPInputStream

/** The original baked, already keyword-resolved map; each definition is shared lazily. */
object DefaultSyntaxes {
    val syntaxMap: SyntaxMap by lazy { readMap() }
    internal fun readMap(): LazySyntaxMap<Syntax> {
        val root = "/highlighting/default-syntax-map/"
        val modules = checkNotNull(javaClass.getResourceAsStream(root + "index.json")).bufferedReader().use { reader ->
            Json.parseToJsonElement(reader.readText()).jsonArray.associate { row ->
                val values = row.jsonArray; values[0].jsonPrimitive.content to values[1].jsonPrimitive.content
            }
        }
        return LazySyntaxMap(modules.keys) { name ->
            val bytes = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream(root + modules.getValue(name) + ".bin.gz"))).use { it.readBytes() }
            when (val decoded = SyntaxBinary.syntax.decode(bytes)) {
                is SyntaxBinaryResult.Success -> {
                    check(decoded.remaining.isEmpty()) { "Trailing bytes in bundled syntax $name" }
                    decoded.value
                }
                is SyntaxBinaryResult.Failure -> error("Bundled syntax $name at ${decoded.consumed}: ${decoded.error}")
            }
        }
    }
}
