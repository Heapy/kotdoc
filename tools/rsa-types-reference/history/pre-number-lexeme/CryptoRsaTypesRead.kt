// RSA.Types derived Read, crypton BSD; GHC.Internal.Read/Lex Glasgow1994-2002/2002.
// Full attribution/licenses retained in GHC-Read-Show-Attribution.txt and base BSD.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.ParserCharacters
import java.math.BigInteger

object CryptoRsaTypesRead {
    fun <A> read(dictionary:ManyElementRead<A>,input:ManyReadInput):A {
        var candidate:(()->A)?=null
        for(row in dictionary.readsPrec({0},input))if(row.remainder().isSpaces()){
            if(candidate!=null)throw IllegalArgumentException("Prelude.read: ambiguous parse")
            candidate=row::value
        }
        return candidate?.invoke()?:throw IllegalArgumentException("Prelude.read: no parse")
    }
    private fun digit(c:Int?,base:Int):Int? {val n=when(c){in 48..57->c!!-48;in 65..70->c!!-65+10;in 97..102->c!!-97+10;else->return null};return n.takeIf{it<base}}
    private val unsigned:ManyReadPrec<BigInteger> = ManyReadPrec.look().flatMap {source->
        val s=source();var start=0L
        while(s.point(start)?.let(ParserCharacters::isSpace)==true)start++
        if(digit(s.point(start),10)==null)ManyReadPrec.fail()else {
            var base=10;var from=start
            if(s.point(start)==48){val b=when(s.point(start+1)){120,88->16;111,79->8;98,66->2;else->10};if(b!=10&&digit(s.point(start+2),b)!=null){base=b;from=start+2}}
            var stop=from;while(digit(s.point(stop),base)!=null)stop++
            // lexP recognizes a decimal fraction/exponent as one Number, which
            // numberToInteger rejects, rather than returning its integer prefix.
            var fraction=false;var exponent=false
            if(base==10){if(s.point(stop)==46&&digit(s.point(stop+1),10)!=null)fraction=true
                if(s.point(stop)==101||s.point(stop)==69){var e=stop+1;if(s.point(e)==43||s.point(e)==45)e++;if(digit(s.point(e),10)!=null)exponent=true}}
            if(fraction||exponent)ManyReadPrec.fail()else {
                val end=stop;val radix=base;val first=from
                ManyReadPrec.consume(end).map{var n=BigInteger.ZERO;for(i in first until end)n=n*radix.toBigInteger()+digit(s.point(i),radix)!!.toBigInteger();n}
            }
        }
    }
    val integer:ManyElementRead<BigInteger> = object:ManyElementRead<BigInteger>{override fun readPrec()=ManyReadPrec.parens(ManyReadPrec.token("-").flatMap{unsigned.map{it().negate()}}.or(unsigned))}
    val int:ManyElementRead<Long> = object:ManyElementRead<Long>{override fun readPrec()=integer.readPrec().map{it().toLong()}}
    private fun <A> record(name:String,labels:List<String>,readers:List<ManyElementRead<*>>,build:(List<Lazy<Any?>>)->A):ManyElementRead<A> = object:ManyElementRead<A> {
        override fun readPrec():ManyReadPrec<A> {
            fun fields(i:Int,values:List<Lazy<Any?>>):ManyReadPrec<A> = if(i==labels.size)ManyReadPrec.token("}").map{build(values)}else {
                val separator=if(i==0)ManyReadPrec.pure{Unit}else ManyReadPrec.token(",")
                separator.flatMap{ManyReadPrec.token(labels[i])}.flatMap{ManyReadPrec.token("=")}.flatMap{
                    @Suppress("UNCHECKED_CAST") val r=readers[i]as ManyElementRead<Any?>
                    r.readPrec().reset().flatMap{v->ManyReadPrec.defer{fields(i+1,values+cryptoRandomMemo(v))}}
                }
            }
            return ManyReadPrec.parens(ManyReadPrec.token(name).flatMap{ManyReadPrec.token("{")}.flatMap{ManyReadPrec.defer{fields(0,emptyList())}}.atPrecedence(11))
        }
    }
    val publicKey:ManyElementRead<CryptoRsaTypes.PublicKey> = record("PublicKey",listOf("public_size","public_n","public_e"),listOf(int,integer,integer),CryptoRsaTypes.PublicKey::source)
    val privateKey:ManyElementRead<CryptoRsaTypes.PrivateKey> = record("PrivateKey",listOf("private_pub","private_d","private_p","private_q","private_dP","private_dQ","private_qinv"),listOf(publicKey)+List(6){integer},CryptoRsaTypes.PrivateKey::source)
    val keyPair:ManyElementRead<CryptoRsaTypes.KeyPair> = object:ManyElementRead<CryptoRsaTypes.KeyPair> {
        override fun readPrec()=ManyReadPrec.parens(ManyReadPrec.token("KeyPair").flatMap{privateKey.readPrec().step().map{CryptoRsaTypes.KeyPair.deferred(it)}}.atPrecedence(10))
    }
}
