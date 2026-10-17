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
internal fun genericRepDemandExecute(request:JsonObject):JsonObject = try {
    val kind=request.getValue("kind").jsonPrimitive.content
    val operation=request.getValue("operation").jsonPrimitive.content
    val many=kind.startsWith("P.Many")
    val element=when {
        "Int->Int" in kind->GenericType("GHC.Prim","FUN",listOf(GenericDatas.int.type,GenericDatas.int.type),"ghc-prim")
        "T.Text" in kind && "Maybe" !in kind->GenericDatas.text.type
        "Bool" in kind->GenericDatas.boolean.type
        "Maybe" in kind->GenericDatas.tuple2(GenericDatas.int,GenericDatas.maybe(GenericDatas.text)).type
        else->GenericDatas.int.type
    }
    val dictionary=if(many)GenericRepData.many(element)else GenericRepData.definition(kind)
    val root:()->GenericRepSource={
        val mode=request.getValue("mode").jsonPrimitive.content
        if(mode=="root")error("root")
        if(many)GenericRepData.manySource(element,GenericRepData.manySequence(element){Many.singleton<Any?>{error("element")}})
        else {
            val constructor=request.getValue("constructor").jsonPrimitive.content
            val c=declarations().getValue(kind).constructors.single{it.name==constructor}
            val i=request.getValue("field").jsonPrimitive.int
            val second=request["secondField"]?.jsonPrimitive?.int
            GenericRepData.source(kind,constructor,c.fields.mapIndexed{j,t->GenericRepField(GenericDefinitionData.resolve(t).type){
                when {
                    j==i->when(mode){"field"->error("field");"deep"->partial(t);"pairRoot"->error("first");else->partial(t,"first")}
                    j==second->if(mode=="pairDeep")partial(t,"second")else error("second")
                    else->default(t)
                }
            }})
        }
    }
    val result=when(operation){
        "from"->{dictionary.from(root).whnf();"7"}
        "toFrom"->{dictionary.to(dictionary.from(root))();"7"}
        "toRoot"->{dictionary.to(GenericRepValue(dictionary.representation){error("representation")})();"7"}
        else->{
            val rep=dictionary.from{error("input")}
            val metadata=(rep.type as GenericRepType.Metadata).information as GenericRepMetadata.Datatype
            when(operation){"datatypeName"->AstShow.show(metadata.name);"moduleName"->AstShow.show(metadata.module);"packageName"->AstShow.show(metadata.packageName);"isNewtype"->if(metadata.newtype)"True"else"False";else->error("operation")}
        }
    }
    buildJsonObject{put("result",result)}
} catch(e:Exception){buildJsonObject{put("exception",e.message?.lineSequence()?.firstOrNull()?:e.javaClass.name)}}
fun main(){
    val root=File("tools/haskell-types-generic-rep-reference")
    val inputs=Json.parseToJsonElement(File(root,"demand-inputs.json").readText()).jsonArray
    val expected=File(root,"demand-native.jsonl").readLines().chunked(2).associate{(name,value)->Json.parseToJsonElement(name).jsonPrimitive.content to Json.parseToJsonElement(value)}
    for(input in inputs){val r=input.jsonObject;val name=r.getValue("name").jsonPrimitive.content;val actual=genericRepDemandExecute(r);check(actual==expected.getValue(name)){"$name expected${expected[name]} actual$actual"}}
    println("${inputs.size} original derived Generic demand/metadata assertions PASS")
}
