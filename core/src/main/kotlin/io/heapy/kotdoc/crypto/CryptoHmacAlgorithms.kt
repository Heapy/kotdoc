// HashAlgorithm adapters to the independently ported crypton hash engines.
// crypton 2.0.1, BSD-3-Clause; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

object CryptoHmacAlgorithms {
    enum class Algorithm {MD5,SHA1,SHA224,SHA256,SHA384,SHA512,SHA512t_224,SHA512t_256,Keccak_224,Keccak_256,Keccak_384,Keccak_512,SHA3_224,SHA3_256,SHA3_384,SHA3_512}
    fun algorithm(algorithm:Algorithm):CryptoHmac.HashAlgorithm=when(algorithm){
        Algorithm.MD5->object:CryptoHmac.HashAlgorithm{
            override val blockSize=64;override val digestSize=16;override val contextSize=96
            private fun c(raw:ByteArray)=CryptoMd5.Context(CryptoMd5.Algorithm.MD5,raw)
            override fun initialize(context:ByteArray)=CryptoMd5.init(c(context))
            override fun update(context:ByteArray,bytes:ByteArray)=CryptoMd5.update(c(context),CryptoByteSpan(bytes))
            override fun finalize(context:ByteArray,output:ByteArray)=CryptoMd5.finalize(c(context),CryptoByteSpan(output))
        }
        Algorithm.SHA1,Algorithm.SHA224,Algorithm.SHA256->{val alg=CryptoSha32.Algorithm.valueOf(algorithm.name);object:CryptoHmac.HashAlgorithm{
            override val blockSize=64;override val digestSize=alg.digestSize;override val contextSize=alg.contextSize
            private fun c(raw:ByteArray)=CryptoSha32.Context(alg,raw)
            override fun initialize(context:ByteArray)=CryptoSha32.init(c(context))
            override fun update(context:ByteArray,bytes:ByteArray)=CryptoSha32.update(c(context),CryptoByteSpan(bytes))
            override fun finalize(context:ByteArray,output:ByteArray)=CryptoSha32.finalize(c(context),CryptoByteSpan(output))
        }}
        Algorithm.SHA384,Algorithm.SHA512,Algorithm.SHA512t_224,Algorithm.SHA512t_256->object:CryptoHmac.HashAlgorithm{
            override val blockSize=128;override val digestSize=when(algorithm){Algorithm.SHA384->48;Algorithm.SHA512->64;Algorithm.SHA512t_224->28;else->32};override val contextSize=256
            private fun c(raw:ByteArray)=CryptoSha512.Context(raw)
            override fun initialize(context:ByteArray){when(algorithm){Algorithm.SHA384->CryptoSha512.init384(c(context));Algorithm.SHA512->CryptoSha512.init(c(context));Algorithm.SHA512t_224->CryptoSha512.initT(c(context),224);else->CryptoSha512.initT(c(context),256)}}
            override fun update(context:ByteArray,bytes:ByteArray)=CryptoSha512.update(c(context),CryptoByteSpan(bytes))
            override fun finalize(context:ByteArray,output:ByteArray){when(algorithm){Algorithm.SHA384->CryptoSha512.finalize384(c(context),CryptoByteSpan(output));Algorithm.SHA512->CryptoSha512.finalize(c(context),CryptoByteSpan(output));else->CryptoSha512.finalizeT(c(context),digestSize*8,CryptoByteSpan(output))}}
        }
        else->{val bits=algorithm.name.substringAfterLast('_').toInt();val keccak=algorithm.name.startsWith("Keccak");object:CryptoHmac.HashAlgorithm{
            override val blockSize=200-2*(bits/8);override val digestSize=bits/8;override val contextSize=208+blockSize
            private fun c(raw:ByteArray)=CryptoSha3.Context(raw)
            override fun initialize(context:ByteArray)=CryptoSha3.init(c(context),bits)
            override fun update(context:ByteArray,bytes:ByteArray)=CryptoSha3.update(c(context),CryptoByteSpan(bytes))
            override fun finalize(context:ByteArray,output:ByteArray){if(keccak)CryptoSha3.keccakFinalize(c(context),bits,CryptoByteSpan(output))else CryptoSha3.finalize(c(context),bits,CryptoByteSpan(output))}
        }}
    }
    fun shake(securityBits:Int,outputBits:Int):CryptoHmac.HashAlgorithm {
        val alg=CryptoSha3.Shake(securityBits,outputBits)
        return object:CryptoHmac.HashAlgorithm{
            override val blockSize=alg.blockSize;override val digestSize=alg.digestSize;override val contextSize=alg.contextSize
            override fun initialize(context:ByteArray)=CryptoSha3.init(CryptoSha3.Context(context),securityBits)
            override fun update(context:ByteArray,bytes:ByteArray)=CryptoSha3.update(CryptoSha3.Context(context),CryptoByteSpan(bytes))
            override fun finalize(context:ByteArray,output:ByteArray)=alg.finalize(CryptoSha3.Context(context),CryptoByteSpan(output))
        }
    }
}
