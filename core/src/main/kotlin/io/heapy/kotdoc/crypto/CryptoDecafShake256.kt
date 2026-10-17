// Translated from decaf/shake.h, Copyright(C)2006-2009 Vincent Hanquez.
// licenses/crypton-sha3-BSD-2-Clause.txt.
package io.heapy.kotdoc.crypto
internal object CryptoDecafShake256 {
 fun init(ctx:CryptoSha3.Context)=CryptoSha3.init(ctx,256)
 internal inline fun forChunks(length:Long,action:(Long,Long)->Unit){var remaining=length;var offset=0L;while(remaining>0xffffffffL){action(offset,0x80000000L);remaining-=0x80000000L;offset+=0x80000000L};action(offset,remaining)}
 fun update(ctx:CryptoSha3.Context,input:CryptoByteSpan)=forChunks(input.size.toLong()){offset,size->CryptoSha3.update(ctx,CryptoByteSpan(input.data,input.offset+offset.toInt(),size.toInt()))}
 fun output(ctx:CryptoSha3.Context,out:CryptoByteSpan)=forChunks(out.size.toLong()){offset,size->CryptoSha3.output(ctx,CryptoByteSpan(out.data,out.offset+offset.toInt(),size.toInt()))}
 fun final(ctx:CryptoSha3.Context,out:CryptoByteSpan){CryptoSha3.finalizeShake(ctx);output(ctx,out);init(ctx)}
 fun destroy(ctx:CryptoSha3.Context)=ctx.data.fill(0,ctx.offset,ctx.offset+344)
 fun hash(out:CryptoByteSpan,input:CryptoByteSpan){val ctx=CryptoSha3.Context(ByteArray(344));init(ctx);update(ctx,input);CryptoSha3.finalizeShake(ctx);output(ctx,out);destroy(ctx)}
}
