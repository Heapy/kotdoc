#!/usr/bin/env python3
"""Generate expressions only; actual derived dictionaries supply every expectation."""
from pathlib import Path
import json
P=Path(__file__).resolve().parent;R=P.parents[1]
source=json.loads((R/'tools/haskell-types-deepseq-reference/demand-inputs.json').read_text())
rows=[]
for r in source:
 if r['operation']!='rwhnf':continue
 for op in ['from','toFrom']:
  row=dict(r);row['operation']=op;row['name']=r['name'].removesuffix('/rwhnf')+'/'+op
  x='(('+r['expression']+')::'+r['kind']+')'
  expr='from '+x if op=='from'else 'to (from '+x+')::'+r['kind']
  row['observe']='('+expr+') `seq` (7::Int)';rows.append(row)
for name in [x['name']for x in json.loads((R/'tools/haskell-types-generic-reference/definition-schema.json').read_text())['types']]+['P.Many Int']:
 for op in ['datatypeName','moduleName','packageName','isNewtype','toRoot']:
  expression='(to (error "representation"::Rep ('+name+') ())::'+name+') `seq` (7::Int)'if op=='toRoot'else op+' (from (error "input"::'+name+'))'
  rows.append({'name':name+'/'+op,'kind':name,'operation':op,'observe':expression})
for op in ['from','toFrom']:
 for mode,expr in [('root','error "root"'),('element','P.singleton (error "element")')]:
  expression='from ('+expr+'::P.Many Int)'if op=='from'else 'to (from ('+expr+'::P.Many Int))::P.Many Int'
  rows.append({'name':'Many/'+mode+'/'+op,'kind':'P.Many Int','mode':mode,'operation':op,'observe':'('+expression+') `seq` (7::Int)'})
for typ in ['P.Many (Int->Int)','P.Many T.Text','P.Many Bool','P.Many (Int,Maybe T.Text)']:
 for op in ['from','toFrom']:
  for mode,expr in [('root','error "root"'),('element','P.singleton (error "element")')]:
   expression='from ('+expr+'::'+typ+')'if op=='from'else 'to (from ('+expr+'::'+typ+'))::'+typ
   rows.append({'name':typ+'/'+mode+'/'+op,'kind':typ,'mode':mode,'operation':op,'observe':'('+expression+') `seq` (7::Int)'})
(P/'demand-inputs.json').write_text(json.dumps(rows,indent=2)+'\n')
header='''{-# LANGUAGE ScopedTypeVariables, OverloadedStrings, TypeFamilies #-}
-- Original derived Generic calls only. No Kotlin-produced expectations.
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import GHC.Generics hiding (Meta)
import qualified Data.Map as M
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
import Data.Aeson
import System.IO
observe name value=do
 result<-try(evaluate(length value)>>pure value)
 B.putStrLn(encode(name::String))
 B.putStrLn(encode(case result of Right v->object["result" .= v];Left(e::SomeException)->object["exception" .= takeWhile(/='\\n')(displayException e)]))
main=do
 hSetBuffering stdout LineBuffering
'''
for r in rows:header+=' observe '+json.dumps(r['name'])+' (show ('+r['observe']+'))\n'
(P/'GenericRepDemand.hs').write_text(header);print(len(rows),'original partial/metadata expressions')
