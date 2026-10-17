package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File

private val registry=DataRegistry()
private fun sourceType(shape:JsonObject):GenericType {
 val t=shape.getValue("typeInfo").jsonObject
 fun read(t:JsonObject):GenericType {
  val name=t.getValue("name").jsonPrimitive.content.let{when(it){"List"->"[]";"Tuple2"->"(,)";"Tuple3"->"(,,)";else->it}}
  val module=t.getValue("module").jsonPrimitive.content
  val library=when {module.startsWith("Text.Pandoc")->"pandoc-types";module.startsWith("Data.Map")||module.startsWith("Data.Sequence")->"containers";module.startsWith("Data.Text")->"text";module=="GHC.Internal.Maybe"->"ghc-internal";else->"ghc-prim"}
  return GenericType(module,name,t.getValue("arguments").jsonArray.map{read(it.jsonObject)},library)
 }
 return read(t)
}
private fun typeJson(type:GenericType):JsonObject=buildJsonObject {
 put("module",type.module);put("name",type.tyConName);put("arguments",JsonArray(type.arguments.map(::typeJson)))
}
@Suppress("UNCHECKED_CAST")
private fun decode(shape:JsonObject):Any? {
 val type=sourceType(shape);val fields=shape.getValue("fields").jsonArray
 fun child(i:Int)=decode(fields[i].jsonObject)
 return when(type.name) {
  "Int"->shape.getValue("scalar").jsonPrimitive.long
  "Double"->shape.getValue("scalar").jsonPrimitive.content.toDouble()
  "Char"->shape.getValue("scalar").jsonPrimitive.int
  "Text"->shape.getValue("scalar").jsonPrimitive.content
  "Bool"->shape.getValue("constructor").jsonPrimitive.content=="True"
  "[]"->if(fields.isEmpty())GenericList.empty<Any?>()else GenericList.cons({child(0)}){child(1) as GenericList<Any?>}
  "(,)"->GenericTuple2({child(0)},{child(1)})
  "(,,)"->GenericTuple3({child(0)},{child(1)},{child(2)})
  "Maybe"->if(fields.isEmpty())GenericMaybe.None else GenericMaybe.Some{child(0)}
  "Seq"->if(fields.isEmpty())ManySequence.empty<Any?>()else ManySequence.fromTree{ManyTrees.cons(ManyCell.Leaf{child(0)},(child(1) as ManySequence<Any?>).tree.get())}
  "Many"->Many(child(0) as ManySequence<Any?>)
  "Map"->{val entries=child(0) as GenericList<GenericTuple2<String,Any?>>;MetadataMap.fromEntries(entries.map{pair->pair.first to {pair.second}})}
  else->{val declaration=GenericDefinitionData.declarations.single{it.name==type.name};val constructor=declaration.constructors.indexOfFirst{it.name==shape.getValue("constructor").jsonPrimitive.content};DataNode(type.name,constructor,fields.indices.map{i->DataThunk{child(i)}})}
 }
}
@Suppress("UNCHECKED_CAST")
private fun genericDictionary(type:GenericType):GenericData<Any?> {
 val args=type.arguments.map(::genericDictionary)
 return (when(type.name) {
  "[]"->GenericDatas.list(args[0]);"(,)"->GenericDatas.tuple2(args[0],args[1]);"(,,)"->GenericDatas.tuple3(args[0],args[1],args[2])
  "Maybe"->GenericDatas.maybe(args[0]);"Seq"->GenericDatas.sequence(args[0]);"Many"->GenericDatas.many(args[0])
  "Map"->GenericDatas.map(args[0],args[1],Comparator<Any?>{a,b->UnicodeTextOrder.compare(a as String,b as String)})
  else->GenericDefinitionData.named(type.name)
 }) as GenericData<Any?>
}
@Suppress("UNCHECKED_CAST")
private fun genericValue(type:GenericType,value:()->Any?):GenericValue<Any?> = GenericValue(genericDictionary(type)) {
 fun convert(i:Int,v:()->Any?)=genericValue(type.arguments[i],v).value()
 when(type.name) {
  "[]"->{
   fun loop(input:GenericList<Any?>):GenericList<Any?> = GenericList.defer {
    when(val step=input.step()) {
     GenericList.Step.Nil->GenericList.empty()
     is GenericList.Step.Cons->GenericList.cons({convert(0,step::head)}){loop(step.tail())}
    }
   }
   loop(value() as GenericList<Any?>)
  }
  "(,)"->{val p=value() as GenericTuple2<Any?,Any?>;GenericTuple2({convert(0){p.first}},{convert(1){p.second}})}
  "(,,)"->{val p=value() as GenericTuple3<Any?,Any?,Any?>;GenericTuple3({convert(0){p.first}},{convert(1){p.second}},{convert(2){p.third}})}
  "Maybe"->when(val v=value() as GenericMaybe<Any?>){GenericMaybe.None->v;is GenericMaybe.Some->GenericMaybe.Some{convert(0){v.value}}}
  "Seq"->GenericSequence.fromList((value() as ManySequence<Any?>).map{convert(0){it}})
  "Many"->GenericSequence.fromList((value() as Many<Any?>).map{convert(0){it}})
  "Map"->MetadataMap.fromMap(value() as Map<String,Any?>).mapped{v->convert(1,v)}
  "Int","Double","Char","Text","Bool"->value()
  else->{val node=value() as DataNode;val spec=GenericDefinitionData.declarations.single{it.name==type.name}.constructors[node.constructor]
   GenericDefinitionData.node(type.name,spec.name,spec.fields.mapIndexed{i,expression->genericValue(GenericDefinitionData.resolve(expression).type,node.fields[i])})}
 }
}
private class CountTag
private class Count<A>(val count:DataThunk<Int>):DataKind<CountTag,A>{override fun forceGenericRepRoot(){count()}}
private val unfold=object:DataUnfold<CountTag> {
 override fun <A> inject(value:DataThunk<A>):DataKind<CountTag,A> = Count(DataThunk{0})
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<CountTag,DataFunction<D,B>>>):DataKind<CountTag,B> = Count(DataThunk{(function() as Count<*>).count()+1})
}
private class FieldsTag
private class Fields<A>(val values:DataThunk<List<JsonElement>>):DataKind<FieldsTag,A>{override fun forceGenericRepRoot(){values()}}
private val folder=object:DataFold<FieldsTag> {
 override fun <A> inject(value:DataThunk<A>):DataKind<FieldsTag,A> = Fields(DataThunk{emptyList()})
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<FieldsTag,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<FieldsTag,B> = Fields(DataThunk{(function() as Fields<*>).values()+typeJson(evidence.representation.type)})
}
private val types=object:DataQuery<JsonElement> {override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):JsonElement=typeJson(evidence.representation.type)}
private val stateMonad=DataProofState<List<JsonElement>>()
private val monadicState=object:DataMonadicTransform<DataProofState.Tag<List<JsonElement>>> {
 override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<DataProofState.Tag<List<JsonElement>>,A> = stateMonad.action{state->GenericTuple2(value){state+typeJson(evidence.representation.type)}}
}
private val monadicIdentity=object:DataMonadicTransform<DataIdentity.Tag> {
 override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=DataIdentity.pure(value)
}
private val monadicMaybe=object:DataMonadicTransform<DataProofMaybe.Tag> {
 override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=DataProofMaybe.pure(value)
}
private val monadicList=object:DataMonadicTransform<DataProofList.Tag> {
 override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=DataProofList.pure(value)
}
private val castList=object:DataCast1<CountTag>{override fun <A> value(argument:DataEvidence<A>):DataKind<CountTag,Any?> = error("unused cast payload")}
private val castMap=object:DataCast2<CountTag>{override fun <A,B> value(left:DataEvidence<A>,right:DataEvidence<B>):DataKind<CountTag,Any?> = error("unused cast payload")}
private val identity=object:DataTransform {override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A=value()}
fun main() {
 val source=DataProofFiles.lines("constructor-responses.jsonl").map{Json.parseToJsonElement(it).jsonObject.getValue("inputShape").jsonObject}
 val expected=DataProofFiles.lines("finite-native.jsonl").map{Json.parseToJsonElement(it).jsonObject}
 var comparisons=0
 for((index,shape)in source.withIndex()) {
  val type=sourceType(shape);val dictionary=registry.dictionary(type);val value=DataThunk{decode(shape)};val want=expected[index]
  fun equal(name:String,actual:JsonElement){check(actual==want.getValue(name)){"row $index $name: $actual != ${want[name]}"};comparisons++}
  equal("gfoldl",JsonArray((PandocDataClass.gfoldl(dictionary,folder,value)() as Fields<*>).values()))
  equal("toConstr",JsonPrimitive(PandocDataClass.toConstr(dictionary,value)().name))
  equal("dataTypeOf",JsonPrimitive(PandocDataClass.dataTypeOf(dictionary,value)().name))
  equal("gunfold",JsonPrimitive((PandocDataClass.gunfold(dictionary,unfold){dictionary.toConstr(value)}() as Count<*>).count()))
  equal("gmapQ",JsonArray(PandocDataClass.gmapQ(dictionary,types,value)().toList()))
  equal("gmapQl",JsonArray(PandocDataClass.gmapQl(dictionary,{left,right->left()+right()},{emptyList<JsonElement>()},types,value)()))
  equal("gmapQr",JsonArray(PandocDataClass.gmapQr(dictionary,{left,right->listOf(left())+right()},{emptyList<JsonElement>()},types,value)()))
  val arity=want.getValue("gmapQ").jsonArray.size
  equal("gmapQi",JsonArray((0 until arity).map{i->PandocDataClass.gmapQi(dictionary,{i.toLong()},types,value)()}))
  equal("gmapT",JsonPrimitive(genericShow(genericValue(type){PandocDataClass.gmapT(dictionary,identity,value)()})))
  equal("dataCast1-list",JsonPrimitive(PandocDataClass.dataCast1(dictionary,{GenericType("GHC.Types","[]",library="ghc-prim")},castList)() is GenericMaybe.Some))
  equal("dataCast1-many",JsonPrimitive(PandocDataClass.dataCast1(dictionary,{GenericType("Text.Pandoc.Builder","Many")},castList)() is GenericMaybe.Some))
  equal("dataCast2-map",JsonPrimitive(PandocDataClass.dataCast2(dictionary,{GenericType("Data.Map.Internal","Map",library="containers")},castMap)() is GenericMaybe.Some))
  equal("gmapM-Identity",JsonPrimitive(genericShow(genericValue(type){DataIdentity.run(PandocDataClass.gmapM(dictionary,DataIdentity,monadicIdentity,value)())})))
  val state=stateMonad.run(PandocDataClass.gmapM(dictionary,stateMonad,monadicState,value)(),emptyList())
  equal("gmapM-State",JsonPrimitive(genericShow(genericValue(type){state.first})))
  equal("gmapM-trace",JsonArray(state.second))
  fun showMaybe(result:GenericMaybe<Any?>):String=when(result){GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just "+genericShow(genericValue(type){result.value},11)}
  equal("gmapMp-Maybe",JsonPrimitive(showMaybe(DataProofMaybe.run(PandocDataClass.gmapMp(dictionary,DataProofMaybe,monadicMaybe,value)()))))
  equal("gmapMo-Maybe",JsonPrimitive(showMaybe(DataProofMaybe.run(PandocDataClass.gmapMo(dictionary,DataProofMaybe,monadicMaybe,value)()))))
  equal("gmapMp-List-count",JsonPrimitive(DataProofList.run(PandocDataClass.gmapMp(dictionary,DataProofList,monadicList,value)()).count()))
  equal("gmapMo-List-count",JsonPrimitive(DataProofList.run(PandocDataClass.gmapMo(dictionary,DataProofList,monadicList,value)()).count()))
 }
 println("$comparisons original Data method comparisons PASS across ${source.size} inputs")
}
