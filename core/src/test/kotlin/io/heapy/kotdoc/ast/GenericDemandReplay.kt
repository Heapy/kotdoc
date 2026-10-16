package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private fun <A> identity(effect:WalkEffect<WalkIdentity.Tag,A>):A=(effect as WalkIdentity.Value<A>).value
private sealed interface Box {data object End:Box;class Cell(val first:()->Boolean,val rest:()->Box):Box}
private val box=object:GenericData<Box>{
 override val type=GenericType("Main","Box",library="generic-oracle")
 override fun layer(value:()->Box):GenericLayer<Box> = when(val input=value()){
  Box.End->GenericLayer(emptyList()){Box.End}
  is Box.Cell->GenericLayer(listOf(GenericValue(GenericDatas.boolean,input.first),GenericValue(this,input.rest))){fields->Box.Cell({fields[0].cast(GenericDatas.boolean)},{fields[1].cast(this)})}
 }
}
private val inline=GenericDefinitionData.named("Inline")
private fun space():Any?=GenericDefinitionData.node("Inline","Space",emptyList())
private fun str():Any?=GenericDefinitionData.node("Inline","Str",listOf(GenericValue(GenericDatas.text){"a"}))
private val list=GenericDatas.list(inline)
private val tuple=GenericDatas.tuple2(inline,inline)
private val map=GenericDatas.map(GenericDatas.text,inline,Comparator.naturalOrder<String>())
private val seq=GenericDatas.sequence(inline)
private fun show(data:GenericData<Any?>,value:Any?)=genericShow(GenericValue(data){value})
@Suppress("UNCHECKED_CAST")
internal fun genericDemandExecute(name:String):String {
 val top=name.startsWith("top-")||name.startsWith("custom-top-")
 fun <A,B> pure(target:GenericData<A>,data:GenericData<B>,f:(()->A)->A,input:()->B):B = if(top)PandocGeneric.topDownLazy(target,data,f,input)else PandocGeneric.bottomUpLazy(target,data,f,input)
 return when(name){
  "bottom-inline-root-const","top-inline-root-const"->show(inline,pure(inline,inline,{space()},{error("root")}))
  "bottom-list-root-const","top-list-root-const"->genericShow(GenericValue(list){pure(list,list,{GenericList.empty()},{error("root")})})
  "bottom-list-partial-tail","top-list-partial-tail"->{val result=pure(inline,list,{space()},{GenericList.cons({str()},{error("tail")})});"["+show(inline,result[0])+"]"}
  "bottom-pair-first","top-pair-first"->{val result=pure(inline,tuple,{space()},{GenericTuple2({str()},{error("right")})});show(inline,result.first)}
  "bottom-text-root-const","top-text-root-const"->AstShow.show(pure(GenericDatas.text,GenericDatas.text,{"ok"},{error("root")}))
  "bottom-map-root-const","top-map-root-const"->genericShow(GenericValue(map){pure(map,map,{emptyMap()},{error("root")})})
  "bottom-seq-root-const","top-seq-root-const"->genericShow(GenericValue(seq){pure(seq,seq,{GenericSequence.empty()},{error("root")})})
  "custom-bottom-root-const","custom-top-root-const"->{val result=pure(box,box,{Box.End},{error("root")});check(result==Box.End);"End"}
  "custom-query-first-tail"->when(val value=PandocGeneric.queryWith(GenericDatas.boolean,box,{GenericMaybe.Some{it}},Box.Cell({true},{error("tail")}),GenericMonoids.first())){GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just "+if(value.value)"True"else"False"}
  "custom-query-last-tail"->when(val value=PandocGeneric.queryWith(GenericDatas.boolean,box,{GenericMaybe.Some{it}},Box.Cell({true},{error("tail")}),GenericMonoids.last())){GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just "+if(value.value)"True"else"False"}
  "custom-bottom-first-field","custom-top-first-field"->{val result=pure(GenericDatas.boolean,box,{!it()},{Box.Cell({true},{error("tail")})}) as Box.Cell;if(result.first())"True"else"False"}
  "custom-identity-tail"->{val result=GenericIdentity.run(PandocGeneric.bottomUpM(GenericIdentity,GenericDatas.boolean,box,{GenericIdentity.pure{!it}},Box.Cell({true},{error("tail")}))) as Box.Cell;if(result.first())"True"else"False"}
  "identity-char-root-const"->{val result=GenericIdentity.run(PandocGeneric.bottomUpMValue(GenericIdentity,GenericDatas.char,GenericDatas.char,{GenericIdentity.pure{120}},{error("root")}));genericShow(result)}
  "identity-inline-root-const"->{val result=GenericIdentity.run(PandocGeneric.bottomUpMValue(GenericIdentity,inline,inline,{GenericIdentity.pure{space()}},{error("root")}));genericShow(result)}
  "query-pair-first-right"->{val data=GenericDatas.tuple2(GenericDatas.boolean,GenericDatas.boolean);when(val value=PandocGeneric.queryWith(GenericDatas.boolean,data,{GenericMaybe.Some{it}},GenericTuple2({true},{error("right")}),GenericMonoids.first())){GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just "+if(value.value)"True"else"False"}}
  "reader-custom-first-field"->{val reader=GenericReader<Boolean>();val result=reader.run(PandocGeneric.bottomUpM(reader,GenericDatas.boolean,box,{b->GenericReader.Value<Boolean,Boolean>{environment->if(environment)!b else b}},Box.Cell({true},{error("tail")})),true) as Box.Cell;if(result.first())"True"else"False"}
  "reader-inline-root-const"->{val reader=GenericReader<Boolean>();val result=reader.run(PandocGeneric.bottomUpMValue(reader,inline,inline,{reader.pure{space()}},{error("root")}),true);genericShow(result)}
  "reader-list-order"->{val reader=GenericReader<Int>();val chars=GenericDatas.list(GenericDatas.char);val result=reader.run(PandocGeneric.bottomUpM(reader,GenericDatas.char,chars,{c->GenericReader.Value<Int,Int>{n->c+n}},listOf(97,98,99)),1);genericShow(GenericValue(chars){result})}
  "maybe-custom-first-failure"->when(GenericMaybeMonad.run(PandocGeneric.bottomUpM(GenericMaybeMonad,GenericDatas.boolean,box,{GenericMaybeMonad.nothing<Boolean>()},Box.Cell({true},{error("tail")})))){GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just"}
  "query-foldl-pair-order","query-foldl-list-order"->{val monoid=object:GenericMonoid<String>{override fun empty()="e";override fun append(left:()->String,right:()->String)="("+left()+">"+right()+")"};val f={b:Boolean->if(b)"True"else"False"};if(name.contains("pair"))PandocGeneric.queryWith(GenericDatas.boolean,GenericDatas.tuple2(GenericDatas.boolean,GenericDatas.boolean),f,GenericTuple2({true},{false}),monoid)else PandocGeneric.queryWith(GenericDatas.boolean,GenericDatas.list(GenericDatas.boolean),f,listOf(true,false),monoid)}
  "definition-list-outer-head","definition-list-terms-only","definition-list-defs-only"->{
   fun <A> failed(label:String):List<A> = object:AbstractList<A>(){override val size:Int get()=error(label);override fun get(index:Int):A=error(label)}
   val original=when(name){
    "definition-list-outer-head"->Block.DefinitionList(object:AbstractList<Pair<List<Inline>,List<List<Block>>>>(){override val size=2;override fun get(index:Int)=if(index==0)listOf<Inline>(Inline.Str("a")) to emptyList<List<Block>>() else error("tail")})
    "definition-list-terms-only"->Block.DefinitionList(listOf(listOf<Inline>(Inline.Str("a")) to failed("defs")))
    else->Block.DefinitionList(listOf(failed<Inline>("terms") to listOf(listOf<Block>(Block.Para(listOf(Inline.Str("a")))))))
   }
   val result=PandocGeneric.bottomUpLazy(GenericAstData.named<Inline>("Inline"),GenericAstData.named<Block>("Block"),{Inline.Space},{original}) as Block.DefinitionList
   when(name){"definition-list-outer-head"->AstShow.show(result.items.take(1));"definition-list-terms-only"->AstShow.show(result.items[0].first);else->AstShow.show(result.items[0].second)}
  }
  else->error(name)
 }
}
fun genericDemandReplayMain(){
 val dir=File("tools/haskell-types-generic-reference")
 val requests=File(dir,"demand-requests.jsonl").readLines().map{Json.parseToJsonElement(it).jsonObject.getValue("probe").jsonPrimitive.content}
 val expected=File(dir,"demand-responses.jsonl").readLines().map{Json.parseToJsonElement(it)}
 for((name,want)in requests.zip(expected)){
  val actual=try{JsonPrimitive(genericDemandExecute(name))}catch(error:Throwable){buildJsonObject{put("exception",error.message?:error.javaClass.simpleName)}}
  check(actual==want){"$name: $actual != $want"}
 }
 println("${requests.size} actual original custom Data/partial-field demand responses PASS")
}
