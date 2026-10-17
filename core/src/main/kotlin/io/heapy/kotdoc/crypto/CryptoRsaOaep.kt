// Crypto.PubKey.RSA.OAEP, crypton2.0.1; Vincent Hanquez.
// licenses/crypton-BSD-3-Clause.txt. No JVM timing guarantee is implied.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.X509RsaPublicKey
import io.heapy.kotdoc.x509.X509RsaPrivateKey

object CryptoRsaOaep {
    enum class Error { MessageSizeIncorrect,MessageTooLong,MessageNotRecognized,SignatureTooLong,InvalidParameters }
    sealed class Result {
        data class Failure(val error:Error):Result()
        class Success internal constructor(private val value:Lazy<ByteArray>):Result(){fun bytes()=value.value.copyOf()}
    }
    class Params(val hash:CryptoHmac.HashAlgorithm,val mask:CryptoMaskGen.Algorithm,val label:Lazy<ByteArray?> = lazyOf(null)){
        constructor(hash:CryptoHmac.HashAlgorithm,mask:CryptoMaskGen.Algorithm,label:ByteArray?):this(hash,mask,lazyOf(label?.copyOf()))
    }
    fun defaultParams(hash:CryptoHmac.HashAlgorithm)=Params(hash,CryptoMaskGen.mgf1(hash))
    private fun success(value:()->ByteArray)=Result.Success(cryptoRandomMemo(value))
    private fun xor(a:ByteArray,b:ByteArray)=ByteArray(minOf(a.size,b.size)){(a[it].toInt() xor b[it].toInt()).toByte()}
    private fun take(bytes:ByteArray,n:Int)=bytes.copyOfRange(0,n.coerceIn(0,bytes.size))
    private fun drop(bytes:ByteArray,n:Int)=bytes.copyOfRange(n.coerceIn(0,bytes.size),bytes.size)
    private fun equal(a:ByteArray,b:ByteArray):Boolean {if(a.size!=b.size)return false;var d=0;for(i in a.indices)d=d or(a[i].toInt() xor b[i].toInt());return d==0}
    fun encryptWithSeed(seed:ByteArray,params:Params,key:X509RsaPublicKey,message:ByteArray)=encryptWithSeedDeferred(lazyOf(seed.copyOf()),params,lazyOf(key),lazyOf(message.copyOf()))
    fun encryptWithSeedDeferred(seed:Lazy<ByteArray>,params:Params,key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>):Result {
        val k=key.value.size;val h=params.hash.digestSize.toLong()
        if(k<2*h+2)return Result.Failure(Error.InvalidParameters)
        if(seed.value.size.toLong()!=h)return Result.Failure(Error.InvalidParameters)
        val m=message.value
        if(m.size.toLong()>k-2*h-2)return Result.Failure(Error.MessageTooLong)
        return success {
            key.value.modulus;key.value.exponent
            CryptoRsa.epDeferred(key,cryptoRandomMemo {
            val labelHash=CryptoMaskGen.hash(params.hash,params.label.value?:ByteArray(0))
            val db=labelHash+ByteArray(Math.toIntExact(k-m.size-2*h-2))+byteArrayOf(1)+m
            val maskedDB=xor(db,params.mask.generate(seed.value,Math.toIntExact(k-h-1)))
            val maskedSeed=if(seed.value.isEmpty())ByteArray(0)else xor(seed.value,params.mask.generate(maskedDB,Math.toIntExact(h)))
            byteArrayOf(0)+maskedSeed+maskedDB
        }) }
    }
    /** Callback effects execute now, but the Either result remains deferred. */
    fun encryptDeferred(params:Params,key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>,random:(Int)->ByteArray):Lazy<Result>{
        return encryptIo(params,key,message){lazyOf(random(it))}
    }
    fun encryptIo(params:Params,key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>):Lazy<Result>{
        val seed=random(params.hash.digestSize)
        return cryptoRandomMemo{encryptWithSeedDeferred(seed,params,key,message)}
    }
    fun encrypt(params:Params,key:X509RsaPublicKey,message:ByteArray,random:(Int)->ByteArray)=encryptDeferred(params,lazyOf(key),lazyOf(message),random).value
    private fun unpad(params:Params,k:Int,input:Lazy<ByteArray>):Result {
        val expectedLabelHash=CryptoMaskGen.hash(params.hash,params.label.value?:ByteArray(0))
        val encoded=input.value
        val h=params.hash.digestSize
        val pb=take(encoded,1);val em0=drop(encoded,1);val maskedSeed=take(em0,h);val maskedDB=drop(em0,h)
        val seed=cryptoRandomMemo{if(maskedSeed.isEmpty())ByteArray(0)else xor(maskedSeed,params.mask.generate(maskedDB,h))}
        val db=if(maskedDB.isEmpty())ByteArray(0)else xor(maskedDB,params.mask.generate(seed.value,Math.toIntExact(k.toLong()-h-1)))
        val labelHash=take(db,h);val db1=drop(db,h)
        var index=db1.size;var unseen=1
        for(i in db1.indices){val w=db1[i].toInt() and 255;val nonZero=(w or -w) ushr 31;val found=-(unseen and nonZero);index=(i and found)or(index and found.inv());unseen=unseen and found.inv()}
        // Original strict and' demands all three tests, rather than short-circuiting.
        val labelOk=equal(labelHash,expectedLabelHash)
        val delimiterOk=equal(take(drop(db1,index),1),byteArrayOf(1))
        val prefixOk=equal(pb,byteArrayOf(0))
        return if(labelOk and delimiterOk and prefixOk)success{drop(db1,index+1)}else Result.Failure(Error.MessageNotRecognized)
    }
    fun decrypt(blinder:CryptoRsa.Blinder?,params:Params,key:X509RsaPrivateKey,cipher:ByteArray)=decryptDeferred(lazyOf(blinder),params,lazyOf(key),lazyOf(cipher))
    fun decryptDeferred(blinder:Lazy<CryptoRsa.Blinder?>,params:Params,key:Lazy<X509RsaPrivateKey>,cipher:Lazy<ByteArray>):Result {
        val c=cipher.value;val k=key.value.publicKey.size
        if(c.size.toLong()!=k)return Result.Failure(Error.MessageSizeIncorrect)
        if(CryptoSerialize.os2ip(c)>=key.value.publicKey.modulus)return Result.Failure(Error.MessageSizeIncorrect)
        if(k<2L*params.hash.digestSize+2)return Result.Failure(Error.InvalidParameters)
        return unpad(params,Math.toIntExact(k),cryptoRandomMemo{CryptoRsa.dpDeferred(blinder,key,lazyOf(c))})
    }
    fun decryptSaferDeferred(rsa:CryptoRsa,params:Params,key:Lazy<X509RsaPrivateKey>,cipher:Lazy<ByteArray>,random:(Int)->ByteArray):Lazy<Result>{
        return decryptSaferIo(rsa,params,key,cipher){lazyOf(random(it))}
    }
    private class CallbackState:CryptoDrg<CallbackState>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,CallbackState> = error("callback dictionary required")}
    fun decryptSaferIo(rsa:CryptoRsa,params:Params,key:Lazy<X509RsaPrivateKey>,cipher:Lazy<ByteArray>,random:(Int)->Lazy<ByteArray>):Lazy<Result>{
        // Force the state projection to execute IO callback effects; its returned
        // Blinder and each callback's ByteString result remain independently lazy.
        val ops=CryptoDrgOperations<CallbackState>{length,previous->previous.value;random(length) to lazyOf(CallbackState())}
        val pair=CryptoPseudoRandom.withDRG(CallbackState(),rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},ops))
        pair.second.value
        return cryptoRandomMemo{decryptDeferred(pair.first,params,key,cipher)}
    }
    fun decryptSafer(rsa:CryptoRsa,params:Params,key:X509RsaPrivateKey,cipher:ByteArray,random:(Int)->ByteArray)=decryptSaferDeferred(rsa,params,lazyOf(key),lazyOf(cipher),random).value
    fun <G:CryptoDrg<G>> encryptAction(params:Params,key:Lazy<X509RsaPublicKey>,message:Lazy<ByteArray>,operations:CryptoDrgOperations<G>)=CryptoPseudoRandom<G,Result>{generator->
        val draw=cryptoRandomMemo{operations.generate(params.hash.digestSize,generator)}
        cryptoRandomMemo{encryptWithSeedDeferred(cryptoRandomMemo{draw.value.first.value},params,key,message)} to cryptoRandomMemo{draw.value.second.value}
    }
    fun <G:CryptoDrg<G>> decryptSaferAction(rsa:CryptoRsa,params:Params,key:Lazy<X509RsaPrivateKey>,cipher:Lazy<ByteArray>,operations:CryptoDrgOperations<G>):CryptoPseudoRandom<G,Result> = rsa.generateBlinderAction(cryptoRandomMemo{key.value.publicKey.modulus},operations).map{decryptDeferred(it,params,key,cipher)}
}
