// Text.Pandoc.App.Input, copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later.
package io.heapy.kotdoc.cli
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.readers.OrgReader
import io.heapy.kotdoc.parsing.Sources
import io.heapy.kotdoc.resources.PandocText
import io.heapy.kotdoc.state.PandocStateContext
internal object OrgCliReader {
    fun read(bytes: ByteArray,name: String,options: ReaderOptions,preserveTabs: Boolean,trace: Boolean,resourcePaths:List<java.nio.file.Path>): Pandoc {
        val text=try {
            PandocText.toTextM(CharacterStringChars.fromString(name),bytes)
        } catch (error: PandocError.PandocUTF8DecodingError) {
            if (hasKnownSignature(bytes)) throw PandocError.PandocInputNotTextError(name)
            val warning=LogMessage.NotUTF8Encoded(CharacterStringChars.fromString(name.ifEmpty { "input" }))
            System.err.println("[WARNING] " + Logging.showLogMessage(warning).replace("\n", "\n  "))
            bytes.toString(Charsets.ISO_8859_1)
        }.filter { it != '\r' }
        val source=SharedText.tabFilter(if(preserveTabs)0 else options.tabStop,text)
        val runtime=Runtime();runtime.setTrace(trace)
        runtime.setInputFiles(listOf(CharacterStringChars.fromString(name)))
        runtime.setResourcePath(resourcePaths.map{CharacterStringChars.fromString(it.toString())})
        return OrgReader.read(options,Sources.fromFiles(listOf(name to source)),runtime) {
            val time=io.heapy.kotdoc.resources.PandocData.getTimestamp(runtime)
            val zone=java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds/60L
            io.heapy.kotdoc.resources.PandocGregorian.zonedDay(time,zone)
        }
    }
    private class Runtime:io.heapy.kotdoc.resources.PandocDataRuntime,io.heapy.kotdoc.state.PandocState by PandocStateContext(output={_,message ->
        if(Logging.messageVerbosity(message)!=io.heapy.kotdoc.logging.Verbosity.INFO)
            System.err.println("[${Logging.messageVerbosity(message)}] "+Logging.showLogMessage(message).replace("\n","\n  "))
    }) {
        private fun path(value:io.heapy.kotdoc.layout.CharacterString)=java.nio.file.Path.of(CharacterStringChars.build(value))
        override fun readFileStrict(path:io.heapy.kotdoc.layout.CharacterString):ByteArray=try {java.nio.file.Files.readAllBytes(path(path))}catch(_:java.io.IOException){throw PandocError.PandocResourceNotFound(CharacterStringChars.build(path))}catch(_:java.nio.file.InvalidPathException){throw PandocError.PandocResourceNotFound(CharacterStringChars.build(path))}
        override fun readFileLazy(path:io.heapy.kotdoc.layout.CharacterString)=readFileStrict(path)
        override fun fileExists(path:io.heapy.kotdoc.layout.CharacterString)=try{java.nio.file.Files.isRegularFile(path(path))}catch(_:java.nio.file.InvalidPathException){false}
        override fun getDataFileName(path:io.heapy.kotdoc.layout.CharacterString)=path
        override fun lookupEnv(name:String)=System.getenv(name)
        override fun getCurrentTime():io.heapy.kotdoc.resources.PandocUtcTime {
            val time=java.time.Instant.now()
            return io.heapy.kotdoc.resources.PandocUtcTime.fromPosixPicoseconds(time.epochSecond.toBigInteger()*java.math.BigInteger.TEN.pow(12)+time.nano.toBigInteger()*1000.toBigInteger())
        }
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
