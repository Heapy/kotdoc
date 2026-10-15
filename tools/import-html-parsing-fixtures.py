#!/usr/bin/env python3
"""Observe original HTML parsing modules; no Kotlin conversion or expectations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-html-parsing-reference';target=root/'core/src/test/resources/html-parsing/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_HTML_PARSING_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=(root/'upstream/pandoc/test/Tests/Helpers.hs').read_text();assert (folder/'OriginalHelpers.hs').read_text()==original.replace('module Tests.Helpers','module OriginalHelpers',1)
sources=['tools/import-html-parsing-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Parsec-BSD-2-Clause.txt','licenses/tagsoup-BSD-3-Clause.txt','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/test/Tests/Readers/HTML.hs','upstream/pandoc/test/test-pandoc.hs','upstream/pandoc/test/html-reader.html','upstream/pandoc/test/html-reader.native']
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'upstream/pandoc/src',root/'upstream/pandoc/xml-light',root/'upstream/pandoc-types/src',root/'vendor/parsec-3.1.18.0',root/'vendor/tagsoup-0.14.8'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts and '__pycache__' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert len(m['htmlUnits'])==69 and all(not isinstance(c['expected'],dict) or 'host' not in c['expected'] for c in cs)
 assert m['exposedModules']==['Text.Pandoc.Readers.HTML.Parsing','Text.Pandoc.Readers.HTML.TagCategories','Text.Pandoc.Readers.HTML.Types']
 base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
 for name in m['exposedModules']:changed=changed.replace('                   '+name+',\n','')
 changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+name+',\n' for name in m['exposedModules']))
 assert m['originalCabalHash']==hashlib.sha256(base.encode()).hexdigest() and m['exposedCabalHash']==hashlib.sha256(changed.encode()).hexdigest()
 tracked=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').split(b'\0')
 assert m['verifiedOriginalBuildFiles']==sum(bool(x) for x in tracked)
 print('Verified',len(cs),'original HTML tag-parser observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared,'--oracle and --prepared required'
# Independently verify that the reference build changed only three module exposure lines.
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
exposed=['Text.Pandoc.Readers.HTML.Parsing','Text.Pandoc.Readers.HTML.TagCategories','Text.Pandoc.Readers.HTML.Types']
for name in exposed:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+name+',\n' for name in exposed))
files=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0');verified=0
for name in filter(None,files):
 expected=changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()
 assert (args.prepared/'pandoc'/name).read_bytes()==expected,name;verified+=1
with tempfile.TemporaryDirectory(prefix='kotdoc-html-parsing-units-') as tmp:
 capture=Path(tmp)/'units.jsonl';env=dict(os.environ,KOTDOC_HTML_PARSING_CAPTURE=str(capture))
 subprocess.run([args.oracle,'--upstream-tests'],env=env,cwd=root,check=True)
 units=[json.loads(l) for l in capture.read_text().splitlines()];assert len(units)==69
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 result=json.loads(line);assert not isinstance(result,dict) or 'host' not in result,(req,result)
 return result
def add(req,group,**extra):
 c=dict(request=req,expected=ask(req),group=group,**extra);cs.append(c)
 if len(cs)%5000==0:print('Captured',len(cs),'HTML token parsing observations',flush=True)
 return c
position=[list(map(ord,'source')),1,1]
def run(ts,p=('any',),pos=position,calls=9223372036854775807):return ['run',list(p),ts,pos,calls]
sets=add(['sets'],'complete-tag-categories')['expected'];standard=ask(['standard-attrs'])
names=sorted(set(sum(sets,[])+['','a','b','span','head','body','html','rb','rt','rtc','option','optgroup','select','label','map','object','P','DIV','unknown','x:tag','é','𝛂']))
for a,b in itertools.product(names,names):
 for kind in [0,1]:
  ts=[[kind,a,[]]] if kind==0 else [[kind,a]]
  if (len(a)+len(b))%7==0:ts=[[5,-3,9223372036854775807]]+ts
  add(run(ts,['closes',b]),'all-category-opening-and-closing-pairs')
attrs=[[],[['id','']], [['id','a'],['id','b']], [['','x']], [['x','']], [['','']], [['class','a b'],['lang','en']]]
tags=[[k,s,a] if k==0 else [k,s] for k,s in itertools.product(range(5),['','x','p','漢🙂','\0\n\t"\\']) for a in (attrs if k==0 else [[]])]+[[5,r,c] for r,c in itertools.product([-1,0,1,9223372036854775807],repeat=2)]
for a,b in itertools.product(tags,tags):add(['match',a,b],'all-tag-constructors-asymmetric-matching')
for t,n,a in itertools.product(tags,['','p','P','漢🙂'],attrs):
 add(['open',n,a,t],'opening-tag-name-and-attribute-matching')
for t,n in itertools.product(tags,['','p','id','class','x','漢🙂']):
 add(['close',n,t],'closing-tag-matching');add(['maybe',n,t],'missing-versus-empty-first-attribute')
for t in tags:add(['show',t],'complete-tag-show-constructors')
for key in standard+['lang','xml:lang','epub:type','id','class','name','custom','data-','data-data-x','LANG','']:
 for pairs in [ [[key,'first'],['data-'+key,'second'],[key,'third']], [['data-'+key,'first'],[key,'second']], [['data-'+key,''],[key,'v']] ]:
  add(['attrs',pairs],'all-standard-and-prefixed-attribute-normalization');add(['string-attrs',pairs],'all-standard-and-prefixed-attribute-normalization')
spaces=[0,9,10,11,12,13,28,32,0x85,0xa0,0x1680,*range(0x2000,0x200c),0x2028,0x2029,0x202f,0x205f,0x3000,0x1f600,0x10ffff]
for c in spaces:
 for a in [[['class','a'+chr(c)+'b'],['epub:type','x'+chr(c)+'y']], [['name','fallback'],['id',''],['xml:lang','first'],['lang','second']]]:
  add(['attrs',a],'unicode-class-words-and-id-precedence')
predicates=[['any'],['never']]+[['kind',k] for k in range(6)]+[['open',n,a] for n in ['','p'] for a in attrs[:3]]+[['close','p'],['attribute','id',''],['match',[3,'']]]
for t,test,prefix,wrapper in itertools.product(tags,predicates,[[],[[5,7,9],[5,-1,0]]],['plain','fallback','look']):
 p=['satisfy',test]
 if wrapper!='plain':p=[wrapper,p]
 add(run(prefix+[t,[2,'tail']],p),'position-skipping-predicate-and-backtracking')
for ts in [[],[[5,4,7]],[[5,4,7],[5,8,9]],[[2,'']],[[2,' \t\n\r']],[[2,'\u00a0']],[[3,'comment']],[[4,'warning']],[[5,2,3],[3,''],[2,'tail']]]:
 for p in [['blank'],['raw'],['eof'],['many',['blank']],['fallback',['blank']],['sequence',[['look',['any']],['any']]],['prim',False,['any']],['prim',True,['any']],['prim',True,['never']]]:
  add(run(ts,p),'blank-raw-eof-and-generic-token-primitives')
body_cases=[[],[[2,'x']],[[2,'']],[[3,'comment']],[[2,' '],[2,'x'],[2,'\n']],[[5,2,9],[2,'x']],[[4,'warning']],[[0,'p',[]],[2,'x']],[[1,'td']],[[1,'th']],[[1,'div']],[[5,7,3]]]
for name,mode,body,ending,fn in itertools.product(['p','div','td','li',''],range(3),body_cases,[[],[[1,'p']],[[1,'div']],[[0,'div',[]]]],['in-tag','in-attr']):
 ts=[[2,' '],[3,'before'],[0,name,[['id','x'],['id','y']]]]+body+ending+[[2,' tail']]
 for content in [['tick',['any']],['pure','empty'],['many',['blank']]]:
  add(run(ts,[fn,mode,name,content]),'all-tag-omissions-attributes-and-content-state')
for name,body,ending,test in itertools.product(['p','div','td','li','a','script','body','unknown'],body_cases,[[],[[1,'p']],[[1,'div']],[[0,'div',[]]]],[['any'],['attribute','id','x']]):
 ts=[[0,name,[['id','x']]]]+body+ending
 add(run(ts,['in-tags',name,['tick',['any']]]),'many-content-and-implicit-closing')
 add(run(ts,['in-tags-where',name,test,['any']]),'opening-tag-custom-predicate')
for i,u in enumerate(units):
 ts=ask(['tags',u['input']]);u['tags']=ts
 for p in [['many',['tick',['any']]],['before',['many',['any']],['eof']],['sequence',[['look',['any']],['any']]],['in-tags',next((t[1] for t in ts if t[0]==0),'p'),['any']]]:
  add(run(ts,p),'all-original-html-unit-input-token-boundaries',unitIndex=i)
legacy=(root/'upstream/pandoc/test/html-reader.html').read_text();ts=ask(['tags',legacy])
add(run(ts,['before',['many',['tick',['any']]],['eof']]),'original-html-reader-legacy-token-boundary')
for name,line,col in itertools.product([[],[0xd800],[0xd800,0xdc00],list(map(ord,'漢🙂'))],[-1,1,9223372036854775807],[-1,1,9223372036854775807]):
 for ts in [[],[[5,-1,9223372036854775807]],[[0,'div',[]]],[[2,'x']]]:
  for parser in [['any'],['raw'],['eof'],['closes','p']]:
   add(run(ts,parser,[name,line,col]),'raw-source-names-and-int64-position-bounds')
rng=random.Random(0x485450);pool=tags+[[2,' '],[2,'\n'],[5,3,7],[5,7,3]]
for i in range(2000):
 ts=rng.choices(pool,k=rng.randrange(20));p=rng.choice([['any'],['blank'],['closes',rng.choice(names)],['many',['any']],['in-tags',rng.choice(names),['tick',['any']]],['in-attr',rng.randrange(3),rng.choice(names),['pure',None]],['prim',bool(i%2),rng.choice(predicates)]])
 p=rng.choice([p,['look',p],['attempt',p],['fallback',p],['before',p,['eof']]])
 add(run(ts,p),'generated-position-omission-state-and-error-composition')
for ts,p in [([[0,'div',[]]]*1500+[[2,'x']]+[[1,'div']]*1500,['nest',1500]), ([[5,2,3]]*30000+[[2,'x']],['any']), ([[2,'x']]*15000,['many',['tick',['any']]]), ([[0,'div',[]]]+[[2,'x']]*15000+[[1,'div']],['in-tags','div',['any']])]:
 add(run(ts,p),'finite-deep-and-long-tag-streams')
for first in range(0,0x110000,65536):
 for op in ['unicode-space','unicode-show']:add([op,first,min(65536,0x110000-first)],'all-char-space-and-tag-show-witnesses')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),htmlUnits=units,exposedModules=exposed,verifiedOriginalBuildFiles=verified,originalCabalHash=hashlib.sha256(base.encode()).hexdigest(),exposedCabalHash=hashlib.sha256(changed.encode()).hexdigest(),oracle='Complete original Pandoc library; only Cabal module exposure changes; original HTML assertions retained')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original HTML token parsing observations; SHA',m['caseHash'],flush=True)
