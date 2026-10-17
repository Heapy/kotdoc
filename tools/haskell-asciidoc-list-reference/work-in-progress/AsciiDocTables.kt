// Text.Pandoc.Writers.AsciiDoc, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*

internal object AsciiDocTables {
 fun decrement(cell:Cell)=if(cell.rowSpan>1)cell.copy(rowSpan=cell.rowSpan-1)else cell
 fun applyEmptyCells(row:Row,placements:List<Pair<Long,Cell>>):Row {
  val sorted=asciiDocWholeSpine(placements).sortedBy{it.first}
  if(sorted.isEmpty())return row
  val cells=AsciiDocPatternList(row.cells)
  return row.copy(cells=asciiDocGenerated(sequence{
   var position=0L;var cellAt=0;var emptyAt=0
   while(emptyAt<sorted.size){val(at,empty)=sorted[emptyAt]
    if(at==position){yield(empty);position+=empty.colSpan;emptyAt++}
    else if(!cells.hasAt(cellAt))return@sequence
    else{val cell=cells[cellAt++];yield(cell);position+=cell.colSpan}
   }
   while(cells.hasAt(cellAt))yield(cells[cellAt++])
  }))
 }
 fun adjustHeaders(rows:List<Row>):List<Row> =asciiDocGenerated(sequence{
  val input=AsciiDocPatternList(rows)
  if(!input.hasAt(0))return@sequence
  if(!input.hasAt(1)){yield(input[0]);return@sequence}
  val first=input[0];val placements=mutableListOf<Pair<Long,Cell>>();var position=0L
  val cells=asciiDocLazyMap(first.cells){cell->val at=position;position+=cell.colSpan
   if(cell.rowSpan>1){placements.add(0,at to Cell(Attr(),Alignment.AlignDefault,cell.rowSpan-1,cell.colSpan,emptyList()));cell.copy(rowSpan=1)}else cell}
  yield(first.copy(cells=cells))
  for(ignored in cells){} // mapAccumL final accumulator is required by row two.
  yield(applyEmptyCells(input[1],placements));var index=2
  while(input.hasAt(index))yield(input[index++])
 })
 data class FooterCell(val column:Long,val spans:Map<Long,WriterTables.Span>,val cell:Cell,val empty:Pair<Long,Cell>?)
 fun adjustFooterCell(rowIndex:Long,footerLength:Long,column:Long,previous:Map<Long,WriterTables.Span>,cell:Cell):FooterCell{
  var at=column;var spans=previous
  while(true){val prior=WriterTables.takePreviousSpansAtColumn(at,spans)?:break;at+=prior.first;spans=prior.second}
  val updated=WriterTables.insertCurrentSpansAtColumn(at,spans,cell.rowSpan,cell.colSpan)
  val reaches=cell.rowSpan>1&&rowIndex+cell.rowSpan>=footerLength
  return FooterCell(at+cell.colSpan,updated,if(reaches)decrement(cell)else cell,
   if(reaches)at to Cell(Attr(),Alignment.AlignDefault,1,cell.colSpan,emptyList())else null)
 }
 fun adjustFooters(rows:List<Row>):List<Row> =asciiDocGenerated(sequence{
  val input=AsciiDocPatternList(rows)
  if(!input.hasAt(0))return@sequence
  if(!input.hasAt(1)){yield(input[0]);return@sequence}
  val footerLength by lazy {asciiDocWholeSpine(rows).size.toLong()} // explicit source length, only a reaching rowspan needs it.
  val placements=mutableListOf<Pair<Long,Cell>>();var spans:Map<Long,WriterTables.Span> =emptyMap();var index=0
  while(input.hasAt(index+1)){
   val row=input[index];var column=0L;val cells=mutableListOf<Cell>()
   for(cell in AsciiDocPatternList(row.cells)){
    val adjusted=adjustFooterCell(index.toLong(),if(cell.rowSpan>1)footerLength else 0,column,spans,cell)
    column=adjusted.column;spans=adjusted.spans;cells.add(adjusted.cell);adjusted.empty?.let(placements::add)
   }
   spans=WriterTables.decrementTrailingRowSpans(column,spans);yield(row.copy(cells=cells));index++
  }
  yield(applyEmptyCells(input[index],placements))
 })
 private class Change(val decrement:Boolean,val previous:Change?)
 private data class RowsLeft(val attr:Attr,val initial:List<Pair<Cell,Long>>,val changes:Change?=null){
  fun cells():List<Pair<Cell,Long>> =asciiDocLazyMap(initial){original->
   var cell=original.first;var left=original.second;val steps=java.util.ArrayDeque<Boolean>();var node=changes
   while(node!=null){steps.addFirst(node.decrement);node=node.previous}
   for(change in steps)if(left>=1){if(change)cell=decrement(cell);left--}
   cell to left
  }
  fun row()=Row(attr,asciiDocLazyMap(cells()){it.first})
  fun remaining(decrement:Boolean)=copy(changes=Change(decrement,changes))
  fun hasSpan():Boolean {for((cell,left)in AsciiDocPatternList(cells()))if(cell.rowSpan>1&&left>=1)return true;return false}
 }
 fun adjustEmptyRows(rows:List<Row>):List<Row> =asciiDocGenerated(sequence{
  val source=AsciiDocPatternList(rows).iterator();val pending=java.util.ArrayDeque<RowsLeft>()
  fun pull():RowsLeft? =if(pending.isNotEmpty())pending.removeFirst()else if(source.hasNext())source.next().let{row->RowsLeft(row.attr,asciiDocLazyMap(row.cells){it to if(it.rowSpan>1)it.rowSpan-1 else 0})}else null
  while(true){
   val row=pull()?:break;var maximum=0L;for((cell,_)in AsciiDocPatternList(row.cells()))maximum=maxOf(maximum,cell.rowSpan)
   if(maximum<=1){yield(row.row());continue}
   var active=listOf(row);var count=maximum-1
   while(count>0){val next=pull()?:break;count--
    val consume=asciiDocNull(next.initial)&&active.any{it.hasSpan()}
    active=active.map{it.remaining(consume)}.let{if(consume)it else listOf(next)+it}
   }
   val ordered=active.asReversed();yield(ordered.first().row());for(i in ordered.lastIndex downTo 1)pending.addFirst(ordered[i])
  }
 })
 fun bodiesToRows(bodies:List<TableBody>):List<Row> =asciiDocGenerated(sequence{for(body in AsciiDocPatternList(bodies)){for(row in AsciiDocPatternList(body.head))yield(row);for(row in AsciiDocPatternList(body.body))yield(row)}})
 fun allRowsEmpty(rows:List<Row>):Boolean {for(row in AsciiDocPatternList(rows))for(cell in AsciiDocPatternList(row.cells))if(!asciiDocNull(cell.blocks))return false;return true}
}
