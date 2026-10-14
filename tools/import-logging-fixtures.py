#!/usr/bin/env python3
"""Original Logging/Definition/Parsec/GHC behavior; expected output never uses Kotlin."""
import argparse,atexit,base64,collections,gzip,hashlib,itertools,json,os,random,re,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/logging/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_LOGGING_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
source=(root/'upstream/pandoc/src/Text/Pandoc/Logging.hs').read_text()
decl=source.split('data LogMessage =',1)[1].split('deriving (Show, Eq',1)[0]
constructors=[]
for line in decl.splitlines():
 line=line.strip().lstrip('|').strip()
 if line:
  parts=re.findall(r'\([^)]*\)|\[[^]]*\]|\S+',line);constructors.append((parts[0],parts[1:]))
shared=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text();a=shared.index('tshow ::');b=shared.index('\n\n',a)
assert shared[a:b] in (root/'tools/haskell-logging-reference/Text/Pandoc/Shared.hs').read_text()
sources=['tools/import-logging-fixtures.py','upstream/pandoc/src/Text/Pandoc/Logging.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-log.lua','core/src/test/resources/native/upstream.json','core/src/test/resources/ast-show/data.jsonl.gz','core/src/test/resources/fb2/oracle.json','core/src/test/resources/fb2/writer.json','licenses/Parsec-BSD-2-Clause.txt','licenses/pandoc-types-BSD-3-Clause.txt','licenses/Aeson-BSD-3-Clause.txt','licenses/Scientific-BSD-3-Clause.txt','licenses/aeson-pretty-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/ghc-prim-BSD-3-Clause.txt']
sources+=['upstream/pandoc-types/'+f for f in subprocess.check_output(['git','-C',str(root/'upstream/pandoc-types'),'ls-files'],text=True).splitlines()]
for folder in ['tools/haskell-logging-reference','vendor/parsec-3.1.18.0','vendor/aeson-pretty-0.8.11','vendor/scientific-0.3.9.0','vendor/aeson-pretty-dependency-2.2.3.0','vendor/text-2.1.2','vendor/ghc-9.12.2','vendor/ghc-ast-order-9.12.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
docs=[d for d in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if d['accepted']]
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 assert m['constructors']==[n for n,_ in constructors] and len(constructors)==54
 assert set(c['request'][1][0] for c in cs if c.get('constructor'))==set(n for n,_ in constructors)
 assert [(c['path'],c['request'][2]) for c in cs if c.get('document')]==[(d['path'],d['json']) for d in docs]
 print(f'Verified {len(cs)} original Logging/ToJSON observations, all 54 constructors, {len(docs)} documents and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def call(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,req
 def normalize(x):
  if isinstance(x,dict):return {('encodedBase64' if k=='encoded' else k):(base64.b64encode(bytes(v)).decode() if k=='encoded' else normalize(v)) for k,v in x.items()}
  return x
 result=json.loads(line)
 return base64.b64encode(bytes(result)).decode() if req[0]=='array' else normalize(result)
def add(*req,**flags):
 cs.append(dict(request=req,expected=call(req),**flags))
 if len(cs)%5000==0:print('Captured',len(cs),'Logging observations',flush=True)
def points(s):return list(map(ord,s))
def tag(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
empty_attr=['',[],[]]
def pos(name='source',line=1,col=2):return [points(name) if isinstance(name,str) else name,line,col]
def base(t):return {
 'Text':points('contents'),'FilePath':points('path/name'),'SourcePos':pos(),
 '(Maybe SourcePos)':None,'Int':1,'Integer':'123456789012345678901234567890',
 '[Text]':[points('md'),points('txt')],'Inline':tag('Str','inline'),'Block':tag('Para',[tag('Str','block')])}[t]
messages=[[n,*[base(t) for t in ts]] for n,ts in constructors]
texts=[points(s) for s in ['', 'x', 'source','-', "a'\"\\\n\b\f",'\x0eH1','漢🙂','ß','.sty','file.sty','file.STY','\x00']]+[[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000]]
positions=[pos(n,l,c) for n,l,c in itertools.product(['','source','-','file','漢🙂'],[0,1,-1,2**63-1,-2**63],[0,1,-1,2**63-1,-2**63])]
positions += [pos(ps,1,2) for ps in texts]
variants={}
for (name,types),m in zip(constructors,messages):
 vs=[m]
 for p in [-2**63,-1,0,6,10,11,2**63-1]:add('message',m,p,constructor=True)
 for i,t in enumerate(types):
  if t in ['Text','FilePath']:values=texts
  elif t=='SourcePos':values=positions
  elif t=='(Maybe SourcePos)':values=[None,*positions]
  elif t=='Int':values=[-2**63,-10,-1,0,1,2,3,4,2**63-1]
  elif t=='Integer':values=[str(v) for v in [-10**200,-2**63,-1,0,1,2**63,10**200]]
  elif t=='[Text]':values=[[],[texts[0]],texts[:5],texts[::-1]]
  else:continue
  for v in values:
   alt=[*m];alt[i+1]=v;vs.append(alt);add('message',alt,11,field=True)
 variants[name]=vs
for a,b in itertools.product(messages,messages):add('relations',a,b,constructorOrder=True)
rng=random.Random(0x1066106)
for name,vs in variants.items():
 for v in vs:add('relations',v,v,self=True)
 for _ in range(35):add('relations',rng.choice(vs),rng.choice(vs),fields=True)
for ms in [[],messages,messages[::-1],messages*2,[variants['ScriptingInfo'][-1],variants['DuplicateAttribute'][-1]],messages[:1]]:add('array',ms)
# Every represented AST payload constructor/field value from the independent original Show corpus.
values=collections.defaultdict(dict)
with gzip.open(root/'core/src/test/resources/ast-show/data.jsonl.gz','rt') as f:
 next(f)
 for line in f:
  c=json.loads(line);a=c['request']
  if a[0]=='show' and a[1] in ['inline','block']:values[a[1]][json.dumps(a[3],sort_keys=True)]=a[3]
for kind,vs in values.items():
 for v in vs.values():add('message',[('InlineNotRendered' if kind=='inline' else 'BlockNotRendered'),v],0,ast=True)
for d in docs:add('ast','pandoc',d['json'],document=True,path=d['path'])
bits=[0,2**63,1,2**63+1,0x0010000000000000,0x7fefffffffffffff,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000000,0xfff8000000000001,0x7ff0000000000001,0x7ff8000000000007]
bits += [struct.unpack('>Q',struct.pack('>d',d))[0] for d in [-1.,1.,1e23,1e19,1e-7,0.1]]
for kind,a,b in itertools.product(['inline','block'],bits,bits):add('width',kind,str(a),str(b),ieee=True)
for _ in range(300):add('width',rng.choice(['inline','block']),str(rng.getrandbits(64)),str(rng.getrandbits(64)),ieee=True)
# Raw Haskell Char names differ from Text and survive position updates/derived Show.
for cp in [*range(256),*range(0xd800,0xe000),0x10000,0x10ffff]:
 a=pos([cp,49],-2**63,2**63-1);b=pos([rng.randrange(0x110000),49],1,2)
 add('position',a,b,[9,10,13,cp],7,-3,[cp],raw=True)
 m=['SkippedContent',[cp],a];add('rawShow',m,11,180,raw=True)
for a,b in itertools.product([[0xd800,0xdc00],[0x10000],[0xfffd,0xfffd]],repeat=2):add('position',pos(a),pos(b),[9],0,0,b,raw=True)
for v in ['ERROR','WARNING','INFO','error','Warning','',' ERROR ',0,None,True,[],{},'漢🙂']:
 add('verbosityJSON',v)
for word,prefix,suffix,prec in itertools.product(['ERROR','WARNING','INFO','error','ERRORx','WARN','NoLangSpecified'],['',' ','(',' ( ( ','{-x-}','--x\n'],['',' ',' ) ) ','x','\x00','--comment','{-x-}',';','\u00a0'],[-1,0,11,2**63-1]):
 add('verbosityRead',prec,points(prefix+word+suffix))
for cp in [*range(256),*range(0xd800,0xe000),*[rng.randrange(0x110000) for _ in range(1500)]]:
 add('verbosityRead',0,[*points('ERROR'),cp,49],readBoundary=True)
for n in [1,10,1000,10000]:add('verbosityRead',2**63-1,points('('*n+'INFO'+')'*n+' rest'),deepRead=True)
for op,i,j,k in itertools.product(['from','fromTo','fromThen','fromThenTo'],range(3),range(3),range(3)):add('enum',op,i,j,k,20)
for op,i in itertools.product(['toEnum','succ','pred'],[-2**63,-1,0,1,2,3,2**63-1]):add('enum',op,i,0,2,20)
add('enum','bounds',0,0,2,20)
for kind,n in itertools.product(['inline','block'],[0,1,10,1000,10000,100000]):add('deep',kind,n,180,deep=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['request'][0] for c in cs)),constructors=[n for n,_ in constructors],documents=len(docs),oracle='Unchanged Pandoc 3.12.1 Logging, pandoc-types 1.23.1.2, Parsec 3.1.18.0, GHC 9.12.2, Aeson 2.2.3.0, aeson-pretty 0.8.11')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'Logging observations; SHA',meta['caseHash'],flush=True)
