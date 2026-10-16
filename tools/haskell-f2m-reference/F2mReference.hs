{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Crypto.Number.F2m
import Crypto.Number.Basic (log2)
import qualified Number.F2mSpec as Original
import OriginalF2mProperties (propertyAt)
import Test.Hspec (hspec)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Control.Exception
import Control.Monad (forM_)
import System.Environment (getArgs,withArgs)

arg o k = case K.lookup k o of
  Just (String s) | "bottom:" `T.isPrefixOf` s -> error (T.unpack (T.drop 7 s))
                  | otherwise -> read (T.unpack s) :: Integer
  _ -> 0
text o k = case K.lookup k o of Just (String s) -> s; _ -> "value"
integer n = object ["integer" .= show n]
maybeValue part n = case n of
  Nothing -> object ["maybe" .= ("none" :: T.Text)]
  Just x | part == "constructor" -> object ["maybe" .= ("some" :: T.Text)]
         | otherwise -> object ["maybe" .= ("some" :: T.Text), "value" .= show x]
observe (Object o) = if part == "ignore" then String "ignored" else case text o "op" of
  "add" -> integer (addF2m a b)
  "mod" -> integer (modF2m f a)
  "mul" -> integer (mulF2m f a b)
  "rawSquare" -> integer (squareF2m' a)
  "square" -> integer (squareF2m f a)
  "pow" -> integer (powF2m f a b)
  "sqrt" -> integer (sqrtF2m f a)
  "inv" -> maybeValue part (invF2m f a)
  "div" -> maybeValue part (divF2m f a b)
  "quadratic" -> maybeValue part (quadraticF2m f a)
  "log2" -> integer (fromIntegral (log2 a))
  "property" -> object ["property" .= propertyAt (fromInteger (arg o "index")) (arg o "m") a b (arg o "c")]
  _ -> error "unknown operation"
 where f = arg o "f"; a = arg o "a"; b = arg o "b"; part = text o "part"
observe _ = error "object required"

main = do
 args <- getArgs
 case args of
  "--original":rest -> withArgs rest (hspec Original.spec)
  _ -> do
   input <- L.getContents
   forM_ (L.lines input) $ \line -> do
    let request = either error id (eitherDecode line)
    outcome <- try (evaluate (forceJSON (observe request)))
    case outcome of
     Right value -> L.putStrLn value
     Left (e :: SomeException) -> L.putStrLn (encode (object ["fatal" .= takeWhile (/='\n') (displayException e)]))
 where forceJSON v = let bytes = encode v in L.length bytes `seq` bytes
