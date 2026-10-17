{-# LANGUAGE OverloadedStrings,FlexibleInstances #-}
module Main where
import qualified Crypto.PubKey.RSA.PKCS15 as P
import qualified Crypto.Random as R
import qualified Crypto.Random.Types as T
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Control.Exception
import Control.DeepSeq
field o k=parseMaybe(.:k)o
data NoDraw=NoDraw
instance R.DRG NoDraw where randomBytesGenerate _ _=error "unexpected-draw"
run o=do
 let k=maybe 0 id(field o "length")::Int
     n=maybe 0 id(field o "messageLength")::Int
     msg=B.replicate n 7
     op=maybe "signature"id(field o "op")::String
     result=if op=="signature"then P.padSignature k msg else fst(R.withDRG NoDraw(P.pad k msg))
     value=case result of Left e->show e;Right _->"Right"
 answer<-try(evaluate(force value))::IO(Either SomeException String)
 return(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])toJSON answer)
main=L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
