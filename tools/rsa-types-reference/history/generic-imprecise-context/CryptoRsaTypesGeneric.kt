// RSA.Types derived Generic, crypton BSD; GHC DerivGenerics Glasgow2011.
// Complete original notices: licenses/crypton-BSD-3-Clause.txt, GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*

object CryptoRsaTypesGeneric {
    private val int=DataDictionaries.int().representation.type
    private val integer=CryptoRsaTypesData.integer.representation.type
    private val publicType=CryptoRsaTypesData.publicKey.representation.type
    private val publicLabels=listOf("public_size","public_n","public_e")
    private val privateLabels=listOf("private_pub","private_d","private_p","private_q","private_dP","private_dQ","private_qinv")
    val publicKey:GenericRepDictionary<CryptoRsaTypes.PublicKey> = dictionary("PublicKey",publicLabels,listOf(int,integer,integer),{v,i->v.field(i)},{CryptoRsaTypes.PublicKey.source(it)})
    val privateKey:GenericRepDictionary<CryptoRsaTypes.PrivateKey> = dictionary("PrivateKey",privateLabels,listOf(publicType)+List(6){integer},{v,i->v.field(i)},{CryptoRsaTypes.PrivateKey.source(it)})
    private fun product(fields:List<GenericRepType>):GenericRepType = if(fields.size==1)fields.single()else {val n=fields.size/2;GenericRepType.Product(product(fields.take(n)),product(fields.drop(n)))}
    private fun count(t:GenericRepType):Int=when(t){is GenericRepType.Field->1;is GenericRepType.Metadata->count(t.child);is GenericRepType.Product->count(t.left)+count(t.right);else->error("RSA product")}
    private fun <A> dictionary(name:String,labels:List<String>,types:List<GenericType>,project:(A,Int)->Any?,construct:(List<Lazy<Any?>>)->A):GenericRepDictionary<A> = object:GenericRepDictionary<A> {
        override val representation=GenericRepType.Metadata(GenericRepMetadata.Datatype(name,"Crypto.PubKey.RSA.Types","crypton-2.0.1-inplace",false),
            GenericRepType.Metadata(GenericRepMetadata.Constructor(name,"Prefix",true),product(labels.mapIndexed{i,label->GenericRepType.Metadata(GenericRepMetadata.Selector(label,"NoSourceUnpackedness","NoSourceStrictness","DecidedLazy"),GenericRepType.Field(types[i]))})))
        override fun from(value:GenericRepThunk<A>):GenericRepValue {
            val input=cryptoRandomMemo{value()}
            fun fields(t:GenericRepType,offset:Int):GenericRepValue=GenericRepValue(t){when(t){
                is GenericRepType.Metadata->GenericRepLayer.Metadata(fields(t.child,offset))
                is GenericRepType.Product->{input.value;GenericRepLayer.Product(fields(t.left,offset),fields(t.right,offset+count(t.left)))}
                is GenericRepType.Field->GenericRepLayer.Field(GenericRepField(t.type){project(input.value,offset)})
                else->error("RSA product")}}
            return fields(representation,0)
        }
        override fun to(value:GenericRepValue):GenericRepThunk<A> {
            require(value.type==representation)
            val result=cryptoRandomMemo {
                val body=(value.layer()as GenericRepLayer.Metadata).child
                val fields=(body.layer()as GenericRepLayer.Metadata).child
                // Generated product patterns demand each product constructor, not K1 fields.
                val pending=ArrayDeque<GenericRepValue>();pending.add(fields)
                while(pending.isNotEmpty()) {val v=pending.removeLast();when(val t=v.type){
                    is GenericRepType.Product->{val p=v.layer()as GenericRepLayer.Product;pending.add(p.right);pending.add(p.left)}
                    is GenericRepType.Metadata->if(t.child is GenericRepType.Product)pending.add((v.layer()as GenericRepLayer.Metadata).child)
                    else->Unit}}
                fun field(v:GenericRepValue,i:Int):GenericRepField=when(val p=v.layer()){
                    is GenericRepLayer.Metadata->field(p.child,i)
                    is GenericRepLayer.Field->p.value
                    is GenericRepLayer.Product->{val n=count(p.left.type);if(i<n)field(p.left,i)else field(p.right,i-n)}
                    else->error("RSA field")}
                construct(types.indices.map{i->cryptoRandomMemo{field(fields,i).value()}})
            }
            return GenericRepThunk{result.value}
        }
    }
}
