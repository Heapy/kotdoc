package io.heapy.kotdoc.ast

import io.heapy.kotdoc.crypto.CryptoRsaTypes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import java.io.File
import kotlin.test.*

class IntrinsicRootInteropTest {
    private val ignored=NFData<CryptoRsaTypes.KeyPair>{}
    private fun bad()=CryptoRsaTypes.KeyPair.deferred{error("privateRoot")}
    private fun partial()=CryptoRsaTypes.KeyPair(CryptoRsaTypes.PrivateKey.deferred({error("publicField")}, {0.toBigInteger()}, {0.toBigInteger()}, {0.toBigInteger()}, {0.toBigInteger()}, {0.toBigInteger()}, {0.toBigInteger()}))
    private class Root(value:()->Any?):GenericRepRoot {
        private val root=GenericMemo(value)
        override fun forceGenericRepRoot(){root.get()}
    }
    @TestFactory fun unchangedOriginalRootContracts():List<DynamicTest> {
        val cases=linkedMapOf<String,()->Unit>(
            "root" to {SourceThunk{bad()}()},
            "rwhnf" to {PandocDeepSeq.rwhnf{bad()}},
            "force" to {PandocDeepSeq.force(CryptoRsaTypes.pairNFData){bad()}()},
            "deepseq-result" to {PandocDeepSeq.deepseq(DeepSeqData.long,{0L}){bad()}()},
            "strictApply-result" to {PandocDeepSeq.strictApply(DeepSeqData.long,{{_:()->Long->bad()}},{0L})()},
            "own-rnf" to {PandocDeepSeq.rnf(CryptoRsaTypes.pairNFData){bad()}},
            "ignored-result" to {PandocDeepSeq.force(CryptoRsaTypes.pairNFData){bad()};Unit},
            "record-lazy-root" to {SourceThunk{partial()}()},
            "record-lazy-rwhnf" to {PandocDeepSeq.rwhnf{partial()}},
            "record-lazy-rnf" to {PandocDeepSeq.rnf(CryptoRsaTypes.pairNFData){partial()}},
            "force-ignored-dictionary" to {PandocDeepSeq.force(NFData<Root>{}){Root{error("representedRoot")}}()},
            "deepseq-ignored-input" to {PandocDeepSeq.deepseq(NFData<Root>{},{Root{error("unused")}}){Unit}()}
        )
        val lines=File(System.getProperty("kotdoc.root"),"tools/haskell-types-deepseq-reference/intrinsic-root-native.txt").readLines()
        assertEquals(cases.keys.toList(),lines.map{it.substringBefore(':')})
        return lines.map{line->val name=line.substringBefore(':');DynamicTest.dynamicTest(name){assertEquals(line.substringAfter(':'),try{cases.getValue(name)();"RETURNED"}catch(e:IllegalStateException){e.message})}}
    }
    @Test fun allPublicValueConsumersForceIntrinsicRoot() {
        val consumers=listOf<(()->CryptoRsaTypes.KeyPair)->()->Any?>(
            {get->{PandocDeepSeq.rwhnf(get)}},
            {get->{PandocDeepSeq.rwhnf(DeepSeqValue(ignored,get))}},
            {get->SourceThunk(get)},
            {get->PandocDeepSeq.force(ignored,get)},
            {get->PandocDeepSeq.deepseq(DeepSeqData.long,{0L},get)},
            {get->PandocDeepSeq.strictApply(DeepSeqData.long,{{_:()->Long->get()}},{0L})}
        )
        for(consumer in consumers){var demands=0;val failure=IllegalStateException("privateRoot");val input=SourceThunk{CryptoRsaTypes.KeyPair.deferred{demands++;throw failure}};val output=consumer(input);assertEquals(0,demands);repeat(3){assertSame(failure,assertFailsWith<IllegalStateException>{output()})};assertEquals(1,demands)}
    }
    @Test fun genericAndDataIntrinsicPathsDoNotDoubleDispatch() {
        for(generic in listOf(false,true)) {
            var dispatches=0
            val root=object:GenericRepRoot{override fun forceGenericRepRoot(){dispatches++}}
            val get:()->Any?=if(generic)GenericRepThunk{root}else SourceThunk{root}
            repeat(3){assertSame(root,get())}
            assertEquals(1,dispatches)
        }
    }
    @Test fun sourceThunkFailureIsMemoizedAndKeepsFirstError() {
        var dispatches=0
        val first=IllegalStateException("first")
        val get=GenericRepThunk {object:GenericRepRoot{override fun forceGenericRepRoot(){dispatches++;throw first}}}
        repeat(3){assertSame(first,assertFailsWith<IllegalStateException>{get()})}
        assertEquals(1,dispatches)
    }
}
