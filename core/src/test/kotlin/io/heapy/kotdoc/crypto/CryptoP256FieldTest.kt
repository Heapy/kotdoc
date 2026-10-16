package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoP256FieldTest {
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.int(key: String) = text(key).toInt()
    private fun JsonObject.words(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.content.toULong().toLong() }.toLongArray()
    private fun json(values: LongArray) = JsonArray(values.map { JsonPrimitive(it.toULong().toString()) })
    private fun observe(r: JsonObject): JsonObject {
        val bits=r.int("bits");val field=CryptoP256Field(bits);val n=field.limbs
        val guard=if(bits==32)0x695a4b3cL else 0xa5968778695a4b3cuL.toLong()
        class Buffer(value: LongArray) {
            val data=longArrayOf(guard)+value+longArrayOf(guard)
            val region=CryptoP256Words.Region(data,1)
            fun values()=data.copyOfRange(1,data.size-1)
            fun guarded()=data.first()==guard&&data.last()==guard
        }
        val a=Buffer(r.words("a"));val b=Buffer(r.words("b"));val c=Buffer(LongArray(n){if(bits==32)0xa5a5a5a5L else 0xa5a5a5a5a5a5a5a5uL.toLong()})
        val word=Buffer(r.words("word"));val low=r.words("low");val high=r.words("high")
        val originalLow=low.copyOf();val originalHigh=high.copyOf()
        val out=when(r.int("alias")){1->a.region;2->b.region;else->c.region}
        val audit=CryptoP256Field.Audit();var ret=0;var constant=longArrayOf()
        when(val op=r.text("op")) {
            "carry"->field.reduceCarry(out,r.text("carry").toULong().toLong(),audit)
            "sum"->field.sum(out,a.region,b.region,audit)
            "diff"->field.difference(out,a.region,b.region,audit)
            "degree"->field.reduceDegree(out,low,high,audit)
            "square"->field.square(out,a.region,audit)
            "mul"->field.multiply(out,a.region,b.region,audit)
            "assign"->field.assign(out,a.region,audit)
            "scalar3","scalar4","scalar8"->field.scalar(out,op.last().digitToInt(),audit)
            "zero"->ret=if(field.isZero(a.region,audit))1 else 0
            "to"->field.toMontgomery(out,word.region,audit)
            "from"->field.fromMontgomery(word.region,a.region,audit)
            else->constant=field.constant(if(op=="zeroConst")"zero" else op)
        }
        return buildJsonObject {
            put("ret",ret);put("a",json(a.values()));put("b",json(b.values()));put("c",json(c.values()));put("word",json(word.values()));put("constant",json(constant))
            put("low",json(low));put("high",json(high))
            put("guards",listOf(a,b,c,word).all{it.guarded()}&&originalLow.contentEquals(low)&&originalHigh.contentEquals(high))
            put("ops",json(audit.operations));put("wordOps",json(audit.words.ops))
            put("trace",JsonArray(audit.trace.map{(name,data)->JsonArray(listOf(JsonPrimitive(name),json(data)))}))
        }
    }
    @TestFactory
    fun originalFieldObservations(): Stream<DynamicTest> {
        val root=File(System.getProperty("kotdoc.root"))
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/crypto-p256-field/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}
        val manifest=Json.parseToJsonElement(lines.first()).jsonObject
        val tests=mutableListOf<DynamicTest>()
        for((path,expected)in manifest.getValue("sources").jsonObject) tests+=DynamicTest.dynamicTest("source $path") {
            val digest=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}
            assertEquals(expected.jsonPrimitive.content,digest,path)
        }
        assertEquals(manifest.int("cases"),lines.size-1)
        for((index,line)in lines.drop(1).withIndex()) {
            val c=Json.parseToJsonElement(line).jsonObject;val r=c.getValue("request").jsonObject
            tests+=DynamicTest.dynamicTest("$index ${r.int("bits")} ${r.text("op")} ${c.text("group")}"){assertEquals(c.getValue("expected"),observe(r),r.toString())}
        }
        return tests.stream()
    }
}
