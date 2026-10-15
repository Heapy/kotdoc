{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Calls the complete original library; no Kotlin expectations are constructed.
module Main where
import Control.Exception as E
import Control.Monad(unless)
import Control.Monad.State.Strict
import Data.Aeson
import qualified Data.Aeson.KeyMap as KM
import qualified Data.Aeson.Key as K
import Data.List(sortOn)
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString.Lazy as BL
import Data.Binary.Put
import Crypto.Hash(SHA256,Digest,hashlazy)
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.Set as S
import qualified Data.Vector as V
import Data.Char(chr)
import Data.Default
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as B
import qualified Text.Pandoc.Shared as P
import Text.Pandoc.Walk
import Text.Pandoc.Readers.Native(readNative)
import Text.Pandoc.Class(runPure)
import System.IO
import System.Environment(getArgs)
import System.Exit(exitFailure)
import Test.QuickCheck hiding(Success)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import Text.Pandoc.Arbitrary()
import qualified OriginalSharedProperty as Original
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr(Array v)=V.toList v
arr _=error "array"
raw v=map(chr.as)(arr v)
wrap kind a xs=case kind::Int of
 0->xs
 1->B.emph xs
 2->B.spanWith a xs
 3->B.linkWith a "target" "title" xs
 4->B.codeWith a(P.stringifyInlines(B.toList xs))
 5->B.space<>xs<>B.softbreak
 6->mempty
 7->B.str "replacement"
 8->B.doubleQuoted xs
 _->error "wrapper"
meta kind v=case kind::Int of
 0->as v::MetaValue
 1->B.toMetaValue(as v::T.Text)
 2->B.toMetaValue(raw v)
 3->B.toMetaValue(as v::Bool)
 4->B.toMetaValue(B.fromList(as v)::B.Inlines)
 5->B.toMetaValue(B.fromList(as v)::B.Blocks)
 6->B.toMetaValue(as v::[MetaValue])
 7->B.toMetaValue(as v::M.Map T.Text MetaValue)
 _->error "metadata conversion"
observeInlines a xs=object["code" .= B.toList(P.formatCode a(B.fromList xs)),"stringify" .= P.stringifyInlines xs,"genericStringify" .= P.stringify xs]
traceInlines :: Pandoc -> ([Int],[Int])
traceInlines p=(query(\(xs::[Inline])->[length xs])p,snd(runState(walkM(\(xs::[Inline])->modify(\s->s++[length xs])>>pure xs)p)[]))
deep kind n=foldr step (Str "leaf") [1..n::Int]
 where step k i=case kind::Int of
        0->Span(T.pack(show k),[],[])[i]
        1->if k `mod` 2==0 then Note[Para[i]] else Emph[i]
        2->Quoted DoubleQuote[i]
        _->error "deep kind"
peel xs=go [] xs where
 go acc [Span _ ys]=go(0:acc)ys
 go acc [Emph ys]=go(1:acc)ys
 go acc [Note[Para ys]]=go(2:acc)ys
 go acc ys=toJSON[toJSON(reverse acc),toJSON ys]
-- A typed raw-codepoint transport avoids coupling the aggregate witness to
-- object key order or either platform's JSON string escape spelling.
wire Null=toJSON[Number 0]
wire(Bool b)=toJSON[Number 1,Bool b]
wire(Number n)=toJSON[Number 2,Number n]
wire(String t)=toJSON[Number 3,toJSON(map fromEnum(T.unpack t))]
wire(Array xs)=toJSON[Number 4,toJSON(map wire(V.toList xs))]
wire(Object m)=toJSON[Number 5,toJSON[[toJSON(map fromEnum(T.unpack(K.toText k))),wire x]|(k,x)<-sortOn(K.toText.fst)(KM.toList m)]]
response v=case arr v of
 [String "samples",seed,size,count]->toJSON(unGen(vectorOf(as count)(resize(as size)(arbitrary::Gen[Inline])))(mkQCGen(as seed))0)
 [String "property",x]->toJSON(Original.stringifyInlinesMatchesStringify(as x))
 [String "inlines",a,x]->observeInlines(as a)(as x)
 [String "extract",kind,a,x]->toJSON(B.toList(P.extractSpaces(wrap(as kind)(as a))(B.fromList(as x))))
 [String "meta",k,kind,x,m]->toJSON(P.addMetaField(as k)(meta(as kind)x)(as m))
 [String "span-tags"]->toJSON(S.toAscList P.htmlSpanLikeElements)
 [String "native",x]->case runPure(readNative def(as x::T.Text))of Right p->toJSON(p::Pandoc);Left e->error(show e)
 [String "inline-lists",p]->toJSON(query(\(xs::[Inline])->[xs])(as p::Pandoc))
 [String "walk",p]->let
  doc=as p::Pandoc
  (before,after)=traceInlines doc
  addBang (Str s)=Str(s<>"!");addBang x=x
  in object["before" .= before,"after" .= after,"inline" .= walk addBang doc,"lists" .= walk(\(xs::[Inline])->xs++[Str "!"])doc]
 [String "deep",kind,n]->let xs=[deep(as kind)(as n)] in object["code" .= peel(B.toList(P.formatCode nullAttr(B.fromList xs))),"stringify" .= P.stringifyInlines xs]
 [String "unicode",start,count]->let
  outputs=[encode(wire(toJSON[ toJSON(B.toList(B.text t)),toJSON(P.stringifyInlines[Quoted DoubleQuote[Str t]]),toJSON(B.toList(P.formatCode nullAttr(B.fromList[Str t]))),toJSON(B.toList(P.extractSpaces B.emph(B.fromList[Space,Str t,SoftBreak]))),toJSON(P.addMetaField "k" [c](Meta(M.singleton "k"(MetaList[])))) ])) | cp<-[as start..as start+as count-1],let c=chr cp,let t=T.pack[c]]
  bytes=runPut(mapM_(\b->putWord32be(fromIntegral(BL.length b))>>putLazyByteString b)outputs)
  in toJSON(show(hashlazy bytes::Digest SHA256))
 _->error "operation"
main=do
 hSetBuffering stdout LineBuffering
 args<-getArgs
 case args of
  ["--original-property"]->do
   result<-quickCheckWithResult stdArgs{maxSuccess=1000,replay=Just(mkQCGen 314159,0)} Original.stringifyInlinesMatchesStringify
   unless(isSuccess result)exitFailure
  _->loop
 where
 loop=do
  end<-isEOF
  if end then pure() else do
   line<-L.pack <$> getLine
   let value=case eitherDecode line of Right x->response x;Left e->error e
   bytes<-E.evaluate(let b=encode value in BL.length b `seq` b)
   L.putStrLn bytes;hFlush stdout;loop
