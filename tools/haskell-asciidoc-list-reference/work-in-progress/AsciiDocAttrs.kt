// Shared.combineAttr and makeSections attribute closure. Pandoc authors,
// GPL-2.0-or-later. Source nubOrd preserves first class; foldr preserves kvs1
// and the rightmost duplicate in kvs2. Unused fields remain unforced.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.Attr
internal fun <A> asciiDocContains(values:List<A>,value:A):Boolean {for(item in AsciiDocPatternList(values))if(item==value)return true;return false}
internal fun <A> asciiDocPrepend(first:A,rest:List<A>):List<A> =asciiDocGenerated(sequence{yield(first);for(value in AsciiDocPatternList(rest))yield(value)})
internal fun asciiDocCombineAttr(first:Attr,second:Attr):Attr {
 val classes=asciiDocGenerated(sequence{
  val seen=hashSetOf<String>();for(values in listOf(first.classes,second.classes))for(value in AsciiDocPatternList(values))if(seen.add(value))yield(value)
 })
 val merged by lazy{
  val fold=DeepRecursiveFunction<AsciiDocPatternList<Pair<String,String>>,List<Pair<String,String>>>{input->
   if(!input.hasAt(0))first.keyValues else{
    val entry=input[0];val rest=callRecursive(input.dropFirst());var found=false
    for(existing in AsciiDocPatternList(rest))if(existing.first==entry.first){found=true;break}
    if(found)rest else asciiDocPrepend(entry,rest)
   }
  }
  fold(AsciiDocPatternList(second.keyValues))
 }
 val kvs=asciiDocGenerated(sequence{for(value in AsciiDocPatternList(merged))yield(value)})
 return Attr(first.identifier.ifEmpty{second.identifier},classes,kvs)
}
