// Port of Text.Pandoc.Builder table/cell constructors.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

object TableBuilders {
 val emptyCell:Cell=TableStreams.emptyCell
 fun cellWith(attr:()->Attr,alignment:()->Alignment,rowSpan:()->RowSpan,colSpan:()->ColSpan,blocks:()->Many<Block>):()->Cell {
  val result=GenericMemo{val h=rowSpan();val w=colSpan();val at=attr();val align=alignment();val values=blocks().toSourceList();val cell=CountBridge.cell(at,align,h,w,values);TableStreams.cell{cell}}
  return result::get
 }
 fun cell(alignment:()->Alignment,rowSpan:()->RowSpan,colSpan:()->ColSpan,blocks:()->Many<Block>):()->Cell=cellWith({Attr()},alignment,rowSpan,colSpan,blocks)
 fun simpleCell(blocks:()->Many<Block>):()->Cell=cell({Alignment.AlignDefault},{RowSpan(1)},{ColSpan(1)},blocks)
 fun tableWith(attr:()->Attr,caption:()->Caption,specs:()->List<ColSpec>,head:()->TableHead,bodies:()->List<TableBody>,foot:()->TableFoot):Many<Block>{
  val attributes=GenericMemo(attr);val capt=GenericMemo(caption);val columns=GenericMemo{GenericList.view(specs())};val width=GenericMemo{TableStreams.length(columns.get())}
  val normalizedHead=PandocTables.normalizeTableHead(width::get,head)
  val normalizedBodies=TableStreams.mapped(bodies){PandocTables.normalizeTableBody(width::get,it)()}
  val normalizedFoot=PandocTables.normalizeTableFoot(width::get,foot)
  return Many.singleton{
   val at=attributes.get();val ca=TableStreams.caption(capt::get);val cs=columns.get().also{it.step()}
   val th=normalizedHead();normalizedBodies.step();val tf=normalizedFoot()
   Block.Table(at,ca,cs,th,normalizedBodies,tf)
  }
 }
 fun table(caption:()->Caption,specs:()->List<ColSpec>,head:()->TableHead,bodies:()->List<TableBody>,foot:()->TableFoot):Many<Block> = tableWith({Attr()},caption,specs,head,bodies,foot)
 fun simpleTable(headers:()->List<Many<Block>>,rows:()->List<List<Many<Block>>>):Many<Block>{
  val hs=GenericMemo{GenericList.view(headers())};val rs=GenericMemo{GenericList.view(rows())}
  val width=GenericMemo{var result=TableStreams.length(hs.get());var rows=rs.get();while(true)when(val n=rows.step()){GenericList.Step.Nil->return@GenericMemo result;is GenericList.Step.Cons->{result=maxOf(result,TableStreams.length(n.head()));rows=n.tail()}};@Suppress("UNREACHABLE_CODE")0L}
  fun row(cells:()->List<Many<Block>>):Row {val mapped=TableStreams.mapped(cells){simpleCell(it)()};return TableStreams.row{Row(Attr(),mapped)}}
  val headRows=GenericList.defer{when(hs.get().step()){GenericList.Step.Nil->GenericList.empty();is GenericList.Step.Cons->GenericList.cons({row(hs::get)},{GenericList.empty()})}}
  val bodyRows=TableStreams.mapped(rs::get){row(it)}
  return table({Caption(null,emptyList())},{TableStreams.replicate(width.get()){ColSpec(Alignment.AlignDefault,ColWidth.Default)}},{TableHead(Attr(),headRows)},{listOf(TableBody(Attr(),0,emptyList(),bodyRows))},{TableFoot(Attr(),emptyList())})
 }
}
