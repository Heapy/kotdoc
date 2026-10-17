package io.heapy.kotdoc.ast
import java.io.File
import java.util.zip.GZIPInputStream
internal object GenericRepFixtures {
    fun text(name:String):String = javaClass.getResourceAsStream("/pandoc-types/generic-rep/$name.gz")?.let { stream ->
        GZIPInputStream(stream).bufferedReader().use{it.readText()}
    } ?: File(File(System.getProperty("kotdoc.root",".")),"tools/haskell-types-generic-rep-reference/$name").readText()
}
