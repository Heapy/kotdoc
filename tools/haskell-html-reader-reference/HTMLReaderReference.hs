{-# LANGUAGE OverloadedStrings, GeneralizedNewtypeDeriving, FlexibleContexts #-}
module Main where
import qualified OriginalReaderTests as Tests
import qualified OriginalSmart
import qualified Text.Pandoc.Readers.HTML as Reader
import Text.Pandoc.Definition (Pandoc(..))
import Text.Pandoc.Options
import Text.Pandoc.Extensions
import Text.Pandoc.Class
import qualified Text.Pandoc.Class as PC
import Text.Pandoc.Error (PandocError)
import Control.Monad.Reader (ReaderT,runReaderT,ask)
import Control.Monad.State.Strict (StateT,runStateT,modify)
import Control.Monad.Trans.Class (lift)
import Control.Monad.Except (MonadError)
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Parsing
import Text.HTML.TagSoup (Tag(..),canonicalizeTags,renderTags,renderTagsOptions,renderOptions,RenderOptions(..))
import Data.Char (isAlphaNum)
import Text.Pandoc.Writers.Native (writeNative)
import Text.Pandoc.Writers.HTML (writeHtml5String)
import Text.Pandoc.Walk (walk)
import qualified Text.Parsec as Q
import qualified Text.Pandoc.Builder as B
import qualified Text.Pandoc.Shared as Shared
import qualified ReaderCases as Cases
import Text.Pandoc.Arbitrary ()
import Test.QuickCheck (arbitrary,resize)
import qualified Test.QuickCheck as QC
import Data.IORef
import Data.Binary.Put
import Crypto.Hash (SHA256,Digest,hashlazy)
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
import qualified Text.Parsec.Pos as Pos
import qualified Data.Map as M
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.ByteString as Bytes
import qualified Data.ByteString.Char8 as BS
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Text.Pandoc.UTF8 as UTF8
import Data.Text.Encoding.Error (UnicodeException)
import System.IO.Unsafe (unsafePerformIO)
import qualified Data.Text as T
import Data.Char(chr,ord)
import Data.Default
import Data.List(foldl')
import Data.Time
import Data.Int(Int64)
import qualified Control.Exception as E
import System.IO
newtype Fixture a = Fixture { unFixture :: ReaderT [(T.Text,(Bytes.ByteString,Maybe T.Text))] (StateT [T.Text] PandocPure) a }
  deriving (Functor,Applicative,Monad,MonadError PandocError)
fp = Fixture . lift . lift
instance PandocMonad Fixture where
 lookupEnv = fp . PC.lookupEnv
 getCurrentTime = fp PC.getCurrentTime
 getCurrentTimeZone = fp PC.getCurrentTimeZone
 newStdGen = fp PC.newStdGen
 newUniqueHash = fp PC.newUniqueHash
 openURL u = Fixture $ do
   lift $ modify (++[u])
   urls<-ask
   maybe (lift $ lift $ PC.openURL u) pure (lookup u urls)
 readFileLazy = fp . PC.readFileLazy
 readFileStrict = fp . PC.readFileStrict
 readStdinStrict = fp PC.readStdinStrict
 glob = fp . PC.glob
 fileExists = fp . PC.fileExists
 getDataFileName = fp . PC.getDataFileName
 getModificationTime = fp . PC.getModificationTime
 getCommonState = fp PC.getCommonState
 putCommonState = fp . PC.putCommonState
 logOutput = fp . PC.logOutput
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
 result<-Reader.readHtml(as opts)(sources src)
 s<-getCommonState
 pure(result,common s)
 of
 Left e->object["error" .= show e]
 Right(d,s)->object["document" .= d,"common" .= s]
pv p=toJSON[pt(T.pack(Pos.sourceName p)),toJSON(Pos.sourceLine p),toJSON(Pos.sourceColumn p)]
sourcev(Sources xs)=toJSON[[pv p,pt t]|(p,t)<-xs]
readURLs opts src urls = case runPure $ do
 let table=[(text u,(Bytes.pack(map as(arr b)),as m))|row<-arr urls,let[u,b,m]=arr row]
 (d,calls)<-runStateT(runReaderT(unFixture(Reader.readHtml(as opts)(sources src)))table)[]
 s<-getCommonState
 pure(d,calls,common s)
 of Left e->object["error" .= show e]
    Right(d,calls,s)->object["document" .= d,"calls" .= calls,"common" .= s]
property v = let Pandoc _ bs = as v
                 d=walk Tests.removeRawInlines $ walk Tests.makeRoundTrip $ Pandoc mempty bs
                 rewrite x = let html= either(error.show)id(runPure(writeHtml5String def{writerWrapText=WrapPreserve} x))
                                 result=either(error.show)id(runPure(Reader.readHtml def(T.snoc html '\n')))
                              in (html,result)
                 (h1,d1)=rewrite d
                 (h2,d2)=rewrite d1
                 (h3,d3)=rewrite d2
              in object["normalized" .= d,"rewrites" .= [(h1,d1),(h2,d2),(h3,d3)],"verdict" .= Tests.roundTrip(B.fromList bs)]
predicate n t=case n of
 "any"->True
 "block"->Reader.isBlockTag t
 "inline"->Reader.isInlineTag t
 "text"->Reader.isTextTag t
 "comment"->Reader.isCommentTag t
 "div"->case t of TagOpen "div" _->True;_->False
 _->False
sourceOp kind opts src pred = sourceReply def{stateOptions=as opts}(sources src) $ case kind of
 "tag"->do (t,raw)<-Reader.htmlTag(predicate pred);pure(toJSON[pt(T.pack(show t)),pt raw])
 "balanced"->pt <$> Reader.htmlInBalanced(predicate pred)
 _->error "source kind"
sourceReply initial src action=case runPure(Q.runParserT parser initial "outer" src) of
 Left e->object["error" .= show e]
 Right(Left e)->object["ok" .= False,"error" .= show e]
 Right(Right x)->x
 where parser=do
        value<-action;p<-Q.getPosition;r<-Q.getInput;s<-Q.getState
        pure(object["ok" .= True,"value" .= value,"position" .= pv p,"rest" .= sourcev r,"quote" .= show(stateQuoteContext s),"last" .= fmap pv(stateLastStrPos s)])
smartOp kind opts src ctx last=sourceReply initial(sources src) action
 where initial=def{stateOptions=as opts,stateQuoteContext=case ctx of 1->InSingleQuote;2->InDoubleQuote;_->NoQuote,stateLastStrPos=if last then Just(Pos.initialPos "outer") else Nothing}
       atom=smartPunctuation atom Q.<|> do c<-anyChar; if isAlphaNum c then updateLastStrPos else pure();pure(B.str(T.singleton c))
       unit p=p>>pure(String "()")
       action=case kind of
         "single-quoted"->toJSON.B.toList <$> OriginalSmart.singleQuoted atom
         "double-quoted"->toJSON.B.toList <$> OriginalSmart.doubleQuoted atom
         "single-start"->unit singleQuoteStart
         "single-end"->unit singleQuoteEnd
         "double-start"->unit doubleQuoteStart
         "double-end"->unit doubleQuoteEnd
         "apostrophe"->toJSON.B.toList <$> apostrophe
         "close"->toJSON.B.toList <$> doubleCloseQuote
         "ellipsis"->toJSON.B.toList <$> ellipses
         "dash"->toJSON.B.toList <$> dash
         "smart"->toJSON.B.toList <$> smartPunctuation atom
         _->error "smart kind"
tag v=case arr v of
 [Number 0,n,a]->TagOpen(text n)[(text k,text v)|x<-arr a,let[k,v]=arr x]
 [Number 1,n]->TagClose(text n)
 [Number 2,s]->TagText(text s)
 [Number 3,s]->TagComment(text s)
 [Number 4,s]->TagWarning(text s)
 [Number 5,r,c]->TagPosition(as r)(as c)
 _->error "tag"
tv t=toJSON $ case t of
 TagOpen n a->[toJSON(0::Int),pt n,toJSON[[pt k,pt v]|(k,v)<-a]]
 TagClose n->[toJSON(1::Int),pt n]
 TagText s->[toJSON(2::Int),pt s]
 TagComment s->[toJSON(3::Int),pt s]
 TagWarning s->[toJSON(4::Int),pt s]
 TagPosition r c->[toJSON(5::Int),toJSON r,toJSON c]
render ts=let tags=map tag(arr ts) in object["canonical" .= map tv(canonicalizeTags tags),"default" .= pt(renderTags tags),"pandoc" .= pt(Shared.renderTags' tags),"custom" .= pt(renderTagsOptions renderOptions{optEscape= \s->"["<>s<>"]",optMinimize=const True,optRawTag=(=="raw")}tags)]
decodeText bs=unsafePerformIO $ do
 let t=UTF8.toText bs
 result<-E.try(E.evaluate(T.length t))::IO(Either UnicodeException Int)
 pure $ case result of
   Left e->object["unicodeException" .= show e]
   Right _->object["text" .= pt t]
originalQuickCheck=unsafePerformIO $ mapM run (concatMap Cases.properties Tests.tests)
 where run(name,n,f)=do
        observations<-newIORef []
        let predicate bs=QC.ioProperty $ do
              let result=f bs
              E.evaluate result
              modifyIORef' observations (++[(B.doc bs,result)])
              pure result
        result<-QC.quickCheckWithResult QC.stdArgs{QC.chatty=False,QC.replay=Just(mkQCGen 83001,0)} $ QC.withMaxSuccess n $ QC.forAllShrinkShow QC.arbitrary QC.shrink show predicate
        values<-readIORef observations
        pure(name,n,QC.isSuccess result,values)
observe v=case arr v of
 [String "defaults"]->toJSON(def::ReaderOptions)
 [String "format-defaults"]->toJSON(def{readerExtensions=getDefaultExtensions "html"}::ReaderOptions)
 [String "tests"]->toJSON[(n,pt t,g,d)|(n,t,g,d)<-concatMap Cases.units Tests.tests]
 [String "read",o,s,fs,env]->readIt o s fs env
 [String "read-urls",o,s,urls]->readURLs o s urls
 [String "source",kind,o,s,p]->sourceOp(as kind)o s(as p)
 [String "smart",kind,o,s,c,l]->smartOp(as kind)o s(as c)(as l)
 [String "render",ts]->render ts
 [String "utf8-all-scalars"]->toJSON(show(hashlazy(runPut(mapM_ (\cp->let t=UTF8.toText(UTF8.fromText(T.singleton(chr cp)));cs=T.unpack t in putWord32le(fromIntegral(length cs))>>mapM_(putWord32le.fromIntegral.ord)cs)[0..0x10ffff]))::Digest SHA256))
 [String "utf8-row",n]->toJSON[decodeText(Bytes.pack[as n,b])|b<-[0..255]]
 [String "utf8",bytes]->decodeText(Bytes.pack(map as(arr bytes)))
 [String "native",standalone,d]->toJSON(either(error.show)id(runPure(writeNative def{writerTemplate=if as standalone then Just mempty else Nothing}(as d))))
 [String "date",d,p,tz]->toJSON(showGregorian(localDay(utcToLocalTime(minutesToTimeZone(as tz))(UTCTime(ModifiedJulianDay(as d))(picosecondsToDiffTime(as p))))))
 [String "date-range",d,n]->toJSON[showGregorian(ModifiedJulianDay(as d+i))|i<-[0..as n-1]]
 [String "quickcheck-properties"]->toJSON originalQuickCheck
 [String "properties"]->toJSON [(name,n,[let bs=unGen(resize size arbitrary)(mkQCGen seed)size::B.Blocks in (seed,size,B.doc bs,f bs)| (seed,size)<-zip [83001..] [0..n-1]])| (name,n,f)<-concatMap Cases.properties Tests.tests]
 [String "roundtrip",d]->property d
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
        Left e->L.putStrLn(encode(case E.fromException e::Maybe UnicodeException of
           Just u->object["unicodeException" .= show u]
           Nothing->object["exception" .= takeWhile(/='\n')(E.displayException e)]))
        Right _->L.putStrLn output
       loop
 loop
