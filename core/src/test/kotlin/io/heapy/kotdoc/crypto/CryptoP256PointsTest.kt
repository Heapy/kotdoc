package io.heapy.kotdoc.crypto

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoP256PointsTest {
    private fun JsonObject.text(key: String)=getValue(key).jsonPrimitive.content
    private fun JsonObject.int(key: String)=text(key).toInt()
    private fun values(a: JsonElement)=a.jsonArray.map{it.jsonPrimitive.content.toULong().toLong()}.toLongArray()
    private fun json(a: LongArray)=JsonArray(a.map{JsonPrimitive(it.toULong().toString())})
    private fun observe(r: JsonObject): JsonObject {
        val bits=r.int("bits");val engine=CryptoP256Points(bits);val n=engine.limbs;val wordn=256/bits
        val guard=if(bits==32)0x695a4b3cL else 0xa5968778695a4b3cuL.toLong()
        class Buffer(value: LongArray) {
            val data=longArrayOf(guard)+value+longArrayOf(guard)
            val region=CryptoP256Words.Region(data,1)
            fun values()=data.copyOfRange(1,data.size-1)
            fun guarded()=data.first()==guard&&data.last()==guard
        }
        val fields=r.getValue("fields").jsonArray.map{Buffer(values(it))}+List(3){Buffer(LongArray(n){0xa5a5a5a5L})}
        val words=r.getValue("words").jsonArray.map{Buffer(values(it))}+List(2){Buffer(LongArray(wordn){if(bits==32)0xa5a5a5a5L else 0xa5a5a5a5a5a5a5a5uL.toLong()})}
        val alias=r.int("alias")
        val ox=fields[when(alias){1->0;2->3;4->1;else->6}].region
        val oy=fields[when(alias){1->1;2->4;3->6;4->0;else->7}].region
        val oz=fields[when(alias){1->2;2->5;3->6;else->8}].region
        val wx=words[when(alias){1->2;2->4;4->3;else->6}].region
        val wy=words[when(alias){1->3;2->5;3->6;4->2;else->7}].region
        val out=CryptoP256Points.Point(ox,oy,oz)
        val a=CryptoP256Points.Point(fields[0].region,fields[1].region,fields[2].region)
        val b=CryptoP256Points.Point(fields[3].region,fields[4].region,fields[5].region)
        val audit=CryptoP256Points.Audit(r.int("limit"))
        when(r.text("op")) {
            "inv"->engine.inverse(ox,a.x,audit)
            "double"->engine.double(out,a,audit)
            "mixed"->engine.addMixed(out,a,b.x,b.y,audit)
            "add"->engine.add(out,a,b,audit)
            "adddouble"->engine.addOrDouble(out,a,b,audit)
            "copy"->engine.copyConditional(ox,a.x,r.text("mask").toULong().toLong(),audit)
            "selectaffine"->{val index=r.int("index");val table=CryptoP256Field(bits).constant("precomputed");engine.selectAffine(ox,oy,CryptoP256Words.Region(table,(index/16)*30*n),(index%16).toLong(),audit)}
            "selectjacobian"->{val mask=if(bits==32)0xffffffffL else -1L;val table=LongArray(16*3*n){(0x9e3779b97f4a7c15uL.toLong()*(it+1)) and mask};table.fill(0,0,3*n);engine.selectJacobian(out,CryptoP256Words.Region(table),r.int("index").toLong(),audit)}
            "baseraw"->engine.baseMult(out,words[0].region,audit)
            "affine"->engine.toAffine(ox,oy,a,audit)
            "scalarraw"->engine.scalarMult(out,a.x,a.y,words[0].region,audit)
            "base"->engine.basePointMultiply(words[0].region,wx,wy,audit)
            "points"->engine.pointsMultiplyVariable(words[0].region,words[1].region,words[2].region,words[3].region,wx,wy,audit)
            "pointadd"->engine.pointAdd(words[2].region,words[3].region,words[4].region,words[5].region,wx,wy,audit)
            "negate"->engine.pointNegate(words[2].region,words[3].region,wx,wy,audit)
            "pointmul"->engine.pointMultiply(words[0].region,words[2].region,words[3].region,wx,wy,audit)
            else->error(r.toString())
        }
        return buildJsonObject {
            put("fields",JsonArray(fields.map{json(it.values())}));put("words",JsonArray(words.map{json(it.values())}));put("guards",(fields+words).all{it.guarded()})
            put("ops",json(audit.operations));put("fieldOps",json(audit.field.operations));put("wordOps",json(LongArray(28){audit.words.ops[it]+audit.field.words.ops[it]}))
            put("hash",audit.hash.toULong().toString());put("events",audit.events.toString())
            put("trace",JsonArray(audit.trace.map{(name,data)->JsonArray(listOf(JsonPrimitive(name),json(data)))}))
        }
    }
    @TestFactory
    fun originalPointObservations(): Stream<DynamicTest> {
        val root=File(System.getProperty("kotdoc.root"))
        val lines=GZIPInputStream(javaClass.getResourceAsStream("/crypto-p256-points/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}
        val manifest=Json.parseToJsonElement(lines.first()).jsonObject
        val tests=mutableListOf<DynamicTest>()
        for((path,expected)in manifest.getValue("sources").jsonObject)tests+=DynamicTest.dynamicTest("source $path") {
            val digest=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}
            assertEquals(expected.jsonPrimitive.content,digest,path)
        }
        assertEquals(manifest.int("cases"),lines.size-1)
        for((index,line)in lines.drop(1).withIndex()){
            val c=Json.parseToJsonElement(line).jsonObject;val r=c.getValue("request").jsonObject
            tests+=DynamicTest.dynamicTest("$index ${r.int("bits")} ${r.text("op")} ${c.text("group")}"){assertEquals(c.getValue("expected"),observe(r),r.toString())}
        }
        return tests.stream()
    }
}
