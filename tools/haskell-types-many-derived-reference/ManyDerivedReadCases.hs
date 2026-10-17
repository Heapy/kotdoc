{-# LANGUAGE ScopedTypeVariables, DeriveGeneric, TypeApplications, OverloadedStrings #-}
import qualified Text.Pandoc.Builder as B
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import Control.Exception
import Data.Char(chr)
import Data.Proxy
import GHC.Generics(Generic)
import qualified Text.ParserCombinators.ReadPrec as RP
import qualified Text.ParserCombinators.ReadP as R
import Text.Read(readPrec,readListPrec,lexP,Lexeme(Ident))
import System.IO

data Request=Request{kind::String,operation::String,precedence::Int,input::[Int]}deriving(Generic,Show)
instance FromJSON Request
newtype ReadsOnly=ReadsOnly Int deriving Show
instance Read ReadsOnly where
 readsPrec p s=[(ReadsOnly v,t)|("R",t)<-lex s,v<-[p,p+1]]
newtype ReadListOnly=ReadListOnly Int deriving Show
instance Read ReadListOnly where
 readPrec=error "unused-readPrec"
 readList s=[([ReadListOnly 1],dropWhile(==' ')s)]
newtype ReadConflict=ReadConflict Int deriving Show
instance Read ReadConflict where
 readPrec=error "unused-readPrec"
 readList s=[([ReadConflict 1],dropWhile(==' ')s)]
 readListPrec=pure[ReadConflict 2]
newtype ReadPrecOnly=ReadPrecOnly Int deriving Show
instance Read ReadPrecOnly where
 readsPrec _ _=error "unused-readsPrec"
 readPrec=do Ident "R"<-lexP;pure(ReadPrecOnly 3)
newtype ReadTag=ReadTag Int deriving Show
instance Read ReadTag where
 readPrec=RP.readP_to_Prec(\p->R.skipSpaces>>R.string "E">>pure(ReadTag p))
 readListPrec=RP.readP_to_Prec(\p->R.skipSpaces>>R.string "TAG">>pure[ReadTag p])
newtype Parallel=Parallel Int deriving Show
instance Read Parallel where
 readPrec=pure(Parallel 1) RP.+++ (RP.get>>pure(Parallel 2))
 readListPrec=pure[Parallel 1] RP.+++ (RP.get>>pure[Parallel 2])
newtype Duplicate=Duplicate Int deriving Show
instance Read Duplicate where
 readsPrec p s=[(Duplicate p,s),(Duplicate(p+1),s)]
 readListPrec=RP.readS_to_Prec(\p s->[([Duplicate p],dropWhile(==' ')s),([Duplicate(p+1)],dropWhile(==' ')s)])
process::forall a.(Read a,Show a)=>Proxy a->Request->String
process _ r=let p=precedence r;s=map chr(input r) in case operation r of
 "readsPrec"->show(readsPrec p s::[(B.Many a,String)])
 "readPrec"->show(RP.readPrec_to_S(readPrec::RP.ReadPrec(B.Many a))p s)
 "readList"->show(readList s::[([B.Many a],String)])
 "readListPrec"->show(RP.readPrec_to_S(readListPrec::RP.ReadPrec[B.Many a])p s)
 "read"->show(read s::B.Many a)
 _->error "operation"
forceString s=foldr seq () s `seq` s
main=do
 hSetBuffering stdout LineBuffering
 content<-L.getContents
 mapM_(\line->case eitherDecode line of
  Left e->error e
  Right r->do
   let result=case kind r of "reads-only"->process(Proxy @ReadsOnly)r;"read-list-only"->process(Proxy @ReadListOnly)r;"read-conflict"->process(Proxy @ReadConflict)r;"read-prec-only"->process(Proxy @ReadPrecOnly)r;"int"->process(Proxy @Int)r;"char"->process(Proxy @Char)r;"text"->process(Proxy @String)r;"tag"->process(Proxy @ReadTag)r;"parallel"->process(Proxy @Parallel)r;"duplicate"->process(Proxy @Duplicate)r;_->error "kind"
   x<-try(evaluate(forceString result))
   L.putStrLn(encode(case x of Right v->object["result" .= v];Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)])))(L.lines content)
