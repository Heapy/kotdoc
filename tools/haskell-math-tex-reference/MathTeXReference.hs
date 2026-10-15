{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified Text.TeXMath.Readers.TeX.Macros as M
import qualified Text.TeXMath.Unicode.ToTeX as U
import Text.TeXMath.Types
import Text.TeXMath.TeX
import qualified Text.TeXMath.Unicode.ToUnicode as F
import qualified Text.TeXMath.Shared as S
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.Map as Map
import Data.Char(ord,chr)
import Control.Monad(unless)
import qualified Control.Exception as E
import System.IO
import Text.Parsec hiding(State,Consumed,Empty,Error)
import qualified Text.Parsec.Prim as P
import Text.Parsec.Error
import Data.Functor.Identity(runIdentity)
import Text.Parsec.Pos(newPos)
import Data.Binary.Put(runPut,putWord32be)
import Crypto.Hash(hashlazy,Digest,SHA256)
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success a->a;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
chars v=map(chr.as)(arr v)
text=T.pack.chars
cp=toJSON.map ord
showv :: Show a => a -> Value
showv=toJSON.show
record r=object["char" .= ord(uchar r),"commands" .= commands r,"category" .= show(category r),"comments" .= comments r,"show" .= show r]
tv (ControlSeq s)=toJSON[String "ControlSeq",toJSON s]
tv (Token c)=toJSON[String "Token",toJSON(ord c)]
tv (Literal s)=toJSON[String "Literal",toJSON s]
tv (Grouped xs)=toJSON[String "Grouped",toJSON(map tv xs)]
tv Space=toJSON[String "Space"]
ev (ESymbol ty t)=toJSON[String "ESymbol",showv ty,toJSON t]
ev _=error "non-symbol"
position p=toJSON[cp(sourceName p),showv(sourceLine p),showv(sourceColumn p)]
err e=object["position" .= position(errorPos e),"messages" .= [toJSON[toJSON kind,cp s]|m<-errorMessages e,let(kind,s)=case m of SysUnExpect s->("SysUnExpect"::String,s);UnExpect s->("UnExpect",s);Expect s->("Expect",s);Message s->("Message",s)],"pretty" .= cp(show e)]
reply consumed r=case r of
 P.Error e->object["consumed" .= consumed,"ok" .= False,"error" .= err e]
 P.Ok m st e->object["consumed" .= consumed,"ok" .= True,"macro" .= show m,"rest" .= cp(P.stateInput st),"position" .= position(P.statePos st),"user" .= show(P.stateUser st),"error" .= err e]
response req=case arr req of
 [String "document",s]->let(ms,rest)=M.parseMacroDefinitions(text s) in pure(object["macros" .= map show ms,"rest" .= cp(T.unpack rest),"out" .= cp(T.unpack(M.applyMacros ms rest))])
 [String "definitions",s]->let(ms,rest)=M.parseMacroDefinitions(text s) in pure(object["macros" .= map show ms,"rest" .= cp(T.unpack rest)])
 [String "round",defs,s]->let(ms,rest)=M.parseMacroDefinitions(text defs) in pure(object["macros" .= map show ms,"rest" .= cp(T.unpack rest),"out" .= cp(T.unpack(M.applyMacros ms(text s)))])
 [String "apply",defs,s]->case traverse (parse M.pMacroDefinition "definition" . chars)(arr defs) of
  Left e->pure(object["invalid" .= err e])
  Right ms->pure(object["macros" .= map show ms,"out" .= cp(T.unpack(M.applyMacros ms(text s)))])
 [String "parse",name,ln,col,user,s]->do
  let initial=P.State(chars s)(newPos(chars name)(read(as ln))(read(as col)))(read(as user)::Integer)
      r=runIdentity(P.runParsecT M.pMacroDefinition initial)
  pure(case r of P.Consumed x->reply True(runIdentity x);P.Empty x->reply False(runIdentity x))
 [String "records"]->pure(toJSON(map record U.records))
 [String "symbol-map"]->pure(toJSON[toJSON[toJSON k,ev v]|(k,v)<-Map.toAscList U.symbolMap])
 [String "types",cs]->pure(toJSON[show(U.getSymbolType(chr(as c)))|c<-arr cs])
 [String "types-range",first,count]->pure(toJSON[(c,show ty)|c<-[as first..as first+as count-1],let ty=U.getSymbolType(chr c),ty/=Ord])
 [String "unicode",env,s]->do
  let ts=U.getTeXMath(text s)(map text(arr env))
      rendered=foldr renderTeX "" ts
  pure(object["tokens" .= map tv ts,"show" .= show ts,"out" .= cp(T.unpack rendered)])
 [String "unicode-range",env,first,count]->do
  let cs=[as first..as first+as count-1]
      ts=U.getTeXMath(T.pack(map chr cs))(map text(arr env))
      out=foldr renderTeX "" ts
  pure(object["tokens" .= length ts,"length" .= T.length out,"sha" .= show(hashlazy(runPut(mapM_(putWord32be.fromIntegral.ord)(T.unpack out)))::Digest SHA256)])
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   b<-B.getLine
   result<-E.try $ do
    v<-response(either error id(eitherDecode(BL.fromStrict b)))
    E.evaluate(BL.length(encode v));pure v
   C.putStrLn(encode(either(\(_::E.SomeException)->String "host") id result));hFlush stdout;loop
