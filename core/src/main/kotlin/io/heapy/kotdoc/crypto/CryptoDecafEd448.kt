/** Translated from Decaf ed448goldilocks/eddsa.c by Mike Hamburg.
 * Copyright (c) 2015-2016 Cryptography Research, Inc.
 * MIT License; see licenses/decaf-MIT.txt (archive lacks referenced LICENSE.txt).
 */
package io.heapy.kotdoc.crypto
internal class CryptoDecafEd448(bits:Int,scalarBits:Int=bits,vectorMasks32:Boolean=false) {
 private val p=CryptoDecaf448Points(bits,scalarBits,vectorMasks32)
 private val multiplication=CryptoDecaf448PointMultiplication(p)
 private val scalar=CryptoDecaf448Scalar(scalarBits)
 private fun fresh()=CryptoDecaf448Scalar.Region(LongArray(scalar.limbs))
 private fun span(a:ByteArray)=CryptoByteSpan(a)
 private fun bytes(a:CryptoByteSpan,n:Int=a.size)=a.data.copyOfRange(a.offset,a.offset+n)
 private fun hash(out:ByteArray,input:CryptoByteSpan)=CryptoDecafShake256.hash(span(out),input)
 fun clamp(secret:CryptoByteSpan){secret.data[secret.offset]=(secret.data[secret.offset].toInt()and 252).toByte();secret.data[secret.offset+56]=0;secret.data[secret.offset+55]=(secret.data[secret.offset+55].toInt()or 128).toByte()}
 fun hashInitWithDom(ctx:CryptoSha3.Context,prehashed:Int,forPrehash:Int,context:CryptoByteSpan){CryptoDecafShake256.init(ctx);CryptoDecafShake256.update(ctx,span("SigEd448".toByteArray(Charsets.US_ASCII)));CryptoDecafShake256.update(ctx,span(byteArrayOf((2+(if(prehashed==0)-1 else 0)+(if(forPrehash==0)-1 else 0)).toByte(),context.size.toByte())));CryptoDecafShake256.update(ctx,context)}
 fun prehashInit(ctx:CryptoSha3.Context)=CryptoDecafShake256.init(ctx)
 fun prehashUpdate(ctx:CryptoSha3.Context,message:CryptoByteSpan)=CryptoDecafShake256.update(ctx,message)
 fun prehashDestroy(ctx:CryptoSha3.Context)=CryptoDecafShake256.destroy(ctx)
 fun convertPrivateKeyToX448(out:CryptoByteSpan,secret:CryptoByteSpan)=CryptoDecafShake256.hash(CryptoByteSpan(out.data,out.offset,56),CryptoByteSpan(secret.data,secret.offset,57))
 fun derivePublicKey(out:CryptoByteSpan,secret:CryptoByteSpan){val expanded=ByteArray(57);hash(expanded,CryptoByteSpan(secret.data,secret.offset,57));clamp(span(expanded));val s=fresh();scalar.decodeLong(s,span(expanded));repeat(2){scalar.halve(s,s)};val point=p.freshShape();multiplication.precomputedScalarmul(point,multiplication.baseTable(),s);p.encodeEd(out,point);scalar.destroy(s);p.destroy(point);expanded.fill(0)}
 fun signInternal(signature:CryptoByteSpan,secret:CryptoByteSpan,public:CryptoByteSpan,message:CryptoByteSpan,prehashed:Int,context:CryptoByteSpan){
  val expanded=ByteArray(114);hash(expanded,CryptoByteSpan(secret.data,secret.offset,57));clamp(span(expanded));val secretScalar=fresh();scalar.decodeLong(secretScalar,CryptoByteSpan(expanded,0,57));val ctx=CryptoSha3.Context(ByteArray(344));hashInitWithDom(ctx,prehashed,0,context);CryptoDecafShake256.update(ctx,CryptoByteSpan(expanded,57,57));CryptoDecafShake256.update(ctx,message);expanded.fill(0)
  val nonce=ByteArray(114);CryptoDecafShake256.final(ctx,span(nonce));val nonceScalar=fresh();scalar.decodeLong(nonceScalar,span(nonce));nonce.fill(0)
  val noncePoint=ByteArray(57);val half=fresh();scalar.halve(half,nonceScalar);scalar.halve(half,half);val point=p.freshShape();multiplication.precomputedScalarmul(point,multiplication.baseTable(),half);p.encodeEd(span(noncePoint),point);p.destroy(point);scalar.destroy(half)
  hashInitWithDom(ctx,prehashed,0,context);CryptoDecafShake256.update(ctx,span(noncePoint));CryptoDecafShake256.update(ctx,CryptoByteSpan(public.data,public.offset,57));CryptoDecafShake256.update(ctx,message);val challenge=ByteArray(114);CryptoDecafShake256.final(ctx,span(challenge));CryptoDecafShake256.destroy(ctx);val challengeScalar=fresh();scalar.decodeLong(challengeScalar,span(challenge));challenge.fill(0);scalar.multiply(challengeScalar,challengeScalar,secretScalar);scalar.add(challengeScalar,challengeScalar,nonceScalar)
  signature.data.fill(0,signature.offset,signature.offset+114);noncePoint.copyInto(signature.data,signature.offset);scalar.encode(CryptoByteSpan(signature.data,signature.offset+57,56),challengeScalar);scalar.destroy(secretScalar);scalar.destroy(nonceScalar);scalar.destroy(challengeScalar)
 }
 fun sign(signature:CryptoByteSpan,secret:CryptoByteSpan,public:CryptoByteSpan,message:CryptoByteSpan,prehashed:Int,context:CryptoByteSpan)=signInternal(signature,secret,public,message,prehashed,context)
 private fun prehashOutput(ctx:CryptoSha3.Context):ByteArray {val copy=CryptoSha3.Context(ctx.data.copyOfRange(ctx.offset,ctx.offset+344));val out=ByteArray(64);CryptoDecafShake256.final(copy,span(out));CryptoDecafShake256.destroy(copy);return out}
 fun signPrehash(signature:CryptoByteSpan,secret:CryptoByteSpan,public:CryptoByteSpan,ctx:CryptoSha3.Context,context:CryptoByteSpan){val output=prehashOutput(ctx);val actual=ByteArray(57);derivePublicKey(span(actual),secret);check(actual.contentEquals(bytes(public,57))){"original abort: mismatched Ed448 public key"};signInternal(signature,secret,span(actual),span(output),1,context);output.fill(0)}
 fun deriveKeypair(keypair:CryptoByteSpan,secret:CryptoByteSpan){secret.data.copyInto(keypair.data,keypair.offset,secret.offset,secret.offset+57);derivePublicKey(CryptoByteSpan(keypair.data,keypair.offset+57,57),CryptoByteSpan(keypair.data,keypair.offset,57))}
 fun keypairExtractPublicKey(out:CryptoByteSpan,keypair:CryptoByteSpan){keypair.data.copyInto(out.data,out.offset,keypair.offset+57,keypair.offset+114)}
 fun keypairExtractPrivateKey(out:CryptoByteSpan,keypair:CryptoByteSpan){keypair.data.copyInto(out.data,out.offset,keypair.offset,keypair.offset+57)}
 fun keypairDestroy(keypair:CryptoByteSpan){keypair.data.fill(0,keypair.offset,keypair.offset+114)}
 fun keypairSign(signature:CryptoByteSpan,keypair:CryptoByteSpan,message:CryptoByteSpan,prehashed:Int,context:CryptoByteSpan)=signInternal(signature,CryptoByteSpan(keypair.data,keypair.offset,57),CryptoByteSpan(keypair.data,keypair.offset+57,57),message,prehashed,context)
 fun keypairSignPrehash(signature:CryptoByteSpan,keypair:CryptoByteSpan,ctx:CryptoSha3.Context,context:CryptoByteSpan){val output=prehashOutput(ctx);signInternal(signature,CryptoByteSpan(keypair.data,keypair.offset,57),CryptoByteSpan(keypair.data,keypair.offset+57,57),span(output),1,context);output.fill(0)}
 fun verify(signature:CryptoByteSpan,public:CryptoByteSpan,message:CryptoByteSpan,prehashed:Int,context:CryptoByteSpan):Int {
  val pk=p.freshShape();val r=p.freshShape();var error=p.decodeEd(pk,public);if(error!=-1)return error;error=p.decodeEd(r,signature);if(error!=-1)return error
  val ctx=CryptoSha3.Context(ByteArray(344));hashInitWithDom(ctx,prehashed,0,context);CryptoDecafShake256.update(ctx,CryptoByteSpan(signature.data,signature.offset,57));CryptoDecafShake256.update(ctx,CryptoByteSpan(public.data,public.offset,57));CryptoDecafShake256.update(ctx,message);val challenge=ByteArray(114);CryptoDecafShake256.final(ctx,span(challenge));CryptoDecafShake256.destroy(ctx);val c=fresh();scalar.decodeLong(c,span(challenge));challenge.fill(0);scalar.sub(c,CryptoDecaf448Scalar.Region(scalar.constant("zero")),c)
  val response=fresh();error=scalar.decode(response,CryptoByteSpan(signature.data,signature.offset+57,56));if(error!=-1)return error;if(signature.data[signature.offset+113]!=0.toByte())return 0
  multiplication.baseDoubleScalarmulNonSecret(pk,response,pk,c);return if(p.equal(pk,r)!=0L)-1 else 0
 }
 fun verifyPrehash(signature:CryptoByteSpan,public:CryptoByteSpan,ctx:CryptoSha3.Context,context:CryptoByteSpan):Int {val output=prehashOutput(ctx);return verify(signature,public,span(output),1,context)}
}
