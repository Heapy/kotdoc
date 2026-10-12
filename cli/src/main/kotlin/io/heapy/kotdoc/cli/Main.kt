package io.heapy.kotdoc.cli

import io.heapy.kotdoc.ast.PandocJson
import io.heapy.kotdoc.formats.NativeReader
import io.heapy.kotdoc.formats.NativeWriter
import java.io.File
import kotlin.system.exitProcess

/** Entry point for ported formats. Full Pandoc CLI parity is tracked in docs/PORTING.md. */
fun main(args: Array<String>) {
    if (args.toList() == listOf("--help")) {
        println("Usage: kotdoc [--from json|native] [--to json|native] [--standalone] [--columns N] [--output FILE] [FILE|-]")
        return
    }
    if (args.toList() in listOf(listOf("--list-input-formats"), listOf("--list-output-formats"))) {
        println("json\nnative")
        return
    }
    try {
        var input: String? = null
        var output: String? = null
        var from = "json"
        var to = "json"
        var columns = 72
        var standalone = false
        var i = 0
        while (i < args.size) {
            when (val arg = args[i++]) {
                "--from", "-f" -> from = args.getOrNull(i++) ?: error("Missing input format")
                "--to", "-t" -> to = args.getOrNull(i++) ?: error("Missing output format")
                "--output", "-o" -> output = args.getOrNull(i++) ?: error("Missing output filename")
                "--columns" -> columns = (args.getOrNull(i++) ?: error("Missing column count")).toInt()
                "--standalone", "-s" -> standalone = true
                else -> {
                    if (arg.startsWith("--columns=")) { columns = arg.substringAfter('=').toInt(); continue }
                    require(arg == "-" || !arg.startsWith("-")) { "Unsupported option: $arg" }
                    require(input == null) { "Only one input file is currently supported" }
                    input = arg
                }
            }
        }
        require(from in listOf("json", "native") && to in listOf("json", "native")) { "Supported formats: json, native (requested $from -> $to)" }
        require(columns > 0) { "Column count must be positive" }
        val text = if (input == null || input == "-") System.`in`.bufferedReader(Charsets.UTF_8).readText() else File(input).readText()
        val document = if (from == "json") PandocJson.decode(text) else NativeReader.read(text)
        val rendered = if (to == "json") PandocJson.encode(document) else NativeWriter.write(document, columns, standalone)
        val converted = if (rendered.endsWith('\n')) rendered else "$rendered\n"
        if (output == null || output == "-") print(converted) else File(output).writeText(converted)
    } catch (e: Exception) {
        System.err.println("kotdoc: ${e.message}")
        exitProcess(1)
    }
}
