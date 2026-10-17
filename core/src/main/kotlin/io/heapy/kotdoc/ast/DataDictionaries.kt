// GHC.Internal.Data.Data: (c) The University of Glasgow, CWI 2001--2004.
// GHC.Tc.Deriv.Generate: (c) The University of Glasgow 2006;
// (c) The GRASP/AQUA Project, Glasgow University, 1992-1998.
// GHC.Internal.Real: (c) The University of Glasgow, 1994-2002.
// GHC.Internal.Float: (c) The University of Glasgow 1994-2002;
// Portions obtained from hbc (c) Lennart Augusstson.
// GHC.Num.Integer: (c) Sylvain Henry 2019, (c) Herbert Valerio Riedel 2014.
// Full original notices and licenses are retained in GHC-Data-Attribution.txt
// and the referenced packaged license files.
package io.heapy.kotdoc.ast

import java.math.BigInteger

/** Lazy source String, with an explicit finite JVM String bridge. */
class DataName private constructor(private val characters:DataThunk<GenericList<Int>>) {
    val chars:GenericList<Int> get()=characters()
    private val finite=GenericMemo { buildString { for(cp in chars)appendCodePoint(cp) } }
    val string:String get()=finite.get()
    companion object {
        fun source(value:()->GenericList<Int>)=DataName(DataThunk(value))
        fun finite(value:()->String)=source { GenericList.view(value().codePoints().toArray().toList()) }
    }
}
private fun <A,B> dataMap(input:GenericList<A>,f:(A)->B):GenericList<B> = GenericList.defer {
    when(val step=input.step()) {
        GenericList.Step.Nil->GenericList.empty()
        is GenericList.Step.Cons->GenericList.cons({f(step.head())},{dataMap(step.tail(),f)})
    }
}
private fun dataAppend(left:GenericList<Int>,right:()->GenericList<Int>):GenericList<Int> = GenericList.defer {
    when(val step=left.step()) {
        GenericList.Step.Nil->right()
        is GenericList.Step.Cons->GenericList.cons({step.head()},{dataAppend(step.tail(),right)})
    }
}
/** Throwing ErrorCall does not render its lazy source message. */
class DataSourceError private constructor(private val text:DataName):RuntimeException(null,null,false,false) {
    constructor(message:()->String):this(DataName.finite(message))
    val sourceMessage:GenericList<Int> get()=text.chars
    override val message:String get()=text.string
    companion object { fun source(message:()->GenericList<Int>)=DataSourceError(DataName.source(message)) }
}
private fun dataSourceError(message:()->String):Nothing=throw DataSourceError(message)
private fun dataNamedError(prefix:String,name:()->GenericList<Int>,suffix:String):Nothing = throw DataSourceError.source {
    dataAppend(DataName.finite{prefix}.chars) { dataAppend(name()) { DataName.finite{suffix}.chars } }
}

enum class DataFixity { Prefix, Infix }
sealed interface DataConstrRep {
    class Algebraic(index:()->Long):DataConstrRep {
        private val cell=DataThunk(index);val index:Long get()=cell()
        constructor(index:Long):this({index})
        override fun equals(other:Any?)=other is Algebraic&&index==other.index
        override fun hashCode()=index.hashCode()
    }
    class Integral(value:()->BigInteger):DataConstrRep {
        private val cell=DataThunk(value);val value:BigInteger get()=cell()
        constructor(value:BigInteger):this({value})
        override fun equals(other:Any?)=other is Integral&&value==other.value
        override fun hashCode()=value.hashCode()
    }
    class Rational(value:()->Pair<BigInteger,BigInteger>):DataConstrRep {
        private val cell=DataThunk(value)
        val numerator:BigInteger get()=cell().first
        val denominator:BigInteger get()=cell().second
        constructor(numerator:BigInteger,denominator:BigInteger):this({numerator to denominator})
        override fun equals(other:Any?)=other is Rational&&numerator==other.numerator&&denominator==other.denominator
        override fun hashCode():Int = numerator.hashCode()*31+denominator.hashCode()
    }
    class Character(codePoint:()->Int):DataConstrRep {
        private val cell=DataThunk(codePoint);val codePoint:Int get()=cell()
        constructor(codePoint:Int):this({codePoint})
        override fun equals(other:Any?)=other is Character&&codePoint==other.codePoint
        override fun hashCode()=codePoint
    }
}
sealed interface DataTypeRep {
    class Algebraic(val constructors:DataThunk<GenericList<DataConstr>>):DataTypeRep
    data object Integral:DataTypeRep
    data object Floating:DataTypeRep
    data object Character:DataTypeRep
    data object None:DataTypeRep
}
/** Source records have independently lazy fields, including their recursive link. */
class DataTypeInfo private constructor(private val sourceName:DataName,representation:()->DataTypeRep) {
    constructor(name:()->String,representation:()->DataTypeRep):this(DataName.finite(name),representation)
    companion object { fun source(name:()->GenericList<Int>,representation:()->DataTypeRep)=DataTypeInfo(DataName.source(name),representation) }
    private val representationCell=DataThunk(representation)
    val name:String get()=sourceName.string
    val nameChars:GenericList<Int> get()=sourceName.chars
    val representation:DataTypeRep get()=representationCell()
    fun constructors():GenericList<DataConstr> = when(val rep=representation) {
        is DataTypeRep.Algebraic->rep.constructors()
        else->dataNamedError("GHC.Internal.Data.Data.dataTypeConstrs is not supported for ",{nameChars},", as it is not an algebraic data type.")
    }
}
class DataConstr private constructor(representation:()->DataConstrRep,private val sourceName:DataName,fields:()->GenericList<DataName>,fixity:()->DataFixity,type:()->DataTypeInfo) {
    constructor(representation:()->DataConstrRep,name:()->String,fields:()->GenericList<String>,fixity:()->DataFixity,type:()->DataTypeInfo):this(representation,DataName.finite(name),{dataMap(fields()){name->DataName.finite{name}}},fixity,type)
    companion object { fun source(representation:()->DataConstrRep,name:()->GenericList<Int>,fields:()->GenericList<GenericList<Int>>,fixity:()->DataFixity,type:()->DataTypeInfo)=DataConstr(representation,DataName.source(name),{dataMap(fields()){name->DataName.source{name}}},fixity,type) }
    private val representationCell=DataThunk(representation)
    private val fieldsCell=DataThunk(fields)
    private val fixityCell=DataThunk(fixity)
    private val typeCell=DataThunk(type)
    val representation:DataConstrRep get()=representationCell()
    val name:String get()=sourceName.string
    val nameChars:GenericList<Int> get()=sourceName.chars
    val fields:GenericList<String> get()=dataMap(fieldsCell()){it.string}
    val fieldNameChars:GenericList<GenericList<Int>> get()=dataMap(fieldsCell()){it.chars}
    val fixity:DataFixity get()=fixityCell()
    val type:DataTypeInfo get()=typeCell()
    val index:Long get()=when(val rep=representation) {
        is DataConstrRep.Algebraic->rep.index
        else->dataNamedError("GHC.Internal.Data.Data.constrIndex is not supported for ",{type.nameChars},", as it is not an algebraic data type.")
    }
    override fun toString():String=name
    // GHC compares representation alone, not the datatype identity.
    override fun equals(other:Any?):Boolean=other is DataConstr&&representation==other.representation
    override fun hashCode():Int=representation.hashCode()
}

/** A constructor program retains field evidence without demanding its dictionary. */
class DataConstructor<A>(
    val metadata:DataConstr,
    val fields:List<DataEvidence<*>>,
    val project:(DataThunk<A>,Int)->DataThunk<Any?>,
    val construct:(List<DataThunk<Any?>>)->A,
)
open class AlgebraicDataDictionary<A>(
    representation:DataRepresentation<A>,
    private val datatype:DataTypeInfo,
    private val constructors:List<DataConstructor<A>>,
    private val select:(DataThunk<A>)->Int,
    private val unfoldSelect:(DataThunk<DataConstr>)->Int,
    private val constrSelect:(DataThunk<A>)->Int=select,
):DataDictionary<A>(representation) {
    override fun dataTypeOf(value:DataThunk<A>):DataTypeInfo=datatype
    override fun toConstr(value:DataThunk<A>):DataConstr=constructors[constrSelect(value)].metadata
    @Suppress("UNCHECKED_CAST")
    override fun <F> gfoldl(fold:DataFold<F>,value:DataThunk<A>):DataKind<F,A> {
        val constructor=constructors[select(value)]
        var result:DataThunk<DataKind<F,Any?>> = DataThunk{fold.forceInject();fold.inject(curried(constructor,0,emptyList()))}
        for(index in constructor.fields.indices) {
            val previous=result
            val evidence=constructor.fields[index] as DataEvidence<Any?>
            result=DataThunk{fold.forceApply(evidence);fold.apply(evidence,DataThunk{previous() as DataKind<F,DataFunction<Any?,Any?>>},evidence.argument{constructor.project(value,index)()})}
        }
        return result() as DataKind<F,A>
    }
    @Suppress("UNCHECKED_CAST")
    override fun <F> gunfold(unfold:DataUnfold<F>,constructor:DataThunk<DataConstr>):DataKind<F,A> {
        val selected=constructors[unfoldSelect(constructor)]
        var result:DataThunk<DataKind<F,Any?>> = DataThunk{unfold.forceInject();unfold.inject(curried(selected,0,emptyList()))}
        for(field in selected.fields) {
            val previous=result
            result=DataThunk{
                val evidence=field as DataEvidence<Any?>
                unfold.forceApply(evidence)
                unfold.apply(evidence,DataThunk{previous() as DataKind<F,DataFunction<Any?,Any?>>})}
        }
        return result() as DataKind<F,A>
    }
    private fun curried(constructor:DataConstructor<A>,index:Int,arguments:List<DataThunk<Any?>>):DataThunk<Any?> = DataThunk {
        if(index==constructor.fields.size)constructor.construct(arguments)
        else DataFunction<Any?,Any?> {argument->curried(constructor,index+1,arguments+argument)}
    }
}

object DataDictionaries {
    /** Definition schema is source-derived; these metadata records remain lazy. */
    val metadata:Map<String,DataTypeInfo> by lazy {
        val result=linkedMapOf<String,DataTypeInfo>()
        for(declaration in GenericDefinitionData.declarations) {
            lateinit var datatype:DataTypeInfo
            val constructors=declaration.constructors.mapIndexed {index,constructor->
                DataConstr({DataConstrRep.Algebraic(index.toLong()+1)},{constructor.name},
                    {GenericList.view(constructor.labels)},{DataFixity.Prefix},{datatype})
            }
            datatype=DataTypeInfo({"Text.Pandoc.Definition.${declaration.name}"},
                {DataTypeRep.Algebraic(DataThunk{GenericList.view(constructors)})})
            result[declaration.name]=datatype
        }
        lateinit var manyType:DataTypeInfo
        val manyConstructor=DataConstr({DataConstrRep.Algebraic(1)},{"Many"},
            {GenericList.cons({"unMany"}){GenericList.empty()}},{DataFixity.Prefix},{manyType})
        manyType=DataTypeInfo({"Text.Pandoc.Builder.Many"},
            {DataTypeRep.Algebraic(DataThunk{GenericList.cons({manyConstructor}){GenericList.empty()}})})
        result["Many"]=manyType
        result
    }
}

/** A represented StrictData constructor. Fields retain independent shared thunks;
 * reaching this constructor's WHNF evaluates its strict fields in declaration order. */
class DataNode(val declaration:String,val constructor:Int,val fields:List<DataThunk<Any?>>):GenericRepRoot {
    override fun forceGenericRepRoot(){for(field in fields)field()}
}

/** Builds the complete derived Definition constructor program from source schema.
 * Field dictionaries are supplied independently of values, including recursive ones. */
fun DataDictionaries.definition(name:String,resolve:(GenericTypeExpression)->DataEvidence<Any?>):DataDictionary<DataNode> {
    val declaration=GenericDefinitionData.declarations.single{it.name==name}
    val datatype=metadata.getValue(name)
    val constructors=declaration.constructors.mapIndexed{index,constructor->
        val fields=constructor.fields.map(resolve)
        DataConstructor(datatype.constructors()[index],fields,
            {value,field->DataThunk{value().fields[field]()}},
            {values->DataNode(name,index,values.mapIndexed{field,value->fields[field].argument(value)})})
    }
    val representation=DataRepresentation<DataNode>(GenericType("Text.Pandoc.Definition",name)) {value->
        value().also{it.forceGenericRepRoot()}
    }
    return AlgebraicDataDictionary(representation,datatype,constructors,
        {value->if(declaration.newtype)0 else value().constructor},
        {constructor->if(constructors.size==1)0 else {
            val rep=constructor().representation
            if(rep is DataConstrRep.Algebraic) {
                if(rep.index in 1L until constructors.size.toLong())rep.index.toInt()-1 else constructors.lastIndex
            } else dataNamedError("GHC.Internal.Data.Data.constrIndex is not supported for ",{constructor().type.nameChars},", as it is not an algebraic data type.")
        }},
        {value->if(declaration.newtype)0 else value().constructor})
}

private fun algebraicMetadata(name:String,constructors:List<Pair<String,DataFixity>>):DataTypeInfo {
    lateinit var datatype:DataTypeInfo
    val records=constructors.mapIndexed{index,(constructor,fixity)->DataConstr(
        {DataConstrRep.Algebraic(index.toLong()+1)},{constructor},{GenericList.empty()},{fixity},{datatype})}
    datatype=DataTypeInfo({name},{DataTypeRep.Algebraic(DataThunk{GenericList.view(records)})})
    return datatype
}

fun <A> DataDictionaries.sequence(element:DataEvidence<A>):DataDictionary<ManySequence<A>> {
    val type=GenericType("Data.Sequence.Internal","Seq",listOf(element.representation.type),"containers")
    val datatype=algebraicMetadata("Data.Sequence.Seq",listOf("empty" to DataFixity.Prefix,"<|" to DataFixity.Infix))
    lateinit var dictionary:DataDictionary<ManySequence<A>>
    val self=DataEvidence(DataRepresentation<ManySequence<A>>(type)){dictionary}
    val empty=DataConstructor<ManySequence<A>>(datatype.constructors()[0],emptyList(),{_,_->error("empty has no fields")},{ManySequence.empty()})
    val cons=DataConstructor<ManySequence<A>>(datatype.constructors()[1],listOf(element,self),
        {value,index->DataThunk{
            val view=ManyTrees.left(value().tree.get())?:error("empty sequence projection")
            if(index==0)(view.first as ManyCell.Leaf<A>).value.get()
            else ManySequence.fromTree{view.second}
        }},
        {values->ManySequence.fromTree{
            @Suppress("UNCHECKED_CAST")
            ManyTrees.cons(ManyCell.Leaf{values[0]() as A},(values[1]() as ManySequence<A>).tree.get())
        }})
    fun select(value:DataThunk<ManySequence<A>>)=if(ManyTrees.left(value().tree.get())==null)0 else 1
    dictionary=object:AlgebraicDataDictionary<ManySequence<A>>(self.representation,datatype,listOf(empty,cons),::select,
        {constructor->when(algebraicIndex(constructor)){1L->0;2L->1;else->error("gunfold")}}) {
        override fun <F> dataCast1(constructor:DataThunk<GenericType>,value:DataCast1<F>):GenericMaybe<DataKind<F,ManySequence<A>>> =
            if(sameConstructor(constructor(),type))GenericMaybe.Some{@Suppress("UNCHECKED_CAST") (value.value(element) as DataKind<F,ManySequence<A>>)}else GenericMaybe.None
    }
    return dictionary
}

fun <A> DataDictionaries.many(element:DataEvidence<A>):DataDictionary<Many<A>> {
    val sequence=sequence(element)
    val representation=DataRepresentation<Many<A>>(GenericType("Text.Pandoc.Builder","Many",listOf(element.representation.type)))
    val constructor=DataConstructor<Many<A>>(metadata.getValue("Many").constructors()[0],listOf(sequence.evidence),
        {value,_->DataThunk{value().unMany}},
        {values->@Suppress("UNCHECKED_CAST") (Many(values.single()() as ManySequence<A>))})
    return object:AlgebraicDataDictionary<Many<A>>(representation,metadata.getValue("Many"),listOf(constructor),{0},{0},{0}) {
        override fun <F> dataCast1(constructor:DataThunk<GenericType>,value:DataCast1<F>):GenericMaybe<DataKind<F,Many<A>>> =
            if(sameConstructor(constructor(),representation.type))GenericMaybe.Some{@Suppress("UNCHECKED_CAST") (value.value(element) as DataKind<F,Many<A>>)}else GenericMaybe.None
    }
}

private fun sameConstructor(left:GenericType,right:GenericType)=left.module==right.module&&left.name==right.name&&left.library==right.library
private fun algebraicIndex(constructor:DataThunk<DataConstr>):Long = when(val rep=constructor().representation) {
    is DataConstrRep.Algebraic->rep.index
    else->dataNamedError("GHC.Internal.Data.Data.constrIndex is not supported for ",{constructor().type.nameChars},", as it is not an algebraic data type.")
}
fun <A> DataDictionaries.list(element:DataEvidence<A>):DataDictionary<GenericList<A>> {
    val type=GenericType("GHC.Types","[]",listOf(element.representation.type),"ghc-prim")
    val datatype=algebraicMetadata("Prelude.[]",listOf("[]" to DataFixity.Prefix,"(:)" to DataFixity.Infix))
    lateinit var dictionary:DataDictionary<GenericList<A>>
    val self=DataEvidence(DataRepresentation<GenericList<A>>(type){value->value().also{it.step()}}){dictionary}
    val nil=DataConstructor<GenericList<A>>(datatype.constructors()[0],emptyList(),{_,_->error("nil has no fields")},{GenericList.empty()})
    val cons=DataConstructor<GenericList<A>>(datatype.constructors()[1],listOf(element,self),
        {value,index->DataThunk{val step=value().step() as GenericList.Step.Cons<A>;if(index==0)step.head()else step.tail()}},
        {values->@Suppress("UNCHECKED_CAST") (GenericList.cons({values[0]() as A}){values[1]() as GenericList<A>})})
    fun select(value:DataThunk<GenericList<A>>)=if(value().step()===GenericList.Step.Nil)0 else 1
    dictionary=object:AlgebraicDataDictionary<GenericList<A>>(self.representation,datatype,listOf(nil,cons),::select,
        {constructor->when(algebraicIndex(constructor)){1L->0;2L->1;else->error("GHC.Internal.Data.Data.gunfold(List)")}}) {
        override fun <F> dataCast1(constructor:DataThunk<GenericType>,value:DataCast1<F>):GenericMaybe<DataKind<F,GenericList<A>>> =
            if(sameConstructor(constructor(),type))GenericMaybe.Some{@Suppress("UNCHECKED_CAST") (value.value(element) as DataKind<F,GenericList<A>>)}else GenericMaybe.None
        override fun gmapT(transform:DataTransform,value:DataThunk<GenericList<A>>):GenericList<A> = when(val step=value().step()) {
            GenericList.Step.Nil->GenericList.empty()
            is GenericList.Step.Cons->GenericList.cons({element.argument{DataInvocation.apply(transform,element,element.argument(step::head))}()}){self.argument{DataInvocation.apply(transform,self,self.argument(step::tail))}()}
        }
        override fun <R> gmapQ(query:DataQuery<R>,value:DataThunk<GenericList<A>>):GenericList<R> = when(val step=value().step()) {
            GenericList.Step.Nil->GenericList.empty()
            is GenericList.Step.Cons->GenericList.cons({DataThunk{DataInvocation.apply(query,element,element.argument(step::head))}()}){
                GenericList.cons({DataThunk{DataInvocation.apply(query,self,self.argument(step::tail))}()}){GenericList.empty()}
            }
        }
        override fun <F> gmapM(monad:DataMonad<F>,transform:DataMonadicTransform<F>,value:DataThunk<GenericList<A>>):DataKind<F,GenericList<A>> = when(val step=value().step()) {
            GenericList.Step.Nil->monad.pure(DataThunk{GenericList.empty()})
            is GenericList.Step.Cons->monad.bind(DataThunk{DataInvocation.apply(transform,element,element.argument(step::head))}){head->
                monad.bind(DataThunk{DataInvocation.apply(transform,self,self.argument(step::tail))}){tail->monad.pure(DataThunk{GenericList.cons(head){tail()}})}
            }
        }
    }
    return dictionary
}

private class PrimitiveDataDictionary<A>(
    representation:DataRepresentation<A>,private val datatype:DataTypeInfo,
    private val constructor:(DataThunk<A>)->DataConstr,
    private val decode:(DataThunk<DataConstr>)->DataThunk<A>,
):DataDictionary<A>(representation) {
    override fun dataTypeOf(value:DataThunk<A>)=datatype
    override fun toConstr(value:DataThunk<A>)=constructor(value)
    override fun <F> gunfold(unfold:DataUnfold<F>,constructor:DataThunk<DataConstr>):DataKind<F,A> {
        // Primitive instances inspect the ConstrRep before calling z, even when z ignores its value.
        val decoded=decode(constructor)
        unfold.forceInject()
        return unfold.inject(decoded)
    }
}
private fun primitiveConstructor(type:DataTypeInfo,name:()->String,representation:()->DataConstrRep)=DataConstr(
    representation,name,{error("GHC.Internal.Data.Data.confields")},{error("GHC.Internal.Data.Data.confixity")},{type})
private fun primitiveMismatch(name:String,constructor:DataThunk<DataConstr>):Nothing =
    dataNamedError("GHC.Internal.Data.Data.gunfold: Constructor ",{constructor().nameChars}," is not of type $name.")

fun DataDictionaries.int():DataDictionary<Long> {
    val datatype=DataTypeInfo({"Prelude.Int"},{DataTypeRep.Integral})
    return PrimitiveDataDictionary(DataRepresentation(GenericDatas.int.type),datatype,
        {value->primitiveConstructor(datatype,{value().toString()},{DataConstrRep.Integral{BigInteger.valueOf(value())}})},
        {constructor->when(val rep=constructor().representation){is DataConstrRep.Integral->DataThunk{rep.value.toLong()};else->primitiveMismatch("Int",constructor)}})
}
private fun doubleRational(value:Double):DataConstrRep.Rational {
    val bits=value.toRawBits();val exponent=((bits ushr 52)and 2047).toInt()
    val fraction=bits and 0x000fffffffffffffL
    val mantissa=BigInteger.valueOf(if(exponent==0)fraction else fraction or (1L shl 52))
    val shift=if(exponent==0)-1074 else exponent-1075
    var numerator=if(bits<0)mantissa.negate()else mantissa
    var denominator=BigInteger.ONE
    if(shift>=0)numerator=numerator.shiftLeft(shift)else denominator=denominator.shiftLeft(-shift)
    val gcd=numerator.gcd(denominator)
    return DataConstrRep.Rational(numerator/gcd,denominator/gcd)
}
private fun rationalDouble(rep:DataConstrRep.Rational):Double {
    var numerator=rep.numerator;var denominator=rep.denominator
    if(denominator.signum()==0)return numerator.toDouble()/0.0
    val negative=numerator.signum()<0;numerator=numerator.abs()
    if(numerator.signum()==0)return 0.0
    if(denominator.signum()<0) {
        // Preserve the installed GHC fromRat'' non-power-of-two branch even
        // for raw (:%) values outside Data.Ratio's positive-denominator invariant.
        // integerLog2# returns zero for non-positive Integers.
        val p0=maxOf(-1021,numerator.bitLength()-1)
        val scaledNumerator=if(p0<53)numerator.shiftLeft(53-p0)else numerator
        val scaledDenominator=(if(p0>53)denominator.shiftLeft(p0-53)else denominator).shiftLeft(1)
        val rounded=scaledNumerator.divide(scaledDenominator)+BigInteger.ONE
        val result=Math.scalb(rounded.toDouble(),p0-52)
        return if(negative)-result else result
    }
    var exponent=numerator.bitLength()-denominator.bitLength()
    val comparison=if(exponent>=0)numerator.compareTo(denominator.shiftLeft(exponent))else numerator.shiftLeft(-exponent).compareTo(denominator)
    if(comparison<0)exponent--
    val sign=if(negative)Long.MIN_VALUE else 0L
    if(exponent>1023)return Double.fromBits(sign or 0x7ff0000000000000L)
    if(exponent< -1075)return Double.fromBits(sign)
    val shift=if(exponent< -1022)1074 else 52-exponent
    val scaledNumerator=if(shift>=0)numerator.shiftLeft(shift)else numerator
    val scaledDenominator=if(shift>=0)denominator else denominator.shiftLeft(-shift)
    val parts=scaledNumerator.divideAndRemainder(scaledDenominator)
    var significand=parts[0]
    val half=parts[1].shiftLeft(1).compareTo(scaledDenominator)
    if(half>0||(half==0&&significand.testBit(0)))significand+=BigInteger.ONE
    if(exponent< -1022)return Double.fromBits(sign or significand.toLong())
    if(significand.bitLength()>53){significand=significand.shiftRight(1);exponent++}
    if(exponent>1023)return Double.fromBits(sign or 0x7ff0000000000000L)
    return Double.fromBits(sign or ((exponent+1023L)shl 52) or (significand.toLong()and 0x000fffffffffffffL))
}
fun DataDictionaries.double():DataDictionary<Double> {
    val datatype=DataTypeInfo({"Prelude.Double"},{DataTypeRep.Floating})
    return PrimitiveDataDictionary(DataRepresentation(GenericDatas.double.type),datatype,
        {value->primitiveConstructor(datatype,{io.heapy.kotdoc.formats.HaskellDouble.show(value())},{DataConstrRep.Rational{doubleRational(value()).let{it.numerator to it.denominator}}})},
        {constructor->when(val rep=constructor().representation){is DataConstrRep.Rational->DataThunk{rationalDouble(rep)};else->primitiveMismatch("Double",constructor)}})
}

fun DataDictionaries.char():DataDictionary<Int> {
    val datatype=DataTypeInfo({"Prelude.Char"},{DataTypeRep.Character})
    fun showChar(value:Int):String="'"+when(value){39->"\\'";34->"\"";else->io.heapy.kotdoc.formats.HaskellString.quoteCodePoints(intArrayOf(value)).drop(1).dropLast(1)}+"'"
    return PrimitiveDataDictionary(DataRepresentation(GenericDatas.char.type),datatype,
        {value->primitiveConstructor(datatype,{showChar(value())},{DataConstrRep.Character{value()}})},
        {constructor->when(val rep=constructor().representation){is DataConstrRep.Character->DataThunk{rep.codePoint};else->primitiveMismatch("Char",constructor)}})
}
fun DataDictionaries.boolean():DataDictionary<Boolean> {
    val datatype=algebraicMetadata("GHC.Types.Bool",listOf("False" to DataFixity.Prefix,"True" to DataFixity.Prefix))
    val constructors=listOf(false,true).mapIndexed{index,value->DataConstructor<Boolean>(datatype.constructors()[index],emptyList(),{_,_->error("Bool has no fields")},{value})}
    return AlgebraicDataDictionary(DataRepresentation(GenericDatas.boolean.type),datatype,constructors,
        {value->if(value())1 else 0},{constructor->if(algebraicIndex(constructor)==1L)0 else 1})
}
fun <A,B> DataDictionaries.tuple2(left:DataEvidence<A>,right:DataEvidence<B>):DataDictionary<GenericTuple2<A,B>> {
    val type=GenericType("GHC.Tuple","(,)",listOf(left.representation.type,right.representation.type),"ghc-prim")
    val datatype=algebraicMetadata("GHC.Tuple.Tuple2",listOf("(,)" to DataFixity.Prefix))
    val constructor=DataConstructor<GenericTuple2<A,B>>(datatype.constructors()[0],listOf(left,right),
        {value,index->DataThunk{if(index==0)value().first else value().second}},
        {values->@Suppress("UNCHECKED_CAST") (GenericTuple2({values[0]() as A}){values[1]() as B})})
    return object:AlgebraicDataDictionary<GenericTuple2<A,B>>(DataRepresentation(type),datatype,listOf(constructor),{value->value();0},{0}) {
        override fun <F> dataCast2(constructor:DataThunk<GenericType>,value:DataCast2<F>):GenericMaybe<DataKind<F,GenericTuple2<A,B>>> =
            if(sameConstructor(constructor(),type))GenericMaybe.Some{@Suppress("UNCHECKED_CAST") (value.value(left,right) as DataKind<F,GenericTuple2<A,B>>)}else GenericMaybe.None
    }
}
fun <A,B,C> DataDictionaries.tuple3(first:DataEvidence<A>,second:DataEvidence<B>,third:DataEvidence<C>):DataDictionary<GenericTuple3<A,B,C>> {
    val type=GenericType("GHC.Tuple","(,,)",listOf(first.representation.type,second.representation.type,third.representation.type),"ghc-prim")
    val datatype=algebraicMetadata("GHC.Tuple.Tuple3",listOf("(,,)" to DataFixity.Prefix))
    val constructor=DataConstructor<GenericTuple3<A,B,C>>(datatype.constructors()[0],listOf(first,second,third),
        {value,index->DataThunk{when(index){0->value().first;1->value().second;else->value().third}}},
        {values->@Suppress("UNCHECKED_CAST") (GenericTuple3({values[0]() as A},{values[1]() as B},{values[2]() as C}))})
    return AlgebraicDataDictionary(DataRepresentation(type),datatype,listOf(constructor),{value->value();0},{0})
}
fun <A> DataDictionaries.maybe(element:DataEvidence<A>):DataDictionary<GenericMaybe<A>> {
    val type=GenericType("GHC.Internal.Maybe","Maybe",listOf(element.representation.type),"ghc-internal")
    val datatype=algebraicMetadata("GHC.Internal.Maybe.Maybe",listOf("Nothing" to DataFixity.Prefix,"Just" to DataFixity.Prefix))
    val nothing=DataConstructor<GenericMaybe<A>>(datatype.constructors()[0],emptyList(),{_,_->error("Nothing has no fields")},{GenericMaybe.None})
    val just=DataConstructor<GenericMaybe<A>>(datatype.constructors()[1],listOf(element),
        {value,_->DataThunk{(value() as GenericMaybe.Some<A>).value}},
        {values->@Suppress("UNCHECKED_CAST") (GenericMaybe.Some{values.single()() as A})})
    return object:AlgebraicDataDictionary<GenericMaybe<A>>(DataRepresentation(type),datatype,listOf(nothing,just),
        {value->if(value()===GenericMaybe.None)0 else 1},{constructor->if(algebraicIndex(constructor)==1L)0 else 1}) {
        override fun <F> dataCast1(constructor:DataThunk<GenericType>,value:DataCast1<F>):GenericMaybe<DataKind<F,GenericMaybe<A>>> =
            if(sameConstructor(constructor(),type))GenericMaybe.Some{@Suppress("UNCHECKED_CAST") (value.value(element) as DataKind<F,GenericMaybe<A>>)}else GenericMaybe.None
    }
}

fun DataDictionaries.text():DataDictionary<String> {
    val datatype=algebraicMetadata("Data.Text.Text",listOf("pack" to DataFixity.Prefix))
    val characters=list(char().evidence)
    val constructor=DataConstructor<String>(datatype.constructors()[0],listOf(characters.evidence),
        {value,_->DataThunk{GenericList.view(value().codePoints().toArray().toList())}},
        {values->buildString {
            @Suppress("UNCHECKED_CAST") val input=values.single()() as GenericList<Int>
            for(point in input)appendCodePoint(if(point in 0xd800..0xdfff)0xfffd else point)
        }})
    return AlgebraicDataDictionary(DataRepresentation(GenericDatas.text.type),datatype,listOf(constructor),{0},
        {c->if(algebraicIndex(c)==1L)0 else error("gunfold")},{0})
}
/** The concrete Map Text dependency used by Definition's metadata fields.
 * Keys are finite source Text; values and the abstraction's child list stay lazy. */
fun <A> DataDictionaries.textMap(element:DataEvidence<A>):DataDictionary<MetadataMap<A>> {
    val key=text().evidence
    val type=GenericType("Data.Map.Internal","Map",listOf(key.representation.type,element.representation.type),"containers")
    val datatype=algebraicMetadata("Data.Map.Internal.Map",listOf("fromList" to DataFixity.Prefix))
    val pair=tuple2(key,element)
    val entries=list(pair.evidence)
    val constructor=DataConstructor<MetadataMap<A>>(datatype.constructors()[0],listOf(entries.evidence),
        {value,_->DataThunk{GenericList.view(value().entries.map{entry->GenericTuple2({entry.key},{entry.value})})}},
        {values->@Suppress("UNCHECKED_CAST") val source=values.single()() as GenericList<GenericTuple2<String,A>>
            MetadataMap.fromEntries(Iterable{source.iterator().let{iterator->object:Iterator<Pair<String,()->A>> {
                override fun hasNext()=iterator.hasNext()
                override fun next():Pair<String,()->A> {val pair=iterator.next();return pair.first to {pair.second}}
            }}})
        })
    return object:AlgebraicDataDictionary<MetadataMap<A>>(DataRepresentation(type),datatype,listOf(constructor),{0},
        {c->if(algebraicIndex(c)==1L)0 else error("gunfold")},{0}) {
        override fun <F> dataCast2(constructor:DataThunk<GenericType>,value:DataCast2<F>):GenericMaybe<DataKind<F,MetadataMap<A>>> =
            if(sameConstructor(constructor(),type))GenericMaybe.Some{@Suppress("UNCHECKED_CAST") (value.value(key,element) as DataKind<F,MetadataMap<A>>)}else GenericMaybe.None
    }
}

/** Required source dictionaries, canonical aliases and recursive Definition fields.
 * Generic Map key classes are not inferred from Kotlin Comparable. */
class DataRegistry {
    private val dictionaries=HashMap<GenericType,DataDictionary<Any?>>()
    fun evidence(type:GenericType):DataEvidence<Any?> = DataEvidence(DataRepresentation(type)){dictionary(type)}
    fun evidence(expression:GenericTypeExpression)=evidence(GenericDefinitionData.resolve(expression).type)
    @Suppress("UNCHECKED_CAST")
    fun dictionary(type:GenericType):DataDictionary<Any?> = synchronized(dictionaries) {
        dictionaries.getOrPut(type) {
            val result:DataDictionary<*> = when(type.name) {
                "Int"->DataDictionaries.int()
                "Double"->DataDictionaries.double()
                "Char"->DataDictionaries.char()
                "Bool"->DataDictionaries.boolean()
                "Text"->DataDictionaries.text()
                "[]"->DataDictionaries.list(evidence(type.arguments.single()))
                "(,)"->DataDictionaries.tuple2(evidence(type.arguments[0]),evidence(type.arguments[1]))
                "(,,)"->DataDictionaries.tuple3(evidence(type.arguments[0]),evidence(type.arguments[1]),evidence(type.arguments[2]))
                "Maybe"->DataDictionaries.maybe(evidence(type.arguments.single()))
                "Seq"->DataDictionaries.sequence(evidence(type.arguments.single()))
                "Many"->DataDictionaries.many(evidence(type.arguments.single()))
                "Map"->{require(type.arguments[0]==GenericDatas.text.type){"Supply an explicit source Ord dictionary for other Map keys"};DataDictionaries.textMap(evidence(type.arguments[1]))}
                else->DataDictionaries.definition(type.name,::evidence)
            }
            result as DataDictionary<Any?>
        }
    }
}
