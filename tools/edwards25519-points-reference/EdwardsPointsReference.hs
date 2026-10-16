{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified Crypto.ECC.Edwards25519 as E
import qualified OriginalEdwards25519Spec as O
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
        point k=unGen(arbitrary::Gen E.Point)(mkQCGen(9271+i*7+k))(i`mod`101)
        scalar k=unGen(arbitrary::Gen E.Scalar)(mkQCGen(9271+i*7+k))(i`mod`101)
        O.PrimeOrder prime=unGen(arbitrary::Gen O.PrimeOrder)(mkQCGen(9271+i*7+6))(i`mod`101)
       in object["a" .= bytes(E.pointEncode(point 0)),"b" .= bytes(E.pointEncode(point 1)),"c" .= bytes(E.pointEncode(point 2)),"s1" .= bytes(E.scalarEncode(scalar 3)),"s2" .= bytes(E.scalarEncode(scalar 4)),"prime" .= bytes(E.pointEncode prime),"seed" .= (9271+i*7),"size" .= (i`mod`101)]|i<-[0..99::Int]]
