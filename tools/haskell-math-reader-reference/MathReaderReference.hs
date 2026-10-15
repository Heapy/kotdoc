{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified MathWire as W
import qualified Text.TeXMath.Readers.TeX as R
import qualified Text.TeXMath.Readers.TeX.Commands as C
import Data.Aeson
import qualified Data.Map as M
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Char(ord)
import Text.TeXMath.Types(Exp)
import Text.Read(readMaybe)
import Data.Char(chr)
import Data.Binary.Put(runPut,putWord32be)
import Crypto.Hash(hashlazy,Digest,SHA256)
import System.IO
import Control.Monad(unless)
import qualified Control.Exception as E
cp=toJSON.map ord.T.unpack
value e=object["tree" .= W.ev e,"show" .= show e]
entries m=toJSON[toJSON[toJSON k,value v]|(k,v)<-M.toAscList m]
response v=case W.arr v of
 [String "read",s]->pure $ case R.readTeX(W.str s) of
  Left err->toJSON[String "Left",cp err]
  Right es->toJSON[String "Right",toJSON(map W.ev es),toJSON(show es)]
 [String "golden",s]->pure $ case (readMaybe(T.unpack(W.str s))::Maybe [Exp]) of
  Nothing->Null
  Just es->toJSON[String "Right",toJSON(map W.ev es),toJSON(show es)]
 [String "read-range",first,count]->do
  let outputs=[either ("L"<>) (T.pack . ("R"++) . show) (R.readTeX(T.singleton(chr n)))|n<-[W.as first..W.as first+W.as count-1]]
      bytes=runPut(mapM_(\t->putWord32be(fromIntegral(T.length t)) >> mapM_(putWord32be.fromIntegral.ord)(T.unpack t)) outputs)
  pure(object["sha" .= show(hashlazy bytes::Digest SHA256),"success" .= length(filter(T.isPrefixOf "R") outputs),"failure" .= length(filter(T.isPrefixOf "L") outputs)])
 [String "tables"]->pure(object["styleKeys" .= M.keys C.styleOps,"textKeys" .= M.keys C.textOps,"enclosures" .= entries C.enclosures,"operators" .= entries C.operators,"symbols" .= entries C.symbols,"units" .= entries C.siUnitMap])
 [String "style",key,es]->pure(maybe Null (value . ($ map W.expr(W.arr es))) (M.lookup(W.str key) C.styleOps))
 [String "text",key,s]->pure(maybe Null (value . ($ W.str s)) (M.lookup(W.str key) C.textOps))
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   b<-B.getLine
   result<-E.try $ do
    v<-response(either error id(eitherDecode(BL.fromStrict b)))
    E.evaluate(BL.length(encode v));pure v
   L.putStrLn(encode(either(\(_::E.SomeException)->String "host") id result));hFlush stdout;loop
