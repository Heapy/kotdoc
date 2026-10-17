{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified Crypto.Cipher.ChaCha as C
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.Word
import Control.Exception
import Control.DeepSeq
import Control.Monad
import System.IO
field o k=parseMaybe(.:k)o
num o k d=maybe d id(field o k)::Int
bytes o k=B.pack(maybe [] id(field o k)::[Word8])
text o k d=maybe d id(field o k)::String
counter s=object["counter64" .= show(C.getCounter64 s),"counter32" .= show(C.getCounter32 s)]
stepState s o=let op=text o "op" "generate" in case op of
 "set64"->(B.empty,C.setCounter64(read(text o "value" "0"))s)
 "set32"->(B.empty,C.setCounter32(read(text o "value" "0"))s)
 "combine"->C.combine s(bytes o "bytes")
 _->C.generate s(num o "length" 0)
stepSimple s o=let op=text o "op" "generate" in case op of
 "set64"->(B.empty,C.setCounter64(read(text o "value" "0"))s)
 "set32"->(B.empty,C.setCounter32(read(text o "value" "0"))s)
 "block"->C.generateSimpleBlock(fromIntegral(num o "rounds" 8))s
 _->C.generateSimple s(num o "length" 0)
runSteps f probe s []=[]
runSteps f probe s (o:os)=let (b,next)=f s o in object["bytes" .= B.unpack b,"state" .= counter next,"previous" .= counter s,"previousProbe" .= B.unpack(probe s)]:runSteps f probe next os
demand name=case name of
 "generate-zero"->toJSON(B.unpack(fst(C.generate(undefined::C.State)0)::B.ByteString))
 "generate-negative"->toJSON(B.unpack(fst(C.generate(undefined::C.State)(-1))::B.ByteString))
 "combine-empty"->toJSON(B.unpack(fst(C.combine(undefined::C.State)B.empty)::B.ByteString))
 "initialize-key"->C.initialize undefined B.empty (undefined::B.ByteString) `seq` Null
 "initialize-nonce"->C.initialize undefined(B.replicate 16 0)B.empty `seq` Null
 "initialize-rounds"->C.initialize 7(B.replicate 16 0)(B.replicate 8 0) `seq` Null
 "initializeX-key"->C.initializeX undefined B.empty (undefined::B.ByteString) `seq` Null
 "initializeX-nonce"->C.initializeX undefined(B.replicate 32 0)B.empty `seq` Null
 "initializeSimple-seed"->C.initializeSimple B.empty `seq` Null
 "block-invalid"->toJSON(B.unpack(fst(C.generateSimpleBlock 7 undefined)::B.ByteString))
 "simple-zero-bottom"->toJSON(B.unpack(fst(C.generateSimple undefined 0)::B.ByteString))
 _->error "unknown demand"
run o | text o "kind" ""=="demand"=demand(text o "name" "")
run o=let kind=text o "kind" "state";steps=maybe [] id(field o "steps")::[Object] in
 if kind=="simple"then let s=C.initializeSimple(bytes o "seed") in object["initial" .= counter s,"steps" .= runSteps stepSimple (\st->fst(C.generateSimple st 33)::B.ByteString) s steps]
 else let s=(if kind=="x"then C.initializeX else C.initialize)(num o "rounds" 8)(bytes o "key")(bytes o "nonce") in object["initial" .= counter s,"steps" .= runSteps stepState (\st->fst(C.generate st 65)::B.ByteString) s steps]
main=do
 hSetBuffering stdout LineBuffering
 forever $ do
  eof<-isEOF
  if eof then fail "EOF" else do
   line<-B.getLine
   case eitherDecodeStrict line of
    Left e->error e
    Right(Object o)->do
     result<-try(evaluate(force(run o)))::IO(Either SomeException Value)
     L.putStrLn(encode(case result of Left e->object["error" .= takeWhile(/='\n')(displayException e)];Right v->v))
