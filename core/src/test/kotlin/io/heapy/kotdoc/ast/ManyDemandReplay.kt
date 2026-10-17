package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private fun <A> poisoned(first:()->A,second:()->A):List<A> = object:AbstractList<A>(){override val size=2;override fun get(index:Int):A=when(index){0->first();1->second();else->throw IndexOutOfBoundsException(index)}}
private val any=object:GenericMonoid<Boolean>{override fun empty()=false;override fun append(left:()->Boolean,right:()->Boolean)=left()||right()}
private fun bool(value:Boolean)=if(value)"True"else"False"
private fun manyDemandObserve(name:String):String {
 fun partial():Many<Long> = Many.fromTree{ManyTree.Deep(listOf(ManyCell.Leaf{1L}),{error("middle")},listOf(ManyCell.Leaf{2L}),10)}
 fun poisonHead():Many<Long> = Many.fromList(poisoned({error("head")},{2L}))
 return when(name){
  "fmap-head"->AstShow.show(poisonHead().fmap{7L}.toList().take(1))
  "replace-head"->AstShow.show(poisonHead().replace{7L}.toList().take(1))
  "foldr-ignore"->ManyFoldable.foldr<Long,Long>(poisonHead(),{error("seed")}){_,_->7L}.toString()
  "foldl-ignore"->ManyFoldable.foldl<Long,Long>(poisonHead(),{error("seed")}){_,_->7L}.toString()
  "foldr-prime-ignore"->ManyFoldable.foldrPrime<Long,Long>(poisonHead(),{error("seed")}){_,_->7L}.toString()
  "foldl-prime-ignore"->ManyFoldable.foldlPrime<Long,Long>(poisonHead(),{error("seed")}){_,_->7L}.toString()
  "foldr1-ignore"->ManyFoldable.foldr1(Many.fromList(poisoned({7L},{error("tail")}))){x,_->x()}.toString()
  "foldl1-ignore"->ManyFoldable.foldl1(Many.fromList(poisoned({error("head")},{7L}))){_,y->y()}.toString()
  "foldmap-ignore"->bool(Many.fromList(poisoned({1L},{error("tail")})).foldMap({it()==1L},any))
  "foldmap-prime-ignore"->bool(ManyFoldable.foldMapPrime(Many.fromList(poisoned({1L},{error("tail")})),{it()==1L},any))
  "elem-ignore"->bool(ManyFoldable.elem(Many.fromList(poisoned({1L},{error("tail")})),{1L}){x,y->x()==y()})
  "identity-length"->GenericIdentity.run(ManyTraversable.traverse(Many.fromList(listOf(1L,2L,3L)),ManyMonadApplicative(GenericIdentity)){GenericIdentity.pure<Long>{error("mapped")}}).length.toString()
  "maybe-fail"->when(GenericMaybeMonad.run(ManyTraversable.traverse(Many.fromList(poisoned({1L},{error("tail")})),ManyMonadApplicative(GenericMaybeMonad)){x->if(x()==1L)GenericMaybeMonad.nothing<Long>()else GenericMaybeMonad.pure(x)})){GenericMaybe.None->"Nothing";is GenericMaybe.Some->"Just"}
  "partial-null"->bool(partial().isNull())
  "partial-length"->partial().length.toString()
  "partial-fmap-length"->partial().fmap{7L}.length.toString()
  "partial-replace-length"->partial().replace{7L}.length.toString()
  "partial-foldr"->ManyFoldable.foldr<Long,Long>(partial(),{error("seed")}){x,_->x()}.toString()
  "partial-foldl"->ManyFoldable.foldl<Long,Long>(partial(),{error("seed")}){_,x->x()}.toString()
  "partial-foldr1"->ManyFoldable.foldr1(partial()){x,_->x()}.toString()
  "partial-foldl1"->ManyFoldable.foldl1(partial()){_,y->y()}.toString()
  "partial-foldmap"->bool(partial().foldMap({it()==1L},any))
  "partial-foldmap-prime"->bool(ManyFoldable.foldMapPrime(partial(),{it()==1L},any))
  "partial-identity-length"->GenericIdentity.run(ManyTraversable.traverse(partial(),ManyMonadApplicative(GenericIdentity)){GenericIdentity.pure{7L}}).length.toString()
  "blocks-negative-root"->ManyBlocks.stimes(-1){Many(ManySequence.deferred<Block>{error("root")})}.length.toString()
  "inline-negative-root"->ManyInlines.stimes(-1){Many(ManySequence.deferred<Inline>{error("root")})}.length.toString()
  "blocks-huge-length"->ManyBlocks.stimes(1099511627776){Many.singleton<Block>{error("head")}}.length.toString()
  "blocks-huge-list-head"->{val list=ManyFoldable.toList(ManyBlocks.stimes(1099511627776){Many.singleton{Block.HorizontalRule}});AstShow.show(listOf((list.step() as GenericList.Step.Cons).head()))}
  "blocks-integer-wrap"->ManyBlocks.stimes(java.math.BigInteger("18446744073709551618")){Many.singleton{Block.HorizontalRule}}.length.toString()
  "blocks-integer-negative-wrap"->ManyBlocks.stimes(java.math.BigInteger("18446744073709551615")){Many.singleton{Block.HorizontalRule}}.length.toString()
  "inline-integer-space"->ManyInlines.stimes(java.math.BigInteger("1267650600228229401496703205376")){Many.singleton{Inline.Space}}.length.toString()
  "inline-huge-space"->ManyInlines.stimes(1099511627776){Many.singleton{Inline.Space}}.length.toString()
  "inline-empty-right"->ManyInlines.append({Many.singleton{error("head")}},{ManyInlines.empty()}).length.toString()
  "blocks-partial-append-length","blocks-partial-repeat-length"->{
   fun value():Many<Block> = Many.fromTree{ManyTree.Deep(List(2){ManyCell.Leaf{Block.HorizontalRule}},{error("middle")},List(2){ManyCell.Leaf{Block.HorizontalRule}},10)}
   (if(name=="blocks-partial-append-length")ManyBlocks.append(::value,::value)else ManyBlocks.stimes(2,::value)).length.toString()
  }
  "inline-emph-head"->ManyInlines.append({Many.singleton{Inline.Emph(object:AbstractList<Inline>(){override val size=1;override fun get(index:Int):Inline=error("head")})}},{Many.singleton{Inline.Emph(emptyList())}}).length.toString()
  "custom-eq-direction"->bool(ManyFoldable.elem(Many.singleton{3L},{2L}){x,y->x()<y()})
  "custom-max-method","custom-min-method"->{val order=object:ManyOrder<Long>{override fun greaterEqual(left:()->Long,right:()->Long)=true;override fun lessEqual(left:()->Long,right:()->Long)=false};(if(name=="custom-max-method")ManyFoldable.maximum(Many.fromList(listOf(1L,2L)),order)else ManyFoldable.minimum(Many.fromList(listOf(1L,2L)),order)).toString()}
  "max-singleton-method","min-singleton-method"->{val order=object:ManyOrder<Long>{override fun greaterEqual(left:()->Long,right:()->Long):Boolean=error("greater");override fun lessEqual(left:()->Long,right:()->Long):Boolean=error("less")};(if(name.startsWith("max"))ManyFoldable.maximum(Many.singleton{1L},order)else ManyFoldable.minimum(Many.singleton{1L},order)).toString()}
  "max-head-before-middle","min-head-before-middle"->{val value=Many.fromTree{ManyTree.Deep(listOf(ManyCell.Leaf<Long>{error("head")},ManyCell.Leaf{2L}),{error("middle")},listOf(ManyCell.Leaf{3L}),10)};val order=object:ManyOrder<Long>{override fun greaterEqual(left:()->Long,right:()->Long)=left()>=right();override fun lessEqual(left:()->Long,right:()->Long)=left()<=right()};(if(name.startsWith("max"))ManyFoldable.maximum(value,order)else ManyFoldable.minimum(value,order)).toString()}
  "custom-min-payload"->ManyFoldable.minimum(Many.fromList(object:AbstractList<Long>(){override val size=3;override fun get(index:Int):Long=if(index==1)error("payload")else index+1L}),object:ManyOrder<Long>{override fun greaterEqual(left:()->Long,right:()->Long)=true;override fun lessEqual(left:()->Long,right:()->Long)=false}).toString()
  "custom-sum-demand","custom-product-demand"->{val numeric=object:ManyNumeric<Long>{override fun zero()=0L;override fun one()=1L;override fun plus(left:()->Long,right:()->Long)=7L;override fun times(left:()->Long,right:()->Long)=9L};val values=Many.fromList(poisoned<Long>({error("head")},{error("tail")}));(if(name=="custom-sum-demand")ManyFoldable.sum(values,numeric)else ManyFoldable.product(values,numeric)).toString()}
  // StrictData makes Emph undefined itself bottom; the source element thunk fails.
  "inline-emph-content"->ManyInlines.append({Many.singleton<Inline>{error("content")}},{Many.singleton{Inline.Emph(emptyList())}}).length.toString()
  else->error("demand case $name")
 }
}
internal fun manyDemandExecute(name:String):JsonElement=try{JsonPrimitive(manyDemandObserve(name))}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){var count=0
 for(pair in ManyProofFixtures.values("demand-native.jsonl").chunked(2)){val name=pair[0].jsonPrimitive.content;val expected=pair[1];val actual=manyDemandExecute(name);check(actual==expected){"$name: $actual != $expected"};count++}
 println("$count original Many demand responses PASS")
}
