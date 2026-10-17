package io.heapy.kotdoc.crypto
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
internal object Sha512PrefixBoundaryReplay {
    fun observe(r:JsonObject):JsonObject {
        fun int(k:String)=r.getValue(k).jsonPrimitive.long.toInt()
        fun long(k:String)=r.getValue(k).jsonPrimitive.long
        fun hex(v:Long)=v.toULong().toString(16).padStart(16,'0')
        fun unsigned(v:Int)=v.toLong() and 0xffffffffL
        val ctx=CryptoSha512.Context();ctx.word(0,r.getValue("low").jsonPrimitive.content.toULong(16).toLong());ctx.word(1,r.getValue("high").jsonPrimitive.content.toULong(16).toLong())
        val control=CryptoSha512.PrefixControl(int("length"),int("n"),ctx.word(0));val bits=CryptoSha512.incrementSize(ctx,control.n)
        var pos=long("start");var index=((control.initialIndex.toLong()+pos)%128).toInt();var blocks=0
        val events=mutableListOf<JsonElement>();val selections=mutableListOf<JsonElement>()
        while(control.continues(pos)&&pos<long("stop")) {
            val input=control.hasInput(pos);var b=if(input)((pos*17+3).toInt() and 255) and control.inputMask(pos) else 0
            b=b or (128 and control.firstPaddingMask(pos));if(index>=112)b=b or ((bits[index-112].toInt() and 255) and control.sizeMask(pos))
            events.add(JsonArray(listOf(pos,index.toLong(),b.toLong(),if(input)1L else 0L,unsigned(control.inputMask(pos)),unsigned(control.firstPaddingMask(pos)),unsigned(control.sizeMask(pos))).map(::JsonPrimitive)))
            index++;pos++
            if(index>=128){index=0;blocks++;selections.add(JsonArray(listOf(pos,unsigned(control.outputMask(pos))).map(::JsonPrimitive)))}
        }
        return buildJsonObject {
            put("summary",JsonArray(listOf<JsonElement>(JsonPrimitive(control.limit),JsonPrimitive(unsigned(control.n)),JsonPrimitive(control.padLength),JsonPrimitive(pos),JsonPrimitive(index),JsonPrimitive(blocks),JsonPrimitive(events.size),JsonPrimitive(selections.size),JsonPrimitive(hex(ctx.word(0))),JsonPrimitive(hex(ctx.word(1))))))
            put("events",JsonArray(events));put("selections",JsonArray(selections))
        }
    }
    @JvmStatic fun main(args:Array<String>) {var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{line->val row=Json.parseToJsonElement(line).jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$count request=${row.getValue("request")} expected=${row.getValue("expected")} actual=$actual"};count++}};println("PASS$count original segmented prefix boundary windows")}
}
class CryptoSha512PrefixBoundaryTest {
    private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-sha512-prefix-boundary/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
    @org.junit.jupiter.api.TestFactory fun originalControlWindows()=lines.drop(1).mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("source-window/$index"){val row=Json.parseToJsonElement(line).jsonObject;org.junit.jupiter.api.Assertions.assertEquals(row.getValue("expected"),Sha512PrefixBoundaryReplay.observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
    @org.junit.jupiter.api.TestFactory fun sourcePins()=Json.parseToJsonElement(lines.first()).jsonObject.getValue("sources").jsonObject.map{(path,expected)->org.junit.jupiter.api.DynamicTest.dynamicTest(path){org.junit.jupiter.api.Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
    @org.junit.jupiter.api.Test fun signedBoundaryRegression() {
        val length=Int.MAX_VALUE-143
        org.junit.jupiter.api.Assertions.assertTrue(length+144<0) // The previous loop skipped every byte here.
        val c=CryptoSha512.PrefixControl(length,length,0)
        org.junit.jupiter.api.Assertions.assertTrue(c.continues(0));org.junit.jupiter.api.Assertions.assertTrue(c.continues(Int.MAX_VALUE.toLong()));org.junit.jupiter.api.Assertions.assertFalse(c.continues(c.limit))
        val worst=CryptoSha512.PrefixControl(Int.MAX_VALUE,Int.MAX_VALUE,113)
        org.junit.jupiter.api.Assertions.assertEquals(Int.MAX_VALUE.toLong()+144,worst.limit);org.junit.jupiter.api.Assertions.assertEquals(128,worst.padLength)
        org.junit.jupiter.api.Assertions.assertEquals(-1,worst.outputMask(worst.limit));org.junit.jupiter.api.Assertions.assertTrue(worst.hasInput(Int.MAX_VALUE.toLong()-1));org.junit.jupiter.api.Assertions.assertFalse(worst.hasInput(Int.MAX_VALUE.toLong()))
    }
    @org.junit.jupiter.api.Test fun validSpanArithmeticBounds() {
        for(length in listOf(0,1,111,112,127,128,Int.MAX_VALUE-145,Int.MAX_VALUE-144,Int.MAX_VALUE-143,Int.MAX_VALUE-8,Int.MAX_VALUE))for(n in listOf(0,1,length,Int.MIN_VALUE,-1))for(index in 0..<128) {
            val c=CryptoSha512.PrefixControl(length,n,index.toLong())
            org.junit.jupiter.api.Assertions.assertTrue(c.n in 0..length);org.junit.jupiter.api.Assertions.assertTrue(c.padLength in 1..128);org.junit.jupiter.api.Assertions.assertEquals(length.toLong()+144,c.limit)
            org.junit.jupiter.api.Assertions.assertTrue(c.limit<0x100000000L);org.junit.jupiter.api.Assertions.assertTrue(c.n.toLong()+c.padLength+16<=c.limit)
            if(length>0)org.junit.jupiter.api.Assertions.assertTrue(c.hasInput(length.toLong()-1));org.junit.jupiter.api.Assertions.assertFalse(c.hasInput(length.toLong()))
        }
    }
}
