#!/usr/bin/env python3
"""Independent complete internal ECC wrapper, dispatch, lazy ladder and cache observations."""
import argparse,collections,gzip,hashlib,importlib.util,itertools,json,random,subprocess,sys,tempfile
from pathlib import Path
sys.dont_write_bytecode=True
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-ecc-wrapper-reference';target=root/'core/src/test/resources/crypto-ecc-wrapper/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
spec=importlib.util.spec_from_file_location('extraction',root/'tools/extract-ecc-wrapper-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
for name,s in ex.sources().items():assert (folder/name).read_text()==s,name
d=root/'vendor/crypton-2.0.1';pin=json.loads((d/'SOURCE.json').read_text());assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
assert set(pin['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for path,h in pin['files'].items():assert hashlib.sha256((d/path).read_bytes()).hexdigest()==h,path
notice=root/'licenses/crypton-BSD-3-Clause.txt';assert notice.read_bytes()==(d/'LICENSE').read_bytes()
old=root/'tools/haskell-ecc-prime-reference/OriginalEccTests.hs'
oldex=root/'tools/extract-ecc-prime-reference.py';sp=importlib.util.spec_from_file_location('old_extraction',oldex);oe=importlib.util.module_from_spec(sp);sp.loader.exec_module(oe);assert old.read_text()==oe.tests()
sources=[Path(__file__).resolve(),root/'tools/extract-ecc-wrapper-reference.py',notice,old,oldex]+[f for f in folder.rglob('*') if f.is_file()]+[f for f in d.rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if args.check_source or args.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(l) for l in f]
 assert manifest['sources']==hashes()
 assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases) and manifest['groups']==groups(cases)
 if args.check_source:print('Verified',len(cases),'ECC wrapper/cache observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert args.oracle
 curves=[json.loads(l) for l in subprocess.run([args.oracle,'--curves'],check=True,capture_output=True,text=True,timeout=120).stdout.splitlines()];assert len(curves)==33
 cases=[];rng=random.Random(0xECCCACE2026)
 def add(group,r):cases.append(dict(group=group,request=r))
 def request(c,k,op='curve',other=False,**kw):
  r={key:v for key,v in c.items() if key not in ['ox','oy']};r.update(op=op,k=str(k),width=str(max(int(c['width']),(k.bit_length()+7)//8)),base=not other)
  if other:r.update(x=c['ox'],y=c['oy'])
  if op=='binaryH':r['width']=str(int(r['width'])*8)
  r.update(kw);return r
 for c in curves:
  n=int(c['order']);methods=['prime','table','curve','curve'] if c['field']=='prime' else ['binaryC','binaryH','curve']
  for k,other in itertools.product([0,1,2,3,15,16,17,n-1,n,n+1,2*n+3,n*n,n*n*n],[False,True]):
   for i,op in enumerate(methods):add('all-thirty-three-curves-base-nonbase-wide-scalars-and-dispatch',request(c,k,op,other,mathCheck=True,**({'base':False} if i==3 else {})))
 selected=[next(c for c in curves if c['curve']==name) for name in ['SEC_p256k1','SEC_p384r1','SEC_t113r1','SEC_t163k1']]
 for c,k in itertools.product(selected,range(256)):
  for op in (['prime','table'] if c['field']=='prime' else ['binaryC','binaryH']):
   add('all-byte-scalars-and-explicit-public-widths',request(c,k,op,width='8' if op=='binaryH' else '1',mathCheck=True))
 for c,k,poison,op in itertools.product(selected,[0,1,17],['0','17','165','255'],['curve']):add('guarded-shared-layout-scalar-clearing-and-output-status',request(c,k,op,poison=poison,mathCheck=True))
 for c,k,status,op in itertools.product(selected,[0,1,-1],['-7','-1','0','1','2'],['curve']):add('controlled-foreign-status-and-scalar-wipe-policy',request(c,k,op,status=status))
 for c in selected:
  if c['field']=='prime':
   for k,status in itertools.product([0,1,-1],['-1','0','1']):add('controlled-table-build-and-multiply-status',request(c,k,'table',status=status))
  else:
   for x in ['0','-1']:add('binary-zero-x-and-negative-input-routing',request(c,3,x=x,base='bottom:base'))
 toy=dict(p='17',a='2',b='2',width='1',k='1',x='5',y='1',fx='19',order='19',field='prime',base=False)
 for op in ['prime','table','tableMulBottom','binaryC','binaryH','curve','lookup']:
  for change in [{},{'p':'0'},{'p':'2'},{'width':'0'},{'k':'-1'},{'x':'0'},{'fx':'0'},{'field':'bottom'}]:
   for key in ['p','a','b','width','k','x','y','fx','order','base']:add('early-guards-deferred-fields-and-entry-order',toy|change|dict(op=op,**{key:'bottom:'+key}))
  for keys in [('p','width'),('width','k'),('p','k'),('order','k'),('fx','width'),('p','a','b'),('a','b')]:add('multiple-deferred-errors-and-precedence',toy|dict(op=op)|{key:'bottom:'+key for key in keys})
  add('ignored-result-never-enters-arguments',dict(op=op,part='ignore',**{key:'bottom:'+key for key in ['p','a','b','width','k','x','y','fx','order','base']}))
 for op,poison,key,value in itertools.product(['prime','table','binaryC'],['0','165','255'],['x','y','a','b','k'],['-1','256','65536']):add('controlled-prefill-after-ignored-serialization-failures',toy|dict(op=op,poison=poison,**{key:value}))
 for fx,b,x,y,k,bits in itertools.product(['-1','0','1','3','7','19','511','1023'],['-1','0','1','bottom:b'],['-1','0','1','bottom:x'],['0','1','bottom:y'],['-1','0','1','3'],['0','1','2','3','7','8']):
  add('integer-ladder-guards-lazy-coordinates-and-inverse-failures',dict(op='binaryH',fx=fx,b=b,x=x,y=y,k=k,width=bits))
 for fx,k in itertools.product(['511','1023','65535','131071'],[0,1,2,3,17]):add('native-polynomial-rejection-and-integer-fallback',dict(op='curve',field='binary',fx=fx,b='1',x='1',y='1',order='257',k=str(k),base='bottom:base'))
 for bits,k in itertools.product([1,2,3,7,8,9,16],[0,1,3,255,256,65535]):add('raw-integer-ladder-walks-exactly-requested-low-bits',dict(op='binaryH',fx='19',b='1',x='1',y='1',width=str(bits),k=str(k)))
 for bits in [512,2048,8192]:
  for k in [0,1,(1<<bits)-1]:add('finite-wide-integer-demand-graph-without-host-stack-recursion',dict(op='binaryH',fx='19',b='1',x='1',y='1',width=str(bits),k=str(k)))
 for width in ['-2147483648','-1','0','2147483647']:
  add('bounded-table-size-guard-without-large-allocation',toy|dict(op='table',p='0',width=width))
 for order,k,field in itertools.product(['-257','-1','0','1','256'],['-1','0','1','256'],['prime','binary']):
  add('dispatch-zero-signed-order-and-maximum-public-width',toy|dict(op='curve',order=order,k=k,field=field))
 for c in curves:
  if c['field']!='prime':continue
  n=int(c['order']);key=request(c,1,'lookup')
  steps=[key|dict(part='constructor'),key|dict(part='constructor'),key,request(c,1),request(c,n),request(c,n*n),key]
  add('fresh-permanent-lazy-table-identity-and-native-routing',dict(op='cacheScenario',steps=steps))
  add('failed-table-construction-is-permanently-memoized',dict(op='cacheScenario',steps=[key|dict(status='-1',part='constructor'),key|dict(part='constructor'),request(c,1),key]))
  for keyname in ['p','a','b','width','x','y']:
   changed=key|{keyname:'-1'}
   add('all-six-cache-key-components-and-no-foreign-work-on-miss',dict(op='cacheScenario',steps=[changed,key,key]))
 for c in selected[:2]:
  key=request(c,1,'lookup');n=(int(c['p']).bit_length()+63)//64
  add('borrowed-permanent-table-mutation-is-shared',dict(op='cacheScenario',steps=[key,key|dict(op='mutate',index=str(3*n*8),xor='1'),key,request(c,1),key]))
  for threads in ['2','32']:add('concurrent-first-use-publishes-one-shared-table',key|dict(op='cacheConcurrent',threads=threads))
  for miss in ['p','a','b','width','x']:
   keys=['p','a','b','width','x','y'];r=key.copy();r[miss]='-1'
   for k in keys[keys.index(miss)+1:]:r[k]='bottom:'+k
   add('cache-key-comparison-does-not-demand-mismatched-tail',dict(op='cacheScenario',steps=[r]))
assert args.oracle
normal=[i for i,c in enumerate(cases) if c['request']['op'] not in ['cacheScenario','cacheConcurrent']]
results=[None]*len(cases)
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(cases[i]['request'],separators=(',',':'))+'\n' for i in normal).encode());inp.seek(0)
 subprocess.run([args.oracle,'--warm'],stdin=inp,stdout=out,check=True,timeout=600);out.seek(0);values=[json.loads(l) for l in out]
assert len(values)==len(normal)
for i,v in zip(normal,values):results[i]=v
for i,c in enumerate(cases):
 if results[i] is None:
  run=subprocess.run([args.oracle],input=json.dumps(c['request'])+'\n',capture_output=True,text=True,check=True,timeout=120)
  values=[json.loads(l) for l in run.stdout.splitlines()];assert len(values)==1,(i,run.stdout);results[i]=values[0]
for c,o in zip(cases,results):
 if c['request'].get('mathCheck'):assert o['observed']['value']==o['math'],(c,o)
if args.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,results)):assert c['expected']==o,(i,c,o)
 print('Original GHC verified all',len(cases),'ECC wrapper/cache observations');raise SystemExit
for c,o in zip(cases,results):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'ECC wrapper observations; SHA256',manifest['caseHash'])
