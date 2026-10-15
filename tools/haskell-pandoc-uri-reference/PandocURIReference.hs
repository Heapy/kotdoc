{-# LANGUAGE OverloadedStrings #-}
-- Original Pandoc/Attoparsec/HTTP/Base64 observations, never Kotlin expectations.
module Main where
import Control.Applicative ((<|>),many)
import Control.Monad (forM_)
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString.Base64 as B64
import qualified Data.Set as S
import qualified Data.Attoparsec.Text as A
import qualified SourceUriPosix as P
import qualified SourceUriWindows as W
import Data.Attoparsec.Combinator (lookAhead)
import Data.Char (chr,ord)
import Data.List (foldl')
import Crypto.Hash (SHA256,Digest,hashlazy)
import Data.Binary.Put (runPut,putWord32be,putLazyByteString)
import System.IO
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
raw = map (chr.as) . arr
text = T.pack . raw
points = toJSON . map ord
pt = points . T.unpack
value p = p >>= pure . toJSON
parser :: Value -> A.Parser Value
parser v=case arr v of
 [String "pure",x]->pure x
 [String "fail",x]->fail(as x)
 [String "char",c]->value(A.char(chr(as c)) >>= pure.ord)
 [String "string",s]->pt <$> A.string(text s)
 [String "ascii",s]->pt <$> A.asciiCI(text s)
 [String "take",s,n]->pt <$> (if as n then A.takeWhile1 else A.takeWhile) (`elem` raw s)
 [String "eof"]->Null <$ A.endOfInput
 [String "or",p,q]->parser p <|> parser q
 [String "then",p,q]->parser p *> parser q
 [String "before",p,q]->parser p <* parser q
 [String "pair",p,q]->(\a b->toJSON [a,b]) <$> parser p <*> parser q
 [String "many",p]->toJSON <$> many(parser p)
 [String "many1",p]->toJSON <$> A.many1(parser p)
 [String "skip",p]->Null <$ A.skipMany(parser p)
 [String "look",p]->lookAhead(parser p)
 [String "match",p]->(\(s,x)->toJSON [pt s,x]) <$> A.match(parser p)
 [String "label",p,s]->parser p A.<?> as s
 _->error "parser expression"
result :: A.Result Value -> Value
result (A.Partial _)=object["tag" .= ("partial"::T.Text)]
result (A.Done rest x)=object["tag" .= ("done"::T.Text),"rest" .= pt rest,"value" .= x]
result (A.Fail rest ctx msg)=object["tag" .= ("fail"::T.Text),"rest" .= pt rest,"contexts" .= ctx,"message" .= msg]
trace p chunks=toJSON $ map result $ scanl A.feed (A.parse p (head chunks)) (tail chunks)
dataParser full=(\(bs,m)->toJSON [toJSON(B.unpack bs),pt m]) <$> (if full then P.pBase64DataURI <* A.endOfInput else P.pBase64DataURI)
exports t=toJSON [pt(P.urlEncode t),pt(P.escapeURI t),points(P.uriPathToPath t),points(W.uriPathToPath t)]
observe :: Value -> Value
observe v=case arr v of
 [String "uri",s]->object["exports" .= exports(text s),"isURI" .= P.isURI(text s)]
 [String "schemes"]->toJSON(S.toList P.schemes)
 [String "data",full,chunks]->trace(dataParser(as full))(map text(arr chunks))
 [String "complete",full,s]->result $ case A.parseOnly (dataParser(as full)) (text s) of
   Left msg -> A.Fail "" [] msg
   Right x -> A.Done "" x
 [String "kernel",expr,chunks]->trace(parser expr)(map text(arr chunks))
 [String "branch",expr,s,a,b]->let r=A.parse(parser expr)(text s) in toJSON
   [toJSON(map result(scanl A.feed r(map text(arr a)))),toJSON(map result(scanl A.feed r(map text(arr b))))]
 [String "base64",bs]->toJSON(B.unpack(B64.decodeLenient(B.pack(as bs))))
 [String "unicode",start,count]->let
   payload=runPut $ forM_ [as start .. as start+as count-1] $ \cp->do
     let bytes=encode(exports(T.singleton(chr cp)))
     putWord32be(fromIntegral(L.length bytes));putLazyByteString bytes
   in toJSON(show(hashlazy payload::Digest SHA256))
 [String "deep",kind,n]->let
   depth=as n::Int
   p=case as kind::Int of
    0->foldl' (\q _->(\x->toJSON((as x::Int)+1)) <$> q) (pure(toJSON(0::Int))) [1..depth]
    1->foldl' (\q _->q >>= \x->pure(toJSON((as x::Int)+1))) (pure(toJSON(0::Int))) [1..depth]
    _->error "deep"
   in result(A.parse p "")
 [String "fragmented",kind,n]->let
   depth=as n::Int
   k=as kind::Int
   p=case k of
    0->toJSON depth <$ A.string(T.replicate depth "a") <* A.endOfInput
    1->toJSON . length <$> many(A.char 'a') <* A.endOfInput
    2->Null <$ (A.string(T.replicate depth "a" <> "b") <|> A.string(T.replicate depth "a" <> "c")) <* A.endOfInput
    _->error "fragmented"
   r=foldl' A.feed (A.parse p "") (replicate depth "a" ++ [if k==2 then "c" else "", ""])
   in result r
 [String "lazy",kind]->let
   bad=pure(error "unforced result"::Int)
   p=case as kind::Int of
    0->bad *> pure Null
    1->Null <$ (show <$> bad)
    2->Null <$ many(A.char 'a' *> bad)
    3->Null <$ A.match bad
    _->error "lazy"
   in result $ A.feed(A.parse p "aaa!") ""
 _->error "request"
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
       eof<-hIsEOF stdin
       if eof then pure () else do
         line<-B.getLine
         case eitherDecodeStrict line of Left e->error e;Right v->L.putStrLn(encode(observe v))
         loop
 loop
