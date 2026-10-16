#!/usr/bin/env python3
"""Independent ordinary portable C vs mechanical point-loop observer capture/replay."""
import argparse,ctypes as C,gzip,hashlib,importlib.util,json,random,subprocess,tempfile,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'tools/edwards25519-points-reference';TARGET=ROOT/'core/src/test/resources/crypto-edwards25519-points/data.jsonl.gz'
OPS=['p1p1_to_partial','p1p1_to_full','full_to_pniels','add_p1p1','double_p1p1','nielsadd2_p1p1','pnielsadd_p1p1','double_partial','double','add','nielsadd2','pnielsadd','pack','unpackNegative','doubleScalar','windowEqual','chooseNiels','baseScalar','decode','equal','isIdentity','negate','pointAdd','pointDouble','cofactor','pointBaseScalar','movePniels','choosePniels','multiply','baseDoubleScalar','hasPrimeOrder']
hx=lambda x:f'{x&((1<<64)-1):016x}'
def check():
 subprocess.run([sys.executable,str(ROOT/'tools/extract-edwards25519-points-reference.py'),'--check'],check=True)
 meta=json.loads((ROOT/'vendor/crypton-2.0.1/SOURCE.json').read_text())
 for p,h in meta['files'].items():assert hashlib.sha256((ROOT/'vendor/crypton-2.0.1'/p).read_bytes()).hexdigest()==h,p
 ram=json.loads((ROOT/'vendor/ram-0.22.1/SOURCE.json').read_text());assert ram['sha256']=='72474962349dd101b970cc46bcc06c799f7311d228e7b59f1a44f81427159f70'
 for p,h in ram['files'].items():assert hashlib.sha256((ROOT/'vendor/ram-0.22.1'/p).read_bytes()).hexdigest()==h,p
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(D.glob('original/*'))+sorted(D.glob('Observed*.h'))+[D/x for x in ['README.md','PointsReference.c','OriginalEdwards25519Spec.hs','EdwardsPointsReference.hs','OriginalSamples.json','OriginalSuite.txt','cabal.project','cabal.project.freeze','kotdoc-edwards25519-points-reference.cabal']]+[ROOT/'vendor/crypton-2.0.1/SOURCE.json',ROOT/'vendor/crypton-2.0.1/Crypto/ECC/Edwards25519.hs',ROOT/'vendor/crypton-2.0.1/tests/Imports.hs',ROOT/'vendor/crypton-2.0.1/tests/Utils.hs',ROOT/'vendor/crypton-2.0.1/tests/ECC/Edwards25519Spec.hs',ROOT/'licenses/ed25519-donna-public-domain.txt',ROOT/'licenses/ed25519-crypton-exts-public-domain.txt',ROOT/'licenses/ram-BSD-3-Clause.txt',ROOT/'vendor/crypton-2.0.1/crypton.cabal',ROOT/'vendor/crypton-2.0.1/Crypto/Internal/ByteArray.hs',ROOT/'vendor/ram-0.22.1/SOURCE.json',ROOT/'vendor/ram-0.22.1/LICENSE',ROOT/'tools/extract-edwards25519-points-reference.py',ROOT/'tools/import-edwards25519-points-fixtures.py']}
class Oracle:
 def __init__(self,tmp):
  self.libs={}
  for bits in (32,64):
   for obs in (False,True):
    path=tmp/f'{bits}-{obs}.dylib';subprocess.run(['cc','-O2','-std=c99','-shared','-fPIC',f'-DPOINT_BITS={bits}']+(['-DOBSERVED']if obs else[])+[str(D/'PointsReference.c'),'-o',str(path)],check=True)
    lib=C.CDLL(str(path));lib.ep_trace_label.restype=C.c_char_p;lib.ep_trace_word.restype=C.c_uint64;self.libs[bits,obs]=lib
 def observe(self,r,obs):
  bits=r['bits'];n=10 if bits==32 else 5;sn=9 if bits==32 else 5;w=C.c_uint32 if bits==32 else C.c_uint64;lib=self.libs[bits,obs]
  def array(key):return(w*(4*n+2))(0x12345678,*[int(x,16)for x in r.get(key,[hx(0)]*(4*n))],0x76543210)
  a=array('a');b=array('b');out=array('out');alias=r.get('alias','none')
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  ptr=lambda v:C.byref(v,C.sizeof(w))
  scal=lambda k:(w*sn)(*[int(x,16)for x in r.get(k,[hx(0)]*sn)])
  s1=scal('s1');s2=scal('s2');ib=(C.c_uint8*34)(17,*r.get('bytes',[0]*32),34);ob=(C.c_uint8*34)(17,*([165]*32),34)
  lib.ep_set_table(r.get('table',0))
  value=lib.ep_run(OPS.index(r['op']),ptr(out),ptr(a),ptr(b),s1,s2,C.c_uint32(r.get('extra',0)),C.byref(ob,1),C.byref(ib,1))
  result=dict(a=list(map(hx,a)),b=list(map(hx,b)),out=list(map(hx,out)),bytes=list(ob),value=value)
  if r.get('trace'):result['trace']=[dict(label=lib.ep_trace_label(i).decode(),limbs=[hx(lib.ep_trace_word(i,j))for j in range(4*n)])for i in range(lib.ep_trace_count())]
  return result
 def expected(self,r):
  if r['op']=='originalSample':
   sample=r['sample'];result={}
   for key in ['a','b','c','prime']:
    decoded=self.expected(dict(bits=r['bits'],op='decode',bytes=sample[key]));assert decoded['value']==1
    encoded=self.expected(dict(bits=r['bits'],op='pack',a=decoded['out'][1:-1]));assert encoded['bytes'][1:-1]==sample[key]
    result[key]=encoded['bytes'][1:-1]
   return result
  ordinary=self.observe(r,False);observed=self.observe(r,True)
  assert {k:v for k,v in observed.items()if k!='trace'}=={k:v for k,v in ordinary.items()if k!='trace'},r
  return observed

def inputs(oracle):
 rng=random.Random(255193232);rows=[]
 for bits,n,sn,radix in [(32,10,9,30),(64,5,5,56)]:
  zero=[hx(0)]*(4*n);ident=zero.copy();ident[n]=ident[2*n]=hx(1)
  def scalar(v):return[hx((v>>(radix*i))&((1<<radix)-1 if i<sn-1 else (1<<bits)-1))for i in range(sn)]
  order=2**252+27742317777372353535851937790883648493
  points=[zero,ident]
  for value in [1,2,3,8,11,123,134,order-1]:
   r=dict(bits=bits,op='baseScalar',s1=scalar(value));ex=oracle.expected(r);points.append(ex['out'][1:-1]);rows.append(dict(request=r,expected=ex))
  torsion=bytes([199,23,106,112,61,77,216,79,186,60,11,118,13,16,103,15,42,32,83,250,44,57,204,198,78,199,253,119,146,172,3,122])
  decoded=oracle.expected(dict(bits=bits,op='decode',bytes=list(torsion)));assert decoded['value']==1
  for value in range(8):
   r=dict(bits=bits,op='multiply',a=decoded['out'][1:-1],s1=scalar(value));ex=oracle.expected(r);points.append(ex['out'][1:-1]);rows.append(dict(request=r,expected=ex))
  points += [[hx((1<<bits)-1)]*(4*n),[hx(1<<(bits-1))]*(4*n)]
  # Includes arbitrary coordinate tuples without pretending they satisfy the curve equation.
  for bound in [1,2,4]:
   points.append([hx(rng.randrange(bound*(1<<(26-i%2 if bits==32 else 51))))for c in range(4)for i in range(n)])
  for idx,a in enumerate(points):
   b=points[(idx*5+3)%len(points)]
   for op in OPS:
    if op in ['windowEqual','chooseNiels','choosePniels','unpackNegative','decode']:continue
    for alias in ['none','out-a','out-b','a-b','all']:
     # All listed aliases are memory-safe; some preserve intentionally surprising source results.
     for extra in ([0,1]if op in ['nielsadd2_p1p1','pnielsadd_p1p1']else[0,1,2,2**32-1]if op=='movePniels'else[0]):
      r=dict(bits=bits,op=op,a=a,b=b,out=points[(idx+2)%len(points)],s1=scalar([0,1,order-1,2**256-1][idx%4]),s2=scalar([2,0,123][idx%3]),alias=alias,extra=extra,trace=op in ['baseScalar','pointBaseScalar','doubleScalar','baseDoubleScalar','multiply']and idx<3 and alias in ['none','all'])
      rows.append(dict(request=r,expected=oracle.expected(r)))
  for mode in [1,2,3]:
   for op in ['baseScalar','chooseNiels']:
    for pos in [0,15,31]:
     for digit in [-128,-9,-8,-1,0,1,8,9,127]:
      r=dict(bits=bits,op=op,table=mode,extra=pos,bytes=[digit&255]+[0]*31,s1=scalar(order-1),trace=op=='baseScalar');rows.append(dict(request=r,expected=oracle.expected(r)))
  for pos in range(32):
   for digit in range(-128,128):
    r=dict(bits=bits,op='chooseNiels',extra=pos,bytes=[digit&255]+[0]*31);rows.append(dict(request=r,expected=oracle.expected(r)))
  for pos in [0,1,2,7,15,16,2**31,2**32-1]:
   r=dict(bits=bits,op='choosePniels',extra=pos,a=points[3],b=points[5]);rows.append(dict(request=r,expected=oracle.expected(r)))
  for a in [0,1,8,2**31,2**32-1]:
   for b in [0,1,8,2**31,2**32-1]:
    r=dict(bits=bits,op='windowEqual',extra=a,s1=[hx(b)]+[hx(0)]*(sn-1));rows.append(dict(request=r,expected=oracle.expected(r)))
  torsion=bytes([199,23,106,112,61,77,216,79,186,60,11,118,13,16,103,15,42,32,83,250,44,57,204,198,78,199,253,119,146,172,3,122])
  payloads=[bytes(32),b'\x01'+bytes(31),torsion]+[v.to_bytes(32,'little')for v in [2**255-20,2**255-19,2**255-18,2**255-1,2**256-1]]+[rng.randbytes(32)for _ in range(120)]
  payloads += [bytes.fromhex(s)for s in ['1337036ac32d8f30d4589c3c1c595812ce0fff40e37c6f5a97ab213f318290ad','c4b800c87010f9468303deea876503e886bfde1900e9e846fd4c3cd09c1cbc9f','5120abe03ca2af66c77ca320f0b21fb556f6b65fdd7e3264c14a30d97bf7a76f']]
  for p in payloads:
   for sign in [0,128]:
    payload=list(p);payload[31]^=sign
    for op in ['decode','unpackNegative']:
     r=dict(bits=bits,op=op,bytes=payload,out=points[-1]);rows.append(dict(request=r,expected=oracle.expected(r)))
  for index,sample in enumerate(json.loads((D/'OriginalSamples.json').read_text())):
   r=dict(bits=bits,op='originalSample',index=index,sample=sample);rows.append(dict(request=r,expected=oracle.expected(r)))
 return rows
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');p.add_argument('--oracle',type=Path);args=p.parse_args();pins=check()
 if args.check_source:
  with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f))
  assert meta['sources']==pins;print(f'Point source pins PASS {len(pins)}');sys.exit()
 if args.oracle:
  actual=json.loads(subprocess.check_output([str(args.oracle)]));assert actual==json.loads((D/'OriginalSamples.json').read_text())
  suite=subprocess.check_output([str(args.oracle),'--original','--seed','1729','--qc-max-success','100'],text=True)
  def stable(text):return '\n'.join(x for x in text.splitlines()if not x.startswith('Finished in '))
  assert stable(suite)==stable((D/'OriginalSuite.txt').read_text())
  assert suite.count('+++ OK, passed 100 tests.')==25 and '31 examples, 0 failures'in suite
  print('Original Haskell 100samples and25properties×100+6literals PASS')
 with tempfile.TemporaryDirectory(prefix='kotdoc-ed-points-')as t:
  oracle=Oracle(Path(t))
  if args.verify_oracle:
   with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f));rows=[json.loads(x)for x in f]
   assert meta['sources']==pins
   for row in rows:assert oracle.expected(row['request'])==row['expected'],row['request']
  else:
   rows=inputs(oracle);meta=dict(sources=pins,operations=OPS,profiles=[32,64],caseHash=hashlib.sha256(json.dumps(rows,sort_keys=True,separators=(',',':')).encode()).hexdigest());TARGET.parent.mkdir(parents=True,exist_ok=True)
   with TARGET.open('wb')as raw:
    with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0)as z:
     for row in [meta]+rows:z.write((json.dumps(row,separators=(',',':'))+'\n').encode())
  print(f'Point native raw/ordinary-observed PASS {len(rows)} cases/{len(pins)} pins')
