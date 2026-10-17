package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
object Ripemd160Replay {
 private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun array(a:ByteArray)=JsonArray(a.map{JsonPrimitive(it.toInt() and 255)})
 fun raw(r:JsonObject,trace:Boolean=false):JsonObject {
  val initial=bytes(r.getValue("context"));val data=ByteArray(160){if(it<16)165.toByte()else if(it>=144)90 else initial[it-16]};val ctx=CryptoRipemd160.Context(CryptoRipemd160.Algorithm.RIPEMD160,data,16)
  val snapshots=mutableListOf<JsonElement>();val outputs=mutableListOf<JsonElement>();val states=mutableListOf<JsonElement>()
  if(trace)CryptoRipemd160.observe={kind,i,values->states+=buildJsonObject{put("kind",kind);put("index",i);put("values",JsonArray(values.map(::JsonPrimitive)))}}
  try{
   if(r["init"]==JsonPrimitive(true))CryptoRipemd160.init(ctx)
   for(v in r["chunks"]?.jsonArray?:JsonArray(emptyList())){val input=bytes(v);val guarded=byteArrayOf(39,39,39)+input+byteArrayOf(73,73,73);CryptoRipemd160.update(ctx,CryptoByteSpan(guarded,3,input.size));check(guarded.contentEquals(byteArrayOf(39,39,39)+input+byteArrayOf(73,73,73)));snapshots.add(array(data.copyOfRange(16,144)))}
   when(r.getValue("op").jsonPrimitive.content){"chunk"->CryptoRipemd160.chunk(ctx,bytes(r.getValue("input")));"aliasUpdate"->CryptoRipemd160.update(ctx,CryptoByteSpan(data,16+r.getValue("alias").jsonPrimitive.int,64))}
   repeat(r["finalize"]?.jsonPrimitive?.int?:0){val out=ByteArray(34){51};val alias=r["outAlias"]?.jsonPrimitive?.int;val target=if(alias==null)CryptoByteSpan(out,7,20)else CryptoByteSpan(data,16+alias,20);CryptoRipemd160.finalize(ctx,target);outputs.add(array(target.data.copyOfRange(target.offset,target.offset+20)));check(out.take(7).all{it==51.toByte()}&&out.drop(27).all{it==51.toByte()})}
   check(data.take(16).all{it==165.toByte()}&&data.drop(144).all{it==90.toByte()})
   return buildJsonObject{put("context",array(data.copyOfRange(16,144)));put("snapshots",JsonArray(snapshots));put("outputs",JsonArray(outputs));put("guards",true);if(trace)put("trace",JsonArray(states))}
  }finally{CryptoRipemd160.observe=null}
 }
 fun counter(r:JsonObject):JsonElement {val initial=r.getValue("counter").jsonPrimitive.content.toULong().toLong();val n=r.getValue("length").jsonPrimitive.content.toUInt();val value=CryptoRipemd160.counterAfter(initial,n);return array(ByteArray(8){(value ushr(8*it)).toByte()})}
 fun haskell(r:JsonObject):JsonObject {
  val a=CryptoRipemd160Hash.Algorithm.RIPEMD160;val chunks=r.getValue("chunks").jsonArray.map(::bytes)
  if(r["mutable"]==JsonPrimitive(true)){val c=CryptoRipemd160Hash.hashMutableInit(a);chunks.forEach{CryptoRipemd160Hash.hashMutableUpdate(c,it)};val first=CryptoRipemd160Hash.hashMutableFinalize(c);val second=CryptoRipemd160Hash.hashMutableFinalize(c);CryptoRipemd160Hash.hashMutableReset(c);val reset=CryptoRipemd160Hash.hashMutableFinalize(c);return buildJsonObject{put("first",array(first.bytes()));put("second",array(second.bytes()));put("reset",array(reset.bytes()))}}
  val c=CryptoRipemd160Hash.hashUpdates(CryptoRipemd160Hash.hashInitWithDeferred(a,lazy{error("algorithm-bottom")}),chunks);val digest=CryptoRipemd160Hash.hashFinalize(c)
  if(r["public"]!=JsonPrimitive(true))return buildJsonObject{put("digest",array(digest.bytes()))}
  val input=r["read"]?.jsonPrimitive?.content?:"";val read=CryptoRipemd160Hash.readsDigest(a,input);val other=read?.first?:digest
  return buildJsonObject{put("digest",array(digest.bytes()));put("show",digest.toString());put("blockSize",a.blockSize);put("digestSize",a.digestSize);put("contextSize",c.debugBytes().size);put("context",array(c.debugBytes().copyOf(96)));put("reads",JsonArray(if(read==null)emptyList()else listOf(buildJsonObject{put("bytes",array(read.first.bytes()));put("rest",read.second)})));put("fromLengths",JsonArray(listOf(0,19,20,21).map{JsonPrimitive(CryptoRipemd160Hash.digestFromByteString(a,ByteArray(it))!=null)}));put("equal",digest==other);put("compare",when(digest.compareTo(other)){0->"EQ";in Int.MIN_VALUE..-1->"LT";else->"GT"});put("witnessDigest",array(CryptoRipemd160Hash.hashWithDeferred(a,lazy{error("algorithm-bottom")},chunks.fold(ByteArray(0)){x,y->x+y}).bytes()))}
 }
 fun demand(r:JsonObject):String {
  val a=CryptoRipemd160Hash.Algorithm.RIPEMD160;val bad=lazy<CryptoRipemd160Hash.Context>{error("context-bottom")};val bytes=lazy<ByteArray>{error("bytes-bottom")};val c=lazy{CryptoRipemd160Hash.hashInit(a)}
  fun tail(first:ByteArray)=object:Iterable<ByteArray>{override fun iterator()=object:Iterator<ByteArray>{var n=0;override fun hasNext()=n<2;override fun next():ByteArray=if(n++==0)first else error("tail-bottom")}}
  return try{when(r.getValue("case").jsonPrimitive.content){"ignored-witness"->CryptoRipemd160Hash.deferHashInit(a,lazy{error("algorithm-bottom")}).value;"ignored-finalize"->Unit;"finalize-root"->CryptoRipemd160Hash.deferHashFinalize(bad).value;"empty-update-root"->CryptoRipemd160Hash.deferHashUpdate(bad,lazyOf(ByteArray(0))).value;"update-bytes-first"->CryptoRipemd160Hash.deferHashUpdate(bad,bytes).value;"updates-empty-tail"->CryptoRipemd160Hash.deferHashUpdates(bad,lazyOf(tail(ByteArray(0)))).value;"updates-context-before-tail"->CryptoRipemd160Hash.deferHashUpdates(bad,lazyOf(tail(byteArrayOf(1)))).value;"updates-good-context-tail"->CryptoRipemd160Hash.deferHashUpdates(c,lazyOf(tail(byteArrayOf(1)))).value;else->error("case")};"42"}catch(t:Throwable){t.message?:t.javaClass.simpleName}
 }
 @JvmStatic fun main(args:Array<String>){var n=0;for((name,observe)in listOf<Pair<String,(JsonObject)->JsonElement>>("NativeObservations" to {raw(it)},"TraceObservations" to {raw(it,true)},"CounterObservations" to  ::counter,"HaskellObservations" to  ::haskell,"LiteralObservations" to  ::haskell)){for(v in Json.parseToJsonElement(File(args[0],"$name.json").readText()).jsonArray){val r=v.jsonObject;val expected=r.getValue("expected");val actual=observe(if(name=="CounterObservations")r else r.getValue("request").jsonObject);check(actual==expected){"$name/$n expected=$expected actual=$actual"};n++}};for(v in Json.parseToJsonElement(File(args[0],"DemandObservations.json").readText()).jsonArray){check(demand(v.jsonObject)==v.jsonObject.getValue("expected").jsonPrimitive.content);n++};println("PASS $n independent RIPEMD160 rows")}
}
