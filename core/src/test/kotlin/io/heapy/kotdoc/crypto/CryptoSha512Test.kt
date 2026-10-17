package io.heapy.kotdoc.crypto
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
internal object Sha512Replay {
    private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
    private fun json(v:ByteArray)=JsonArray(v.map{JsonPrimitive(it.toInt() and 255)})
    fun observe(r:JsonObject):JsonObject {
        fun number(o:JsonObject,k:String,default:Int=0)=o[k]?.jsonPrimitive?.long?.toInt()?:default
        fun hex(v:Long)=v.toULong().toString(16).padStart(16,'0')
        if("helper" in r) {
            val a=r.getValue("a").jsonPrimitive.content.toULong(16).toLong();val b=r.getValue("b").jsonPrimitive.content.toULong(16).toLong()
            val value=when(number(r,"helper")){0->CryptoSha512.msb(a.toInt()).toLong() and 0xffffffffL;1->CryptoSha512.lt(a.toInt(),b.toInt()).toLong() and 0xffffffffL;2->CryptoSha512.ge(a.toInt(),b.toInt()).toLong() and 0xffffffffL;3->CryptoSha512.isZero(a.toInt()).toLong() and 0xffffffffL;4->CryptoSha512.eq(a.toInt(),b.toInt()).toLong() and 0xffffffffL;5->CryptoSha512.msb64(a);else->CryptoSha512.lt64(a,b)}
            return buildJsonObject{put("value",hex(value))}
        }
        if(r["digest"]?.jsonPrimitive?.boolean==true) {
            val alg=when(r.getValue("algorithm").jsonPrimitive.content){"SHA384"->CryptoSha512Hash.Algorithm.SHA384;"SHA512"->CryptoSha512Hash.Algorithm.SHA512;"SHA512/224"->CryptoSha512Hash.Algorithm.SHA512t_224;else->CryptoSha512Hash.Algorithm.SHA512t_256}
            var ctx=if(r["witnessBottom"]?.jsonPrimitive?.boolean==true)CryptoSha512Hash.hashInitWithDeferred(alg,lazy{error("algorithm-bottom")})else CryptoSha512Hash.hashInit(alg);for(chunk in r.getValue("chunks").jsonArray)ctx=CryptoSha512Hash.hashUpdate(ctx,bytes(chunk))
            val digest=if("prefix" in r)CryptoSha512Hash.hashFinalizePrefix(ctx,bytes(r.getValue("end")),number(r,"prefix"))else CryptoSha512Hash.hashFinalize(ctx)
            return buildJsonObject {
                put("digest",json(digest.bytes()))
                if(r["public"]?.jsonPrimitive?.boolean==true) {
                    put("witnessDigest",json(CryptoSha512Hash.hashWithDeferred(alg,lazy{error("algorithm-bottom")},r.getValue("chunks").jsonArray.fold(byteArrayOf()){a,v->a+bytes(v)}).bytes()));put("show",digest.toString());put("blockSize",alg.blockSize);put("digestSize",alg.digestSize);put("contextSize",alg.contextSize);put("context",json(ctx.debugBytes().copyOf(208)))
                    val parsed=CryptoSha512Hash.readsDigest(alg,r.getValue("read").jsonPrimitive.content)
                    put("reads",JsonArray(if(parsed==null)emptyList()else listOf(buildJsonObject{put("bytes",json(parsed.first.bytes()));put("rest",parsed.second)})))
                    put("fromLengths",JsonArray(listOf(0,alg.digestSize-1,alg.digestSize,alg.digestSize+1).map{JsonPrimitive(CryptoSha512Hash.digestFromByteString(alg,ByteArray(it))!=null)}));val other=parsed?.first?:digest;put("equal",digest==other);put("compare",when{digest<other->"LT";digest>other->"GT";else->"EQ"})
                }
            }
        }
        val ctx=CryptoSha512.Context(ByteArray(320){if(it<32)17 else if(it>=288)34 else 81},32)
        val contexts=mutableListOf<JsonElement>();val buffers=mutableListOf<JsonElement>();val outputs=mutableListOf<JsonElement>();val trace=mutableListOf<JsonElement>();val tracing=r["trace"]?.jsonPrimitive?.boolean?:false
        CryptoSha512.observe=if(tracing){label,index,state->trace+=JsonPrimitive(hex(if(label=="schedule")0 else index+1L));trace+=JsonPrimitive(hex(state.size.toLong()));state.forEach{trace+=JsonPrimitive(hex(it))}}else null
        try {for(value in r.getValue("steps").jsonArray) {
            val s=value.jsonObject;s["context"]?.let{bytes(it).copyInto(ctx.data,32)}
            val data=s["bytes"]?.let(::bytes)?:byteArrayOf();val op=s.getValue("op").jsonPrimitive.content;val alignment=number(s,"alignment");val length=number(s,"length",data.size);val size=maxOf(data.size,length,if(op=="chunk"||op=="chunkGeneric")128 else 0,64)
            val buffer=ByteArray(64+alignment+size){if(it<32+alignment)17 else if(it>=32+alignment+size)34 else 165.toByte()};data.copyInto(buffer,32+alignment)
            val alias=s["alias"]?.jsonPrimitive?.boolean?:false;val out=if(alias)buffer else ByteArray(128){if(it<32)17 else if(it>=96)34 else 165.toByte()};val output=CryptoByteSpan(out,if(alias)32+alignment else 32,64);val input=CryptoByteSpan(buffer,32+alignment,length);val arg=number(s,"arg")
            when(op){"init"->CryptoSha512.init(ctx);"init384"->CryptoSha512.init384(ctx);"initT"->CryptoSha512.initT(ctx,arg);"update"->CryptoSha512.update(ctx,input);"update384"->CryptoSha512.update384(ctx,input);"updateT"->CryptoSha512.updateT(ctx,input);"finalize"->CryptoSha512.finalize(ctx,output);"finalize384"->CryptoSha512.finalize384(ctx,output);"finalizeT"->CryptoSha512.finalizeT(ctx,arg,output);"finalizePrefix"->CryptoSha512.finalizePrefix(ctx,input,arg,output);"finalizePrefix384"->CryptoSha512.finalizePrefix384(ctx,input,arg,output);"chunk","chunkGeneric"->CryptoSha512.chunk(ctx,buffer,32+alignment);"incrementSize"->CryptoSha512.incrementSize(ctx,arg).copyInto(out,output.offset);"selectDigest"->CryptoSha512.selectDigest(ctx,output,arg);"getIndex"->{val index=CryptoSha512.getIndex(ctx);for(i in 0..<4)out[output.offset+i]=(index ushr(8*i)).toByte()};else->error(op)}
            contexts.add(json(ctx.data));buffers.add(json(buffer));outputs.add(json(out))
        }}finally{CryptoSha512.observe=null}
        return buildJsonObject{put("contexts",JsonArray(contexts));put("buffers",JsonArray(buffers));put("outputs",JsonArray(outputs));if(tracing)put("trace",JsonArray(trace))}
    }
    @JvmStatic fun main(args:Array<String>) {var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{line->val row=Json.parseToJsonElement(line).jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$count request=${row.getValue("request")} expected=${row.getValue("expected")} actual=$actual"};count++;if(count%1000==0)println("PASS$count")}};println("PASS$count SHA512 original observations")}
}
class CryptoSha512Test {
    private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-sha512/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
    @org.junit.jupiter.api.TestFactory fun originalObservations()=lines.drop(1).mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("original/$index"){val row=Json.parseToJsonElement(line).jsonObject;org.junit.jupiter.api.Assertions.assertEquals(row.getValue("expected"),Sha512Replay.observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
    @org.junit.jupiter.api.TestFactory fun originalPropertyFamilies()=lines.drop(1).filter{"sampleFamily" in it}.mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("HashSpec/$index") {
        val r=Json.parseToJsonElement(line).jsonObject.getValue("request").jsonObject
        val alg=when(r.getValue("algorithm").jsonPrimitive.content){"SHA384"->CryptoSha512Hash.Algorithm.SHA384;"SHA512"->CryptoSha512Hash.Algorithm.SHA512;"SHA512/224"->CryptoSha512Hash.Algorithm.SHA512t_224;else->CryptoSha512Hash.Algorithm.SHA512t_256}
        fun b(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
        val chunks=r.getValue("chunks").jsonArray.map(::b);val all=chunks.fold(byteArrayOf()){a,v->a+v}
        if(r.getValue("sampleFamily").jsonPrimitive.content=="chunk")org.junit.jupiter.api.Assertions.assertEquals(CryptoSha512Hash.hash(alg,all),CryptoSha512Hash.hashLazy(alg,chunks))
        else {val end=b(r.getValue("end"));val n=r.getValue("prefix").jsonPrimitive.int;org.junit.jupiter.api.Assertions.assertEquals(CryptoSha512Hash.hash(alg,all+end.take(n).toByteArray()),CryptoSha512Hash.hashFinalizePrefix(CryptoSha512Hash.hashUpdates(CryptoSha512Hash.hashInit(alg),chunks),end,n))}
    }}.stream()
    @org.junit.jupiter.api.Test fun immutableOwnershipAndCapabilities() {
        for(alg in CryptoSha512Hash.Algorithm.entries) {
            val ctx=CryptoSha512Hash.hashInit(alg);val before=ctx.debugBytes();org.junit.jupiter.api.Assertions.assertSame(ctx,CryptoSha512Hash.hashUpdates(ctx,listOf(byteArrayOf(),byteArrayOf())))
            val input=ByteArray(257){it.toByte()};val updated=CryptoSha512Hash.hashUpdate(ctx,input);val first=CryptoSha512Hash.hashFinalize(updated);input.fill(0);org.junit.jupiter.api.Assertions.assertEquals(first,CryptoSha512Hash.hashFinalize(updated));org.junit.jupiter.api.Assertions.assertArrayEquals(before,ctx.debugBytes())
            val bytes=first.bytes();bytes.fill(0);org.junit.jupiter.api.Assertions.assertNotEquals(bytes.toHexString(),first.toString());val copy=CryptoSha512Hash.digestFromByteString(alg,first.bytes())!!;org.junit.jupiter.api.Assertions.assertEquals(first,copy);org.junit.jupiter.api.Assertions.assertEquals(0,first.compareTo(copy));first.force();ctx.force()
            if(!alg.prefix)org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java){CryptoSha512Hash.hashPrefix(alg,byteArrayOf(),0)}
        }
    }
    @org.junit.jupiter.api.TestFactory fun sourcePins()=Json.parseToJsonElement(lines.first()).jsonObject.getValue("sources").jsonObject.map{(path,expected)->org.junit.jupiter.api.DynamicTest.dynamicTest(path){org.junit.jupiter.api.Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
