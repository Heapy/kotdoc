// Text.Pandoc.App.Input, copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later.
package io.heapy.kotdoc.cli
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.readers.MuseReader
import io.heapy.kotdoc.parsing.Sources
import io.heapy.kotdoc.resources.PandocText
import io.heapy.kotdoc.state.PandocStateContext
internal object MuseCliReader {
    fun read(bytes: ByteArray,name: String,options: ReaderOptions,preserveTabs: Boolean,trace: Boolean): Pandoc {
        val text=try {
            PandocText.toTextM(CharacterStringChars.fromString(name),bytes)
        } catch (error: PandocError.PandocUTF8DecodingError) {
            if (hasKnownSignature(bytes)) throw PandocError.PandocInputNotTextError(name)
            val warning=LogMessage.NotUTF8Encoded(CharacterStringChars.fromString(name.ifEmpty { "input" }))
            System.err.println("[WARNING] " + Logging.showLogMessage(warning).replace("\n", "\n  "))
            bytes.toString(Charsets.ISO_8859_1)
        }.filter { it != '\r' }
        val source=SharedText.tabFilter(if(preserveTabs)0 else options.tabStop,text)
        val runtime=PandocStateContext();runtime.setTrace(trace)
        return MuseReader.read(options,Sources.fromFiles(listOf(name to source)),runtime)
    }
    // App.Input applies signature detection only after UTF-8 decoding fails.
    private fun hasKnownSignature(bytes: ByteArray): Boolean {
        fun prefix(vararg signature: Int)=bytes.size>=signature.size && signature.indices.all { (bytes[it].toInt() and 255)==signature[it] }
        return prefix(0x50,0x4b,0x03,0x04) || prefix(0x25,0x50,0x44,0x46,0x2d) ||
            prefix(0xd0,0xcf,0x11,0xe0,0xa1,0xb1,0x1a,0xe1) ||
            (prefix(0x41,0x54,0x26,0x54,0x46,0x4f,0x52,0x4d) && bytes.size>=15 &&
                bytes.sliceArray(12..14).contentEquals(byteArrayOf(0x44,0x4a,0x56)))
    }
}
