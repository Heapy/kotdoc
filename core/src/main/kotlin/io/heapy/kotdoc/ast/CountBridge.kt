// pandoc-types count-field constructor/pattern adapters, ©2006-2023 John MacFarlane.
// BSD-3-Clause; original Definition and licenses retained.
package io.heapy.kotdoc.ast
/** Explicit compatibility bridge: no replacement of the existing Long field API. */
object CountBridge {
 fun cell(attr:Attr,alignment:Alignment,rowSpan:RowSpan,colSpan:ColSpan,blocks:List<Block>):Cell = Cell(attr,alignment,rowSpan.value,colSpan.value,blocks)
 fun tableBody(attr:Attr,rowHeadColumns:RowHeadColumns,head:List<Row>,body:List<Row>):TableBody = TableBody(attr,rowHeadColumns.value,head,body)
 fun rowSpan(cell:Cell):RowSpan=RowSpan(cell.rowSpan)
 fun colSpan(cell:Cell):ColSpan=ColSpan(cell.colSpan)
 fun rowHeadColumns(body:TableBody):RowHeadColumns=RowHeadColumns(body.rowHeadColumns)
}
