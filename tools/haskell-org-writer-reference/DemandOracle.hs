{-# LANGUAGE OverloadedStrings #-}
import SourceOrgWriter
import Text.Pandoc hiding(writeOrg,trace)
import Data.Aeson
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Control.Exception as E
import Debug.Trace(trace)
import System.Environment(getArgs)
main=do
 [kind]<-getArgs
 let failAt event message=trace("EVENT "++event)(errorWithoutStackTrace message)
     specs=case kind of
      "bad-root"->failAt "column-root" "column root forced"
      "bad-pair"->[failAt "column-pair" "column pair forced"]
      "bad-tuple-fields"->[(failAt "alignment" "alignment forced",failAt "width" "width forced")]
      "bad-spine-tail"->(AlignDefault,ColWidthDefault):failAt "column-spine" "column spine forced"
      _->[]
     caption=if kind=="bad-spine-tail"then Caption Nothing[Plain[trace "EVENT caption"(Str "cap")]]else Caption Nothing[]
     doc=Pandoc nullMeta[Table nullAttr caption specs(TableHead nullAttr[])[TableBody nullAttr 0[][Row nullAttr[Cell nullAttr AlignDefault 1 1[Plain[Str "x"]]]]](TableFoot nullAttr[])]
     result=case runPure(writeOrg def doc)of
      Left e->errorWithoutStackTrace(show e)
      Right t->T.length t `seq` t
 observed<-E.try(E.evaluate result)::IO(Either E.SomeException T.Text)
 B.putStrLn$encode$case observed of
  Left e->object["kind".=kind,"error".=E.displayException e]
  Right t->object["kind".=kind,"output".=t]
