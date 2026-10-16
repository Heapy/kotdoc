#!/usr/bin/env python3
import argparse,ctypes as C,gzip,hashlib,json,random,subprocess,tempfile,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'tools/decaf448-field-reference';TARGET=ROOT/'core/src/test/resources/crypto-decaf448-field/data.jsonl.gz';hx=lambda x:f'{x&((1<<64)-1):016x}'
OPS=['copy','addRaw','subRaw','bias','weakReduce','strongReduce','add','sub','multiply','square','mulwUnsigned','isr','equal','lobit','serialize','deserialize','squareN','subNr','subxNr','mulw','select','condNeg','condSwap','mulQnr','divQnr','addNr']
def pins():
 subprocess.run([sys.executable,str(ROOT/'tools/extract-decaf448-field-reference.py'),'--check'],check=True)
 meta=json.loads((ROOT/'vendor/crypton-2.0.1/SOURCE.json').read_text())
 for p,h in meta['files'].items():assert hashlib.sha256((ROOT/'vendor/crypton-2.0.1'/p).read_bytes()).hexdigest()==h
 paths=sorted((D/'original').rglob('*'))+[D/'ObservedArithmetic.c',D/'FieldReference.c',D/'README.md',D/'RemainingSourceInventory.json',ROOT/'licenses/decaf-MIT.txt',ROOT/'vendor/crypton-2.0.1/crypton.cabal',ROOT/'vendor/crypton-2.0.1/Crypto/PubKey/Curve448.hs',ROOT/'vendor/crypton-2.0.1/Crypto/PubKey/Ed448.hs',ROOT/'vendor/crypton-2.0.1/tests/Curve448Spec.hs',ROOT/'vendor/crypton-2.0.1/tests/Ed448Spec.hs',ROOT/'tools/extract-decaf448-field-reference.py',ROOT/'tools/import-decaf448-field-fixtures.py',ROOT/'vendor/crypton-2.0.1/SOURCE.json']
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths if p.is_file()}
class Oracle:
 def __init__(self,tmp):
  self.libs={}
  for bits,arch in [(32,'arch_32'),(64,'arch_ref64')]:
   for observed in (False,True):
    target=tmp/f'{bits}-{observed}.dylib';args=['cc','-O2','-std=c99','-shared','-fPIC',f'-DFIELD_BITS={bits}']+(['-DOBSERVED']if observed else[])
    for p in [f'include/{arch}',f'p448/{arch}','include','p448']:args+=['-I',str(D/'original'/p)]
    subprocess.run(args+[str(D/'FieldReference.c'),'-o',str(target)],check=True);lib=C.CDLL(str(target));lib.df_run.restype=C.c_uint64;lib.df_trace_word.restype=C.c_uint64;lib.df_trace_label.restype=C.c_char_p;lib.df_constant.restype=C.c_uint64;lib.df_profile.restype=C.c_uint64;self.libs[bits,observed]=lib
 def observe(self,r,observed):
  bits=r['bits'];n=16 if bits==32 else 8;word=C.c_uint32 if bits==32 else C.c_uint64;prefix=32//C.sizeof(word);lib=self.libs[bits,observed]
  if r['op']=='profile':return dict(values=[lib.df_profile(i)for i in range(8)])
  if r['op']=='constant':return dict(words=[hx(lib.df_constant(['ZERO','ONE','MODULUS'].index(r['name']),i))for i in range(n)])
  keep=[]
  def storage(key):
   raw=C.create_string_buffer(160);keep.append(raw);address=(C.addressof(raw)+31)&~31;v=(word*(n+2*prefix)).from_address(address)
   for i in range(n+2*prefix):v[i]=0x12345678 if i<prefix else 0x76543210 if i>=prefix+n else int(r.get(key,[hx(0)]*n)[i-prefix],16)
   return v
  a=storage('a');b=storage('b');out=storage('out');alias=r.get('alias','none')
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  pointer=lambda v:C.byref(v,32)
  output=(C.c_uint8*58)(17,*([165]*56),34);inp=(C.c_uint8*58)(17,*r.get('bytes',[0]*56),34)
  value=lib.df_run(OPS.index(r['op']),pointer(out),pointer(a),pointer(b),C.c_int(r.get('amount',0)),C.c_uint64(int(r.get('mask',hx(0)),16)),C.byref(output,1),C.byref(inp,1))
  result=dict(a=list(map(hx,a)),b=list(map(hx,b)),out=list(map(hx,out)),bytes=list(output),value=hx(value))
  if r.get('trace'):result['trace']=[dict(label=lib.df_trace_label(i).decode(),limbs=[hx(lib.df_trace_word(i,j))for j in range(n)])for i in range(lib.df_trace_count())]
  return result
 def expected(self,r):
  ordinary=self.observe(r,False);observed=self.observe(r,True);assert {k:v for k,v in ordinary.items()if k!='trace'}=={k:v for k,v in observed.items()if k!='trace'},r
  return observed

def inputs(oracle):
 rng=random.Random(4482527);rows=[]
 for bits,n,radix in [(32,16,28),(64,8,56)]:
  p=2**448-2**224-1;mask=(1<<radix)-1;dm=(1<<bits)-1
  digits=lambda v:[hx((v>>(radix*i))&mask)for i in range(n)]
  vec=[digits(v)for v in [0,1,2,p-1,p,p+1,2**447,2**448-1]]
  vec += [[hx(mask*bound)]*n for bound in [2,3,4]]+[[hx(dm)]*n,[hx(1<<(bits-1))]*n]
  vec += [[hx(rng.getrandbits(radix+1))for _ in range(n)]for _ in range(20)]
  for index,a in enumerate(vec):
   b=vec[(index*7+3)%len(vec)]
   for op in OPS:
    restrict=op in ['multiply','square','mulwUnsigned','squareN','mulw','mulQnr','divQnr','condSwap']
    aliases=['none','a-b']if restrict else['none','out-a','out-b','a-b','all']
    for alias in aliases:
     amounts=([0,1,2,-1,9998,9999,-2**31]+([2**31-1]if op=='bias'else[]))if op in ['bias','subxNr']else[1,2,3,5,9]if op=='squareN'else([-((1<<28)-1),-2,-1,0,1,2,(1<<28)-1]+([-2**31+1,2**31-1]if bits==64 else[]))if op=='mulw'else[0]
     masks=[0,1,2,(1<<28)-1]+([2**32-1]if bits==64 else[])if op=='mulwUnsigned'else[0,1,128,255]if op=='deserialize'else[0,1,dm,dm//3]if op in ['select','condNeg','condSwap']else[0]
     for amount in amounts:
      for m in masks:
       r=dict(bits=bits,op=op,a=a,b=b,out=vec[(index+2)%len(vec)],alias=alias,amount=amount,mask=hx(m),bytes=list(rng.randbytes(56)),trace=op=='isr'and index<8)
       rows.append(dict(request=r,expected=oracle.expected(r)))
  for nvalue in [0,1,p-1,p,p+1,2**448-1]:
   for hi in [0,1,128,255]:
    r=dict(bits=bits,op='deserialize',bytes=list(nvalue.to_bytes(56,'little')),mask=hx(hi));rows.append(dict(request=r,expected=oracle.expected(r)))
  r=dict(bits=bits,op='profile');ex=oracle.expected(r);assert ex['values']==([16,32,64,2,448,32,56,32]if bits==32 else[8,64,128,9999,448,32,56,64]);rows.append(dict(request=r,expected=ex))
  for name in ['ZERO','ONE','MODULUS']:
   r=dict(bits=bits,op='constant',name=name);rows.append(dict(request=r,expected=oracle.expected(r)))
 # Mathematical assertions are secondary checks of ORIGINAL outputs, never expectations from Kotlin.
 for row in rows:
  r=row['request'];e=row['expected'];bits=r['bits'];radix=28 if bits==32 else 56
  def val(v):return sum(int(x,16)<<(radix*i)for i,x in enumerate(v))
  a=r.get('a',[]);b=r.get('b',[]);limit=(1<<radix)-1;p=(1<<448)-(1<<224)-1
  canonical=lambda x:bool(x)and all(int(v,16)<=limit for v in x)and val(x)<p
  if r.get('alias','none') in ['a-b','all']:b=a
  if canonical(a)and canonical(b)and r['op']in ['multiply','square','mulwUnsigned','add','sub','isr']:
   n=len(a);prefix=32//(bits//8);out=val(e['out'][prefix:prefix+n]);av=val(a);bv=val(b);op=r['op']
   target=av*bv if op=='multiply'else av*av if op=='square'else av*int(r['mask'],16)if op=='mulwUnsigned'else av+bv if op=='add'else av-bv if op=='sub'else None
   if target is not None:assert out%p==target%p,(r,out%p,target%p)
   else:assert (out*out*av)%p==(1 if int(e['value'],16)else 0 if av==0 else p-1),r
   r['mathDomain']='canonical-inputs'
 return rows
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--check-source',action='store_true');a.add_argument('--verify-oracle',action='store_true');args=a.parse_args();sources=pins()
 if args.check_source:
  with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f))
  assert meta['sources']==sources;print('Decaf field source pins PASS');sys.exit()
 with tempfile.TemporaryDirectory(prefix='kotdoc-decaf-field-')as tmp:
  oracle=Oracle(Path(tmp))
  if args.verify_oracle:
   with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f));rows=[json.loads(x)for x in f]
   assert meta['sources']==sources
   for row in rows:assert oracle.expected(row['request'])==row['expected'],row['request']
  else:
   rows=inputs(oracle);meta=dict(sources=sources,operations=OPS,caseHash=hashlib.sha256(json.dumps(rows,sort_keys=True,separators=(',',':')).encode()).hexdigest());TARGET.parent.mkdir(parents=True,exist_ok=True)
   with TARGET.open('wb')as raw:
    with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0)as z:
     for row in [meta]+rows:z.write((json.dumps(row,separators=(',',':'))+'\n').encode())
  print(f'Decaf field ordinary+observer PASS {len(rows)}rows/{len(sources)}pins')
