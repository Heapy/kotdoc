package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private object Counter:WalkMonad<Counter.Tag>{
 class Tag
 class Value<A>(val run:(Long)->Pair<A,Long>):WalkEffect<Tag,A>
 override fun <A> pure(value:A):WalkEffect<Tag,A> = Value{value to it}
 override fun <A,B> flatMap(value:WalkEffect<Tag,A>,next:(A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Value{start->val(a,state)=(value as Value<A>).run(start);(next(a) as Value<B>).run(state)}
 override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<Tag,WalkLoop<S,A>>):WalkEffect<Tag,A> = Value{start->var state=start;var current=initial
  while(true){val(loop,next)=(step(current) as Value<WalkLoop<S,A>>).run(state);state=next;when(loop){is WalkLoop.Continue->current=loop.state;is WalkLoop.Done->return@Value loop.value to state}}
  error("unreachable")
 }
}
private fun inlineObservation(depth:Int):JsonObject {
 val inline=GenericDefinitionData.named("Inline");val lists=GenericDatas.list(inline)
 var input:Any?=GenericDefinitionData.node("Inline","Str",listOf(GenericValue(GenericDatas.text){"a"}))
 repeat(depth){val previous=input;input=GenericDefinitionData.node("Inline","Emph",listOf(GenericValue(lists){GenericList.cons({previous},{GenericList.empty()})}))}
 fun text(value:Any?)=PandocGeneric.queryWith(GenericDatas.char,inline,{sequenceOf(it)},value,GenericMonoids.sequence()).joinToString(""){String(Character.toChars(it))}
 val bottom=PandocGeneric.bottomUp(GenericDatas.char,inline,{98},input)
 val top=PandocGeneric.topDown(GenericDatas.char,inline,{98},input)
 val count=PandocGeneric.queryWith(GenericDatas.char,inline,{1L},input,GenericMonoids.sumLong)
 val nodes=PandocGeneric.queryWith(inline,inline,{1L},input,GenericMonoids.sumLong)
 val(result,effects)=(PandocGeneric.bottomUpM(Counter,GenericDatas.char,inline,{Counter.Value{state->98 to state+1}},input) as Counter.Value<Any?>).run(0)
 return buildJsonObject{put("bottomUp",text(bottom));put("topDown",text(top));put("queryChars",count);put("queryNodes",nodes);put("bottomUpM",text(result));put("effects",effects)}
}
internal fun genericDeepExecute(request:JsonObject):JsonObject {
 val depth=request.getValue("depth").jsonPrimitive.int
 if(request["shape"]?.jsonPrimitive?.content=="inline")return inlineObservation(depth)
 val chars=GenericDatas.list(GenericDatas.char);val input=List(depth){97}
 fun text(value:List<Int>)=buildString{for(c in value)appendCodePoint(c)}
 val bottom=PandocGeneric.bottomUp(GenericDatas.char,chars,{98},input)
 val top=PandocGeneric.topDown(GenericDatas.char,chars,{98},input)
 val count=PandocGeneric.queryWith(GenericDatas.char,chars,{1L},input,GenericMonoids.sumLong)
 val tails=PandocGeneric.queryWith(chars,chars,{1L},input,GenericMonoids.sumLong)
 val(result,effects)=(PandocGeneric.bottomUpM(Counter,GenericDatas.char,chars,{Counter.Value{state->98 to state+1}},input) as Counter.Value<List<Int>>).run(0)
 return buildJsonObject{put("bottomUp",text(bottom));put("topDown",text(top));put("queryChars",count);put("queryTails",tails);put("bottomUpM",text(result));put("effects",effects)}
}
fun genericDeepReplayMain(){
 val requests=GenericProofFixtures.rows("deep-requests.jsonl");val expected=GenericProofFixtures.rows("deep-responses.jsonl")
 for((request,want)in requests.zip(expected))check(genericDeepExecute(request)==want){"deep $request"}
 println("72 original deep observations PASS including list20000/Inline10000")
}
