#!/usr/bin/env python3
"""Pinned ordinary C raw-memory observations, not Kotlin-derived field expectations."""
import argparse,collections,ctypes as C,gzip,hashlib,importlib.util,json,random,subprocess,sys,tempfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/x25519-field-reference';TARGET=ROOT/'core/src/test/resources/crypto-x25519-field/data.jsonl.gz';MASK=(1<<64)-1
hexword=lambda x:format(x&MASK,'016x')
OPS32=['sum','difference','scalar','product','reduce_degree','reduce_coefficients','mul','square_inner','square','expand','contract','div26','div25','eq32','gte32']
OPS64=['sum','difference','scalar','mul','square','expand','contract','load','store']
def inputs():
 result=[];rng=random.Random(25519003264)
 def add(bits,op,**r):result.append(dict(request=dict(bits=bits,op=op,**r)))
 for bits,ops in [(32,OPS32),(64,OPS64)]:
  n=10 if bits==32 else 5;width=26 if bits==32 else 51
  vectors=[[0]*n,[1]*n,[(1<<width)-1]*n,[(1<<(width-1))if i%2==0 else 0 for i in range(n)]]
  if bits==32:vectors += [[-1]*n,[-((1<<26)-1)]*n,[(1<<26)-1 if i%2 else -((1<<26)-1) for i in range(n)]]
  else:vectors += [[(1<<52)-1]*n,[(1<<55)-1]*n]
  vectors += [[rng.randrange(-(1<<25),(1<<25)) if bits==32 else rng.randrange(1<<51) for _ in range(n)] for _ in range(80)]
  for i,a in enumerate(vectors):
   b=vectors[(i*17+3)%len(vectors)]
   for op in ['sum','difference','scalar','mul','square']:
    for alias in ['none','out-a','out-b','a-b','all']:
     for count in ([1,2,5] if op=='square' and bits==64 else [1]):
      add(bits,op,alias=alias,a=list(map(hexword,a)),b=list(map(hexword,b)),out=list(map(hexword,a)),scalar=hexword(121665 if i%2 else 19),count=hexword(count))
   add(bits,'contract',alias='none',a=list(map(hexword,[max(-(1<<26)+1,min((1<<26)-1,x)) for x in a] if bits==32 else [x&((1<<52)-1) for x in a])))
   if bits==32:
    add(bits,'product',alias='none',a=list(map(hexword,a)),b=list(map(hexword,b)))
    add(bits,'square_inner',alias='none',a=list(map(hexword,a)))
  for i in range(120):
   data=[rng.randrange(256) for _ in range(32)] if i<100 else [i%256]*32
   add(bits,'expand',alias='none',bytes=data)
   if bits==64:
    add(bits,'load',alias='none',bytes=data)
    add(bits,'store',alias='none',scalar=hexword(rng.getrandbits(64)))
  if bits==32:
   # Preconditions bound degree reduction below14*2^54 and coefficients below280*2^54.
   for i in range(120):
    for op,limit in [('reduce_degree',14*(1<<54)),('reduce_coefficients',280*(1<<54))]:
     a=[rng.randrange(-limit+1,limit) for _ in range(19)];add(bits,op,alias='none',out=list(map(hexword,a)))
   for v in [-(1<<63),-(1<<63)+1,-(1<<32),-(1<<26)-1,-(1<<26),-1,0,1,(1<<25)-1,1<<25,(1<<26)-1,1<<26,(1<<63)-1]+[rng.randrange(-(1<<63),1<<63)for _ in range(150)]:
    for op in ['div25','div26']:add(bits,op,scalar=hexword(v))
   for a in [0,1,2,0x1ffffff,0x3ffffff,0x7fffffff]:
    for b in [0,1,2,0x1ffffff,0x3ffffff,0x7fffffff]:
     for op in ['eq32','gte32']:add(bits,op,x=hexword(a),y=hexword(b))
  p=(1<<255)-19
  for value in [0,1,p-1,p,p+1,(1<<255)-1,1<<255,(1<<256)-1]:add(bits,'expand',alias='none',bytes=list(value.to_bytes(32,'little')))
  for value in [0,1,p-1,p,p+1,(1<<255)-1]:
   digits=[];left=value
   for i in range(n):w=51 if bits==64 else 26-i%2;digits.append(left&((1<<w)-1));left>>=w
   add(bits,'contract',alias='none',a=list(map(hexword,digits)))
  for factor in [0,1,19,121665,(1<<31)-1,(1<<63)-1,1<<63,(1<<64)-1]:
   for alias in ['none','out-a']:add(bits,'scalar',alias=alias,a=[hexword(1)]*n,scalar=hexword(factor))
  for i in range(12):
   steps=['sum','difference','scalar']+(['reduce_coefficients']if bits==32 else[])+['mul','square','contract','expand']
   add(bits,'trace',alias='none',a=[hexword(rng.randrange(1<<20))for _ in range(n)],b=[hexword(rng.randrange(1<<20))for _ in range(n)],out=[hexword(rng.randrange(1<<20))for _ in range(n)],scalar=hexword(121665),count=hexword(2 if bits==64 else 1),steps=steps)
 for case in result:
  r=case['request'];r['observationClass']='selected-compiler-signed-assumptions'if r['bits']==32 else'unsigned64-128'
  if r['op']=='difference'and r['bits']==64:
   key='a'if r['alias']in['out-a','all']else'b'if r['alias']=='out-b'else'out'
   r['withinDocumentedOutputBound']=all(int(x,16)<1<<52 for x in r[key])
 return result

class Oracle:
 def __init__(self,folder):
  self.libs={}
  for bits in [32,64]:
   path=folder/f'field{bits}.dylib';subprocess.run(['clang','-O2','-std=c99',f'-DFIELD_BITS={bits}','-dynamiclib',str(FOLDER/'FieldReference.c'),'-o',str(path)],check=True)
   self.libs[bits]=C.CDLL(str(path))
   for name,restype,args in [('div25',C.c_int64,[C.c_int64]),('div26',C.c_int64,[C.c_int64]),('eq32',C.c_int32,[C.c_int32,C.c_int32]),('gte32',C.c_int32,[C.c_int32,C.c_int32]),('load',C.c_uint64,[C.c_void_p])]:
    if hasattr(self.libs[bits],'x25_'+name):f=getattr(self.libs[bits],'x25_'+name);f.restype=restype;f.argtypes=args
 def observe(self,r):
  if r['op']=='trace':
   step=dict(r);step.pop('steps');trace=[]
   for op in r['steps']:
    step['op']=op;state=self.observe(step);trace.append(dict(op=op,state=state))
    step['a']=state['out'][1:20];step['out']=state['out'][1:20];step['bytes']=state['bytes'][3:35]
   return dict(trace=trace)
  bits=r['bits'];lib=self.libs[bits];op=r['op'];n=10 if bits==32 else 5
  guard=[0x0123456789abcdef];tail=[0xfedcba9876543210,0x8877665544332211];poison=0xa5a5a5a5a5a5a5a5
  def buf(key):v=[int(x,16) for x in r.get(key,[])];return(C.c_uint64*22)(*(guard+v+[poison]*(19-len(v))+tail))
  a=buf('a');b=buf('b');out=buf('out');alias=r.get('alias','none')
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  ptr=lambda v:C.cast(C.byref(v,8),C.c_void_p)
  data=r.get('bytes',[165]*32);by=(C.c_uint8*39)(*([17,34,51]+data+[68,85,102,119]));bp=C.cast(C.byref(by,3),C.c_void_p)
  f=getattr(lib,'x25_'+op)
  scalar=C.c_uint64(int(r.get('scalar','0'),16))
  value=None
  if op in ['sum','difference']:f(ptr(out),ptr(a))
  elif op=='scalar':f(ptr(out),ptr(a),scalar)
  elif op in ['mul','product']:f(ptr(out),ptr(a),ptr(b))
  elif op in ['reduce_degree','reduce_coefficients']:f(ptr(out))
  elif op in ['square_inner']:f(ptr(out),ptr(a))
  elif op=='square':f(ptr(out),ptr(a),C.c_uint64(int(r['count'],16)))if bits==64 else f(ptr(out),ptr(a))
  elif op=='expand':f(ptr(out),bp)
  elif op=='contract':f(bp,ptr(a))
  elif op in ['div25','div26']:value=f(C.c_int64(scalar.value))
  elif op in ['eq32','gte32']:value=f(C.c_int32(int(r['x'],16)&0xffffffff),C.c_int32(int(r['y'],16)&0xffffffff))
  elif op=='load':value=f(bp)
  elif op=='store':f(bp,scalar)
  else:raise ValueError(op)
  result=dict(out=list(map(hexword,out)),a=list(map(hexword,a)),b=list(map(hexword,b)),bytes=list(by))
  if value is not None:result['value']=hexword(value)
  return result

def sources():
 spec=importlib.util.spec_from_file_location('extract',ROOT/'tools/extract-x25519-field-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 for name,text in ex.sources().items():assert (FOLDER/name).read_text()==text
 d=ROOT/'vendor/crypton-2.0.1';m=json.loads((d/'SOURCE.json').read_text());assert m['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 files=[d/'SOURCE.json',Path(__file__).resolve(),ROOT/'tools/extract-x25519-field-reference.py']+[p for p in FOLDER.glob('*')if p.is_file()]
 for bits,name,notice in [(32,'curve25519-donna.c','Google-BSD-3-Clause'),(64,'curve25519-donna-c64.c','public-domain')]:
  p=d/'cbits/curve25519'/name;assert hashlib.sha256(p.read_bytes()).hexdigest()==m['files']['cbits/curve25519/'+name];files.append(p)
  n=ROOT/f'licenses/curve25519-donna-{notice}.txt';assert n.read_text()==p.read_text().split('#include',1)[0];files.append(n)
 return{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(files)}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':'))for c in cs).encode()).hexdigest()
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();hashes=sources()
 if a.check_source or a.verify_oracle:
  with gzip.open(TARGET,'rt')as f:m=json.loads(next(f));cs=[json.loads(l)for l in f]
  assert m['sources']==hashes and m['caseHash']==digest(cs) and m['cases']==len(cs)
  if a.verify_oracle:assert m['compiler']==subprocess.check_output(['clang','--version'],text=True).splitlines()[0]
  assert [c['request']for c in cs]==[c['request']for c in inputs()]
 else:cs=inputs()
 if not a.check_source or a.verify_oracle:
  with tempfile.TemporaryDirectory(prefix='kotdoc-x25519-native-')as folder:
   oracle=Oracle(Path(folder))
   for c in cs:
    expected=oracle.observe(c['request'])
    if 'expected'in c:assert c['expected']==expected,c
    else:c['expected']=expected
 if not(a.check_source or a.verify_oracle):
  m=dict(cases=len(cs),caseHash=digest(cs),sources=hashes,compiler=subprocess.check_output(['clang','--version'],text=True).splitlines()[0],flags=['-O2','-std=c99'],profiles={'32':OPS32,'64':OPS64},groups=dict(collections.Counter(str(c['request']['bits'])+'/'+c['request']['op']for c in cs)),limits={'32':'Signed64 limbs, signed32 casts. Negative/overflowing signed left shifts, including contract packing and s32_eq, have ISO-C UB; two-complement/arithmetic-shift are source-required assumptions. Raw observations are pinned compiler behavior, not ISO-C portability guarantees. Reduction and multiply inputs retain documented finite magnitude bounds.','64':'Unsigned64/128 arithmetic and raw redundant limbs. Positive square counts only. count0 corresponds to2^64 do-while iterations, excluded from finite replay; no identity result invented.','aliases':'All masks/storage guards replayed. product/square_inner remain disjoint per source precondition; mul uses local temporaries and is exercised with defined aliases.'})
  TARGET.parent.mkdir(parents=True,exist_ok=True)
  with TARGET.open('wb')as raw:
   with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0)as f:f.write(('\n'.join(json.dumps(c,separators=(',',':'))for c in [m]+cs)+'\n').encode())
 print(('Verified'if a.check_source or a.verify_oracle else'Generated'),len(cs),'Donna field observations,',len(hashes),'source pins; native replay',a.verify_oracle)
