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
      k | k `elem` ["bad-spine-tail","empty-sections","empty-body","empty-head-row","empty-foot-row"]->(AlignDefault,ColWidthDefault):failAt "column-spine" "column spine forced"
      _->[]
     caption=if kind `elem` ["bad-spine-tail","empty-sections","empty-body","empty-head-row","empty-foot-row"]then Caption Nothing[Plain[trace "EVENT caption"(Str "cap")]]else Caption Nothing[]
     bodies=case kind of
      "empty-sections"->[]
      "empty-head-row"->[]
      "empty-foot-row"->[]
      "empty-body"->[TableBody nullAttr 0[][]]
      _->[TableBody nullAttr 0[][Row nullAttr[Cell nullAttr AlignDefault 1 1[Plain[Str "x"]]]]]
     headRows=if kind=="empty-head-row"then[Row nullAttr[]]else[]
     footRows=if kind=="empty-foot-row"then[Row nullAttr[]]else[]
     doc=Pandoc nullMeta[Table nullAttr caption specs(TableHead nullAttr headRows)bodies(TableFoot nullAttr footRows)]
     result=case runPure(writeOrg def doc)of
      Left e->errorWithoutStackTrace(show e)
      Right t->T.length t `seq` t
 observed<-E.try(E.evaluate result)::IO(Either E.SomeException T.Text)
 B.putStrLn$encode$case observed of
  Left e->object["kind".=kind,"error".=E.displayException e]
  Right t->object["kind".=kind,"output".=t]
