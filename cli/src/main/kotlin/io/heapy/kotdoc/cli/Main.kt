package io.heapy.kotdoc.cli

import io.heapy.kotdoc.ast.PandocJson
import java.io.File
import kotlin.system.exitProcess

/** Initial JSON reader/writer entry point. Other formats are tracked in docs/PORTING.md. */
fun main(args: Array<String>) {
    if (args.toList() == listOf("--help")) {
        println("Usage: kotdoc [--from json] [--to json] [--output FILE] [FILE|-]")
        return
    }
    try {
        var input: String? = null
        var output: String? = null
        var from = "json"
        var to = "json"
        var i = 0
        while (i < args.size) {
            when (val arg = args[i++]) {
                "--from", "-f" -> from = args.getOrNull(i++) ?: error("Missing input format")
                "--to", "-t" -> to = args.getOrNull(i++) ?: error("Missing output format")
                "--output", "-o" -> output = args.getOrNull(i++) ?: error("Missing output filename")
                else -> {
                    require(arg == "-" || !arg.startsWith("-")) { "Unsupported option: $arg" }
                    require(input == null) { "Only one input file is currently supported" }
                    input = arg
                }
            }
        }
        require(from == "json" && to == "json") { "Only Pandoc JSON is implemented so far (requested $from -> $to)" }
        val text = if (input == null || input == "-") System.`in`.bufferedReader(Charsets.UTF_8).readText() else File(input).readText()
        val converted = PandocJson.encode(PandocJson.decode(text)) + "\n"
        if (output == null || output == "-") print(converted) else File(output).writeText(converted)
    } catch (e: Exception) {
        System.err.println("kotdoc: ${e.message}")
        exitProcess(1)
    }
}
