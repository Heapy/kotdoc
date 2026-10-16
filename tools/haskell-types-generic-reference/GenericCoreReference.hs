{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Calls the pinned original Generic module and dependency Data instances only.
import qualified Text.Pandoc.Generic as G
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import qualified Data.Text as T
import qualified Data.Sequence as S
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import Data.Monoid
import Control.Monad.State.Strict
import Control.Exception
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
first f x=show(getFirst(G.queryWith f x))
hit (_::a)=First(Just "hit"::Maybe String)
normal :: [Inline] -> [Inline]
normal (Space:Space:xs)=Space:xs
normal (Emph xs:Emph ys:zs)=Emph(xs++ys):zs
normal xs=xs
myDoc=Pandoc nullMeta[Para[Str "Hi",Space,Emph[Str "world",Space],Emph[Space,Str "emphasized"]]]
operation r=toJSON$case as(field "probe" r)::String of
 "bottom-up-char-string"->show(G.bottomUp(\c->if c=='a' then 'X' else c)("aba"::String))
 "top-down-char-string"->show(G.topDown(\c->if c=='a' then 'X' else c)("aba"::String))
 "bottom-up-list-tails"->show(G.bottomUp(\s->filter(/='#')(s::String))("a#b#"::String))
 "top-down-list-tails"->show(G.topDown(\s->filter(/='#')(s::String))("a#b#"::String))
 "query-string-tails"->show(G.queryWith(\(s::String)->[s])("abc"::String))
 "query-string-tail-count"->show(getSum(G.queryWith(\(_::String)->Sum(1::Int))("abc"::String)))
 "query-first-bottom-char"->first(hit::Char->First String)(error "root"::Char)
 "query-first-bottom-int"->first(hit::Int->First String)(error "root"::Int)
 "query-first-bottom-bool"->first(hit::Bool->First String)(error "root"::Bool)
 "query-first-bottom-quote"->first(hit::QuoteType->First String)(error "root"::QuoteType)
 "query-first-bottom-text"->first(hit::T.Text->First String)(error "root"::T.Text)
 "query-first-bottom-format"->first(hit::Format->First String)(error "root"::Format)
 "query-first-bottom-meta"->first(hit::Meta->First String)(error "root"::Meta)
 "query-first-bottom-many"->first(hit::P.Inlines->First String)(error "root"::P.Inlines)
 "query-first-bottom-seq"->first(hit::S.Seq Inline->First String)(error "root"::S.Seq Inline)
 "query-first-bottom-inline"->first(hit::Inline->First String)(error "root"::Inline)
 "query-first-bottom-list"->first(hit::String->First String)(error "root"::String)
 "query-unit-leaf-callback"->show(G.queryWith(\(_::Char)->error "callback"::())'a')
 "query-unit-empty-list-callback"->show(G.queryWith(\(_::String)->error "callback"::())(""::String))
 "query-unit-cons-callback"->show(G.queryWith(\(_::String)->error "callback"::())("a"::String))
 "query-unit-text-callback"->show(G.queryWith(\(_::T.Text)->error "callback"::())("a"::T.Text))
 "bottom-upM-char-state"->show(runState(G.bottomUpM(\(c::Char)->modify(++[c])>>pure(if c=='a' then 'X' else c))("abc"::String))([]::String))
 "bottom-upM-string-state"->show(runState(G.bottomUpM(\(s::String)->modify(++[s])>>pure(s++"#"))("abc"::String))([]::[String]))
 "bottom-upM-char-list"->show(G.bottomUpM(\(c::Char)->[c,if c=='a' then 'A' else 'B'])("ab"::String))
 "bottom-up-haddock-normal"->show(G.bottomUp normal myDoc)
 "top-down-haddock-normal"->show(G.topDown normal myDoc)
 _->error "probe"
respond line=do
 let r=either error id(eitherDecode line)
 result<-try(evaluate(let b=encode(operation r)in B.length b `seq` b))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
