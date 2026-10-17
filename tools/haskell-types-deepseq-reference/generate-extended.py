#!/usr/bin/env python3
"""Additional actual runtime expressions; Kotlin never supplies expectations."""
from pathlib import Path
import json
p=Path(__file__).resolve().parent
cases=[]
def add(name,expression):cases.append({'name':name,'expression':expression})
for name,expression in [
 ('force/ignored','show (const (7::Int) (force (error "root"::Inline)))'),
 ('force/root','show (force (error "root"::Inline) `seq` (7::Int))'),
 ('force/deep','show (force (Emph [error "head"]) `seq` (7::Int))'),
 ('force/identity','show (force (Emph [Space]))'),
 ('deepseq/ignored','show (const (7::Int) (deepseq (error "first"::Inline) (error "second"::Int)))'),
 ('deepseq/first','show (deepseq (error "first"::Inline) (error "second"::Int))'),
 ('deepseq/second','show (deepseq Space (error "second"::Int))'),
 ('strictApply/const','show (((const (7::Int)) $!! Emph [error "head"]))'),
 ('strictApply/function','show (((error "function"::Inline->Int) $!! Space))'),
 ('strictApply/both','show (((error "function"::Inline->Int) $!! (error "input"::Inline)))'),
 ('rwhnf/lazy-child','show (rwhnf (Emph [error "head"]))'),
 ('rwhnf/newtype','show (rwhnf (RowSpan (error "int")))'),
 ('custom/ignored','show (rnf (error "ignored"::Ignored))'),
 ('custom/list','show (rnf [error "ignored"::Ignored])'),
 ('custom/tuple','show (rnf ((error "ignored"::Ignored),Space))'),
 ('custom/list-tail','show (rnf ((error "ignored"::Ignored):error "tail"))'),
 ('list/head','show (rnf [error "head"::Inline])'),
 ('list/tail','show (rnf (Space:error "tail"))'),
 ('list/order','show (rnf ((error "head"::Inline):error "tail"))'),
 ('tuple/order','show (rnf ((error "first"::Inline),(error "second"::Inline)))'),
 ('triple/order','show (rnf (Space,(error "second"::Inline),(error "third"::Inline)))'),
 ('maybe/none','show (rnf (Nothing::Maybe Inline))'),
 ('maybe/just','show (rnf (Just (error "just"::Inline)))'),
 ('map/value','show (rnf (M.singleton "k" (error "value"::Inline)))'),
 ('map/key','show (rnf (MI.Bin 1 (error "key"::T.Text) Space MI.Tip MI.Tip))'),
 ('map/left','show (rnf (MI.Bin 1 ("k"::T.Text) Space (error "left") MI.Tip))'),
 ('map/right','show (rnf (MI.Bin 1 ("k"::T.Text) Space MI.Tip (error "right")))'),
 ('map/root-before-value','show (rnf (MI.Bin 1 ("k"::T.Text) (error "value"::Inline) (error "left") MI.Tip))'),
 ('map/value-before-left-value','show (rnf (MI.Bin 2 ("m"::T.Text) (error "root-value"::Inline) (MI.Bin 1 "a" (error "left-value") MI.Tip MI.Tip) MI.Tip))'),
 ('map/key-before-value','show (rnf (MI.Bin 1 (error "key"::T.Text) (error "value"::Inline) MI.Tip MI.Tip))')]:add(name,expression)
for size in [0,1,32,4096,50000]:
 add('deep/inline/'+str(size),'show (rnf (iterate (Emph . (:[])) Space !! '+str(size)+'))')
 add('deep/list/'+str(size),'show (rnf (replicate '+str(size)+' Space))')
 add('deep/list-last/'+str(size),'show (rnf (replicate '+str(size)+' Space ++ [error "last"]))')
 add('deep/map/'+str(size),'show (rnf (M.fromDistinctAscList [(i,Space)|i<-[1..'+str(size)+'::Int]]))')
for size in [1,4096,50000]:
 add('deep/map-skew/'+str(size),'show (rnf (foldr (\\i rest->MI.Bin 1 i Space rest MI.Tip) MI.Tip [1..'+str(size)+'::Int]))')
 add('deep/map-skew-tail/'+str(size),'show (rnf (foldr (\\i rest->MI.Bin 1 i Space rest MI.Tip) (error "tail") [1..'+str(size)+'::Int]))')
add('map/size-min','show (rnf (MI.Bin minBound ("k"::T.Text) Space MI.Tip MI.Tip))')
add('map/size-max','show (rnf (MI.Bin maxBound ("k"::T.Text) Space MI.Tip MI.Tip))')
add('double/nan','show (rnf (ColWidth (0/0)))')
add('double/positive-infinity','show (rnf (ColWidth (1/0)))')
add('double/negative-infinity','show (rnf (ColWidth ((-1)/0)))')
add('custom/deepseq-ignored','show (deepseq (error "input"::Ignored) (7::Int))')
add('custom/strictApply-ignored','show ((const (7::Int)) $!! (error "input"::Ignored))')
add('custom/strictApply-demanded','show ((\\x->x `seq` (7::Int)) $!! (error "input"::Ignored))')
add('custom/force','show (force (error "input"::Ignored) `seq` (7::Int))')
add('rnf/ignored','show (const (7::Int) (rnf (error "root"::Inline)))')
add('rwhnf/ignored','show (const (7::Int) (rwhnf (error "root"::Inline)))')
add('custom/force-ignored','show (const (7::Int) (force (error "input"::Ignored)))')
add('custom/pair-root','show (rnf (error "pair"::PairBox))')
add('custom/pair-second-ignored','show (rnf (PairBox Space (error "second")))')
add('custom/pair-first','show (rnf (PairBox (error "first") Space))')
add('custom/pair-deep','show (rnf (PairBox (Emph [error "head"]) (error "second")))')
add('custom/pair-force','show (force (PairBox Space (error "second")) `seq` (7::Int))')
add('custom/pair-apply-ignored','show ((const (7::Int)) $!! PairBox Space (error "second"))')
add('custom/pair-apply-second','show ((\\(PairBox _ b)->b `seq` (7::Int)) $!! PairBox Space (error "second"))')
add('custom/map-value-ignored','show (rnf (MI.Bin 1 ("k"::T.Text) (error "value"::Ignored) MI.Tip MI.Tip))')
add('custom/map-key-root','show (rnf (MI.Bin 1 (error "key"::Ignored) Space MI.Tip MI.Tip))')
add('custom/map-key-pair','show (rnf (MI.Bin 1 (PairBox Space (error "second")) Space MI.Tip MI.Tip))')
add('custom/map-key-pair-first','show (rnf (MI.Bin 1 (PairBox (error "first") Space) Space MI.Tip MI.Tip))')
add('custom/map-key-before-value','show (rnf (MI.Bin 1 (PairBox (error "first") Space) (error "value"::Inline) MI.Tip MI.Tip))')
for scalar,defined in [('Int','0'),('Double','0/0'),('Bool','False'),('Char',"'x'"),('Text','"unicode漢😀"')]:
 typ='T.Text'if scalar=='Text'else scalar
 add('scalar/'+scalar+'/root','show (rnf (error "scalar"::'+typ+'))')
 add('scalar/'+scalar+'/defined','show (rnf (('+defined+')::'+typ+'))')
for operation in ['force','apply']:
 for field in ['root','head']:
  inner='Emph (error "field")' if field=='root' else 'Emph [error "head"]'
  expression=('force (Ignored ('+inner+')) `seq` (7::Int)') if operation=='force' else ('((\\x->x `seq` (7::Int)) $!! Ignored ('+inner+'))')
  add('custom/strict-root/'+operation+'/'+field,'show ('+expression+')')
outputs=[('list-root','(error "result root"::[Int])'),('list-head','[error "head"::Int]'),('map-root','(error "result root"::M.Map T.Text Inline)'),('map-value','MI.Bin 1 ("k"::T.Text) (error "value"::Inline) MI.Tip MI.Tip'),('map-size','MI.Bin (error "size") ("k"::T.Text) Space MI.Tip MI.Tip'),('map-left','MI.Bin 1 ("k"::T.Text) Space (error "left") MI.Tip'),('node-root','Emph (error "field")'),('node-head','Emph [error "head"]'),('newtype','RowSpan (error "int")'),('tuple','((error "first"::Inline),Space)')]
for operation in ['deepseq','apply']:
 for shape,out in outputs:
  application=('deepseq (error "ignored"::Ignored) ('+out+')') if operation=='deepseq' else ('((const ('+out+')) $!! (error "ignored"::Ignored))')
  add('return/'+operation+'/'+shape,'show (('+application+') `seq` (7::Int))')
 unused='deepseq (error "input"::Inline) (error "result root"::[Int])' if operation=='deepseq' else '(const (error "result root"::[Int])) $!! (error "input"::Inline)'
 add('return/'+operation+'/unused','show (const (7::Int) ('+unused+'))')
 application='deepseq (error "input"::Inline) (error "result root"::[Int])' if operation=='deepseq' else '(const (error "result root"::[Int])) $!! (error "input"::Inline)'
 add('return/'+operation+'/input-first','show (('+application+') `seq` (7::Int))')
(p/'extended-inputs.json').write_text(json.dumps(cases,indent=2)+'\n')
header='''{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Direct original runtime calls, including custom NFData dictionaries.
import Text.Pandoc.Definition
import Control.DeepSeq
import Control.Exception
import qualified Data.Map as M
import qualified Data.Map.Internal as MI
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as B
import Data.Aeson
import System.IO
newtype Ignored=Ignored Inline
instance NFData Ignored where rnf _=()
data PairBox=PairBox Inline Inline
instance NFData PairBox where rnf (PairBox x _)=rnf x
observe name value = do
 result<-try(evaluate(length value)>>pure value)
 B.putStrLn(encode(name::String))
 B.putStrLn(encode(case result of Right result->object["result" .= result];Left(e::SomeException)->object["exception" .= takeWhile(/='\\n')(displayException e)]))
main=do
 hSetBuffering stdout LineBuffering
'''
for case in cases:header+=' observe '+json.dumps(case['name'])+' ('+case['expression']+')\n'
(p/'DeepSeqExtended.hs').write_text(header);print(len(cases),'extended original expressions')
