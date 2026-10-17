// pandoc-types Builder.Many: Copyright (c) 2010–2023 John MacFarlane.
// containers Seq: Ross Paterson 2005, Louis Wasserman 2009; Bertram Felgenhauer,
// David Feuer, Ross Paterson and Milan Straka 2014.
// GHC Classes/Show: (c) The University of Glasgow, 1992–2002.
// Exact headers: licenses/GHC-Read-Show-Attribution.txt; full base/ghc-prim BSD licenses retained.
package io.heapy.kotdoc.ast

/** Source callbacks observe represented intrinsic WHNF, not merely a JVM reference. */
class ManyDerivedThunk<A>(value:()->A):()->A {
    private val shared=GenericMemo {val result=value();GenericRepIntrinsic.force(result);result}
    override fun invoke():A=shared.get()
}

@Suppress("UNCHECKED_CAST")
internal fun <A> manyDerivedThunk(value:()->A):ManyDerivedThunk<A> =
    if(value is ManyDerivedThunk<*>)value as ManyDerivedThunk<A> else ManyDerivedThunk(value)

/** Explicit source callbacks receive shared arguments without demanding their roots. */
interface ManyElementEq<A> {
    fun equal(left: () -> A, right: () -> A): Boolean = !notEqual(left,right)
    fun notEqual(left: () -> A, right: () -> A): Boolean = !equal(left, right)
}
interface ManyElementOrd<A> : ManyElementEq<A> {
    fun compare(left: () -> A, right: () -> A): Int = if(equal(left,right))0 else if(lessEqual(left,right))-1 else 1
    fun less(left: () -> A, right: () -> A): Boolean = !lessEqual(right,left)
    fun lessEqual(left: () -> A, right: () -> A): Boolean = compare(left, right) <= 0
    fun greater(left: () -> A, right: () -> A): Boolean = !lessEqual(left,right)
    fun greaterEqual(left: () -> A, right: () -> A): Boolean = lessEqual(right,left)
    fun minimum(left: () -> A, right: () -> A): A = if (lessEqual(left,right)) left() else right()
    fun maximum(left: () -> A, right: () -> A): A = if (lessEqual(left,right)) right() else left()
}
class ManyEqDictionary<A>(private val element: ManyElementEq<A>) {
    fun equal(left: () -> Many<A>, right: () -> Many<A>): Boolean {
        val xs = ManyDerivedThunk(left); val ys = ManyDerivedThunk(right)
        // Seq's cached length guard precedes any element/list demand.
        if (xs().length != ys().length) return false
        val x = xs().cells(); val y = ys().cells()
        while (true) {
            val a = x.hasNext(); val b = y.hasNext()
            if (!a || !b) return a == b
            val av = x.next(); val bv = y.next()
            if (!element.equal(ManyDerivedThunk(av.value::get),ManyDerivedThunk(bv.value::get))) return false
        }
    }
    // The Seq default uses its own equality; element (/=) is not delegated to.
    fun notEqual(left: () -> Many<A>, right: () -> Many<A>) = !equal(left,right)
    fun equalLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{equal(left,right)}
    fun notEqualLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{notEqual(left,right)}
}
class ManyOrdDictionary<A>(private val element: ManyElementOrd<A>) {
    fun compareLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{compare(left,right)}
    fun lessLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{less(left,right)}
    fun lessEqualLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{lessEqual(left,right)}
    fun greaterLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{greater(left,right)}
    fun greaterEqualLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{greaterEqual(left,right)}
    fun minimumLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{minimum(left,right)}
    fun maximumLazy(left:()->Many<A>,right:()->Many<A>)=manyDerivedThunk{maximum(left,right)}
    fun compare(left: () -> Many<A>, right: () -> Many<A>): Int {
        val xs=ManyDerivedThunk(left); val ys=ManyDerivedThunk(right)
        val x=GenericMemo{xs().cells()}; val y=GenericMemo{ys().cells()}
        while(true) {
            val a=x.get().hasNext(); val b=y.get().hasNext()
            if (!a || !b) return if(a) 1 else if(b) -1 else 0
            val av=x.get().next(); val bv=y.get().next()
            val result=element.compare(ManyDerivedThunk(av.value::get),ManyDerivedThunk(bv.value::get))
            if(result!=0) return result.compareTo(0)
        }
    }
    fun less(left: () -> Many<A>,right: () -> Many<A>)=!lessEqual(right,left)
    fun lessEqual(left: () -> Many<A>,right: () -> Many<A>)=compare(left,right)<=0
    fun greater(left: () -> Many<A>,right: () -> Many<A>)=!lessEqual(left,right)
    fun greaterEqual(left: () -> Many<A>,right: () -> Many<A>)=lessEqual(right,left)
    fun minimum(left: () -> Many<A>,right: () -> Many<A>): Many<A> {
        val a=GenericMemo(left);val b=GenericMemo(right)
        return if(lessEqual(a::get,b::get))a.get() else b.get()
    }
    fun maximum(left: () -> Many<A>,right: () -> Many<A>): Many<A> {
        val a=GenericMemo(left);val b=GenericMemo(right)
        return if(lessEqual(a::get,b::get))b.get() else a.get()
    }
}
/** Haskell String/ShowS retain raw Char slots, lazy tails and unbounded prefixes. */
typealias ManyShowString = GenericList<Int>
interface ManyElementShow<A> {
    fun showsPrec(precedence: Long,value: () -> A,suffix: () -> ManyShowString): ManyShowString = GenericList.defer {ManyShowRuntime.append(show(value),suffix)}
    fun show(value: () -> A): ManyShowString = GenericList.defer{showsPrec(0,value,GenericList.Companion::empty)}
    fun showList(value: () -> GenericList<A>,suffix: () -> ManyShowString): ManyShowString =
        ManyShowRuntime.list(value,{v,rest->showsPrec(0,ManyDerivedThunk(v),rest)},suffix)
}
internal object ManyShowRuntime {
    fun append(first:ManyShowString,tail:()->ManyShowString):ManyShowString = GenericList.defer {
        when(val node=first.step()){GenericList.Step.Nil->tail();is GenericList.Step.Cons->GenericList.cons(node::head){append(node.tail(),tail)}}
    }
    fun text(text:String,tail:()->ManyShowString):ManyShowString {
        val points=text.codePoints().toArray()
        fun at(index:Int):ManyShowString=GenericList.defer {
            if(index==points.size)tail() else GenericList.cons({points[index]},{at(index+1)})
        }
        return at(0)
    }
    fun <A> list(value:()->GenericList<A>,show:(()->A,()->ManyShowString)->ManyShowString,suffix:()->ManyShowString):ManyShowString {
        fun items(input:()->GenericList<A>,first:Boolean):ManyShowString=GenericList.defer {
            when(val cell=input().step()) {
                GenericList.Step.Nil->text("]",suffix)
                is GenericList.Step.Cons->{
                    val render={show(ManyDerivedThunk(cell::head)){items(cell::tail,false)}}
                    if(first)render() else text(",",render)
                }
            }
        }
        return GenericList.defer {
            when(val root=value().step()) {
                GenericList.Step.Nil->text("[]",suffix)
                is GenericList.Step.Cons->text("["){show(ManyDerivedThunk(root::head)){items(root::tail,false)}}
            }
        }
    }
}
class ManyShowDictionary<A>(private val element:ManyElementShow<A>) {
    fun showsPrecLazy(precedence:()->Long,value:()->Many<A>,suffix:()->ManyShowString):ManyShowString = GenericList.defer{showsPrec(precedence(),value,suffix)}
    fun showsPrec(precedence:Long,value:()->Many<A>,suffix:()->ManyShowString):ManyShowString {
        val shared=ManyDerivedThunk(value)
        val close={ManyShowRuntime.text(if(precedence>10)"})" else "}",suffix)}
        // Record Show delegates Seq at precedence zero.
        val field={element.showList(ManyDerivedThunk{shared().toSourceList()},close)}
        return ManyShowRuntime.text(if(precedence>10)"(Many {unMany = fromList " else "Many {unMany = fromList ",field)
    }
    fun show(value:()->Many<A>):ManyShowString=showsPrec(0,value,GenericList.Companion::empty)
    fun showList(value:()->GenericList<Many<A>>,suffix:()->ManyShowString):ManyShowString =
        ManyShowRuntime.list(value,{v,rest->showsPrec(0,ManyDerivedThunk(v),rest)},suffix)
}
