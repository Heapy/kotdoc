-- Unchanged original public-key generators/property, crypton-x509 1.9.2 Tests/Tests.hs.
module OriginalPublicKeyTests where
import Test.QuickCheck
import Control.Monad
import qualified Data.ByteString as B
import Crypto.Error(throwCryptoError)
import qualified Crypto.PubKey.RSA as RSA
import qualified Crypto.PubKey.DSA as DSA
import qualified Crypto.PubKey.Curve25519 as X25519
import qualified Crypto.PubKey.Curve448 as X448
import qualified Crypto.PubKey.Ed25519 as Ed25519
import qualified Crypto.PubKey.Ed448 as Ed448
import Data.ASN1.Types
import Data.X509.PublicKey

instance Arbitrary RSA.PublicKey where
    arbitrary = do
        bytes <- elements [64, 128, 256]
        e <- elements [0x3, 0x10001]
        n <- choose (2 ^ (8 * (bytes - 1)), 2 ^ (8 * bytes))
        return $
            RSA.PublicKey
                { RSA.public_size = bytes
                , RSA.public_n = n
                , RSA.public_e = e
                }

instance Arbitrary DSA.Params where
    arbitrary = DSA.Params <$> arbitrary <*> arbitrary <*> arbitrary

instance Arbitrary DSA.PublicKey where
    arbitrary = DSA.PublicKey <$> arbitrary <*> arbitrary

instance Arbitrary X25519.PublicKey where
    arbitrary = X25519.toPublic <$> arbitrary

instance Arbitrary X448.PublicKey where
    arbitrary = X448.toPublic <$> arbitrary

instance Arbitrary Ed25519.PublicKey where
    arbitrary = Ed25519.toPublic <$> arbitrary

instance Arbitrary Ed448.PublicKey where
    arbitrary = Ed448.toPublic <$> arbitrary

instance Arbitrary PubKey where
    arbitrary =
        oneof
            [ PubKeyRSA <$> arbitrary
            , PubKeyDSA <$> arbitrary
            , -- , PubKeyECDSA ECDSA_Hash_SHA384 <$> (B.pack <$> replicateM 384 arbitrary)
              PubKeyX25519 <$> arbitrary
            , PubKeyX448 <$> arbitrary
            , PubKeyEd25519 <$> arbitrary
            , PubKeyEd448 <$> arbitrary
            ]

instance Arbitrary X25519.SecretKey where
    arbitrary = throwCryptoError . X25519.secretKey <$> arbitraryBS 32 32

instance Arbitrary X448.SecretKey where
    arbitrary = throwCryptoError . X448.secretKey <$> arbitraryBS 56 56

instance Arbitrary Ed25519.SecretKey where
    arbitrary = throwCryptoError . Ed25519.secretKey <$> arbitraryBS 32 32

instance Arbitrary Ed448.SecretKey where
    arbitrary = throwCryptoError . Ed448.secretKey <$> arbitraryBS 57 57

arbitraryBS r1 r2 = choose (r1, r2) >>= \l -> (B.pack <$> replicateM l arbitrary)

property_unmarshall_marshall_id
    :: (Show o, Arbitrary o, ASN1Object o, Eq o) => o -> Bool
property_unmarshall_marshall_id o =
    case got of
        Right (gotObject, [])
            | gotObject == o -> True
            | otherwise ->
                error ("object is different: " ++ show gotObject ++ " expecting " ++ show o)
        Right (gotObject, l) ->
            error
                ( "state remaining: "
                    ++ show l
                    ++ " marshalled: "
                    ++ show oMarshalled
                    ++ " parsed: "
                    ++ show gotObject
                )
        Left e ->
            error
                ( "parsing failed: "
                    ++ show e
                    ++ " object: "
                    ++ show o
                    ++ " marshalled as: "
                    ++ show oMarshalled
                )
  where
    got = fromASN1 oMarshalled
    oMarshalled = toASN1 o []

