{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified Crypto.ECC.Edwards25519 as E
import Crypto.Error
import qualified OriginalEdwards25519Spec as O
import Imports(arbitraryBS)
import Test.Hspec(hspec)
import Test.QuickCheck
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import System.Environment(getArgs,withArgs)
bytes b=toJSON(B.unpack(b::B.ByteString))
main=do
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  _->L.putStrLn$encode[let
        payload k=unGen(arbitraryBS 64)(mkQCGen(1729+i*3+k))(i`mod`101)
        value k=throwCryptoError(E.scalarDecodeLong(payload k))
        original=unGen(arbitrary::Gen E.Scalar)(mkQCGen(1729+i*3))(i`mod`101)
       in if E.scalarEncode original/=(E.scalarEncode(value 0)::B.ByteString)then error "original Arbitrary mismatch"else object["a" .= bytes(payload 0),"b" .= bytes(payload 1),"c" .= bytes(payload 2),"encoded" .= bytes(E.scalarEncode original),"seed" .= (1729+i*3),"size" .= (i`mod`101)]|i<-[0..199::Int]]
