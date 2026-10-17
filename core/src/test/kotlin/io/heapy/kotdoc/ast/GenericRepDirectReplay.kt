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
internal fun genericRepDirectExecute(request:JsonObject):JsonObject=try {
    val kind=request.getValue("kind").jsonPrimitive.content
    val constructor=request.getValue("constructor").jsonPrimitive.content
    val spec=declarations().getValue(kind).constructors.single{it.name==constructor}
    val mode=request.getValue("mode").jsonPrimitive.content
    val changed=request["field"]?.jsonPrimitive?.int
    val dictionary=GenericRepData.definition(kind)
    fun decode(type:GenericRepType,json:JsonObject):GenericRepValue=GenericRepValue(type){
        when(val tag=json.getValue("tag").jsonPrimitive.content){
            "error"->error(json.getValue("message").jsonPrimitive.content)
            "U1"->GenericRepLayer.Unit
            "M1"->GenericRepLayer.Metadata(decode((type as GenericRepType.Metadata).child,json.getValue("child").jsonObject))
            "L1","R1"->{type as GenericRepType.Sum;GenericRepLayer.Sum(tag=="R1",decode(if(tag=="R1")type.right else type.left,json.getValue("child").jsonObject))}
            "Product"->{type as GenericRepType.Product;GenericRepLayer.Product(decode(type.left,json.getValue("left").jsonObject),decode(type.right,json.getValue("right").jsonObject))}
            "K1"->{val index=json.getValue("index").jsonPrimitive.int;val field=spec.fields[index]
                GenericRepLayer.Field(GenericRepField((type as GenericRepType.Field).type){when{
                    mode=="race"&&index==0->error("first")
                    index==changed->if(mode=="field")error("field")else partial(field)
                    else->default(field)
                }})
            }
            else->error("tag $tag")
        }
    }
    dictionary.to(decode(dictionary.representation,request.getValue("tree").jsonObject))()
    buildJsonObject{put("result","7")}
} catch(e:Exception){buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.name)}}
fun main(){
    val root=File("tools/haskell-types-generic-rep-reference")
    val inputs=Json.parseToJsonElement(File(root,"direct-inputs.json").readText()).jsonArray
    val expected=File(root,"direct-native.jsonl").readLines().chunked(2).associate{(name,value)->Json.parseToJsonElement(name).jsonPrimitive.content to Json.parseToJsonElement(value)}
    for(input in inputs){val r=input.jsonObject;val name=r.getValue("name").jsonPrimitive.content;val actual=genericRepDirectExecute(r);check(actual==expected.getValue(name)){"$name expected${expected[name]} actual$actual"}}
    println("${inputs.size} original direct partial Rep assertions PASS")
}
