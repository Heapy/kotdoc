// Text.GridTable.{Trace,Parse,ArrayTable}, gridtables 0.1.1.0.
// Copyright © 2022-2023 Posit Software, PBC; MIT (retained original license).
package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.Alignment
import io.heapy.kotdoc.layout.DisplayWidth
import io.heapy.kotdoc.parsing.*
import java.util.TreeSet

/** Original gridtables tracing algorithm; parsing cell blocks remains a reader callback. */
internal object RstGridTable {
 data class Cell(val lines:List<String>,val rowSpan:Int=1,val colSpan:Int=1)
 sealed interface RawCell{data class Content(val cell:Cell):RawCell;data class Continuation(val row:Int,val column:Int):RawCell}
 data class Table(val rows:List<List<Cell>>,val head:Int?,val foot:Int?,val specs:List<Pair<Alignment,Int>>,val rawCells:List<List<RawCell>> = emptyList())
 fun <U> tableLine():Parsec<U,String>{
  val first=Parsec.char<U>(43) or Parsec.char<U>(124)
  val rest=Parsec.satisfy<U>{it!=10&&it!=13}.many().before(Parsec.char<U>(10).label("lf new-line"))
  return first.flatMap{c->rest.map{xs->buildString{appendCodePoint(c);xs.forEach(::appendCodePoint)}.trimEnd{ParserCharacters.isSpace(it.code)}}}.attempt()
 }
 fun <U> parser():Parsec<U,Table>{
  fun char(c:Char)=Parsec.char<U>(c.code)
  val colon=char(':').map{":"}.option("");val gridPart=colon.flatMap{left->char('-').many1().flatMap{ds->colon.before(char('+')).map{right->left+"-".repeat(ds.size)+right+"+"}}}
  val first=char('+').then(gridPart.many1()).map{"+"+it.joinToString("")}.before(Parsec.satisfy<U>{it==32||it==9}.skipMany()).before(char('\n').label("lf new-line"))
  return first.flatMap{top->tableLine<U>().many1().flatMap{lines->trace(listOf(top)+lines)?.let{Parsec.pure(it)}?:Parsec.fail("tracing failed")}}.attempt()
 }
 fun rowView(grid:Array<Array<Cell?>>,continuations:Set<Pair<Int,Int>>)=grid.mapIndexed{r,line->line.mapIndexedNotNull{c,cell->if((r+1 to c+1)in continuations)null else cell?:Cell(emptyList())}}
 private data class Point(val row:Int,val col:Int):Comparable<Point>{override fun compareTo(other:Point)=compareValuesBy(this,other,Point::row,Point::col)}
 private data class GChar(val point:Int?=null,val zero:List<Int> = emptyList(),val padding:Boolean=false)
 private data class Spec(val start:Int,val end:Int,val alignment:Alignment)
 private data class Part(val row:Int,val specs:List<Spec>)
 private data class Scan(val end:Point,val rows:Set<Int>,val cols:Set<Int>)
 private data class Traced(val start:Point,val end:Point,val lines:List<String>)
 private class Grid(lines:List<String>){
  val height=lines.size;val width=lines.maxOfOrNull{DisplayWidth.realLength(it).toInt()}?:0
  private val chars=lines.flatMap{line->val points=line.codePoints().toArray();val out=mutableListOf<GChar>();var at=0;var terminalZero=false
   while(at<points.size){val cp=points[at++];when(DisplayWidth.charWidth(cp).toInt()){
    2->{out+=GChar(cp);out+=GChar(padding=true)}
    1->out+=GChar(cp)
    else->{val zs=mutableListOf(cp);while(at<points.size&&DisplayWidth.charWidth(points[at])==0L)zs+=points[at++];val next=if(at<points.size)points[at++]else{terminalZero=true;0};out+=GChar(next,zs);if(DisplayWidth.charWidth(next)==2L)out+=GChar(padding=true)}
   }};List(if(terminalZero)minOf(width,out.size)else width){out.getOrNull(it)?:GChar()}}.toMutableList()
  fun inside(r:Int,c:Int)=r in 1..height&&c in 1..width
  fun get(r:Int,c:Int):GChar{check(inside(r,c)){"array index out of range"};return chars[(r-1)*width+c-1]}
  fun isChar(r:Int,c:Int,ch:Char)=get(r,c)==GChar(ch.code)
  fun normalize(rows:List<Int>){for(r in rows)for(c in 1..width)if(isChar(r,c,'=')||isChar(r,c,':'))chars[(r-1)*width+c-1]=GChar('-'.code)}
 }
 private fun specs(grid:Grid,r:Int,ch:Char):List<Spec>?{
  if(!grid.isChar(r,1,'+'))return null
  val out=mutableListOf<Spec>();var start=2
  while(start<grid.width){var end=start+1;var right=false
   while(true){when{
    grid.isChar(r,end,'+')->break
    grid.isChar(r,end,':')->{if(!grid.isChar(r,end+1,'+'))return null;end++;right=true;break}
    grid.isChar(r,end,ch)->end++
    else->return null
   }}
   val left=grid.isChar(r,start,':');val align=when{left&&right->Alignment.AlignCenter;left->Alignment.AlignLeft;right->Alignment.AlignRight;else->Alignment.AlignDefault};out+=Spec(start,end,align);start=end+1
  };return out
 }
 private fun scanUp(g:Grid,start:Point,end:Point):Set<Int>?{
  val rows=mutableSetOf<Int>();for(r in start.row+1 until end.row)when{g.isChar(r,start.col,'+')->rows+=r;g.isChar(r,start.col,'|')->Unit;else->return null};return rows
 }
 private fun scanLeft(g:Grid,start:Point,end:Point):Pair<Set<Int>,Set<Int>>?{
  if(!g.isChar(end.row,start.col,'+'))return null
  val cols=mutableSetOf<Int>();for(c in start.col+1 until end.col)when{g.isChar(end.row,c,'+')->cols+=c;g.isChar(end.row,c,'-')->Unit;else->return null};return(scanUp(g,start,end)?:return null)to cols
 }
 private fun scanDown(g:Grid,start:Point,right:Int):Scan?{
  val rows=mutableSetOf<Int>();var r=start.row+1
  while(g.inside(r,right)){when{
   g.isChar(r,right,'+')->{rows+=r;val rest=scanLeft(g,start,Point(r,right));if(rest!=null)return Scan(Point(r,right),rows+rest.first,rest.second)}
   g.isChar(r,right,'|')->Unit
   right==g.width->Unit
   else->return null
  };r++};return null
 }
 private fun scanRest(g:Grid,start:Point):Scan?{
  for(r in start.row+1..g.height){if(!g.isChar(r,start.col,'+'))continue;var valid=true
   for(c in start.col+1..g.width)if(!g.isChar(r,c,'+')&&!g.isChar(r,c,'-')&&g.get(r,c)!=GChar()){valid=false;break}
   if(valid)return Scan(Point(r,g.width),setOf(r),setOf(g.width))
  };return null
 }
 private fun scanRight(g:Grid,start:Point):Scan?{
  val cols=mutableSetOf<Int>();val fallbacks=mutableListOf<Int>();var c=start.col+1
  while(g.inside(start.row,c)){when{
   g.isChar(start.row,c,'-')->c++
   g.isChar(start.row,c,'+')->{cols+=c;val down=scanDown(g,start,c);if(down!=null)return down.copy(cols=cols+down.cols);fallbacks+=c+1;c++}
   else->break
  }}
  // The source tries farther borders before the forgiving last-cell fallback.
  for(right in fallbacks.asReversed())if(g.inside(start.row,right)&&g.get(start.row,right)==GChar())return scanRest(g,start)
  return null
 }
 private fun contents(g:Grid,start:Point,end:Point)= (start.row+1 until end.row).map{r->buildString{for(c in start.col+1 until end.col){val ch=g.get(r,c);if(ch.point!=null){ch.zero.forEach(::appendCodePoint);appendCodePoint(ch.point)}}}}
 fun trace(lines:List<String>):Table?{
  val g=Grid(lines);if(g.height==0||g.width==0)return null
  val first=specs(g,1,'-');val parts=(1..g.height).mapNotNull{r->specs(g,r,'=')?.let{Part(r,it)}};g.normalize(listOf(1)+parts.map{it.row})
  val rows=TreeSet<Int>();rows+=1;val cols=TreeSet<Int>();cols+=1;val corners=TreeSet<Point>();corners+=Point(1,1);val cells=sortedMapOf<Point,Traced>()
  while(corners.isNotEmpty()){val start=corners.pollFirst();val scan=scanRight(g,start)?:continue;rows+=scan.rows;cols+=scan.cols;corners+=Point(start.row,scan.end.col);corners+=Point(scan.end.row,start.col);cells[start]=Traced(start,scan.end,contents(g,start,scan.end))}
  if(cells.isEmpty())return null
  val rs=rows.toList();val cs=cols.toList();val ri=rs.withIndex().associate{it.value to it.index+1};val ci=cs.withIndex().associate{it.value to it.index+1};val grid=Array(rs.size-1){arrayOfNulls<Cell>(cs.size-1)};val continuations=mutableMapOf<Point,Point>()
  for(cell in cells.values){val row=ri.getValue(cell.start.row);val col=ci.getValue(cell.start.col);val rowSpan=ri.getValue(cell.end.row)-row;val colSpan=ci.getValue(cell.end.col)-col;grid[row-1][col-1]=Cell(cell.lines,rowSpan,colSpan);continuations.remove(Point(row,col));for(r in row until row+rowSpan)for(c in col until col+colSpan)if(r!=row||c!=col){grid[r-1][c-1]=null;continuations[Point(r,c)]=Point(row,col)}}
  val result=rowView(grid,continuations.keys.map{it.row to it.col}.toSet())
  val alignments=(parts.firstOrNull()?.specs?:first?:emptyList()).map{it.alignment};val specs=cs.zipWithNext().mapIndexed{i,(a,b)->(alignments.getOrNull(i)?:Alignment.AlignDefault)to(b-a-1)}
  val head=parts.firstOrNull()?.let{ri[it.row]?.minus(1)};val foot=if(parts.size>=2&&parts.last().row==rs.last())ri[parts[parts.lastIndex-1].row]else null
  val raw=grid.mapIndexed{r,line->line.mapIndexed{c,cell->val anchor=continuations[Point(r+1,c+1)];if(anchor!=null)RawCell.Continuation(anchor.row,anchor.col)else RawCell.Content(cell?:Cell(emptyList()))}}
  return Table(result,head,foot,specs,raw)
 }
}
