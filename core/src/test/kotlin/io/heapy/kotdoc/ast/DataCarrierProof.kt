package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
@Suppress("UNCHECKED_CAST")
fun main(){
 val expected=DataProofFiles.lines("carrier-native.jsonl").map{Json.parseToJsonElement(it).jsonArray}.associate{it[0].jsonPrimitive.content to it[1].jsonPrimitive.content}
 var checked=0
 fun probe(name:String,body:()->String){val got=try{body()}catch(_:Throwable){"error"};check(got==expected.getValue(name)){"$name: $got != ${expected[name]}"};checked++}
 val integer=DataDictionaries.int();val list=DataDictionaries.list(integer.evidence);val pair=DataDictionaries.tuple2(integer.evidence,integer.evidence)
 val maybe=DataDictionaries.maybe(integer.evidence);val text=DataDictionaries.text();val map=DataDictionaries.textMap(integer.evidence)
 val sequence=DataDictionaries.sequence(integer.evidence);val many=DataDictionaries.many(integer.evidence)
 val xs=GenericList.cons<Long>({error("head")}){error("tail")}
 val badTransform=object:DataTransform{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A=error("transform")}
 val badInitial:()->String={error("initial")}
 val query=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)="child"}
 val firstQuery=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=if(evidence.representation.type==integer.representation.type)"head"else error("tail query")}
 val lastQuery=object:DataQuery<String>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=if(evidence.representation.type==integer.representation.type)error("head query")else"tail"}
 val replaceInt=object:DataTransform{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A=if(evidence.representation.type==integer.representation.type)17L as A else value()}
 probe("list-map-outer-with-bottom-transform"){if(PandocDataClass.gmapT(list,badTransform){xs}().step() is GenericList.Step.Cons)"cons"else"nil"}
 probe("list-map-head-replacement"){(PandocDataClass.gmapT(list,replaceInt){xs}().step() as GenericList.Step.Cons).head().toString()}
 probe("list-map-head-forced-transform"){(PandocDataClass.gmapT(list,badTransform){xs}().step() as GenericList.Step.Cons).head();"head"}
 probe("list-map-tail-forced-transform"){(PandocDataClass.gmapT(list,badTransform){xs}().step() as GenericList.Step.Cons).tail().step();"tail"}
 probe("list-query-head-ignores-fields"){PandocDataClass.gmapQi(list,{0L},query){xs}()}
 probe("list-query-tail-ignores-fields"){PandocDataClass.gmapQi(list,{1L},query){xs}()}
 probe("list-gmapQ-first-only"){PandocDataClass.gmapQ(list,firstQuery){xs}().first()}
 probe("list-left-ignores-previous-query"){PandocDataClass.gmapQl(list,{_,right->right()},badInitial,lastQuery){xs}()}
 probe("list-right-ignores-following-query"){PandocDataClass.gmapQr(list,{left,_->left()},badInitial,firstQuery){xs}()}
 probe("list-query-negative-index"){PandocDataClass.gmapQi(list,{-1L},query){xs}()}
 val tuple=GenericTuple2<Long,Long>({error("first")},{error("second")})
 probe("tuple-map-first-replacement"){PandocDataClass.gmapT(pair,replaceInt){tuple}().first.toString()}
 probe("tuple-query-first-ignores-fields"){PandocDataClass.gmapQi(pair,{0L},query){tuple}()}
 probe("tuple-query-second-ignores-fields"){PandocDataClass.gmapQi(pair,{1L},query){tuple}()}
 val just=GenericMaybe.Some<Long>{error("payload")}
 probe("maybe-map-payload-replacement"){(PandocDataClass.gmapT(maybe,replaceInt){just}() as GenericMaybe.Some).value.toString()}
 probe("maybe-query-ignores-payload"){PandocDataClass.gmapQi(maybe,{0L},query){just}()}
 probe("text-query-ignores-root"){PandocDataClass.gmapQi(text,{0L},query){error("text")}()}
 val characters=DataDictionaries.list(DataDictionaries.char().evidence)
 val replaceString=object:DataTransform{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A=if(evidence.representation.type==characters.representation.type)GenericList.view("new".codePoints().toArray().toList()) as A else value()}
 probe("text-map-replacement-ignores-root"){PandocDataClass.gmapT(text,replaceString){error("text")}()}
 probe("map-query-ignores-root"){PandocDataClass.gmapQi(map,{0L},query){error("map")}()}
 val entries=DataDictionaries.list(DataDictionaries.tuple2(text.evidence,integer.evidence).evidence)
 val replaceEntries=object:DataTransform{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A=if(evidence.representation.type==entries.representation.type)GenericList.empty<GenericTuple2<String,Long>>() as A else value()}
 probe("map-map-replacement-ignores-root"){PandocDataClass.gmapT(map,replaceEntries){error("map")}().size.toString()}
 val singleton=ManySequence.singleton<Long>{error("head")}
 probe("seq-query-head-ignores-payload"){PandocDataClass.gmapQi(sequence,{0L},query){singleton}()}
 probe("seq-query-tail-ignores-payload"){PandocDataClass.gmapQi(sequence,{1L},query){singleton}()}
 probe("seq-query-forces-root"){PandocDataClass.gmapQi(sequence,{0L},query){error("seq")}()}
 probe("many-query-ignores-root"){PandocDataClass.gmapQi(many,{0L},query){error("many")}()}
 val replaceSeq=object:DataTransform{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A=if(evidence.representation.type==sequence.representation.type)ManySequence.empty<Long>() as A else value()}
 probe("many-map-replacement-ignores-root"){PandocDataClass.gmapT(many,replaceSeq){error("many")}().length.toString()}
 val inline=DataRegistry().dictionary(GenericDefinitionData.named("Inline").type)
 val emph=GenericDefinitionData.declarations.single{it.name=="Inline"}.constructors.indexOfFirst{it.name=="Emph"}
 probe("StrictData-toConstr-forces-list-root"){PandocDataClass.toConstr(inline){DataNode("Inline",emph,listOf(DataThunk{error("list")}))}().name}
 probe("StrictData-toConstr-ignores-list-head"){PandocDataClass.toConstr(inline){DataNode("Inline",emph,listOf(DataThunk{GenericList.cons<Any?>({error("head")}){GenericList.empty()}}))}().name}
 val normal=GenericMaybe.Some{1L}
 val resultQuery=object:DataQuery<GenericList<Long>>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):GenericList<Long> = GenericList.defer{error("result")}}
 val unitQuery=object:DataQuery<Unit>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>)=Unit}
 probe("result-gmapQi-list-root"){PandocDataClass.gmapQi(maybe,{0L},resultQuery){normal}();"returned"}
 probe("result-gmapQl-list-root"){PandocDataClass.gmapQl(maybe,{_,_->GenericList.defer<Long>{error("result")}},{GenericList.empty<Long>()},unitQuery){normal}();"returned"}
 probe("result-gmapQr-list-root"){PandocDataClass.gmapQr(maybe,{_,_->GenericList.defer<Long>{error("result")}},{GenericList.empty<Long>()},unitQuery){normal}();"returned"}
 probe("result-gmapQ-outer-ignores-head"){if(PandocDataClass.gmapQ(maybe,resultQuery){normal}().step() is GenericList.Step.Cons)"outer"else"empty"}
 probe("result-gmapQ-head-forces-result"){PandocDataClass.gmapQ(maybe,resultQuery){normal}().first();"returned"}
 val tailLength=object:DataQuery<Long>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):Long=if(evidence.representation.type==list.representation.type)(value() as GenericList<Long>).count().toLong()else-1}
 for(size in listOf(1,17,10000,100000))probe("deep-list-tail-length-$size"){PandocDataClass.gmapQi(list,{1L},tailLength){GenericList.view(List(size){1L})}().toString()}
 val sequenceLength=object:DataQuery<Long>{override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):Long=if(evidence.representation.type==sequence.representation.type)(value() as ManySequence<Long>).length else-1}
 for(size in listOf(1L,17L,4096L,34359738368L))probe("Long-seq-tail-length-$size"){
  PandocDataClass.gmapQi(sequence,{1L},sequenceLength){ManySequence.fromTree{ManyRepetition.replicate(size,ManyCell.Leaf<Long>{error("head")})}}().toString()
 }
 check(checked==expected.size)
 println("$checked independent partial-carrier/returned-root/deep observations PASS")
}
