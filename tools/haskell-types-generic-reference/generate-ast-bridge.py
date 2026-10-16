import re
show=open('core/src/main/kotlin/io/heapy/kotdoc/ast/AstShow.kt').read()
read=open('core/src/main/kotlin/io/heapy/kotdoc/ast/PandocRead.kt').read()
rows=[]
for m in re.finditer(r'(is ([\w.]+)|([\w.]+)) -> application\("(\w+)", p(?:, (.*))?\)$',show,re.M):
 typ=m[2] or m[3];ctor=m[4];args=m[5] or ''
 if typ.startswith(('Inline.','Block.')) or typ in ['Pandoc','MetaValue.MetaMap','MetaValue.MetaList','MetaValue.MetaBool','MetaValue.MetaString','MetaValue.MetaInlines','MetaValue.MetaBlocks','Format','ColWidth.Width','Caption','Row','Cell','TableHead','TableBody','TableFoot']:
  args=args.replace('Meta(v.meta)','v.meta').replace('Optional(v.short)','v.short')
  args=re.sub(r'Count\("\w+", (v.\w+)\)',r'\1',args)
  rows.append(f'        is {typ} -> "{ctor}" to listOf({args})')
for typ,ctor in [('Inline.Space','Space'),('Inline.SoftBreak','SoftBreak'),('Inline.LineBreak','LineBreak'),('Block.HorizontalRule','HorizontalRule'),('ColWidth.Default','ColWidthDefault')]: rows.append(f'        {typ} -> "{ctor}" to emptyList()')
rows += ['        is Citation -> "Citation" to listOf(v.citationId,v.citationPrefix,v.citationSuffix,v.citationMode,v.citationNoteNum,v.citationHash)', '        is Enum<*> -> v.name to emptyList()']
builds=[]
for m in re.finditer(r'spec\(R\.(\w+),"(\w+)"[^\n{]*\)\{([^{}]*)\}',read):
 root,ctor,expr=m.groups()
 if ctor=='Many':continue
 expr=expr.replace('it.v','fields.v')
 if ctor=='Meta':expr='fields.v<Map<String,MetaValue>>(0)'
 builds.append(f'            "{ctor}" -> {expr}')
# source enum names are unique among these classes
for typ in ['ListNumberStyle','ListNumberDelim','Alignment','QuoteType','MathType','CitationMode']:
 # AST enum definitions
 pass
header='''// Finite existing-AST adapters for the source Data representation.
// pandoc-types © John MacFarlane, BSD-3-Clause; source licenses retained.
package io.heapy.kotdoc.ast

/** Existing Kotlin records have strict scalar fields. Use GenericDefinitionData
 * and GenericNode for source partial-field demand; these adapters preserve full
 * finite values and lazily expose list/map fields where the Kotlin type allows. */
object GenericAstData {
    val inlines:GenericData<Inlines> = many("Inline",{it.values},::Inlines)
    val blocks:GenericData<Blocks> = many("Block",{it.values},::Blocks)
    private fun <A,B> many(name:String,unwrap:(B)->List<A>,wrap:(List<A>)->B):GenericData<B> = object:GenericData<B> {
        private val element=named<A>(name)
        private val source=GenericDatas.many(element)
        override val type=source.type
        override fun toCanonical(value:B):Any?=source.toCanonical(GenericSequence.fromList(unwrap(value)))
        override fun fromCanonical(value:Any?):B=wrap(source.fromCanonical(value).toList())
        override fun layer(value:()->B):GenericLayer<B> {
            val layer=source.layer{GenericSequence.fromList(unwrap(value()))}
            return GenericLayer(layer.fields){fields->wrap(layer.rebuild(fields).toList())}
        }
    }
    @Suppress("UNCHECKED_CAST")
    fun <A> named(name:String):GenericData<A> = object:GenericData<A> {
        private val source=GenericDefinitionData.named(name)
        override val type=source.type
        override fun toCanonical(value:A):Any?=encode(GenericTypeExpression.N(name),value).canonical()
        override fun fromCanonical(value:Any?):A=decode(GenericTypeExpression.N(name),GenericValue(source){source.fromCanonical(value)}) as A
        override fun layer(value:()->A):GenericLayer<A> {
            val converted=GenericValue(source){source.fromCanonical(toCanonical(value()))}
            val layer=source.layer(converted::value)
            return GenericLayer(layer.fields){fields->fromCanonical(source.toCanonical(layer.rebuild(fields)))}
        }
    }
    private fun split(v:Any?):Pair<String,List<Any?>> = when(v) {
'''
footer='''
        else -> error("Unsupported existing AST value: ${v?.javaClass}")
    }
    @Suppress("UNCHECKED_CAST")
    private fun <A> List<Any?>.v(index:Int):A=this[index] as A
    private fun build(name:String,constructor:String,fields:List<Any?>):Any? = when(constructor) {
BUILDS
        else -> when(name) {
            "ListNumberStyle"->ListNumberStyle.valueOf(constructor)
            "ListNumberDelim"->ListNumberDelim.valueOf(constructor)
            "Alignment"->Alignment.valueOf(constructor)
            "QuoteType"->QuoteType.valueOf(constructor)
            "MathType"->MathType.valueOf(constructor)
            "CitationMode"->CitationMode.valueOf(constructor)
            else->error("Unknown source constructor $name.$constructor")
        }
    }
    private fun mapValuesLazy(input:Map<Any?,Any?>,transform:(Any?)->Any?):Map<Any?,Any?> = object:AbstractMap<Any?,Any?>(){
        override val entries:Set<Map.Entry<Any?,Any?>> = object:AbstractSet<Map.Entry<Any?,Any?>>(){
            override val size get()=input.size
            override fun iterator():Iterator<Map.Entry<Any?,Any?>> = input.entries.asSequence().map{entry->object:Map.Entry<Any?,Any?> {
                override val key get()=entry.key
                override val value get()=transform(entry.value)
                override fun equals(other:Any?):Boolean=other is Map.Entry<*,*> && this.key==other.key && this.value==other.value
                override fun hashCode():Int=(this.key?.hashCode()?:0) xor (this.value?.hashCode()?:0)
            }}.iterator()
        }
    }
    private fun deferredList(value:()->List<Any?>):List<Any?> = object:AbstractList<Any?>(){
        private val source by lazy(value)
        override val size get()=source.size
        override fun get(index:Int)=source[index]
    }
    @Suppress("UNCHECKED_CAST")
    private fun encode(expression:GenericTypeExpression,value:Any?):GenericValue<Any?> {
        val data=GenericDefinitionData.resolve(expression)
        return GenericValue(data){when(expression) {
            is GenericTypeExpression.N -> {
                val alias=GenericDefinitionData.aliases[expression.name]
                if(alias!=null) {
                    val tuple=when(value){is Attr->GenericTuple3({value.identifier},{value.classes},{value.keyValues});is Target->GenericTuple2({value.url},{value.title});is ListAttributes->GenericTuple3({value.start},{value.style},{value.delimiter});is ColSpec->GenericTuple2({value.alignment},{value.width});else->value}
                    encode(alias,tuple).value()
                } else if(expression.name in setOf("Int","Double","Bool","Char","Text"))value
                else {
                    val (constructor,fields)=when(expression.name){"Meta"->"Meta" to listOf(value);"RowHeadColumns","RowSpan","ColSpan"->expression.name to listOf(value);else->split(value)}
                    val spec=GenericDefinitionData.declarations.single{it.name==expression.name}.constructors.single{it.name==constructor}
                    GenericDefinitionData.node(expression.name,constructor,fields.zip(spec.fields).map{(field,type)->encode(type,field)})
                }
            }
            is GenericTypeExpression.L -> {
                val input=value as List<Any?>
                fun at(index:Int):GenericList<Any?> = if(index==input.size)GenericList.empty() else GenericList.cons({encode(expression.element,input[index]).value()},{at(index+1)})
                at(0)
            }
            is GenericTypeExpression.T -> {
                val fields=when(value){is Pair<*,*>->listOf({value.first},{value.second});is Triple<*,*,*>->listOf({value.first},{value.second},{value.third});is GenericTuple2<*,*>->listOf({value.first},{value.second});is GenericTuple3<*,*,*>->listOf({value.first},{value.second},{value.third});else->error("tuple")}
                fun field(i:Int)=encode(expression.fields[i],fields[i]()).value()
                if(fields.size==2)GenericTuple2({field(0)},{field(1)}) else GenericTuple3({field(0)},{field(1)},{field(2)})
            }
            is GenericTypeExpression.A -> when(expression.name){
                "Maybe"->if(value==null)GenericMaybe.None else GenericMaybe.Some{encode(expression.arguments.single(),value).value()}
                "M.Map"->mapValuesLazy(value as Map<Any?,Any?>){v->encode(expression.arguments[1],v).value()}
                else->error("application")
            }
        }}
    }
    @Suppress("UNCHECKED_CAST")
    private fun decode(expression:GenericTypeExpression,value:GenericValue<Any?>):Any? = when(expression) {
        is GenericTypeExpression.N->{
            val alias=GenericDefinitionData.aliases[expression.name]
            if(alias!=null){
                val tuple=decode(alias,value)
                when(expression.name){"Attr"->{val t=tuple as Triple<String,List<String>,List<Pair<String,String>>>;Attr(t.first,t.second,t.third)};"Target"->{val t=tuple as Pair<String,String>;Target(t.first,t.second)};"ListAttributes"->{val t=tuple as Triple<Long,ListNumberStyle,ListNumberDelim>;ListAttributes(t.first,t.second,t.third)};"ColSpec"->{val t=tuple as Pair<Alignment,ColWidth>;ColSpec(t.first,t.second)};else->tuple}
            } else if(expression.name in setOf("Int","Double","Bool","Char","Text"))value.value()
            else {val node=value.value() as GenericNode;val spec=GenericDefinitionData.declarations.single{it.name==expression.name}.constructors.single{it.name==node.constructor};build(expression.name,node.constructor,node.fields.zip(spec.fields).map{(field,type)->decode(type,field as GenericValue<Any?>)})}
        }
        is GenericTypeExpression.L->{val input=value.value() as List<Any?>;val element=GenericDefinitionData.resolve(expression.element);object:AbstractList<Any?>(){override val size get()=input.size;override fun get(index:Int)=decode(expression.element,GenericValue(element){input[index]})}}
        is GenericTypeExpression.T->{val input=value.value();fun field(i:Int,v:()->Any?):Any? { val type=expression.fields[i];val result={decode(type,GenericValue(GenericDefinitionData.resolve(type),v))};return if(type is GenericTypeExpression.L)deferredList{result() as List<Any?>}else result() }
            when(input){is GenericTuple2<*,*>->field(0){input.first} to field(1){input.second};is GenericTuple3<*,*,*>->Triple(field(0){input.first},field(1){input.second},field(2){input.third});else->error("tuple")}}
        is GenericTypeExpression.A->when(expression.name){
            "Maybe"->when(val input=value.value() as GenericMaybe<Any?>){GenericMaybe.None->null;is GenericMaybe.Some->decode(expression.arguments.single(),GenericValue(GenericDefinitionData.resolve(expression.arguments.single())){input.value})}
            "M.Map"->mapValuesLazy(value.value() as Map<Any?,Any?>){v->decode(expression.arguments[1],GenericValue(GenericDefinitionData.resolve(expression.arguments[1])){v})}
            else->error("application")
        }
    }
}
'''
open('tools/haskell-types-generic-reference/work-in-progress/GenericAstData.kt','w').write(header+'\n'.join(rows)+footer.replace('BUILDS','\n'.join(builds)))
