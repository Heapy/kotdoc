// Source Data schema of pandoc-types Definition; Copyright (c) 2006–2023 John MacFarlane.
// BSD-3-Clause. Generated from the pinned declarations; never used to make expected output.
package io.heapy.kotdoc.ast

sealed interface GenericTypeExpression {
    data class N(val name:String):GenericTypeExpression
    data class L(val element:GenericTypeExpression):GenericTypeExpression
    data class T(val fields:List<GenericTypeExpression>):GenericTypeExpression
    data class A(val name:String,val arguments:List<GenericTypeExpression>):GenericTypeExpression
}
class GenericConstructor(val name:String,val fields:List<GenericTypeExpression>,val labels:List<String>)
class GenericDeclaration(val name:String,val newtype:Boolean,val constructors:List<GenericConstructor>)
class GenericNode internal constructor(val declaration:String,val constructor:String,val fields:List<GenericValue<*>>)

object GenericDefinitionData {
    private fun N(name:String)=GenericTypeExpression.N(name)
    private fun L(element:GenericTypeExpression)=GenericTypeExpression.L(element)
    private fun T(fields:List<GenericTypeExpression>)=GenericTypeExpression.T(fields)
    private fun A(name:String,args:List<GenericTypeExpression>)=GenericTypeExpression.A(name,args)
    val declarations:List<GenericDeclaration> = listOf(
        GenericDeclaration("Pandoc",false,listOf(GenericConstructor("Pandoc",listOf(N("Meta"),L(N("Block"))),listOf()))),
        GenericDeclaration("Meta",true,listOf(GenericConstructor("Meta",listOf(A("M.Map",listOf(N("Text"),N("MetaValue")))),listOf("unMeta")))),
        GenericDeclaration("MetaValue",false,listOf(GenericConstructor("MetaMap",listOf(A("M.Map",listOf(N("Text"),N("MetaValue")))),listOf()),GenericConstructor("MetaList",listOf(L(N("MetaValue"))),listOf()),GenericConstructor("MetaBool",listOf(N("Bool")),listOf()),GenericConstructor("MetaString",listOf(N("Text")),listOf()),GenericConstructor("MetaInlines",listOf(L(N("Inline"))),listOf()),GenericConstructor("MetaBlocks",listOf(L(N("Block"))),listOf()))),
        GenericDeclaration("ListNumberStyle",false,listOf(GenericConstructor("DefaultStyle",listOf(),listOf()),GenericConstructor("Example",listOf(),listOf()),GenericConstructor("Decimal",listOf(),listOf()),GenericConstructor("LowerRoman",listOf(),listOf()),GenericConstructor("UpperRoman",listOf(),listOf()),GenericConstructor("LowerAlpha",listOf(),listOf()),GenericConstructor("UpperAlpha",listOf(),listOf()))),
        GenericDeclaration("ListNumberDelim",false,listOf(GenericConstructor("DefaultDelim",listOf(),listOf()),GenericConstructor("Period",listOf(),listOf()),GenericConstructor("OneParen",listOf(),listOf()),GenericConstructor("TwoParens",listOf(),listOf()))),
        GenericDeclaration("Format",true,listOf(GenericConstructor("Format",listOf(N("Text")),listOf()))),
        GenericDeclaration("RowHeadColumns",true,listOf(GenericConstructor("RowHeadColumns",listOf(N("Int")),listOf()))),
        GenericDeclaration("Alignment",false,listOf(GenericConstructor("AlignLeft",listOf(),listOf()),GenericConstructor("AlignRight",listOf(),listOf()),GenericConstructor("AlignCenter",listOf(),listOf()),GenericConstructor("AlignDefault",listOf(),listOf()))),
        GenericDeclaration("ColWidth",false,listOf(GenericConstructor("ColWidth",listOf(N("Double")),listOf()),GenericConstructor("ColWidthDefault",listOf(),listOf()))),
        GenericDeclaration("Row",false,listOf(GenericConstructor("Row",listOf(N("Attr"),L(N("Cell"))),listOf()))),
        GenericDeclaration("TableHead",false,listOf(GenericConstructor("TableHead",listOf(N("Attr"),L(N("Row"))),listOf()))),
        GenericDeclaration("TableBody",false,listOf(GenericConstructor("TableBody",listOf(N("Attr"),N("RowHeadColumns"),L(N("Row")),L(N("Row"))),listOf()))),
        GenericDeclaration("TableFoot",false,listOf(GenericConstructor("TableFoot",listOf(N("Attr"),L(N("Row"))),listOf()))),
        GenericDeclaration("Caption",false,listOf(GenericConstructor("Caption",listOf(A("Maybe",listOf(N("ShortCaption"))),L(N("Block"))),listOf()))),
        GenericDeclaration("Cell",false,listOf(GenericConstructor("Cell",listOf(N("Attr"),N("Alignment"),N("RowSpan"),N("ColSpan"),L(N("Block"))),listOf()))),
        GenericDeclaration("RowSpan",true,listOf(GenericConstructor("RowSpan",listOf(N("Int")),listOf()))),
        GenericDeclaration("ColSpan",true,listOf(GenericConstructor("ColSpan",listOf(N("Int")),listOf()))),
        GenericDeclaration("Block",false,listOf(GenericConstructor("Plain",listOf(L(N("Inline"))),listOf()),GenericConstructor("Para",listOf(L(N("Inline"))),listOf()),GenericConstructor("LineBlock",listOf(L(L(N("Inline")))),listOf()),GenericConstructor("CodeBlock",listOf(N("Attr"),N("Text")),listOf()),GenericConstructor("RawBlock",listOf(N("Format"),N("Text")),listOf()),GenericConstructor("BlockQuote",listOf(L(N("Block"))),listOf()),GenericConstructor("OrderedList",listOf(N("ListAttributes"),L(L(N("Block")))),listOf()),GenericConstructor("BulletList",listOf(L(L(N("Block")))),listOf()),GenericConstructor("DefinitionList",listOf(L(T(listOf(L(N("Inline")),L(L(N("Block"))))))),listOf()),GenericConstructor("Header",listOf(N("Int"),N("Attr"),L(N("Inline"))),listOf()),GenericConstructor("HorizontalRule",listOf(),listOf()),GenericConstructor("Table",listOf(N("Attr"),N("Caption"),L(N("ColSpec")),N("TableHead"),L(N("TableBody")),N("TableFoot")),listOf()),GenericConstructor("Figure",listOf(N("Attr"),N("Caption"),L(N("Block"))),listOf()),GenericConstructor("Div",listOf(N("Attr"),L(N("Block"))),listOf()))),
        GenericDeclaration("QuoteType",false,listOf(GenericConstructor("SingleQuote",listOf(),listOf()),GenericConstructor("DoubleQuote",listOf(),listOf()))),
        GenericDeclaration("MathType",false,listOf(GenericConstructor("DisplayMath",listOf(),listOf()),GenericConstructor("InlineMath",listOf(),listOf()))),
        GenericDeclaration("Inline",false,listOf(GenericConstructor("Str",listOf(N("Text")),listOf()),GenericConstructor("Emph",listOf(L(N("Inline"))),listOf()),GenericConstructor("Underline",listOf(L(N("Inline"))),listOf()),GenericConstructor("Strong",listOf(L(N("Inline"))),listOf()),GenericConstructor("Strikeout",listOf(L(N("Inline"))),listOf()),GenericConstructor("Superscript",listOf(L(N("Inline"))),listOf()),GenericConstructor("Subscript",listOf(L(N("Inline"))),listOf()),GenericConstructor("SmallCaps",listOf(L(N("Inline"))),listOf()),GenericConstructor("Quoted",listOf(N("QuoteType"),L(N("Inline"))),listOf()),GenericConstructor("Cite",listOf(L(N("Citation")),L(N("Inline"))),listOf()),GenericConstructor("Code",listOf(N("Attr"),N("Text")),listOf()),GenericConstructor("Space",listOf(),listOf()),GenericConstructor("SoftBreak",listOf(),listOf()),GenericConstructor("LineBreak",listOf(),listOf()),GenericConstructor("Math",listOf(N("MathType"),N("Text")),listOf()),GenericConstructor("RawInline",listOf(N("Format"),N("Text")),listOf()),GenericConstructor("Link",listOf(N("Attr"),L(N("Inline")),N("Target")),listOf()),GenericConstructor("Image",listOf(N("Attr"),L(N("Inline")),N("Target")),listOf()),GenericConstructor("Note",listOf(L(N("Block"))),listOf()),GenericConstructor("Span",listOf(N("Attr"),L(N("Inline"))),listOf()))),
        GenericDeclaration("Citation",false,listOf(GenericConstructor("Citation",listOf(N("Text"),L(N("Inline")),L(N("Inline")),N("CitationMode"),N("Int"),N("Int")),listOf("citationId","citationPrefix","citationSuffix","citationMode","citationNoteNum","citationHash")))),
        GenericDeclaration("CitationMode",false,listOf(GenericConstructor("AuthorInText",listOf(),listOf()),GenericConstructor("SuppressAuthor",listOf(),listOf()),GenericConstructor("NormalCitation",listOf(),listOf())))
    )
    val aliases:Map<String,GenericTypeExpression> = mapOf(
        "ListAttributes" to T(listOf(N("Int"),N("ListNumberStyle"),N("ListNumberDelim"))),
        "Attr" to T(listOf(N("Text"),L(N("Text")),L(T(listOf(N("Text"),N("Text")))))),
        "ColSpec" to T(listOf(N("Alignment"),N("ColWidth"))),
        "ShortCaption" to L(N("Inline")),
        "Target" to T(listOf(N("Text"),N("Text")))
    )
    private val dictionaries=HashMap<String,GenericData<Any?>>()
    @Suppress("UNCHECKED_CAST")
    fun named(name:String):GenericData<Any?> {
        val primitive:GenericData<*>?=when(name){"Int"->GenericDatas.int;"Double"->GenericDatas.double;"Bool"->GenericDatas.boolean;"Char"->GenericDatas.char;"Text"->GenericDatas.text;else->null}
        if(primitive!=null)return primitive as GenericData<Any?>
        aliases[name]?.let{return resolve(it)}
        return synchronized(dictionaries){dictionaries.getOrPut(name){DeclarationData(declarations.single{it.name==name}) as GenericData<Any?>}}
    }
    @Suppress("UNCHECKED_CAST")
    fun resolve(expression:GenericTypeExpression):GenericData<Any?> = when(expression) {
        is GenericTypeExpression.N->named(expression.name)
        is GenericTypeExpression.L->GenericDatas.list(resolve(expression.element)) as GenericData<Any?>
        is GenericTypeExpression.T->{val fields=expression.fields.map(::resolve);val result=when(fields.size){2->GenericDatas.tuple2(fields[0],fields[1]);3->GenericDatas.tuple3(fields[0],fields[1],fields[2]);else->error("Unsupported source tuple arity")};result as GenericData<Any?>}
        is GenericTypeExpression.A->when(expression.name){
            "Maybe"->GenericDatas.maybe(resolve(expression.arguments.single())) as GenericData<Any?>
            "M.Map"->GenericDatas.map(resolve(expression.arguments[0]),resolve(expression.arguments[1]),Comparator<Any?>{a,b->UnicodeTextOrder.compare(a as String,b as String)}) as GenericData<Any?>
            else->error("Unknown source type application: $expression")
        }
    }
    fun node(name:String,constructor:String,fields:List<GenericValue<*>>):GenericNode {
        val declaration=declarations.single{it.name==name};val spec=declaration.constructors.single{it.name==constructor}
        require(fields.size==spec.fields.size)
        fields.zip(spec.fields).forEach{(field,type)->require(field.data.type==resolve(type).type)}
        return GenericNode(name,constructor,fields)
    }
    fun value(name:String,constructor:String,fields:List<GenericValue<*>>):GenericValue<Any?> = GenericValue(named(name)){node(name,constructor,fields)}
    private class DeclarationData(private val declaration:GenericDeclaration):GenericData<GenericNode> {
        override val type=GenericType("Text.Pandoc.Definition",declaration.name)
        override fun force(value:()->GenericNode):GenericNode {
            val input=value();require(input.declaration==declaration.name)
            val constructor=declaration.constructors.single{it.name==input.constructor}
            // Definition uses StrictData; newtype WHNF is the underlying field WHNF.
            for((field,expression)in input.fields.zip(constructor.fields))field.cast(resolve(expression))
            return input
        }
        override fun toCanonical(value:GenericNode):Any? = if(declaration.newtype)value.fields.single().canonical() else value
        override fun fromCanonical(value:Any?):GenericNode {
            if(!declaration.newtype)return value as GenericNode
            val constructor=declaration.constructors.single();val field=resolve(constructor.fields.single())
            return node(declaration.name,constructor.name,listOf(GenericValue(field){field.fromCanonical(value)}))
        }
        override fun layer(value:()->GenericNode):GenericLayer<GenericNode> {
            if(declaration.newtype) {
                val constructor=declaration.constructors.single();val field=resolve(constructor.fields.single())
                return GenericLayer(listOf(GenericValue(field){value().fields.single().cast(field)})){fields->node(declaration.name,constructor.name,fields)}
            }
            val input=value()
            return GenericLayer(input.fields){fields->node(declaration.name,input.constructor,fields)}
        }
    }
}
