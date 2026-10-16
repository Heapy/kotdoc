-- Unchanged original private-key generators/properties, crypton-x509 1.9.2 Tests/Tests.hs.
module OriginalPrivateKeyTests where
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
import Data.List(isInfixOf)
import Crypto.Debug(DebugShow(..))
import qualified Crypto.PubKey.ECC.Types as ECC
import Data.X509.PublicKey(SerializedPoint(..))
import Data.X509.PrivateKey

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

instance Arbitrary RSA.PrivateKey where
    arbitrary =
        RSA.PrivateKey
            <$> arbitrary
            <*> arbitrary
            <*> arbitrary
            <*> arbitrary
            <*> arbitrary
            <*> arbitrary
            <*> arbitrary

instance Arbitrary DSA.PrivateKey where
    arbitrary = DSA.PrivateKey <$> arbitrary <*> arbitrary

instance Arbitrary X25519.SecretKey where
    arbitrary = throwCryptoError . X25519.secretKey <$> arbitraryBS 32 32

instance Arbitrary X448.SecretKey where
    arbitrary = throwCryptoError . X448.secretKey <$> arbitraryBS 56 56

instance Arbitrary Ed25519.SecretKey where
    arbitrary = throwCryptoError . Ed25519.secretKey <$> arbitraryBS 32 32

instance Arbitrary Ed448.SecretKey where
    arbitrary = throwCryptoError . Ed448.secretKey <$> arbitraryBS 57 57

instance Arbitrary PrivKey where
    arbitrary =
        oneof
            [ PrivKeyRSA <$> arbitrary
            , PrivKeyDSA <$> arbitrary
            , -- , PrivKeyECDSA ECDSA_Hash_SHA384 <$> (B.pack <$> replicateM 384 arbitrary)
              PrivKeyX25519 <$> arbitrary
            , PrivKeyX448 <$> arbitrary
            , PrivKeyEd25519 <$> arbitrary
            , PrivKeyEd448 <$> arbitrary
            ]

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

-- | A scalar long enough that finding it in the rendered key cannot be an
-- accident of the curve parameters beside it.
newtype ECSecret = ECSecret Integer
    deriving (Show)

instance Arbitrary ECSecret where
    arbitrary = ECSecret <$> choose (10 ^ (40 :: Int), 10 ^ (41 :: Int))

-- | 'show' of an EC private key holds the curve and not the scalar, and
-- 'debugShow' holds both.  The wrapper is checked too, since it is
-- @Credential@ and @ServerParams@ that a program actually prints.
property_ec_show_redacts :: ECSecret -> Bool
property_ec_show_redacts (ECSecret d) = all ok [named, prime]
  where
    named = PrivKeyEC_Named ECC.SEC_p256r1 d
    prime = PrivKeyEC_Prime d 1 2 3 (SerializedPoint B.empty) 4 1 5
    digits = show d
    ok k =
        not (digits `isInfixOf` show k)
            && not (digits `isInfixOf` show (PrivKeyEC k))
            && "<secret>" `isInfixOf` show k
            && digits `isInfixOf` debugShow k
            && digits `isInfixOf` debugShow (PrivKeyEC k)

