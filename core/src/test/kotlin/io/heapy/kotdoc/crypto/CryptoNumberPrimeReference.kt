package io.heapy.kotdoc.crypto
import java.math.BigInteger
import kotlinx.serialization.json.*
private fun String.bi()=BigInteger(this)
internal object CryptoNumberPrimeReference {
 private fun maybe(value:CryptoMaybe<BigInteger>)=when(value){is CryptoMaybe.Some->if(value.value.signum()<0)"Just (${value.value})"else"Just ${value.value}";CryptoMaybe.None->"Nothing"}
 fun assertRow(line:String) {
   val row=Json.parseToJsonElement(line).jsonObject
   val req=row.getValue("request").jsonPrimitive.content
   val fields=req.split(' '); val expected=row.getValue("value").jsonPrimitive.content
   val draws=mutableListOf<Pair<Int,List<Int>>>()
   val prime=CryptoNumberPrime(CryptoProbabilistic{row.getValue("secret").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()}){n,b->draws+=n to b.map{it.toInt()and 255}}
   val got=try {when(fields[0]) {
    "demandMR"->prime.primalityTestMillerRabinDeferred(lazy{if(fields[1]=="bottom")error("tries demanded")else fields[1].toInt()},lazy{if(fields[2]=="bottom")error("n demanded")else fields[2].bi()}).toString().replaceFirstChar{it.uppercase()}
    "demandFermat"->prime.primalityTestFermatDeferred(lazy{if(fields[1]=="bottom")error("tries demanded")else fields[1].toInt()},lazy{if(fields[2]=="bottom")error("a demanded")else fields[2].bi()},lazy{if(fields[3]=="bottom")error("p demanded")else fields[3].bi()}).toString().replaceFirstChar{it.uppercase()}
    "findTrace"->{val calls=mutableListOf<BigInteger>();val result=prime.findPrimeFromWithRounds(fields[1].toInt(),{calls+=it;it.mod(fields[3].bi())==fields[4].bi()},fields[2].bi());"($result,[${calls.joinToString(",")}])"}
    "probablyRounds"->prime.probablyPrime(fields[1].toInt(),fields[2].bi()).toString().replaceFirstChar{it.uppercase()}
    "findDrawn"->prime.findPrimeFromDrawn(fields[1].toInt(),fields[2].bi()).toString()
    "findRounds"->prime.findPrimeFromWithRounds(fields[1].toInt(),{it.mod(fields[3].bi())==fields[4].bi()},fields[2].bi()).toString()
    "divides"->prime.divides(fields[1].bi(),fields[2].bi()).toString().replaceFirstChar{it.uppercase()}
    "table"->CryptoNumberPrime.firstPrimes.joinToString(",","[","]")
    "compat"->"unsupported"
    "rounds"->CryptoNumberPrime.roundsForDrawn(fields[1].toInt()).toString()
    "naive"->prime.primalityTestNaive(fields[1].bi()).toString().replaceFirstChar{it.uppercase()}
    "fixedProbably"->prime.isProbablyPrime(fields[1].bi()).toString().replaceFirstChar{it.uppercase()}
    "fermat"->prime.primalityTestFermat(fields[1].toInt(),fields[2].bi(),fields[3].bi()).toString().replaceFirstChar{it.uppercase()}
    "coprime"->prime.isCoprime(fields[1].bi(),fields[2].bi()).toString().replaceFirstChar{it.uppercase()}
    "fixed","mr"->prime.primalityTestMillerRabin(fields[1].toInt(),fields[2].bi()).toString().replaceFirstChar{it.uppercase()}
    "fixedFind"->prime.findPrimeFrom(fields[1].bi()).toString()
    "findWith"->prime.findPrimeFromWith({it.mod(fields[2].bi())==fields[3].bi()},fields[1].bi()).toString()
    "wordsGenerate","wordsSafe","square"->{
     var drg=CryptoChaChaDrg.initializeWords(fields[2].toULong(),fields[3].toULong(),fields[4].toULong(),fields[5].toULong(),fields[6].toULong())
     val trace=mutableListOf<Int>();val random:(Int)->ByteArray={n->trace+=n;val r=drg.randomBytesGenerate(n);drg=r.second;r.first}
     val p=if(fields[0]=="wordsSafe")prime.generateSafePrime(fields[1].toInt(),random)else prime.generatePrime(fields[1].toInt(),random)
     val probe=drg.randomBytesGenerate(65).first.joinToString(","){(it.toInt()and 255).toString()}
     if(fields[0]=="square") {
      // g is an independently generated original QuickCheck choose observation.
      val g=expected.substringAfter(',').substringBefore(',').bi()
      val root=CryptoModular.squareRoot(p,g);val jacobi=CryptoModular.jacobi(g,p)
      check(when(val j=jacobi){is CryptoMaybe.Some->if(j.value==BigInteger.ONE) root is CryptoMaybe.Some && root.value*root.value%p==g else root==CryptoMaybe.None;CryptoMaybe.None->false})
      "($p,$g,${maybe(root)},${maybe(jacobi)},[${trace.joinToString(",")}],[$probe])"
     } else {
      check(p.bitLength()==fields[1].toInt());val independent=CryptoNumberPrime(CryptoProbabilistic{ByteArray(32){it.toByte()}});check(independent.isProbablyPrime(p));if(fields[0]=="wordsSafe")check(independent.isProbablyPrime((p-BigInteger.ONE)/BigInteger.TWO))
      "($p,[${trace.joinToString(",")}],[$probe])"
     }
    }
    "generate","safe"->{var drg=CryptoChaChaDrg.initialize(ByteArray(40){(fields[2].toInt()+it).toByte()});val trace=mutableListOf<Int>();val random:(Int)->ByteArray={n->trace+=n;val r=drg.randomBytesGenerate(n);drg=r.second;r.first};val value=if(fields[0]=="safe")prime.generateSafePrime(fields[1].toInt(),random)else prime.generatePrime(fields[1].toInt(),random);"($value,[${trace.joinToString(",")}],[${drg.randomBytesGenerate(65).first.joinToString(","){(it.toInt()and 255).toString()}}])"}
    else->error(req)
   }}catch(e:Exception){"error:${e.message}"}
   if(got!=expected)error("$req expected $expected got $got")
   if(row.getValue("draws") is JsonArray) {val expectedDraws=row.getValue("draws").jsonArray.map{val a=it.jsonArray;a[0].jsonPrimitive.int to a[1].jsonArray.map{b->b.jsonPrimitive.int}};check(draws==expectedDraws){"draws $req expected $expectedDraws got $draws"}}
 }
}
