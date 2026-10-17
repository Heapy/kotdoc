{-# LANGUAGE OverloadedStrings,ScopedTypeVariables #-}
-- Imported RSA.Types Read instances are the unchanged pinned production source.
module Main where
import qualified Crypto.PubKey.RSA.Types as R
import Crypto.Debug(debugShow)
import Text.Read(readPrec,readListPrec,readPrec_to_S)
import Data.Aeson
import Data.Aeson.Types(parseMaybe)
import qualified Data.ByteString.Lazy.Char8 as L
import Control.Exception
import Control.DeepSeq
import System.IO
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
number o k d=maybe d id(field o k)::Int
input o=case text o "end""eof" of
 "root"->errorWithoutStackTrace "inputRoot"
 "tail"->prefix++errorWithoutStackTrace "inputTail"
 "head"->prefix++[errorWithoutStackTrace "inputHead"]
 _->prefix
 where prefix=text o "input"""
observe :: forall a.Read a=>Object->(a->String)->a->Value
observe o render _ = if method=="read"||method=="readPrec"
 then select(toJSON.render)(single::[(a,String)])
 else select(toJSON.map render)(multiple::[([a],String)])
 where
  s=input o
  p=number o "precedence"0
  method=text o "method""read"
  projection=text o "projection""alternative"
  single=if method=="read"then readsPrec p s else readPrec_to_S readPrec p s
  multiple=if method=="readList"then readList s else readPrec_to_S readListPrec p s
  select :: (b->Value)->[(b,String)]->Value
  select showValue rows=case projection of
   "ignore"->toJSON(42::Int)
   "value"->case rows of []->String "EMPTY";(v,_):_->showValue v
   "remainder"->case rows of []->String "EMPTY";(_,r):_->toJSON(take(number o "prefixLength"3)r)
   _->toJSON(not(null rows))

run o=do
 let v=case text o "type""PublicKey"of
       "PublicKey"->observe o show(undefined::R.PublicKey)
       "PrivateKey"->observe o debugShow(undefined::R.PrivateKey)
       _->observe o debugShow(undefined::R.KeyPair)
 r<-try(evaluate(force v))::IO(Either SomeException Value)
 return$either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id r
main=do hSetBuffering stdout LineBuffering;contents<-L.getContents;mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode)(L.lines contents)
