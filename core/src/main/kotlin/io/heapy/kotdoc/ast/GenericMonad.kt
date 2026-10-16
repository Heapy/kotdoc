// Source lazy Monad dictionary used by Data.gmapM; BSD-3-Clause.
// GHC runtime semantics ©University of Glasgow; retained GHC-base license.
package io.heapy.kotdoc.ast

/** Unlike a strict JVM bind, both the action and its result are source thunks.
 * A dictionary decides which actions/results it demands. WalkMonad overloads
 * remain convenient stack-safe adapters for strict effect sequencing. */
interface GenericMonad<F> {
    fun <A> pure(value:()->A):WalkEffect<F,A>
    fun <A,B> flatMap(value:()->WalkEffect<F,A>,next:(()->A)->WalkEffect<F,B>):WalkEffect<F,B>
}
object GenericIdentity:GenericMonad<GenericIdentity.Tag> {
    class Tag
    class Value<A>(value:()->A):WalkEffect<Tag,A> {private val result=GenericMemo(value);fun get():A=result.get()}
    override fun <A> pure(value:()->A):WalkEffect<Tag,A> = Value(value)
    override fun <A,B> flatMap(value:()->WalkEffect<Tag,A>,next:(()->A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> {
        val action=GenericMemo(value)
        return Value{run(next{run(action.get())})}
    }
    fun <A> run(value:WalkEffect<Tag,A>):A=(value as Value<A>).get()
}
/** Reader's source bind shares the environment and leaves result fields lazy. */
class GenericReader<R>:GenericMonad<GenericReader.Tag<R>> {
    class Tag<R>
    class Value<R,A>(val run:(R)->A):WalkEffect<Tag<R>,A>
    override fun <A> pure(value:()->A):WalkEffect<Tag<R>,A> {val result=GenericMemo(value);return Value{result.get()}}
    override fun <A,B> flatMap(value:()->WalkEffect<Tag<R>,A>,next:(()->A)->WalkEffect<Tag<R>,B>):WalkEffect<Tag<R>,B> = Value{environment->
        val result=GenericMemo{run(value(),environment)}
        run(next(result::get),environment)
    }
    fun <A> run(value:WalkEffect<Tag<R>,A>,environment:R):A=(value as Value<R,A>).run(environment)
}

@Suppress("UNCHECKED_CAST")
internal fun <F,A> genericMonadic(monad:GenericMonad<F>,input:GenericValue<*>,target:GenericData<A>,transform:(()->A)->WalkEffect<F,A>):WalkEffect<F,GenericValue<*>> {
    val data=input.data as GenericData<Any?>
    fun children():WalkEffect<F,GenericValue<*>> {
        val layer=data.layer(input::erased)
        var collected:()->WalkEffect<F,List<GenericValue<*>>> = {monad.pure{emptyList()}}
        for(child in layer.fields) {
            val previous=collected
            collected={monad.flatMap(previous){before->monad.flatMap({genericMonadic(monad,child,target,transform)}){after->
                monad.pure{before()+GenericValue(child.data as GenericData<Any?>){after().erased()}}
            }}}
        }
        return monad.flatMap(collected){fields->monad.pure{GenericValue(data){layer.rebuild(fields())}}}
    }
    return monad.flatMap(::children){rebuilt->
        if(data.type==target.type)monad.flatMap({transform{rebuilt().cast(target)}}){result->
            monad.pure{GenericValue(data){data.fromCanonical(target.toCanonical(result()))}}
        } else monad.pure(rebuilt)
    }
}
object GenericMaybeMonad:GenericMonad<GenericMaybeMonad.Tag> {
    class Tag
    class Value<A>(val result:GenericMaybe<A>):WalkEffect<Tag,A>
    override fun <A> pure(value:()->A):WalkEffect<Tag,A> = Value(GenericMaybe.Some(value))
    fun <A> nothing():WalkEffect<Tag,A> = Value(GenericMaybe.None)
    override fun <A,B> flatMap(value:()->WalkEffect<Tag,A>,next:(()->A)->WalkEffect<Tag,B>):WalkEffect<Tag,B> = when(val result=run(value())) {
        GenericMaybe.None->nothing()
        is GenericMaybe.Some->next{result.value}
    }
    fun <A> run(value:WalkEffect<Tag,A>):GenericMaybe<A> =(value as Value<A>).result
}
