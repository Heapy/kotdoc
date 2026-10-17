{-# LANGUAGE OverloadedStrings #-}
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Text.DocTemplates(compileTemplate)
import Data.Int(Int64)
import Data.Default
import Data.Aeson
import Control.Exception
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import qualified Data.Map as M
cases :: [(T.Text,Pandoc)]
cases=
 [("note/root",Pandoc nullMeta[Para[Note(error"note-root")]])
 ,("note/head2",Pandoc nullMeta[Para[Note[error"note-head",Para[Str"tail"]]]])
 ,("note/first-para-inline2",Pandoc nullMeta[Para[Note[Para(error"inline-root"),Para[Str"tail"]]]])
 ,("note/tail",Pandoc nullMeta[Para[Note(Plain[Str"x"]:error"note-tail")]])
 ,("note/multi-ignored-inline",Pandoc nullMeta[Para[Note[Plain[Str"x"],Para(error"ignored-inline")]]])
 ,("note/first-quote-inner",Pandoc nullMeta[Para[Note[BlockQuote(error"ignored-quote"),Para[Str"tail"]]]])
 ,("meta/ignored",Pandoc (Meta(error"meta-root"))[Para[Str"body"]])
 ,("caption/ignored-short",Pandoc nullMeta[Figure nullAttr(Caption(Just(error"ignored-short"))[])[Para[Str"body"]]])
 ,("code/ignored-attrid",Pandoc nullMeta[CodeBlock(error"code-attrid",[],[])"code"])
 ]
main=mapM_ observe cases
observe(name,doc)=do
 let result=case runPure$do{out<-writeAsciiDoc def doc;logs<-getLog;pure(out,logs)}of{Left e->object["name".=name,"error".=show e];Right(out,logs)->object["name".=name,"output".=out,"logs".=logs]}
 forced<-try(evaluate(B.length(encode result)))::IO(Either SomeException Int64)
 case forced of{Left e->B.putStrLn$encode$object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right _->B.putStrLn$encode result}
