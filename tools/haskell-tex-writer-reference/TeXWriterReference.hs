{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Observation transport only: no replacement writer/reader or expectation code.
module Main where
import qualified MathWire as W
import Text.TeXMath.Writers.TeX
import Text.TeXMath.Readers.TeX(readTeX)
import Text.TeXMath.Readers.MathML(readMathML)
import Text.TeXMath.Readers.OMML(readOMML)
import Text.TeXMath.Types
import Text.TeXMath.Unicode.ToTeX(records)
import Text.Show.Pretty(ppShow)
import Text.Read(readMaybe)
import Data.Aeson
import Data.Char(chr,ord)
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Control.Exception as E
import Control.Monad(unless)
import Data.Binary.Put
import Crypto.Hash(SHA256,Digest,hashlazy)
import System.IO
parse fmt t=case fmt of
 "native"->maybe (Left "native read failed") Right (readMaybe(T.unpack t))
 "tex"->readTeX t
 "mml"->readMathML t
 "mathml"->readMathML t
 "omml"->readOMML t
 _->error "format"
result= either (\e->toJSON[String "Left",toJSON e]) (\es->toJSON[String "Right",toJSON(map W.ev es)])
deep kind n=iterate step(EIdentifier "x")!!n where
 step e=case kind of
  "group"->EGrouped[e]
  "sqrt"->ESqrt e
  "sub"->ESub e(ENumber "2")
  "under"->EUnder True e(ESymbol Accent "\x0332")
  "narrow"->EGrouped[EStyled TextBold[e]]
  _->error "deep"
response v=case W.arr v of
 [String "records"]->pure(toJSON[[toJSON(ord(uchar r)),toJSON(show(category r)),toJSON(map fst(commands r))]|r<-records])
 [String "write",es]->pure(toJSON(writeTeX(map W.expr(W.arr es))))
 [String "with",env,es]->pure(toJSON(writeTeXWith(map W.str(W.arr env))(map W.expr(W.arr es))))
 [String "environment",dt,s]->pure(toJSON(addLaTeXEnvironment(if W.str dt=="DisplayInline" then DisplayInline else DisplayBlock)(W.str s)))
 [String "parse",fmt,s]->pure(result(parse(W.as fmt)(W.str s)))
 [String "pretty",es]->pure(toJSON(ppShow(map W.expr(W.arr es))))
 [String "roundtrip",es]->let x=map W.expr(W.arr es);r=readTeX(writeTeX x) in pure(object["actual" .= result r,"original" .= result(Right x),"matchesOriginal" .= (r==Right x)])
 [String "from-mathml",s]->pure(toJSON(either (\e->toJSON[String "Left",toJSON e]) (\t->toJSON[String "Right",toJSON t])(writeTeX <$> readMathML(W.str s))))
 [String "deep",kind,n,env]->pure(toJSON(writeTeXWith(map W.str(W.arr env))[deep(W.as kind)(W.as n)]))
 [String "write-range",env,first,count]->do
  let en=map W.str(W.arr env)
      outputs=[writeTeXWith en[e]|n<-[W.as first..W.as first+W.as count-1],let s=T.singleton(chr n),e<-[EIdentifier s,EText TextNormal s,ESymbol Ord s,ESymbol Bin s]]
      bytes=runPut(mapM_(\t->putWord32be(fromIntegral(T.length t)) >> mapM_(putWord32be.fromIntegral.ord)(T.unpack t))outputs)
  pure(toJSON(show(hashlazy bytes::Digest SHA256)))
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-B.getLine
   out<-E.try $ do
    value<-response(either error id(eitherDecode(BL.fromStrict line)))
    E.evaluate(BL.length(encode value));pure value
   L.putStrLn(encode(either (\(e::E.SomeException)->object["host" .= show e]) id out));hFlush stdout;loop
