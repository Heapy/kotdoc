#!/usr/bin/env python3
"""Raw output from unchanged aeson-pretty/Scientific implementations; no Kotlin-derived expectations."""
import argparse,atexit,base64,collections,gzip,hashlib,itertools,json,os,random,struct,subprocess,sys
from decimal import Decimal
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sys.set_int_max_str_digits(0)
target=root/'core/src/test/resources/pretty-json/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_PRETTY_JSON_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-pretty-json-fixtures.py','core/src/test/resources/native/upstream.json','licenses/Scientific-BSD-3-Clause.txt','licenses/aeson-pretty-BSD-3-Clause.txt','licenses/Aeson-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt']
for folder in ['tools/haskell-pretty-json-reference','vendor/aeson-pretty-0.8.11','vendor/scientific-0.3.9.0','vendor/aeson-pretty-dependency-2.2.3.0','vendor/ghc-pretty-sort-9.12.2','vendor/text-2.1.2','vendor/ghc-9.12.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
def normalized(expected):
 if isinstance(expected,dict) and 'bytes' in expected:
  expected=dict(expected);expected['bytesBase64']=base64.b64encode(bytes(expected.pop('bytes'))).decode()
 return expected
def raw_cases(cs):
 for c in cs:
  e=c['expected']
  if isinstance(e,dict) and 'bytesBase64' in e:
   e=dict(e);e['bytes']=list(base64.b64decode(e.pop('bytesBase64')))
  yield dict(c,expected=e)
def n(c,e=0):return ['n',str(c),e]
def points(s):return list(map(ord,s))
def txt(s):return ['s',points(s)]
def transport(x):
 if x is None:return ['z']
 if isinstance(x,bool):return ['b',x]
 if isinstance(x,str):return txt(x)
 if isinstance(x,list):return ['a',[transport(v) for v in x]]
 if isinstance(x,dict):return ['o',[[points(k),transport(v)] for k,v in x.items()]]
 d=Decimal(x);t=d.as_tuple();c=int(''.join(map(str,t.digits)));return n(-c if t.sign else c,t.exponent)
original=(root/'vendor/scientific-0.3.9.0/test/test.hs').read_text()
projection=(root/'tools/haskell-pretty-json-reference/OriginalGenerator.hs').read_text()
assert original[original.index('instance QC.Arbitrary Scientific where'):original.index('nonNegativeScientificGen ::')] in projection
assert original[original.index('bigIntGen ::'):] in projection
docs=[d for d in json.loads((root/'core/src/test/resources/native/upstream.json').read_text(),parse_float=Decimal) if d['accepted']]
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['nativeCaseHash']==digest(raw_cases(cs))
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 assert [(c['path'],c['request'][2]) for c in cs if c.get('document')]==[(d['path'],transport(d['json'])) for d in docs for _ in range(3)]
 assert sum(c.get('originalGenerator',False) for c in cs)==2000
 assert [c['request'][3] for c in cs if c.get('originalUnit')]==[n(c) for c in [1,0,-1]]
 assert any(c.get('originalDigits') and c['request']==['digits',['n','9'*1000000,0]] and c['expected']==dict(digits='9'*1000000,exponent=1000000) for c in cs)
 print(f'Verified {len(cs)} raw pretty/Scientific observations, {len(docs)} documents, {len(sources)} hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def call(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,req
 result=json.loads(line)
 return normalized(result)
def add(*req,**extra):
 cs.append(dict(request=req,expected=call(req),**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'pretty/Scientific observations',flush=True)
def conf(indent=4,cmp='equal',keys=(),nf=('Generic',),trail=False):return [indent,cmp,[points(k) for k in keys],list(nf),trail]
rng=random.Random(0xAE50811)
# Every formatter mode, precision branch, sign, zero, trailing-zero normalization and ties.
for c,e,fmt,d in itertools.product([0,1,-1,2,-2,5,15,25,35,45,55,65,75,85,95,99,999,100,101,1001,-999,1234500],[ -30,-8,-7,-2,-1,0,1,6,7,8,19,20,30],['Generic','Exponent','Fixed'],[None,-3,0,1,2,7,20]):
 add('format',fmt,d,n(c,e))
for _ in range(3000):add('format',rng.choice(['Generic','Exponent','Fixed']),rng.choice([None,-1,0,1,2,8,21]),n(rng.randint(-10**60,10**60),rng.randrange(-80,81)))
# Int64 arithmetic extremes with bounded actual results (never replace exponents with Int32).
for e in [-2**63,-2**63+1,2**63-2,2**63-1]:
 for c in [0,1,-1,10,-10,1001,99999]:
  add('format','Exponent',None,n(c,e));add('format','Exponent',2,n(c,e));add('digits',n(c,e))
  # Native strict Text.replicate cannot allocate near-Int64 zero runs. Test its
  # defined streaming branches and the original String Exponent-zero branch.
  options=[('Exponent',None),('Exponent',0),('Exponent',2),('Fixed',0),('Fixed',2)]
  if c!=0:options.append(('Exponent',2**63-1))
  else:options.append(('Fixed',2**63-1))
  for (fmt,d),count in itertools.product(options,[0,1,2,10,4097]):add('prefix',fmt,d,n(c,e),count)
  if c==0:
   for count in [0,1,2,10,4097]:add('stringPrefix','Exponent',2**63-1,n(c,e),count)
  if c==0 or e==-2**63 or (e==2**63-1 and abs(c)>=10):add('format','Fixed',0,n(c,e))
for c in call(['samples',0xAE50811,1000]):cs.append(dict(c,originalGenerator=True))
# Original formatter unit values; full Maybe Show remains a separate Scientific contract.
for c in [1,0,-1]:add('format','Generic',None,n(c),originalUnit=True)
for c,e in [(int('9'*1000000),0),(int('1'+'0'*10000),-20000)]:add('digits',n(c,e),originalDigits=True)
# Pretty number threshold is strictly >1e19, and stored scale affects Aeson fallback.
for c,e,nf in itertools.product([0,1,-1,2,-2,10,-10,9999999999999999999,10000000000000000000,10000000000000000001,-10000000000000000001],[ -22,-8,-7,-1,0,1,18,19,20],[("Generic",),("Scientific",),("Decimal",),("fields",),("format","Fixed",2)]):
 add('pretty',conf(nf=nf),n(c,e),number=True)
bits=[0,2**63,1,2**63+1,0x0010000000000000,0x7fefffffffffffff,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000000,0x7ff8000000000001]
bits += [struct.unpack('>Q',struct.pack('>d',x))[0] for x in [1.,-1.,1e23,1e19,1e-7,1e-6,0.1,0.3,2.5]]
for b in bits+[rng.getrandbits(64) for _ in range(3000)]:add('double',str(b))
for cp in [*range(256),*range(0xd800,0xe000),0x2028,0x2029,0xffff,0x10000,0x10ffff,*[rng.randrange(0x110000) for _ in range(1000)]]:
 v=['s',[cp,34,92,8,12,10,13,9,47,49]]
 add('pretty',conf(indent=0),v,unicode=True)
keys=['foo','bar','baz','quux','z','aa','bbb','cccc','d','ee','fff','gggg','h','ii','漢','🙂','\ue000','\U00010000']
comparators=['equal','natural','reverse','length','keyOrder','keyThenNatural','alwaysGT','alwaysLT','cycle']
for cmp in comparators:
 for length in range(0,71):
  ks=rng.choices(keys,k=length)
  add('sort',cmp,[points(k) for k in ['foo','bar','foo']],[points(k) for k in ks],sorting=True)
  obj=['o',[[points(k),n(i,-1)] for i,k in enumerate(ks)]]
  add('pretty',conf(indent=rng.choice([0,-1,1,4,'Tab']),cmp=cmp,keys=['foo','bar','foo']),obj,sorting=True)
values=[['z'],['b',True],['b',False],txt(''),txt('\b\f\0漢🙂'),['a',[]],['o',[]],['a',[n(1),txt('x'),['a',[n(-12345,-3)]]]],['o',[[points('z'),['a',[n(99,-1)]]],[points('a'),txt('hello')]]]]
for indent,trail,nf,v in itertools.product([0,-3,1,2,4,'Tab'],[False,True],[('Generic',),('Scientific',),('Decimal',),('fields',),('format','Exponent',3),('format','Fixed',0)],values):
 add('pretty',conf(indent=indent,nf=nf,trail=trail),v,config=True)
for doc in docs:
 for config in [conf(),conf(indent=0),conf(indent=2,cmp='keyOrder',keys=['type','verbosity','contents','message','path','source','line','column'])]:
  add('pretty',config,transport(doc['json']),document=True,path=doc['path'])
for depth in [0,1,10,1000,10000,100000]:add('deep',depth,deep=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),nativeCaseHash=digest(raw_cases(cs)),operations=dict(collections.Counter(c['request'][0] for c in cs)),documents=len(docs),oracle='Original aeson-pretty 0.8.11, Scientific 0.3.9.0, Aeson 2.2.3.0 +ordered-keymap, GHC 9.12.2')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} observations; SHA {meta["caseHash"]}',flush=True)
