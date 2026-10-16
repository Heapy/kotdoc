#!/usr/bin/env python3
"""Simple 33 curves and explicit modern prime partition; independent original observations."""
import argparse,collections,gzip,hashlib,importlib.util,json,re,subprocess,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/haskell-ecc-modern-reference';TARGET=ROOT/'core/src/test/resources/crypto-ecc-modern-prime/data.jsonl.gz'
def inputs(inv,types):
 cs=[]
 def add(group,layer,name,op,**r):cs.append(dict(group=group,request=dict(layer=layer,curve=name,op=op,**r)))
 def simple(group,name,op,**r):add(group,'simple',name,op,**r)
 def modern(group,name,op,**r):add(group,'modern',name,op,**r)
 for name,c in types.items():
  n=int(c['n']);f=int(c['modulus']);g=c['g'];width=(n.bit_length()+7)//8
  simple('all33-types',name,'type')
  for a in [-1,0,1,n-1,n,n+1,f-1,f,f+1,1<<600]:simple('Simple-field-scalar-bounds',name,'scalarFromInteger',a=str(a))
  for a in [-1,0,1]:simple('Simple-Scalar-Show',name,'scalarShow',a=str(a),precedence='11')
  points=[None,g,['0','0'],['1','1'],[str(f),'0']]
  for p in points:
   for op in ['pointShow','pointNegate','pointDouble','isPointAtInfinity','isPointInSubgroup']:simple('Simple-raw-points',name,op,p=p)
   if p is not None:
    for op in ['pointFromIntegers','isPointValid']:simple('Simple-validation',name,op,a=p[0],b=p[1])
   for q in [None,g]:simple('Simple-pointAdd',name,'pointAdd',p=p,q=q)
   for a in [-5,0,1,2,5,n,n+1]:simple('Simple-pointMul',name,'pointMul',a=str(a),p=p)
  for a in [-3,0,1,n,n+1,1<<600]:simple('Simple-baseMul',name,'pointBaseMul',a=str(a))
  for a,b in [(0,1),(1,0),(3,5),(n,1),(-5,3)]:simple('Simple-two-muls',name,'pointAddTwoMuls',a=str(a),b=str(b),p=g,q=g)
  ran=n-1;bits=ran.bit_length();over=bits>3 and not(ran>>(bits-2)&1) and not(ran>>(bits-3)&1);rw=(bits+int(over)+7)//8
  for payloads in [[[0]*rw],[[255]*rw,[0]*rw],[[255]*rw]*100]:simple('Simple-RNG',name,'scalarGenerate',payloads=payloads)
 for row in inv['subgroups']:
  p=row['p'];simple('Simple-all20-subgroup-rejections',row['curve'],'pointFromIntegers',a=p[0],b=p[1])
  simple('Simple-all20-direct-subgroup',row['curve'],'isPointInSubgroup',p=p)
 for name,sec in [('P256','SEC_p256r1'),('P384','SEC_p384r1'),('P521','SEC_p521r1')]:
  c=types[sec];n=int(c['n']);g=c['g'];size=(c['bits']+7)//8;orderwidth=(n.bit_length()+7)//8;limit=1<<(orderwidth*8)
  modern('modern-sizes',name,'sizes')
  values=[-limit,-limit+1,-1,0,1,2,n-1,n,n+1,limit-1,limit]
  for a in values:
   for op in ['scalarFromInteger','encodeScalar','scalarToPoint','pointBaseSmul']:modern('modern-scalar-policies',name,op,a=str(a))
   for op in ['pointSmul','ecdh','ecdhRaw']:modern('modern-DH-scalar-policies',name,op,a=str(a),p=g)
  for p in [None,g,['0','0'],['1','1']]:
   for op in ['encodePoint','pointNegate']:modern('modern-raw-points',name,op,p=p)
   for q in [None,g]:modern('modern-pointAdd',name,'pointAdd',p=p,q=q)
   for op in ['ecdh','ecdhRaw']:modern('modern-raw-DH',name,op,a='1',p=p)
  for a,b in [(0,0),(1,n-1),(n,n),(n+1,2),(limit-1,limit-1),(-3,5)]:
   for op in ['scalarAdd','scalarMul']:modern('modern-scalar-arithmetic',name,op,a=str(a),b=str(b))
   modern('modern-two-muls',name,'pointsSmulVarTime',a=str(a),b=str(b),p=g)
  for length in [0,1,orderwidth-1,orderwidth,orderwidth+1]:modern('modern-scalar-width-only',name,'decodeScalar',bytes=[255]*length)
  # Tag error ordering, arbitrary payload split widths, zero padding and odd lengths.
  gx,gy=map(int,g);xb=gx.to_bytes(size,'big');yb=gy.to_bytes(size,'big')
  payloads=[b'',b'\0',xb+yb,xb[1:]+yb[1:],b'\0'+xb+b'\0'+yb,b'\0'+xb+yb,xb+b'\0'+yb,bytes(size*2),bytes(size*2+1)]
  for tag in [0,2,3,4,255]:
   for payload in payloads:modern('modern-point-tag-split-policy',name,'decodePoint',bytes=[tag]+list(payload))
  modern('modern-empty-point',name,'decodePoint',bytes=[])
  rw=32 if name=='P256' else orderwidth
  for op in ['generateScalar','generateKeyPair']:
   for data in [[0]*rw,[1]*rw,[255]*rw]:modern('modern-RNG-effects',name,op,payloads=[data]*100)
  for row in inv['samples'][name]:
   for family in ['decodePoint.encodePoint==id','ECDH commutes','decodeScalar.encodeScalar==id','scalarFromInteger.scalarToInteger==id','(a + b).P = a.P + b.P','(a * b).P = a.(b.P)']:modern('original-six-families-'+name,name,'property',family=family,**row)
 for row in inv['vectors']:
  name={'Curve_P256R1':'P256','Curve_P384R1':'P384','Curve_P521R1':'P521'}.get(row['curve'])
  if name:modern('original-point-vectors',name,'decodePoint',bytes=row['bytes'])
 for row in inv['KATs']:
  raw=row['public'][1:];p=[str(int.from_bytes(bytes(raw[:32]),'big')),str(int.from_bytes(bytes(raw[32:]),'big'))]
  modern('original-P256-ECDH-KAT-including-zero-X','P256','ecdh',a=str(int.from_bytes(bytes(row['private']),'big')),p=p)
 return cs
def hashes():
 files=[]
 for name in ['crypton-2.0.1','ram-0.22.1']:
  folder=ROOT/'vendor'/name;meta=json.loads((folder/'SOURCE.json').read_text())
  for path,hash in meta['files'].items():assert hashlib.sha256((folder/path).read_bytes()).hexdigest()==hash,path
  files.append(folder/'SOURCE.json')
 for path in ['Crypto/ECC.hs','Crypto/ECC/Simple/Types.hs','Crypto/ECC/Simple/Prim.hs','Crypto/Internal/ECC.hs','Crypto/Internal/Imports.hs','Crypto/Internal/DeepSeq.hs','Crypto/Internal/Compat.hs','Crypto/Number/Compat.hs','Crypto/Number/Serialize/Internal.hs','Crypto/Number/Generate.hs','Crypto/PubKey/ECC/P256.hs','tests/ECCSpec.hs','tests/PubKey/ECCSpec.hs','tests/Imports.hs','tests/Utils.hs']:files.append(ROOT/'vendor/crypton-2.0.1'/path)
 spec=importlib.util.spec_from_file_location('extract',ROOT/'tools/extract-ecc-modern-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 for name,text in ex.expected().items():assert (FOLDER/name).read_text()==text,name
 simple=(ROOT/'vendor/crypton-2.0.1/Crypto/ECC/Simple/Types.hs').read_text();legacy=(ROOT/'vendor/crypton-2.0.1/Crypto/PubKey/ECC/Types.hs').read_text()
 a={n:[int(v,16) for v in re.findall('0x([0-9a-f]+)',b)]+[int(re.search(r'curveEccH = (\d+)',b)[1])] for n,b in re.findall(r'type(SEC_\w+) =(.+?)(?=\ntypeSEC_|\Z)',simple,re.S)}
 b={n:[int(v,16) for v in re.findall('0x([0-9a-f]+)',b)]+[int(re.search(r'ecc_h = (\d+)',b)[1])] for n,b in re.findall(r'getCurveByName (SEC_\w+) =(.+?)(?=getCurveByName SEC_|\Z)',legacy,re.S)}
 assert len(a)==33 and a==b
 files += [ROOT/'licenses/crypton-BSD-3-Clause.txt',ROOT/'licenses/ram-BSD-3-Clause.txt',Path(__file__).resolve(),ROOT/'tools/extract-ecc-modern-reference.py']+[p for p in FOLDER.glob('*') if p.is_file()]
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
 match=re.search(r'(\d+) examples, 0 failures',p.stdout);assert match;examples=int(match[1])
 counts=list(map(int,re.findall(r'passed (\d+) tests',p.stdout)));assert len(counts)==6 and sum(counts)==660
 return dict(examples=examples,failures=0,properties=6,propertyChecks=660,seed=1729,maxSuccess=200)
def inventory(oracle):
 inv=json.loads(subprocess.check_output([oracle,'--inventory'],timeout=120))
 assert len(inv['vectors'])==35 and len(inv['KATs'])==2 and len(inv['subgroups'])==20
 assert set(inv['samples'])=={'P256','P384','P521'} and all(len(v)==20 for v in inv['samples'].values())
 names=re.findall(r'^data (SEC_\w+) =',(ROOT/'vendor/crypton-2.0.1/Crypto/ECC/Simple/Types.hs').read_text(),re.M)
 cs=[dict(request=dict(layer='simple',curve=n,op='type')) for n in names];query(oracle,cs)
 types={n:c['expected'] for n,c in zip(names,cs)}
 for c in types.values():c['modulus']=re.search(r'Param (\d+)',c['type'])[1]
 return inv,types
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();source=hashes()
 if a.check_source or a.verify_oracle:
  with gzip.open(TARGET,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
  assert m['sources']==source and m['cases']==len(cs) and m['caseHash']==digest(cs)
  assert [{k:v for k,v in c.items() if k!='expected'} for c in cs]==inputs(m['inventory'],m['types'])
  if a.verify_oracle:assert inventory(a.oracle)==(m['inventory'],m['types']);assert original(a.oracle)==m['originalSuite'];query(a.oracle,cs)
  print('Verified',len(cs),'Simple/modern prime observations,',len(source),'source hashes; replay =',a.verify_oracle)
 else:
  assert a.oracle;inv,types=inventory(a.oracle);summary=original(a.oracle);cs=inputs(inv,types);query(a.oracle,cs)
  partition=dict(supported=['P256','P384','P521'],pending=['X25519','X448','Edwards25519'],supportedPointVectors=27,pendingPointVectors=8,pendingWeakPointVectors=9,supportedECDHKATs=2,propertyFamiliesPerCurve=6,samplesPerFamily=20)
  m=dict(partition=partition,cases=len(cs),sources=source,caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),inventory=inv,types=types,originalSuite=summary)
  TARGET.parent.mkdir(parents=True,exist_ok=True)
  with TARGET.open('wb') as raw:
   with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0) as f:f.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cs)+'\n').encode())
  print('Generated',len(cs),'Simple/modern prime observations; SHA256',m['caseHash'])
