{-# LANGUAGE DeriveDataTypeable, ScopedTypeVariables, OverloadedStrings #-}
-- Independent source Data and syb demand probes; no Kotlin-generated expectations.
import Text.Pandoc.Definition
import qualified Text.Pandoc.Generic as G
import qualified Data.Map as M
import qualified Data.Sequence as S
import qualified Data.Text as T
import Data.Data
import Data.Monoid
import Data.Functor.Identity
import Control.Monad.Reader
import Control.Exception
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import System.IO
newtype Trace=Trace String
instance Semigroup Trace where Trace a<>Trace b=Trace("("++a++">"++b++")")
instance Monoid Trace where mempty=Trace "e"
data Box=Box Bool Box|End deriving(Data,Typeable,Show)
probe name=case name of
 "bottom-inline-root-const"->show(G.bottomUp(const Space::Inline->Inline)(error "root"::Inline))
 "top-inline-root-const"->show(G.topDown(const Space::Inline->Inline)(error "root"::Inline))
 "bottom-list-root-const"->show(G.bottomUp(const[]::[Inline]->[Inline])(error "root"::[Inline]))
 "top-list-root-const"->show(G.topDown(const[]::[Inline]->[Inline])(error "root"::[Inline]))
 "bottom-list-partial-tail"->show(take 1(G.bottomUp(const Space::Inline->Inline)(Str "a":error "tail")))
 "top-list-partial-tail"->show(take 1(G.topDown(const Space::Inline->Inline)(Str "a":error "tail")))
 "bottom-pair-first"->show(fst(G.bottomUp(const Space::Inline->Inline)(Str "a",error "right"::Inline)))
 "top-pair-first"->show(fst(G.topDown(const Space::Inline->Inline)(Str "a",error "right"::Inline)))
 "bottom-text-root-const"->show(G.bottomUp(const "ok"::T.Text->T.Text)(error "root"::T.Text))
 "top-text-root-const"->show(G.topDown(const "ok"::T.Text->T.Text)(error "root"::T.Text))
 "bottom-map-root-const"->show(G.bottomUp(const M.empty::M.Map T.Text Inline->M.Map T.Text Inline)(error "root"::M.Map T.Text Inline))
 "top-map-root-const"->show(G.topDown(const M.empty::M.Map T.Text Inline->M.Map T.Text Inline)(error "root"::M.Map T.Text Inline))
 "bottom-seq-root-const"->show(G.bottomUp(const S.empty::S.Seq Inline->S.Seq Inline)(error "root"::S.Seq Inline))
 "top-seq-root-const"->show(G.topDown(const S.empty::S.Seq Inline->S.Seq Inline)(error "root"::S.Seq Inline))
 "custom-bottom-root-const"->show(G.bottomUp(const End::Box->Box)(error "root"::Box))
 "custom-top-root-const"->show(G.topDown(const End::Box->Box)(error "root"::Box))
 "custom-query-first-tail"->show(getFirst(G.queryWith(\b->First(Just b)::First Bool)(Box True(error "tail"))))
 "custom-query-last-tail"->show(getLast(G.queryWith(\b->Last(Just b)::Last Bool)(Box True(error "tail"))))
 "custom-bottom-first-field"->case G.bottomUp(not::Bool->Bool)(Box True(error "tail"))of Box b _->show b;End->"End"
 "custom-top-first-field"->case G.topDown(not::Bool->Bool)(Box True(error "tail"))of Box b _->show b;End->"End"
 "custom-identity-tail"->case runIdentity(G.bottomUpM(\b->Identity(not b)::Identity Bool)(Box True(error "tail")))of Box b _->show b;End->"End"
 "identity-char-root-const"->show(runIdentity(G.bottomUpM(const(Identity 'x')::Char->Identity Char)(error "root"::Char)))
 "identity-inline-root-const"->show(runIdentity(G.bottomUpM(const(Identity Space)::Inline->Identity Inline)(error "root"::Inline)))
 "query-pair-first-right"->show(getFirst(G.queryWith(\b->First(Just b)::First Bool)(True,error "right"::Bool)))
 "reader-custom-first-field"->case runReader(G.bottomUpM(\b->reader(\environment->if environment then not b else b)::Reader Bool Bool)(Box True(error "tail")))True of Box b _->show b;End->"End"
 "reader-inline-root-const"->show(runReader(G.bottomUpM(const(pure Space)::Inline->Reader Bool Inline)(error "root"::Inline))True)
 "reader-list-order"->show(runReader(G.bottomUpM(\(c::Char)->reader(\n->toEnum(fromEnum c+n)))("abc"::String))(1::Int))
 "maybe-custom-first-failure"->case G.bottomUpM(const Nothing::Bool->Maybe Bool)(Box True(error "tail"))of Nothing->"Nothing";Just _->"Just"
 "query-foldl-pair-order"->let Trace result=G.queryWith(\(b::Bool)->Trace(show b))(True,False)in result
 "query-foldl-list-order"->let Trace result=G.queryWith(\(b::Bool)->Trace(show b))[True,False]in result
 "definition-list-outer-head"->case G.bottomUp(const Space::Inline->Inline)(DefinitionList[([Str "a"],[]),error "tail"])of DefinitionList xs->show(take 1 xs);_->error "constructor"
 "definition-list-terms-only"->case G.bottomUp(const Space::Inline->Inline)(DefinitionList[([Str "a"],error "defs")])of DefinitionList xs->show(fst(head xs));_->error "constructor"
 "definition-list-defs-only"->case G.bottomUp(const Space::Inline->Inline)(DefinitionList[(error "terms",[[Para[Str "a"]]])])of DefinitionList xs->show(snd(head xs));_->error "constructor"
 _->error "probe"
respond line=do
 let Object r=either error id(eitherDecode line); Just (String name)=K.lookup "probe" r
 result<-try(evaluate(let bytes=encode(probe(T.unpack name))in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
