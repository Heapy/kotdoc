// GHC.Internal.Data.Data: (c) The University of Glasgow, CWI 2001--2004.
// Full original source attribution and base BSD license retained in reference/ licenses.
package io.heapy.kotdoc.ast

/** A shared represented source value. Unknown JVM carriers must implement the
 * open GenericRepRoot protocol when their intrinsic WHNF has work to do. */
class DataThunk<A>(value:()->A):()->A {
    private val shared=GenericMemo { @Suppress("UNCHECKED_CAST") (GenericRepIntrinsic.force(value()) as A) }
    override fun invoke():A=shared.get()
}
interface DataKind<F,A>:GenericRepRoot
fun interface DataFunction<A,B> {fun apply(value:DataThunk<A>):DataThunk<B>}
class DataRepresentation<A>(val type:GenericType,private val force:(()->A)->A={it()}) {
    fun argument(value:()->A)=DataThunk {force(value)}
}
class DataEvidence<A>(val representation:DataRepresentation<A>,dictionary:()->DataDictionary<A>) {
    private val shared=GenericMemo(dictionary)
    fun dictionary():DataDictionary<A> = shared.get()
    fun argument(value:()->A)=representation.argument(value)
}
interface DataFold<F> {
    fun forceInject() {}
    fun <D> forceApply(evidence:DataEvidence<D>) {}
    fun <A> inject(value:DataThunk<A>):DataKind<F,A>
    fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<F,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<F,B>
}
interface DataUnfold<F> {
    fun forceInject() {}
    fun <D> forceApply(evidence:DataEvidence<D>) {}
    fun <A> inject(value:DataThunk<A>):DataKind<F,A>
    fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<F,DataFunction<D,B>>>):DataKind<F,B>
}
interface DataTransform {fun <A> forceRoot(evidence:DataEvidence<A>) {}
    fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A}
interface DataQuery<R> {fun <A> forceRoot(evidence:DataEvidence<A>) {}
    fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):R}
interface DataMonadicTransform<F> {fun <A> forceRoot(evidence:DataEvidence<A>) {}
    fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<F,A>}
interface DataMonad<F> {
    fun <A> pure(value:DataThunk<A>):DataKind<F,A>
    fun <A,B> bind(value:DataThunk<DataKind<F,A>>,next:(DataThunk<A>)->DataKind<F,B>):DataKind<F,B>
}
interface DataMonadPlus<F>:DataMonad<F> {
    fun <A> zero():DataKind<F,A>
    fun <A> plus(left:DataThunk<DataKind<F,A>>,right:DataThunk<DataKind<F,A>>):DataKind<F,A>
}

/** All14 source members; callback dictionaries are not assumed lawful.
 * Public PandocData applications delay whole virtual dispatch as well as fields. */
abstract class DataDictionary<A>(val representation:DataRepresentation<A>) {
    val evidence:DataEvidence<A> by lazy {DataEvidence(representation){this}}
    open fun <F> gfoldl(fold:DataFold<F>,value:DataThunk<A>):DataKind<F,A> = run {fold.forceInject();fold.inject(value)}
    abstract fun <F> gunfold(unfold:DataUnfold<F>,constructor:DataThunk<DataConstr>):DataKind<F,A>
    abstract fun toConstr(value:DataThunk<A>):DataConstr
    abstract fun dataTypeOf(value:DataThunk<A>):DataTypeInfo
    open fun <F> dataCast1(constructor:DataThunk<GenericType>,value:DataCast1<F>):GenericMaybe<DataKind<F,A>> = GenericMaybe.None
    open fun <F> dataCast2(constructor:DataThunk<GenericType>,value:DataCast2<F>):GenericMaybe<DataKind<F,A>> = GenericMaybe.None
    open fun gmapT(transform:DataTransform,value:DataThunk<A>):A = DataDefaults.map(this,transform,value)
    open fun <R,P> gmapQl(combine:(DataThunk<R>,DataThunk<P>)->R,initial:DataThunk<R>,query:DataQuery<P>,value:DataThunk<A>):R = DataDefaults.left(this,combine,initial,query,value)
    open fun <R,P> gmapQr(combine:(DataThunk<P>,DataThunk<R>)->R,initial:DataThunk<R>,query:DataQuery<P>,value:DataThunk<A>):R = DataDefaults.right(this,combine,initial,query,value)
    open fun <R> gmapQ(query:DataQuery<R>,value:DataThunk<A>):GenericList<R> = gmapQr({head,tail->GenericList.cons(head){tail()}},DataThunk{GenericList.empty()},query,value)
    open fun <R> gmapQi(index:DataThunk<Long>,query:DataQuery<R>,value:DataThunk<A>):R = DataDefaults.index(this,index,query,value)
    open fun <F> gmapM(monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>):DataKind<F,A> = DataDefaults.monadic(this,monad,transform,value)
    open fun <F> gmapMp(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>):DataKind<F,A> = DataDefaults.optional(this,monad,transform,value,false)
    open fun <F> gmapMo(monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>):DataKind<F,A> = DataDefaults.optional(this,monad,transform,value,true)
}
interface DataCast1<F> {fun <A> value(argument:DataEvidence<A>):DataKind<F,Any?>}
interface DataCast2<F> {fun <A,B> value(left:DataEvidence<A>,right:DataEvidence<B>):DataKind<F,Any?>}

object PandocData {
    fun <F,A> gfoldl(dictionary:DataDictionary<A>,fold:DataFold<F>,value:()->A)=DataThunk{dictionary.gfoldl(fold,dictionary.representation.argument(value))}
    fun <F,A> gunfold(dictionary:DataDictionary<A>,unfold:DataUnfold<F>,constructor:()->DataConstr)=DataThunk{dictionary.gunfold(unfold,DataThunk(constructor))}
    fun <A> toConstr(dictionary:DataDictionary<A>,value:()->A)=DataThunk{dictionary.toConstr(dictionary.representation.argument(value))}
    fun <A> dataTypeOf(dictionary:DataDictionary<A>,value:()->A)=DataThunk{dictionary.dataTypeOf(dictionary.representation.argument(value))}
    fun <F,A> dataCast1(dictionary:DataDictionary<A>,constructor:()->GenericType,value:DataCast1<F>)=DataThunk{dictionary.dataCast1(DataThunk(constructor),value)}
    fun <F,A> dataCast2(dictionary:DataDictionary<A>,constructor:()->GenericType,value:DataCast2<F>)=DataThunk{dictionary.dataCast2(DataThunk(constructor),value)}
    fun <A> gmapT(dictionary:DataDictionary<A>,transform:DataTransform,value:()->A)=DataThunk{dictionary.gmapT(transform,dictionary.representation.argument(value))}
    fun <A,R,P> gmapQl(dictionary:DataDictionary<A>,combine:(DataThunk<R>,DataThunk<P>)->R,initial:()->R,query:DataQuery<P>,value:()->A)=DataThunk{dictionary.gmapQl(combine,DataThunk(initial),query,dictionary.representation.argument(value))}
    fun <A,R,P> gmapQr(dictionary:DataDictionary<A>,combine:(DataThunk<P>,DataThunk<R>)->R,initial:()->R,query:DataQuery<P>,value:()->A)=DataThunk{dictionary.gmapQr(combine,DataThunk(initial),query,dictionary.representation.argument(value))}
    fun <A,R> gmapQ(dictionary:DataDictionary<A>,query:DataQuery<R>,value:()->A)=DataThunk{dictionary.gmapQ(query,dictionary.representation.argument(value))}
    fun <A,R> gmapQi(dictionary:DataDictionary<A>,index:()->Long,query:DataQuery<R>,value:()->A)=DataThunk{dictionary.gmapQi(DataThunk(index),query,dictionary.representation.argument(value))}
    fun <F,A> gmapM(dictionary:DataDictionary<A>,monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:()->A)=DataThunk{dictionary.gmapM(monad,transform,dictionary.representation.argument(value))}
    fun <F,A> gmapMp(dictionary:DataDictionary<A>,monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:()->A)=DataThunk{dictionary.gmapMp(monad,transform,dictionary.representation.argument(value))}
    fun <F,A> gmapMo(dictionary:DataDictionary<A>,monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:()->A)=DataThunk{dictionary.gmapMo(monad,transform,dictionary.representation.argument(value))}
    // Source dictionary expressions can themselves be bottom. These overloads
    // defer selection and share both successful results and failed applications.
    fun <F,A> gfoldl(dictionary:()->DataDictionary<A>,fold:DataFold<F>,value:()->A)=DataThunk{gfoldl(dictionary(),fold,value)()}
    fun <F,A> gunfold(dictionary:()->DataDictionary<A>,unfold:DataUnfold<F>,constructor:()->DataConstr)=DataThunk{gunfold(dictionary(),unfold,constructor)()}
    fun <A> toConstr(dictionary:()->DataDictionary<A>,value:()->A)=DataThunk{toConstr(dictionary(),value)()}
    fun <A> dataTypeOf(dictionary:()->DataDictionary<A>,value:()->A)=DataThunk{dataTypeOf(dictionary(),value)()}
    fun <F,A> dataCast1(dictionary:()->DataDictionary<A>,constructor:()->GenericType,value:DataCast1<F>)=DataThunk{dataCast1(dictionary(),constructor,value)()}
    fun <F,A> dataCast2(dictionary:()->DataDictionary<A>,constructor:()->GenericType,value:DataCast2<F>)=DataThunk{dataCast2(dictionary(),constructor,value)()}
    fun <A> gmapT(dictionary:()->DataDictionary<A>,transform:DataTransform,value:()->A)=DataThunk{gmapT(dictionary(),transform,value)()}
    fun <A,R,P> gmapQl(dictionary:()->DataDictionary<A>,combine:(DataThunk<R>,DataThunk<P>)->R,initial:()->R,query:DataQuery<P>,value:()->A)=DataThunk{gmapQl(dictionary(),combine,initial,query,value)()}
    fun <A,R,P> gmapQr(dictionary:()->DataDictionary<A>,combine:(DataThunk<P>,DataThunk<R>)->R,initial:()->R,query:DataQuery<P>,value:()->A)=DataThunk{gmapQr(dictionary(),combine,initial,query,value)()}
    fun <A,R> gmapQ(dictionary:()->DataDictionary<A>,query:DataQuery<R>,value:()->A)=DataThunk{gmapQ(dictionary(),query,value)()}
    fun <A,R> gmapQi(dictionary:()->DataDictionary<A>,index:()->Long,query:DataQuery<R>,value:()->A)=DataThunk{gmapQi(dictionary(),index,query,value)()}
    fun <F,A> gmapM(dictionary:()->DataDictionary<A>,monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:()->A)=DataThunk{gmapM(dictionary(),monad,transform,value)()}
    fun <F,A> gmapMp(dictionary:()->DataDictionary<A>,monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:()->A)=DataThunk{gmapMp(dictionary(),monad,transform,value)()}
    fun <F,A> gmapMo(dictionary:()->DataDictionary<A>,monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:()->A)=DataThunk{gmapMo(dictionary(),monad,transform,value)()}

}

object DataIdentity:DataMonad<DataIdentity.Tag> {
    class Tag
    class Value<A>(val value:DataThunk<A>):DataKind<Tag,A> {override fun forceGenericRepRoot(){value()}}
    override fun <A> pure(value:DataThunk<A>):DataKind<Tag,A> = Value(value)
    override fun <A,B> bind(value:DataThunk<DataKind<Tag,A>>,next:(DataThunk<A>)->DataKind<Tag,B>):DataKind<Tag,B> = Value(DataThunk{run(next(DataThunk{run(value())}))})
    fun <A> run(value:DataKind<Tag,A>):A=(value as Value<A>).value()
}
private class ConstTag<R>
private class DataConst<R,A>(val value:DataThunk<R>):DataKind<ConstTag<R>,A> {override fun forceGenericRepRoot(){value()}}
private class QrTag<R,P>
private class DataQr<R,P,A>(val previous:DataThunk<DataQr<R,P,*>>?,val query:DataThunk<P>?):DataKind<QrTag<R,P>,A> {
    override fun forceGenericRepRoot() {} // source newtype around a function
    fun run(combine:(DataThunk<P>,DataThunk<R>)->R,initial:DataThunk<R>):R {
        val queries=ArrayList<DataThunk<P>>();var current:DataQr<R,P,*> = this
        while(current.previous!=null){queries.add(current.query!!);current=current.previous!!()}
        var result=initial
        for(query in queries){val tail=result;result=DataThunk{combine(query,tail)}}
        return result()
    }
}
private class QiTag<R>
private class DataQi<R,A>(val previous:DataQi<R,*>?,val query:DataThunk<R>?,val count:Long):DataKind<QiTag<R>,A> {
    override fun forceGenericRepRoot() {} // source data Qi: lazy counter/Maybe fields
    fun at(index:DataThunk<Long>):R {var current:DataQi<R,*> = this
        while(current.previous!=null){if(index()==current.count-1)return current.query!!();current=current.previous!!}
        error("Maybe.fromJust: Nothing")
    }
}
private class MpTag<F>
private class DataMp<F,A>(val action:DataThunk<DataKind<F,GenericTuple2<A,Boolean>>>):DataKind<MpTag<F>,A> {override fun forceGenericRepRoot(){action()}}

private object DataDefaults {
    fun <A> map(dictionary:DataDictionary<A>,transform:DataTransform,value:DataThunk<A>):A {
        val fold=object:DataFold<DataIdentity.Tag> {
            override fun <G> inject(value:DataThunk<G>):DataKind<DataIdentity.Tag,G> = DataIdentity.pure(value)
            override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<DataIdentity.Tag,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<DataIdentity.Tag,B> = DataIdentity.pure(DataThunk{DataIdentity.run(function()).apply(evidence.argument{DataInvocation.apply(transform,evidence,value)})()})
        }
        return DataIdentity.run(PandocData.gfoldl(dictionary,fold,value)())
    }
    fun <A,R,P> left(dictionary:DataDictionary<A>,combine:(DataThunk<R>,DataThunk<P>)->R,initial:DataThunk<R>,query:DataQuery<P>,value:DataThunk<A>):R {
        val fold=object:DataFold<ConstTag<R>> {
            override fun <G> inject(value:DataThunk<G>):DataKind<ConstTag<R>,G> = DataConst(initial)
            override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<ConstTag<R>,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<ConstTag<R>,B> = DataConst(DataThunk{combine(DataThunk{(function() as DataConst<R,*>).value()},DataThunk{DataInvocation.apply(query,evidence,value)})})
        }
        return (PandocData.gfoldl(dictionary,fold,value)() as DataConst<R,A>).value()
    }
    fun <A,R,P> right(dictionary:DataDictionary<A>,combine:(DataThunk<P>,DataThunk<R>)->R,initial:DataThunk<R>,query:DataQuery<P>,value:DataThunk<A>):R {
        val fold=object:DataFold<QrTag<R,P>> {
            override fun <G> inject(value:DataThunk<G>):DataKind<QrTag<R,P>,G> = DataQr(null,null)
            override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<QrTag<R,P>,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<QrTag<R,P>,B> = DataQr(DataThunk{function() as DataQr<R,P,*>},DataThunk{DataInvocation.apply(query,evidence,value)})
        }
        return (PandocData.gfoldl(dictionary,fold,value)() as DataQr<R,P,A>).run(combine,initial)
    }
    fun <A,R> index(dictionary:DataDictionary<A>,index:DataThunk<Long>,query:DataQuery<R>,value:DataThunk<A>):R {
        val fold=object:DataFold<QiTag<R>> {
            override fun <G> inject(value:DataThunk<G>):DataKind<QiTag<R>,G> = DataQi(null,null,0)
            override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<QiTag<R>,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<QiTag<R>,B> {val before=function() as DataQi<R,*>;return DataQi(before,DataThunk{DataInvocation.apply(query,evidence,value)},before.count+1)}
        }
        return (PandocData.gfoldl(dictionary,fold,value)() as DataQi<R,A>).at(index)
    }
    fun <F,A> monadic(dictionary:DataDictionary<A>,monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>):DataKind<F,A> {
        val fold=object:DataFold<F> {
            override fun <G> inject(value:DataThunk<G>):DataKind<F,G> = monad.pure(value)
            override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<F,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<F,B> = monad.bind(function){f->monad.bind(DataThunk{DataInvocation.apply(transform,evidence,value)}){x->monad.pure(DataThunk{f().apply(x)()})}}
        }
        return PandocData.gfoldl(dictionary,fold,value)()
    }
    fun <F,A> optional(dictionary:DataDictionary<A>,monad:DataMonadPlus<F>,transform:DataMonadicTransform<F>,value:DataThunk<A>,one:Boolean):DataKind<F,A> {
        val fold=object:DataFold<MpTag<F>> {
            override fun <G> inject(value:DataThunk<G>):DataKind<MpTag<F>,G> = DataMp(DataThunk{monad.pure(DataThunk{GenericTuple2(value){false}})})
            override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<MpTag<F>,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<MpTag<F>,B> = DataMp(DataThunk {
                monad.bind(DataThunk{(function() as DataMp<F,DataFunction<D,B>>).action()}){pair->
                    val p=pair()
                    fun unchanged():DataKind<F,GenericTuple2<B,Boolean>> = monad.pure(DataThunk{GenericTuple2({p.first.apply(value)()}){p.second}})
                    if(one&&p.second)unchanged() else monad.plus(DataThunk{monad.bind(DataThunk{DataInvocation.apply(transform,evidence,value)}){changed->monad.pure(DataThunk{GenericTuple2({p.first.apply(changed)()}){true}})}},DataThunk{unchanged()})
                }
            })
        }
        return monad.bind(DataThunk{(PandocData.gfoldl(dictionary,fold,value)() as DataMp<F,A>).action()}){pair->val p=pair();if(p.second)monad.pure(DataThunk{p.first})else monad.zero()}
    }
}

/** Separate source function roots: k and z can be bottom independently. */
interface DataInject<F> {fun <A> inject(value:DataThunk<A>):DataKind<F,A>}
interface DataFoldApply<F> {fun <D> forceRoot(evidence:DataEvidence<D>) {}
    fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<F,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<F,B>}
interface DataUnfoldApply<F> {fun <D> forceRoot(evidence:DataEvidence<D>) {}
    fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<F,DataFunction<D,B>>>):DataKind<F,B>}
object DataCallbacks {
    fun <R> query(factory:()->DataQuery<R>):DataQuery<R> = object:DataQuery<R> {
        private val root=GenericMemo(factory)
        override fun <A> forceRoot(evidence:DataEvidence<A>){root.get().forceRoot(evidence)}
        override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):R {forceRoot(evidence);return root.get().apply(evidence,value)}
    }
    fun transform(factory:()->DataTransform):DataTransform = object:DataTransform {
        private val root=GenericMemo(factory)
        override fun <A> forceRoot(evidence:DataEvidence<A>){root.get().forceRoot(evidence)}
        override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):A {forceRoot(evidence);return root.get().apply(evidence,value)}
    }
    fun <F> monadic(factory:()->DataMonadicTransform<F>):DataMonadicTransform<F> = object:DataMonadicTransform<F> {
        private val root=GenericMemo(factory)
        override fun <A> forceRoot(evidence:DataEvidence<A>){root.get().forceRoot(evidence)}
        override fun <A> apply(evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<F,A> {forceRoot(evidence);return root.get().apply(evidence,value)}
    }
    fun <F> fold(inject:()->DataInject<F>,apply:()->DataFoldApply<F>):DataFold<F> = object:DataFold<F> {
        private val z=GenericMemo(inject);private val k=GenericMemo(apply)
        override fun forceInject(){z.get()}
        override fun <D> forceApply(evidence:DataEvidence<D>){k.get().forceRoot(evidence)}
        override fun <A> inject(value:DataThunk<A>):DataKind<F,A> = z.get().inject(value)
        override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<F,DataFunction<D,B>>>,value:DataThunk<D>):DataKind<F,B> = k.get().apply(evidence,function,value)
    }
    fun <F> unfold(inject:()->DataInject<F>,apply:()->DataUnfoldApply<F>):DataUnfold<F> = object:DataUnfold<F> {
        private val z=GenericMemo(inject);private val k=GenericMemo(apply)
        override fun forceInject(){z.get()}
        override fun <D> forceApply(evidence:DataEvidence<D>){k.get().forceRoot(evidence)}
        override fun <A> inject(value:DataThunk<A>):DataKind<F,A> = z.get().inject(value)
        override fun <D,B> apply(evidence:DataEvidence<D>,function:DataThunk<DataKind<F,DataFunction<D,B>>>):DataKind<F,B> = k.get().apply(evidence,function)
    }
}

internal object DataInvocation {
    fun <A> apply(transform:DataTransform,evidence:DataEvidence<A>,value:DataThunk<A>):A {
        transform.forceRoot(evidence);return transform.apply(evidence,value)
    }
    fun <R,A> apply(query:DataQuery<R>,evidence:DataEvidence<A>,value:DataThunk<A>):R {
        query.forceRoot(evidence);return query.apply(evidence,value)
    }
    fun <F,A> apply(transform:DataMonadicTransform<F>,evidence:DataEvidence<A>,value:DataThunk<A>):DataKind<F,A> {
        transform.forceRoot(evidence);return transform.apply(evidence,value)
    }
}
