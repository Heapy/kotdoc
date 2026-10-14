{-# LANGUAGE OverloadedStrings #-}
module Main where
import Data.Aeson
import qualified Data.Aeson.Key as K
import qualified Data.Aeson.KeyMap as KM
import qualified Data.Aeson.Encode.Pretty as P
import qualified Data.Scientific as S
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import qualified Data.Text.Lazy.Builder as B
import qualified Data.Text.Lazy.Builder.Scientific as F
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.ByteString.Char8 as BS
import qualified Data.Vector as V
import Data.Char (chr)
import Data.List (sortBy)
import Data.Word (Word64)
import GHC.Float (castWord64ToDouble)
import Control.Monad (forever)
import System.IO (isEOF, hFlush, stdout)
import OriginalGenerator ()
import qualified Test.QuickCheck as QC
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)

arr (Array v) = V.toList v
arr _ = error "array"
str (String s) = T.unpack s
str _ = error "string"
int x = case fromJSON x of Success n -> n; Error s -> error s
points = T.pack . map (chr . int) . arr
num (Array v) = S.scientific (read (str (v V.! 1))) (int (v V.! 2))
num _ = error "scientific fields"
value x = case arr x of
 [String "n",_,_] -> Number (num x)
 [String "s",ps] -> String (points ps)
 [String "b",Bool b] -> Bool b
 [String "z"] -> Null
 [String "a",vs] -> Array (V.fromList (map value (arr vs)))
 [String "o",pairs] -> Object (KM.fromList [(K.fromText (points k),value v) | p <- arr pairs, let [k,v] = arr p])
 _ -> error "value"
fmt "Generic" = S.Generic
fmt "Exponent" = S.Exponent
fmt "Fixed" = S.Fixed
fmt _ = error "format"
decs Null = Nothing
decs n = Just (int n)
render f d = TL.unpack . B.toLazyText . F.formatScientificBuilder (fmt (str f)) (decs d)
comparator name ks = case name of
 "equal" -> \_ _ -> EQ
 "natural" -> compare
 "reverse" -> flip compare
 "length" -> \a b -> compare (T.length a) (T.length b)
 "keyOrder" -> P.keyOrder ks
 "keyThenNatural" -> \a b -> P.keyOrder ks a b <> compare a b
 "alwaysGT" -> \_ _ -> GT
 "alwaysLT" -> \_ _ -> LT
 "cycle" -> \a b -> let x = T.length a `mod` 3; y = T.length b `mod` 3 in if x == y then EQ else if (x+1) `mod` 3 == y then LT else GT
 _ -> error "comparator"
numberFormat x = case arr x of
 [String "Generic"] -> P.Generic
 [String "Scientific"] -> P.Scientific
 [String "Decimal"] -> P.Decimal
 [String "fields"] -> P.Custom (\s -> B.fromString (show (S.coefficient s) ++ "@" ++ show (S.base10Exponent s)))
 [String "format",f,d] -> P.Custom (B.fromString . render f d)
 _ -> error "numberFormat"
config x = case arr x of
 [indent,String cmp,ks,nf,Bool trail] -> P.Config
   (case indent of String "Tab" -> P.Tab; n -> P.Spaces (int n))
   (comparator (T.unpack cmp) (map points (arr ks))) (numberFormat nf) trail
 _ -> error "config"
textResult t = object ["text" .= t]
response x = case arr x of
 [String "samples",seed,n] -> let ss=unGen (QC.vectorOf (int n) QC.arbitrary) (mkQCGen (int seed)) 30 :: [S.Scientific]
   in toJSON [object ["request" .= q,"expected" .= response q] | s <- ss,
       let fields=toJSON [String "n",toJSON (show (S.coefficient s)),toJSON (S.base10Exponent s)],
       q <- [toJSON [String "format",String "Generic",Null,fields],toJSON [String "digits",fields]]]
 [String "format",f,d,n] -> textResult (render f d (num n))
 [String "prefix",f,d,n,count] -> textResult (TL.unpack (TL.take (int count) (B.toLazyText (F.formatScientificBuilder (fmt (str f)) (decs d) (num n)))))
 [String "stringPrefix",f,d,n,count] -> textResult (take (int count) (S.formatScientific (fmt (str f)) (decs d) (num n)))
 [String "digits",n] -> let (ds,e) = S.toDecimalDigits (abs (num n)) in object ["digits" .= map (\d -> chr (48+d)) ds,"exponent" .= e]
 [String "double",bits] -> let d = castWord64ToDouble (read (str bits) :: Word64) in
   if isNaN d || isInfinite d then object ["pretty" .= TL.unpack (B.toLazyText (P.encodePrettyToTextBuilder d))]
   else let s = S.fromFloatDigits d in object ["coefficient" .= show (S.coefficient s),"exponent" .= S.base10Exponent s,"formatted" .= render (String "Generic") Null s,"pretty" .= TL.unpack (B.toLazyText (P.encodePrettyToTextBuilder d))]
 [String "pretty",c,v] -> let conf=config c; val=value v; bytes=P.encodePretty' conf val; t=TL.unpack (B.toLazyText (P.encodePrettyToTextBuilder' conf val)) in object ["text" .= t,"bytes" .= BL.unpack bytes]
 [String "sort",String c,ks,ps] -> toJSON (sortBy (comparator (T.unpack c) (map points (arr ks))) (map points (arr ps)))
 [String "deep",n] -> let v = iterate (\z -> Array (V.singleton z)) Null !! int n in textResult (TL.unpack (B.toLazyText (P.encodePrettyToTextBuilder' (P.defConfig {P.confIndent=P.Spaces 0}) v)))
 _ -> error "operation"
main = loop where
 loop = do
  end <- isEOF
  if end then pure () else do
   line <- BS.getLine
   let request = case eitherDecode (BL.fromStrict line) of Right v -> v; Left e -> error e
   C.putStrLn (encode (response request)); hFlush stdout; loop
