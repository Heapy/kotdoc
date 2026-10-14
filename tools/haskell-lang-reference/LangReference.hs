{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Transport only; every operation invokes unchanged Text.Collate.Lang.
module Main where
import Data.Aeson
import qualified Data.Text as T
import qualified Data.ByteString as BS
import qualified Data.ByteString.Char8 as BSC
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.Vector as V
import qualified Data.Binary as Bin
import Data.Char(chr,ord)
import Data.Int(Int64)
import Data.String(fromString)
import Numeric(readHex)
import Control.Exception(try,SomeException,evaluate,displayException)
import System.IO(stdout,hFlush,isEOF)
import Text.Collate.Lang
import OriginalLangExamples
as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr (Array v) = V.toList v
arr _ = error "array"
chars v = map (chr . as) (arr v)
txt = T.pack . chars
maybeText Null = Nothing
maybeText v = Just (txt v)
lang v = case arr v of
 [a,b,c,d,e,f] -> Lang (txt a) (maybeText b) (maybeText c) (map txt (arr d))
    [(txt k,[(txt x,txt y) | pair<-arr vs, let [x,y]=arr pair]) | pair<-arr e,let [k,vs]=arr pair] (map txt (arr f))
 _ -> error "lang"
project :: Lang -> Value
project (Lang a b c d e f) = toJSON [toJSON a,toJSON b,toJSON c,toJSON d,toJSON e,toJSON f]
hex = T.pack . concatMap (\w -> ["0123456789abcdef"!!fromIntegral(w `div` 16),"0123456789abcdef"!!fromIntegral(w `mod` 16)]) . BL.unpack
unhex s = BL.pack(go(T.unpack s)) where
 go []=[]
 go(a:b:rest)=case readHex[a,b] of [(n,"")]->n:go rest;_->error "hex"
 go _=error "hex"
fields p l = object ["fields" .= project l,"rendered" .= renderLang l,
 "shown" .= T.pack(showsPrec p l ""),"encoded" .= hex(Bin.encode l)]
parsed s = case parseLang s of Left e -> object["error" .= e];Right l -> object["success" .= fields 0 l]
relation a b = object["eq" .= (a==b),"ne" .= (a/=b),"lt" .= (a<b),"le" .= (a<=b),"gt" .= (a>b),"ge" .= (a>=b),
 "compare" .= show(compare a b),"min" .= project(min a b),"max" .= project(max a b)]
response x = case arr x of
 [String "parse",s] -> pure(parsed(txt s))
 [String "fromString",s] -> pure(fields 0 (fromString(chars s)))
 [String "record",l,p] -> pure(fields (as p) (lang l))
 [String "relation",a,b] -> pure(relation(lang a)(lang b))
 [String "lookup",q,vs] -> pure (case lookupLang (lang q) [(lang l,as n::Int) | pair<-arr vs,let [l,n]=arr pair] of
    Nothing -> Null;Just(l,n)->object["fields" .= project l,"value" .= n])
 [String "decode",s] -> pure (case Bin.decodeOrFail(unhex(as s)) of
    Left(rest,n,e) -> object["error" .= e,"consumed" .= n,"remaining" .= hex rest]
    Right(rest,n,l) -> object["success" .= fields 0 l,"consumed" .= n,"remaining" .= hex rest])
 [String "original",n] -> let (s,l)=langPairs!!as n in pure(object["input" .= s,"expected" .= project l,
    "parsed" .= parsed s,"roundtrip" .= parsed(renderLang l)])
 _ -> error "operation"
main=loop where
 loop=do
  end<-isEOF
  if end then pure() else do
   line<-BSC.getLine
   let v=case eitherDecode(BL.fromStrict line) of Right a->a;Left e->error e
   result<-response v
   e<-try(evaluate(BL.length(encode result)))::IO(Either SomeException Int64)
   C.putStrLn(encode(case e of Right _->result;Left err->object["exception" .= takeWhile(/='\n')(displayException err)]))
   hFlush stdout;loop
