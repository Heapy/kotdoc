{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Observation only. All HTML helpers come from the complete original library.
module Main where
import Control.Monad(unless,void)
import Control.Monad.Reader(runReaderT)
import Data.Aeson
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Vector as V
import Data.Default
import Data.Char(chr)
import qualified Data.Set as Set
import Text.HTML.TagSoup(Tag(..),(~==),parseTagsOptions,parseOptions,ParseOptions(..))
import qualified Text.Pandoc.XML as X
import Data.Binary.Put
import Crypto.Hash(SHA256,Digest,hashlazy)
import System.Environment(getArgs,withArgs)
import Test.Tasty(defaultMain,testGroup)
import qualified Tests.Readers.HTML as OriginalHTML
import qualified Text.Parsec as P
import qualified Text.Parsec.Prim as PP
import qualified Text.Parsec.Error as PE
import qualified Text.Parsec.Pos as Pos
import qualified Text.Pandoc.Readers.HTML.Parsing as H
import qualified Text.Pandoc.Readers.HTML.TagCategories as C
import Text.Pandoc.Readers.HTML.Types
import Text.Pandoc.Class(PandocPure,runPure)
import qualified Control.Exception as E
import System.IO
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr(Array v)=V.toList v
arr _=error "array"
tag :: Value -> Tag T.Text
tag v=case arr v of
 [Number 0,n,a]->TagOpen(as n)(as a)
 [Number 1,n]->TagClose(as n)
 [Number 2,t]->TagText(as t)
 [Number 3,t]->TagComment(as t)
 [Number 4,t]->TagWarning(as t)
 [Number 5,r,c]->TagPosition(as r)(as c)
 _->error "tag"
tv :: Tag T.Text -> Value
tv t=toJSON $ case t of
 TagOpen n a->[toJSON(0::Int),toJSON n,toJSON a]
 TagClose n->[toJSON(1::Int),toJSON n]
 TagText s->[toJSON(2::Int),toJSON s]
 TagComment s->[toJSON(3::Int),toJSON s]
 TagWarning s->[toJSON(4::Int),toJSON s]
 TagPosition r c->[toJSON(5::Int),toJSON r,toJSON c]
pv p=toJSON[P.sourceName p `seq` toJSON(map fromEnum(P.sourceName p)),toJSON(P.sourceLine p),toJSON(P.sourceColumn p)]
pos v=case arr v of [n,l,c]->Pos.newPos(map(chr.as)(arr n))(as l)(as c);_->error "position"
err e=object["position" .= pv(PE.errorPos e),"show" .= show e,"characters" .= map fromEnum(show e),"messages" .= [[toJSON(fromEnum m),toJSON(PE.messageString m)]|m<-PE.errorMessages e]]
predTag :: Value -> Tag T.Text -> Bool
predTag v t=case arr v of
 [String "any"]->True
 [String "kind",n]->case (as n::Int,t) of (0,TagOpen{})->True;(1,TagClose{})->True;(2,TagText{})->True;(3,TagComment{})->True;(4,TagWarning{})->True;(5,TagPosition{})->True;_->False
 [String "open",n,a]->H.matchTagOpen(as n)(as a)t
 [String "close",n]->H.matchTagClose(as n)t
 [String "match",expected]->t ~== tag expected
 [String "attribute",n,value]->H.maybeFromAttrib(as n)t==Just(as value)
 [String "never"]->False
 _->error "predicate"
omission v=case as v::Int of 0->H.TagsRequired;1->H.ClosingTagOptional;2->H.TagsOmittable;_->error "omission"
core :: Value->TagParser PandocPure Value
core v=case arr v of
 [String "any"]->tv <$> H.pAny
 [String "raw"]->tv <$> P.anyToken
 [String "prim",update,test]->PP.tokenPrimEx show (\p _ rest->Pos.newPos(P.sourceName p)(P.sourceLine p+length rest)(P.sourceColumn p+1))
   (if as update then Just(\_ _ _ u->u{iframeDepth=iframeDepth u+1}) else Nothing)
   (\t->if predTag test t then Just(tv t) else Nothing)
 [String "blank"]->String "()" <$ H.pBlank
 [String "satisfy",test]->tv <$> H.pSatisfy(predTag test)
 [String "closes",n]->String "()" <$ H.pCloses(as n)
 [String "in-tags",n,p]->toJSON <$> H.pInTags(as n)((:[]) <$> core p)
 [String "in-tags-where",n,test,p]->toJSON <$> H.pInTags'(as n)(predTag test)((:[]) <$> core p)
 [String "in-tag",o,n,p]->H.pInTag(omission o)(as n)(core p)
 [String "in-attr",o,n,p]->toJSON <$> H.pInTagWithAttribs(omission o)(as n)(core p)
 [String "pure",x]->pure x
 [String "fail",msg]->P.parserFail(as msg)
 [String "zero"]->P.parserZero
 [String "eof"]->String "()" <$ P.eof
 [String "attempt",p]->P.try(core p)
 [String "look",p]->P.lookAhead(core p)
 [String "label",name,p]->core p P.<?> as name
 [String "fallback",p]->core p P.<|> pure(String "fallback")
 [String "or",p,q]->core p P.<|> core q
 [String "many",p]->toJSON <$> P.many(core p)
 [String "sequence",ps]->toJSON <$> mapM core(arr ps)
 [String "before",p,q]->core p <* core q
 [String "tick",p]->do x<-core p;P.modifyState(\s->s{iframeDepth=iframeDepth s+1});pure x
 [String "nest",n]->if as n==(0::Int) then core(toJSON[String "satisfy",toJSON[String "kind",toJSON(2::Int)]]) else H.pInTag H.TagsRequired "div"(core(toJSON[String "nest",toJSON(as n-1::Int)]))
 _->error "parser"
observe parser input position n=case runPure $ runReaderT action def of
 Left e->error(show e)
 Right result->result
 where
 user=HTMLState def mempty mempty Nothing mempty [] mempty def False False n
 action=do
  consumed<-PP.runParsecT(core parser)(PP.State input position user)
  let finish c action'=do
       reply<-action'
       pure $ case reply of
        PP.Error e->object["ok" .= False,"consumed" .= c,"error" .= err e]
        PP.Ok value state e->object["ok" .= True,"consumed" .= c,"value" .= value,"position" .= pv(P.statePos state),"remaining" .= map tv(P.stateInput state),"user" .= iframeDepth(P.stateUser state),"error" .= err e]
  case consumed of PP.Consumed r->finish True r;PP.Empty r->finish False r
response v=case arr v of
 [String "sets"]->toJSON[toJSON(Set.toAscList C.blockHtmlTags),toJSON(Set.toAscList C.blockDocBookTags),toJSON(Set.toAscList C.eitherBlockOrInline),toJSON(Set.toAscList C.epubTags),toJSON(Set.toAscList C.blockTags),toJSON(Set.toAscList C.voidTags),toJSON C.sectioningContent,toJSON C.groupingContent]
 [String "standard-attrs"]->toJSON(Set.toAscList(X.html5Attributes<>X.html4Attributes<>X.rdfaAttributes))
 [String "tags",source]->toJSON(map tv(parseTagsOptions parseOptions{optTagPosition=True}(as source::T.Text)))
 [String "unicode-space",first,count]->toJSON(show(hashlazy(BL.pack[if H.isSpace(chr n) then 1 else 0|n<-[as first..as first+as count-1]])::Digest SHA256))
 [String "unicode-show",first,count]->let
   outputs=[T.pack(show t)|n<-[as first..as first+as count-1],let c=T.singleton(chr n),t<-[TagOpen c [(c,c)],TagClose c,TagText c,TagComment c,TagWarning c]]
   bytes=runPut(mapM_(\t->putWord32be(fromIntegral(T.length t)) >> mapM_(putWord32be.fromIntegral.fromEnum)(T.unpack t))outputs)
   in toJSON(show(hashlazy bytes::Digest SHA256))
 [String "space",c]->toJSON(H.isSpace(chr(as c)))
 [String "match",t,expected]->toJSON(tag t ~== tag expected)
 [String "open",n,attrs,t]->toJSON(H.matchTagOpen(as n)(as attrs)(tag t))
 [String "close",n,t]->toJSON(H.matchTagClose(as n)(tag t))
 [String "maybe",n,t]->toJSON(H.maybeFromAttrib(as n)(tag t))
 [String "attrs",attrs]->toJSON(H.toAttr(as attrs))
 [String "string-attrs",attrs]->toJSON(H.toStringAttr(as attrs))
 [String "show",t]->toJSON(show(tag t))
 [String "run",p,ts,position,n]->observe p(map tag(arr ts))(pos position)(as n)
 _->error "request"
main=do
 args<-getArgs
 case args of
  ["--upstream-tests"]->withArgs ["--quickcheck-replay","314159"](defaultMain(testGroup "Original HTML reader" OriginalHTML.tests))
  _->loop
 where
 loop=do
  end<-isEOF
  unless end $ do
   line<-B.getLine
   out<-E.try $ E.evaluate $ let result=response(either error id(eitherDecode(BL.fromStrict line))) in BL.length(encode result) `seq` result
   L.putStrLn(encode(either(\(e::E.SomeException)->object["host" .= show e]) id out));hFlush stdout;loop
