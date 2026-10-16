package io.heapy.kotdoc.ast
import io.heapy.kotdoc.formats.HaskellString
import kotlinx.serialization.json.*
import java.io.File
private val chars=GenericDatas.list(GenericDatas.char)
private fun cs(s:String):List<Int> = s.codePoints().toArray().toList()
private fun show(s:List<Int>)=HaskellString.quoteCodePoints(s.toIntArray())
private object State:WalkMonad<State.Tag> {
 class Tag
 class Effect<A>(val run:(List<String>)->Pair<A,List<String>>):WalkEffect<Tag,A>
 override fun <A> pure(value:A):WalkEffect<Tag,A> = Effect{value to it}
 override fun <A,B> flatMap(value:WalkEffect<Tag,A>,next:(A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Effect{initial->val (a,state)=(value as Effect<A>).run(initial);(next(a) as Effect<B>).run(state)}
 override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<Tag,WalkLoop<S,A>>):WalkEffect<Tag,A> = Effect{start->
  var current=initial;var state=start
  while(true){val (loop,next)=(step(current) as Effect<WalkLoop<S,A>>).run(state);state=next
   when(loop){is WalkLoop.Continue->current=loop.state;is WalkLoop.Done->return@Effect loop.value to state}}
  error("unreachable")
 }
}
private object Branch:WalkMonad<Branch.Tag> {
 class Tag
 class Effect<A>(val values:Sequence<A>):WalkEffect<Tag,A>
 override fun <A> pure(value:A):WalkEffect<Tag,A> = Effect(sequenceOf(value))
 override fun <A,B> flatMap(value:WalkEffect<Tag,A>,next:(A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Effect((value as Effect<A>).values.flatMap{(next(it) as Effect<B>).values})
 override fun <S,A> tailRecM(initial:S,step:(S)->WalkEffect<Tag,WalkLoop<S,A>>):WalkEffect<Tag,A> = Effect(sequence {
  val stack=ArrayDeque<Iterator<WalkLoop<S,A>>>();stack.addLast(sequenceOf<WalkLoop<S,A>>(WalkLoop.Continue(initial)).iterator())
  while(stack.isNotEmpty()){val iterator=stack.last();if(!iterator.hasNext()){stack.removeLast();continue};when(val loop=iterator.next()){is WalkLoop.Done->yield(loop.value);is WalkLoop.Continue->stack.addLast((step(loop.state) as Effect<WalkLoop<S,A>>).values.iterator())}}
 })
}
private fun <A> first(data:GenericData<A>):String = when(val result=PandocGeneric.queryWithLazy(data,data,{GenericMaybe.Some{"hit"}},{error("root")},GenericMonoids.first())) {GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just "+AstShow.show(result.value)}
private val quote=object:GenericData<QuoteType> {
 override val type=GenericType("Text.Pandoc.Definition","QuoteType")
 override fun layer(value:()->QuoteType):GenericLayer<QuoteType> {val input=value();return GenericLayer(emptyList()){input}}
}
private fun atom(name:String,value:Any?):GenericValue<Any?> = GenericValue(GenericDefinitionData.named(name)){value}
private fun node(name:String,constructor:String,vararg fields:GenericValue<*>):GenericValue<Any?> = GenericDefinitionData.value(name,constructor,fields.toList())
private fun sourceList(element:GenericData<Any?>,values:List<GenericValue<*>>):GenericValue<List<Any?>> {
 val data=GenericDatas.list(element)
 fun at(index:Int):GenericList<Any?> = if(index==values.size)GenericList.empty() else GenericList.cons({values[index].cast(element)},{at(index+1)})
 return GenericValue(data){at(0)}
}
private val inlineData get()=GenericDefinitionData.named("Inline")
private val inlinesData get()=GenericDatas.list(inlineData)
private fun str(text:String)=node("Inline","Str",atom("Text",text))
private fun space()=node("Inline","Space")
private fun emph(vararg values:GenericValue<*>)=node("Inline","Emph",sourceList(inlineData,values.toList()))
private fun myDoc():GenericValue<Any?> {
 val meta=GenericValue(GenericDefinitionData.named("Meta")){GenericDefinitionData.named("Meta").fromCanonical(emptyMap<String,Any?>())}
 val para=node("Block","Para",sourceList(inlineData,listOf(str("Hi"),space(),emph(str("world"),space()),emph(space(),str("emphasized")))))
 return node("Pandoc","Pandoc",meta,sourceList(GenericDefinitionData.named("Block"),listOf(para)))
}
private fun concat(a:List<Any?>,b:List<Any?>):GenericList<Any?> = GenericList.defer {when(val step=GenericList.view(a).step()){GenericList.Step.Nil->GenericList.view(b);is GenericList.Step.Cons->GenericList.cons(step::head){concat(step.tail(),b)}}}
private fun normal(values:List<Any?>):List<Any?> {
 val first=GenericList.view(values).step();if(first !is GenericList.Step.Cons)return values
 val x=first.head() as GenericNode;val second=first.tail().step();if(second !is GenericList.Step.Cons)return values
 val y=second.head() as GenericNode
 return when {
  x.constructor=="Space" && y.constructor=="Space"->GenericList.cons({x},second::tail)
  x.constructor=="Emph" && y.constructor=="Emph"->{
   val content=GenericValue(inlinesData){concat(x.fields.single().cast(inlinesData),y.fields.single().cast(inlinesData))}
   GenericList.cons({node("Inline","Emph",content).value()},second::tail)
  }
  else->values
 }
}
internal fun genericCoreExecute(probe:String):JsonElement? = try {val value=when(probe) {
 "bottom-up-char-string"->show(PandocGeneric.bottomUp(GenericDatas.char,chars,{if(it==97)88 else it},cs("aba")))
 "top-down-char-string"->show(PandocGeneric.topDown(GenericDatas.char,chars,{if(it==97)88 else it},cs("aba")))
 "bottom-up-list-tails"->show(PandocGeneric.bottomUp(chars,chars,{it.filter{c->c!=35}},cs("a#b#")))
 "top-down-list-tails"->show(PandocGeneric.topDown(chars,chars,{it.filter{c->c!=35}},cs("a#b#")))
 "query-string-tails"->PandocGeneric.queryWith(chars,chars,{sequenceOf(it)},cs("abc"),GenericMonoids.sequence()).joinToString(",","[","]"){show(it)}
 "query-string-tail-count"->PandocGeneric.queryWith(chars,chars,{1L},cs("abc"),GenericMonoids.sumLong).toString()
 "query-first-bottom-char"->first(GenericDatas.char)
 "query-first-bottom-int"->first(GenericDatas.int)
 "query-first-bottom-bool"->first(GenericDatas.boolean)
 "query-first-bottom-quote"->first(quote)
 "query-first-bottom-text"->first(GenericDatas.text)
 "query-first-bottom-format"->first(GenericDatas.newtype(GenericType("Text.Pandoc.Definition","Format"),GenericDatas.text))
 "query-first-bottom-list"->first(chars)
 "query-first-bottom-meta"->first(GenericDefinitionData.named("Meta"))
 "query-first-bottom-inline"->first(inlineData)
 "query-first-bottom-many"->first(GenericDatas.many(inlineData))
 "query-first-bottom-seq"->first(GenericDatas.sequence(inlineData))
 "bottom-up-haddock-normal", "top-down-haddock-normal"->{val root=GenericDefinitionData.named("Pandoc");val input=myDoc().value();val result=if(probe.startsWith("bottom"))PandocGeneric.bottomUp(inlinesData,root,::normal,input)else PandocGeneric.topDown(inlinesData,root,::normal,input);genericShow(GenericValue(root){result})}
 "query-unit-leaf-callback"->{PandocGeneric.queryWith(GenericDatas.char,GenericDatas.char,{error("callback")},97,GenericMonoids.unit);"()"}
 "query-unit-empty-list-callback"->{PandocGeneric.queryWith(chars,chars,{error("callback")},emptyList(),GenericMonoids.unit);"()"}
 "query-unit-cons-callback"->{PandocGeneric.queryWith(chars,chars,{error("callback")},cs("a"),GenericMonoids.unit);"()"}
 "query-unit-text-callback"->{PandocGeneric.queryWith(GenericDatas.text,GenericDatas.text,{error("callback")},"a",GenericMonoids.unit);"()"}
 "bottom-upM-char-state"->{val(result,trace)=(PandocGeneric.bottomUpM(State,GenericDatas.char,chars,{c->State.Effect{(if(c==97)88 else c) to (it+String(Character.toChars(c)))}},cs("abc")) as State.Effect<List<Int>>).run(emptyList());"("+show(result)+","+AstShow.show(trace.joinToString(""))+")"}
 "bottom-upM-string-state"->{val(result,trace)=(PandocGeneric.bottomUpM(State,chars,chars,{s->State.Effect{(s+35) to (it+show(s))}},cs("abc")) as State.Effect<List<Int>>).run(emptyList());"("+show(result)+",["+trace.joinToString(",")+"])"}
 "bottom-upM-char-list"->(PandocGeneric.bottomUpM(Branch,GenericDatas.char,chars,{c->Branch.Effect(sequenceOf(c,if(c==97)65 else 66))},cs("ab")) as Branch.Effect<List<Int>>).values.joinToString(",","[","]"){show(it)}
 else->return null
 };JsonPrimitive(value)} catch(e:IllegalStateException){buildJsonObject{put("exception",e.message!!)}}
fun genericCoreReplayMain() {
 val root=File("tools/haskell-types-generic-reference");val requests=File(root,"core-requests.jsonl").readLines();val responses=File(root,"core-responses.jsonl").readLines();var count=0;val remaining=mutableListOf<String>()
 requests.zip(responses).forEach{(q,r)->val probe=Json.parseToJsonElement(q).jsonObject.getValue("probe").jsonPrimitive.content;val actual=genericCoreExecute(probe);if(actual==null)remaining+=probe else{check(actual==Json.parseToJsonElement(r)){"$probe: $actual != $r"};count++}}
 println("$count actual original Generic core responses PASS; remaining ${remaining.joinToString()}")
}
