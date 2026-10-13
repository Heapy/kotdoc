{-# LANGUAGE CPP, OverloadedStrings, ScopedTypeVariables #-}
{-# OPTIONS_GHC -fno-specialise -fno-spec-constr #-}
module Main where
import Skylighting.Types
import Skylighting.Styles
import PandocHighlightMethod
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.ByteString as S
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.Map as M
import Data.Word(Word64)
import GHC.Float(castWord64ToDouble,castDoubleToWord64)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)
import Control.Exception(SomeException,try,evaluate,displayException)

success :: (Show a,ToJSON a) => a -> Value
success a=object["accepted" .= True,"value" .= toJSON a,"encoded" .= TE.decodeUtf8(B.toStrict(encode a)),"show" .= show a]
failure e=object["accepted" .= False,"error" .= e]
parsed :: (Show a,ToJSON a) => Either String a -> Value
parsed=either failure success
order :: forall a. (FromJSON a,Ord a) => a -> Value -> Value
order witness v=case parseEither parseJSON v::Either String [a] of
 Left e->failure e
 Right [a,b]->object["order" .= show(compare a b),"equal" .= (a==b)]
 _->error "expected comparison pair"
colorResult Nothing=object["accepted" .= False]
colorResult(Just c)=case success c of
 Object v->object["result" .= Object v,"fractionBits" .= map castDoubleToWord64 [r,g,b]]
  where (r,g,b)=fromColor c::(Double,Double,Double)
process :: (String,Value) -> Value
process(op,v)=case op of
 "color"->parsed(parseEither parseJSON v::Either String Color)
 "token"->parsed(parseEither parseJSON v::Either String TokenType)
 "token-style"->parsed(parseEither parseJSON v::Either String TokenStyle)
 "style"->parsed(parseEither parseJSON v::Either String Style)
 "highlight"->parsed(parseEither parseJSON v::Either String HighlightMethod)
 "color-order"->order(undefined::Color)v
 "token-order"->order(undefined::TokenType)v
 "token-style-order"->order(undefined::TokenStyle)v
 "style-order"->order(undefined::Style)v
 "theme"->case v of String s->parsed(parseTheme(B.fromStrict(TE.encodeUtf8 s)));_->error "expected theme input"
 "builtin"->case v of
   String s->success(fromMaybeError(T.unpack s)(lookup(T.unpack s)styles))
   _->error "expected builtin name"
 "color-string"->case v of String s->colorResult(toColor(T.unpack s));_->error "expected color string"
 "color-int"->case fromJSON v of Success n->colorResult(toColor(n::Int));_->error "expected color integer"
 "color-double"->case fromJSON v of
   Success [a,b,c]->colorResult(toColor(castWord64ToDouble a,castWord64ToDouble b,castWord64ToDouble c))
   _->error "expected color double bits"
 _->error("unexpected operation "++op)
 where fromMaybeError _ (Just a)=a;fromMaybeError s _=error s
styles=[("pygments",pygments),("tango",tango),("espresso",espresso),("zenburn",zenburn),("kate",kate),("monochrome",monochrome),("breezedark",breezeDark),("haddock",haddock)]
main=do
 args<-getArgs
 if args==["--reference-info"] then B.putStrLn(encode["ghc",showVersion compilerVersion,"skylighting-core","0.15","aeson",VERSION_aeson])
 else B.getContents >>= mapM_ respond . B.lines
 where respond s=do
        let req=case eitherDecode s of Right a->a;Left e->error e
        result<-try(evaluate(B.toStrict(encode(process req))))::IO(Either SomeException S.ByteString)
        case result of
         Left e->B.putStrLn(encode(object["exception" .= takeWhile(/='\n')(displayException e)]))
         Right b->B.putStrLn(B.fromStrict b)
