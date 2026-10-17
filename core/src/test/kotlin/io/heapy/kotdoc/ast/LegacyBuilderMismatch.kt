package io.heapy.kotdoc.ast
/** Counterparts: BuilderDemand.hs */
internal fun legacyBuilderObservations():List<Pair<String,()->Long>> {
 val cases=listOf<Pair<String,()->Long>>(
  "lineBlock/lines/length" to {Builder.lineBlock(GenericList.defer{error("lines")}).values.size.toLong()},
  "orderedListWith/items/length" to {Builder.orderedListWith(ListAttributes(),GenericList.defer{error("items")}).values.size.toLong()},
  "orderedList/items/length" to {Builder.orderedList(GenericList.defer{error("items")}).values.size.toLong()},
  "bulletList/items/length" to {Builder.bulletList(GenericList.defer{error("items")}).values.size.toLong()},
  "definitionList/definitions/length" to {Builder.definitionList(GenericList.defer{error("definitions")}).values.size.toLong()}
 )
 return cases
}

fun main(){for((name,observe)in legacyBuilderObservations())println(name+":"+try{observe().toString()}catch(e:RuntimeException){"exception:"+e.message})}
