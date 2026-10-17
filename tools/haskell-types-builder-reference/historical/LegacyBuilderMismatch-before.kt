package io.heapy.kotdoc.ast
/** Counterparts: BuilderDemand.hs */
fun main(){
 val cases=listOf<Pair<String,()->Long>>(
  "lineBlock/lines/length" to {Builder.lineBlock(GenericList.defer{error("lines")}).values.size.toLong()},
  "orderedList/items/length" to {Builder.orderedList(GenericList.defer{error("items")}).values.size.toLong()},
  "bulletList/items/length" to {Builder.bulletList(GenericList.defer{error("items")}).values.size.toLong()},
  "definitionList/definitions/length" to {Builder.definitionList(GenericList.defer{error("definitions")}).values.size.toLong()}
 )
 for((name,observe)in cases)println(name+":"+try{observe().toString()}catch(e:RuntimeException){"exception:"+e.message})
}
