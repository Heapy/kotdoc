// Port of Text.Pandoc.Builder normalize/clip/place table processing.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

object PandocTables {
 fun placeRowSection(oldHang:()->List<Long>,cells:()->List<Cell>):()->TablePlacement=TableStreams.placement(oldHang,cells)
 fun placeRowSection(oldHang:List<Long>,cells:List<Cell>):TablePlacement=placeRowSection({oldHang},{cells})()
 fun clipRows(input:()->List<Row>):GenericList<Row>{
  val source=GenericMemo(input);val height=GenericMemo{TableStreams.length(source.get())}
  fun clipped(high:Long,rows:()->GenericList<Row>):GenericList<Row> = GenericList.defer{
   if(high<1)return@defer GenericList.empty()
   when(val n=rows().step()){
    GenericList.Step.Nil->GenericList.empty()
    is GenericList.Step.Cons->GenericList.cons({
     val row=TableStreams.row(n::head)
     val cells=TableStreams.map({row.cells}){value->val cell=TableStreams.cell(value);TableStreams.cell{cell.copy(rowSpan=minOf(high,maxOf(1L,cell.rowSpan)))}}
     TableStreams.row{row.copy(cells=cells)}
    },{clipped(high-1,n::tail)})
   }
  }
  return GenericList.defer{clipped(height.get()){GenericList.view(source.get())}}
 }
 fun clipRows(rows:List<Row>)=clipRows{rows}
 private fun header(width:()->Long,rows:()->List<Row>):GenericList<Row>{
  val clipped=clipRows(rows)
  fun next(hang:()->List<Long>,rows:()->GenericList<Row>):GenericList<Row> = GenericList.defer{when(val n=rows().step()){
   GenericList.Step.Nil->GenericList.empty()
   is GenericList.Step.Cons->{
    val row=TableStreams.row(n::head)
    val stream=TableStreams.append({GenericList.view(row.cells)},{TableStreams.repeat{TableStreams.emptyCell}})
    val placed=placeRowSection(hang){stream}
    GenericList.cons({TableStreams.row{row.copy(cells=placed().second)}},{next({placed().first},n::tail)})
   }
  }}
  val initial=GenericMemo{TableStreams.replicate(width()){1L}}
  return next(initial::get){clipped}
 }
 private fun body(width:()->Long,headWidth:()->Long,rows:()->List<Row>):GenericList<Row>{
  val clipped=clipRows(rows)
  fun next(headHang:()->List<Long>,bodyHang:()->List<Long>,rows:()->GenericList<Row>):GenericList<Row> = GenericList.defer{when(val n=rows().step()){
   GenericList.Step.Nil->GenericList.empty()
   is GenericList.Step.Cons->{
    val row=TableStreams.row(n::head)
    val stream=TableStreams.append({GenericList.view(row.cells)},{TableStreams.repeat{TableStreams.emptyCell}})
    val head=placeRowSection(headHang){stream}
    val body=placeRowSection(bodyHang){head().third}
    GenericList.cons({val cells=TableStreams.append({head().second},{body().second});TableStreams.row{row.copy(cells=cells)}},{next({head().first},{body().first},n::tail)})
   }
  }}
  val h=GenericMemo{TableStreams.replicate(headWidth()){1L}}
  val b=GenericMemo{TableStreams.replicate(width()-headWidth()){1L}}
  return next(h::get,b::get){clipped}
 }
 fun normalizeTableHead(width:()->Long,input:()->TableHead):()->TableHead {
  val w=GenericMemo(width);val result=GenericMemo{val head=TableStreams.head(input);val rows=header(w::get){head.rows};TableStreams.head{head.copy(rows=rows)}}
  return result::get
 }
 fun normalizeTableHead(width:Long,input:TableHead):TableHead=normalizeTableHead({width},{input})()
 fun normalizeTableFoot(width:()->Long,input:()->TableFoot):()->TableFoot {
  val w=GenericMemo(width);val result=GenericMemo{val foot=TableStreams.foot(input);val rows=header(w::get){foot.rows};TableStreams.foot{foot.copy(rows=rows)}}
  return result::get
 }
 fun normalizeTableFoot(width:Long,input:TableFoot):TableFoot=normalizeTableFoot({width},{input})()
 fun normalizeTableBody(width:()->Long,input:()->TableBody):()->TableBody {
  val w=GenericMemo(width);val result=GenericMemo{
   val source=TableStreams.body(input);val headWidth=maxOf(0L,minOf(w.get(),source.rowHeadColumns))
   val head=header(w::get){source.head};val body=body(w::get,{headWidth}){source.body}
   TableStreams.body{source.copy(rowHeadColumns=headWidth,head=head,body=body)}
  };return result::get
 }
 fun normalizeTableBody(width:Long,input:TableBody):TableBody=normalizeTableBody({width},{input})()
}
