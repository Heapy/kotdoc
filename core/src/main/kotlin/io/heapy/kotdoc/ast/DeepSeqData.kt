// NFData dictionaries for the pinned pandoc-types Definition declarations.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; original notice retained.
// Control.DeepSeq: (c) The University of Glasgow 2001-2009, BSD-style.
// Map NFData: (c) Daan Leijen 2002, Andriy Palamarchuk 2008, BSD-style.
package io.heapy.kotdoc.ast

@Suppress("UNCHECKED_CAST")
private fun NFData<*>.erased():NFData<Any?> = this as NFData<Any?>

class DeepSeqNode internal constructor(val declaration:String,val constructor:String,val fields:List<DeepSeqValue<*>>)
class DeepSeqTuple(val fields:List<DeepSeqValue<*>>)

/** Raw source Map constructor carrier. Finite Kotlin Map bridges are separate. */
class DeepSeqMap private constructor(root:()->Step) {
    sealed interface Step {
        data object Tip:Step
        class Bin(val size:DeepSeqValue<Long>,val key:DeepSeqValue<*>,val item:DeepSeqValue<*>,left:()->DeepSeqMap,right:()->DeepSeqMap):Step {
            private val l=GenericMemo(left);private val r=GenericMemo(right)
            fun left()=l.get();fun right()=r.get()
        }
    }
    private val input=GenericMemo(root)
    fun step()=input.get()
    private var spineReady=false
    private var spineFailure:Throwable?=null
    internal fun forceSpine() {
        spineFailure?.let{throw it}
        val pending=ArrayDeque<Pair<()->DeepSeqMap,Boolean>>()
        pending.addLast(({this}) to false)
        try {
            while(pending.isNotEmpty()) {
                val (get,exit)=pending.removeLast();val node=get()
                node.spineFailure?.let{throw it}
                if(node.spineReady)continue
                if(exit){node.spineReady=true;continue}
                when(val root=node.step()) {
                    Step.Tip->node.spineReady=true
                    is Step.Bin->{
                        root.size.value();root.key.value()
                        pending.addLast(({node}) to true)
                        pending.addLast(({root.right()}) to false)
                        pending.addLast(({root.left()}) to false)
                    }
                }
            }
        } catch(failure:Throwable) {spineFailure=failure;throw failure}
    }
    companion object {
        fun tip()=DeepSeqMap{Step.Tip}
        fun bin(size:()->Long,key:DeepSeqValue<*>,item:DeepSeqValue<*>,left:()->DeepSeqMap,right:()->DeepSeqMap)=DeepSeqMap{Step.Bin(DeepSeqValue(DeepSeqData.long,size),key,item,left,right)}
        fun defer(root:()->DeepSeqMap)=DeepSeqMap{root().step()}
    }
}

private abstract class SourceNFData<A>:PlannedNFData<A> {
    open fun whnf(value:()->A):A=value()
}
object DeepSeqData {
    internal fun sourceWHNF(value:Any?):Any? {
        when(value) {
            is DeepSeqNode -> (named(value.declaration) as SourceNFData<Any?>).whnf { value }
            is GenericList<*> -> value.step()
            is DeepSeqMap -> value.forceSpine()
        }
        return value
    }

    private fun <A> scalar():NFData<A> = object:SourceNFData<A>() {
        override fun children(value:()->A):Iterator<DeepSeqValue<*>> {whnf(value);return emptyList<DeepSeqValue<*>>().iterator()}
    }
    val long:NFData<Long> = scalar()
    val double:NFData<Double> = scalar()
    val boolean:NFData<Boolean> = scalar()
    val char:NFData<Int> = scalar()
    /** Text's NFData uses only seq; Data's unpack field is deliberately absent. */
    val text:NFData<String> = scalar()
    @Suppress("UNCHECKED_CAST")
    fun whnf(value:DeepSeqValue<*>):Any? {
        val dictionary=value.dictionary
        return if(dictionary is SourceNFData<*>) (dictionary as SourceNFData<Any?>).whnf{value.value()} else value.value()
    }
    private val declarations=GenericDefinitionData.declarations.associateBy{it.name}
    private val dictionaries=HashMap<String,NFData<Any?>>()
    @Suppress("UNCHECKED_CAST")
    fun named(name:String):NFData<Any?> {
        val primitive:NFData<*>?=when(name){"Int"->long;"Double"->double;"Bool"->boolean;"Char"->char;"Text"->text;else->null}
        if(primitive!=null)return primitive as NFData<Any?>
        GenericDefinitionData.aliases[name]?.let{return resolve(it)}
        return synchronized(dictionaries){dictionaries.getOrPut(name){declaration(declarations.getValue(name))}}
    }
    private fun declaration(spec:GenericDeclaration):NFData<Any?> = object:SourceNFData<Any?>() {
        override fun whnf(value:()->Any?):Any? {
            val node=value() as DeepSeqNode;require(node.declaration==spec.name)
            val constructor=spec.constructors.single{it.name==node.constructor}
            require(node.fields.size==constructor.fields.size)
            // StrictData and newtype representation both demand each field WHNF.
            for(field in boundFields(node))DeepSeqData.whnf(field)
            return node
        }
        private fun boundFields(node:DeepSeqNode):List<DeepSeqValue<*>> {
            val constructor=spec.constructors.single{it.name==node.constructor}
            // The pinned instance owns these constraints. A field's input carrier
            // cannot replace NFData [Inline], Text, etc. with another dictionary.
            return node.fields.zip(constructor.fields).map{(field,type)->DeepSeqValue(resolve(type)){field.value()}}
        }
        override fun children(value:()->Any?):Iterator<DeepSeqValue<*>> = boundFields(whnf(value) as DeepSeqNode).iterator()
    }
    fun node(name:String,constructor:String,fields:List<DeepSeqValue<*>>):DeepSeqValue<Any?> {
        val spec=declarations.getValue(name).constructors.single{it.name==constructor}
        require(fields.size==spec.fields.size)
        return DeepSeqValue(named(name)){DeepSeqNode(name,constructor,fields)}
    }
    @Suppress("UNCHECKED_CAST")
    fun resolve(type:GenericTypeExpression):NFData<Any?> = when(type) {
        is GenericTypeExpression.N->named(type.name)
        is GenericTypeExpression.L->list(resolve(type.element)) as NFData<Any?>
        is GenericTypeExpression.T->tuple(type.fields.map(::resolve)) as NFData<Any?>
        is GenericTypeExpression.A->when(type.name){
            "Maybe"->maybe(resolve(type.arguments.single())) as NFData<Any?>
            "M.Map"->map(resolve(type.arguments[0]),resolve(type.arguments[1])) as NFData<Any?>
            else->error("Unsupported NFData application $type")
        }
    }
    fun <A> list(item:NFData<A>):NFData<List<A>> = object:SourceNFData<List<A>>() {
        override fun whnf(value:()->List<A>):List<A> = GenericList.view(value()).also{it.step()}
        override fun children(value:()->List<A>):Iterator<DeepSeqValue<*>> {
            val input=GenericList.view(whnf(value))
            return when(val step=input.step()) {
                GenericList.Step.Nil->emptyList<DeepSeqValue<*>>().iterator()
                is GenericList.Step.Cons->listOf(DeepSeqValue(item){step.head()},DeepSeqValue(this){step.tail()}).iterator()
            }
        }
    }
    fun tuple(fields:List<NFData<*>>):NFData<DeepSeqTuple> = object:SourceNFData<DeepSeqTuple>() {
        override fun children(value:()->DeepSeqTuple):Iterator<DeepSeqValue<*>> {
            val input=value();require(input.fields.size==fields.size)
            return fields.indices.asSequence().map{i->DeepSeqValue(fields[i].erased()){input.fields[i].value()}}.iterator()
        }
    }
    fun <A> maybe(item:NFData<A>):NFData<GenericMaybe<A>> = object:SourceNFData<GenericMaybe<A>>() {
        override fun children(value:()->GenericMaybe<A>):Iterator<DeepSeqValue<*>> = when(val input=value()) {
            GenericMaybe.None->emptyList<DeepSeqValue<*>>().iterator()
            is GenericMaybe.Some->listOf(DeepSeqValue(item){input.value}).iterator()
        }
    }
    /** Fully defined AST values only. Explicit source carriers above retain partial trees. */
    fun <A> fromAst(name:String,value:()->A):DeepSeqValue<Any?> {
        val source=GenericAstData.named<A>(name)
        val input=GenericValue(source,value)
        return convert(GenericTypeExpression.N(name),input)
    }
    @Suppress("UNCHECKED_CAST")
    private fun convert(type:GenericTypeExpression,input:GenericValue<*>):DeepSeqValue<Any?> = DeepSeqValue(resolve(type)) {
        val source=GenericDefinitionData.resolve(type)
        val value=input.cast(source)
        when(type) {
            is GenericTypeExpression.N->{
                val alias=GenericDefinitionData.aliases[type.name]
                if(alias!=null)convert(alias,GenericValue(source){value}).value()
                else if(type.name !in declarations)value
                else {
                    val node=value as GenericNode
                    val spec=declarations.getValue(type.name).constructors.single{it.name==node.constructor}
                    DeepSeqNode(type.name,node.constructor,node.fields.zip(spec.fields).map{(field,fieldType)->convert(fieldType,field)})
                }
            }
            is GenericTypeExpression.L->{
                val item=GenericDefinitionData.resolve(type.element)
                fun elements(list:()->List<Any?>):GenericList<Any?> = GenericList.defer {
                    when(val step=GenericList.view(list()).step()) {
                        GenericList.Step.Nil->GenericList.empty()
                        is GenericList.Step.Cons->GenericList.cons({convert(type.element,GenericValue(item){step.head()}).value()},{elements{step.tail()}})
                    }
                }
                elements{value as List<Any?>}
            }
            is GenericTypeExpression.T->{
                val getters:List<()->Any?> = when(value) {
                    is GenericTuple2<*,*>->listOf({value.first},{value.second})
                    is GenericTuple3<*,*,*>->listOf({value.first},{value.second},{value.third})
                    else->error("Source tuple")
                }
                DeepSeqTuple(type.fields.zip(getters).map{(field,get)->convert(field,GenericValue(GenericDefinitionData.resolve(field),get))})
            }
            is GenericTypeExpression.A->when(type.name) {
                "Maybe"->when(value) {
                    GenericMaybe.None->GenericMaybe.None
                    is GenericMaybe.Some<*>->GenericMaybe.Some{convert(type.arguments.single(),GenericValue(GenericDefinitionData.resolve(type.arguments.single())){value.value}).value()}
                    else->error("Source Maybe")
                }
                "M.Map"->{
                    val entries=(value as Map<Any?,Any?>).entries.toList()
                    fun tree(from:Int,to:Int):DeepSeqMap {
                        if(from==to)return DeepSeqMap.tip()
                        val mid=(from+to) ushr 1;val entry=entries[mid]
                        return DeepSeqMap.bin({(to-from).toLong()},convert(type.arguments[0],GenericValue(GenericDefinitionData.resolve(type.arguments[0])){entry.key}),convert(type.arguments[1],GenericValue(GenericDefinitionData.resolve(type.arguments[1])){entry.value}),{tree(from,mid)},{tree(mid+1,to)})
                    }
                    tree(0,entries.size)
                }
                else->error("Source application")
            }
        }
    }
    fun map(key:NFData<*>,item:NFData<*>):NFData<DeepSeqMap> = object:SourceNFData<DeepSeqMap>() {
        override fun whnf(value:()->DeepSeqMap):DeepSeqMap {
            val root=value()
            root.forceSpine()
            return root
        }
        override fun children(value:()->DeepSeqMap):Iterator<DeepSeqValue<*>> {
            val input=whnf(value)
            return when(val step=input.step()) {
                    DeepSeqMap.Step.Tip->emptyList<DeepSeqValue<*>>().iterator()
                    is DeepSeqMap.Step.Bin->listOf(DeepSeqValue(key.erased()){step.key.value()},DeepSeqValue(item.erased()){step.item.value()},DeepSeqValue(this){step.left()},DeepSeqValue(this){step.right()}).iterator()
            }
        }
    }
}

/** Concrete finite AST dictionaries. For partial source constructors use
 * DeepSeqData.named and its explicit carriers. Count dictionaries keep the
 * distinct source wrappers; existing Long fields are bridged separately. */
object PandocNFData {
    private fun <A> ast(name:String):NFData<A> = NFData { value->val input=DeepSeqData.fromAst(name,value);input.dictionary.rnf{input.value()} }
    val Pandoc:NFData<io.heapy.kotdoc.ast.Pandoc> = ast("Pandoc")
    val Meta:NFData<Map<String,io.heapy.kotdoc.ast.MetaValue>> = ast("Meta")
    val MetaValue:NFData<io.heapy.kotdoc.ast.MetaValue> = ast("MetaValue")
    val ListNumberStyle:NFData<io.heapy.kotdoc.ast.ListNumberStyle> = ast("ListNumberStyle")
    val ListNumberDelim:NFData<io.heapy.kotdoc.ast.ListNumberDelim> = ast("ListNumberDelim")
    val Format:NFData<io.heapy.kotdoc.ast.Format> = ast("Format")
    val RowHeadColumns:NFData<io.heapy.kotdoc.ast.RowHeadColumns> = NFData {value->value()}
    val Alignment:NFData<io.heapy.kotdoc.ast.Alignment> = ast("Alignment")
    val ColWidth:NFData<io.heapy.kotdoc.ast.ColWidth> = ast("ColWidth")
    val Row:NFData<io.heapy.kotdoc.ast.Row> = ast("Row")
    val TableHead:NFData<io.heapy.kotdoc.ast.TableHead> = ast("TableHead")
    val TableBody:NFData<io.heapy.kotdoc.ast.TableBody> = ast("TableBody")
    val TableFoot:NFData<io.heapy.kotdoc.ast.TableFoot> = ast("TableFoot")
    val Caption:NFData<io.heapy.kotdoc.ast.Caption> = ast("Caption")
    val Cell:NFData<io.heapy.kotdoc.ast.Cell> = ast("Cell")
    val RowSpan:NFData<io.heapy.kotdoc.ast.RowSpan> = NFData {value->value()}
    val ColSpan:NFData<io.heapy.kotdoc.ast.ColSpan> = NFData {value->value()}
    val Block:NFData<io.heapy.kotdoc.ast.Block> = ast("Block")
    val QuoteType:NFData<io.heapy.kotdoc.ast.QuoteType> = ast("QuoteType")
    val MathType:NFData<io.heapy.kotdoc.ast.MathType> = ast("MathType")
    val Inline:NFData<io.heapy.kotdoc.ast.Inline> = ast("Inline")
    val Citation:NFData<io.heapy.kotdoc.ast.Citation> = ast("Citation")
    val CitationMode:NFData<io.heapy.kotdoc.ast.CitationMode> = ast("CitationMode")
}
