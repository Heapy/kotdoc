// RSA.Types Show/DebugShow, crypton BSD; GHC Show defaults Glasgow1994-2002.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*

object CryptoRsaTypesShow {
    private fun text(s:String,tail:()->ManyShowString)=ManyShowRuntime.text(s,tail)
    private fun atom(value:()->Any?,tail:()->ManyShowString):ManyShowString=GenericList.defer{text(value().toString(),tail)}
    private fun parens(p:Long,body:(()->ManyShowString)->ManyShowString,tail:()->ManyShowString):ManyShowString = if(p>10)text("("){body{text(")",tail)}}else body(tail)
    private fun emit(parts:List<()->String>,tail:()->ManyShowString,index:Int=0):ManyShowString=GenericList.defer {
        if(index==parts.size)tail()else text(parts[index]()){emit(parts,tail,index+1)}
    }
    val blinder:ManyElementShow<CryptoRsa.Blinder> = object:ManyElementShow<CryptoRsa.Blinder>{
        override fun showsPrec(precedence:Long,value:()->CryptoRsa.Blinder,suffix:()->ManyShowString):ManyShowString=GenericList.defer {
            val v=value();v.force()
            fun arg(n:java.math.BigInteger)=if(n.signum()<0)"($n)"else n.toString()
            parens(precedence,{tail->emit(listOf({"Blinder "},{arg(v.r)},{" "},{arg(v.inverse)}),tail)},suffix)
        }
    }
    val error:ManyElementShow<CryptoRsaOaep.Error> = object:ManyElementShow<CryptoRsaOaep.Error>{
        override fun showsPrec(precedence:Long,value:()->CryptoRsaOaep.Error,suffix:()->ManyShowString):ManyShowString=GenericList.defer{text(value().toString(),suffix)}
    }
    val publicKey:ManyElementShow<CryptoRsaTypes.PublicKey> = object:ManyElementShow<CryptoRsaTypes.PublicKey> {
        override fun showsPrec(precedence:Long,value:()->CryptoRsaTypes.PublicKey,suffix:()->ManyShowString):ManyShowString=GenericList.defer {
            val v=value() // derived ordinary constructor pattern precedes the prefix
            parens(precedence,{tail->emit(listOf({"PublicKey {public_size = "},{v.size.toString()},{", public_n = "},{v.n.toString()},{", public_e = "},{v.e.toString()},{"}"}),tail)},suffix)
        }
    }
    val privateKey:ManyElementShow<CryptoRsaTypes.PrivateKey> = object:ManyElementShow<CryptoRsaTypes.PrivateKey> {
        override fun showsPrec(precedence:Long,value:()->CryptoRsaTypes.PrivateKey,suffix:()->ManyShowString):ManyShowString=GenericList.defer {
            parens(precedence,{tail->text("PrivateKey {private_pub = "){publicKey.showsPrec(0,{value().publicKey}){text(", private_d = <secret>, private_p = <secret>, private_q = <secret>, private_dP = <secret>, private_dQ = <secret>, private_qinv = <secret>}",tail)}}},suffix)
        }
    }
    val keyPair:ManyElementShow<CryptoRsaTypes.KeyPair> = object:ManyElementShow<CryptoRsaTypes.KeyPair> {
        override fun showsPrec(precedence:Long,value:()->CryptoRsaTypes.KeyPair,suffix:()->ManyShowString):ManyShowString=GenericList.defer {
            parens(precedence,{tail->text("KeyPair "){privateKey.showsPrec(11,{value().toPrivateKey()},tail)}},suffix)
        }
    }
    fun debugPrivate(value:()->CryptoRsaTypes.PrivateKey,suffix:()->ManyShowString={GenericList.empty()}):ManyShowString {
        fun rest(i:Int):ManyShowString = if(i==7)text("}",suffix)else {
            val name=listOf("private_pub","private_d","private_p","private_q","private_dP","private_dQ","private_qinv")[i]
            text(if(i==0)"PrivateKey {$name = "else", $name = "){
                if(i==0)publicKey.show{value().publicKey}.let{ManyShowRuntime.append(it){rest(i+1)}}
                else atom({value().field(i)}){rest(i+1)}
            }
        }
        return GenericList.defer{rest(0)}
    }
    fun debugPair(value:()->CryptoRsaTypes.KeyPair)=text("KeyPair ("){debugPrivate({value().toPrivateKey()}){text(")"){GenericList.empty()}}}
}
