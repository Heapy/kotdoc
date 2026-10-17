package io.heapy.kotdoc.crypto

import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test

class CryptoRsaTest {
 private fun resource(name:String)=javaClass.getResourceAsStream("/crypto-rsa-core/$name")!!
 @TestFactory fun originalOracle()=listOf("original.jsonl.gz","original-samples.jsonl.gz","extra-demand.jsonl.gz").flatMap { file->
  GZIPInputStream(resource(file)).bufferedReader().readLines().mapIndexed { i,line->DynamicTest.dynamicTest("$file/$i"){
   val row=Json.parseToJsonElement(line).jsonObject
   assertEquals(row.getValue("expected"),RsaReplay.observe(row.getValue("request").jsonObject))
  }}
 }
 @TestFactory fun strictRootOracle()=listOf("root-demand.jsonl","callback-root.jsonl").flatMap { file->
  resource(file).bufferedReader().readLines().mapIndexed { i,line->DynamicTest.dynamicTest("$file/$i"){RsaRootReference.assertRow(line)}}
 }
 @Test fun publicCallbackReturnsStrictBlinderRoot(){
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("secret")}))
  val failure=assertThrows(ArithmeticException::class.java){rsa.generateBlinder(BigInteger.ZERO){ByteArray(it)}}
  assertEquals("divide by zero",failure.message)
  val ops=CryptoDrgOperations<CryptoChaChaDrg>{length,state->
   val result=cryptoRandomMemo{state.value.randomBytesGenerate(length)}
   cryptoRandomMemo{result.value.first} to cryptoRandomMemo{result.value.second}
  }
  val generator=lazyOf(CryptoChaChaDrg.initialize(ByteArray(40)))
  val action=rsa.generateBlinderAction(lazyOf(BigInteger.ZERO),ops)
  val pair=CryptoPseudoRandom.withDRG(generator,action)
  assertEquals("divide by zero",assertThrows(ArithmeticException::class.java){pair.first.value}.message)
  assertEquals(42,CryptoPseudoRandom.withDRG(generator,action.map{42}).first.value)
  assertEquals(65,pair.second.value.randomBytesGenerate(65).first.size)
  val deferred=rsa.generateBlinderDeferred(BigInteger.ZERO){ByteArray(it)}
  val first=assertThrows(ArithmeticException::class.java){deferred.value}
  assertSame(first,assertThrows(ArithmeticException::class.java){deferred.value})
 }
 @TestFactory fun finiteRetries()=listOf("retry.jsonl.gz","blinder-retry.jsonl.gz").flatMap { file->
  GZIPInputStream(resource(file)).bufferedReader().readLines().mapIndexed { i,line->DynamicTest.dynamicTest("$file/$i"){
   if(file=="retry.jsonl.gz")RsaRetryReference.assertRow(line)else RsaBlinderRetryReference.assertRow(line)
  }}
 }
 @TestFactory fun sourcePins():List<DynamicTest> {
  val manifest=Json.parseToJsonElement(resource("Manifest.json").bufferedReader().readText()).jsonObject
  return manifest.getValue("sources").jsonObject.map{(path,expected)->DynamicTest.dynamicTest(path){
   val bytes=File(System.getProperty("kotdoc.root"),path).readBytes()
   assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
  }}
 }
 private fun withResourceFile(name:String,action:(String)->Unit){val file=File.createTempFile("rsa-reference-",name.substringAfterLast('.'));try{file.writeBytes(resource(name).readBytes());action(file.absolutePath)}finally{file.delete()}}
 @Test fun bothOriginalBlinderProperties(){withResourceFile("original-samples.jsonl.gz"){RsaOriginalProperties.main(arrayOf(it))}}
 @Test fun customDictionaryDemand(){withResourceFile("demand.txt"){RsaDemandReplay.main(arrayOf(it))}}
 @Test fun effectfulCallbackIsDifferentFromPureState(){
  val expected=resource("callback.txt").bufferedReader().readLines()
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{ByteArray(32){it.toByte()}}))
  val got=listOf(0,1,2,17).map{e->val calls=mutableListOf<Int>();val failure=assertThrows(IllegalStateException::class.java){rsa.generate(2,e.toBigInteger()){n->calls+=n;error("rng budget")}};"($e,\"${failure.message}\",[${calls.joinToString(",")}])"}
  assertEquals(expected,got)
 }
 @Test fun invalidExponentLeavesPrimesAndSizeUnused(){
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("secret")}))
  for(e in -2..1)assertSame(CryptoMaybe.None,rsa.generateWithDeferred(cryptoRandomMemo{error("p")},cryptoRandomMemo{error("q")},cryptoRandomMemo{error("size")},lazyOf(e.toBigInteger())))
 }
 @Test fun blinderFailureAndSuccessAreSharedAndRandomInputIsOwned(){
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("secret")}))
  val source=ByteArray(16){1};val copy=source.copyOf();var calls=0
  val b=rsa.generateBlinderDeferred(BigInteger.ONE){calls++;source}
  val first=assertThrows(CoprimesAssertionError::class.java){b.value}
  val second=assertThrows(CoprimesAssertionError::class.java){b.value}
  assertSame(first,second);assertEquals(1,calls);assertArrayEquals(copy,source)
  val good=CryptoRsa.Blinder(2.toBigInteger(),1617.toBigInteger())
  assertEquals("Blinder 2 1617",good.toString());assertEquals(good,CryptoRsa.Blinder(2.toBigInteger(),1617.toBigInteger()))
 }
 @Test fun checkedResourceDimensionsDoNotWrap(){
  val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("secret")}))
  var draws=0
  assertThrows(ArithmeticException::class.java){rsa.generate(Int.MAX_VALUE,17.toBigInteger()){draws++;error("bytes")}}
  assertEquals(0,draws)
 }
}
