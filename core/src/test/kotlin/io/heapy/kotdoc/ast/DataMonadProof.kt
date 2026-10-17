package io.heapy.kotdoc.ast

internal object DataProofMaybe:DataMonadPlus<DataProofMaybe.Tag> {
 class Tag
 class Effect<A>(val value:DataThunk<GenericMaybe<A>>):DataKind<Tag,A>{override fun forceGenericRepRoot(){value()}}
 override fun <A> pure(value:DataThunk<A>):DataKind<Tag,A> = Effect(DataThunk{GenericMaybe.Some{value()}})
 override fun <A,B> bind(value:DataThunk<DataKind<Tag,A>>,next:(DataThunk<A>)->DataKind<Tag,B>):DataKind<Tag,B> = Effect(DataThunk{
  when(val input=run(value())) {GenericMaybe.None->GenericMaybe.None;is GenericMaybe.Some->run(next(DataThunk{input.value}))}
 })
 override fun <A> zero():DataKind<Tag,A> = Effect(DataThunk{GenericMaybe.None})
 override fun <A> plus(left:DataThunk<DataKind<Tag,A>>,right:DataThunk<DataKind<Tag,A>>):DataKind<Tag,A> = Effect(DataThunk{
  when(val input=run(left())){GenericMaybe.None->run(right());is GenericMaybe.Some->input}
 })
 fun <A> run(value:DataKind<Tag,A>):GenericMaybe<A> = (value as Effect<A>).value()
}
internal object DataProofList:DataMonadPlus<DataProofList.Tag> {
 class Tag
 class Effect<A>(val value:DataThunk<GenericList<A>>):DataKind<Tag,A>{override fun forceGenericRepRoot(){value().step()}}
 override fun <A> pure(value:DataThunk<A>):DataKind<Tag,A> = Effect(DataThunk{GenericList.cons(value){GenericList.empty()}})
 private fun <A> append(left:GenericList<A>,right:()->GenericList<A>):GenericList<A> = GenericList.defer {
  when(val step=left.step()){GenericList.Step.Nil->right();is GenericList.Step.Cons->GenericList.cons(step::head){append(step.tail(),right)}}
 }
 private fun <A,B> bindList(value:()->GenericList<A>,next:(DataThunk<A>)->DataKind<Tag,B>):GenericList<B> = GenericList.defer {
  when(val step=value().step()){GenericList.Step.Nil->GenericList.empty();is GenericList.Step.Cons->append(run(next(DataThunk(step::head)))){bindList(step::tail,next)}}
 }
 override fun <A,B> bind(value:DataThunk<DataKind<Tag,A>>,next:(DataThunk<A>)->DataKind<Tag,B>):DataKind<Tag,B> = Effect(DataThunk{bindList({run(value())},next)})
 override fun <A> zero():DataKind<Tag,A> = Effect(DataThunk{GenericList.empty()})
 override fun <A> plus(left:DataThunk<DataKind<Tag,A>>,right:DataThunk<DataKind<Tag,A>>):DataKind<Tag,A> = Effect(DataThunk{append(run(left())){run(right())}})
 fun <A> run(value:DataKind<Tag,A>):GenericList<A> = (value as Effect<A>).value()
}

internal class DataProofState<S>:DataMonad<DataProofState.Tag<S>> {
 class Tag<S>
 class Effect<S,A>(val run:(S)->GenericTuple2<A,S>):DataKind<Tag<S>,A>{override fun forceGenericRepRoot(){}}
 override fun <A> pure(value:DataThunk<A>):DataKind<Tag<S>,A> = Effect{state->GenericTuple2(value){state}}
 override fun <A,B> bind(value:DataThunk<DataKind<Tag<S>,A>>,next:(DataThunk<A>)->DataKind<Tag<S>,B>):DataKind<Tag<S>,B> = Effect{state->
  val before=run(value(),state)
  run(next(DataThunk{before.first}),before.second)
 }
 fun <A> action(run:(S)->GenericTuple2<A,S>):DataKind<Tag<S>,A> = Effect(run)
 fun <A> run(value:DataKind<Tag<S>,A>,state:S):GenericTuple2<A,S> = (value as Effect<S,A>).run(state)
}
