package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.test.*

class OriginalTypesDeepSeqTest {
    private val root=File(System.getProperty("kotdoc.root","."))
    private fun text(name:String):String = javaClass.getResourceAsStream("/pandoc-types/deepseq/$name.gz")?.let{GZIPInputStream(it).bufferedReader().use{r->r.readText()}}?:File(root,"tools/haskell-types-deepseq-reference/$name").readText()
    @TestFactory fun originalFinite():List<DynamicTest> {
        val requests=text("requests.jsonl").lines().filter(String::isNotEmpty);val expected=text("responses.jsonl").lines().filter(String::isNotEmpty)
        check(requests.size==308&&expected.size==requests.size)
        return requests.indices.map{i->DynamicTest.dynamicTest("original NFData finite $i"){assertEquals(Json.parseToJsonElement(expected[i]),deepSeqExecute(Json.parseToJsonElement(requests[i]).jsonObject))}}
    }
    @TestFactory fun originalTypedFinite():List<DynamicTest> {
        val requests=text("requests.jsonl").lines().filter(String::isNotEmpty);val expected=text("responses.jsonl").lines().filter(String::isNotEmpty)
        check(requests.size==308&&expected.size==requests.size)
        return requests.indices.map{i->DynamicTest.dynamicTest("original NFData typed finite $i"){assertEquals(Json.parseToJsonElement(expected[i]),deepSeqTypedExecute(Json.parseToJsonElement(requests[i]).jsonObject))}}
    }
    @TestFactory fun originalDemand():List<DynamicTest> {
        val inputs=Json.parseToJsonElement(text("demand-inputs.json")).jsonArray
        val expected=text("demand-native.jsonl").lines().filter(String::isNotEmpty).chunked(2).associate{(name,value)->Json.parseToJsonElement(name).jsonPrimitive.content to Json.parseToJsonElement(value)}
        check(inputs.size==680&&expected.size==680)
        return inputs.map{input->val r=input.jsonObject;val name=r.getValue("name").jsonPrimitive.content;DynamicTest.dynamicTest("original NFData demand $name"){assertEquals(expected.getValue(name),deepSeqDemandExecute(r))}}
    }
    @TestFactory fun originalExtended():List<DynamicTest> {
        val expected=text("extended-native.jsonl").lines().filter(String::isNotEmpty).chunked(2)
        check(expected.size==118)
        return expected.map{(wire,want)->val name=Json.parseToJsonElement(wire).jsonPrimitive.content;DynamicTest.dynamicTest("original NFData extended $name"){assertEquals(Json.parseToJsonElement(want),deepSeqExtendedExecute(name))}}
    }
    @Test fun forceSharesValueAndNormalForm(){
        var roots=0;var normalForms=0
        val dictionary=NFData<Any>{normalForms++;it()}
        val input=Any();val result=PandocDeepSeq.force(dictionary){roots++;input}
        assertEquals(0,roots);assertEquals(0,normalForms)
        repeat(5){assertSame(input,result())}
        assertEquals(1,roots);assertEquals(1,normalForms)
    }
    @Test fun forceSharesFailureWithoutDemandingResult(){
        var attempts=0;var results=0
        val failure=IllegalStateException("normal form")
        val result=PandocDeepSeq.deepseq(NFData<Any>{attempts++;throw failure},{Any()}){results++;Any()}
        repeat(5){assertSame(failure,assertFailsWith<IllegalStateException>{result()})}
        assertEquals(1,attempts);assertEquals(0,results)
    }
    @Test fun declaredFieldsRetainTheirSourceDictionaries(){
        val ignored=NFData<Any?>{}
        val list=DeepSeqValue(ignored){GenericList.cons<Any?>({error("deep")},{GenericList.empty()})}
        val emph=DeepSeqData.node("Inline","Emph",listOf(list))
        val attr=DeepSeqValue(ignored){DeepSeqTuple(listOf(DeepSeqValue(ignored){error("deep")},DeepSeqValue(ignored){GenericList.empty<Any?>()},DeepSeqValue(ignored){GenericList.empty<Any?>()}))}
        val span=DeepSeqData.node("Inline","Span",listOf(attr,DeepSeqValue(ignored){GenericList.empty<Any?>()}))
        val map=DeepSeqValue(ignored){DeepSeqMap.bin({1L},DeepSeqValue(ignored){"k"},DeepSeqValue(ignored){error("deep")},{DeepSeqMap.tip()},{DeepSeqMap.tip()})}
        val meta=DeepSeqData.node("MetaValue","MetaMap",listOf(map))
        val expected=text("demand-native.jsonl").lines().filter(String::isNotEmpty).chunked(2).associate{(name,value)->Json.parseToJsonElement(name).jsonPrimitive.content to Json.parseToJsonElement(value)}
        for((name,input)in listOf("Inline/Emph/0/deep/rnf" to emph,"Inline/Span/0/deep/rnf" to span,"MetaValue/MetaMap/0/deep/rnf" to meta)){
            val failure=assertFailsWith<IllegalStateException>{input.dictionary.rnf{input.value()}}
            assertEquals(expected.getValue(name).jsonObject.getValue("exception").jsonPrimitive.content,failure.message)
        }
    }
    @Test fun returnedCarrierDemandAndFailuresAreShared(){
        for(apply in listOf(false,true)) {
            var normal=0;var result=0;var root=0
            val failure=IllegalStateException("result root")
            val dictionary=NFData<Int>{normal++}
            val output:()->GenericList<Int> = {result++;GenericList.defer{root++;throw failure}}
            val delayed=if(apply)PandocDeepSeq.strictApply(dictionary,{{_:()->Int->output()}},{1})
                else PandocDeepSeq.deepseq(dictionary,{1},output)
            assertEquals(0,normal);assertEquals(0,result);assertEquals(0,root)
            repeat(5){assertSame(failure,assertFailsWith<IllegalStateException>{delayed()})}
            assertEquals(1,normal);assertEquals(1,result);assertEquals(1,root)
        }
    }
    @Test fun sourceIntegrity(){
        val manifest=Json.parseToJsonElement(File(root,"tools/haskell-types-deepseq-reference/source-manifest.json").readText()).jsonObject
        for((path,want)in manifest.getValue("sha256").jsonObject){val hash=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){"%02x".format(it)};assertEquals(want.jsonPrimitive.content,hash,path)}
    }
}
