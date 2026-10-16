// Crypto.ECC Curve_X448 instance, crypton2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
internal object CryptoEccX448: CryptoEcc.EllipticCurveDH<CryptoCurve448.PublicKey,CryptoCurve448.SecretKey> {
        override fun toString()="Curve_X448"
        private val raw=CryptoCurve448()
        override val sizeBits=448
        override fun generateScalar(random: (Int)->ByteArray)=raw.generateSecretKey(random)
        override fun encodePoint(p: CryptoCurve448.PublicKey)=p.toByteArray()
        override fun decodePoint(bytes: ByteArray)=raw.publicKey(bytes)
        override fun encodeScalar(s: CryptoCurve448.SecretKey)=s.toByteArray()
        override fun decodeScalar(bytes: ByteArray)=raw.secretKey(bytes)
        override fun scalarToPoint(s: CryptoCurve448.SecretKey)=raw.toPublic(s)
        override fun ecdhRaw(s: CryptoCurve448.SecretKey,p: CryptoCurve448.PublicKey)=CryptoEcc.SharedSecret(raw.dh(p,s).toByteArray())
        override fun ecdh(s: CryptoCurve448.SecretKey,p: CryptoCurve448.PublicKey): CryptoEcc.Failable<CryptoEcc.SharedSecret> {
            val secret=ecdhRaw(s,p);var nonzero=0;for(v in secret.toByteArray())nonzero=nonzero or(v.toInt() and 255)
            return if(nonzero==0)CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_ScalarMultiplicationInvalid)else CryptoEcc.Failable.Passed(secret)
        }
    }

