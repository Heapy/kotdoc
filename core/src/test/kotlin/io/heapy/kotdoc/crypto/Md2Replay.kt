package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
object Md2Replay {
 private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun array(a:ByteArray)=JsonArray(a.map{JsonPrimitive(it.toInt() and 255)})
 fun raw(r:JsonObject,trace:Boolean=false):JsonObject {
  val initial=bytes(r.getValue("context"));val data=ByteArray(128){if(it<16)165.toByte()else if(it>=112)90 else initial[it-16]};val ctx=CryptoMd2.Context(CryptoMd2.Algorithm.MD2,data,16)
  val snapshots=mutableListOf<JsonElement>();val outputs=mutableListOf<JsonElement>();val states=mutableListOf<JsonElement>()
  if(trace)CryptoMd2.observe={kind,i,values->states+=buildJsonObject{put("kind",kind);put("index",i);put("values",JsonArray(values.map(::JsonPrimitive)))}}
  try{
   if(r["init"]==JsonPrimitive(true))CryptoMd2.init(ctx)
   for(v in r["chunks"]?.jsonArray?:JsonArray(emptyList())){val input=bytes(v);val guarded=byteArrayOf(39,39,39)+input+byteArrayOf(73,73,73);CryptoMd2.update(ctx,CryptoByteSpan(guarded,3,input.size));check(guarded.contentEquals(byteArrayOf(39,39,39)+input+byteArrayOf(73,73,73)));snapshots.add(array(data.copyOfRange(16,112)))}
   when(r.getValue("op").jsonPrimitive.content){"chunk"->CryptoMd2.chunk(ctx,bytes(r.getValue("input")));"aliasUpdate"->CryptoMd2.update(ctx,CryptoByteSpan(data,16+r.getValue("alias").jsonPrimitive.int,16))}
   repeat(r["finalize"]?.jsonPrimitive?.int?:0){val out=ByteArray(30){51};val alias=r["outAlias"]?.jsonPrimitive?.int;val target=if(alias==null)CryptoByteSpan(out,7,16)else CryptoByteSpan(data,16+alias,16);CryptoMd2.finalize(ctx,target);outputs.add(array(target.data.copyOfRange(target.offset,target.offset+16)));check(out.take(7).all{it==51.toByte()}&&out.drop(23).all{it==51.toByte()})}
   check(data.take(16).all{it==165.toByte()}&&data.drop(112).all{it==90.toByte()})
   return buildJsonObject{put("context",array(data.copyOfRange(16,112)));put("snapshots",JsonArray(snapshots));put("outputs",JsonArray(outputs));put("guards",true);if(trace)put("trace",JsonArray(states))}
  }finally{CryptoMd2.observe=null}
 }
 fun counter(r:JsonObject):JsonElement {val initial=r.getValue("counter").jsonPrimitive.content.toULong().toLong();val n=r.getValue("length").jsonPrimitive.content.toUInt();val value=CryptoMd2.counterAfter(initial,n);return array(ByteArray(8){(value ushr(8*it)).toByte()})}
 fun haskell(r:JsonObject):JsonObject {
  val a=CryptoMd2Hash.Algorithm.MD2;val chunks=r.getValue("chunks").jsonArray.map(::bytes)
  if(r["mutable"]==JsonPrimitive(true)){val c=CryptoMd2Hash.hashMutableInit(a);chunks.forEach{CryptoMd2Hash.hashMutableUpdate(c,it)};val first=CryptoMd2Hash.hashMutableFinalize(c);val second=CryptoMd2Hash.hashMutableFinalize(c);CryptoMd2Hash.hashMutableReset(c);val reset=CryptoMd2Hash.hashMutableFinalize(c);return buildJsonObject{put("first",array(first.bytes()));put("second",array(second.bytes()));put("reset",array(reset.bytes()))}}
  val c=CryptoMd2Hash.hashUpdates(CryptoMd2Hash.hashInitWithDeferred(a,lazy{error("algorithm-bottom")}),chunks);val digest=CryptoMd2Hash.hashFinalize(c)
  if(r["public"]!=JsonPrimitive(true))return buildJsonObject{put("digest",array(digest.bytes()))}
  val input=r["read"]?.jsonPrimitive?.content?:"";val read=CryptoMd2Hash.readsDigest(a,input);val other=read?.first?:digest
  return buildJsonObject{put("digest",array(digest.bytes()));put("show",digest.toString());put("blockSize",a.blockSize);put("digestSize",a.digestSize);put("contextSize",c.debugBytes().size);put("context",array(c.debugBytes().copyOf(56)));put("reads",JsonArray(if(read==null)emptyList()else listOf(buildJsonObject{put("bytes",array(read.first.bytes()));put("rest",read.second)})));put("fromLengths",JsonArray(listOf(0,15,16,17).map{JsonPrimitive(CryptoMd2Hash.digestFromByteString(a,ByteArray(it))!=null)}));put("equal",digest==other);put("compare",when(digest.compareTo(other)){0->"EQ";in Int.MIN_VALUE..-1->"LT";else->"GT"});put("witnessDigest",array(CryptoMd2Hash.hashWithDeferred(a,lazy{error("algorithm-bottom")},chunks.fold(ByteArray(0)){x,y->x+y}).bytes()))}
 }
 fun demand(r:JsonObject):String {
  val a=CryptoMd2Hash.Algorithm.MD2;val bad=lazy<CryptoMd2Hash.Context>{error("context-bottom")};val bytes=lazy<ByteArray>{error("bytes-bottom")};val c=lazy{CryptoMd2Hash.hashInit(a)}
  fun tail(first:ByteArray)=object:Iterable<ByteArray>{override fun iterator()=object:Iterator<ByteArray>{var n=0;override fun hasNext()=n<2;override fun next():ByteArray=if(n++==0)first else error("tail-bottom")}}
  return try{when(r.getValue("case").jsonPrimitive.content){"ignored-witness"->CryptoMd2Hash.deferHashInit(a,lazy{error("algorithm-bottom")}).value;"ignored-finalize"->Unit;"finalize-root"->CryptoMd2Hash.deferHashFinalize(bad).value;"empty-update-root"->CryptoMd2Hash.deferHashUpdate(bad,lazyOf(ByteArray(0))).value;"update-bytes-first"->CryptoMd2Hash.deferHashUpdate(bad,bytes).value;"updates-empty-tail"->CryptoMd2Hash.deferHashUpdates(bad,lazyOf(tail(ByteArray(0)))).value;"updates-context-before-tail"->CryptoMd2Hash.deferHashUpdates(bad,lazyOf(tail(byteArrayOf(1)))).value;"updates-good-context-tail"->CryptoMd2Hash.deferHashUpdates(c,lazyOf(tail(byteArrayOf(1)))).value;else->error("case")};"42"}catch(t:Throwable){t.message?:t.javaClass.simpleName}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for((name,observe)in listOf<Pair<String,(JsonObject)->JsonElement>>("NativeObservations" to {raw(it)},"TraceObservations" to {raw(it,true)},"CounterObservations" to  ::counter,"HaskellObservations" to  ::haskell,"LiteralObservations" to  ::haskell)){for(v in Json.parseToJsonElement(File(args[0],"$name.json").readText()).jsonArray){val r=v.jsonObject;val expected=r.getValue("expected");val actual=observe(if(name=="CounterObservations")r else r.getValue("request").jsonObject);check(actual==expected){"$name/$n expected=$expected actual=$actual"};n++}};for(v in Json.parseToJsonElement(File(args[0],"DemandObservations.json").readText()).jsonArray){check(demand(v.jsonObject)==v.jsonObject.getValue("expected").jsonPrimitive.content);n++};println("PASS $n independent MD2 rows")}
}
