package io.heapy.kotdoc.crypto

import io.heapy.kotdoc.x509.*
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*

/** Replays original RSASpec blinder properties with original Utils.TestDRG seeds. */
object RsaOriginalProperties {
 @JvmStatic fun main(args:Array<String>) {
  val vector=Json.parseToJsonElement(File(System.getProperty("kotdoc.root", "."),"tools/rsa-core-reference/VectorInputs.json").readText()).jsonArray.first().jsonObject.getValue("private").jsonObject
  fun field(name:String)=vector.getValue(name).jsonPrimitive.content.toBigInteger()
  val public=vector.getValue("public").jsonObject
  val pub=X509RsaPublicKey(public.getValue("size").jsonPrimitive.content.toLong(),public.getValue("n").jsonPrimitive.content.toBigInteger(),public.getValue("e").jsonPrimitive.content.toBigInteger())
  val key=X509RsaPrivateKey(pub,X509SecretIntegers(listOf("d","p","q","dP","dQ","qinv").map{lazyOf(field(it))}))
  val cipher=CryptoRsa.ep(pub,ByteArray(32){7})
  val plain=CryptoRsa.dp(null,key,cipher)
  var count=0
  GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines { lines->lines.forEach { line->
   val row=Json.parseToJsonElement(line).jsonObject
   val words=row.getValue("request").jsonObject.getValue("words").jsonArray.map{it.jsonPrimitive.content.toULong()}
   val drg=CryptoChaChaDrg.initializeWords(words[0],words[1],words[2],words[3],words[4])
   val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{ByteArray(32){it.toByte()}}))
   var generator=drg
   val blinder=rsa.generateBlinder(pub.modulus){n->val pair=generator.randomBytesGenerate(n);generator=pair.second;pair.first}
   check((blinder.r*blinder.inverse).mod(pub.modulus)==BigInteger.ONE){"inverse property $count"}
   check(CryptoRsa.dp(blinder,key,cipher).contentEquals(plain)){"decryption property $count"}
   count++
  }}
  check(count==100)
  println("PASS ${count*2} original blinder property instances")
 }
}
