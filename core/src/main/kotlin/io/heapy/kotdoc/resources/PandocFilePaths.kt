// System.FilePath 1.5.4.0 and Text.Pandoc.Shared; original notices in licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.templates.PartialPaths

/** Keep raw Haskell Chars separate while delegating ASCII-only path structure. */
object PandocFilePaths {
    internal fun escape(path: CharacterString) = buildString { path.points().forEach { p ->
        if (p < 128) append(p.toChar()) else { append('\ue000');append(p.toString(16).padStart(6,'0')) }
    } }
    internal fun unescape(path: String): CharacterString {
        val out=mutableListOf<Int>();var at=0
        while(at<path.length) if(path[at]=='\ue000') { out.add(path.substring(at+1,at+7).toInt(16));at+=7 } else out.add(path[at++].code)
        return CharacterString(out.toIntArray())
    }
    fun combine(a: CharacterString,b: CharacterString,platform: PartialPaths = PartialPaths.System) = unescape(platform.combine(escape(a),escape(b)))
    fun canonical(path: CharacterString,platform: PartialPaths = PartialPaths.System) = unescape(platform.canonical(escape(path)))
    fun isRelative(path: CharacterString,platform: PartialPaths = PartialPaths.System) = platform.isRelative(escape(path))
    fun directories(path: CharacterString,platform: PartialPaths = PartialPaths.System) = platform.directories(escape(path)).map(::unescape)
}
