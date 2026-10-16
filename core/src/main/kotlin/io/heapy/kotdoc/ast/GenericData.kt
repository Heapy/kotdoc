// Structural Data protocol for pandoc-types/syb traversal.
// GHC Data: © University of Glasgow; licenses/GHC-base-BSD-3-Clause.txt.
// Text: ©2009–2012 Bryan O'Sullivan; ©2009 Duncan Coutts;
// ©2008–2009 Tom Harper; ©2021 Andrew Lelechenko (BSD-style).
// Map: ©Daan Leijen 2002, Andriy Palamarchuk 2008 (BSD-style).
// Seq: ©Ross Paterson 2005, Louis Wasserman 2009;
// ©Bertram Felgenhauer, David Feuer, Ross Paterson, Milan Straka 2014.
// Full containers/text source notices and licenses retained in reference tools/vendor.
// Copyright (c) 2006–2023 John MacFarlane; BSD-3-Clause, see licenses/.
package io.heapy.kotdoc.ast

/** Source Typeable identity. Aliases share a key; distinct newtypes do not. */
data class GenericType(val module: String, val name: String, val arguments: List<GenericType> = emptyList(), val library: String = "pandoc-types") {
    /** GHC 9.12 TyCon spelling; source syntax names remain convenient keys. */
    val tyConName:String get()=when(name){"[]"->"List";"(,)"->"Tuple2";"(,,)"->"Tuple3";else->name}
}

/** Caller-defined Data dictionary, including source representation adapters.
 * Dictionaries sharing a source key must use the same canonical representation. */
interface GenericData<A> {
    val type: GenericType
    fun layer(value: () -> A): GenericLayer<A>
    fun force(value: () -> A): A = value()
    fun toCanonical(value: A): Any? = value
    @Suppress("UNCHECKED_CAST") fun fromCanonical(value: Any?): A = value as A
}
class GenericLayer<A>(val fields: List<GenericValue<*>>, val rebuild: (List<GenericValue<*>>) -> A)
class GenericValue<A>(val data: GenericData<A>, value: () -> A) {
    private val input = GenericMemo { data.force(value) }
    fun value(): A = input.get()
    internal fun erased(): Any? = value()
    internal fun canonical(): Any? = data.toCanonical(value())
    fun <B> cast(dictionary: GenericData<B>): B {
        require(data.type == dictionary.type) { "Source types differ: ${data.type} / ${dictionary.type}" }
        @Suppress("UNCHECKED_CAST")
        if(data===dictionary)return value() as B
        return dictionary.fromCanonical(canonical())
    }
}
internal class GenericMemo<A>(value: () -> A) {
    private val result by lazy { runCatching(value) }
    fun get(): A = result.getOrThrow()
}

private fun <A,B> genericMapList(input:List<A>,f:(A)->B):GenericList<B> = GenericList.defer {
    when(val step=GenericList.view(input).step()) {
        GenericList.Step.Nil->GenericList.empty()
        is GenericList.Step.Cons->GenericList.cons({f(step.head())},{genericMapList(step.tail(),f)})
    }
}

/** Source list constructors, with independent head/tail thunks. Also a finite
 * Kotlin List view; source traversal uses step(), never total size. */
class GenericList<A> private constructor(step: () -> Step<A>) : AbstractList<A>() {
    sealed interface Step<out A> {
        data object Nil : Step<Nothing>
        class Cons<A>(head: () -> A, tail: () -> GenericList<A>) : Step<A> {
            private val headValue = GenericMemo(head)
            private val tailValue = GenericMemo(tail)
            fun head(): A = headValue.get()
            fun tail(): GenericList<A> = tailValue.get()
        }
    }
    private val cell = GenericMemo(step)
    fun step(): Step<A> = cell.get()
    private val length = GenericMemo {
        var current = this; var count = 0
        while (true) when (val node = current.step()) {
            Step.Nil -> return@GenericMemo count
            is Step.Cons -> { count = Math.addExact(count, 1); current = node.tail() }
        }
        @Suppress("UNREACHABLE_CODE") 0
    }
    override val size: Int get() = length.get()
    override fun get(index: Int): A {
        if(index < 0) throw IndexOutOfBoundsException("index: $index")
        var current = this; var at = index
        while(true) when(val node=current.step()) {
            Step.Nil -> throw IndexOutOfBoundsException("index: $index")
            is Step.Cons -> if(at-- == 0) return node.head() else current=node.tail()
        }
    }
    override fun iterator(): Iterator<A> = object : Iterator<A> {
        private var current: () -> GenericList<A> = { this@GenericList }
        override fun hasNext(): Boolean = current().step() is Step.Cons
        override fun next(): A = when(val node=current().step()) {
            Step.Nil -> throw NoSuchElementException()
            is Step.Cons -> { current={node.tail()};node.head() }
        }
    }
    companion object {
        fun <A> empty(): GenericList<A> = GenericList { Step.Nil }
        fun <A> cons(head: () -> A, tail: () -> GenericList<A>): GenericList<A> = GenericList { Step.Cons(head,tail) }
        fun <A> defer(value: () -> GenericList<A>): GenericList<A> = GenericList { value().step() }
        fun <A> view(values: List<A>, offset: Int = 0): GenericList<A> =
            if(values is GenericList<A> && offset == 0)values else GenericList {
                if(offset == values.size)Step.Nil else Step.Cons({values[offset]},{view(values,offset+1)})
            }
    }
}
class GenericTuple2<A,B>(first: () -> A, second: () -> B) {
    private val left=GenericMemo(first);private val right=GenericMemo(second)
    val first:A get()=left.get();val second:B get()=right.get()
}
class GenericTuple3<A,B,C>(first: () -> A, second: () -> B, third: () -> C) {
    private val left=GenericMemo(first);private val middle=GenericMemo(second);private val right=GenericMemo(third)
    val first:A get()=left.get();val second:B get()=middle.get();val third:C get()=right.get()
}
sealed interface GenericMaybe<out A> {
    data object None:GenericMaybe<Nothing>
    class Some<A>(value:()->A):GenericMaybe<A> {private val input=GenericMemo(value);val value:A get()=input.get()}
}

/** Seq's Data instance exposes viewl, hence head and every Seq tail. */
class GenericSequence<A> private constructor(val length:Int,view:()->View<A>) {
    sealed interface View<out A> {
        data object Empty:View<Nothing>
        class Cons<A>(head:()->A,tail:()->GenericSequence<A>):View<A> {
            private val first=GenericMemo(head);private val rest=GenericMemo(tail)
            fun head():A=first.get();fun tail():GenericSequence<A> = rest.get()
        }
    }
    private val cell=GenericMemo(view)
    fun view():View<A> = cell.get()
    fun <B> map(f:(A)->B):GenericSequence<B> = GenericSequence(length){when(val v=view()){View.Empty->View.Empty;is View.Cons->View.Cons({f(v.head())},{v.tail().map(f)})}}
    fun toList():List<A> = GenericList.defer {when(val v=view()){View.Empty->GenericList.empty();is View.Cons->GenericList.cons({v.head()},{GenericList.view(v.tail().toList())})}}
    companion object {
        fun <A> empty():GenericSequence<A> = GenericSequence(0){View.Empty}
        fun <A> cons(head:()->A,tail:()->GenericSequence<A>):GenericSequence<A> {
            val rest=GenericMemo(tail);val length=Math.addExact(rest.get().length,1)
            return GenericSequence(length){View.Cons(head,rest::get)}
        }
        fun <A> fromList(values:List<A>):GenericSequence<A> {
            fun at(index:Int):GenericSequence<A> = GenericSequence(values.size-index){if(index==values.size)View.Empty else View.Cons({values[index]},{at(index+1)})}
            return at(0)
        }
    }
}
private class GenericMap<K,V>(private val cells:java.util.SortedMap<K,GenericMemo<V>>):AbstractMap<K,V>() {
    override val entries:Set<Map.Entry<K,V>> = object:AbstractSet<Map.Entry<K,V>>() {
        override val size:Int get()=cells.size
        override fun iterator():Iterator<Map.Entry<K,V>> = cells.entries.asSequence().map { cell->object:Map.Entry<K,V> {
            override val key:K get()=cell.key
            override val value:V get()=cell.value.get()
            override fun equals(other:Any?):Boolean=other is Map.Entry<*,*> && this.key==other.key && this.value==other.value
            override fun hashCode():Int=(this.key?.hashCode()?:0) xor (this.value?.hashCode()?:0)
        }}.iterator()
    }
}

/** Compositional dependency dictionaries. Full AST dictionaries follow these
 * source structures rather than Kotlin reflection or restricted Walk shapes. */
object GenericDatas {
    private fun primitive(name:String):GenericType=GenericType("GHC.Types",name,library="ghc-prim")
    private fun <A> scalar(type:GenericType):GenericData<A> = object:GenericData<A> {
        override val type=type
        override fun layer(value:()->A)=GenericLayer(emptyList()){value()}
    }
    val int:GenericData<Long> = scalar(primitive("Int"))
    val double:GenericData<Double> = scalar(primitive("Double"))
    /** Haskell Char includes surrogate code points; it is not Kotlin Char. */
    val char:GenericData<Int> = scalar(primitive("Char"))
    val boolean:GenericData<Boolean> = object:GenericData<Boolean> {
        override val type=primitive("Bool")
        override fun layer(value:()->Boolean):GenericLayer<Boolean> {val input=value();return GenericLayer(emptyList()){input}}
    }
    fun <A> list(element:GenericData<A>):GenericData<List<A>> = object:GenericData<List<A>> {
        override val type=GenericType("GHC.Types","[]",listOf(element.type),"ghc-prim")
        override fun toCanonical(value:List<A>):Any=genericMapList(value,element::toCanonical)
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):List<A> = genericMapList(value as List<Any?>,element::fromCanonical)
        override fun force(value:()->List<A>):List<A> = GenericList.view(value()).also{it.step()}
        override fun layer(value:()->List<A>):GenericLayer<List<A>> {
            val source=GenericList.view(value())
            return when(val step=source.step()) {
                GenericList.Step.Nil -> GenericLayer(emptyList()){GenericList.empty()}
                is GenericList.Step.Cons -> GenericLayer(listOf(GenericValue(element){step.head()},GenericValue(this){step.tail()})){fields->
                    GenericList.cons({fields[0].cast(element)},{GenericList.view(fields[1].cast(this))})
                }
            }
        }
    }
    fun <A,B> tuple2(a:GenericData<A>,b:GenericData<B>):GenericData<GenericTuple2<A,B>> = object:GenericData<GenericTuple2<A,B>> {
        override val type=GenericType("GHC.Tuple","(,)",listOf(a.type,b.type),"ghc-prim")
        override fun toCanonical(value:GenericTuple2<A,B>):Any=GenericTuple2({a.toCanonical(value.first)},{b.toCanonical(value.second)})
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):GenericTuple2<A,B> = (value as GenericTuple2<Any?,Any?>).let{GenericTuple2({a.fromCanonical(it.first)},{b.fromCanonical(it.second)})}
        override fun layer(value:()->GenericTuple2<A,B>):GenericLayer<GenericTuple2<A,B>> {
            val pair=value()
            return GenericLayer(listOf(GenericValue(a){pair.first},GenericValue(b){pair.second})){fields->GenericTuple2({fields[0].cast(a)},{fields[1].cast(b)})}
        }
    }
    fun <A,B> pair(a:GenericData<A>,b:GenericData<B>):GenericData<Pair<A,B>> = object:GenericData<Pair<A,B>> {
        private val source=tuple2(a,b)
        override val type=source.type
        override fun toCanonical(value:Pair<A,B>):Any?=source.toCanonical(GenericTuple2({value.first},{value.second}))
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):Pair<A,B> = source.fromCanonical(value).let{it.first to it.second}
        override fun layer(value:()->Pair<A,B>):GenericLayer<Pair<A,B>> {
            val pair=value()
            return GenericLayer(listOf(GenericValue(a){pair.first},GenericValue(b){pair.second})){fields->fields[0].cast(a) to fields[1].cast(b)}
        }
    }
    fun <A,B,C> tuple3(a:GenericData<A>,b:GenericData<B>,c:GenericData<C>):GenericData<GenericTuple3<A,B,C>> = object:GenericData<GenericTuple3<A,B,C>> {
        override val type=GenericType("GHC.Tuple","(,,)",listOf(a.type,b.type,c.type),"ghc-prim")
        override fun toCanonical(value:GenericTuple3<A,B,C>):Any=GenericTuple3({a.toCanonical(value.first)},{b.toCanonical(value.second)},{c.toCanonical(value.third)})
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):GenericTuple3<A,B,C> = (value as GenericTuple3<Any?,Any?,Any?>).let{GenericTuple3({a.fromCanonical(it.first)},{b.fromCanonical(it.second)},{c.fromCanonical(it.third)})}
        override fun layer(value:()->GenericTuple3<A,B,C>):GenericLayer<GenericTuple3<A,B,C>> {
            val tuple=value()
            return GenericLayer(listOf(GenericValue(a){tuple.first},GenericValue(b){tuple.second},GenericValue(c){tuple.third})){fields->GenericTuple3({fields[0].cast(a)},{fields[1].cast(b)},{fields[2].cast(c)})}
        }
    }
    fun <A> maybe(element:GenericData<A>):GenericData<GenericMaybe<A>> = object:GenericData<GenericMaybe<A>> {
        override val type=GenericType("GHC.Internal.Maybe","Maybe",listOf(element.type),"ghc-internal")
        override fun toCanonical(value:GenericMaybe<A>):Any=when(value){GenericMaybe.None->GenericMaybe.None;is GenericMaybe.Some->GenericMaybe.Some{element.toCanonical(value.value)}}
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):GenericMaybe<A> = when(val input=value as GenericMaybe<Any?>){GenericMaybe.None->GenericMaybe.None;is GenericMaybe.Some->GenericMaybe.Some{element.fromCanonical(input.value)}}
        override fun layer(value:()->GenericMaybe<A>):GenericLayer<GenericMaybe<A>> = when(val input=value()) {
            GenericMaybe.None -> GenericLayer(emptyList()){GenericMaybe.None}
            is GenericMaybe.Some -> GenericLayer(listOf(GenericValue(element){input.value})){fields->GenericMaybe.Some{fields[0].cast(element)}}
        }
    }
    fun <A:Any> nullable(element:GenericData<A>):GenericData<A?> = object:GenericData<A?> {
        override val type=maybe(element).type
        override fun toCanonical(value:A?):Any=if(value==null)GenericMaybe.None else GenericMaybe.Some{element.toCanonical(value)}
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):A?=when(val input=value as GenericMaybe<Any?>){GenericMaybe.None->null;is GenericMaybe.Some->element.fromCanonical(input.value)}
        override fun layer(value:()->A?):GenericLayer<A?> = value().let { input->
            if(input==null)GenericLayer(emptyList()){null} else GenericLayer(listOf(GenericValue(element){input})){fields->fields[0].cast(element)}
        }
    }
    fun <A> newtype(type:GenericType,field:GenericData<A>):GenericData<A> = object:GenericData<A> {
        override val type=type
        override fun toCanonical(value:A):Any?=field.toCanonical(value)
        override fun fromCanonical(value:Any?):A=field.fromCanonical(value)
        override fun force(value:()->A):A=field.force(value)
        override fun layer(value:()->A)=GenericLayer(listOf(GenericValue(field,value))){fields->fields[0].cast(field)}
    }
    fun <A> sequence(element:GenericData<A>):GenericData<GenericSequence<A>> = object:GenericData<GenericSequence<A>> {
        override val type=GenericType("Data.Sequence.Internal","Seq",listOf(element.type),"containers")
        override fun toCanonical(value:GenericSequence<A>):Any=value.map(element::toCanonical)
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):GenericSequence<A> = (value as GenericSequence<Any?>).map(element::fromCanonical)
        override fun layer(value:()->GenericSequence<A>):GenericLayer<GenericSequence<A>> = when(val view=value().view()) {
            GenericSequence.View.Empty -> GenericLayer(emptyList()){GenericSequence.empty()}
            is GenericSequence.View.Cons -> GenericLayer(listOf(GenericValue(element){view.head()},GenericValue(this){view.tail()})){fields->GenericSequence.cons({fields[0].cast(element)},{fields[1].cast(this)})}
        }
    }
    fun <A> many(element:GenericData<A>):GenericData<GenericSequence<A>> = newtype(GenericType("Text.Pandoc.Builder","Many",listOf(element.type)),sequence(element))
    fun <K,V> map(key:GenericData<K>,item:GenericData<V>,order:Comparator<K>):GenericData<Map<K,V>> = object:GenericData<Map<K,V>> {
        private val pair=tuple2(key,item)
        private val contents=list(pair)
        override val type=GenericType("Data.Map.Internal","Map",listOf(key.type,item.type),"containers")
        override fun toCanonical(value:Map<K,V>):Any { val keyData=key;return object:AbstractMap<Any?,Any?>(){
            override val entries:Set<Map.Entry<Any?,Any?>> = object:AbstractSet<Map.Entry<Any?,Any?>>() {
                override val size get()=value.size
                override fun iterator():Iterator<Map.Entry<Any?,Any?>> = value.entries.asSequence().map{entry->object:Map.Entry<Any?,Any?> {
                    override val key get()=keyData.toCanonical(entry.key)
                    override val value get()=item.toCanonical(entry.value)
                    override fun equals(other:Any?):Boolean=other is Map.Entry<*,*> && this.key==other.key && this.value==other.value
                    override fun hashCode():Int=(this.key?.hashCode()?:0) xor (this.value?.hashCode()?:0)
                }}.iterator()
            }
        }}
        @Suppress("UNCHECKED_CAST") override fun fromCanonical(value:Any?):Map<K,V> {
            val input=value as Map<Any?,Any?>;val cells=java.util.TreeMap<K,GenericMemo<V>>(order)
            for(entry in input.entries)cells[key.fromCanonical(entry.key)]=GenericMemo{item.fromCanonical(entry.value)}
            return GenericMap(cells)
        }
        override fun layer(value:()->Map<K,V>):GenericLayer<Map<K,V>> = GenericLayer(listOf(GenericValue(contents){
            val input=value();val keys=input.keys.sortedWith(order)
            fun entries(index:Int):GenericList<GenericTuple2<K,V>> = if(index==keys.size)GenericList.empty() else GenericList.cons({GenericTuple2({keys[index]},{input.getValue(keys[index])})},{entries(index+1)})
            entries(0)
        })){fields->
            val cells=java.util.TreeMap<K,GenericMemo<V>>(order)
            for(pair in fields[0].cast(contents))cells[pair.first]=GenericMemo{pair.second}
            GenericMap(cells)
        }
    }
    private fun characters(value:()->String,index:Int=0):GenericList<Int> = GenericList.defer {
        val input=value()
        if(index==input.length)GenericList.empty() else {
            val cp=input.codePointAt(index)
            GenericList.cons({cp},{characters(value,index+Character.charCount(cp))})
        }
    }
    val text:GenericData<String> = object:GenericData<String> {
        override val type=GenericType("Data.Text.Internal","Text",library="text")
        private val string=list(char)
        override fun layer(value:()->String)=GenericLayer(listOf(GenericValue(string){characters(value)})){fields->
            buildString { for(cp in fields[0].cast(string))appendCodePoint(if(cp in 0xD800..0xDFFF)0xFFFD else cp) }
        }
    }
}
