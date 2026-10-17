package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.*
class OriginalTypesGenericRepTest {
    private val root=File(System.getProperty("kotdoc.root","."))
    private val base=File(root,"tools/haskell-types-generic-rep-reference")
    private fun observations(name:String)=GenericRepFixtures.text(name).lines().filter(String::isNotEmpty).chunked(2).map{(n,v)->Json.parseToJsonElement(n).jsonPrimitive.content to Json.parseToJsonElement(v)}
    @TestFactory fun originalFinite()=List(85){i->DynamicTest.dynamicTest("derived Generic full source and typed $i"){genericRepFiniteCase(i)}}
    @TestFactory fun originalDemand():List<DynamicTest> {
        val inputs=Json.parseToJsonElement(GenericRepFixtures.text("demand-inputs.json")).jsonArray
        val expected=observations("demand-native.jsonl").toMap();check(inputs.size==820&&expected.size==820)
        return inputs.map{input->val r=input.jsonObject;val name=r.getValue("name").jsonPrimitive.content;DynamicTest.dynamicTest("derived Generic demand $name"){assertEquals(expected.getValue(name),genericRepDemandExecute(r))}}
    }
    @TestFactory fun originalDirect():List<DynamicTest> {
        val inputs=Json.parseToJsonElement(GenericRepFixtures.text("direct-inputs.json")).jsonArray
        val expected=observations("direct-native.jsonl").toMap();check(inputs.size==773&&expected.size==773)
        return inputs.map{input->val r=input.jsonObject;val name=r.getValue("name").jsonPrimitive.content;DynamicTest.dynamicTest("derived Generic direct Rep $name"){assertEquals(expected.getValue(name),genericRepDirectExecute(r))}}
    }
    @TestFactory fun originalCustom():List<DynamicTest> {
        val rows=observations("custom-native.jsonl");check(rows.size==56)
        return rows.map{(name,value)->DynamicTest.dynamicTest("custom Generic $name"){assertEquals(value,genericRepCustomExecute(name))}}
    }
    @Test fun rootsAndFailuresAreShared(){
        val dictionary=GenericRepData.definition("Inline")
        var roots=0;var fields=0
        val root=GenericRepData.source("Inline","Emph",listOf(GenericRepField(GenericDatas.list(GenericDefinitionData.named("Inline")).type){fields++;GenericList.empty<Any?>()}))
        val rep=dictionary.from{roots++;root}
        assertEquals(0,roots);repeat(5){rep.whnf()};assertEquals(1,roots);assertEquals(1,fields)
        val output=dictionary.to(rep);val first=output();repeat(5){assertSame(first,output())};assertEquals(1,fields)
        var attempts=0;val failure=IllegalStateException("representation")
        val failed=dictionary.to(GenericRepValue(dictionary.representation){attempts++;throw failure})
        repeat(5){assertSame(failure,assertFailsWith<IllegalStateException>{failed()})};assertEquals(1,attempts)
    }
    @Test fun dynamicallyMalformedRepresentationsAreGuarded(){
        // JVM guards have no ill-typed Haskell counterpart and are counted separately.
        val dictionary=GenericRepData.definition("Inline")
        assertFailsWith<IllegalArgumentException>{dictionary.to(GenericRepValue(GenericRepType.Unit){GenericRepLayer.Unit})}
        var payload=0
        val wrong=GenericRepValue(GenericRepType.Field(GenericDatas.int.type)){GenericRepLayer.Field(GenericRepField(GenericDatas.text.type){payload++;"x"})}
        assertFailsWith<IllegalArgumentException>{wrong.whnf()};assertEquals(0,payload)
    }
    @Test fun customMethodDispatchIsDelayedAndShared(){
        var fromCalls=0;var toCalls=0;var roots=0;var representations=0
        val inputFailure=IllegalStateException("input");val repFailure=IllegalStateException("representation")
        val dictionary=object:GenericRepDictionary<Unit>{
            override val representation=GenericRepType.Unit
            override fun from(value:GenericRepThunk<Unit>):GenericRepValue {fromCalls++;value();return GenericRepValue(representation){GenericRepLayer.Unit}}
            override fun to(value:GenericRepValue):GenericRepThunk<Unit>{toCalls++;value.layer();return GenericRepThunk{Unit}}
        }
        val rep=PandocGenericRep.from(dictionary){roots++;throw inputFailure}
        assertEquals(0,fromCalls);assertEquals(0,roots)
        repeat(5){assertSame(inputFailure,assertFailsWith<IllegalStateException>{rep.whnf()})}
        assertEquals(1,fromCalls);assertEquals(1,roots)
        val result=PandocGenericRep.to(dictionary,GenericRepValue(GenericRepType.Unit){representations++;throw repFailure})
        assertEquals(0,toCalls);assertEquals(0,representations)
        repeat(5){assertSame(repFailure,assertFailsWith<IllegalStateException>{result()})}
        assertEquals(1,toCalls);assertEquals(1,representations)
    }
    @Test fun sourceIntegrity(){
        val manifest=Json.parseToJsonElement(File(base,"source-manifest.json").readText()).jsonObject
        for((path,want)in manifest.getValue("sha256").jsonObject){val hash=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){"%02x".format(it)};assertEquals(want.jsonPrimitive.content,hash,path)}
    }
}
