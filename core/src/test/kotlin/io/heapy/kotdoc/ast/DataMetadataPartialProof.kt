package io.heapy.kotdoc.ast

private fun partialName(head:Int):GenericList<Int> = GenericList.cons({head}){error("name tail")}
private fun prefix(value:GenericList<Int>,n:Int)=buildString {
    var current=value
    repeat(n) { when(val step=current.step()) {
        GenericList.Step.Nil->return@buildString
        is GenericList.Step.Cons->{appendCodePoint(step.head());if(it+1<n)current=step.tail()}
    } }
}
fun main() {
    val actual=linkedMapOf<String,String>()
    fun probe(name:String,value:()->String) {actual[name]=try {value()}catch(_:Exception){"error"}}
    val dt=DataTypeInfo.source({partialName('D'.code)}){DataTypeRep.Algebraic(DataThunk{GenericList.empty()})}
    val c=DataConstr.source({DataConstrRep.Algebraic(1)},{partialName('C'.code)},
        {GenericList.cons({partialName('f'.code)}){GenericList.empty()}},{error("fixity")},{dt})
    probe("datatype-prefix"){prefix(dt.nameChars,1)}
    probe("constructor-prefix"){prefix(c.nameChars,1)}
    probe("field-prefix"){prefix(c.fieldNameChars[0],1)}
    probe("constr-type-prefix"){prefix(c.type.nameChars,1)}
    probe("index-ignores-names"){c.index.toString()}
    probe("fixity-demand"){c.fixity.toString()}
    val bad=DataTypeInfo.source({error("datatype name")}){DataTypeRep.Integral}
    val exception=try {bad.constructors();error("expected failure")}catch(e:DataSourceError){e}
    probe("error-message-prefix"){prefix(exception.sourceMessage,10)}
    probe("error-message-full"){exception.message}
    val expected=DataProofFiles.lines("metadata-partial-native.jsonl").associate {line->
        val values=kotlinx.serialization.json.Json.parseToJsonElement(line) as kotlinx.serialization.json.JsonArray
        (values[0] as kotlinx.serialization.json.JsonPrimitive).content to (values[1] as kotlinx.serialization.json.JsonPrimitive).content
    }
    check(actual==expected){"$actual != $expected"}
    println("${actual.size} independent lazy metadata String prefix observations PASS")
}
