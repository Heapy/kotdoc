// RSA.Types derived Eq, crypton BSD-3-Clause; GHC derivation Glasgow.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.ManyElementEq
object CryptoRsaTypesEq {
 val publicKey:ManyElementEq<CryptoRsaTypes.PublicKey> = object:ManyElementEq<CryptoRsaTypes.PublicKey>{override fun equal(left:()->CryptoRsaTypes.PublicKey,right:()->CryptoRsaTypes.PublicKey):Boolean {val a=left();val b=right();return a==b}}
 val privateKey:ManyElementEq<CryptoRsaTypes.PrivateKey> = object:ManyElementEq<CryptoRsaTypes.PrivateKey>{override fun equal(left:()->CryptoRsaTypes.PrivateKey,right:()->CryptoRsaTypes.PrivateKey):Boolean {val a=left();val b=right();return a==b}}
 val keyPair:ManyElementEq<CryptoRsaTypes.KeyPair> = object:ManyElementEq<CryptoRsaTypes.KeyPair>{override fun equal(left:()->CryptoRsaTypes.KeyPair,right:()->CryptoRsaTypes.KeyPair):Boolean {val a=left().toPrivateKey();val b=right().toPrivateKey();return a==b}}
 val blinder:ManyElementEq<CryptoRsa.Blinder> = object:ManyElementEq<CryptoRsa.Blinder>{override fun equal(left:()->CryptoRsa.Blinder,right:()->CryptoRsa.Blinder):Boolean {val a=left().also{it.force()};val b=right().also{it.force()};return a==b}}
 val error:ManyElementEq<CryptoRsaOaep.Error> = object:ManyElementEq<CryptoRsaOaep.Error>{override fun equal(left:()->CryptoRsaOaep.Error,right:()->CryptoRsaOaep.Error)=left()==right()}
}
