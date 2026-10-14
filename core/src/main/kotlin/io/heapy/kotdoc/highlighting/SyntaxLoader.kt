// Skylighting.Loader 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.templates.PartialPaths
import java.nio.file.Files
import java.nio.file.Path

sealed interface SyntaxMapResult {
    class Success(val syntaxes: SyntaxMap) : SyntaxMapResult
    class Failure(val error: String) : SyntaxMapResult
}
class LoadedSyntaxes(errors: Map<String, String>, syntaxes: SyntaxMap) {
    val errors: Map<String, String> = syntaxMap(errors)
    val syntaxes: SyntaxMap = syntaxMap(syntaxes)
}

object SyntaxLoader {
    fun loadSyntaxFromFile(path: Path): SyntaxParseResult = when (val result = SyntaxParser.parseSyntaxDefinition(path)) {
        is SyntaxParseResult.Success -> result
        is SyntaxParseResult.Failure -> SyntaxParseResult.Failure("Error parsing file ${HaskellString.quote(path.toString())}: ${result.error}")
    }
    internal fun syntaxFiles(directory: Path): List<Path> = Files.list(directory).use { entries ->
        entries.filter { PartialPaths.System.takeExtension(it.fileName.toString()) == ".xml" }
            .filter { Files.exists(it) && !Files.isDirectory(it) }.toList()
    }
    fun loadSyntaxesFromDir(directory: Path): SyntaxMapResult = loadOrderedFiles(syntaxFiles(directory))
    fun loadValidSyntaxesFromDir(directory: Path): LoadedSyntaxes = loadValidOrderedFiles(syntaxFiles(directory))

    internal fun loadOrderedFiles(files: List<Path>): SyntaxMapResult {
        val syntaxes = linkedMapOf<String, Syntax>()
        for (path in files) when (val result = loadSyntaxFromFile(path)) {
            is SyntaxParseResult.Failure -> return SyntaxMapResult.Failure(result.error)
            is SyntaxParseResult.Success -> syntaxes[result.syntax.name] = result.syntax
        }
        val map = syntaxMap(syntaxes)
        return SyntaxMapResult.Success(syntaxMap(map.mapValues { Syntaxes.resolveKeywords(map, it.value) }))
    }
    internal fun loadValidOrderedFiles(files: List<Path>): LoadedSyntaxes {
        val errors = linkedMapOf<String, String>(); val syntaxes = linkedMapOf<String, Syntax>()
        for (path in files) when (val result = loadSyntaxFromFile(path)) {
            is SyntaxParseResult.Failure -> errors[path.toString()] = result.error
            is SyntaxParseResult.Success -> syntaxes[result.syntax.name] = result.syntax
        }
        val map = syntaxMap(syntaxes)
        return LoadedSyntaxes(errors, map.mapValues { Syntaxes.resolveKeywords(map, it.value) })
    }
}
