package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
import java.math.BigInteger
private fun <C:PandocCount> execute(r:JsonObject,d:CountDictionary<C>):JsonElement {
 val x=r.getValue("x").jsonPrimitive.long;val y=r.getValue("y").jsonPrimitive.long;val z=r.getValue("z").jsonPrimitive.long
 val a=d.wrap(x);val b=d.wrap(y);val c=d.wrap(z)
 fun show(value:C):String=when(r.getValue("kind").jsonPrimitive.content){"RowHeadColumns"->AstShow.rowHeadColumns(value.value);"RowSpan"->AstShow.rowSpan(value.value);else->AstShow.colSpan(value.value)}
 fun range(values:GenericList<C>):JsonElement {
  val limit=r.getValue("limit").jsonPrimitive.int;var rest=values;val result=mutableListOf<JsonElement>()
  repeat(limit){when(val step=rest.step()){GenericList.Step.Nil->return buildJsonObject{put("values",JsonArray(result));put("truncated",false)};is GenericList.Step.Cons->{result+=JsonPrimitive(show(step.head()));rest=step.tail()}}}
  return buildJsonObject{put("values",JsonArray(result));put("truncated",rest.step() is GenericList.Step.Cons)}
 }
 val op=r.getValue("operation").jsonPrimitive.content
 return when(op){
  "pattern"->JsonPrimitive(d.match({a}){it().toString()})
  "fromEnum"->JsonPrimitive(d.fromEnum{a}.toString())
  "bridge"->{
   val kind=r.getValue("kind").jsonPrimitive.content
   if(kind=="RowHeadColumns"){val value=CountBridge.tableBody(Attr(),RowHeadColumns(x),emptyList(),emptyList());buildJsonObject{put("constructed",AstShow.show(value));put("extracted",CountBridge.rowHeadColumns(value).value.toString())}}
   else{val value=if(kind=="RowSpan")CountBridge.cell(Attr(),Alignment.AlignDefault,RowSpan(x),ColSpan(y),emptyList())else CountBridge.cell(Attr(),Alignment.AlignDefault,RowSpan(y),ColSpan(x),emptyList());buildJsonObject{put("constructed",AstShow.show(value));put("extracted",(if(kind=="RowSpan")CountBridge.rowSpan(value).value else CountBridge.colSpan(value).value).toString())}}
  }
  "enumFrom"->range(d.enumFrom{a})
  "enumFromThen"->range(d.enumFromThen({a},{b}))
  "enumFromTo"->range(d.enumFromTo({a},{c}))
  "enumFromThenTo"->range(d.enumFromThenTo({a},{b},{c}))
  else->JsonPrimitive(show(when(op){"constructor"->d.construct{x}();"plus"->d.plus({a},{b});"minus"->d.minus({a},{b});"times"->d.times({a},{b});"negate"->d.negate{a};"abs"->d.abs{a};"signum"->d.signum{a};"fromInteger"->d.fromInteger(BigInteger(r.getValue("integer").jsonPrimitive.content));"succ"->d.succ{a};"pred"->d.pred{a};"toEnum"->d.toEnum(x);else->error("operation $op")}))
 }
}
internal fun countExecute(r:JsonObject):JsonElement=try{when(r.getValue("kind").jsonPrimitive.content){"RowHeadColumns"->execute(r,PandocCounts.RowHeadColumns);"RowSpan"->execute(r,PandocCounts.RowSpan);"ColSpan"->execute(r,PandocCounts.ColSpan);else->error("kind")}}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){val root=File("tools/haskell-types-count-reference");val requests=File(root,"requests.jsonl").readLines();val expected=File(root,"responses.jsonl").readLines();check(requests.size==expected.size)
 for((r,e)in requests.zip(expected)){val request=Json.parseToJsonElement(r).jsonObject;val want=Json.parseToJsonElement(e);val actual=countExecute(request);check(actual==want){"$r\n$actual != $want"}}
 println("${requests.size} original count Num/Enum responses PASS")
}
