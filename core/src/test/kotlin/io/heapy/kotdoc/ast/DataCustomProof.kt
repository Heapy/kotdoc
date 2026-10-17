package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private class CustomTag
private class CustomConst<A>(val value:DataThunk<String>):DataKind<CustomTag,A>{override fun forceGenericRepRoot(){value()}}
private val customFold=object:DataFold<CustomTag> {
 override fun <A> inject(value:DataThunk<A>):DataKind<CustomTag,A> = CustomConst(DataThunk{"zero"})
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<CustomTag,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<CustomTag,B> = CustomConst(DataThunk{"field"})
}
private val customUnfold=object:DataUnfold<CustomTag> {
 override fun <A> inject(value:DataThunk<A>):DataKind<CustomTag,A> = CustomConst(DataThunk{"zero"})
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<CustomTag,DataFunction<D,B>>>):DataKind<CustomTag,B> = error("unused step")
}
private fun metadata(name:String):DataTypeInfo {
 lateinit var datatype:DataTypeInfo
 val constructor=DataConstr({DataConstrRep.Algebraic(1)},{name},{GenericList.empty()},{DataFixity.Prefix},{datatype})
 datatype=DataTypeInfo({"Test.$name"},{DataTypeRep.Algebraic(DataThunk{GenericList.cons({constructor}){GenericList.empty()}})})
 return datatype
}
private val customType=metadata("Custom")
private val customDictionary=object:DataDictionary<GenericTuple2<Long,Long>>(DataRepresentation(GenericType("Test","Custom"))) {
 override fun <F> gfoldl(fold:DataFold<F>,value:DataThunk<GenericTuple2<Long,Long>>):DataKind<F,GenericTuple2<Long,Long>> = fold.inject(DataThunk{GenericTuple2({7L},{9L})})
 override fun <F> gunfold(unfold:DataUnfold<F>,constructor:DataThunk<DataConstr>):DataKind<F,GenericTuple2<Long,Long>> = unfold.inject(DataThunk{GenericTuple2({11L},{13L})})
 override fun toConstr(value:DataThunk<GenericTuple2<Long,Long>>)=customType.constructors().first()
 override fun dataTypeOf(value:DataThunk<GenericTuple2<Long,Long>>)=customType
 override fun gmapT(transform:DataTransform,value:DataThunk<GenericTuple2<Long,Long>>)=GenericTuple2({101L},{102L})
 override fun <R,P> gmapQl(combine:(DataThunk<R>,DataThunk<P>)->R,initial:DataThunk<R>,query:DataQuery<P>,value:DataThunk<GenericTuple2<Long,Long>>):R=initial()
 override fun <R,P> gmapQr(combine:(DataThunk<P>,DataThunk<R>)->R,initial:DataThunk<R>,query:DataQuery<P>,value:DataThunk<GenericTuple2<Long,Long>>):R=initial()
 override fun <R> gmapQi(index:DataThunk<Long>,query:DataQuery<R>,value:DataThunk<GenericTuple2<Long,Long>>):R=error("custom index")
 override fun <F> gmapM(monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:DataThunk<GenericTuple2<Long,Long>>):DataKind<F,GenericTuple2<Long,Long>> = monad.pure(DataThunk{GenericTuple2({103L},{104L})})
 override fun <F> gmapMp(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<GenericTuple2<Long,Long>>):DataKind<F,GenericTuple2<Long,Long>> = monad.zero()
 override fun <F> gmapMo(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<GenericTuple2<Long,Long>>):DataKind<F,GenericTuple2<Long,Long>> = monad.zero()
}
private val query=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)="child"}
private val badQuery=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):String=error("unused query")}
private val badTransform=object:DataTransform{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A=error("unused transform")}
private val badIdentity=object:DataMonadicTransform<DataIdentity.Tag>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<DataIdentity.Tag,A> = error("unused transform")}
private val delayedIdentity=object:DataMonadicTransform<DataIdentity.Tag>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<DataIdentity.Tag,A> = DataIdentity.pure(DataThunk{error("field")})}
private val badMaybe=object:DataMonadicTransform<DataProofMaybe.Tag>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<DataProofMaybe.Tag,A> = error("unused transform")}
private val failedMaybe=object:DataMonadicTransform<DataProofMaybe.Tag>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<DataProofMaybe.Tag,A> = DataProofMaybe.zero()}
private object RootProbeMarker
private val rootMetadata=metadata("RootProbe")
private val rootDictionary=object:DataDictionary<RootProbeMarker>(DataRepresentation(GenericType("Test","RootProbe"))) {
 private val integer=DataDictionaries.int().evidence
 override fun <F> gfoldl(fold:DataFold<F>,value:DataThunk<RootProbeMarker>):DataKind<F,RootProbeMarker> {fold.forceApply(integer);fold.forceInject();return fold.inject(DataThunk{RootProbeMarker})}
 override fun <F> gunfold(unfold:DataUnfold<F>,constructor:DataThunk<DataConstr>):DataKind<F,RootProbeMarker> {unfold.forceApply(integer);unfold.forceInject();return unfold.inject(DataThunk{RootProbeMarker})}
 override fun toConstr(value:DataThunk<RootProbeMarker>)=rootMetadata.constructors().first()
 override fun dataTypeOf(value:DataThunk<RootProbeMarker>)=rootMetadata
 override fun gmapT(transform:DataTransform,value:DataThunk<RootProbeMarker>):RootProbeMarker {transform.forceRoot(integer);return RootProbeMarker}
 override fun <R> gmapQ(query:DataQuery<R>,value:DataThunk<RootProbeMarker>):GenericList<R> {query.forceRoot(integer);return GenericList.empty()}
 override fun <F> gmapM(monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:DataThunk<RootProbeMarker>):DataKind<F,RootProbeMarker> {transform.forceRoot(integer);return monad.pure(DataThunk{RootProbeMarker})}
 override fun <F> gmapMp(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<RootProbeMarker>):DataKind<F,RootProbeMarker> {transform.forceRoot(integer);return monad.zero()}
 override fun <F> gmapMo(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<RootProbeMarker>):DataKind<F,RootProbeMarker> {transform.forceRoot(integer);return monad.zero()}
}
fun main(){
 val expected=DataProofFiles.lines("custom-native.jsonl").map{Json.parseToJsonElement(it).jsonArray}.associate{it[0].jsonPrimitive.content to it[1].jsonPrimitive.content}
 var checked=0
 fun probe(name:String,body:()->String){val got=try{body()}catch(_:Throwable){"error"};check(got==expected.getValue(name)){"$name: $got != ${expected[name]}"};checked++}
 val bad=DataThunk<GenericTuple2<Long,Long>>{error("root")}
 fun show(value:GenericTuple2<Long,Long>)="Custom ${value.first} ${value.second}"
 probe("custom-gfoldl-ignored-root"){(PandocDataClass.gfoldl(customDictionary,customFold,bad)() as CustomConst<*>).value()}
 probe("custom-gunfold-ignored-constr"){(PandocDataClass.gunfold(customDictionary,customUnfold){error("constr")}() as CustomConst<*>).value()}
 probe("custom-toConstr"){PandocDataClass.toConstr(customDictionary,bad)().name}
 probe("custom-dataTypeOf"){PandocDataClass.dataTypeOf(customDictionary,bad)().name}
 probe("custom-gmapT"){show(PandocDataClass.gmapT(customDictionary,badTransform,bad)())}
 probe("custom-gmapQl"){PandocDataClass.gmapQl(customDictionary,{_,_->error("unused combine")},{"initial"},badQuery,bad)()}
 probe("custom-gmapQr"){PandocDataClass.gmapQr(customDictionary,{_,_->error("unused combine")},{"initial"},badQuery,bad)()}
 probe("custom-default-gmapQ-dispatch"){PandocDataClass.gmapQ(customDictionary,badQuery,bad)().toString()}
 probe("custom-gmapQi"){PandocDataClass.gmapQi(customDictionary,{error("index")},badQuery,bad)()}
 probe("custom-gmapM"){show(DataIdentity.run(PandocDataClass.gmapM(customDictionary,DataIdentity,badIdentity,bad)()))}
 probe("custom-gmapMp"){if(DataProofMaybe.run(PandocDataClass.gmapMp(customDictionary,DataProofMaybe,badMaybe,bad)())===GenericMaybe.None)"Nothing"else"Just"}
 probe("custom-gmapMo"){if(DataProofMaybe.run(PandocDataClass.gmapMo(customDictionary,DataProofMaybe,badMaybe,bad)())===GenericMaybe.None)"Nothing"else"Just"}
 val datatype=metadata("LazyPair");val integer=DataDictionaries.int().evidence
 val constructor=DataConstructor<GenericTuple2<Long,Long>>(datatype.constructors()[0],listOf(integer,integer),
  {value,index->DataThunk{if(index==0)value().first else value().second}},
  {values->GenericTuple2({values[0]() as Long},{values[1]() as Long})})
 val pair=AlgebraicDataDictionary(DataRepresentation<GenericTuple2<Long,Long>>(GenericType("Test","LazyPair")),datatype,listOf(constructor),{0},{0},{0})
 probe("default-gfoldl-irrefutable-root"){(PandocDataClass.gfoldl(pair,customFold,bad)() as CustomConst<*>).value()}
 probe("default-gmapQ-irrefutable-root"){PandocDataClass.gmapQ(pair,query,bad)().joinToString(",","[","]"){"\"$it\""}}
 probe("default-gmapQl-ignore-fields"){PandocDataClass.gmapQl(pair,{left,_->left()},{"initial"},badQuery,bad)()}
 probe("default-gmapQr-ignore-fields"){PandocDataClass.gmapQr(pair,{_,right->right()},{"initial"},badQuery,bad)()}
 probe("default-gmapQi-unused-index-nullary"){PandocDataClass.gmapQi(DataDictionaries.boolean(),{error("index")},query){true}()}
 probe("default-gmapT-unused-fields"){PandocDataClass.gmapT(pair,badTransform,bad)();"constructor"}
 probe("default-gmapM-Identity-unused-fields"){DataIdentity.run(PandocDataClass.gmapM(pair,DataIdentity,delayedIdentity,bad)());"constructor"}
 probe("default-gmapMp-Maybe-failure"){if(DataProofMaybe.run(PandocDataClass.gmapMp(pair,DataProofMaybe,failedMaybe,bad)())===GenericMaybe.None)"True"else"False"}
 probe("default-gmapMo-Maybe-failure"){if(DataProofMaybe.run(PandocDataClass.gmapMo(pair,DataProofMaybe,failedMaybe,bad)())===GenericMaybe.None)"True"else"False"}
 val inject=object:DataInject<CustomTag>{override fun <A> inject(value:DataThunk<A>):DataKind<CustomTag,A> = CustomConst(DataThunk{"zero"})}
 val foldApply=object:DataFoldApply<CustomTag>{override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<CustomTag,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<CustomTag,B> = CustomConst(DataThunk{"field"})}
 val unfoldApply=object:DataUnfoldApply<CustomTag>{override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<CustomTag,DataFunction<D,B>>>):DataKind<CustomTag,B> = CustomConst(DataThunk{"field"})}
 probe("function-root-gfoldl-k"){(PandocDataClass.gfoldl(rootDictionary,DataCallbacks.fold({inject},{error("k")})){RootProbeMarker}() as CustomConst<*>).value()}
 probe("function-root-gfoldl-z"){(PandocDataClass.gfoldl(rootDictionary,DataCallbacks.fold({error("z")},{foldApply})){RootProbeMarker}() as CustomConst<*>).value()}
 probe("function-root-gunfold-k"){(PandocDataClass.gunfold(rootDictionary,DataCallbacks.unfold({inject},{error("k")})){error("constr")}() as CustomConst<*>).value()}
 probe("function-root-gunfold-z"){(PandocDataClass.gunfold(rootDictionary,DataCallbacks.unfold({error("z")},{unfoldApply})){error("constr")}() as CustomConst<*>).value()}
 probe("function-root-gunfold-ignored-constr"){(PandocDataClass.gunfold(rootDictionary,DataCallbacks.unfold({inject},{unfoldApply})){error("constr")}() as CustomConst<*>).value()}
 val queryRoot=DataCallbacks.query<String>{error("query")}
 probe("function-root-gmapT"){PandocDataClass.gmapT(rootDictionary,DataCallbacks.transform{error("transform")}){RootProbeMarker}();"RootProbe"}
 probe("function-root-gmapQ"){PandocDataClass.gmapQ(rootDictionary,queryRoot){RootProbeMarker}().toString()}
 probe("function-root-gmapQr-ignored-query"){PandocDataClass.gmapQr(rootDictionary,{_,right->right()},{"initial"},queryRoot){RootProbeMarker}()}
 probe("function-root-gmapM"){DataIdentity.run(PandocDataClass.gmapM(rootDictionary,DataIdentity,DataCallbacks.monadic{error("transform")}){RootProbeMarker}());"RootProbe"}
 val monadicRoot=DataCallbacks.monadic<DataProofMaybe.Tag>{error("transform")}
 probe("function-root-gmapMp"){DataProofMaybe.run(PandocDataClass.gmapMp(rootDictionary,DataProofMaybe,monadicRoot){RootProbeMarker}()).toString()}
 probe("function-root-gmapMo"){DataProofMaybe.run(PandocDataClass.gmapMo(rootDictionary,DataProofMaybe,monadicRoot){RootProbeMarker}()).toString()}
 val choices=object:DataMonadicTransform<DataProofList.Tag> {
  @Suppress("UNCHECKED_CAST")
  override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<DataProofList.Tag,A> {
   if(evidence.representation.type!=integer.representation.type)return DataProofList.zero()
   return DataProofList.Effect(DataThunk{GenericList.cons({((value() as Long)+10) as A}){GenericList.cons({((value() as Long)+20) as A}){GenericList.empty()}}})
  }
 }
 fun showPair(value:GenericTuple2<Long,Long>)="LazyPair ${value.first} ${value.second}"
 fun showPairs(value:GenericList<GenericTuple2<Long,Long>>)=value.joinToString(",","[","]",transform=::showPair)
 probe("list-Mp-choice-order"){showPairs(DataProofList.run(PandocDataClass.gmapMp(pair,DataProofList,choices){GenericTuple2({1L},{2L})}()))}
 probe("list-Mo-choice-order"){showPairs(DataProofList.run(PandocDataClass.gmapMo(pair,DataProofList,choices){GenericTuple2({1L},{2L})}()))}
 probe("list-Mo-first-result-unused-second"){DataProofList.run(PandocDataClass.gmapMo(pair,DataProofList,choices){GenericTuple2({1L},{error("second")})}())[0].first.toString()}
 probe("list-Mp-first-result-demands-second"){DataProofList.run(PandocDataClass.gmapMp(pair,DataProofList,choices){GenericTuple2({1L},{error("second")})}())[0].first.toString()}
 check(checked==expected.size)
 println("$checked independent custom/default Data observations PASS")
}
