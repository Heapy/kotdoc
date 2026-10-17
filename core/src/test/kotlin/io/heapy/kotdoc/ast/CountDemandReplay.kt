package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private fun <C:PandocCount> demand(kind:String,name:String,d:CountDictionary<C>):String {
 fun value(n:Long)=d.toEnum(n)
 fun show(v:C)=when(kind){"RowHeadColumns"->AstShow.rowHeadColumns(v.value);"RowSpan"->AstShow.rowSpan(v.value);else->AstShow.colSpan(v.value)}
 fun take(n:Int,list:GenericList<C>,ignore:Boolean=false):String{var rest=list;val out=mutableListOf<String>();repeat(n){when(val step=rest.step()){GenericList.Step.Nil->return out.joinToString(",","[","]");is GenericList.Step.Cons->{out+=if(ignore)"7"else show(step.head());rest=step.tail()}}};return out.joinToString(",","[","]")}
 return when(name){
  "ignored-root"->d.match({error("root")}){"matched"}
  "ignored-field"->d.match(d.construct{error("field")}){"matched"}
  "forced-pattern"->d.match({error("root")}){it().toString()}
  "num-left"->show(d.plus({error("left")},{value(1)}))
  "num-right"->show(d.plus({value(1)},{error("right")}))
  "num-constructed-field"->show(d.plus(d.construct{error("field")},{value(1)}))
  "enum-take-zero"->take(0,d.enumFrom{error("root")})
  "enum-null"->if(d.enumFrom{error("root")}.step() is GenericList.Step.Nil)"True"else"False"
  "enum-ignore-head"->take(1,d.enumFrom{error("root")},true)
  "enum-head"->show((d.enumFrom{error("root")}.step() as GenericList.Step.Cons).head())
  "then-second-root"->take(1,d.enumFromThen({value(1)},{error("second")}))
  "to-end-root"->take(1,d.enumFromTo({value(1)},{error("end")}))
  "then-to-end-root"->take(1,d.enumFromThenTo({value(1)},{value(2)},{error("end")}))
  "then-to-short"->take(1,d.enumFromThenTo({value(1)},{value(3)},{value(2)}))
  "zero-step-prefix"->take(4,d.enumFromThenTo({value(2)},{value(2)},{value(5)}))
  "zero-step-empty"->take(4,d.enumFromThenTo({value(2)},{value(2)},{value(1)}))
  else->error("demand $name")
 }
}
internal fun countDemandExecute(key:String):JsonElement=try{val(kind,name)=key.split('/');JsonPrimitive(when(kind){"RowHeadColumns"->demand(kind,name,PandocCounts.RowHeadColumns);"RowSpan"->demand(kind,name,PandocCounts.RowSpan);"ColSpan"->demand(kind,name,PandocCounts.ColSpan);else->error("kind")})}catch(e:RuntimeException){buildJsonObject{put("exception",e.message.orEmpty())}}
fun main(){val rows=File("tools/haskell-types-count-reference/demand-native.jsonl").readLines().map(Json::parseToJsonElement).chunked(2)
 for(pair in rows){val key=pair[0].jsonPrimitive.content;val actual=countDemandExecute(key);check(actual==pair[1]){"$key: $actual != ${pair[1]}"}}
 println("${rows.size} original count constructor/Num/Enum demand responses PASS")
}
