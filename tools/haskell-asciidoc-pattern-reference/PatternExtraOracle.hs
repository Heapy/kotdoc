{-# LANGUAGE OverloadedStrings #-}
-- Original writer demand observations, GPL-2.0-or-later; Pandoc authors.
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Text.Pandoc.Logging
import Data.Default
import Data.Aeson
import Control.Exception
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as B
import Data.Int(Int64)
p x=Para x
s=Str "x"
note xs=[p[Note xs]]
emph xs=[p[Emph xs]]
figure xs=[Figure nullAttr(Caption Nothing[])[Plain xs]]
image xs=[p[Image nullAttr xs("x.png","")]]
table bs=[Table nullAttr(Caption Nothing[])[(AlignDefault,ColWidthDefault)](TableHead nullAttr[])[TableBody nullAttr 0[][Row nullAttr[Cell nullAttr AlignDefault(RowSpan 1)(ColSpan 1)bs]]](TableFoot nullAttr[])]
cases :: [(T.Text,[Block])]
cases=[("figure/body-competing",[Figure nullAttr(Caption Nothing[])(error"block-head":error"block-tail")])
 ,("figure/body-inline-before-tail",[Figure nullAttr(Caption Nothing[])(Plain[error"image-head"]:error"body-tail")])
 ,("bullet/task-competing",[BulletList[error"block-head":error"block-tail"]])
 ,("ordered/competing",[OrderedList(1,DefaultStyle,DefaultDelim)[error"block-head":error"block-tail"]])
 ,("section/second-head-before-tail",[Div("",["section"],[])(Header 1 nullAttr[Str"title"]:error"second-head":error"unused-tail")])
 ,("title/wrong-class-ignored-tail",[Div("",["note"],[])[Div("","other":error"class-tail",[])[Para[Str"title"]],Para[Str"body"]]])
 ,("span/classes-competing",[Para[Span("",error"class-head":error"class-tail",[])[Str"x"]]])
 ,("code/classes-competing",[CodeBlock("",error"class-head":error"class-tail",[])"x"])]
main=mapM_ observe cases
observe(name,bs)=do
 let result=case runPure$do{out<-writeAsciiDoc def(Pandoc nullMeta bs);logs<-getLog;pure(out,logs)}of{Left e->object["name".=name,"error".=show e];Right(out,logs)->object["name".=name,"output".=out,"logs".=logs]}
 forced<-try(evaluate(B.length(encode result)))::IO(Either SomeException Int64)
 B.putStrLn$encode$case forced of{Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right _->result}
