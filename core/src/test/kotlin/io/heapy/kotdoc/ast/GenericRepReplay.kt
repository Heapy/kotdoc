package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
@Suppress("UNCHECKED_CAST")
private fun dictionary(type:GenericType):GenericData<Any?> {
 val args=type.arguments.map(::dictionary)
 return (when(type.name){
 "[]"->GenericDatas.list(args.single());"(,)"->GenericDatas.tuple2(args[0],args[1]);"(,,)"->GenericDatas.tuple3(args[0],args[1],args[2])
 "Maybe"->GenericDatas.maybe(args.single());"Seq"->GenericDatas.sequence(args.single())
 "Map"->GenericDatas.map(args[0],args[1],Comparator<Any?>{a,b->UnicodeTextOrder.compare(a as String,b as String)})
 else->GenericDefinitionData.named(type.name)
 }) as GenericData<Any?>
}
private fun typeName(type:GenericType):String=when(type.name){
 "[]"->"[${typeName(type.arguments.single())}]"
 "(,)","(,,)"->type.arguments.joinToString(",","(",")",transform=::typeName)
 else->type.name+type.arguments.joinToString(""){" "+typeName(it).let{s->if(it.arguments.isNotEmpty()&&it.name!="[]"&&it.name!="(,)"&&it.name!="(,,)")"($s)"else s}}
}
private fun shape(value:GenericRepValue):JsonObject=buildJsonObject {
 when(val layer=value.layer()){
 GenericRepLayer.Unit->put("node","U1")
 is GenericRepLayer.Field->{put("node","K1");put("type",typeName(layer.value.type));put("value",repShow(GenericValue(dictionary(layer.value.type)){layer.value.value()}))}
 is GenericRepLayer.Product->{put("node","Product");put("left",shape(layer.left));put("right",shape(layer.right))}
 is GenericRepLayer.Sum->{put("node",if(layer.right)"R1"else"L1");put("child",shape(layer.child))}
 is GenericRepLayer.Metadata->{
  when(val m=(value.type as GenericRepType.Metadata).information){
   is GenericRepMetadata.Datatype->{put("node","D1");put("name",m.name);put("module",m.module);put("package",m.packageName);put("newtype",m.newtype)}
   is GenericRepMetadata.Constructor->{put("node","C1");put("name",m.name);put("fixity",m.fixity);put("record",m.record)}
   is GenericRepMetadata.Selector->{put("node","S1");put("name",m.name);put("unpackedness",m.unpackedness);put("strictness",m.strictness);put("decided",m.decidedStrictness)}
  };put("child",shape(layer.child))
 }
 }
}
private val names=mapOf("style" to "ListNumberStyle","delim" to "ListNumberDelim","quote" to "QuoteType","math" to "MathType")
internal fun genericRepFiniteCase(index:Int){
 val base=File("tools/haskell-types-generic-rep-reference")
 val inputs=GenericRepFixtures.text("requests.jsonl").lines().filter(String::isNotEmpty).map{Json.parseToJsonElement(it).jsonObject}
 val expected=GenericRepFixtures.text("responses.jsonl").lines().filter(String::isNotEmpty).map{Json.parseToJsonElement(it).jsonObject}
 val request=inputs[index];val want=expected[index]
 run {
  val kind=request.getValue("kind").jsonPrimitive.content;val text=request.getValue("input").jsonPrimitive.content
  val name=names[kind]?:kind.replaceFirstChar{it.uppercase()}
  val many=kind in listOf("inlines","blocks")
  val element=if(kind=="inlines")GenericDefinitionData.named("Inline")else GenericDefinitionData.named("Block")
  val dict=if(many)GenericRepData.many(element.type)else GenericRepData.definition(name)
  val read=PandocRead::class.java.getMethod("get"+if(many)kind.replaceFirstChar{it.uppercase()}else name).invoke(PandocRead) as ReadDictionary<Any?>
  val ast=read.read(text)
  val node=if(many){
   @Suppress("UNCHECKED_CAST") val bridge=(if(kind=="inlines")GenericAstData.inlines else GenericAstData.blocks)as GenericData<Any?>
   val seq=GenericValue(bridge){ast}.cast(GenericDatas.many(element))
   GenericRepData.manySource(element.type,GenericRepField(GenericDatas.sequence(element).type){seq})
  } else {
   val n=GenericValue(GenericAstData.named<Any?>(name)){ast}.cast(GenericDefinitionData.named(name)) as GenericNode
   GenericRepData.source(name,n.constructor,n.fields.map{f->GenericRepField(f.data.type){f.value()}})
  }
  val rep=dict.from{node};val actual=shape(rep);check(actual==want.getValue("from")){"$request\n$actual\n${want["from"]}"}
  val result=dict.to(rep)();check(shape(dict.from{result})==want.getValue("fromToFrom")){"fromToFrom $request"}
  val shown=if(many)repShow(GenericValue(GenericDatas.many(element)){result.fields.single().value() as GenericSequence<Any?>}) else {
   val constructor=GenericDefinitionData.declarations.single{it.name==name}.constructors.single{it.name==result.constructor}
   val canonical=GenericDefinitionData.node(name,result.constructor,result.fields.mapIndexed{i,f->GenericValue(GenericDefinitionData.resolve(constructor.fields[i])){f.value()}})
   repShow(GenericValue(GenericDefinitionData.named(name)){canonical})
  }
  check(shown==want.getValue("toFrom").jsonPrimitive.content){"toFrom $request $shown"}
  val bridge=if(many){
   @Suppress("UNCHECKED_CAST") val data=(if(kind=="inlines")GenericAstData.inlines else GenericAstData.blocks)as GenericData<Any?>
   GenericRepData.astMany(element,data)
  }else GenericRepData.ast<Any?>(name)
  val astRep=bridge.from{ast};check(shape(astRep)==want.getValue("from")){"AST from $request"}
  val roundtrip=bridge.to(astRep)();check(shape(bridge.from{roundtrip})==want.getValue("fromToFrom")){"AST to $request"}

 }

}

fun main(){repeat(85,::genericRepFiniteCase);println("85 full source and typed representation/metadata/from-to original comparisons PASS")}
