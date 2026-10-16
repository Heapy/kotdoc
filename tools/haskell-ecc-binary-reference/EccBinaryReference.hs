{-# LANGUAGE OverloadedStrings #-}
{-# OPTIONS_GHC -Wno-deprecations #-}
module Main where
import qualified OriginalBinaryTests as Original
import qualified Crypto.PubKey.ECC.Types as E
import qualified Crypto.Number.Serialize as BE
import Crypto.Number.Basic(numBits)
import Crypto.Number.F2m(squareF2m)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Data.Word
import Control.Monad
import System.Environment(getArgs,withArgs)
import Test.Hspec(hspec)

text o k = case K.lookup k o of Just(String s)->s;_->""
array o k = case K.lookup k o of
 Just v -> case fromJSON v of Success a->a;Error e->error e
 _ -> []
point E.PointO = toJSON(["infinity"] :: [T.Text])
point(E.Point x y) = toJSON[show x,show y]
curves = forM_ ([minBound..maxBound] :: [E.CurveName]) $ \name -> case E.getCurveByName name of
 c@(E.CurveF2m(E.CurveBinary fx cc)) -> do
  let E.Point gx gy=E.ecc_g cc;E.Point ox oy=Original.doubleAndAdd c 5(E.ecc_g cc)
      two=iterate(squareF2m fx)(E.ecc_b cc) !! (numBits fx-2)
  L.putStrLn(encode(object["name" .= show name,"fx" .= show fx,"a" .= show(E.ecc_a cc),"b" .= show(E.ecc_b cc),"n" .= show(E.ecc_n cc),"gx" .= show gx,"gy" .= show gy,"ox" .= show ox,"oy" .= show oy,"twoY" .= show two]))
 _ -> return ()
edges = forM_ Original.originalBinaryNames $ \name -> do
 let n=E.ecc_n(E.common_curve(E.getCurveByName name))
 L.putStrLn(encode(object["name" .= show name,"scalars" .= map show(Original.originalBinaryScalars n)]))
observe(Object o) = do
 let name=head[n|n<-[minBound..maxBound] :: [E.CurveName],T.pack(show n)==text o "curve"]
     c=E.getCurveByName name
     value k=BE.os2ip(B.pack(array o k :: [Word8]))
 return(point(Original.doubleAndAdd c (value "k")(E.Point(value "x")(value "y"))))
observe _ = error "object required"
main = do
 args<-getArgs
 case args of
  ["--curves"] -> curves
  ["--edges"] -> edges
  "--original":rest -> withArgs rest(hspec Original.spec)
  _ -> L.getContents >>= mapM_ (\l->observe(either error id(eitherDecode l)) >>= L.putStrLn . encode) . L.lines
