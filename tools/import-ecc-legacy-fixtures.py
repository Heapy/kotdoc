#!/usr/bin/env python3
"""Independent pinned legacy ECC Types/Prim/DH and complete original ECCSpec."""
import argparse,collections,gzip,hashlib,importlib.util,json,re,subprocess,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/haskell-ecc-legacy-reference';TARGET=ROOT/'core/src/test/resources/crypto-ecc-legacy/data.jsonl.gz'
def inputs(inv,curves):
 cs=[]
 def add(group,op,name,**r):cs.append(dict(group=group,request=dict(op=op,curve=name,**r)))
 for name,c in curves.items():
  g=c['g'];n=int(c['n']);f=int(c['modulus']);bits=c['bits'];zero=['0','0']
  add('all33-source-curves','curve',name)
  for a in [-3,0,1,2,3,n-1,n,n+1,1<<600]:
   for op in ['scalarAdd','scalarMul']:add('scalar-dispatch-bounds',op,name,a=str(a),b=str(n+1))
   add('scalar-dispatch-bounds','scalarInverse',name,a=str(a))
   for op in ['pointBaseMul','calculatePublic']:add('scalar-dispatch-bounds',op,name,a=str(a))
  for p in [None,g,zero,['1','1'],['-1','0'],[str(f),'0']]:
   for op in ['isPointValid','isPointInSubgroup','isPointAtInfinity','pointNegate','pointDouble','pointDecompose']:
    if name.startswith('SEC_t') and p==['-1','0'] and op in ['pointDouble','pointDecompose']:continue # source gcdF2m negative-polynomial domain is undefined/non-returning
    add('raw-points-policy',op,name,p=p)
   if not(name.startswith('SEC_t') and p==['-1','0']):add('point-add-all-raw-domains','pointAdd',name,p=p,q=g)
   add('point-add-infinity-identity','pointAdd',name,p=p,q=None)
   for a in [0,1,2,5,-5]:add('raw-point-fallback','pointMul',name,a=str(a),p=p)
   for a in [0,1]:
    for op in ['tryGetShared','getShared']:add('DH-validation-order',op,name,a=str(a),p=p)
  for a,b in [(0,0),(1,0),(0,1),(1,1)]:
   for parity in [False,True]:add('compose-range-parity','pointCompose',name,a=str(a),b=str(b),parity=parity)
  for op in ['generatePrivate','scalarGenerate']:
   ran=n if op=='generatePrivate' else n-1;rbits=ran.bit_length();over=rbits>3 and not(ran>>(rbits-2)&1) and not(ran>>(rbits-3)&1);width=(rbits+int(over)+7)//8
   for payload in [[0]*width,[1]*width,[255]*width]:add('RNG-rejection-and-ranges',op,name,payloads=[payload]*100)
   add('RNG-rejection-then-accept',op,name,payloads=[[255]*width,[0]*width])
 for v in inv['vectors']:add('original-valid-point-vectors','isPointValid',v['curve'],p=v['point'])
 private=[2,3,100,101,3141592653589793238,3141592653589793239]
 for row in inv['subgroups']:
  name=row['curve'];q=row['point'];n=curves[name]['n'];g=curves[name]['g']
  add('original-subgroup-oncurve','isPointValid',name,p=q);add('original-subgroup-not-generated','pointMul',name,a=n,p=q)
  for d in private:add('original-subgroup-six-private-rejections','tryGetShared',name,a=str(d),p=q)
  # Original 7G controls are filled independently by a preliminary original query.
  for d in private:add('original-subgroup-six-valid7G-controls','tryGetShared',name,a=str(d),p=curves[name]['seven'])
 families=['point-add','point-negate-add','point-negate-negate','point-mul-mul','point-mul-matches-double-and-add','scalar-arithmetic','double-scalar-mult']
 for row in inv['generators']:
  for family in families:add('original-property-'+family,'originalProperty',row['curve'],family=family,**{k:row[k] for k in ['a','b','p','q']})
 # All source P256 fast-path scalar edges and double-multiplication shapes.
 name='SEC_p256r1';c=curves[name];n=int(c['n']);g=c['g'];q=c['p256Q']
 for a in [n,n+1,-1,-7,3*n+11,n-1,(1<<256)-1,1<<256,2*n,2*n+3,0]:add('original-P256-fastpath','pointMul',name,a=str(a),p=g)
 for a,b in [(0,0),(0,7),(1,n-1),(n-1,n-1),(n,n),(n+1,2),((1<<256)-1,(1<<256)-1),(1<<256,3),((1<<300)+5,(1<<256)+9),(-3,5),(3,-5)]:
  for op in ['scalarAdd','scalarMul']:add('original-P256-arithmetic',''+op,name,a=str(a),b=str(b))
 for p,r,a,b in [(g,q,3,5),(q,g,5,3),(g,q,n,5)]:add('original-P256-two-muls','pointAddTwoMuls',name,a=str(a),b=str(b),p=p,q=r)
 for name in ['SEC_p384r1','SEC_p521r1']:
  c=curves[name];n=int(c['n']);bits=n.bit_length();ones=(1<<(bits-2))-1;alt=sum(1<<i for i in range(0,bits-1,2));g=c['g']
  for k in [1,2,3,15,16,17,n-1,n,n+1]:add('original-weight-other-point','pointMul',name,a=str(k),p=c['five'])
  for a,b in [(1,1),(1<<(bits-2),1),(ones,1),(alt,3),(ones,alt),(n-1,n-1),(n*n,3),(n*n*n,alt)]:add('original-weight-addition','originalProperty',name,family='weight-add',a=str(a),b=str(b),p=g,q=g)
 for name in ['SEC_t113r1','SEC_t163k1','SEC_t233r1','SEC_t283k1']:
  c=curves[name];n=int(c['n']);two=next(r['point'] for r in inv['subgroups'] if r['curve']==name)
  for k in [1,2,3,15,16,17,n-1,n,n+1,2*n+3,n*n]:add('original-binary-edges','pointMul',name,a=str(k),p=c['g'])
  for k in [1,2,3,2*n,15,16,17]:add('original-binary-order-two','pointMul',name,a=str(k),p=two)
 return cs
def hashes():
 source=(ROOT/'vendor/crypton-2.0.1/Crypto/PubKey/ECC/Types.hs').read_text()
 blocks=re.findall(r'getCurveByName (SEC_\w+) =(.+?)(?=getCurveByName SEC_|\Z)',source,re.S)
 kotlin=(ROOT/'core/src/main/kotlin/io/heapy/kotdoc/x509/X509NamedCurveParameters.kt').read_text()
 for name,block in blocks:
  nums=[int(v,16) for v in re.findall(r'0x([0-9a-f]+)',block)]+[int(re.search(r'ecc_h = (\d+)',block)[1])]
  row=next(line for line in kotlin.splitlines() if line.endswith('// '+name))
  assert nums==list(map(int,re.findall(r'n\("(\d+)"\)',row))),name
 assert len(blocks)==33
 oids=re.findall(r'\(SEC_(\w+), \[([\d,]+)\]\)',source)
 table=(ROOT/'core/src/main/kotlin/io/heapy/kotdoc/x509/X509PublicKeys.kt').read_text()
 for name,oid in oids:
  actual=re.search(r'X509CurveName.SEC_'+name+r' to x509Oid\(([^)]+)\)',table)[1]
  assert list(map(int,actual.split(',')))==list(map(int,oid.split(','))),name
 assert len(oids)==33
 files=[]
 for name,tarhash in [('crypton-2.0.1','9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'),('ram-0.22.1','72474962349dd101b970cc46bcc06c799f7311d228e7b59f1a44f81427159f70')]:
  d=ROOT/'vendor'/name;m=json.loads((d/'SOURCE.json').read_text());assert m['sha256']==tarhash
  for p,h in m['files'].items():assert hashlib.sha256((d/p).read_bytes()).hexdigest()==h,p
  if name.startswith('ram'):files += [p for p in d.rglob('*') if p.is_file()]
  else:files += [d/p for p in ['SOURCE.json','Crypto/PubKey/ECC/Types.hs','Crypto/PubKey/ECC/Prim.hs','Crypto/PubKey/ECC/DH.hs','Crypto/Internal/ECC.hs','Crypto/Number/Generate.hs','Crypto/PubKey/ECC/P256.hs','Crypto/Internal/ByteArray.hs','Crypto/Number/Serialize.hs','Crypto/Number/Serialize/Internal.hs','tests/PubKey/ECCSpec.hs','tests/Imports.hs','tests/Utils.hs','crypton.cabal']]
 spec=importlib.util.spec_from_file_location('extract',ROOT/'tools/extract-ecc-legacy-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex);assert (FOLDER/'OriginalECCSpec.hs').read_text()==ex.extract()
 notice=ROOT/'licenses/ram-BSD-3-Clause.txt';assert notice.read_bytes()==(ROOT/'vendor/ram-0.22.1/LICENSE').read_bytes()
 files += [notice,ROOT/'licenses/crypton-BSD-3-Clause.txt',Path(__file__).resolve(),ROOT/'tools/extract-ecc-legacy-reference.py']+[p for p in FOLDER.glob('*') if p.is_file()]
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def query(oracle,cs):
 p=subprocess.run([oracle],input=''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cs),capture_output=True,text=True,check=True,timeout=240)
 result=[json.loads(l) for l in p.stdout.splitlines()];assert len(result)==len(cs)
 for c,o in zip(cs,result):
  if 'expected' in c:assert c['expected']==o,(c,o)
  else:c['expected']=o

def original(oracle):
 p=subprocess.run([oracle,'--original','--seed','1729','--qc-max-success','20'],capture_output=True,text=True,check=True,timeout=120)
 match=re.search(r'(\d+) examples, 0 failures',p.stdout);assert match;examples=int(match[1])
 counts=list(map(int,re.findall(r'passed (\d+) tests',p.stdout)));assert len(counts)==7 and sum(counts)==140
 return dict(examples=examples,failures=0,properties=7,propertyChecks=140,seed=1729,maxSuccess=20)
def capture_inventory(oracle):
 inv=json.loads(subprocess.check_output([oracle,'--inventory'],timeout=240))
 inv['oids']={name:list(map(int,oid.split(','))) for name,oid in re.findall(r'\((SEC_\w+), \[([\d,]+)\]\)',(ROOT/'vendor/crypton-2.0.1/Crypto/PubKey/ECC/Types.hs').read_text())}
 assert len(inv['curves'])==33 and len(inv['subgroups'])==20 and len(inv['vectors'])==12 and len(inv['generators'])==140
 cs=[dict(request=dict(op='curve',curve=n)) for n in inv['curves']];query(oracle,cs)
 curves={n:c['expected'] for n,c in zip(inv['curves'],cs)}
 for k,label in [(5,'five'),(7,'seven')]:
  cs=[dict(request=dict(op='pointBaseMul',curve=n,a=str(k))) for n in inv['curves']];query(oracle,cs)
  for n,c in zip(inv['curves'],cs):curves[n][label]=c['expected']
 cs=[dict(request=dict(op='pointBaseMul',curve='SEC_p256r1',a=str(0x2a3f1c9e)))];query(oracle,cs);curves['SEC_p256r1']['p256Q']=cs[0]['expected']
 return inv,curves
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();source=hashes()
 if a.check_source or a.verify_oracle:
  with gzip.open(TARGET,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
  assert m['sources']==source and m['cases']==len(cs) and m['caseHash']==digest(cs)
  assert [{k:v for k,v in c.items() if k!='expected'} for c in cs]==inputs(m['inventory'],m['curves'])
  if a.verify_oracle:
   assert a.oracle;assert capture_inventory(a.oracle)==(m['inventory'],m['curves']);assert original(a.oracle)==m['originalSuite'];query(a.oracle,cs)
  print('Verified',len(cs),'legacy ECC observations,',len(source),'source hashes; replay =',a.verify_oracle)
 else:
  assert a.oracle
  inv,curves=capture_inventory(a.oracle);summary=original(a.oracle);cs=inputs(inv,curves);query(a.oracle,cs)
  m=dict(excludedDomains=[dict(curve=n,point=['-1','0'],operations=['pointDouble','pointDecompose','pointAdd with base point'],reason='Original F2m negative-polynomial domain is undefined; pointDouble probe did not complete in a3second diagnostic. No finite expected result or all-Integer parity claimed.') for n in inv['curves'] if n.startswith('SEC_t')],cases=len(cs),sources=source,caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),inventory=inv,curves=curves,originalSuite=summary)
  TARGET.parent.mkdir(parents=True,exist_ok=True)
  with TARGET.open('wb') as raw:
   with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0) as f:f.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cs)+'\n').encode())
  print('Generated',len(cs),'legacy ECC observations; SHA256',m['caseHash'])
