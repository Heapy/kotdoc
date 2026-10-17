package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private val sum=object:GenericMonoid<Long>{override fun empty()=0L;override fun append(left:()->Long,right:()->Long)=left()+right()}
private val doubleNumeric=object:ManyNumeric<Double>{override fun zero()=0.0;override fun one()=1.0;override fun plus(left:()->Double,right:()->Double)=left()+right();override fun times(left:()->Double,right:()->Double)=left()*right()}
private val doubleOrder=object:ManyOrder<Double>{override fun greaterEqual(left:()->Double,right:()->Double)=left()>=right();override fun lessEqual(left:()->Double,right:()->Double)=left()<=right()}
private val trace=object:GenericMonoid<String>{override fun empty()="e";override fun append(left:()->String,right:()->String)="("+left()+">"+right()+")"}
private val longOrder=object:ManyOrder<Long>{override fun greaterEqual(left:()->Long,right:()->Long)=left()>=right();override fun lessEqual(left:()->Long,right:()->Long)=left()<=right()}
private val longNumeric=object:ManyNumeric<Long>{override fun zero()=0L;override fun one()=1L;override fun plus(left:()->Long,right:()->Long)=left()+right();override fun times(left:()->Long,right:()->Long)=left()*right()}
private object StateA:ManyMonadEffects<StateA.Tag>{
 class Tag
 class Action<A>(val run:(MutableList<Long>)->A):WalkEffect<Tag,A>
 override fun <A> pure(value:()->A):WalkEffect<Tag,A> = Action{value()}
 override fun <A,B> fmap(value:()->WalkEffect<Tag,A>,f:(()->A)->B):WalkEffect<Tag,B> = Action{state->val result=run(value(),state);f{result}}
 override fun <A,B> ap(function:()->WalkEffect<Tag,(()->A)->B>,argument:()->WalkEffect<Tag,A>):WalkEffect<Tag,B> = Action{state->val fn=run(function(),state);val value=run(argument(),state);fn{value}}
 override fun <A,B> flatMap(value:()->WalkEffect<Tag,A>,next:(()->A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = Action{state->val result=run(value(),state);run(next{result},state)}
 fun <A> run(effect:WalkEffect<Tag,A>,state:MutableList<Long>):A=(effect as Action<A>).run(state)
 fun action(value:()->Long):WalkEffect<Tag,Long> = Action{state->val x=value();state.add(x);x+1}
}
private object ListA:ManyApplicative<ListA.Tag>{
 class Tag
 class Values<A>(val values:List<A>):WalkEffect<Tag,A>
 override fun <A> pure(value:()->A):WalkEffect<Tag,A> = Values(listOf(value()))
 override fun <A,B> fmap(value:()->WalkEffect<Tag,A>,f:(()->A)->B):WalkEffect<Tag,B> = Values(run(value()).map{x->f{x}})
 override fun <A,B> ap(function:()->WalkEffect<Tag,(()->A)->B>,argument:()->WalkEffect<Tag,A>):WalkEffect<Tag,B> = Values(run(function()).flatMap{f->run(argument()).map{x->f{x}}})
 fun <A> run(effect:WalkEffect<Tag,A>):List<A> = (effect as Values<A>).values
}
private object ShapeA:ManyMonadEffects<ShapeA.Tag>{
 class Tag
 class Value<A>(value:()->A,val trace:String):WalkEffect<Tag,A>{val result=ManyThunk(value)}
 override fun <A> pure(value:()->A):WalkEffect<Tag,A> = Value(value,"p")
 override fun <A,B> fmap(value:()->WalkEffect<Tag,A>,f:(()->A)->B):WalkEffect<Tag,B>{val x=value() as Value<A>;return Value({f(x.result::get)},"f("+x.trace+")")}
 override fun <A,B> ap(function:()->WalkEffect<Tag,(()->A)->B>,argument:()->WalkEffect<Tag,A>):WalkEffect<Tag,B>{val f=function() as Value<(()->A)->B>;val x=argument() as Value<A>;return Value({f.result.get()(x.result::get)},"a("+f.trace+","+x.trace+")")}
 override fun <A,B,C> liftA2(a:()->WalkEffect<Tag,A>,b:()->WalkEffect<Tag,B>,f:(()->A,()->B)->C):WalkEffect<Tag,C>{val x=a() as Value<A>;val y=b() as Value<B>;return Value({f(x.result::get,y.result::get)},"2("+x.trace+","+y.trace+")")}
 override fun <A,B> flatMap(value:()->WalkEffect<Tag,A>,next:(()->A)->WalkEffect<Tag,B>):WalkEffect<Tag,B>{val x=value() as Value<A>;val y=next(x.result::get) as Value<B>;return Value(y.result::get,"b("+x.trace+","+y.trace+")")}
}
private fun <A> manyShow(value:Many<A>)="Many {unMany = fromList "+AstShow.show(value.toList())+"}"
private fun <A> algebra(request:JsonObject,left:Many<A>,right:Many<A>,empty:()->Many<A>,append:(()->Many<A>,()->Many<A>)->Many<A>,sconcat:(()->Many<A>,List<Many<A>>)->Many<A>,mconcat:(List<Many<A>>)->Many<A>,stimes:(Long,()->Many<A>)->Many<A>):String= manyShow(when(request.getValue("method").jsonPrimitive.content){
 "append","mappend"->append({left},{right})
 "mempty"->empty()
 "mconcat"->mconcat(listOf(left,right,left))
 "sconcat"->sconcat({left},listOf(right,left))
 "stimes"->stimes(request.getValue("argument").jsonPrimitive.long){left}
 else->error("algebra method")
})
private fun manyObserve(request:JsonObject):String? {
 val input=request.getValue("values").jsonArray.map{it.jsonPrimitive.long};val many=Many.fromList(input);val argument=request.getValue("argument").jsonPrimitive.long
 return when(request.getValue("operation").jsonPrimitive.content){
  "replaceTrace"->many.replace{argument}.foldMap({it().toString()},trace)
  "mapMShape"->(ManyTraversable.mapM(many,ShapeA){x->ShapeA.Value(x,x().toString())} as ShapeA.Value).trace
  "sequenceAShape"->(ManyTraversable.sequenceA(many.fmap{x->ShapeA.Value(x,x().toString()) as WalkEffect<ShapeA.Tag,Long>},ShapeA) as ShapeA.Value).trace
  "sequenceShape"->(ManyTraversable.sequence(many.fmap{x->ShapeA.Value(x,x().toString()) as WalkEffect<ShapeA.Tag,Long>},ShapeA) as ShapeA.Value).trace
  "traverseShape"->(ManyTraversable.traverse(many,ShapeA){x->ShapeA.Value(x,x().toString())} as ShapeA.Value).trace
  "floatSum","floatProduct","floatMaximum","floatMinimum"->{
   val numbers=Many.fromList(request.getValue("numbers").jsonArray.map{it.jsonPrimitive.content.toDouble()})
   AstShow.show(when(request.getValue("operation").jsonPrimitive.content){"floatSum"->ManyFoldable.sum(numbers,doubleNumeric);"floatProduct"->ManyFoldable.product(numbers,doubleNumeric);"floatMaximum"->ManyFoldable.maximum(numbers,doubleOrder);else->ManyFoldable.minimum(numbers,doubleOrder)})
  }
  "fromChars"->manyShow(ManyInlines.fromString(io.heapy.kotdoc.layout.CharacterString(request.getValue("characters").jsonArray.map{it.jsonPrimitive.int}.toIntArray())))
  "algebra"->{
   val left=request.getValue("left").jsonPrimitive.content;val right=request.getValue("right").jsonPrimitive.content
   if(request.getValue("method").jsonPrimitive.content=="fromString")manyShow(ManyInlines.fromString(left))
   else if(request.getValue("kind").jsonPrimitive.content=="inline")algebra(request,Many.fromList(PandocRead.Inlines.read("Many {unMany = fromList "+left+"}").values),Many.fromList(PandocRead.Inlines.read("Many {unMany = fromList "+right+"}").values),ManyInlines::empty,(if(request.getValue("method").jsonPrimitive.content=="mappend")ManyInlines::mappend else ManyInlines::append),ManyInlines::sconcat,ManyInlines::mconcat,ManyInlines::stimes)
   else algebra(request,Many.fromList(PandocRead.Blocks.read("Many {unMany = fromList "+left+"}").values),Many.fromList(PandocRead.Blocks.read("Many {unMany = fromList "+right+"}").values),ManyBlocks::empty,(if(request.getValue("method").jsonPrimitive.content=="mappend")ManyBlocks::mappend else ManyBlocks::append),ManyBlocks::sconcat,ManyBlocks::mconcat,ManyBlocks::stimes)
  }
  "seqAppendTrace"->Many.fromTree{ManyTrees.append(many.tree.get(),emptyList(),Many.fromList(input.reversed()+7L).tree.get())}.foldMap({it().toString()},trace)
  "seqCycleTrace"->Many.fromTree{ManyRepetition.cycle(argument,many.tree.get())}.foldMap({it().toString()},trace)
  "seqCycle"->"fromList "+AstShow.show(Many.fromTree{ManyRepetition.cycle(argument,many.tree.get())}.toList())
  "constructor"->manyShow(Many(ManySequence.fromList(input)))
  "fromList"->manyShow(many)
  "unMany"->"fromList "+AstShow.show(many.unMany.toList())
  "singleton"->manyShow(Many.singleton{argument})
  "toList"->AstShow.show(ManyFoldable.toList(many))
  "isNull"->if(many.isNull())"True"else"False"
  "fmap"->manyShow(many.fmap{it()+1})
  "replace"->manyShow(many.replace{argument})
  "traverseList"->ListA.run(ManyTraversable.traverse(many,ListA){value->val x=value();ListA.Values(listOf(x,-x))}).joinToString(",","[","]",transform=::manyShow)
  "traverseMaybe"->when(val result=GenericMaybeMonad.run(ManyTraversable.traverse(many,ManyMonadApplicative(GenericMaybeMonad)){value->val x=value();if(x<0)GenericMaybeMonad.nothing()else GenericMaybeMonad.pure{x+1}})){
   GenericMaybe.None->"Nothing"
   is GenericMaybe.Some->"Just ("+manyShow(result.value)+")"
  }
  "traverse","sequenceA","mapM","sequence"->{
   val result=when(request.getValue("operation").jsonPrimitive.content){
    "traverse"->ManyTraversable.traverse(many,StateA,StateA::action)
    "mapM"->ManyTraversable.mapM(many,StateA,StateA::action)
    "sequenceA"->ManyTraversable.sequenceA(many.fmapLazy(StateA::action),StateA)
    else->ManyTraversable.sequence(many.fmapLazy(StateA::action),StateA)
   }
   val state=mutableListOf<Long>();val value=StateA.run(result,state);"("+manyShow(value)+","+AstShow.show(state)+")"
  }
  "fold"->ManyFoldable.fold(many,sum).toString()
  "foldMapPrime"->ManyFoldable.foldMapPrime(many,{it()},sum).toString()
  "foldMapPrimeTrace"->ManyFoldable.foldMapPrime(many,{it().toString()},trace)
  "foldr","foldrPrime"->(if(request.getValue("operation").jsonPrimitive.content=="foldr")ManyFoldable.foldr(many,{"z"}){a,b->"("+a()+">"+b()+")"}else ManyFoldable.foldrPrime(many,{"z"}){a,b->"("+a()+">"+b()+")"})
  "foldl","foldlPrime"->(if(request.getValue("operation").jsonPrimitive.content=="foldl")ManyFoldable.foldl(many,{"z"}){a,b->"("+a()+">"+b()+")"}else ManyFoldable.foldlPrime(many,{"z"}){a,b->"("+a()+">"+b()+")"})
  "foldr1"->ManyFoldable.foldr1(many){a,b->a()-b()}.toString()
  "foldl1"->ManyFoldable.foldl1(many){a,b->a()-b()}.toString()
  "null"->if(ManyFoldable.`null`(many))"True"else"False"
  "length"->ManyFoldable.length(many).toString()
  "elem"->if(ManyFoldable.elem(many,{argument}){a,b->a()==b()})"True"else"False"
  "maximum"->ManyFoldable.maximum(many,longOrder).toString()
  "minimum"->ManyFoldable.minimum(many,longOrder).toString()
  "sum"->ManyFoldable.sum(many,longNumeric).toString()
  "product"->ManyFoldable.product(many,longNumeric).toString()
  "foldMap"->many.foldMap({it()},sum).toString()
  "foldMapTrace"->many.foldMap({it().toString()},trace)
  else->null
 }
}
internal fun manyExecute(request:JsonObject):JsonElement=try{JsonPrimitive(manyObserve(request)?:error("unmapped request $request"))}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){var checked=0
 for(stream in ManyProofFixtures.streams){
  val requests=ManyProofFixtures.values("$stream-requests.jsonl");val responses=ManyProofFixtures.values("$stream-responses.jsonl")
  check(requests.size==responses.size)
  for((request,response)in requests.zip(responses)){val actual=manyExecute(request.jsonObject);check(actual==response){"$request\n$actual\n$response"};checked++}
 }
 println("$checked original Many core/Traversable/specialized algebra and Seq structure responses PASS")
}
