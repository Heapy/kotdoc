// Text.Pandoc.Readers.Org.Blocks, Copyright (C) 2014-2024 Albert Krewinkel.
// GPL-2.0-or-later; original source and notices retained in upstream/pandoc.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.resources.safeReadInteger
internal data class OrgColumn(val alignment:Alignment?=null,val width:Long?=null)
internal sealed interface OrgRow {class Content(val cells:OF<List<Blocks>>):OrgRow;class Align(val columns:List<OrgColumn>):OrgRow;data object Hline:OrgRow}
internal data class OrgTable(val columns:List<OrgColumn> = emptyList(),val header:List<Blocks> = emptyList(),val rows:List<List<Blocks>> = emptyList())
internal fun OrgGrammar.endOfCell():OP<Int> = (char('|') or newline().lookAhead()).attempt()
internal fun OrgGrammar.columnPropertyCell():OP<OrgColumn> {
 val empty=skipSpaces().then(endOfCell()).map{OrgColumn()}.attempt()
 val align=char('l').map{Alignment.AlignLeft} or char('c').map{Alignment.AlignCenter} or char('r').map{Alignment.AlignRight}
 val width=take1(ParserCharacters::isDigit).flatMap{raw->safeReadInteger(raw)?.toLong()?.let(::pure)?:zero()}
 return (empty or skipSpaces().then(char('<')).then(maybe(align)).flatMap{a->maybe(width).before(char('>')).before(empty).map{OrgColumn(a,it)}}.attempt()).label("alignment info")
}
internal fun OrgGrammar.tableAlignRow():OP<OrgRow> = tableStart().then(till1(columnPropertyCell(),newline())).flatMap{columns->guard(columns.any{it!=OrgColumn()}).map<OrgRow>{OrgRow.Align(columns)}}.attempt()
internal fun OrgGrammar.tableContentCell():OP<OF<Blocks>> = till(delay{inline()},endOfCell()).map{inlines(it).mapValue{B.plain(it.trim())}}.attempt()
internal fun OrgGrammar.tableContentRow():OP<OrgRow> = tableStart().then(till(tableContentCell(),newline())).map<OrgRow>{OrgRow.Content(sequence(it))}.attempt()
internal fun OrgGrammar.tableHline():OP<OrgRow> = tableStart().then(char('-')).then(anyLine()).map<OrgRow>{OrgRow.Hline}.attempt()
internal fun OrgGrammar.orgTable():OP<OF<Blocks>> = tableStart().lookAhead().then((tableAlignRow() or tableHline() or tableContentRow()).many().attempt()).map{rows->
 var out:OF<OrgTable> = future(OrgTable())
 for(row in rows)out=out.flatMapValue{table->when(row){OrgRow.Hline->future(if(table.header.isEmpty()&&table.rows.size==1)table.copy(header=table.rows.single(),rows=emptyList())else table);is OrgRow.Align->future(table.copy(columns=row.columns));is OrgRow.Content->row.cells.mapValue{table.copy(rows=listOf(it)+table.rows)}}}
 out.mapValue{table->val body=table.rows.asReversed();val cols=(if(table.header.isNotEmpty())table.header else body.firstOrNull()?:emptyList()).size;val properties=List(cols){table.columns.getOrElse(it){OrgColumn()}};val total=if(properties.any{it.width!=null})properties.fold(0L){sum,c->sum+(c.width?:1)}else null
 fun row(cells:List<Blocks>)=Row(Attr(),cells.map{Cell(Attr(),Alignment.AlignDefault,1,1,it.values)})
 val specs=properties.map{c->ColSpec(c.alignment?:Alignment.AlignDefault,if(total!=null&&c.width!=null)ColWidth.Width(c.width.toDouble()/total.toDouble())else ColWidth.Default)}
 TableCompaction.compactify(B.tableWith(Attr(),Caption(null,emptyList()),specs,TableHead(Attr(),if(table.header.isEmpty())emptyList()else listOf(row(table.header))),listOf(TableBody(Attr(),0,emptyList(),body.map(::row))),TableFoot(Attr(),emptyList())))
 }
}
internal fun OrgGrammar.table():OP<OF<Blocks>> = state().flatMap{s->guard(!(s.user.listContext&&s.user.lastPre==null))}.then(blockAttributes()).flatMap{attrs->(gridTable() or orgTable()).flatMap{table->state().map{s->if(!s.user.export.withTables)future(Blocks())else table.flatMapValue{blocks->val original=blocks.values.singleOrNull()as?Block.Table
 if(original==null)future(TableCompaction.compactify(blocks))else (attrs.caption?:future(Inlines())).mapValue{caption->TableCompaction.compactify(Blocks(listOf(original.copy(attr=Attr(attrs.name?:"",emptyList(),attrs.keyValues),caption=if(attrs.caption==null)Caption(null,emptyList())else Caption(null,B.plain(caption).values)))))}}
 }}}.attempt()
