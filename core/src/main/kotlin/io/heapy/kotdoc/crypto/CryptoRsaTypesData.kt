// Crypto.PubKey.RSA.Types derived Data; crypton BSD-3-Clause.
// GHC.Internal.Data.Data: Glasgow/CWI 2001-2004; licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import io.heapy.kotdoc.ast.*
import java.math.BigInteger

object CryptoRsaTypesData {
    private val module="Crypto.PubKey.RSA.Types"
    private fun type(name:String)=GenericType(module,name,library="crypton-2.0.1-inplace")
    private fun metadata(name:String,labels:List<String>):DataTypeInfo {
        lateinit var data:DataTypeInfo
        val constructor=DataConstr({DataConstrRep.Algebraic(1)},{name},{GenericList.view(labels)},{DataFixity.Prefix},{data})
        data=DataTypeInfo({"$module.$name"},{DataTypeRep.Algebraic(DataThunk{GenericList.view(listOf(constructor))})})
        return data
    }
    val integer:DataDictionary<BigInteger> = object:DataDictionary<BigInteger>(DataRepresentation(GenericType("GHC.Num.Integer","Integer",library="ghc-bignum"))) {
        private val data=DataTypeInfo({"Prelude.Integer"},{DataTypeRep.Integral})
        override fun dataTypeOf(value:DataThunk<BigInteger>)=data
        override fun toConstr(value:DataThunk<BigInteger>)=DataConstr({DataConstrRep.Integral{value()}},{value().toString()},{error("GHC.Internal.Data.Data.confields")},{error("GHC.Internal.Data.Data.confixity")},{data})
        override fun <F> gunfold(unfold:DataUnfold<F>,constructor:DataThunk<DataConstr>):DataKind<F,BigInteger> {
            val rep=constructor().representation
            if(rep !is DataConstrRep.Integral)throw DataSourceError{"GHC.Internal.Data.Data.gunfold: Constructor ${constructor().name} is not of type Integer."}
            unfold.forceInject();return unfold.inject(DataThunk{rep.value})
        }
    }
    private val int=DataDictionaries.int()
    val publicKey:DataDictionary<CryptoRsaTypes.PublicKey> by lazy {
        val data=metadata("PublicKey",listOf("public_size","public_n","public_e"))
        val c=DataConstructor<CryptoRsaTypes.PublicKey>(data.constructors()[0],listOf(int.evidence,integer.evidence,integer.evidence),
            {v,i->DataThunk{v().field(i)}},{fields->CryptoRsaTypes.PublicKey.source(fields.map{f->cryptoRandomMemo{f()}})})
        AlgebraicDataDictionary(DataRepresentation(type("PublicKey")),data,listOf(c),{v->v();0},{0})
    }
    val privateKey:DataDictionary<CryptoRsaTypes.PrivateKey> by lazy {
        val data=metadata("PrivateKey",listOf("private_pub","private_d","private_p","private_q","private_dP","private_dQ","private_qinv"))
        val c=DataConstructor<CryptoRsaTypes.PrivateKey>(data.constructors()[0],listOf(publicKey.evidence)+List(6){integer.evidence},
            {v,i->DataThunk{v().field(i)}},{fields->CryptoRsaTypes.PrivateKey.source(fields.map{f->cryptoRandomMemo{f()}})})
        AlgebraicDataDictionary(DataRepresentation(type("PrivateKey")),data,listOf(c),{v->v();0},{0})
    }
    val keyPair:DataDictionary<CryptoRsaTypes.KeyPair> by lazy {
        val data=metadata("KeyPair",emptyList())
        val c=DataConstructor<CryptoRsaTypes.KeyPair>(data.constructors()[0],listOf(privateKey.evidence),
            {v,_->DataThunk{v().toPrivateKey()}},{fields->CryptoRsaTypes.KeyPair.deferred{fields[0]() as CryptoRsaTypes.PrivateKey}})
        // Derived newtype gfoldl/toConstr need not demand the represented root.
        AlgebraicDataDictionary(DataRepresentation(type("KeyPair")),data,listOf(c),{0},{0},{0})
    }
}
