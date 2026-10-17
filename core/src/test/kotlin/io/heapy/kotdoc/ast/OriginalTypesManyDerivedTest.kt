package io.heapy.kotdoc.ast

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

class OriginalTypesManyDerivedTest {
    @TestFactory fun originalFinite()=ManyDerivedProof.named("native.jsonl").map{(name,_)->DynamicTest.dynamicTest("Many generic finite $name"){replayManyDerivedFinite(name)}}
    @TestFactory fun originalDemand()=ManyDerivedProof.named("demand-native.jsonl").map{(name,_)->DynamicTest.dynamicTest("Many generic demand $name"){replayManyDerivedDemand(name)}}
    @TestFactory fun originalRead()=ManyDerivedProof.lines("read-inputs.jsonl").indices.map{i->DynamicTest.dynamicTest("Many generic Read $i"){replayManyDerivedRead(i)}}
    @TestFactory fun originalParserDemand()=ManyDerivedProof.named("parser-demand-native.jsonl").map{(name,_)->DynamicTest.dynamicTest("Many generic parser demand $name"){replayManyDerivedParser(name)}}

    @TestFactory fun originalLexicalDemand()=ManyDerivedProof.lines("lex-demand-native.jsonl").indices.map{i->DynamicTest.dynamicTest("Many full lexP demand $i"){replayManyDerivedLex(i)}}

    @Test fun sourceThunksShareValuesAndFailuresAcrossThreads(){
        val pool=Executors.newFixedThreadPool(4)
        try {
            val calls=AtomicInteger();val marker=Any();val value=ManyDerivedThunk{calls.incrementAndGet();marker}
            pool.invokeAll(List(32){Callable{value()}}).forEach{assertSame(marker,it.get())};assertEquals(1,calls.get())
            val failures=AtomicInteger();val failure=IllegalStateException("shared");val failed=ManyDerivedThunk<Any>{failures.incrementAndGet();throw failure}
            pool.invokeAll(List(32){Callable{runCatching{failed()}.exceptionOrNull()}}).forEach{assertSame(failure,it.get())};assertEquals(1,failures.get())
        } finally {pool.shutdownNow()}
    }
    @Test fun extremaPreserveCallerIdentityAndIgnoreElementExtrema(){
        val element=object:ManyElementOrd<Int>{
            override fun equal(left:()->Int,right:()->Int)=true
            override fun compare(left:()->Int,right:()->Int)=0
            override fun minimum(left:()->Int,right:()->Int):Int=error("unused element min")
            override fun maximum(left:()->Int,right:()->Int):Int=error("unused element max")
        }
        val a=Many.fromList(listOf(1));val b=Many.fromList(listOf(2));val d=ManyOrdDictionary(element)
        assertSame(a,d.minimum({a},{b}));assertSame(b,d.maximum({a},{b}))
    }
    @Test fun deferredApplicationsShareWholeMethodFailures(){
        val failure=IllegalStateException("root");var calls=0
        val root:()->Many<Int> = {calls++;throw failure}
        val e=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=true;override fun compare(left:()->Int,right:()->Int)=0}
        val eq=ManyEqDictionary(e);val ord=ManyOrdDictionary(e)
        val applications=listOf<()->Any>(eq.equalLazy(root,root),eq.notEqualLazy(root,root),ord.compareLazy(root,root),ord.lessLazy(root,root),ord.lessEqualLazy(root,root),ord.greaterLazy(root,root),ord.greaterEqualLazy(root,root),ord.minimumLazy(root,root),ord.maximumLazy(root,root))
        assertEquals(0,calls)
        applications.forEach{application->val before=calls;repeat(3){assertSame(failure,assertFailsWith<IllegalStateException>{application()})};assertEquals(before+1,calls)}
    }
    @Test fun parserFactoryAndReturnedCarrierFailuresAreShared(){
        assertNull(ManyReadInput("x").point(Int.MAX_VALUE.toLong()+1))
        assertEquals(120,ManyReadInput("x").point(0L))
        var calls=0;val failure=IllegalStateException("parser")
        val parser=ManyReadPrec.defer<Int>{calls++;throw failure}
        val result=parser.results(0,ManyReadInput(""));assertEquals(0,calls)
        repeat(3){assertSame(failure,assertFailsWith<IllegalStateException>{result.step()})};assertEquals(1,calls)
        var roots=0;val output=ManyReadPrec.pure{GenericList.defer<Int>{roots++;throw failure}}.results(0,ManyReadInput(""))
        val cell=output.step() as GenericList.Step.Cons
        assertEquals(0,roots);repeat(3){assertSame(failure,assertFailsWith<IllegalStateException>{cell.head().value()})};assertEquals(1,roots)
    }
    @Test fun sourceIntegrity(){
        val root=File(System.getProperty("kotdoc.root","."));val manifest=Json.parseToJsonElement(File(root,"tools/haskell-types-many-derived-reference/source-manifest.json").readText()).jsonObject
        for((path,want)in manifest.getValue("sha256").jsonObject){val got=MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).joinToString(""){"%02x".format(it)};assertEquals(want.jsonPrimitive.content,got,path)}
    }
}
