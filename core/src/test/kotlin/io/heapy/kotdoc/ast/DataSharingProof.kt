package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private class SharingTag
private class SharingConst<A>(val text:DataThunk<String>):DataKind<SharingTag,A>{override fun forceGenericRepRoot(){text()}}
private fun <A> attempt(value:()->A){try{value()}catch(_:Throwable){}}
private class OutputDictionary<A>(name:String,private val output:()->A):DataDictionary<A>(DataRepresentation(GenericType("Test",name))) {
 override fun <F> gunfold(unfold:DataUnfold<F>,constructor:DataThunk<DataConstr>):DataKind<F,A> = error("unused")
 override fun toConstr(value:DataThunk<A>):DataConstr=error("unused")
 override fun dataTypeOf(value:DataThunk<A>):DataTypeInfo=error("unused")
 override fun gmapT(transform:DataTransform,value:DataThunk<A>):A=output()
 override fun <F> gmapM(monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>):DataKind<F,A> = monad.pure(DataThunk(output))
 override fun <F> gmapMp(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>):DataKind<F,A> = monad.pure(DataThunk(output))
 override fun <F> gmapMo(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>):DataKind<F,A> = monad.pure(DataThunk(output))
}
fun main(){
 val expected=DataProofFiles.lines("sharing-native.jsonl").map{Json.parseToJsonElement(it).jsonArray}.associate{it[0].jsonPrimitive.content to it[1].jsonPrimitive.content}
 var checked=0
 fun probe(name:String,body:()->String){val got=try{body()}catch(_:Throwable){"error"};check(got==expected.getValue(name)){"$name: $got != ${expected[name]}"};checked++}
 val integer=DataDictionaries.int();val list=DataDictionaries.list(integer.evidence)
 val query=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)="child"}
 probe("shared-input-success"){
  var calls=0;val app=PandocDataClass.gmapQ(list,query){calls++;GenericList.view(listOf(1L,2L))}
  repeat(2){attempt{app().first()}};calls.toString()
 }
 probe("shared-input-failure"){
  var calls=0;val app=PandocDataClass.gmapQ(list,query){calls++;error("input")}
  repeat(2){attempt{app().first()}};calls.toString()
 }
 fun countedQuery(fail:Boolean,counter:()->Unit)=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):String {counter();if(fail)error("query ${evidence.representation.type}");return evidence.representation.type.name}}
 probe("shared-query-head-twice"){
  var calls=0;val app=PandocDataClass.gmapQ(list,countedQuery(false){calls++}){GenericList.view(listOf(1L,2L))}
  repeat(2){attempt{app().first()}};calls.toString()
 }
 probe("shared-query-head-and-tail"){
  var calls=0;val app=PandocDataClass.gmapQ(list,countedQuery(false){calls++}){GenericList.view(listOf(1L,2L))}
  attempt{app().first()};attempt{app()[1]};calls.toString()
 }
 probe("shared-query-failure"){
  var calls=0;val app=PandocDataClass.gmapQ(list,countedQuery(true){calls++}){GenericList.view(listOf(1L))}
  repeat(2){attempt{app().first()}};calls.toString()
 }
 probe("unused-query-function-factory"){
  var calls=0;val callback=DataCallbacks.query<String>{calls++;error("query function")}
  attempt{PandocDataClass.gmapQ(DataDictionaries.boolean(),callback){true}()};calls.toString()
 }
 val inject=object:DataInject<SharingTag>{override fun <A> inject(value:DataThunk<A>):DataKind<SharingTag,A> = SharingConst(DataThunk{"zero"})}
 val apply=object:DataFoldApply<SharingTag>{override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<SharingTag,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<SharingTag,B> = SharingConst(DataThunk{"field"})}
 probe("unused-k-function-factory"){
  var calls=0;val fold=DataCallbacks.fold({inject},{calls++;error("k function")})
  attempt{(PandocDataClass.gfoldl(list,fold){GenericList.empty()}() as SharingConst<*>).text()};calls.toString()
 }
 probe("unused-z-function-factory"){
  var calls=0;val fold=DataCallbacks.fold({calls++;error("z function")},{apply})
  val format=DataRegistry().dictionary(GenericDefinitionData.named("Format").type)
  attempt{(PandocDataClass.gfoldl(format,fold){error("value")}() as SharingConst<*>).text()};calls.toString()
 }
 val primitive=object:DataUnfold<SharingTag>{override fun <A> inject(value:DataThunk<A>):DataKind<SharingTag,A> = SharingConst(DataThunk{"zero"});override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<SharingTag,DataFunction<D,B>>>):DataKind<SharingTag,B> = error("field")}
 probe("wrong-tag-ignores-Constr-name"){
  var calls=0;val type=DataTypeInfo({"Foreign"},{DataTypeRep.Algebraic(DataThunk{GenericList.empty()})})
  val c=DataConstr({DataConstrRep.Algebraic(1)},{calls++;error("name")},{GenericList.empty()},{DataFixity.Prefix},{type})
  attempt{PandocDataClass.gunfold(integer,primitive){c}()};calls.toString()
 }
 probe("constrIndex-ignores-datatype-name"){
  var calls=0;val type=DataTypeInfo({calls++;error("name")},{DataTypeRep.Integral})
  val c=DataConstr({DataConstrRep.Integral(java.math.BigInteger.ONE)},{"1"},{error("fields")},{error("fixity")},{type})
  attempt{c.index};calls.toString()
 }
 probe("dataTypeConstrs-ignores-name"){
  var calls=0;val type=DataTypeInfo({calls++;error("name")},{DataTypeRep.Integral})
  attempt{type.constructors()};calls.toString()
 }
 val identity=object:DataTransform{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=value()}
 val monadicIdentity=object:DataMonadicTransform<DataIdentity.Tag>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=DataIdentity.pure(value)}
 val monadicMaybe=object:DataMonadicTransform<DataProofMaybe.Tag>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=DataProofMaybe.pure(value)}
 val outList=OutputDictionary("OutList"){GenericList.defer<Long>{error("result root")}}
 probe("returned-list-gmapT-root"){PandocDataClass.gmapT(outList,identity){GenericList.empty()}();"returned"}
 probe("returned-list-gmapM-Identity-root"){PandocDataClass.gmapM(outList,DataIdentity,monadicIdentity){GenericList.empty()}();"returned"}
 fun maybeResult(method:Int):GenericMaybe<GenericList<Long>> = DataProofMaybe.run(when(method){
  0->PandocDataClass.gmapM(outList,DataProofMaybe,monadicMaybe){GenericList.empty()}()
  1->PandocDataClass.gmapMp(outList,DataProofMaybe,monadicMaybe){GenericList.empty()}()
  else->PandocDataClass.gmapMo(outList,DataProofMaybe,monadicMaybe){GenericList.empty()}()
 })
 for((method,name)in listOf("gmapM","gmapMp","gmapMo").withIndex()){
  probe("returned-list-$name-Maybe-outer"){maybeResult(method);"outer"}
  probe("returned-list-$name-Maybe-inner"){(maybeResult(method) as GenericMaybe.Some).value;"returned"}
 }
 probe("returned-Seq-newtype-root"){
  val dict=OutputDictionary("OutSeq"){ManySequence.deferred<Long>{error("result root")}}
  PandocDataClass.gmapT(dict,identity){ManySequence.empty()}();"returned"
 }
 probe("returned-Map-newtype-root"){
  val dict=OutputDictionary("OutMap"){DeepSeqMap.defer{error("result root")}}
  PandocDataClass.gmapT(dict,identity){DeepSeqMap.tip()}();"returned"
 }
 probe("returned-StrictData-field"){
  val dict=OutputDictionary("OutStrict"){DataNode("OutStrict",0,listOf(DataThunk{error("result field")}))}
  PandocDataClass.gmapT(dict,identity){DataNode("OutStrict",0,listOf(DataThunk{GenericList.empty<Long>()}))}();"returned"
 }
 val maybe=DataDictionaries.maybe(integer.evidence)
 val tupleQuery=object:DataQuery<GenericTuple2<Long,Long>>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=GenericTuple2<Long,Long>({error("first")},{error("second")})}
 probe("returned-tuple-root-ignores-fields"){PandocDataClass.gmapQi(maybe,{0L},tupleQuery){GenericMaybe.Some{1L}}();"tuple"}
 probe("returned-tuple-first-forces-field"){PandocDataClass.gmapQi(maybe,{0L},tupleQuery){GenericMaybe.Some{1L}}().first;"returned"}
 val functionQuery=object:DataQuery<(Long)->Long>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):(Long)->Long = {error("body")}}
 probe("returned-function-root-ignores-body"){PandocDataClass.gmapQi(maybe,{0L},functionQuery){GenericMaybe.Some{1L}}();"function"}
 probe("returned-function-application-forces-body"){PandocDataClass.gmapQi(maybe,{0L},functionQuery){GenericMaybe.Some{1L}}()(1L);"returned"}
 check(checked==expected.size)
 var factories=0
 val factory:()->DataDictionary<Long> = {factories++;error("dictionary")}
 val deferred=PandocDataClass.dataTypeOf(factory){error("unused input")}
 check(factories==0)
 repeat(2){attempt{deferred()}}
 check(factories==1)
 println("$checked independent sharing/returned-carrier observations PASS; 2 JVM dictionary-factory adapter assertions PASS")
}
