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
cases=[("note/competing",note(error"note-head":error"note-tail"))
 ,("note/two-prefix",note(Plain[s]:error"second-head":error"unused-tail"))
 ,("note/para-two-prefix",note(Para[s]:error"second-head":error"unused-tail"))
 ,("note/wrong-head",note(BlockQuote[]:error"unused-tail"))
 ,("note/empty",note[]),("note/plain-single",note[Plain[s]]),("note/para-single",note[Para[s]])
 ,("note/plain-tail",note(Plain[s]:error"note-tail"))
 ,("note/strict-body",note(Plain(error"body-root"):error"note-tail"))
 ,("emph/competing",emph(error"inline-head":error"inline-tail"))
 ,("emph/strong-tail",emph(Strong[s]:error"inline-tail"))
 ,("emph/strong-two",emph(Strong[s]:error"second-head":error"unused-tail"))
 ,("emph/empty",emph[]),("emph/single-strong",emph[Strong[s]]),("emph/single-other",emph[s])
 ,("figure/competing",figure(error"image-head":error"image-tail"))
 ,("figure/image-tail",figure(Image nullAttr[s]("x.png",""):error"image-tail"))
 ,("figure/image-two",figure(Image nullAttr[s]("x.png",""):error"second-head":error"unused-tail"))
 ,("figure/empty",figure[]),("figure/single-image",figure[Image nullAttr[s]("x.png","")])
 ,("alt/competing",image(error"alt-head":error"alt-tail"))
 ,("alt/empty-str-tail",image(Str"":error"alt-tail"))
 ,("alt/other-two",image(s:error"second-head":error"unused-tail"))
 ,("alt/empty",image[]),("alt/empty-str",image[Str""]),("alt/other",image[s])
 ,("cell/competing",table(error"cell-head":error"cell-tail"))
 ,("cell/plain-tail",table(Plain[s]:error"cell-tail"))
 ,("cell/plain-two",table(Plain[s]:error"second-head":error"unused-tail"))
 ,("cell/wrong-head",table(BlockQuote[]:error"cell-tail"))]
main=mapM_ observe cases
observe(name,bs)=do
 let result=case runPure$do{out<-writeAsciiDoc def(Pandoc nullMeta bs);logs<-getLog;pure(out,logs)}of{Left e->object["name".=name,"error".=show e];Right(out,logs)->object["name".=name,"output".=out,"logs".=logs]}
 forced<-try(evaluate(B.length(encode result)))::IO(Either SomeException Int64)
 B.putStrLn$encode$case forced of{Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right _->result}
