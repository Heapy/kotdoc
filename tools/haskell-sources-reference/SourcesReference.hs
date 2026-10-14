{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Transport and observations only; Text.Pandoc.Sources compiles unchanged.
module Main where
import Data.Aeson
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Map as M
import qualified Data.Vector as V
import qualified Data.Text as T
import Data.Char (chr,ord,isSpace,isLetter,isAlphaNum,isDigit,isHexDigit)
import qualified Text.Parsec as P
import qualified Text.Parsec.Prim as P
import qualified Text.Parsec.Pos as PP
import qualified Text.Parsec.Error as PE
import qualified Text.Pandoc.Sources as S
import Text.Pandoc.Parsing.Lists
import Text.Pandoc.Parsing.State
import Data.String (fromString)
import Data.List (groupBy)
import Data.Functor.Identity
import System.IO
import Control.Monad (unless)
import Control.Exception

arr (Array x)=V.toList x
arr x=error(show x)
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error x->error x
get k (Object o)=as (Object o) M.! k :: Value
get _ v=error(show v)
points v=map chr(as v::[Int])
pack v=T.pack(points v)
pos v=case arr v of [n,l,c]->PP.newPos(as n)(as l)(as c);_->error(show v)
position p=toJSON [toJSON(P.sourceName p),toJSON(P.sourceLine p),toJSON(P.sourceColumn p)]
source v=S.Sources [(pos p,pack t)|x<-arr v,let [p,t]=arr x]
encSource (S.Sources xs)=toJSON [toJSON [position p,toJSON(map ord(T.unpack t))]|(p,t)<-xs]
predicate v=case arr v of
 [String "true"]->const True
 [String "false"]->const False
 [String "space"]->isSpace
 [String "letter"]->isLetter
 [String "alphaNum"]->isAlphaNum
 [String "digit"]->isDigit
 [String "hexDigit"]->isHexDigit
 [String "oneOf",ps]->(`elem` points ps)
 [String "noneOf",ps]->(`notElem` points ps)
 _->error(show v)
core :: (P.Stream s Identity Char, S.UpdateSourcePos s Char) =>
        (Value -> Maybe (P.Parsec s Int Value)) -> Value -> P.Parsec s Int Value
core extra v=case arr v of
 [String "satisfy",f]->toJSON.ord <$> S.satisfy(predicate f)
 [String "char",c]->toJSON.ord <$> S.char(chr(as c))
 [String "string",cs]->toJSON.map ord <$> S.string(points cs)
 [String "any"]->toJSON.ord <$> S.anyChar
 [String "newline"]->toJSON.ord <$> S.newline
 [String "space"]->toJSON.ord <$> S.space
 [String "spaces"]->S.spaces *> pure Null
 [String "letter"]->toJSON.ord <$> S.letter
 [String "alphaNum"]->toJSON.ord <$> S.alphaNum
 [String "digit"]->toJSON.ord <$> S.digit
 [String "hexDigit"]->toJSON.ord <$> S.hexDigit
 [String "oneOf",cs]->toJSON.ord <$> S.oneOf(points cs)
 [String "noneOf",cs]->toJSON.ord <$> S.noneOf(points cs)
 [String "eof"]->P.eof *> pure Null
 [String "ordinary",c]->toJSON.ord <$> P.char(chr(as c))
 [String "many",p]->toJSON <$> P.many(core extra p)
 [String "manyText",f]->toJSON.map ord <$> P.many(S.satisfy(predicate f))
 [String "many1Text",f]->toJSON.map ord <$> P.many1(S.satisfy(predicate f))
 [String "try",p]->P.try(core extra p)
 [String "look",p]->P.lookAhead(core extra p)
 [String "fallback",p]->core extra p P.<|> pure (String "fallback")
 [String "label",label,p]->core extra p P.<?> as label
 [String "sequence",ps]->toJSON <$> sequence(map (core extra)(arr ps))
 [String "user",n]->P.putState(as n) *> pure Null
 _->case extra v of Just p->p;Nothing->error(show v)
extra v=case arr v of
 [String "take",f]->Just(toJSON.map ord.T.unpack <$> S.takeWhileP(predicate f))
 [String "take1",f]->Just(toJSON.map ord.T.unpack <$> S.takeWhile1P(predicate f))
 [String "add",p,t]->Just(S.addToSources(pos p)(pack t) *> pure Null)
 [String "prepend",t]->Just(S.addToInput(pack t) *> pure Null)
 _->Nothing
encError e=object ["position" .= position(PE.errorPos e),"show" .= show e,
 "messages" .= [toJSON [toJSON(fromEnum m),toJSON(PE.messageString m)]|m<-PE.errorMessages e]]
run parser input position0 user encInput=
 let result=runIdentity(P.runParsecT parser(P.State input position0 user))
     (consumed,reply)=case result of P.Consumed r->(True,runIdentity r);P.Empty r->(False,runIdentity r)
 in case reply of
   P.Error e->object ["ok" .= False,"consumed" .= consumed,"error" .= encError e]
   P.Ok value s e->object ["ok" .= True,"consumed" .= consumed,"value" .= value,"error" .= encError e,
     "position" .= position(P.statePos s),"remaining" .= encInput(P.stateInput s),"user" .= P.stateUser s]
runInput v=let typ=as(get "type" v)::String;input=get "input" v;p=get "parser" v
               position0=pos(get "position" v);user=as(get "user" v)
           in case typ of
             "sources"->run(core extra p)(source input)position0 user encSource
             "text"->run(core (const Nothing) p)(pack input)position0 user (toJSON.map ord.T.unpack)
             "chars"->run(core (const Nothing) p)(points input)position0 user (toJSON.map ord)
             "bytes"->run(core (const Nothing) p)(BS.pack(as input))position0 user (toJSON.BS.unpack)
             "lazyBytes"->run(core (const Nothing) p)(BL.fromChunks(map (BS.pack.as)(arr input)))position0 user (toJSON.map BS.unpack.BL.toChunks)
             _->error typ
partial :: String -> Value
partial kind=case kind of
 "initial-ignore-text-tail"->toJSON(S.initialSourceName(S.Sources [(PP.initialPos "first",error "unused text"),error "unused pair"]))
 "initial-ignore-tail"->toJSON(S.initialSourceName(S.Sources ((PP.initialPos "first",error "unused text"):error "unused tail")))
 "concat-ignore-position"->toJSON(S.sourcesToText(S.Sources [(error "unused position","abc")]))
 "uncons-ignore-position"->case runIdentity(P.uncons(S.Sources [(error "unused position","x")])) of Just(c,_)->toJSON(ord c);_->Null
 "uncons-ignore-empty-position"->case runIdentity(P.uncons(S.Sources [(error "unused position",""),(error "unused position","x")])) of Just(c,_)->toJSON(ord c);_->Null
 "uncons-ignore-char"->case runIdentity(P.uncons([error "unused char"]::[Char])) of Just(_,rest)->toJSON(null rest);_->Null
 "append-ignore-rhs"->toJSON(S.initialSourceName(S.Sources [(PP.initialPos "left",error "unused text")] <> error "unused rhs"))
 "append-ignore-left-fields"->toJSON(S.sourcesToText(S.Sources [(error "unused pos","abc")] <> S.Sources []))
 "update-ignore-single-text"->position(S.updateSourcePos(PP.initialPos "parser") '\t'(S.Sources [(error "unused pos",error "unused text")]))
 "update-ignore-next-text"->position(S.updateSourcePos(PP.initialPos "parser") 'x'(S.Sources [(error "unused pos",""),(PP.newPos "next" 4 6,error "unused text")]))
 "update-demand-tail"->position(S.updateSourcePos(PP.initialPos "parser") 'x'(S.Sources ((error "unused pos","x"):error "demanded tail")))
 "update-demand-next-pair"->position(S.updateSourcePos(PP.initialPos "parser") 'x'(S.Sources [(error "unused pos","x"),error "demanded pair"]))
 "ensure-ignore-earlier-pair"->toJSON(S.initialSourceName(S.ensureFinalNewlines 2(S.Sources [error "earlier pair",(PP.initialPos "last","\n\n")])) )
 "ensure-ignore-earlier-text"->toJSON(S.initialSourceName(S.ensureFinalNewlines 2(S.Sources [(PP.initialPos "first",error "unused text"),(error "unused pos","\n\n")])) )
 "take-false-ignore-tail"->case P.runParser(S.takeWhileP(const False)) () "parser" (S.Sources ((error "unused pos","x"):error "unused tail")) of Right t->toJSON t;Left e->error(show e)
 "any-ignore-last-position"->case P.runParser S.anyChar () "parser" (S.Sources [(error "unused pos","x")]) of Right c->toJSON(ord c);Left e->error(show e)
 "any-demand-tail"->case P.runParser S.anyChar () "parser" (S.Sources ((error "unused pos","x"):error "demanded tail")) of Right c->toJSON(ord c);Left e->error(show e)
 "prepend-demand-spine"->case P.runParser(S.addToInput "a") () "parser" (S.Sources(error "demanded spine")) of Right ()->Null;Left e->error(show e)
 "prepend-demand-pair"->case P.runParser(S.addToInput "a") () "parser" (S.Sources [error "demanded pair"]) of Right ()->Null;Left e->error(show e)
 "prepend-ignore-fields"->case P.runParser(S.addToInput "a") () "parser" (S.Sources [(error "unused pos",error "unused text")]) of Right ()->Null;Left e->error(show e)
 "add-ignore-old-spine"->case P.runParser(S.addToSources (PP.initialPos "new") "a") () "parser" (S.Sources(error "unused spine")) of Right ()->Null;Left e->error(show e)
 "add-ignore-old-pair"->case P.runParser(S.addToSources (PP.initialPos "new") "a") () "parser" (S.Sources [error "unused pair"]) of Right ()->Null;Left e->error(show e)
 "ensure-zero-demand-last-text"->toJSON(S.initialSourceName(S.ensureFinalNewlines 0(S.Sources [(PP.initialPos "last",error "demanded text")])) )
 "take-whole-demand-next-position"->case P.runParser(S.takeWhileP(const True)) () "parser" (S.Sources [(error "unused pos","x"),(error "demanded next position","")]) of Right t->toJSON t;Left e->error(show e)
 "take-prefix-ignore-tail"->case P.runParser(S.takeWhileP(=='x')) () "parser" (S.Sources ((error "unused pos","x!"):error "unused tail")) of Right t->toJSON t;Left e->error(show e)
 "take-empty-retain-pos"->case P.runParser(S.takeWhileP(const False) *> (P.sourceName <$> P.getPosition)) () "parser" (S.Sources [(error "unused pos",""),(error "unused pos","x")]) of Right t->toJSON t;Left e->error(show e)
 "update-jump-ignore-pos-char"->position(S.updateSourcePos (error "unused current position") (error "unused char"::Char) (S.Sources [(error "unused pos",""),(PP.newPos "next" 4 6,error "unused text")]))
 "update-fallback-demand-char"->position(S.updateSourcePos (PP.initialPos "parser") (error "demanded char"::Char) (S.Sources [(error "unused pos","x")]))
 "mconcat-ignore-tail"->toJSON(S.initialSourceName(mconcat(S.Sources [(PP.initialPos "first",error "unused text")]:error "unused source list")))
 "infinite-initial"->toJSON(S.initialSourceName(S.Sources(repeat(PP.initialPos "infinite","x"))))
 "infinite-prefix"->case P.runParser(S.string(replicate 10000 'x')) () "parser" (S.Sources(repeat(PP.initialPos "infinite","x"))) of Right s->toJSON(length s);Left e->error(show e)
 _->error kind
operation [String "classes"]=toJSON [(fst(head g),snd(head g))|g<-groupBy (\a b->snd a==snd b) [(ord c,flags c)|c<-[minBound..maxBound]]]
 where flags c=sum [if f c then n else 0|(f,n)<-[(isLetter,1),(isAlphaNum,2),(isSpace,4),(isDigit,8),(isHexDigit,16)]]
operation [String "list",v]=
 let parser=case as(get "parser" v)::String of
              "any"->(\(n,s,d)->toJSON [toJSON n,toJSON(show s),toJSON(show d)]) <$> anyOrderedListMarker
              "roman"->toJSON <$> romanNumeral True
              "decimal"->(\(s,n)->toJSON [toJSON(show s),toJSON n]) <$> decimal
              "eof"->(\(n,s,d)->toJSON [toJSON n,toJSON(show s),toJSON(show d)]) <$> (anyOrderedListMarker <* P.eof)
     initial=P.State(source(get "input" v))(pos(get "position" v))(ParserState 1 M.empty "retained")
     result=runIdentity(P.runParsecT parser initial)
     (consumed,reply)=case result of P.Consumed r->(True,runIdentity r);P.Empty r->(False,runIdentity r)
 in case reply of
   P.Error e->object ["ok" .= False,"consumed" .= consumed,"error" .= encError e]
   P.Ok value st e->object ["ok" .= True,"consumed" .= consumed,"value" .= value,"error" .= encError e,
     "position" .= position(P.statePos st),"remaining" .= encSource(P.stateInput st),
     "next" .= stateNextExample(P.stateUser st),"examples" .= M.toList(stateExamples(P.stateUser st)),"untouched" .= untouched(P.stateUser st)]
operation [String "run",v]=runInput v
operation [String "source",v]=let s=source v in object ["chunks" .= encSource s,"text" .= S.sourcesToText s,"name" .= S.initialSourceName s,"show" .= show s,"show11" .= showsPrec 11 s ""]
operation [String "from",String "text",v]=encSource(S.toSources(pack v))
operation [String "from",String "string",v]=encSource(S.toSources((fromString (points v)::S.Sources)))
operation [String "from",String "files",v]=encSource(S.toSources [(as n::FilePath,pack t)|x<-arr v,let [n,t]=arr x])
operation [String "ensure",n,v]=encSource(S.ensureFinalNewlines(as n)(source v))
operation [String "concat",xs]=encSource(mconcat(map source(arr xs)))
operation [String "append",a,b]=encSource(source a <> source b)
operation [String "update",typ,p,c,v]=position(case as typ::String of
 "sources"->S.updateSourcePos(pos p)(chr(as c))(source v)
 "text"->S.updateSourcePos(pos p)(chr(as c))(error "unused Text"::T.Text)
 "chars"->S.updateSourcePos(pos p)(chr(as c))(error "unused [Char]"::[Char])
 "bytes"->S.updateSourcePos(pos p)(chr(as c))(error "unused ByteString"::BS.ByteString)
 "lazyBytes"->S.updateSourcePos(pos p)(chr(as c))(error "unused ByteString"::BL.ByteString))
operation [String "uncons",v]=case runIdentity(P.uncons(source v)) of Nothing->Null;Just(c,rest)->toJSON [toJSON(ord c),encSource rest]
operation [String "partial",String k]=partial(T.unpack k)
operation v=error(show v)
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
       done<-isEOF
       unless done $ do
         line<-B.pack <$> getLine
         let request=case eitherDecode line of Right x->x;Left e->error e
         reply<-try(evaluate(let b=encode(operation(arr request)) in BL.length b `seq` b))::IO(Either SomeException BL.ByteString)
         B.putStrLn(case reply of Right b->b;Left e->encode(object ["exception" .= takeWhile (/='\n')(displayException e)]))
         loop
 loop
