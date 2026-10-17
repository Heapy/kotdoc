package io.heapy.kotdoc.ast
/** Corresponding observations are evaluated independently in TableDemand.hs. */
internal fun legacyTableObservations():List<Pair<String,()->String>> {
 fun row()=Row(Attr(),listOf(Tables.emptyCell))
 fun rows()=GenericList.cons({row()},{GenericList.cons<Row>({error("second")},{GenericList.empty()})})
 fun cells()=listOf(Row(Attr(),GenericList.cons({Tables.emptyCell},{GenericList.cons<Cell>({error("second")},{GenericList.empty()})})))
 fun <A> first(input:List<A>):List<A> = when(val n=GenericList.view(input).step()){GenericList.Step.Nil->emptyList();is GenericList.Step.Cons->listOf(n.head())}
 val probes=listOf<Pair<String,()->String>>(
  "clip-prefix-ignores-second-row" to {AstShow.show(first(Tables.clipRows(rows())))},
  "head-zero-unused-second-cell" to {AstShow.show(Tables.normalizeHead(0,TableHead(Attr(),cells())).rows)},
  "foot-zero-unused-second-cell" to {AstShow.show(Tables.normalizeFoot(0,TableFoot(Attr(),cells())).rows)},
  "body-zero-unused-second-cell" to {AstShow.show(Tables.normalizeBody(0,TableBody(Attr(),0,emptyList(),cells())).body)},
  "head-prefix-ignores-second-row" to {AstShow.show(first(Tables.normalizeHead(1,TableHead(Attr(),rows())).rows))},
  "foot-prefix-ignores-second-row" to {AstShow.show(first(Tables.normalizeFoot(1,TableFoot(Attr(),rows())).rows))},
  "legacy-tableWith-ignores-head" to {Builder.tableWith(Attr(),Tables.emptyCaption,listOf(ColSpec(Alignment.AlignDefault,ColWidth.Default)),TableHead(Attr(),rows()),emptyList(),TableFoot(Attr(),emptyList())).values.size.toString()},
  "legacy-table-ignores-head" to {Builder.table(Tables.emptyCaption,listOf(ColSpec(Alignment.AlignDefault,ColWidth.Default)),TableHead(Attr(),rows()),emptyList(),TableFoot(Attr(),emptyList())).values.size.toString()},
  "legacy-simpleTable-ignores-header" to {Builder.simpleTable(GenericList.cons<Blocks>({error("header")},{GenericList.empty()}),emptyList()).values.size.toString()}
 )
 return probes
}

fun main(){ for((name,probe)in legacyTableObservations())println(name+":"+try{probe()}catch(e:RuntimeException){"exception:"+e.message}) }
