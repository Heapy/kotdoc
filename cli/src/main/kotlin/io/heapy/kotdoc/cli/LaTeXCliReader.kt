package io.heapy.kotdoc.cli

import io.heapy.kotdoc.ast.Pandoc
import io.heapy.kotdoc.ast.SharedText
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.latex.LaTeXReader
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.ReaderOptions
import io.heapy.kotdoc.parsing.Sources
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.state.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.math.BigInteger

internal object LaTeXCliReader {
    data class Result(val document: Pandoc,val resourcePaths: List<Path>)
    fun read(bytes: ByteArray,name: String,options: ReaderOptions,preserveTabs: Boolean,resourcePaths: List<Path>): Result {
        val runtime=Runtime()
        runtime.setResourcePath(resourcePaths.map { CharacterStringChars.fromString(it.toString()) })
        val text=PandocText.toTextM(CharacterStringChars.fromString(name),bytes)
        val input=SharedText.tabFilter(if(preserveTabs)0 else options.tabStop,text)
        val reader=LaTeXReader(runtime) {
            val time=PandocData.getTimestamp(runtime)
            val zone=ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds/60L
            PandocGregorian.zonedDay(time,zone)
        }
        val document=reader.read(options,Sources.fromFiles(listOf(name to input)))
        return Result(document,runtime.getResourcePath().map { Path.of(CharacterStringChars.build(it)) })
    }
    private class Runtime : PandocDataRuntime,PandocState by PandocStateContext(output={ _,message ->
        System.err.println("[${Logging.messageVerbosity(message)}] "+Logging.showLogMessage(message).replace("\n","\n  "))
    }) {
        private fun path(value: CharacterString)=Path.of(CharacterStringChars.build(value))
        override fun fileExists(path: CharacterString)=Files.isRegularFile(path(path))
        override fun readFileStrict(path: CharacterString): ByteArray = try { Files.readAllBytes(path(path)) } catch(_: IOException) { throw PandocError.PandocResourceNotFound(TextChars.fromPoints(path.points())) }
        override fun readFileLazy(path: CharacterString)=readFileStrict(path)
        override fun getDataFileName(path: CharacterString)=path
        override fun lookupEnv(name: String)=System.getenv(name)
        override fun getCurrentTime(): PandocUtcTime {
            val time=Instant.now()
            return PandocUtcTime.fromPosixPicoseconds(BigInteger.valueOf(time.epochSecond)*BigInteger.TEN.pow(12)+BigInteger.valueOf(time.nano.toLong())*BigInteger.valueOf(1000))
        }
    }
}
