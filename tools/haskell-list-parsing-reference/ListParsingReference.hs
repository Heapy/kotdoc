{-# LANGUAGE OverloadedStrings #-}
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Map as M
import qualified Data.Vector as V
import qualified Data.Text as T
import qualified Text.Parsec as P
import qualified Text.Parsec.Prim as P
import qualified Text.Parsec.Pos as PP
import qualified Text.Parsec.Error as PE
import qualified Text.Pandoc.Sources as S
import Text.Pandoc.Parsing.Lists
import Text.Pandoc.Parsing.State
import Text.Pandoc.Definition
import Text.Pandoc.Shared (safeRead)
import Data.Functor.Identity
import System.IO
import SourceMarker (isOrderedListMarker)
import Control.Monad (unless)

arr (Array x)=V.toList x
arr x=error(show x)
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error x->error x
get k (Object o)=as (Object o) M.! k :: Value
get _ v=error(show v)
style v=read(as v)::ListNumberStyle
delim v=read(as v)::ListNumberDelim
pair (s,n)=toJSON [toJSON(show s),toJSON n]
attributes (n,s,d)=toJSON [toJSON n,toJSON(show s),toJSON(show d)]
core :: Value -> P.Parsec T.Text ParserState Value
core v=case arr v of
 [String "any"]->attributes <$> anyOrderedListMarker
 [String "ordered",s,d]->toJSON <$> orderedListMarker(style s)(delim d)
 [String "roman",u]->toJSON <$> romanNumeral(as u)
 [String "decimal"]->pair <$> decimal
 [String "lowerAlpha"]->pair <$> lowerAlpha
 [String "upperAlpha"]->pair <$> upperAlpha
 [String "lowerRoman"]->pair <$> lowerRoman
 [String "upperRoman"]->pair <$> upperRoman
 [String "eof",p]->core p <* P.eof
 [String "fallback",p]->core p P.<|> pure (String "fallback")
 [String "many",p]->toJSON <$> P.many(core p)
 [String "sequence",ps]->toJSON <$> P.sepBy1 (foldr (P.<|>) P.parserZero (map core (arr ps))) (S.char ',')
 _->error(show v)
position p=toJSON [toJSON(P.sourceName p),toJSON(P.sourceLine p),toJSON(P.sourceColumn p)]
encError e=object ["position" .= position(PE.errorPos e),"show" .= show e,
 "messages" .= [toJSON [toJSON(fromEnum m),toJSON(PE.messageString m)]|m<-PE.errorMessages e]]
run v=let [name,line,col]=arr(get "position" v)
          pos=PP.newPos(as name)(as line)(as col)
          st=ParserState(as(get "next" v))(M.fromList(as(get "examples" v))) "retained"
          initial=P.State(as(get "text" v))pos st
          result=runIdentity(P.runParsecT(core(get "parser" v))initial)
          (consumed,reply)=case result of P.Consumed r->(True,runIdentity r);P.Empty r->(False,runIdentity r)
      in case reply of
        P.Error e->object ["ok" .= False,"consumed" .= consumed,"error" .= encError e]
        P.Ok value s e->object ["ok" .= True,"consumed" .= consumed,"value" .= value,"error" .= encError e,
          "position" .= position(P.statePos s),"remaining" .= P.stateInput s,
          "next" .= stateNextExample(P.stateUser s),"examples" .= M.toList(stateExamples(P.stateUser s)),
          "untouched" .= untouched(P.stateUser s)]
operation [String "run",v]=run v
operation [String "read",v]=toJSON (safeRead(as v)::Maybe Int)
operation [String "marker",v]=toJSON $ isOrderedListMarker(as v)
operation [String "partial",String "reuse-next"]=toJSON $ case P.runParser anyOrderedListMarker
  (ParserState (error "unused next example") (M.singleton "label" 42) (error "untouched")) "" ("@label."::T.Text) of
    Right v->attributes v
    Left e->error(show e)
operation [String "partial",String "decimal-state"]=toJSON $ case P.runParser anyOrderedListMarker
  (ParserState (error "unused next example") (error "unused examples") (error "untouched")) "" ("123."::T.Text) of
    Right v->attributes v
    Left e->error(show e)
operation [String "partial",String "default-state"]=toJSON $ case P.runParser (orderedListMarker Example TwoParens)
  (ParserState (error "unused next example") (error "unused examples") (error "untouched")) "" ("(#)"::T.Text) of
    Right v->toJSON v
    Left e->error(show e)
operation v=error(show v)
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
       done<-isEOF
       unless done $ do
         line<-B.pack <$> getLine
         let request=case eitherDecode line of Right x->x;Left e->error e
         B.putStrLn(encode(operation(arr request)))
         loop
 loop
