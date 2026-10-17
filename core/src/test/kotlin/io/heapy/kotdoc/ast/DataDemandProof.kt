package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
private class Tag
private class K<A>(val text:DataThunk<String>):DataKind<Tag,A>{override fun forceGenericRepRoot(){text()}}
private val fold=object:DataFold<Tag>{
 override fun <A> inject(value:DataThunk<A>):DataKind<Tag,A> = K(DataThunk{"zero"})
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<Tag,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<Tag,B> = K(DataThunk{"field"})
}
private val unfold=object:DataUnfold<Tag>{
 override fun <A> inject(value:DataThunk<A>):DataKind<Tag,A> = K(DataThunk{"zero"})
 override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<Tag,DataFunction<D,B>>>):DataKind<Tag,B> = K(DataThunk{"field"})
}
private fun dictionary(name:String)=DataDictionaries.definition(name){expression->
 DataEvidence(DataRepresentation<Any?>(GenericDefinitionData.resolve(expression).type)){error("unused dictionary")}
}
private fun probe(name:String,body:()->String){val result=try{body()}catch(_:Throwable){"error"};println(buildJsonArray{add(name);add(result)})}
fun main(){
 for(name in listOf("Pandoc","Meta","Row","Format"))probe("toConstr-$name-bottom"){PandocDataClass.toConstr(dictionary(name)){error("root")}().name}
 probe("gunfold-Pandoc-bottomConstr"){(PandocDataClass.gunfold(dictionary("Pandoc"),unfold){error("constructor")}() as K<*>).text()}
 // Many's independent newtype constructor program is covered in the next registry gate.
 probe("gunfold-Many-bottomConstr") {
  val element=dictionary("Inline").evidence
  (PandocDataClass.gunfold(DataDictionaries.many(element),unfold){error("constructor")}() as K<*>).text()
 }
 for(index in listOf(-1L,0L,1L,2L,3L,4L,5L,100L))probe("gunfold-Alignment-foreign-$index"){
  val foreign=DataConstr({DataConstrRep.Algebraic(index)},{"Foreign"},{GenericList.empty()},{DataFixity.Prefix},{error("unused type")})
  val value=DataIdentity.run(PandocDataClass.gunfold(dictionary("Alignment"),object:DataUnfold<DataIdentity.Tag>{
   override fun <A> inject(value:DataThunk<A>)=DataIdentity.pure(value)
   override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<DataIdentity.Tag,DataFunction<D,B>>>):DataKind<DataIdentity.Tag,B> = error("unused field")
  }){foreign}())
  GenericDefinitionData.declarations.single{it.name=="Alignment"}.constructors[value.constructor].name
 }
 for(name in listOf("Meta","Pandoc","Format"))probe("gfoldl-$name-bottom"){(PandocDataClass.gfoldl(dictionary(name),fold){error("root")}() as K<*>).text()}
 val registry=DataRegistry()
 val sourceTypes=GenericDefinitionData.declarations.map{GenericDefinitionData.named(it.name).type}+GenericType("Text.Pandoc.Builder","Many",listOf(GenericDefinitionData.named("Inline").type))
 val query=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)="child"}
 val badQuery=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):String=error("query")}
 val failure=object:DataMonadicTransform<DataProofMaybe.Tag>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<DataProofMaybe.Tag,A> = DataProofMaybe.zero()}
 for(type in sourceTypes) {
  val dictionary=registry.dictionary(type)
  val name=dictionary.dataTypeOf(DataThunk{error("unused metadata")}).name
  val bad=DataThunk<Any?>{error("root $name")}
  probe("$name/gfoldl-root"){(PandocDataClass.gfoldl(dictionary,fold,bad)() as K<*>).text()}
  probe("$name/toConstr-root"){PandocDataClass.toConstr(dictionary,bad)().name}
  probe("$name/gmapQ-root"){PandocDataClass.gmapQ(dictionary,query,bad)().joinToString(",","[","]"){"\"$it\""}}
  probe("$name/gmapQl-root"){PandocDataClass.gmapQl(dictionary,{left,_->left()},{"initial"},badQuery,bad)()}
  probe("$name/gmapQr-root"){PandocDataClass.gmapQr(dictionary,{_,right->right()},{"initial"},badQuery,bad)()}
  probe("$name/gmapQi-root"){PandocDataClass.gmapQi(dictionary,{0L},query,bad)()}
  fun failed(value:DataKind<DataProofMaybe.Tag,Any?>)=if(DataProofMaybe.run(value)===GenericMaybe.None)"True"else"False"
  probe("$name/gmapM-failure"){failed(PandocDataClass.gmapM(dictionary,DataProofMaybe,failure,bad)())}
  probe("$name/gmapMp-failure"){failed(PandocDataClass.gmapMp(dictionary,DataProofMaybe,failure,bad)())}
  probe("$name/gmapMo-failure"){failed(PandocDataClass.gmapMo(dictionary,DataProofMaybe,failure,bad)())}
 }

}
