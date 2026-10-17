package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File

private fun n(name:String)=GenericTypeExpression.N(name)
private fun expanded(type:GenericTypeExpression):GenericTypeExpression = if(type is GenericTypeExpression.N)GenericDefinitionData.aliases[type.name]?.let(::expanded)?:type else type
private fun declarations()=GenericDefinitionData.declarations.associateBy{it.name}
private fun input(type:GenericTypeExpression,get:()->Any?)=DeepSeqValue(DeepSeqData.resolve(type),get)
private fun default(type:GenericTypeExpression):Any? = when(val t=expanded(type)) {
    is GenericTypeExpression.L->GenericList.empty<Any?>()
    is GenericTypeExpression.T->DeepSeqTuple(t.fields.map{field->input(field){default(field)}})
    is GenericTypeExpression.A->if(t.name=="Maybe")GenericMaybe.None else DeepSeqMap.tip()
    is GenericTypeExpression.N->when(t.name) {
        "Int"->0L;"Double"->0.5;"Bool"->true;"Text"->"x";"Char"->'x'.code
        else->{val c=declarations().getValue(t.name).constructors.first();DeepSeqNode(t.name,c.name,c.fields.map{field->input(field){default(field)}})}
    }
}
private fun canDeep(type:GenericTypeExpression):Boolean = when(val t=expanded(type)) {
    is GenericTypeExpression.L,is GenericTypeExpression.T,is GenericTypeExpression.A->true
    is GenericTypeExpression.N->if(t.name in setOf("Int","Double","Bool","Text","Char"))false else declarations().getValue(t.name).constructors.any{it.fields.any(::canDeep)}
}
private fun partial(type:GenericTypeExpression,message:String="deep"):Any? = when(val t=expanded(type)) {
    is GenericTypeExpression.L->GenericList.cons({error(message)},{GenericList.empty<Any?>()})
    is GenericTypeExpression.T->DeepSeqTuple(t.fields.mapIndexed{i,field->input(field){if(i==0)error(message)else default(field)}})
    is GenericTypeExpression.A->when(t.name) {
        "Maybe"->GenericMaybe.Some<Any?>{error(message)}
        else->DeepSeqMap.bin({1L},DeepSeqValue(DeepSeqData.text){"k"},input(t.arguments[1]){error(message)},{DeepSeqMap.tip()},{DeepSeqMap.tip()})
    }
    is GenericTypeExpression.N->{
        val c=declarations().getValue(t.name).constructors.first{it.fields.any(::canDeep)}
        val i=c.fields.indexOfFirst(::canDeep)
        DeepSeqNode(t.name,c.name,c.fields.mapIndexed{j,field->input(field){if(i==j)partial(field,message)else default(field)}})
    }
}
internal fun deepSeqDemandExecute(request:JsonObject):JsonObject = try {
    val kind=request.getValue("kind").jsonPrimitive.content
    val mode=request.getValue("mode").jsonPrimitive.content
    val value=DeepSeqValue(DeepSeqData.named(kind)) {
        if(mode=="root")error("root")
        val constructor=request.getValue("constructor").jsonPrimitive.content
        val c=declarations().getValue(kind).constructors.single{it.name==constructor}
        val i=request.getValue("field").jsonPrimitive.int
        val second=request["secondField"]?.jsonPrimitive?.int
        DeepSeqNode(kind,constructor,c.fields.mapIndexed{j,t->input(t){
            when {
                j==i->when(mode){"field"->error("field");"deep"->partial(t);"pairRoot"->error("first");else->partial(t,"first")}
                j==second->if(mode=="pairDeep")partial(t,"second")else error("second")
                else->default(t)
            }
        }})
    }
    when(request.getValue("operation").jsonPrimitive.content) {
        "rnf"->PandocDeepSeq.rnfLazy(value.dictionary){value.value()}()
        "rwhnf"->PandocDeepSeq.rwhnfLazy(value)()
        else->error("operation")
    }
    buildJsonObject{put("result","()")}
} catch(e:Exception){buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.name)}}
fun main(){
    val root=File("tools/haskell-types-deepseq-reference")
    val inputs=Json.parseToJsonElement(File(root,"demand-inputs.json").readText()).jsonArray
    val expected=File(root,"demand-native.jsonl").readLines().chunked(2).associate{(name,value)->Json.parseToJsonElement(name).jsonPrimitive.content to Json.parseToJsonElement(value)}
    for(input in inputs){val r=input.jsonObject;val name=r.getValue("name").jsonPrimitive.content;val actual=deepSeqDemandExecute(r);check(actual==expected.getValue(name)){"$name expected${expected[name]} actual$actual"}}
    println("${inputs.size} original NFData demand assertions PASS")
}
