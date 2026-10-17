// pandoc-types Definition count newtypes, Copyright (c) 2006-2023 John MacFarlane.
// GHC.Internal.Num: (c) The University of Glasgow 1994-2002.
// GHC.Internal.Enum: (c) The University of Glasgow, 1992-2002.
// BSD notices retained with the original source and in licenses/.
package io.heapy.kotdoc.ast
import java.math.BigInteger

/** Distinct source newtypes; existing table fields retain their Long compatibility API. */
sealed interface PandocCount {val value:Long}
@JvmInline value class RowHeadColumns(override val value:Long):PandocCount
@JvmInline value class RowSpan(override val value:Long):PandocCount
@JvmInline value class ColSpan(override val value:Long):PandocCount

/** Only the source Num/Enum dictionaries are supplied; no Real/Integral/Bounded instance. */
class CountDictionary<C:PandocCount> internal constructor(internal val wrap:(Long)->C){
 /** Newtype construction and pattern matching retain source non-strictness. */
 fun construct(value:()->Long):()->C {val result=GenericMemo{wrap(value())};return result::get}
 fun <R> match(value:()->C,next:(()->Long)->R):R {val root=GenericMemo(value);return next{root.get().value}}
 fun plus(left:()->C,right:()->C):C=wrap(left().value+right().value)
 fun minus(left:()->C,right:()->C):C=wrap(left().value-right().value)
 fun times(left:()->C,right:()->C):C=wrap(left().value*right().value)
 fun negate(value:()->C):C=wrap(-value().value)
 fun abs(value:()->C):C {val n=value().value;return wrap(if(n<0)-n else n)}
 fun signum(value:()->C):C {val n=value().value;return wrap(when{n<0->-1;n>0->1;else->0})}
 fun fromInteger(value:BigInteger):C=wrap(value.toLong())
 fun succ(value:()->C):C {val n=value().value;if(n==Long.MAX_VALUE)error("Enum.succ{Int}: tried to take `succ' of last tag in enumeration");return wrap(n+1)}
 fun pred(value:()->C):C {val n=value().value;if(n==Long.MIN_VALUE)error("Enum.pred{Int}: tried to take `pred' of first tag in enumeration");return wrap(n-1)}
 fun toEnum(value:Long):C=wrap(value)
 fun fromEnum(value:()->C):Long=value().value
 fun enumFrom(value:()->C):GenericList<C> = GenericList.defer{CountEnums.ascending(value().value,Long.MAX_VALUE,wrap)}
 fun enumFromTo(first:()->C,last:()->C):GenericList<C> = GenericList.defer{CountEnums.ascending(first().value,last().value,wrap)}
 fun enumFromThen(first:()->C,second:()->C):GenericList<C> = GenericList.defer{val x=first().value;val y=second().value;CountEnums.stepped(x,y,if(y>=x)Long.MAX_VALUE else Long.MIN_VALUE,wrap)}
 fun enumFromThenTo(first:()->C,second:()->C,last:()->C):GenericList<C> = GenericList.defer{CountEnums.stepped(first().value,second().value,last().value,wrap)}
}
object PandocCounts {
 val RowHeadColumns=CountDictionary(::RowHeadColumns)
 val RowSpan=CountDictionary(::RowSpan)
 val ColSpan=CountDictionary(::ColSpan)
}
