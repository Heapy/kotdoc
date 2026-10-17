// Derived representation generation: (c) The University of Glasgow 2011.
// Derived Generic dictionaries of pandoc-types; (c)2006–2023 John MacFarlane.
// GHC.Internal.Generics: (c) Universiteit Utrecht 2010-2011, University of Oxford 2012-2014.
// Original libraries/base license and complete attribution retained in licenses/.
package io.heapy.kotdoc.ast

/** Source constructor carrier; fields are independently deferred and well typed. */
class GenericRepSource internal constructor(val declaration:String,val constructor:String,val fields:List<GenericRepField>):GenericRepRoot {
    private val demand=GenericMemo { fields.forEach{it.value()};this }
    fun whnf():GenericRepSource=demand.get()
    override fun forceGenericRepRoot(){whnf()}
}
object GenericRepData {
    private val source=GenericDefinitionData.declarations.associateBy{it.name}
    private fun fieldType(t:GenericTypeExpression)=GenericDefinitionData.resolve(t).type
    private fun split(n:Int)=n/2
    private fun tree(items:List<GenericRepType>,sum:Boolean):GenericRepType=when(items.size) {
        0->GenericRepType.Unit
        1->items.single()
        else->{val at=split(items.size);val l=tree(items.take(at),sum);val r=tree(items.drop(at),sum);if(sum)GenericRepType.Sum(l,r)else GenericRepType.Product(l,r)}
    }
    private fun selector(d:GenericDeclaration,c:GenericConstructor,i:Int):GenericRepMetadata.Selector {
        val t=c.fields[i]
        val unpack=t is GenericTypeExpression.N&&t.name in setOf("Int","Double","RowSpan","ColSpan","RowHeadColumns")
        return GenericRepMetadata.Selector(c.labels.getOrElse(i){""},"NoSourceUnpackedness","NoSourceStrictness",if(d.newtype)"DecidedLazy"else if(unpack)"DecidedUnpack"else"DecidedStrict")
    }
    private fun constructor(d:GenericDeclaration,c:GenericConstructor,types:(GenericTypeExpression)->GenericType):GenericRepType=GenericRepType.Metadata(
        GenericRepMetadata.Constructor(c.name,"Prefix",c.labels.isNotEmpty()),tree(c.fields.mapIndexed{i,t->GenericRepType.Metadata(selector(d,c,i),GenericRepType.Field(types(t)))},false))
    private val dictionaries=HashMap<String,GenericRepDictionary<GenericRepSource>>()
    fun definition(name:String):GenericRepDictionary<GenericRepSource> = synchronized(dictionaries){dictionaries.getOrPut(name){dictionary(source.getValue(name))}}
    fun source(name:String,constructor:String,fields:List<GenericRepField>):GenericRepSource {
        val spec=source.getValue(name).constructors.single{it.name==constructor}
        require(fields.size==spec.fields.size)
        require(fields.zip(spec.fields).all{(v,t)->v.type==fieldType(t)})
        return GenericRepSource(name,constructor,fields)
    }
    /** One parameterized instance, not only Inlines and Blocks. */
    fun many(element:GenericType):GenericRepDictionary<GenericRepSource> {
        val d=GenericDeclaration("Many",true,listOf(GenericConstructor("Many",listOf(GenericTypeExpression.N("Seq")),listOf("unMany"))))
        return dictionary(d,"Text.Pandoc.Builder"){GenericType("Data.Sequence.Internal","Seq",listOf(element),"containers")}
    }
    fun manySource(element:GenericType,field:GenericRepField):GenericRepSource {
        require(field.type==GenericType("Data.Sequence.Internal","Seq",listOf(element),"containers"))
        return GenericRepSource("Many","Many",listOf(field))
    }
    /** Exact source Seq root adapter, without measuring or visiting its elements. */
    fun <A> manySequence(element:GenericType,value:()->Many<A>):GenericRepField = GenericRepField(GenericType("Data.Sequence.Internal","Seq",listOf(element),"containers")) {
        value()
    }
    private fun dictionary(d:GenericDeclaration,module:String="Text.Pandoc.Definition",types:(GenericTypeExpression)->GenericType=::fieldType):GenericRepDictionary<GenericRepSource> = object:GenericRepDictionary<GenericRepSource> {
        override val representation=GenericRepType.Metadata(GenericRepMetadata.Datatype(d.name,module,"pandoc-types-1.23.1.2-inplace",d.newtype),tree(d.constructors.map{constructor(d,it,types)},true))
        override fun from(value:GenericRepThunk<GenericRepSource>):GenericRepValue {
            val input=GenericMemo { value().also{require(it.declaration==d.name)}.whnf() }
            fun fields(type:GenericRepType,offset:Int):GenericRepValue=GenericRepValue(type){when(type){
                GenericRepType.Unit->{input.get();GenericRepLayer.Unit}
                is GenericRepType.Metadata->GenericRepLayer.Metadata(fields(type.child,offset))
                is GenericRepType.Product->{input.get();GenericRepLayer.Product(fields(type.left,offset),fields(type.right,offset+countFields(type.left)))}
                is GenericRepType.Field->GenericRepLayer.Field(GenericRepField(type.type){input.get().fields[offset].value()})
                is GenericRepType.Sum->error("field sum")
            }}
            fun constructors(type:GenericRepType,offset:Int,count:Int):GenericRepValue=GenericRepValue(type){when(type){
                is GenericRepType.Sum->{val index=d.constructors.indexOfFirst{it.name==input.get().constructor};require(index>=offset&&index<offset+count);val left=split(count);val right=index>=offset+left;GenericRepLayer.Sum(right,if(right)constructors(type.right,offset+left,count-left)else constructors(type.left,offset,left))}
                is GenericRepType.Metadata->GenericRepLayer.Metadata(fields(type.child,0))
                else->error("constructor representation")
            }}
            return GenericRepValue(representation){GenericRepLayer.Metadata(constructors(representation.child,0,d.constructors.size))}
        }
        override fun to(value:GenericRepValue):GenericRepThunk<GenericRepSource> {
            require(value.type==representation)
            val output=GenericMemo {
                var rep=(value.layer() as GenericRepLayer.Metadata).child;var at=0;var count=d.constructors.size
                while(rep.type is GenericRepType.Sum){val part=rep.layer() as GenericRepLayer.Sum;val left=split(count);if(part.right){at+=left;count-=left}else count=left;rep=part.child}
                val constructor=d.constructors[at];val body=(rep.layer() as GenericRepLayer.Metadata).child
                prepareProducts(body)
                val fields=constructor.fields.indices.map{i->GenericRepField(types(constructor.fields[i])){field(body,i).value()}}
                GenericRepSource(d.name,constructor.name,fields).whnf()
            }
            return GenericRepThunk{output.get()}
        }
    }
    /** Bridge for fully defined existing AST values; partial values use source carriers. */
    fun <A> ast(name:String):GenericRepDictionary<A> = object:GenericRepDictionary<A> {
        private val dictionary=definition(name)
        private val bridge=GenericAstData.named<A>(name)
        private val data=GenericDefinitionData.named(name)
        override val representation=dictionary.representation
        override fun from(value:GenericRepThunk<A>):GenericRepValue=dictionary.from {
            val node=GenericValue(bridge,value).cast(data) as GenericNode
            source(name,node.constructor,node.fields.map{field->GenericRepField(field.data.type){field.value()}})
        }
        override fun to(value:GenericRepValue):GenericRepThunk<A> {
            val input=dictionary.to(value)
            val output=GenericMemo {
                val root=input();val c=source.getValue(name).constructors.single{it.name==root.constructor}
                val node=GenericDefinitionData.node(name,root.constructor,root.fields.mapIndexed{i,field->GenericValue(GenericDefinitionData.resolve(c.fields[i])){field.value()}})
                GenericValue(data){node}.cast(bridge)
            }
            return GenericRepThunk{output.get()}
        }
    }
    /** Fully defined finite bridge; its Data constraint belongs only to conversion. */
    fun <A,E> astMany(element:GenericData<E>,bridge:GenericData<A>):GenericRepDictionary<A> = object:GenericRepDictionary<A> {
        private val data=GenericDatas.many(element)
        private val sequence=GenericDatas.sequence(element)
        private val dictionary=many(element.type)
        init {require(bridge.type==data.type)}
        override val representation=dictionary.representation
        override fun from(value:GenericRepThunk<A>):GenericRepValue=dictionary.from {
            val canonical=GenericValue(bridge,value).cast(data)
            manySource(element.type,GenericRepField(sequence.type){canonical})
        }
        override fun to(value:GenericRepValue):GenericRepThunk<A> {
            val input=dictionary.to(value)
            return GenericRepThunk {
                @Suppress("UNCHECKED_CAST")
                val canonical=input().fields.single().value() as GenericSequence<E>
                GenericValue(data){canonical}.cast(bridge)
            }
        }
    }
    private fun needsPattern(type:GenericRepType):Boolean=when(type){
        is GenericRepType.Metadata->needsPattern(type.child)
        is GenericRepType.Product,GenericRepType.Unit->true
        else->false
    }
    /** Derived to matches the complete product pattern before constructing strict fields.
     * S1/K1 newtypes do not independently demand their contained field. */
    private fun prepareProducts(value:GenericRepValue) {
        val pending=ArrayDeque<GenericRepValue>();pending.addLast(value)
        while(pending.isNotEmpty()) {
            val current=pending.removeLast()
            if(!needsPattern(current.type))continue
            when(val node=current.layer()){
                is GenericRepLayer.Metadata->pending.addLast(node.child)
                is GenericRepLayer.Product->{pending.addLast(node.right);pending.addLast(node.left)}
                GenericRepLayer.Unit->Unit
                else->error("product pattern")
            }
        }
    }
    private fun countFields(type:GenericRepType):Int=when(type){GenericRepType.Unit->0;is GenericRepType.Field->1;is GenericRepType.Metadata->countFields(type.child);is GenericRepType.Product->countFields(type.left)+countFields(type.right);is GenericRepType.Sum->error("field sum")}
    private fun field(value:GenericRepValue,index:Int):GenericRepField=when(val node=value.layer()){
        is GenericRepLayer.Metadata->field(node.child,index)
        is GenericRepLayer.Product->{val count=countFields(node.left.type);if(index<count)field(node.left,index)else field(node.right,index-count)}
        is GenericRepLayer.Field->{require(index==0);node.value}
        else->error("field representation")
    }
}
