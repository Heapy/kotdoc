package io.heapy.kotdoc.crypto
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
internal object ChaChaCoreReplay {
    private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
    private fun json(v:ByteArray)=JsonArray(v.map{JsonPrimitive(it.toInt() and 255)})
    fun haskellBytes(r:JsonObject,override:String?=null,single:Boolean=false):ByteArray {
        val ctx=CryptoChaChaCore.Context();val key=bytes(r.getValue("key"));val iv=bytes(r.getValue("iv"));val rounds=r.getValue("rounds").jsonPrimitive.int
        if(r["xchacha"]?.jsonPrimitive?.boolean==true)CryptoChaChaCore.initX(ctx,rounds,CryptoByteSpan(key),CryptoByteSpan(iv))else CryptoChaChaCore.init(ctx,rounds,key.size,CryptoByteSpan(key),iv.size,CryptoByteSpan(iv))
        CryptoChaChaCore.setCounter32(ctx.state,r["counter"]?.jsonPrimitive?.long?.toInt()?:0)
        val plan=r.getValue("sizes").jsonArray.map{it.jsonPrimitive.int};val sizes=if(single)listOf(plan.sum())else plan
        val op=override?:r.getValue("operation").jsonPrimitive.content;val input=if(override=="combine")ByteArray(plan.sum())else bytes(r.getValue("bytes"));val result=ByteArray(plan.sum());var pos=0
        for(n in sizes){val out=CryptoByteSpan(result,pos,n);if(op=="generate")CryptoChaChaCore.generate(out,ctx)else CryptoChaChaCore.combine(out,ctx,CryptoByteSpan(input,pos,n));pos+=n}
        return result
    }
    fun observe(r:JsonObject):JsonObject {
        if(r["haskell"]?.jsonPrimitive?.boolean==true){val output=haskellBytes(r);return buildJsonObject{put("bytes",json(output));if(r["sha256"]?.jsonPrimitive?.boolean==true)put("sha256",json(java.security.MessageDigest.getInstance("SHA-256").digest(output)))}}

        fun number(o:JsonObject,k:String,default:Int=0)=o[k]?.jsonPrimitive?.long?.toInt()?:default
        val ctx=CryptoChaChaCore.Context(ByteArray(200){if(it<32)17 else if(it>=if(r["logical132"]?.jsonPrimitive?.boolean==true)164 else 168)34 else 81},32)
        val snapshots=mutableListOf<JsonElement>();val trace=mutableListOf<JsonElement>();val tracing=r["trace"]?.jsonPrimitive?.boolean?:false
        fun word(v:Int)=JsonPrimitive(v.toUInt().toString(16).padStart(8,'0'))
        CryptoChaChaCore.observe=if(tracing){label,index,state->trace.add(word(when(label){"coreInput"->0;"coreRound"->1;"coreOutput"->2;"hcoreInput"->3;else->4}));trace.add(word(index));state.forEach{trace.add(word(it))}}else null
        try {for(value in r.getValue("steps").jsonArray) {
            val s=value.jsonObject;s["context"]?.let{bytes(it).copyInto(ctx.data,32)};val op=s.getValue("op").jsonPrimitive.content
            val data=s["bytes"]?.let(::bytes)?:byteArrayOf();val length=number(s,"length",data.size);val alignment=number(s,"alignment");val shift=number(s,"outShift");val size=maxOf(data.size,length+shift,64+shift)
            val buffer=ByteArray(64+alignment+size){if(it<32+alignment)17 else if(it>=32+alignment+size)34 else 165.toByte()};data.copyInto(buffer,32+alignment)
            val output=ByteArray(64+alignment+size){if(it<32+alignment)17 else if(it>=32+alignment+size)34 else 165.toByte()}
            val key=s["key"]?.let(::bytes)?:ByteArray(32){it.toByte()};val iv=s["iv"]?.let(::bytes)?:ByteArray(24){it.toByte()};val kb=ByteArray(64+key.size){if(it<32)17 else 34};key.copyInto(kb,32);val ib=ByteArray(64+iv.size){if(it<32)17 else 34};iv.copyInto(ib,32)
            val out=when{ "outCtx" in s->CryptoByteSpan(ctx.data,32+number(s,"outCtx"),length);s["alias"]?.jsonPrimitive?.boolean==true->CryptoByteSpan(buffer,32+alignment+shift,length);else->CryptoByteSpan(output,32+alignment+shift,length)}
            val keySpan=if("keyCtx" in s)CryptoByteSpan(ctx.data,32+number(s,"keyCtx"),32)else CryptoByteSpan(kb,32,key.size);val ivSpan=if("ivCtx" in s)CryptoByteSpan(ctx.data,32+number(s,"ivCtx"),24)else CryptoByteSpan(ib,32,iv.size);val input=if("inputCtx" in s)CryptoByteSpan(ctx.data,32+number(s,"inputCtx"),length)else CryptoByteSpan(buffer,32+alignment,length);val rounds=number(s,"rounds",20);val scalar=s["value"]?.jsonPrimitive?.content?.toULong(16)?.toLong()?:0L
            when(op){
                "initKeyState"->CryptoChaChaCore.initKeyState(ctx.state,number(s,"keyLength",32),keySpan)
                "initCore"->CryptoChaChaCore.initCore(ctx.state,number(s,"keyLength",32),keySpan,number(s,"ivLength",8),ivSpan)
                "hinitCore"->CryptoChaChaCore.hinitCore(ctx.state,keySpan,ivSpan)
                "init"->CryptoChaChaCore.init(ctx,rounds,number(s,"keyLength",32),keySpan,number(s,"ivLength",8),ivSpan)
                "hchacha"->CryptoChaChaCore.hchacha(rounds,keySpan,ivSpan,out)
                "initX"->CryptoChaChaCore.initX(ctx,rounds,keySpan,ivSpan)
                "core"->CryptoChaChaCore.core(rounds,out,ctx.state)
                "hcore"->CryptoChaChaCore.hcore(rounds,out,ctx.state)
                "combine"->CryptoChaChaCore.combine(out,ctx,input)
                "generate"->CryptoChaChaCore.generate(out,ctx)
                "counter64"->{val v=CryptoChaChaCore.counter64(ctx.state);for(i in 0..<8)out.data[out.offset+i]=(v ushr(8*i)).toByte()}
                "counter32"->{val v=CryptoChaChaCore.counter32(ctx.state);for(i in 0..<4)out.data[out.offset+i]=(v ushr(8*i)).toByte()}
                "setCounter64"->CryptoChaChaCore.setCounter64(ctx.state,scalar)
                "setCounter32"->CryptoChaChaCore.setCounter32(ctx.state,scalar.toInt())
                "simpleBlock"->CryptoChaChaCore.simpleBlock(out,ctx.state,rounds)
                "random"->CryptoChaChaCore.random(rounds,out,ctx.state)
                else->error(op)
            }
            snapshots.add(buildJsonObject{put("context",json(ctx.data));put("buffer",json(buffer));put("output",json(output));put("key",json(kb));put("iv",json(ib))})
        }}finally{CryptoChaChaCore.observe=null}
        return buildJsonObject{put("snapshots",JsonArray(snapshots));if(tracing)put("trace",JsonArray(trace))}
    }
    @JvmStatic fun main(args:Array<String>){var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{line->val row=Json.parseToJsonElement(line).jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$count request=${row.getValue("request")} expected=${row.getValue("expected")} actual=$actual"};count++;if(count%1000==0)println("PASS$count")}};println("PASS$count ordinary ChaCha portable core observations")}
}
class CryptoChaChaCoreTest {
    private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-chacha-core/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
    @org.junit.jupiter.api.TestFactory fun originalObservations()=lines.drop(1).mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("original/$index"){val row=Json.parseToJsonElement(line).jsonObject;org.junit.jupiter.api.Assertions.assertEquals(row.getValue("expected"),ChaChaCoreReplay.observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
    @org.junit.jupiter.api.TestFactory fun originalPropertyFamilies()=lines.drop(1).filter{"\"family\":" in it}.mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("ChaChaSpec/$index") {
        val r=Json.parseToJsonElement(line).jsonObject.getValue("request").jsonObject;val family=r.getValue("family").jsonPrimitive.int
        val actual=ChaChaCoreReplay.haskellBytes(r);val other=if(family==1)ChaChaCoreReplay.haskellBytes(r,"combine")else ChaChaCoreReplay.haskellBytes(r,single=true)
        org.junit.jupiter.api.Assertions.assertArrayEquals(actual,other)
    }}.stream()
    @org.junit.jupiter.api.Test fun completeSourceMatrixAndLogicalStoreBoundary() {
        val root=File(System.getProperty("kotdoc.root"));val matrix=Json.parseToJsonElement(File(root,"tools/chacha-core-reference/SourceMatrix.json").readText()).jsonObject
        org.junit.jupiter.api.Assertions.assertEquals(16,matrix.getValue("portable").jsonPrimitive.int);org.junit.jupiter.api.Assertions.assertEquals(13,matrix.getValue("exports").jsonPrimitive.int)
        val rows=lines.drop(1).asSequence().map{Json.parseToJsonElement(it).jsonObject};val names=rows.filter{"steps" in it.getValue("request").jsonObject}.flatMap{it.getValue("request").jsonObject.getValue("steps").jsonArray.map{v->v.jsonObject.getValue("op").jsonPrimitive.content}}.toSet()
        org.junit.jupiter.api.Assertions.assertEquals(16,names.size);org.junit.jupiter.api.Assertions.assertEquals(600,rows.count{"family" in it.getValue("request").jsonObject})
        val guarded=rows.filter{it.getValue("request").jsonObject["logical132"]?.jsonPrimitive?.boolean==true}.toList();org.junit.jupiter.api.Assertions.assertEquals(6,guarded.size)
        for(row in guarded){val result=ChaChaCoreReplay.observe(row.getValue("request").jsonObject);val ctx=result.getValue("snapshots").jsonArray.first().jsonObject.getValue("context").jsonArray;for(i in 164..<168)org.junit.jupiter.api.Assertions.assertEquals(0,ctx[i].jsonPrimitive.int);for(i in 168..<200)org.junit.jupiter.api.Assertions.assertEquals(34,ctx[i].jsonPrimitive.int)}
    }
    @org.junit.jupiter.api.TestFactory fun sourcePins()=Json.parseToJsonElement(lines.first()).jsonObject.getValue("sources").jsonObject.map{(path,expected)->org.junit.jupiter.api.DynamicTest.dynamicTest(path){org.junit.jupiter.api.Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
