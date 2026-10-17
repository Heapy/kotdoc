package io.heapy.kotdoc.ast

import java.io.File
import java.util.zip.GZIPInputStream

internal object DataProofFiles {
    fun lines(name:String):List<String> {
        val resource=javaClass.getResourceAsStream("/pandoc-types/data/$name.gz")
        if(resource!=null)return GZIPInputStream(resource).bufferedReader().use{it.readLines()}
        val root=File(System.getProperty("kotdoc.root","."))
        val folder=if(name=="constructor-responses.jsonl")"haskell-types-generic-reference"else"haskell-types-data-reference"
        return File(root,"tools/$folder/$name").readLines()
    }
}
