{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified OriginalReaderTests as Tests
import qualified Text.Pandoc.Readers.LaTeX as Reader
import Text.Pandoc.Options
import Text.Pandoc.Extensions
import Text.Pandoc.Class
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Parsing (Sources(..),ParserState(..),extractMacros)
import Text.Pandoc.Writers.Native (writeNative)
import qualified Text.Parsec as Q
import qualified Text.Pandoc.Builder as B
import qualified Text.Pandoc.Shared as Shared
import qualified Text.Parsec.Pos as Pos
import qualified Data.Map as M
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.ByteString as Bytes
import qualified Data.ByteString.Char8 as BS
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import Data.Char(chr,ord)
import Data.Default
import Data.List(foldl')
import Data.Time
import Data.Int(Int64)
import qualified Control.Exception as E
import System.IO
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success a->a;Error e->error e
arr(Array x)=V.toList x
arr _=error "array"
text v=T.pack(map(chr.as)(arr v))
pt=toJSON . map ord . T.unpack
pos v=let[n,l,c]=arr v in Pos.newPos(T.unpack(text n))(as l)(as c)
sources v=Sources[(pos p,text t)|x<-arr v,let[p,t]=arr x]
common s=object["log" .= reverse(stLog s),"resources" .= stResourcePath s,"cache" .= show(stTranslations s)]
readIt opts src files env=case runPure $ do
 ps<-getPureState
 let tree=foldl'(\t item->let[p,b]=arr item in insertInFileTree(T.unpack(text p))(FileInfo(stTime ps)(Bytes.pack(map as(arr b))))t)mempty(arr files)
 putPureState ps{stFiles=tree,stEnv=[(text k,text v)|x<-arr env,let[k,v]=arr x]}
 result<-Reader.readLaTeX(as opts)(sources src)
 s<-getCommonState
 pure(result,common s)
 of
 Left e->object["error" .= show e]
 Right(d,s)->object["document" .= d,"common" .= s]
pv p=toJSON[pt(T.pack(Pos.sourceName p)),toJSON(Pos.sourceLine p),toJSON(Pos.sourceColumn p)]
sourcev(Sources xs)=toJSON[[pv p,pt t]|(p,t)<-xs]
bridge kind opts src=case runPure(Q.runParserT action def{stateOptions=as opts} "outer"(sources src)) of
 Left e->object["error" .= show e]
 Right(Left e)->object["ok" .= False,"error" .= show e]
 Right(Right x)->x
 where action=do
        value<-case kind of
          "inline"->toJSON <$> Reader.rawLaTeXInline
          "block"->toJSON <$> Reader.rawLaTeXBlock
          "command"->toJSON . B.toList <$> Reader.inlineCommand
          _->error "bridge kind"
        s<-Q.getState;p<-Q.getPosition;rest<-Q.getInput
        pure(object["ok" .= True,"value" .= value,"position" .= pv p,"rest" .= sourcev rest,"macros" .= [(k,show v)|(k,v)<-M.toAscList(extractMacros s)]])
observe v=case arr v of
 [String "defaults"]->toJSON(def{readerExtensions=getDefaultExtensions "latex"}::ReaderOptions)
 [String "tests"]->toJSON[(n,pt t,g,d)|(n,t,g,d)<-concat Tests.tests]
 [String "read",o,s,fs,env]->readIt o s fs env
 [String "bridge",kind,o,s]->bridge(as kind)o s
 [String "native",standalone,d]->toJSON(either(error.show)id(runPure(writeNative def{writerTemplate=if as standalone then Just mempty else Nothing}(as d))))
 [String "date",d,p,tz]->toJSON(showGregorian(localDay(utcToLocalTime(minutesToTimeZone(as tz))(UTCTime(ModifiedJulianDay(as d))(picosecondsToDiffTime(as p))))))
 [String "date-range",d,n]->toJSON[showGregorian(ModifiedJulianDay(as d+i))|i<-[0..as n-1]]
 [String "case-data"]->toJSON[(cp,pt(T.toUpper t),pt(T.toLower t),pt(T.toTitle t))|cp<-[0..0x10ffff],let t=T.singleton(chr cp),T.toUpper t/=t || T.toLower t/=t || T.toTitle t/=t]
 [String "tabs",n,t]->pt(Shared.tabFilter(as n)(text t))
 _->error "request"
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
      eof<-hIsEOF stdin
      if eof then pure() else do
       line<-BS.getLine
       let output=encode(observe(either error id(eitherDecodeStrict line)))
       result<-E.try(E.evaluate(L.length output))::IO(Either E.SomeException Int64)
       case result of
        Left e->L.putStrLn(encode(object["exception" .= takeWhile(/='\n')(E.displayException e)]))
        Right _->L.putStrLn output
       loop
 loop
