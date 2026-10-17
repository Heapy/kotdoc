package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.readLines
internal fun tableExecute(row:JsonObject):JsonElement=try {
 fun raw(name:String)=row.getValue(name).jsonPrimitive.content
 fun num(name:String)=row.getValue(name).jsonPrimitive.long
 fun cells()=PandocRead.Cell.readList(raw("cells")).single().first
 fun blocks()=Many.fromList(PandocRead.Block.readList(raw("blocks")).single().first)
 fun <A> prefix(input:GenericList<A>,limit:Int):Pair<List<A>,Boolean>{val values=mutableListOf<A>();var list=input;repeat(maxOf(0,limit)){when(val n=list.step()){GenericList.Step.Nil->return values to false;is GenericList.Step.Cons->{values+=n.head();list=n.tail()}}};return values to (list.step()is GenericList.Step.Cons)}
 when(raw("operation")){
 "place"->{val hang=row.getValue("hang").jsonArray.map{it.jsonPrimitive.long};val input=GenericList.view(cells());val stream=if(row.getValue("pad").jsonPrimitive.boolean)TableStreams.append({input},{TableStreams.repeat{TableStreams.emptyCell}})else input;val result=PandocTables.placeRowSection(hang,stream);val remaining=prefix(result.third,num("limit").toInt());buildJsonObject{put("overhang",result.first.joinToString(",","[","]"){AstShow.rowSpan(it)});put("cells",AstShow.show(result.second));put("remaining",AstShow.show(remaining.first));put("remainingTruncated",remaining.second)}}
 "clip"->JsonPrimitive(AstShow.show(PandocTables.clipRows(PandocRead.Row.readList(raw("rows")).single().first)))
 "head"->JsonPrimitive(AstShow.show(PandocTables.normalizeTableHead(num("width"),PandocRead.TableHead.read(raw("head")))))
 "body"->JsonPrimitive(AstShow.show(PandocTables.normalizeTableBody(num("width"),PandocRead.TableBody.read(raw("body")))))
 "foot"->JsonPrimitive(AstShow.show(PandocTables.normalizeTableFoot(num("width"),PandocRead.TableFoot.read(raw("foot")))))
 "cellWith"->JsonPrimitive(AstShow.show(TableBuilders.cellWith({PandocRead.Attr.read(raw("attr"))},{PandocRead.Alignment.read(raw("alignment"))},{RowSpan(num("rowSpan"))},{ColSpan(num("colSpan"))},::blocks)()))
 "cell"->JsonPrimitive(AstShow.show(TableBuilders.cell({PandocRead.Alignment.read(raw("alignment"))},{RowSpan(num("rowSpan"))},{ColSpan(num("colSpan"))},::blocks)()))
 "simpleCell"->JsonPrimitive(AstShow.show(TableBuilders.simpleCell(::blocks)()))
 "emptyCell"->JsonPrimitive(AstShow.show(TableBuilders.emptyCell))
 "tableWith","table"->{val caption={PandocRead.Caption.read(raw("caption"))};val specs={PandocRead.ColSpec.readList(raw("specs")).single().first};val head={PandocRead.TableHead.read(raw("head"))};val bodies={PandocRead.TableBody.readList(raw("bodies")).single().first};val foot={PandocRead.TableFoot.read(raw("foot"))};val result=if(raw("operation")=="tableWith")TableBuilders.tableWith({PandocRead.Attr.read(raw("attr"))},caption,specs,head,bodies,foot)else TableBuilders.table(caption,specs,head,bodies,foot);JsonPrimitive("Many {unMany = fromList "+AstShow.show(result.toSourceList())+"}")}
 "simpleTable"->{val blockList=ReadShape.Items(ReadShape.Root.BLOCK);val headers=ReadDictionary<List<List<Block>>>(ReadShape.Items(blockList)).read(raw("headers"));val rows=ReadDictionary<List<List<List<Block>>>>(ReadShape.Items(ReadShape.Items(blockList))).read(raw("simpleRows"));val result=TableBuilders.simpleTable({headers.map{Many.fromList(it)}},{rows.map{r->r.map{Many.fromList(it)}}});JsonPrimitive("Many {unMany = fromList "+AstShow.show(result.toSourceList())+"}")}
 else->error("operation")
 }
}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){val h=Path.of("tools/haskell-types-table-reference");val request=h.resolve("requests.jsonl").readLines();val expected=h.resolve("responses.jsonl").readLines();check(request.size==expected.size);request.zip(expected).forEachIndexed{i,(r,e)->val actual=tableExecute(Json.parseToJsonElement(r).jsonObject);check(actual==Json.parseToJsonElement(e)){"$i: $actual != $e"}};println("${request.size} original table responses PASS")}
