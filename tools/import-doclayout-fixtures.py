#!/usr/bin/env python3
"""Capture unchanged DocLayout operations and original test inputs independently."""
import argparse,gzip,hashlib,importlib.util,itertools,json,os,random,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/layout/doclayout.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_DOCLAYOUT_REFERENCE'));a=p.parse_args()
sources=['tools/import-doclayout-fixtures.py','tools/import-doclayout-data.py','tools/doclayout-original-tests.py','licenses/DocLayout-BSD-3-Clause.txt','upstream/pandoc/stack.yaml','vendor/emojis-0.1.5/src/Text/emojis.inc']
for folder in ['vendor/doclayout-0.6','tools/haskell-doclayout-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts and '__pycache__' not in p.parts)
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert len([c for c in cs if c.get('original')])==54
 chars=[x for c in cs if c['op']=='chars' for x in c['input']]
 assert chars==list(range(0x110000))
 assert len([c for c in cs if c.get('quickcheck')])==20000
 print(f'Verified {len(cs)} original DocLayout observations, 54 render goldens and every Char');raise SystemExit()
if not a.oracle:p.error('--oracle is required')
def native(reqs):
 r=subprocess.run([a.oracle],input=('\n'.join(json.dumps(v,separators=(',',':')) for v in reqs)+'\n').encode(),capture_output=True,check=True)
 lines=r.stdout.splitlines();assert len(lines)==len(reqs),(len(lines),len(reqs),r.stderr)
 result=[json.loads(l) for l in lines]
 assert all(not isinstance(v,dict) or 'exception' not in v for v in result),result
 return result
assert json.loads(subprocess.check_output([a.oracle,'--reference-info']))==['doclayout-0.6','emojis-0.1.5']
cs=[]
def add(op,v,name,**extra):cs.append(dict(name=str(len(cs))+' '+name,op=op,input=v,**extra))
def doc(d,name,**extra):add('doc',[d,[None,0,1,2,4,5,8,10,20,80],0],name,**extra)
sys.dont_write_bytecode=True
spec=importlib.util.spec_from_file_location('original',root/'tools/doclayout-original-tests.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
original=module.original_render_tests((root/'vendor/doclayout-0.6/test/test.hs').read_text())
for c in original:
 add('doc',[c['doc'],[c['width']],0],'original '+c['name'],original=dict(line=c['line'],golden=c['golden']))
for start in range(0,0x110000,4096):add('chars',list(range(start,min(start+4096,0x110000))),f'Char U+{start:06X}..')
emoji=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/emojis.json').read_text())
for group in ['base','zwj']:
 for i,s in enumerate(emoji[group]):add('width',list(map(ord,s)),f'original {group} emoji {i}',emoji=True)
samples=native([['samples',[0xD0C600,20000]]])[0]
for i,s in enumerate(samples):add('width',s,f'original QuickCheck Arbitrary String {i}',quickcheck=True)
rng=random.Random(0xd0c600)
special=['','a','ab\n','\n','\n\n','\t','  a','a  ','圆◯圆','a\u0302b','\u0302abc','😀😺','©️','👩\u200d💻','a\x00b']
for s in special:
 for k in ['identity','nowrap','chomp','nestle','before','flush']:
  doc(s if k=='identity' else [k,s],f'literal {k} '+repr(s))
styles=[['weight',v] for v in [False,True]]+[['shape',v] for v in [False,True]]+[['strikeout',v] for v in [False,True]]+[['underline',v] for v in range(4)]+[[kind,v] for kind in ['fg','bg'] for v in [None,*range(8)]]
for s in styles:
 for nested in [False,True]:doc(['styled',s,['styled',['weight',True],['hsep',['a','b','c']]]] if nested else ['styled',s,['hsep',['a','b','c']]],'style '+repr(s)+str(nested))
for s in special:
 for prefix in ['','> ','   ','😀 ','\t','\u00a0','\u2028','\u2029','\u0085']:
  for constructor in ['prefixed','after']:
   doc([constructor,prefix,s] if constructor=='prefixed' else ['append',['after',prefix],s],constructor+' '+repr((prefix,s)))
default=[False,False,0,False,None,None,None]
for width in [-3,0,1,2,4,8]:
 for s in special:
  for kind in ['lblock','rblock','cblock']:doc([kind,width,s],f'{kind} {width} {s!r}')
 for lines in [[],[''],['a','','b'],['abc','圆','😀']]:
  block=['block',width,[[[None,default,s]] for s in lines]]
  for d in [block,['append',block,['fill','|']],['append',['fill','|'],block],['prefixed','> ',block]]:doc(d,'raw block '+repr(d))
for i in range(128):
 font=[bool(i&1),bool(i&2),(i>>2)%4,bool(i&16),(i>>5)%8,None,'font-link' if i&64 else None]
 parts=[[None,font,'ab\n'],['https://a',default,'cd'],[None,font,'\u0302😀']]
 for kind in ['cooked','block']:
  doc([kind,5,parts if kind=='cooked' else [parts]],f'attributed {kind} {i}')
for linkTarget in ['','https://x','a\x1bb','😀']:
 for inner in ['a',['linked','inner','b'],['linked','middle',['linked','inner','b']]]:
  doc(['hcat',[['linked',linkTarget,inner],'c']],'links '+repr((linkTarget,inner)))
def random_doc(depth):
 if depth==0 or rng.randrange(5)==0:return rng.choice(special+[['empty'],['space'],['cr'],['newline'],['blanklines',rng.randrange(-2,4)]])
 kind=rng.choice(['append','concat','spaced','above','separated','prefixed','before','flush','after','styled','linked','nowrap','chomp','nestle','nest','lblock','rblock','cblock','hcat','hsep','vcat','vsep','braces','brackets','parens','quotes','doubleQuotes'])
 if kind in ['append','concat','spaced','above','separated']:return [kind,random_doc(depth-1),random_doc(depth-1)]
 if kind=='after':return [kind,rng.choice(special)]
 if kind in ['prefixed','linked']:return [kind,rng.choice(['','> ','  ','😀','url']),random_doc(depth-1)]
 if kind=='styled':return [kind,rng.choice(styles),random_doc(depth-1)]
 if kind in ['nest','lblock','rblock','cblock']:return [kind,rng.randrange(-2,12),random_doc(depth-1)]
 if kind in ['hcat','hsep','vcat','vsep']:return [kind,[random_doc(depth-1) for _ in range(rng.randrange(5))]]
 return [kind,random_doc(depth-1)]
for i in range(3000):doc(random_doc(5),'random document '+str(i))
for i,font in enumerate(itertools.product([False,True],[False,True],range(4),[False,True],[None,*range(8)],[None,*range(8)],[None,'font-link'])):
 add('font',[font,[]],f'font state {i}')
 if i%17==0:add('font',[font,styles],f'font requests {i}')
parts_cases=[[],[[None,default,'']]]+[[[None,default,s]] for s in special]
for i in range(48):
 parts_cases.append([[rng.choice([None,'url','']),[bool(i&1),bool(i&2),i%4,bool(i&4),i%8,(i//8)%8,None],rng.choice(special)] for _ in range(rng.randrange(1,5))])
operations=['identity','lblock','rblock','cblock','prefix','bold','link','nowrap','chomp','nestle']
for i,parts in enumerate(parts_cases):
 add('attributed',parts,f'HasChars Attributed {i}')
 for operation in operations:add('rich-doc',[parts,operation,[None,0,1,4,10]],f'rich document {i} {operation}')
for i,part in enumerate([p for ps in parts_cases for p in ps]):
 for operation in operations:add('part-doc',[part,operation,[None,0,1,4,10]],f'Attr document {i} {operation}')
for i,(a1,a2) in enumerate(itertools.product([p for ps in parts_cases for p in ps][:30],repeat=2)):
 add('attr-append',[a1,a2],f'Attr semigroup {i}')
point_cases=[[],[0],[10],[13,10],[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000],[97,0xd800,0xdc00,98,10],
 [0x10ffff],[0x301,97,10],[0x1f469,0x200d,0x1f4bb],[0xd800,10,0xdc00],[0xdc00,0xd800]]
for i,points in enumerate(point_cases):
 for operation in operations:add('string-doc',[points,operation,[None,0,1,4,10]],f'Haskell String {i} {operation}')
doc(['quotes',['before',['before','abc']]],'nested BeforeNonBlank measurement regression')
doc(['linked','outer',['append',['linked','inner',['space']],'abcde']],'nested link breaking lookahead regression')
for n,k in itertools.product([-2**63,-2**63+1,-1,0,1,2**63-2,2**63-1],repeat=2):
 for d in [['raw-text',n,'x'],['append',['raw-text',n,'x'],['raw-text',k,'y']],['spaced',['raw-text',n,'x'],'y'],['prefixed','> ',['raw-text',n,'x']]]:
  add('measure',[d,k],f'Int64 measure {n} {k}')
for n,s,kind in itertools.product([-2,0,1,5],['','a\nb','😀',' a'],['raw-text','vfill']):
 d=[kind,n,s]
 for value in [d,['append',d,'x'],['hcat',[['space'],d,'y']],['prefixed','\n> ',d],['styled',['weight',True],d]]:
  doc(value,'explicit cached width '+repr(value))
for c in cs[:]:
 if c['op']=='doc' and (c.get('original') or rng.randrange(6)==0):add('walk',c['input'][0],'derived value walk '+c['name'])
for start in range(0,len(cs),128):
 batch=cs[start:start+128]
 for c,e in zip(batch,native([[c['op'],c['input']] for c in batch])):
  c['expected']=e
  if c.get('original'):assert e['renders'][0][0]==c['original']['golden'],c
  if c.get('emoji'):assert e[0]==2,c
 if start%2048==0:print(f'Captured {start+len(batch)}/{len(cs)} DocLayout observations',flush=True)
target.parent.mkdir(parents=True,exist_ok=True)
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [dict(sources=hashes,cases=len(cs),caseHash=digest(cs))]+cs)+'\n').encode(),mtime=0))
print(f'Captured {len(cs)} original DocLayout observations')
