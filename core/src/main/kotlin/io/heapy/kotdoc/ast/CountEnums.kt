// GHC.Internal.Enum eftInt/efdtIntUp/efdtIntDn translation.
// Copyright (c) The University of Glasgow, 1992-2002; BSD-style.
package io.heapy.kotdoc.ast
internal object CountEnums {
 fun <C> ascending(first:Long,last:Long,wrap:(Long)->C):GenericList<C> = GenericList.defer{
  if(first>last)GenericList.empty()else GenericList.cons({wrap(first)}){if(first==last)GenericList.empty()else ascending(first+1,last,wrap)}
 }
 fun <C> stepped(first:Long,second:Long,last:Long,wrap:(Long)->C):GenericList<C>{
  val up=second>=first
  if(if(up)last<second else last>second)return if(if(up)last<first else last>first)GenericList.empty()else GenericList.cons({wrap(first)},{GenericList.empty()})
  val delta=second-first
  val threshold=last-delta
  fun next(value:Long):GenericList<C> = GenericList.cons({wrap(value)}){if(if(up)value>threshold else value<threshold)GenericList.empty()else next(value+delta)}
  return GenericList.cons({wrap(first)}){next(second)}
 }
}
