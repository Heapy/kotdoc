#!/usr/bin/env python3
"""Direct partial Rep inputs; output always comes from GHC's original derived to."""
from pathlib import Path
import json,copy
P=Path(__file__).resolve().parent;R=P.parents[1]
old=R/'tools/haskell-types-deepseq-reference/generate-demand.py';ns={'__file__':str(old)};exec(old.read_text().split('rows=[]')[0],ns)
declarations=ns['decl'];default=ns['default'];deep=ns['deep']
def product(fields):
 if not fields:return {'tag':'U1'}
 if len(fields)==1:return fields[0]
 at=len(fields)//2;return {'tag':'Product','left':product(fields[:at]),'right':product(fields[at:])}
def constructor(c,values):return {'tag':'M1','child':product([{'tag':'M1','child':{'tag':'K1','index':i,'expression':v}}for i,v in enumerate(values)])}
def sumtree(constructors,index,body):
 if len(constructors)==1:return body
 at=len(constructors)//2
 return {'tag':'L1','child':sumtree(constructors[:at],index,body)}if index<at else {'tag':'R1','child':sumtree(constructors[at:],index-at,body)}
def tree(d,c,values):return {'tag':'M1','child':sumtree(d['constructors'],d['constructors'].index(c),constructor(c,values))}
def render(t):
 tag=t['tag']
 if tag=='error':return '(error '+json.dumps(t['message'])+')'
 if tag=='U1':return 'U1'
 if tag=='K1':return '(K1 ('+t['expression']+'))'
 if tag=='Product':return '('+render(t['left'])+' :*: '+render(t['right'])+')'
 return '('+tag+' '+render(t['child'])+')'
def paths(t,path=[]):
 yield path,t
 for key in ['child','left','right']:
  if key in t:yield from paths(t[key],path+[key])
def replace(t,path,new):
 if not path:return new
 result=copy.deepcopy(t);at=result
 for key in path[:-1]:at=at[key]
 at[path[-1]]=new;return result
rows=[]
for name,d in declarations.items():
 for c in d['constructors']:
  values=[default(x)for x in c['fields']];base=tree(d,c,values)
  for path,node in paths(base):
   changed=replace(base,path,{'tag':'error','message':'representation'})
   rows.append({'name':name+'/'+c['name']+'/rep/'+'.'.join(path),'kind':name,'constructor':c['name'],'mode':'representation','path':path,'tree':changed})
   # An independent spine-vs-field race, not assumed left-to-right evaluation.
   if node['tag']=='Product' and path and c['fields'] and 'right' in path:
    raced=tree(d,c,['error "first"']+values[1:]);raced=replace(raced,path,{'tag':'error','message':'spine'})
    rows.append({'name':name+'/'+c['name']+'/race/'+'.'.join(path),'kind':name,'constructor':c['name'],'mode':'race','path':path,'tree':raced})
  for i,t in enumerate(c['fields']):
   for mode,value in [('field','error "field"'),('deep',deep(t,'deep'))]:
    if value is None:continue
    fields=values.copy();fields[i]=value
    rows.append({'name':name+'/'+c['name']+'/'+str(i)+'/'+mode,'kind':name,'constructor':c['name'],'mode':mode,'field':i,'path':[],'tree':tree(d,c,fields)})
(P/'direct-inputs.json').write_text(json.dumps(rows,indent=2)+'\n')
header='''{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Every partial representation is interpreted by the pinned derived Generic.to.
import Text.Pandoc.Definition
import GHC.Generics hiding (Meta)
import qualified Data.Map as M
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
import Data.Aeson
import System.IO
observe name value=do
 result<-try(evaluate value)
 B.putStrLn(encode(name::String))
 B.putStrLn(encode(case result of Right v->object["result" .= show(v::Int)];Left(e::SomeException)->object["exception" .= takeWhile(/='\\n')(displayException e)]))
main=do
 hSetBuffering stdout LineBuffering
'''
for r in rows:header+=' observe '+json.dumps(r['name'])+' ((to '+render(r['tree'])+'::'+r['kind']+') `seq` (7::Int))\n'
(P/'GenericRepDirect.hs').write_text(header);print(len(rows),'original direct partial Rep expressions')
