// Crypto.PubKey.RSA.PSS, crypton2.0.1; Vincent Hanquez.
// Full notice licenses/crypton-BSD-3-Clause.txt. No JVM timing claim.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.X509RsaPublicKey
import io.heapy.kotdoc.x509.X509RsaPrivateKey
import io.heapy.kotdoc.crypto.CryptoRsaOaep.Error
object CryptoRsaPss {
 sealed class Result {
  data class Failure(val error:Error):Result()
  class Success internal constructor(private val value:Lazy<ByteArray>):Result(){fun bytes()=value.value.copyOf()}
 }
 fun interface MaskAlgorithm {fun generate(seed:Lazy<ByteArray>,length:Lazy<Long>):Lazy<ByteArray>}
 private fun defaultMask(hash:CryptoHmac.HashAlgorithm)=MaskAlgorithm{seed,length->CryptoMaskGen.mgf1Deferred(hash,seed,cryptoRandomMemo{Math.toIntExact(length.value)})}
 class Dictionary(val engine:CryptoHmac.HashAlgorithm,val digestSize:(Lazy<Any?>)->Long = {engine.digestSize.toLong()})
 class Params(val dictionary:Dictionary,val mask:Lazy<MaskAlgorithm>,val saltLength:Lazy<Long>,val trailer:Lazy<Byte>,val witness:Lazy<Any?> = lazyOf(Unit)) {
  constructor(hash:CryptoHmac.HashAlgorithm,mask:Lazy<MaskAlgorithm>,saltLength:Lazy<Long>,trailer:Lazy<Byte>,witness:Lazy<Any?> = lazyOf(Unit)):this(Dictionary(hash),mask,saltLength,trailer,witness)
  constructor(hash:CryptoHmac.HashAlgorithm,mask:CryptoMaskGen.Algorithm,saltLength:Long,trailer:Byte):this(Dictionary(hash),lazyOf(MaskAlgorithm{seed,length->cryptoRandomMemo{mask.generate(seed.value,Math.toIntExact(length.value))}}),lazyOf(saltLength),lazyOf(trailer))
 }
 fun defaultParams(dictionary:Dictionary,witness:Lazy<Any?> = lazyOf(Unit))=Params(dictionary,cryptoRandomMemo{defaultMask(dictionary.engine)},cryptoRandomMemo{dictionary.digestSize(witness)},lazyOf(0xbc.toByte()),witness)
 fun defaultParams(hash:CryptoHmac.HashAlgorithm)=defaultParams(Dictionary(hash))
 fun defaultParamsSHA1()=defaultParams(CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1))
 private fun success(block:()->ByteArray)=Result.Success(cryptoRandomMemo(block))
 internal fun emTruncate(bits:Long)=((bits-1)and 7)==0L
 internal fun normalizeToKeySize(bits:Long,bytes:ByteArray):ByteArray=bytes.copyOf().also{if(it.isNotEmpty()){val sh=((bits-1)and 7).toInt();val mask=if(sh>0)255 ushr(8-sh)else 255;it[0]=(it[0].toInt()and mask).toByte()}}
 private fun take(b:ByteArray,n:Long)=b.copyOfRange(0,n.coerceIn(0,b.size.toLong()).toInt())
 private fun drop(b:ByteArray,n:Long)=b.copyOfRange(n.coerceIn(0,b.size.toLong()).toInt(),b.size)
 private fun xor(a:ByteArray,b:ByteArray)=ByteArray(minOf(a.size,b.size)){(a[it].toInt()xor b[it].toInt()).toByte()}
 private fun equal(a:ByteArray,b:ByteArray):Boolean{if(a.size!=b.size)return false;var d=0;for(i in a.indices)d=d or(a[i].toInt()xor b[i].toInt());return d==0}
 fun signDigestWithSalt(salt:ByteArray,blinder:CryptoRsa.Blinder?,params:Params,key:X509RsaPrivateKey,digest:ByteArray)=signDigestWithSaltDeferred(params.dictionary,lazyOf(salt.copyOf()),lazyOf(blinder),lazyOf(params),lazyOf(key),lazyOf(digest.copyOf()))
 fun signDigestWithSaltDeferred(dictionary:Dictionary,salt:Lazy<ByteArray>,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>):Result {
  val s=salt.value;val hLen=dictionary.digestSize(cryptoRandomMemo{params.value.witness.value})
  val pk=key.value;val bits=CryptoNumberBasic.numBits(pk.publicKey.modulus).toLong();val k=pk.publicKey.size;val emLen=if(emTruncate(bits))k-1 else k
  if(emLen<hLen+s.size+2)return Result.Failure(Error.InvalidParameters)
  return success{
   CryptoRsa.dpDeferred(blinder,key,cryptoRandomMemo{
    val h=cryptoRandomMemo{CryptoMaskGen.hash(dictionary.engine,ByteArray(8)+digest.value+s)};val dbLen=emLen-hLen-1
    val db=ByteArray(Math.toIntExact((dbLen-s.size-1).coerceAtLeast(0)))+byteArrayOf(1)+s
    normalizeToKeySize(bits,xor(db,params.value.mask.value.generate(h,lazyOf(dbLen)).value))+h.value+byteArrayOf(params.value.trailer.value)
   })
  }
 }
 fun signWithSalt(salt:ByteArray,blinder:CryptoRsa.Blinder?,params:Params,key:X509RsaPrivateKey,message:ByteArray)=signWithSaltDeferred(params.dictionary,lazyOf(salt.copyOf()),lazyOf(blinder),lazyOf(params),lazyOf(key),lazyOf(message.copyOf()))
 fun signWithSaltDeferred(dictionary:Dictionary,salt:Lazy<ByteArray>,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>)=signDigestWithSaltDeferred(dictionary,salt,blinder,params,key,cryptoRandomMemo{CryptoMaskGen.hash(dictionary.engine,message.value)})
 fun verify(params:Params,key:X509RsaPublicKey,message:ByteArray,signature:ByteArray)=verifyDeferred(params.dictionary,lazyOf(params),lazyOf(key),lazyOf(message.copyOf()),lazyOf(signature.copyOf()))
 fun verifyDeferred(dictionary:Dictionary,params:Lazy<Params>,key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>,signature:Lazy<ByteArray>)=verifyDigestDeferred(dictionary,params,key,cryptoRandomMemo{CryptoMaskGen.hash(dictionary.engine,message.value)},signature)
 fun verifyDigest(params:Params,key:X509RsaPublicKey,digest:ByteArray,signature:ByteArray)=verifyDigestDeferred(params.dictionary,lazyOf(params),lazyOf(key),lazyOf(digest.copyOf()),lazyOf(signature.copyOf()))
 fun verifyDigestDeferred(dictionary:Dictionary,params:Lazy<Params>,key:Lazy<X509RsaPublicKey>,digest:Lazy<ByteArray>,signature:Lazy<ByteArray>):Boolean {
  val pk=key.value;val s=signature.value;val k=pk.size
  if(s.size.toLong()!=k)return false
  if(CryptoSerialize.os2ip(s)>=pk.modulus)return false
  val bits=CryptoNumberBasic.numBits(pk.modulus).toLong();val emLen=if(emTruncate(bits))k-1 else k;val encoded=CryptoRsa.epDeferred(key,lazyOf(s));val pre=take(encoded,k-emLen);val em=drop(encoded,k-emLen)
  if(pre.any{it!=0.toByte()})return false
  if(em.isEmpty())error("Data.ByteString.last: empty ByteString")
  if(em.last()!=params.value.trailer.value)return false
  val p=params.value;val hLen=dictionary.digestSize(cryptoRandomMemo{params.value.witness.value});val dbLen=emLen-hLen-1;val masked=take(em,dbLen);val h=take(drop(em,masked.size.toLong()),hLen)
  val db=if(masked.isEmpty())ByteArray(0)else normalizeToKeySize(bits,xor(masked,p.mask.value.generate(lazyOf(h),lazyOf(dbLen)).value));val first=db.indexOf(1.toByte());val split=if(first<0)db.size else first
  if(db.take(split).any{it!=0.toByte()})return false
  val z=drop(db,split.toLong());if(!take(z,1).contentEquals(byteArrayOf(1)))return false
  val salt=drop(z,1);if(p.saltLength.value!=salt.size.toLong())return false
  return equal(h,CryptoMaskGen.hash(dictionary.engine,ByteArray(8)+digest.value+salt))
 }
 fun interface SourceRandomCallback {fun generate(length:Lazy<Long>):Lazy<ByteArray>}
 fun interface SourceRandomOperations<G:CryptoDrg<G>> {fun generate(length:Lazy<Long>,state:Lazy<G>):Pair<Lazy<ByteArray>,Lazy<G>>}
 fun <G:CryptoDrg<G>> sourceOperations(ops:CryptoDrgOperations<G>)=SourceRandomOperations<G>{length,state->ops.generate(Math.toIntExact(length.value),state)}
 private fun <G:CryptoDrg<G>> bridge(ops:SourceRandomOperations<G>)=CryptoDrgOperations<G>{length,state->ops.generate(lazyOf(length.toLong()),state)}
 fun signSourceIo(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,random:SourceRandomCallback):Lazy<Result>{val salt=random.generate(cryptoRandomMemo{params.value.saltLength.value});return cryptoRandomMemo{signWithSaltDeferred(dictionary,salt,blinder,params,key,message)}}
 fun signDigestSourceIo(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,random:SourceRandomCallback):Lazy<Result>{val salt=random.generate(cryptoRandomMemo{params.value.saltLength.value});return cryptoRandomMemo{signDigestWithSaltDeferred(dictionary,salt,blinder,params,key,digest)}}
 fun signIo(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>)=signSourceIo(dictionary,blinder,params,key,message,SourceRandomCallback{n->random(Math.toIntExact(n.value))})
 fun signDigestIo(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>)=signDigestSourceIo(dictionary,blinder,params,key,digest,SourceRandomCallback{n->random(Math.toIntExact(n.value))})
 fun sign(blinder:CryptoRsa.Blinder?,params:Params,key:X509RsaPrivateKey,message:ByteArray,random:(Int)->ByteArray)=signIo(params.dictionary,lazyOf(blinder),lazyOf(params),lazyOf(key),lazyOf(message.copyOf())){lazyOf(random(it))}.value
 fun signDigest(blinder:CryptoRsa.Blinder?,params:Params,key:X509RsaPrivateKey,digest:ByteArray,random:(Int)->ByteArray)=signDigestIo(params.dictionary,lazyOf(blinder),lazyOf(params),lazyOf(key),lazyOf(digest.copyOf())){lazyOf(random(it))}.value
 private fun <G:CryptoDrg<G>> saltAction(params:Lazy<Params>,ops:SourceRandomOperations<G>)=CryptoPseudoRandom<G,ByteArray>{state->
  val draw=cryptoRandomMemo{ops.generate(cryptoRandomMemo{params.value.saltLength.value},state)}
  cryptoRandomMemo{draw.value.first.value} to cryptoRandomMemo{draw.value.second.value}
 }
 fun <G:CryptoDrg<G>> signSourceAction(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,ops:SourceRandomOperations<G>)=saltAction(params,ops).map{signWithSaltDeferred(dictionary,it,blinder,params,key,message)}
 fun <G:CryptoDrg<G>> signDigestSourceAction(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,ops:SourceRandomOperations<G>)=saltAction(params,ops).map{signDigestWithSaltDeferred(dictionary,it,blinder,params,key,digest)}
 fun <G:CryptoDrg<G>> signAction(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,ops:CryptoDrgOperations<G>)=signSourceAction(dictionary,blinder,params,key,message,sourceOperations(ops))
 fun <G:CryptoDrg<G>> signDigestAction(dictionary:Dictionary,blinder:Lazy<CryptoRsa.Blinder?>,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,ops:CryptoDrgOperations<G>)=signDigestSourceAction(dictionary,blinder,params,key,digest,sourceOperations(ops))
 fun <G:CryptoDrg<G>> signSaferSourceAction(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,ops:SourceRandomOperations<G>)=rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},bridge(ops)).flatMap{signSourceAction(dictionary,it,params,key,message,ops)}
 fun <G:CryptoDrg<G>> signDigestSaferSourceAction(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,ops:SourceRandomOperations<G>)=rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},bridge(ops)).flatMap{signDigestSourceAction(dictionary,it,params,key,digest,ops)}
 fun <G:CryptoDrg<G>> signSaferAction(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,ops:CryptoDrgOperations<G>)=signSaferSourceAction(dictionary,rsa,params,key,message,sourceOperations(ops))
 fun <G:CryptoDrg<G>> signDigestSaferAction(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,ops:CryptoDrgOperations<G>)=signDigestSaferSourceAction(dictionary,rsa,params,key,digest,sourceOperations(ops))
 private class CallbackState:CryptoDrg<CallbackState>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,CallbackState> = error("callback dictionary required")}
 private fun blinderIo(rsa:CryptoRsa,key:Lazy<X509RsaPrivateKey>,random:SourceRandomCallback):Lazy<CryptoRsa.Blinder>{val ops=CryptoDrgOperations<CallbackState>{n,previous->previous.value;random.generate(lazyOf(n.toLong()))to lazyOf(CallbackState())};val pair=CryptoPseudoRandom.withDRG(CallbackState(),rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},ops));pair.second.value;return pair.first}
 fun signSaferSourceIo(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,random:SourceRandomCallback):Lazy<Result>{val b=blinderIo(rsa,key,random);return signSourceIo(dictionary,b,params,key,message,random)}
 fun signDigestSaferSourceIo(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,random:SourceRandomCallback):Lazy<Result>{val b=blinderIo(rsa,key,random);return signDigestSourceIo(dictionary,b,params,key,digest,random)}
 fun signSaferIo(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>)=signSaferSourceIo(dictionary,rsa,params,key,message,SourceRandomCallback{n->random(Math.toIntExact(n.value))})
 fun signDigestSaferIo(dictionary:Dictionary,rsa:CryptoRsa,params:Lazy<Params>,key:Lazy<X509RsaPrivateKey>,digest:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>)=signDigestSaferSourceIo(dictionary,rsa,params,key,digest,SourceRandomCallback{n->random(Math.toIntExact(n.value))})
 fun signSafer(rsa:CryptoRsa,params:Params,key:X509RsaPrivateKey,message:ByteArray,random:(Int)->ByteArray)=signSaferIo(params.dictionary,rsa,lazyOf(params),lazyOf(key),lazyOf(message.copyOf())){lazyOf(random(it))}.value
 fun signDigestSafer(rsa:CryptoRsa,params:Params,key:X509RsaPrivateKey,digest:ByteArray,random:(Int)->ByteArray)=signDigestSaferIo(params.dictionary,rsa,lazyOf(params),lazyOf(key),lazyOf(digest.copyOf())){lazyOf(random(it))}.value
}
