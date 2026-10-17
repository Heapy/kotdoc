package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
fun main() {
    for(datatype in DataDictionaries.metadata.values)println(buildJsonArray {
        add(datatype.name)
        add(buildJsonArray {for(constructor in datatype.constructors()) add(buildJsonArray {
            add((constructor.representation as DataConstrRep.Algebraic).index)
            add(constructor.name)
            add(buildJsonArray{for(field in constructor.fields)add(field)})
            add(constructor.fixity.name)
        })})
    })
}
