#!/usr/bin/env python3
"""Construct partial source inputs, never expected results."""
from pathlib import Path
import json
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
schema=json.loads((ROOT/'tools/haskell-types-generic-reference/definition-schema.json').read_text());decl={d['name']:d for d in schema['types']};aliases=schema.get('aliases',{})
# The checked source schema stores aliases separately in the Kotlin descriptor;
# these five signatures below are verbatim Definition aliases.
N=lambda name:{'name':name};L=lambda x:{'list':x};T=lambda *xs:{'tuple':list(xs)}
aliases={'Attr':T(N('Text'),L(N('Text')),L(T(N('Text'),N('Text')))),'Target':T(N('Text'),N('Text')),'ListAttributes':T(N('Int'),N('ListNumberStyle'),N('ListNumberDelim')),'ShortCaption':L(N('Inline')),'ColSpec':T(N('Alignment'),N('ColWidth'))}
def default(t):
 if 'list'in t:return '[]'
 if 'tuple'in t:return '('+','.join(map(default,t['tuple']))+')'
 if 'apply'in t:return 'Nothing'if t['apply']=='Maybe'else 'M.empty'
 name=t['name']
 if name in aliases:return default(aliases[name])
 if name in {'Text','Int','Double','Bool','Char'}:return {'Text':'"x"','Int':'0','Double':'0.5','Bool':'True','Char':"'x'"}[name]
 c=decl[name]['constructors'][0];return '('+c['name']+''.join(' ('+default(x)+')'for x in c['fields'])+')'
def deep(t,tag):
 err='error '+json.dumps(tag)
 if 'list'in t:return '['+err+']'
 if 'tuple'in t:return '('+','.join(err if i==0 else default(x)for i,x in enumerate(t['tuple']))+')'
 if 'apply'in t:return '(Just ('+err+'))'if t['apply']=='Maybe'else '(M.singleton "k" ('+err+'))'
 name=t['name']
 if name in aliases:return deep(aliases[name],tag)
 if name in {'Text','Int','Double','Bool','Char'}:return None
 for c in decl[name]['constructors']:
  for i,field in enumerate(c['fields']):
   inner=deep(field,tag)
   if inner:return '('+c['name']+''.join(' ('+(inner if j==i else default(x))+')'for j,x in enumerate(c['fields']))+')'
 return None
rows=[]
def add(name,kind,expression,operation,**input):rows.append(dict(name=name,kind=kind,expression=expression,operation=operation,**input))
for name,d in decl.items():
 for op in ['rnf','rwhnf']:
  add(name+'/root/'+op,name,'error "root"',op,mode='root')
 for c in d['constructors']:
  for i,t in enumerate(c['fields']):
   for mode in ['field','deep']:
    replacement='error "field"'if mode=='field'else deep(t,'deep')
    if replacement is None:continue
    expression='('+c['name']+''.join(' ('+(replacement if j==i else default(x))+')'for j,x in enumerate(c['fields']))+')'
    for op in ['rnf','rwhnf']:add(name+'/'+c['name']+'/'+str(i)+'/'+mode+'/'+op,name,expression,op,mode=mode,constructor=c['name'],field=i)
# Competing bottom fields expose the installed GHC constructor and Generic-product
# demand choices; the expected winner is captured, never guessed by this script.
for name,d in decl.items():
 for c in d['constructors']:
  for i,a in enumerate(c['fields']):
   for j in range(i+1,len(c['fields'])):
    b=c['fields'][j]
    for mode in ['pairRoot','pairDeep','mixed']:
     left='error "first"'if mode=='pairRoot'else deep(a,'first')
     right=deep(b,'second')if mode=='pairDeep'else 'error "second"'
     if left is None or right is None:continue
     expression='('+c['name']+''.join(' ('+(left if k==i else right if k==j else default(x))+')'for k,x in enumerate(c['fields']))+')'
     for op in ['rnf','rwhnf']:add(name+'/'+c['name']+'/'+str(i)+'-'+str(j)+'/'+mode+'/'+op,name,expression,op,mode=mode,constructor=c['name'],field=i,secondField=j)
(HERE/'demand-inputs.json').write_text(json.dumps(rows,indent=2)+'\n')
header='''{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Original source NFData evaluation only. Inputs generated independently of Kotlin.
import Text.Pandoc.Definition
import Control.DeepSeq
import Control.Exception
import qualified Data.Map as M
import qualified Data.ByteString.Lazy.Char8 as B
import Data.Aeson
import System.IO
observe name value = do
 result<-try(evaluate value)
 B.putStrLn(encode(name::String))
 B.putStrLn(encode(case result of Right ()->object["result" .= ("()"::String)];Left(e::SomeException)->object["exception" .= takeWhile(/='\\n')(displayException e)]))
main=do
 hSetBuffering stdout LineBuffering
'''
for row in rows:header+=' observe '+json.dumps(row['name'])+' ('+row['operation']+' (('+row['expression']+')::'+row['kind']+'))\n'
(HERE/'DeepSeqDemand.hs').write_text(header);print(len(rows),'original partial-field inputs')
