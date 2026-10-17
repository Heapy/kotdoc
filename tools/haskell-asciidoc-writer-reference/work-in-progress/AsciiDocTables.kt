// Text.Pandoc.Writers.AsciiDoc, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*

internal object AsciiDocTables {
 fun decrement(cell:Cell)=if(cell.rowSpan>1)cell.copy(rowSpan=cell.rowSpan-1)else cell
 fun applyEmptyCells(row:Row,placements:List<Pair<Long,Cell>>):Row {
  val sorted=placements.sortedBy{it.first};val out=mutableListOf<Cell>();var position=0L;var cellAt=0;var emptyAt=0
  while(emptyAt<sorted.size){val(at,empty)=sorted[emptyAt]
   if(at==position){out.add(empty);position+=empty.colSpan;emptyAt++}
   else if(cellAt==row.cells.size)break
   else{val cell=row.cells[cellAt++];out.add(cell);position+=cell.colSpan}
  }
  while(cellAt<row.cells.size)out.add(row.cells[cellAt++])
  return row.copy(cells=out)
 }
 fun adjustHeaders(rows:List<Row>):List<Row>{
  if(rows.size<2)return rows
  val placements=mutableListOf<Pair<Long,Cell>>();var position=0L
  val cells=rows[0].cells.map{cell->val at=position;position+=cell.colSpan
   if(cell.rowSpan>1){placements.add(0,at to Cell(Attr(),Alignment.AlignDefault,cell.rowSpan-1,cell.colSpan,emptyList()));cell.copy(rowSpan=1)}else cell}
  return listOf(rows[0].copy(cells=cells),applyEmptyCells(rows[1],placements))+rows.drop(2)
 }
 data class FooterCell(val column:Long,val spans:Map<Long,WriterTables.Span>,val cell:Cell,val empty:Pair<Long,Cell>?)
 fun adjustFooterCell(rowIndex:Long,footerLength:Long,column:Long,previous:Map<Long,WriterTables.Span>,cell:Cell):FooterCell{
  var at=column;var spans=previous
  while(true){val prior=WriterTables.takePreviousSpansAtColumn(at,spans)?:break;at+=prior.first;spans=prior.second}
  val updated=WriterTables.insertCurrentSpansAtColumn(at,spans,cell.rowSpan,cell.colSpan)
  val reaches=cell.rowSpan>1&&rowIndex+cell.rowSpan>=footerLength
  return FooterCell(at+cell.colSpan,updated,if(reaches)decrement(cell)else cell,
   if(reaches)at to Cell(Attr(),Alignment.AlignDefault,1,cell.colSpan,emptyList())else null)
 }
 fun adjustFooters(rows:List<Row>):List<Row>{
  if(rows.size<2)return rows
  val out=mutableListOf<Row>();val placements=mutableListOf<Pair<Long,Cell>>();var spans:Map<Long,WriterTables.Span> = emptyMap()
  for(i in 0 until rows.lastIndex){val row=rows[i];var column=0L;val cells=mutableListOf<Cell>()
   for(cell in row.cells){val adjusted=adjustFooterCell(i.toLong(),rows.size.toLong(),column,spans,cell);column=adjusted.column;spans=adjusted.spans;cells.add(adjusted.cell);adjusted.empty?.let(placements::add)}
   spans=WriterTables.decrementTrailingRowSpans(column,spans);out.add(row.copy(cells=cells))
  }
  out.add(applyEmptyCells(rows.last(),placements));return out
 }
 private data class RowsLeft(val attr:Attr,val cells:List<Pair<Cell,Long>>){
  fun row()=Row(attr,cells.map{it.first})
  fun remaining(decrement:Boolean)=copy(cells=cells.map{(cell,left)->if(left>=1)(if(decrement)AsciiDocTables.decrement(cell)else cell)to(left-1)else cell to left})
 }
 fun adjustEmptyRows(rows:List<Row>):List<Row>{
  val pending=java.util.ArrayDeque<RowsLeft>();rows.forEach{row->pending.addLast(RowsLeft(row.attr,row.cells.map{it to if(it.rowSpan>1)it.rowSpan-1 else 0}))}
  val out=mutableListOf<Row>()
  while(pending.isNotEmpty()){
   val row=pending.removeFirst();val maximum=row.cells.maxOfOrNull{it.first.rowSpan}?:0
   if(maximum<=1){out.add(row.row());continue}
   val following=mutableListOf<RowsLeft>();var count=maximum-1
   while(count>0&&pending.isNotEmpty()){following.add(pending.removeFirst());count--}
   var active=listOf(row)
   for(next in following){val consume=next.cells.isEmpty()&&active.any{it.cells.any{(cell,left)->cell.rowSpan>1&&left>=1}}
    active=active.map{it.remaining(consume)}.let{if(consume)it else listOf(next)+it}
   }
   val ordered=active.asReversed();out.add(ordered.first().row());for(i in ordered.lastIndex downTo 1)pending.addFirst(ordered[i])
  }
  return out
 }
}
