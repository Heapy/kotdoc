// Crypto.PubKey.RSA.PKCS15, crypton 2.0.1; Vincent Hanquez.
// licenses/crypton-BSD-3-Clause.txt. No JVM timing claim is implied.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.X509RsaPublicKey
import io.heapy.kotdoc.x509.X509RsaPrivateKey
import io.heapy.kotdoc.crypto.CryptoRsaOaep.Error

object CryptoRsaPkcs15 {
 sealed class Result {
  data class Failure(val error:Error):Result()
  class Success internal constructor(private val payload:Lazy<ByteArray>):Result(){fun bytes()=payload.value.copyOf()}
 }
 class HashAlgorithmASN1(val hash:CryptoHmac.HashAlgorithm,val digestASN1:(Lazy<ByteArray>)->Lazy<ByteArray>,val witness:Lazy<Any?> = lazyOf(Unit))
 enum class Algorithm {MD2,MD5,SHA1,SHA224,SHA256,SHA384,SHA512,SHA512t_224,SHA512t_256,SHA3_224,SHA3_256,SHA3_384,SHA3_512,RIPEMD160}
 private val prefixes=mapOf(Algorithm.MD2 to byteArrayOf(48,32,48,12,6,8,42,134.toByte(),72,134.toByte(),247.toByte(),13,2,2,5,0,4,16),
Algorithm.MD5 to byteArrayOf(48,32,48,12,6,8,42,134.toByte(),72,134.toByte(),247.toByte(),13,2,5,5,0,4,16),
Algorithm.SHA1 to byteArrayOf(48,33,48,9,6,5,43,14,3,2,26,5,0,4,20),
Algorithm.SHA224 to byteArrayOf(48,45,48,13,6,9,96,134.toByte(),72,1,101,3,4,2,4,5,0,4,28),
Algorithm.SHA256 to byteArrayOf(48,49,48,13,6,9,96,134.toByte(),72,1,101,3,4,2,1,5,0,4,32),
Algorithm.SHA384 to byteArrayOf(48,65,48,13,6,9,96,134.toByte(),72,1,101,3,4,2,2,5,0,4,48),
Algorithm.SHA512 to byteArrayOf(48,81,48,13,6,9,96,134.toByte(),72,1,101,3,4,2,3,5,0,4,64),
Algorithm.SHA512t_224 to byteArrayOf(48,45,48,13,6,9,96,134.toByte(),72,1,101,3,4,2,5,5,0,4,28),
Algorithm.SHA512t_256 to byteArrayOf(48,49,48,13,6,9,96,134.toByte(),72,1,101,3,4,2,6,5,0,4,32),
Algorithm.SHA3_224 to byteArrayOf(48,43,48,11,6,9,96,134.toByte(),72,1,101,3,4,2,7,4,28),
Algorithm.SHA3_256 to byteArrayOf(48,47,48,11,6,9,96,134.toByte(),72,1,101,3,4,2,8,4,32),
Algorithm.SHA3_384 to byteArrayOf(48,63,48,11,6,9,96,134.toByte(),72,1,101,3,4,2,9,4,48),
Algorithm.SHA3_512 to byteArrayOf(48,79,48,11,6,9,96,134.toByte(),72,1,101,3,4,2,10,4,64),
Algorithm.RIPEMD160 to byteArrayOf(48,33,48,9,6,5,43,36,3,2,1,5,0,4,20))
 fun algorithm(a:Algorithm):HashAlgorithmASN1 {
  val hash=when(a){
   Algorithm.MD2->object:CryptoHmac.HashAlgorithm{
    override val blockSize=16;override val digestSize=16;override val contextSize=96
    private fun c(b:ByteArray)=CryptoMd2.Context(CryptoMd2.Algorithm.MD2,b)
    override fun initialize(context:ByteArray)=CryptoMd2.init(c(context))
    override fun update(context:ByteArray,bytes:ByteArray)=CryptoMd2.update(c(context),CryptoByteSpan(bytes))
    override fun finalize(context:ByteArray,output:ByteArray)=CryptoMd2.finalize(c(context),CryptoByteSpan(output))
   }
   Algorithm.RIPEMD160->object:CryptoHmac.HashAlgorithm{
    override val blockSize=64;override val digestSize=20;override val contextSize=128
    private fun c(b:ByteArray)=CryptoRipemd160.Context(CryptoRipemd160.Algorithm.RIPEMD160,b)
    override fun initialize(context:ByteArray)=CryptoRipemd160.init(c(context))
    override fun update(context:ByteArray,bytes:ByteArray)=CryptoRipemd160.update(c(context),CryptoByteSpan(bytes))
    override fun finalize(context:ByteArray,output:ByteArray)=CryptoRipemd160.finalize(c(context),CryptoByteSpan(output))
   }
   else->CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.valueOf(a.name))
  }
  return HashAlgorithmASN1(hash,{digest->cryptoRandomMemo{prefixes.getValue(a)+digest.value}})
 }
 private fun success(payload:()->ByteArray)=Result.Success(cryptoRandomMemo(payload))
 fun padSignature(length:Long,signature:ByteArray)=padSignatureDeferred(lazyOf(length),lazyOf(signature.copyOf()))
 fun padSignatureDeferred(length:Lazy<Long>,signature:Lazy<ByteArray>):Result {
  val k=length.value;val s=signature.value
  if(k<s.size.toLong()+11)return Result.Failure(Error.SignatureTooLong)
  return success{byteArrayOf(0,1)+ByteArray(Math.toIntExact(k-s.size-3)){255.toByte()}+byteArrayOf(0)+s}
 }
 fun unpad(input:ByteArray):Result {
  val packed=input.copyOf()
  var zeroIndex=packed.size;var unseen=1
  for(i in 2..<packed.size){val w=packed[i].toInt()and 255;val nonZero=(w or -w)ushr 31;val found=-(unseen and nonZero.inv());zeroIndex=(i and found)or(zeroIndex and found.inv());unseen=unseen and found.inv()}
  val prefixOk=packed.size>=2 && packed[0]==0.toByte()&&packed[1]==2.toByte()
  val delimiterOk=zeroIndex<packed.size;val minimumOk=zeroIndex-2>=8
  return if(prefixOk and delimiterOk and minimumOk)success{packed.copyOfRange(zeroIndex+1,packed.size)}else Result.Failure(Error.MessageNotRecognized)
 }
 fun pad(length:Long,message:ByteArray,random:(Int)->ByteArray)=padIo(lazyOf(length),lazyOf(message.copyOf())){lazyOf(random(it))}.value
 fun padIo(length:Lazy<Long>,message:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>):Lazy<Result>{
  val m=message.value;val k=length.value
  if(m.size.toLong()>k-11)return lazyOf(Result.Failure(Error.MessageTooLong))
  val chunks=mutableListOf<ByteArray>();var remaining=k-m.size-3
  while(true){val raw=random(Math.toIntExact(remaining)).value;val bytes=raw.filter{it!=0.toByte()}.toByteArray();chunks+=bytes;remaining-=bytes.size;if(remaining==0L)break}
  return lazyOf(success{byteArrayOf(0,2)+chunks.fold(ByteArray(0)){x,y->x+y}+byteArrayOf(0)+m})
 }
 fun encryptIo(key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>):Lazy<Result>{
  val padded=padIo(cryptoRandomMemo{key.value.size},message,random)
  return cryptoRandomMemo{when(val r=padded.value){is Result.Failure->r;is Result.Success->success{CryptoRsa.epDeferred(key,cryptoRandomMemo{r.bytes()})}}}
 }
 fun encrypt(key:X509RsaPublicKey,message:ByteArray,random:(Int)->ByteArray)=encryptIo(lazyOf(key),lazyOf(message.copyOf())){lazyOf(random(it))}.value
 fun decrypt(blinder:CryptoRsa.Blinder?,key:X509RsaPrivateKey,cipher:ByteArray)=decryptDeferred(lazyOf(blinder),lazyOf(key),lazyOf(cipher.copyOf()))
 fun decryptDeferred(blinder:Lazy<CryptoRsa.Blinder?>,key:Lazy<X509RsaPrivateKey>,cipher:Lazy<ByteArray>):Result {
  val privateKey=key.value;val c=cipher.value;val k=privateKey.publicKey.size
  if(c.size.toLong()!=k)return Result.Failure(Error.MessageSizeIncorrect)
  if(CryptoSerialize.os2ip(c)>=key.value.publicKey.modulus)return Result.Failure(Error.MessageSizeIncorrect)
  return unpad(CryptoRsa.dpDeferred(blinder,key,lazyOf(c)))
 }
 internal fun makeSignature(hash:Lazy<HashAlgorithmASN1?>,length:Lazy<Long>,message:Lazy<ByteArray>):Result {
  val algorithm=hash.value
  val encoded=if(algorithm==null)message else cryptoRandomMemo{algorithm.digestASN1(cryptoRandomMemo{CryptoMaskGen.hash(algorithm.hash,message.value)}).value}
  return padSignatureDeferred(length,encoded)
 }
 fun sign(blinder:CryptoRsa.Blinder?,hash:HashAlgorithmASN1?,key:X509RsaPrivateKey,message:ByteArray)=signDeferred(lazyOf(blinder),lazyOf(hash),lazyOf(key),lazyOf(message.copyOf()))
 fun signDeferred(blinder:Lazy<CryptoRsa.Blinder?>,hash:Lazy<HashAlgorithmASN1?>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>):Result {
  // Selected original worker demands the PrivateKey root before the Maybe hash,
  // while the public_size field remains deferred until padSignature.
  val privateKey=key.value
  return when(val r=makeSignature(hash,cryptoRandomMemo{privateKey.publicKey.size},message)){
  is Result.Failure->r
  is Result.Success->success{CryptoRsa.dpDeferred(blinder,key,cryptoRandomMemo{r.bytes()})}
 }
 }
 fun verify(hash:HashAlgorithmASN1?,key:X509RsaPublicKey,message:ByteArray,signature:ByteArray)=verifyDeferred(lazyOf(hash),lazyOf(key),lazyOf(message.copyOf()),lazyOf(signature.copyOf()))
 fun verifyDeferred(hash:Lazy<HashAlgorithmASN1?>,key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>,signature:Lazy<ByteArray>):Boolean {
  val publicKey=key.value;val s=signature.value;val k=publicKey.size
  if(s.size.toLong()!=k)return false
  if(CryptoSerialize.os2ip(s)>=key.value.modulus)return false
  return when(val r=makeSignature(hash,lazyOf(k),message)){is Result.Failure->false;is Result.Success->r.bytes().contentEquals(CryptoRsa.epDeferred(key,lazyOf(s)))}
 }
 private class CallbackState:CryptoDrg<CallbackState>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,CallbackState> = error("callback dictionary required")}
 private fun blinderIo(rsa:CryptoRsa,key:Lazy<X509RsaPrivateKey>,random:(Int)->Lazy<ByteArray>):Lazy<CryptoRsa.Blinder>{
  val ops=CryptoDrgOperations<CallbackState>{length,previous->previous.value;random(length) to lazyOf(CallbackState())}
  val pair=CryptoPseudoRandom.withDRG(CallbackState(),rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},ops));pair.second.value;return pair.first
 }
 fun decryptSaferIo(rsa:CryptoRsa,key:Lazy<X509RsaPrivateKey>,cipher:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>):Lazy<Result>{val b=blinderIo(rsa,key,random);return cryptoRandomMemo{decryptDeferred(b,key,cipher)}}
 fun signSaferIo(rsa:CryptoRsa,hash:Lazy<HashAlgorithmASN1?>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>):Lazy<Result>{val b=blinderIo(rsa,key,random);return cryptoRandomMemo{signDeferred(b,hash,key,message)}}
 fun decryptSafer(rsa:CryptoRsa,key:X509RsaPrivateKey,cipher:ByteArray,random:(Int)->ByteArray)=decryptSaferIo(rsa,lazyOf(key),lazyOf(cipher.copyOf())){lazyOf(random(it))}.value
 fun signSafer(rsa:CryptoRsa,hash:HashAlgorithmASN1?,key:X509RsaPrivateKey,message:ByteArray,random:(Int)->ByteArray)=signSaferIo(rsa,lazyOf(hash),lazyOf(key),lazyOf(message.copyOf())){lazyOf(random(it))}.value
 fun <G:CryptoDrg<G>> padAction(length:Lazy<Long>,message:Lazy<ByteArray>,operations:CryptoDrgOperations<G>)=CryptoPseudoRandom<G,Result>{initial->
  // Pure State can expose Right without demanding the padding action's pair root.
  // Its payload and successor state share the same iterative draw computation.
  val guard=cryptoRandomMemo{val m=message.value;val k=length.value;Triple(k,m,m.size.toLong()>k-11)}
  val outcome=cryptoRandomMemo{
   var state=initial
   val(k,m,_)=guard.value
   val value=padIo(lazyOf(k),lazyOf(m)){n->val previous=state;val draw=cryptoRandomMemo{operations.generate(n,previous)};state=cryptoRandomMemo{draw.value.second.value};cryptoRandomMemo{draw.value.first.value}}
   value to state
  }
  cryptoRandomMemo{if(guard.value.third)Result.Failure(Error.MessageTooLong)else success{when(val r=outcome.value.first.value){is Result.Success->r.bytes();is Result.Failure->error("unreachable padding guard")}}} to cryptoRandomMemo{if(guard.value.third)initial.value else outcome.value.second.value}
 }
 fun <G:CryptoDrg<G>> encryptAction(key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>,operations:CryptoDrgOperations<G>)=padAction(cryptoRandomMemo{key.value.size},message,operations).map{padded->when(val r=padded.value){is Result.Failure->r;is Result.Success->success{CryptoRsa.epDeferred(key,cryptoRandomMemo{r.bytes()})}}}
 fun <G:CryptoDrg<G>> decryptSaferAction(rsa:CryptoRsa,key:Lazy<X509RsaPrivateKey>,cipher:Lazy<ByteArray>,operations:CryptoDrgOperations<G>)=rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},operations).map{decryptDeferred(it,key,cipher)}
 fun <G:CryptoDrg<G>> signSaferAction(rsa:CryptoRsa,hash:Lazy<HashAlgorithmASN1?>,key:Lazy<X509RsaPrivateKey>,message:Lazy<ByteArray>,operations:CryptoDrgOperations<G>)=rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},operations).map{signDeferred(it,hash,key,message)}
}
