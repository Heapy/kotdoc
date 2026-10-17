// Text.Pandoc.App.Input, copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later.
package io.heapy.kotdoc.cli
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.readers.RstReader
import io.heapy.kotdoc.readers.RstReaderEffects
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.logging.Verbosity
import java.nio.file.*
import java.math.BigInteger
import java.time.Instant
import io.heapy.kotdoc.parsing.Sources
import io.heapy.kotdoc.resources.PandocText
import io.heapy.kotdoc.state.PandocStateContext
internal object RstCliReader {
    fun read(bytes: ByteArray,name: String,options: ReaderOptions,preserveTabs: Boolean,trace: Boolean,resourcePaths:List<Path>): Pandoc {
        val text=try {
            PandocText.toTextM(CharacterStringChars.fromString(name),bytes)
        } catch (error: PandocError.PandocUTF8DecodingError) {
            if (hasKnownSignature(bytes)) throw PandocError.PandocInputNotTextError(name)
            val warning=LogMessage.NotUTF8Encoded(CharacterStringChars.fromString(name.ifEmpty { "input" }))
            System.err.println("[WARNING] " + Logging.showLogMessage(warning).replace("\n", "\n  "))
            bytes.toString(Charsets.ISO_8859_1)
        }.filter { it != '\r' }
        val source=SharedText.tabFilter(if(preserveTabs)0 else options.tabStop,text)
        val runtime=PandocStateContext(output={ _,message ->
            if(Logging.messageVerbosity(message)!=Verbosity.INFO)System.err.println("[${Logging.messageVerbosity(message)}] "+Logging.showLogMessage(message).replace("\n","\n  "))
        });runtime.setTrace(trace)
        runtime.setInputFiles(listOf(CharacterStringChars.fromString(name)))
        runtime.setResourcePath(resourcePaths.map{CharacterStringChars.fromString(it.toString())})
        return RstReader.read(options,Sources.fromFiles(listOf(name to source)),Effects,runtime)
    }
    private object Effects:RstReaderEffects {
        override fun readFile(path:String):ByteArray=try{
            if('\u0000' in path)throw ioError(path,IoErrorType.InvalidArgument,"FilePaths must not contain internal NUL code units.")
            val p=Path.of(path)
            if(Files.isDirectory(p))throw ioError(path,IoErrorType.InappropriateType,"is a directory")
            Files.readAllBytes(p)
        }catch(e:NoSuchFileException){throw ioError(path,IoErrorType.NoSuchThing,"No such file or directory")}
         catch(e:NotDirectoryException){throw ioError(path,IoErrorType.InappropriateType,"Not a directory")}
         catch(e:AccessDeniedException){throw ioError(path,IoErrorType.PermissionDenied,"Permission denied")}
         catch(e:FileSystemException){
            // UnixFileSystemProvider uses the generic exception for ENOTDIR.
            if(e.reason=="Not a directory")throw ioError(path,IoErrorType.InappropriateType,"Not a directory")
            throw e
         }
        override fun openURL(url:String):ByteArray {
            // Explicit unfinished Pandoc Class.IO.HTTP backend; do not substitute
            // java.net.http responses/errors for pinned http-client semantics.
            throw UnsupportedOperationException("RST reader HTTP backend is not yet connected: $url")
        }
        override fun getTimestamp():PandocUtcTime {
            val epoch=System.getenv("SOURCE_DATE_EPOCH")?.let(::safeReadInteger)
            if(epoch!=null)return PandocUtcTime.fromPosixPicoseconds(epoch*BigInteger.TEN.pow(12))
            val now=Instant.now()
            return PandocUtcTime.fromPosixPicoseconds(now.epochSecond.toBigInteger()*BigInteger.TEN.pow(12)+now.nano.toBigInteger()*1000.toBigInteger())
        }
        private fun ioError(path:String,type:IoErrorType,description:String)=PandocError.PandocIOError(path,GhcIoError(type,
            CharacterStringChars.fromString("withBinaryFile"),CharacterStringChars.fromString(description),CharacterStringChars.fromString(path)))
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
