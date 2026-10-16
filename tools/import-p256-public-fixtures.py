#!/usr/bin/env python3
"""Independent original-module public P256 observations and complete original-suite evidence."""
import argparse,collections,gzip,hashlib,importlib.util,json,re,subprocess,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/haskell-p256-public-reference';TARGET=ROOT/'core/src/test/resources/crypto-p256-public/data.jsonl.gz'
N=int('ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551',16);P=int('ffffffff00000001000000000000000000000000ffffffffffffffffffffffff',16);HIGH=1<<256
GX=int('6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296',16);GY=int('4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5',16)

def inputs(gens):
 cs=[]
 def add(group,op,**r):cs.append(dict(group=group,request=dict(op=op,**r)))
 def pt(x,y):return [str(x),str(y)]
 def scalarbytes(x):return list(x.to_bytes(32,'big'))
 for op in ['scalarZero','scalarN','pointBase']:add('public-constants',op)
 values=[0,1,2,99,100,101,N-101,N-100,N-1,N,N+1,N+100,N+101,P-1,P,P+1,HIGH-101,HIGH-100,HIGH-1]
 for a in values:
  for op in ['scalar','scalarIsZero','scalarReduce','scalarInvSafe','scalarForce']:add('full-scalar-domain',op,a=str(a))
  if a != 0:add('nonzero-variable-inverse', 'scalarInv',a=str(a))
  for precedence in [-1,0,10,11,12,100]:add('RAM-secret-show-and-constructor-precedence','scalarShow',a=str(a),precedence=str(precedence))
  for b in values:
   for op in ['scalarAdd','scalarSub','scalarMul','scalarCmp','scalarEq']:add('scalar-arithmetic-and-byte-equality',op,a=str(a),b=str(b))
 for n in [0,1,15,31,32,33,63,64,65,255,256]:
  b=[i%256 for i in range(n)]
  for op in ['scalarFromBinary','unsafePointFromBinary','pointFromBinary','scalarGenerate']:add('exact-length-errors-and-random-single-request',op,bytes=b)
 for x in [0,N,HIGH-1]:add('RNG-no-zero-order-range-retry','scalarGenerate',bytes=scalarbytes(x))
 add('RNG-provider-exception-propagation','scalarGenerateThrow')
 for x in [-HIGH,-1,0,1,HIGH-1,HIGH,HIGH+1,1<<1024]:
  add('integer-boundaries','scalarFromInteger',a=str(x))
  for y in [-1,0,HIGH-1,HIGH]:add('point-integer-boundaries-and-error-order','pointFromIntegers',x=str(x),y=str(y))
 points=[pt(0,0),pt(GX,GY),pt(0,P),pt(P,0),pt(HIGH-1,HIGH-1),pt(N,1),pt(N+1,2),pt(0,1)]
 for point in points:
  for op in ['point','pointX','pointIsAtInfinity','pointIsValid','pointForce','pointNegate']:add('raw-point-policy-infinity-validity-and-x',op,p=point)
  for d in [-1,0,10,11,100]:add('RAM-Bytes-derived-point-Show','pointShow',p=point,precedence=str(d))
  for q in points:
   for op in ['pointAdd','pointEq']:add('point-add-infinity-and-byte-equality',op,p=point,q=q)
 for byte in range(256):
  data=[byte]*64
  add('every-byte-Point-Show-and-roundtrip','unsafePointFromBinary',bytes=data)
 for a in values:
  add('toPoint-zero-error-and-order-infinity','toPoint',a=str(a))
  for op in ['pointMul','pointDh']:add('point-multiply-and-DH-all-scalar-domains',op,a=str(a),p=pt(GX,GY))
 for a,b in [(0,0),(0,1),(1,0),(1,1),(N-1,1),(N,1),(N+1,N-1)]:add('double-scalar-raw-source-domain','pointsMulVarTime',a=str(a),b=str(b),p=pt(GX,GY))
 # Every original family is translated, with original-module generated inputs.
 for row in gens:
  a=row['a'];b=row['b'];qa=int(row['qa'])
  add('original-scalar-marshalling','scalarFromBinary',bytes=scalarbytes(qa))
  for family,op,other in [('add','scalarAdd',b),('add0','scalarAdd','0'),('sub','scalarSub',b),('sub-reverse','scalarSub',a),('sub0','scalarSub','0'),('mul','scalarMul',b),('inv','scalarInv',None),('inv-safe','scalarInvSafe',None),('inv-safe-mul','originalInvSafeMul',None)]:
   r=dict(a=b if family=='sub-reverse' else a)
   if other is not None:r['b']=other
   add('original-scalar-'+family,op,**r)
  for family,op in [('marshalling','originalMarshalPoint'),('marshalling-integer','pointFromIntegers'),('lift-to-curve','toPoint'),('point-add','originalPointAdd'),('point-add-infinity-identity','originalPointAddInfinity'),('point-add-inverse','originalPointAddInverse'),('point-negate','originalPointNegate'),('point-mul','originalPointMul')]:
   r=dict(a=a,b=b) if op not in ['pointFromIntegers','toPoint'] else dict(x=a,y=b) if op=='pointFromIntegers' else dict(a=a)
   add('original-point-'+family,op,**r)
 for a in [0,N]:add('original-scalar-inv-safe-zero','scalarInvSafe',a=str(a))
 for p,q in [(pt(0,0),pt(GX,GY)),(pt(GX,GY),pt(0,0)),(pt(0,0),pt(0,0))]:add('original-point-add-infinity','pointAdd',p=p,q=q)
 for a in [N,1]:add('original-point-infinity','toPoint',a=str(a))
 source=(ROOT/'vendor/crypton-2.0.1/tests/PubKey/P256Spec.hs').read_text()
 def literal(name):return int(re.search(r'^'+name+r' = (0x[0-9a-f]+)',source,re.M)[1],16)
 for x,y in [('xS','yS'),('xR','yR'),('xT','yT'),('xU','yU'),('xV','yV')]:add('original-valid-point-literal-units','pointIsValid',p=pt(literal(x),literal(y)))
 # Five source edge-case literals, extracted verbatim from the original table.
 block=source.split('validPointEdgeCases =',1)[1].split('spec ::',1)[0]
 for x,y in re.findall(r'\(\s*(0x[0-9a-f]+|0|1),\s*(0x[0-9a-f]+|0|1)\s*\)',block):add('original-valid-point-edge-units','pointIsValid',p=pt(int(x,0),int(y,0)))
 add('original-point-add-literal-unit','pointAdd',p=pt(literal('xS'),literal('yS')),q=pt(literal('xT'),literal('yT')))
 return cs

def hashes():
 files=[]
 for name,tarhash in [('crypton-2.0.1','9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'),('ram-0.22.1','72474962349dd101b970cc46bcc06c799f7311d228e7b59f1a44f81427159f70')]:
  d=ROOT/'vendor'/name;m=json.loads((d/'SOURCE.json').read_text());assert m['sha256']==tarhash
  for p,h in m['files'].items():assert hashlib.sha256((d/p).read_bytes()).hexdigest()==h,p
  if name.startswith('ram'):files += [p for p in d.rglob('*') if p.is_file()]
  else:files += [d/p for p in ['SOURCE.json','Crypto/PubKey/ECC/P256.hs','Crypto/Internal/ByteArray.hs','Crypto/Number/Serialize.hs','Crypto/Number/Serialize/Internal.hs','tests/PubKey/P256Spec.hs','tests/Imports.hs','tests/Utils.hs','crypton.cabal']]
 spec=importlib.util.spec_from_file_location('extract',ROOT/'tools/extract-p256-public-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex);assert (FOLDER/'OriginalP256Spec.hs').read_text()==ex.extract()
 notice=ROOT/'licenses/ram-BSD-3-Clause.txt';assert notice.read_bytes()==(ROOT/'vendor/ram-0.22.1/LICENSE').read_bytes()
 files += [notice,ROOT/'licenses/crypton-BSD-3-Clause.txt',Path(__file__).resolve(),ROOT/'tools/extract-p256-public-reference.py']+[p for p in FOLDER.glob('*') if p.is_file()]
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def query(oracle,cs):
 p=subprocess.run([oracle],input=''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cs),capture_output=True,text=True,check=True,timeout=240)
 result=[json.loads(l) for l in p.stdout.splitlines()];assert len(result)==len(cs)
 for c,o in zip(cs,result):
  if 'expected' in c:assert c['expected']==o,(c,o)
  else:c['expected']=o

def original(oracle):
 p=subprocess.run([oracle,'--original','--seed','1729','--qc-max-success','200'],capture_output=True,text=True,check=True,timeout=120)
 assert re.search(r'31 examples, 0 failures',p.stdout)
 counts=list(map(int,re.findall(r'passed (\d+) tests',p.stdout)));assert len(counts)==20 and sum(counts)==4000
 return dict(examples=31,failures=0,properties=20,propertyChecks=4000,seed=1729,maxSuccess=200)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();source=hashes()
 if a.check_source or a.verify_oracle:
  with gzip.open(TARGET,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
  assert m['sources']==source and m['cases']==len(cs) and m['caseHash']==digest(cs)
  assert [{k:v for k,v in c.items() if k!='expected'} for c in cs]==inputs(m['generators'])
  if a.verify_oracle:
   assert a.oracle;assert json.loads(subprocess.check_output([a.oracle,'--generators']))==m['generators'];assert original(a.oracle)==m['originalSuite'];query(a.oracle,cs)
  print('Verified',len(cs),'public observations,',len(source),'source hashes; replay =',a.verify_oracle)
 else:
  assert a.oracle
  gens=json.loads(subprocess.check_output([a.oracle,'--generators']));assert len(gens)==400
  summary=original(a.oracle);cs=inputs(gens);query(a.oracle,cs);m=dict(cases=len(cs),sources=source,caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),generators=gens,originalSuite=summary)
  with TARGET.open('wb') as raw:
   with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0) as f:f.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cs)+'\n').encode())
  print('Generated',len(cs),'public observations; SHA256',m['caseHash'])
