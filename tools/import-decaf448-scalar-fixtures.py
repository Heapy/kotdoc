#!/usr/bin/env python3
import argparse,ctypes as C,gzip,hashlib,json,random,subprocess,tempfile,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'tools/decaf448-scalar-reference';TARGET=ROOT/'core/src/test/resources/crypto-decaf448-scalar/data.jsonl.gz';hx=lambda x:f'{x&((1<<64)-1):016x}'
OPS=['subx','montmul','multiply','montsqr','invert','sub','add','setUnsigned','equal','decodeShort','decode','destroy','decodeLong','encode','condSelect','halve','copy','bzero']
def pins():
 subprocess.run([sys.executable,str(ROOT/'tools/extract-decaf448-scalar-reference.py'),'--check'],check=True)
 meta=json.loads((ROOT/'vendor/crypton-2.0.1/SOURCE.json').read_text())
 for p,h in meta['files'].items():assert hashlib.sha256((ROOT/'vendor/crypton-2.0.1'/p).read_bytes()).hexdigest()==h,p
 paths=sorted((D/'original').rglob('*'))+[D/p for p in ['CompilerProfile.txt','README.md','ScalarReference.hs','OriginalEd448Spec.hs','OriginalCurve448Spec.hs','OriginalPublicSuites.txt','HaskellObservations.json','verify-haskell.py','verify-mathematics.py','cabal.project','cabal.project.freeze','kotdoc-decaf448-scalar-reference.cabal']]+[D/'NormalizedObservedScalar.c',D/'BoundedObservedScalar.c',D/'ScalarReference.c',ROOT/'tools/extract-decaf448-scalar-reference.py',ROOT/'tools/import-decaf448-scalar-fixtures.py',ROOT/'licenses/decaf-MIT.txt',ROOT/'vendor/crypton-2.0.1/SOURCE.json']
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths if p.is_file()}
class Oracle:
 def __init__(self,tmp):
  self.libs={}
  for bits,arch,archbits in [(32,'arch_32',32),(64,'arch_ref64',64),(32,'arch_ref64',64)]:
   for variant,opt in [(0,'0'),(0,'2'),(0,'3'),(1,'2'),(2,'2')]:
    path=tmp/f'{bits}-{archbits}-{variant}-{opt}.dylib';args=['cc',f'-O{opt}','-std=c99','-shared','-fPIC',f'-DSCALAR_WORD_BITS={bits}',f'-DVARIANT={variant}']
    if bits==32 and archbits==64:args+=['-DDS_MIXED_ARCH64=1']
    for p in [f'include/{arch}','include']:args+=['-I',str(D/'original'/p)]
    subprocess.run(args+[str(D/'ScalarReference.c'),'-o',str(path)],check=True);lib=C.CDLL(str(path));lib.ds_run.restype=C.c_uint64;lib.ds_constant.restype=C.c_uint64;lib.ds_profile.restype=C.c_uint64;lib.ds_trace_label.restype=C.c_char_p;lib.ds_trace_word.restype=C.c_uint64;lib.ds_trace_extra.restype=C.c_uint64;self.libs[bits,variant,opt,archbits]=lib
 def observe(self,r,variant=1,opt='2',archbits=None):
  bits=r['bits'];n=448//bits;word=C.c_uint32 if bits==32 else C.c_uint64;size=bits//8;lib=self.libs[bits,variant,opt,archbits or bits]
  if r['op']=='constant':return dict(words=[hx(lib.ds_constant(['factor','p','r2','one','zero'].index(r['name']),i))for i in range(1 if r['name']=='factor'else n)])
  if r['op']=='profile':return dict(values=[lib.ds_profile(i)for i in range(8)])
  def storage(k):return(word*(n+3))(0x12345678,*[int(v,16)for v in r.get(k,[hx(0)]*n)],0x76543210,0x88776655)
  a=storage('a');b=storage('b');p=storage('p');out=storage('out');alias=r.get('alias','none');payload=r.get('bytes',[]);length=r.get('length',len(payload));body=max(128,length+64);buffer=(C.c_uint8*(body+16))(*([17]*8),*([165]*body),*([34]*8))
  for i,v in enumerate(payload):buffer[8+i]=v
  base=C.addressof(buffer)+8+r.get('byteShift',0)
  if alias=='byte-a':
   for i,v in enumerate(r['a']):
    for j in range(size):buffer[8+i*size+j]=(int(v,16)>>(8*j))&255
   a=(word*(n+3)).from_address(C.addressof(buffer)+8-size)
  if alias=='byte-out':out=(word*(n+3)).from_address(C.addressof(buffer)+8-size)
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  elif alias=='out-p':out=p
  if alias=='p-a':p=a
  if alias=='p-b':p=b
  ptr=lambda v:C.byref(v,size)
  mode=r.get('traceMode',0);value=lib.ds_run(OPS.index(r['op']),ptr(out),ptr(a),ptr(b),ptr(p),C.c_uint64(int(r.get('word',hx(0)),16)),C.c_void_p(base),C.c_size_t(length),mode)
  result=dict(a=list(map(hx,a)),b=list(map(hx,b)),p=list(map(hx,p)),out=list(map(hx,out)),bytes=list(buffer),value=hx(value))
  if mode:result['trace']=[dict(label=lib.ds_trace_label(i).decode(),index=lib.ds_trace_index(i),words=[hx(lib.ds_trace_word(i,j))for j in range(lib.ds_trace_length(i))],extra=[hx(lib.ds_trace_extra(i,j))for j in range(3)])for i in range(lib.ds_trace_count())]
  return result
 def expected(self,r):
  expected=self.observe(r);ordinary=self.observe(r,0)
  base=lambda x:{k:v for k,v in x.items()if k!='trace'}
  mixed={}
  if r['bits']==32:
   mixedExpected=self.observe(r,archbits=64)
   if r['op']!='profile':assert mixedExpected==expected,r
   mixed=dict(nativeMixed32Arch64=mixedExpected)
   if r['op']=='invert':mixed['mixedUndefinedShiftObservations']={f'clang-O{o}':base(self.observe(r,0,o,64))for o in ['0','2','3']}
   elif r['op']!='profile':assert base(self.observe(r,0,archbits=64))==base(expected),r
  if r['op']!='invert':assert base(expected)==base(ordinary),r;return dict(request=r,expected=expected,**mixed)
  variants={f'clang-O{o}':base(self.observe(r,0,o))for o in ['0','2','3']}
  bounded=self.observe(r,2);prefix=[s for s in expected.get('trace',[])if s['label']in ['inverse:table','inverse:prefix']]
  assert bounded['trace'][:-1]==prefix and bounded['trace'][-1]['label']=='inverse:bounded-stop'
  assert bounded['trace'][-1]['words']==prefix[-1]['words']
  return dict(request=r,expected=expected,nativeUndefinedShiftObservations=variants,definedPrefixStop=bounded['trace'][-1],ordinaryMatchesNormalized={k:base(expected)==v for k,v in variants.items()},**mixed)

def inputs(oracle):
 rng=random.Random(4481718);rows=[]
 for bits in [32,64]:
  n=448//bits;dm=(1<<bits)-1;p=2**446-13818066809895115352007386748515426880336692474882178609894547503885
  digits=lambda v:[hx((v>>(i*bits))&dm)for i in range(n)]
  vec=[digits(v)for v in [0,1,2,p-1,p,p+1,2*p-1,2*p,2**447,2**448-1]]+[[hx(1<<(bits-1))]*n]+[digits(rng.randrange(p))for _ in range(8)]
  for index,a in enumerate(vec):
   b=vec[(index*7+3)%len(vec)]
   for op in ['subx','montmul','multiply','montsqr','sub','add','equal','destroy','condSelect','halve','copy']:
    aliases=['none','out-a','out-b','a-b','all']+(['out-p','p-a','p-b']if op=='subx'else[])
    for alias in aliases:
     for w in ([0,1]if op=='subx'else[0,1,2,1<<(bits-1),dm]if op=='condSelect'else[0]):
      r=dict(bits=bits,op=op,a=a,b=b,p=digits(p),out=vec[(index+2)%len(vec)],alias=alias,word=hx(w),traceMode=1 if op in ['subx','montmul','multiply','montsqr','sub','add']and index<4 else 0);rows.append(oracle.expected(r))
   if index<10 or 11<=index<14:
    for alias in ['none','out-a','out-b','a-b','all']:
     r=dict(bits=bits,op='invert',a=a,b=b,out=vec[(index+2)%len(vec)],alias=alias,traceMode=2,inverseCUndefined=True);rows.append(oracle.expected(r))
   for shift in [-1,0,1,8]:
    r=dict(bits=bits,op='encode',a=a,alias='byte-a',byteShift=shift);rows.append(oracle.expected(r))
   r=dict(bits=bits,op='encode',a=a);rows.append(oracle.expected(r))
  for length in list(range(0,129))+[168,224,256,511,512,513,1024]:
   payload=list(rng.randbytes(length+1))
   for op in ['decodeShort','decodeLong','bzero']:
    for alias in ['none','byte-out']:
     for shift in ([-1,0,1]if alias=='byte-out'else[0]):
      r=dict(bits=bits,op=op,bytes=payload,length=length,alias=alias,byteShift=shift,traceMode=3 if op=='decodeLong'else 0);rows.append(oracle.expected(r))
  for v in [0,1,p-1,p,p+1,2**448-1]:
   for alias in ['none','byte-out']:
    r=dict(bits=bits,op='decode',bytes=list(v.to_bytes(56,'little')),length=56,alias=alias,traceMode=1);rows.append(oracle.expected(r))
  for v in [0,1,2**32-1,2**32,2**63,2**64-1]:
   r=dict(bits=bits,op='setUnsigned',word=hx(v));rows.append(oracle.expected(r))
  for name in ['factor','p','r2','one','zero']:rows.append(oracle.expected(dict(bits=bits,op='constant',name=name)))
  rows.append(oracle.expected(dict(bits=bits,op='profile')))
 return rows
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--check-source',action='store_true');a.add_argument('--verify-oracle',action='store_true');args=a.parse_args();sources=pins()
 if args.check_source:
  with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f))
  assert meta['sources']==sources;print('Decaf scalar source pins PASS');sys.exit()
 with tempfile.TemporaryDirectory(prefix='kotdoc-decaf-scalar-')as tmp:
  oracle=Oracle(Path(tmp))
  if args.verify_oracle:
   with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f));rows=[json.loads(x)for x in f]
   assert meta['sources']==sources
   for row in rows:assert oracle.expected(row['request'])==row,row['request']
  else:
   rows=inputs(oracle);meta=dict(sources=sources,operations=OPS,caseHash=hashlib.sha256(json.dumps(rows,sort_keys=True,separators=(',',':')).encode()).hexdigest());TARGET.parent.mkdir(parents=True,exist_ok=True)
   with TARGET.open('wb')as raw:
    with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0)as z:
     for row in [meta]+rows:z.write((json.dumps(row,separators=(',',':'))+'\n').encode())
  print(f'Decaf scalar native PASS {len(rows)}rows/{len(sources)}pins')
