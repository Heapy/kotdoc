// Text.Pandoc.Parsing.GridTable, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; pinned original source retained in upstream/pandoc.
package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*

/** The Identity-specialized table adapter used by the original RST reader. */
internal object RstTableAdapter {
 enum class Normalization { None, Header }
 data class Spanned(val blocks:Blocks,val rows:Long=1,val columns:Long=1)
 fun widthsFromIndices(columns:Long,indices:List<Int>):List<Double>{
  if(indices.isEmpty())return emptyList()
  val lengths=indices.mapIndexed{i,n->n-(indices.getOrNull(i-1)?:0)}.toMutableList()
  if(lengths.size>=2){val x=lengths.last();val y=lengths[lengths.lastIndex-1];if(x<y&&y-x<=2)lengths[lengths.lastIndex]=y}
  val total=lengths.sum();val quotient=maxOf(total.toLong(),maxOf(columns,indices.last().toLong())).toDouble()
  return lengths.map{it/quotient}.drop(1)
 }
 fun components(normalization:Normalization,aligns:List<Alignment>,widths:List<Double>,heads:List<List<Spanned>>,rows:List<List<Spanned>>):Blocks{
  fun row(cells:List<Spanned>)=Row(Attr(),cells.map{Cell(Attr(),Alignment.AlignDefault,it.rows,it.columns,it.blocks.values)})
  val total=widths.sum();val normalized=if(total<1)widths else widths.map{it/total}
  val specs=aligns.zip(normalized){a,w->ColSpec(a,if(w>0)ColWidth.Width(w)else ColWidth.Default)}
  val head=heads.filter{if(normalization==Normalization.None)it.isNotEmpty()else it.any{cell->cell.blocks.values.isNotEmpty()}}.map(::row)
  return Builder.tableWith(Attr(),Caption(null,emptyList()),specs,TableHead(Attr(),head),listOf(TableBody(Attr(),0,emptyList(),rows.map(::row))),TableFoot(Attr(),emptyList()))
 }
 fun <U> grid(normalization:Normalization,columns:(U)->Long,blanklines:Parsec<U,*>,parseCell:(String)->Parsec<U,Blocks>):Parsec<U,Blocks>{
  fun <A> sequence(ps:List<Parsec<U,A>>):Parsec<U,List<A>>{
   class Link<A>(val value:A,val next:Link<A>?)
   fun loop(i:Int):Parsec<U,Link<A>?> = if(i==ps.size)Parsec.pure(null)else ps[i].flatMap{a->Parsec.defer{loop(i+1)}.map{Link(a,it)}}
   return Parsec.defer{loop(0)}.map{node->buildList{var at=node;while(at!=null){add(at.value);at=at.next}}}
  }
  return RstGridTable.parser<U>().before(blanklines.optional()).flatMap{table->
   val parsed=table.rows.map{row->sequence(row.map{cell->
    val stripped=cell.lines.map{it.trimEnd{c->ParserCharacters.isSpace(c.code)}}
    val unindented=if(stripped.all{it.isEmpty()||it.startsWith(' ')})stripped.map{it.drop(1)}else stripped
    val raw=unindented.joinToString("\n",postfix=if(unindented.isEmpty())""else"\n")+"\n"
    parseCell(raw).map{Cell(Attr(),Alignment.AlignDefault,cell.rowSpan.toLong(),cell.colSpan.toLong(),it.values)}
   }).map{Row(Attr(),it)}}
   sequence(parsed).flatMap{rows->Parsec.state<U>().map{state->
    val widths=table.specs.map{it.second+1};val norm=maxOf((widths.sum()+widths.size-2).toLong(),columns(state.user)).toDouble()
    val specs=table.specs.zip(widths){spec,w->ColSpec(spec.first,ColWidth.Width(w/norm))}
    val h=table.head?:0;var heads=rows.take(h);val rest=rows.drop(h);val bodyLength=table.foot?.let{it-h-1}?:rest.size
    val body=rest.take(bodyLength.coerceAtLeast(0));val foot=rest.drop(bodyLength.coerceAtLeast(0))
    if(normalization==Normalization.Header&&heads.size==1){val cells=heads.single().cells;if(cells.isEmpty()||cells.all{it.attr==Attr()&&it.alignment==Alignment.AlignDefault&&it.rowSpan==1L&&it.colSpan==1L&&it.blocks.isEmpty()})heads=emptyList()}
    Builder.tableWith(Attr(),Caption(null,emptyList()),specs,TableHead(Attr(),heads),listOf(TableBody(Attr(),0,emptyList(),body)),TableFoot(Attr(),foot))
   }}
  }
 }
}
