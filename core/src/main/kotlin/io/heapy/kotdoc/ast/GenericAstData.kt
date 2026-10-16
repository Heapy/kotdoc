// Finite existing-AST adapters for the source Data representation.
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
        is Pandoc -> "Pandoc" to listOf(v.meta, v.blocks)
        is MetaValue.MetaMap -> "MetaMap" to listOf(v.values)
        is MetaValue.MetaList -> "MetaList" to listOf(v.values)
        is MetaValue.MetaBool -> "MetaBool" to listOf(v.value)
        is MetaValue.MetaString -> "MetaString" to listOf(v.value)
        is MetaValue.MetaInlines -> "MetaInlines" to listOf(v.content)
        is MetaValue.MetaBlocks -> "MetaBlocks" to listOf(v.content)
        is Format -> "Format" to listOf(v.value)
        is ColWidth.Width -> "ColWidth" to listOf(v.value)
        is Caption -> "Caption" to listOf(v.short, v.long)
        is Row -> "Row" to listOf(v.attr, v.cells)
        is Cell -> "Cell" to listOf(v.attr, v.alignment, v.rowSpan, v.colSpan, v.blocks)
        is TableHead -> "TableHead" to listOf(v.attr, v.rows)
        is TableBody -> "TableBody" to listOf(v.attr, v.rowHeadColumns, v.head, v.body)
        is TableFoot -> "TableFoot" to listOf(v.attr, v.rows)
        is Inline.Str -> "Str" to listOf(v.text)
        is Inline.Emph -> "Emph" to listOf(v.content)
        is Inline.Underline -> "Underline" to listOf(v.content)
        is Inline.Strong -> "Strong" to listOf(v.content)
        is Inline.Strikeout -> "Strikeout" to listOf(v.content)
        is Inline.Superscript -> "Superscript" to listOf(v.content)
        is Inline.Subscript -> "Subscript" to listOf(v.content)
        is Inline.SmallCaps -> "SmallCaps" to listOf(v.content)
        is Inline.Quoted -> "Quoted" to listOf(v.type, v.content)
        is Inline.Cite -> "Cite" to listOf(v.citations, v.content)
        is Inline.Code -> "Code" to listOf(v.attr, v.text)
        is Inline.Math -> "Math" to listOf(v.type, v.text)
        is Inline.RawInline -> "RawInline" to listOf(v.format, v.text)
        is Inline.Link -> "Link" to listOf(v.attr, v.content, v.target)
        is Inline.Image -> "Image" to listOf(v.attr, v.content, v.target)
        is Inline.Note -> "Note" to listOf(v.blocks)
        is Inline.Span -> "Span" to listOf(v.attr, v.content)
        is Block.Plain -> "Plain" to listOf(v.content)
        is Block.Para -> "Para" to listOf(v.content)
        is Block.LineBlock -> "LineBlock" to listOf(v.lines)
        is Block.CodeBlock -> "CodeBlock" to listOf(v.attr, v.text)
        is Block.RawBlock -> "RawBlock" to listOf(v.format, v.text)
        is Block.BlockQuote -> "BlockQuote" to listOf(v.blocks)
        is Block.OrderedList -> "OrderedList" to listOf(v.attributes, v.items)
        is Block.BulletList -> "BulletList" to listOf(v.items)
        is Block.DefinitionList -> "DefinitionList" to listOf(v.items)
        is Block.Header -> "Header" to listOf(v.level, v.attr, v.content)
        is Block.Table -> "Table" to listOf(v.attr, v.caption, v.columns, v.head, v.bodies, v.foot)
        is Block.Figure -> "Figure" to listOf(v.attr, v.caption, v.blocks)
        is Block.Div -> "Div" to listOf(v.attr, v.blocks)
        Inline.Space -> "Space" to emptyList()
        Inline.SoftBreak -> "SoftBreak" to emptyList()
        Inline.LineBreak -> "LineBreak" to emptyList()
        Block.HorizontalRule -> "HorizontalRule" to emptyList()
        ColWidth.Default -> "ColWidthDefault" to emptyList()
        is Citation -> "Citation" to listOf(v.citationId,v.citationPrefix,v.citationSuffix,v.citationMode,v.citationNoteNum,v.citationHash)
        is Enum<*> -> v.name to emptyList()
        else -> error("Unsupported existing AST value: ${v?.javaClass}")
    }
    @Suppress("UNCHECKED_CAST")
    private fun <A> List<Any?>.v(index:Int):A=this[index] as A
    private fun build(name:String,constructor:String,fields:List<Any?>):Any? = when(constructor) {
            "Pandoc" -> Pandoc(fields.v(0),fields.v(1))
            "Meta" -> fields.v<Map<String,MetaValue>>(0)
            "MetaMap" -> MetaValue.MetaMap(fields.v(0))
            "MetaList" -> MetaValue.MetaList(fields.v(0))
            "MetaBool" -> MetaValue.MetaBool(fields.v(0))
            "MetaString" -> MetaValue.MetaString(fields.v(0))
            "MetaInlines" -> MetaValue.MetaInlines(fields.v(0))
            "MetaBlocks" -> MetaValue.MetaBlocks(fields.v(0))
            "Format" -> Format(fields.v(0))
            "RowHeadColumns" -> fields.v<Long>(0)
            "RowSpan" -> fields.v<Long>(0)
            "ColSpan" -> fields.v<Long>(0)
            "ColWidth" -> ColWidth.Width(fields.v(0))
            "ColWidthDefault" -> ColWidth.Default
            "Row" -> Row(fields.v(0),fields.v(1))
            "TableHead" -> TableHead(fields.v(0),fields.v(1))
            "TableFoot" -> TableFoot(fields.v(0),fields.v(1))
            "TableBody" -> TableBody(fields.v(0),fields.v(1),fields.v(2),fields.v(3))
            "Cell" -> Cell(fields.v(0),fields.v(1),fields.v(2),fields.v(3),fields.v(4))
            "Caption" -> Caption(fields.v(0),fields.v(1))
            "Citation" -> Citation(fields.v(0),fields.v(1),fields.v(2),fields.v(3),fields.v(4),fields.v(5))
            "Str" -> Inline.Str(fields.v(0))
            "Emph" -> Inline.Emph(fields.v(0))
            "Underline" -> Inline.Underline(fields.v(0))
            "Strong" -> Inline.Strong(fields.v(0))
            "Strikeout" -> Inline.Strikeout(fields.v(0))
            "Superscript" -> Inline.Superscript(fields.v(0))
            "Subscript" -> Inline.Subscript(fields.v(0))
            "SmallCaps" -> Inline.SmallCaps(fields.v(0))
            "Quoted" -> Inline.Quoted(fields.v(0),fields.v(1))
            "Cite" -> Inline.Cite(fields.v(0),fields.v(1))
            "Code" -> Inline.Code(fields.v(0),fields.v(1))
            "Space" -> Inline.Space
            "SoftBreak" -> Inline.SoftBreak
            "LineBreak" -> Inline.LineBreak
            "Math" -> Inline.Math(fields.v(0),fields.v(1))
            "RawInline" -> Inline.RawInline(fields.v<Format>(0),fields.v<String>(1))
            "Link" -> Inline.Link(fields.v(0),fields.v(1),fields.v(2))
            "Image" -> Inline.Image(fields.v(0),fields.v(1),fields.v(2))
            "Note" -> Inline.Note(fields.v(0))
            "Span" -> Inline.Span(fields.v(0),fields.v(1))
            "Plain" -> Block.Plain(fields.v(0))
            "Para" -> Block.Para(fields.v(0))
            "LineBlock" -> Block.LineBlock(fields.v(0))
            "CodeBlock" -> Block.CodeBlock(fields.v(0),fields.v(1))
            "RawBlock" -> Block.RawBlock(fields.v<Format>(0),fields.v<String>(1))
            "BlockQuote" -> Block.BlockQuote(fields.v(0))
            "OrderedList" -> Block.OrderedList(fields.v(0),fields.v(1))
            "BulletList" -> Block.BulletList(fields.v(0))
            "DefinitionList" -> Block.DefinitionList(fields.v(0))
            "Header" -> Block.Header(fields.v(0),fields.v(1),fields.v(2))
            "HorizontalRule" -> Block.HorizontalRule
            "Table" -> Block.Table(fields.v(0),fields.v(1),fields.v(2),fields.v(3),fields.v(4),fields.v(5))
            "Figure" -> Block.Figure(fields.v(0),fields.v(1),fields.v(2))
            "Div" -> Block.Div(fields.v(0),fields.v(1))
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
