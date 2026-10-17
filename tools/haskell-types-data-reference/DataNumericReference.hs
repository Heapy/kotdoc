{-# LANGUAGE OverloadedStrings #-}
-- Original primitive gunfold and ConstrRep conversions, preserving raw IEEE bits.
import Data.Data
import Data.Char
import Data.Word
import Data.Ratio
import GHC.Real(Ratio((:%)))
import GHC.Float(castDoubleToWord64,castWord64ToDouble)
import Data.Functor.Identity
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
unfold c=runIdentity(gunfold(\_->error "primitive field")Identity c)
operation req=case as(field "op" req)::String of
 "int"->let n=read(as(field "a" req))::Integer;c=mkIntegralConstr(dataTypeOf(0::Int))n in toJSON(show(unfold c::Int))
 "rational"->let n=read(as(field "a" req));d=read(as(field "b" req));r=n:%d;c=mkRealConstr(dataTypeOf(0::Double))r in toJSON(show(castDoubleToWord64(unfold c::Double)))
 "double-ratio"->let bits=read(as(field "a" req))::Word64;x=castWord64ToDouble bits in case constrRep(toConstr x)of FloatConstr r->toJSON[show(numerator r),show(denominator r)];_->error "tag"
 "char"->let cp=as(field "cp" req)::Int;c=mkCharConstr(dataTypeOf('a'))(chr cp) in toJSON(ord(unfold c::Char))
 _->error "operation"
observe line = case eitherDecode line of
 Left e->error e
 Right req->do
  result<-try(evaluate(fromIntegral(B.length(encode(operation req)))::Integer))::IO(Either SomeException Integer)
  case result of
   Left e->B.putStrLn(encode(object["exception" .=takeWhile(/='\n')(displayException e)]))
   Right _->B.putStrLn(encode(operation req))
main=do
 input<-B.getContents
 mapM_ observe (B.lines input)
