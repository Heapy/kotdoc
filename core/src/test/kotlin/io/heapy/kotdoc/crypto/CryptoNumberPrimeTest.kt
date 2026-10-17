package io.heapy.kotdoc.crypto

import java.math.BigInteger
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CryptoNumberPrimeTest {
 private fun prime()=CryptoNumberPrime(CryptoProbabilistic{ByteArray(32){it.toByte()}})
 private fun rows(name:String)=GZIPInputStream(javaClass.getResourceAsStream("/crypto-number-prime/$name")!!).bufferedReader().readLines()
 @TestFactory fun sourceOracle()=listOf("original.jsonl.gz","original-properties.jsonl.gz","demand.jsonl.gz","original-primality.jsonl.gz","private.jsonl.gz").flatMap { file ->
  rows(file).mapIndexed {i,row->DynamicTest.dynamicTest("$file/$i"){CryptoNumberPrimeReference.assertRow(row)}}
 }
 @TestFactory fun allSevenOriginalPrimalityExamples():List<DynamicTest> {
  val literal=Json.parseToJsonElement(javaClass.getResourceAsStream("/crypto-number-prime/original-literals.json")!!.bufferedReader().readText()).jsonObject
  val carmichaels=literal.getValue("carmichaels").jsonArray.map{it.jsonPrimitive.content.toBigInteger()}
  val pseudoprimes=literal.getValue("strongPseudoprimesBase2").jsonArray.map{it.jsonPrimitive.content.toBigInteger()}
  val p=prime();val one=BigInteger.ONE
  return listOf(
   DynamicTest.dynamicTest("NumberSpec: agrees with trial division on odd 5..5001"){for(n in 5..5001 step 2)assertEquals(p.primalityTestNaive(n.toBigInteger()),p.primalityTestMillerRabin(30,n.toBigInteger()))},
   DynamicTest.dynamicTest("NumberSpec: Carmichael numbers"){assertTrue(carmichaels.none{p.primalityTestMillerRabin(30,it)})},
   DynamicTest.dynamicTest("NumberSpec: strong base-2 pseudoprimes"){assertTrue(pseudoprimes.none{p.primalityTestMillerRabin(30,it)})},
   DynamicTest.dynamicTest("NumberSpec: probablyPrime composites"){assertTrue((carmichaels+pseudoprimes).none{p.isProbablyPrime(it)})},
   DynamicTest.dynamicTest("NumberSpec: 512-bit prime and product"){assertTrue(p.primalityTestMillerRabin(30,one.shiftLeft(512)-569.toBigInteger()));assertFalse(p.primalityTestMillerRabin(30,(one.shiftLeft(256)-189.toBigInteger())*(one.shiftLeft(256)-357.toBigInteger())))},
   DynamicTest.dynamicTest("NumberSpec: small numbers and shortcut edges"){assertTrue(listOf(-3,-1,0,1,4,6,8,9,2911).none{p.isProbablyPrime(it.toBigInteger())});assertTrue(listOf(2,3,5,7,2897,2903,2909,2917).all{p.isProbablyPrime(it.toBigInteger())})},
   DynamicTest.dynamicTest("NumberSpec: repeated source askAgain"){val first=p.primalityTestMillerRabin(1,2465.toBigInteger());repeat(20){assertEquals(first,p.primalityTestMillerRabin(1,2465.toBigInteger()))}}
  )
 }
 @TestFactory fun sourcePins():List<DynamicTest> {
  val manifest=Json.parseToJsonElement(javaClass.getResourceAsStream("/crypto-number-prime/Manifest.json")!!.bufferedReader().readText()).jsonObject
  assertEquals(14334,manifest.getValue("cases").jsonPrimitive.int)
  assertEquals(420,manifest.getValue("sourceTableEntries").jsonPrimitive.int)
  return manifest.getValue("sources").jsonObject.map{(path,expected)->DynamicTest.dynamicTest(path){
   val bytes=java.io.File(System.getProperty("kotdoc.root"),path).readBytes()
   assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
  }}
 }
 @Test fun guardsDoNotRequestEntropyOrCandidateBytes() {
  var entropy=0;var candidate=0
  val p=CryptoNumberPrime(CryptoProbabilistic{entropy++;error("entropy demanded")})
  assertTrue(p.isProbablyPrime(2903.toBigInteger()));assertFalse(p.isProbablyPrime(2911.toBigInteger()))
  assertFalse(p.primalityTestMillerRabin(0,4.toBigInteger()))
  assertThrows(IllegalStateException::class.java){p.primalityTestMillerRabin(0,5.toBigInteger())}
  assertThrows(CryptoNumberPrime.PrimeSizeInvalid::class.java){p.generatePrime(4){candidate++;error("candidate demanded")}}
  assertThrows(CryptoNumberPrime.PrimeSizeInvalid::class.java){p.generateSafePrime(5){candidate++;error("candidate demanded")}}
  assertEquals(0,entropy);assertEquals(0,candidate)
 }
 @Test fun processSecretIsSharedAndWitnessStateRestartsForEachMaterial() {
  var entropy=0;val trace=mutableListOf<List<Int>>()
  val p=CryptoNumberPrime(CryptoProbabilistic{entropy++;ByteArray(32){it.toByte()}}){_,b->trace+=b.map{it.toInt()and 255}}
  val first=p.primalityTestMillerRabin(3,2909.toBigInteger());val draws=trace.toList();trace.clear()
  assertEquals(first,p.primalityTestMillerRabin(3,2909.toBigInteger()));assertEquals(draws,trace)
  assertTrue(p.primalityTestMillerRabin(3,2917.toBigInteger()));assertEquals(1,entropy)
 }
}
