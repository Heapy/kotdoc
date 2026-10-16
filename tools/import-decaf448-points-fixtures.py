#!/usr/bin/env python3
import argparse,ctypes as C,gzip,hashlib,json,random,subprocess,tempfile,sys,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'tools/decaf448-points-reference';TARGET=ROOT/'core/src/test/resources/crypto-decaf448-points/data.jsonl.gz';hx=lambda x:f'{x&((1<<64)-1):016x}'
OPS=['invert','deisogenize','encode','decode','sub','add','doubleInternal','double','negate','condNegNiels','toPniels','fromPniels','fromNiels','addNiels','subNiels','addPniels','subPniels','prepareFixedWindow','equal','valid','torque','pscale','select','encodeEd','decodeEd','edPublicToX','encodeX','destroy','copy']
def pins():
 subprocess.run([sys.executable,str(ROOT/'tools/extract-decaf448-points-reference.py'),'--check'],check=True)
 meta=json.loads((ROOT/'vendor/crypton-2.0.1/SOURCE.json').read_text())
 for p,h in meta['files'].items():assert hashlib.sha256((ROOT/'vendor/crypton-2.0.1'/p).read_bytes()).hexdigest()==h,p
 paths=sorted((D/'original').rglob('*'))+[D/p for p in ['CompilerProfile.txt','README.md','SourceMatrix.json','RangeObservations.json','RangeWitnesses.json','HaskellObservations.json','PointsReference.hs','OriginalEd448Spec.hs','OriginalCurve448Spec.hs','OriginalPublicSuites.txt','verify-haskell.py','verify-mathematics.py','cabal.project','cabal.project.freeze','kotdoc-decaf448-points-reference.cabal']]+[D/'ObservedPoints.c',D/'TableLiterals.json',D/'PointsReference.c',ROOT/'tools/extract-decaf448-points-reference.py',ROOT/'tools/import-decaf448-points-fixtures.py',ROOT/'licenses/decaf-MIT.txt',ROOT/'vendor/crypton-2.0.1/SOURCE.json']
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths if p.is_file()}
class Oracle:
 def __init__(self,tmp):
  self.libs={};self.paths={}
  for bits,scalarbits,arch,vector in [(32,32,'arch_32',False),(64,64,'arch_ref64',False),(64,32,'arch_ref64',False),(64,32,'arch_ref64',True)]:
   for observed in [False,True]:
    path=tmp/f'{bits}-{scalarbits}-{vector}-{observed}.dylib';args=['cc','-O2','-std=c99','-shared','-fPIC',f'-DFIELD_BITS={bits}',f'-DSCALAR_WORD_BITS={scalarbits}']+(['-DOBSERVED']if observed else[])+(['-DNATIVE_VECTOR']if vector else[])
    for p in [f'include/{arch}',f'p448/{arch}','include','p448','']:args+=['-I',str(D/'original'/p)]
    subprocess.run(args+[str(D/'PointsReference.c'),'-o',str(path)],check=True);lib=C.CDLL(str(path));self.libs[bits,scalarbits,vector,observed]=lib
    if not observed:self.paths[bits,scalarbits,vector]=path
    for name in ['dp_run','dp_table','dp_constant','dp_profile','dp_trace_word']:getattr(lib,name).restype=C.c_uint64
    lib.dp_trace_label.restype=C.c_char_p
 def observe(self,r,observed=False):
  bits=r['bits'];sb=r['scalarBits'];n=16 if bits==32 else 8;word=C.c_uint32 if bits==32 else C.c_uint64;prefix=32//C.sizeof(word);lib=self.libs[bits,sb,r.get('vectorMasks32',False),observed];op=r['op']
  if op=='profile':return dict(values=[lib.dp_profile(i)for i in range(10)],byteorder=sys.byteorder)
  if op=='table':
   out=(C.c_ubyte*58)(17,*([165]*56),34);raw=(C.c_ubyte*66)(17,*([165]*64),34);lib.dp_table_bytes(r['index'],C.byref(out,1));lib.dp_table_raw_bytes(r['index'],C.byref(raw,1));return dict(words=[hx(lib.dp_table(r['index'],i))for i in range(n)],bytes=list(out),rawBytes=list(raw))
  if op=='constant':
   which=['ristretto','scalarAdjustment','precomputedAdjustment','xBase','identity'].index(r['name']);count=n if which==0 else 448//sb if which in [1,2]else 56 if which==3 else 4*n;return dict(words=[hx(lib.dp_constant(which,i))for i in range(count)])
  keep=[];count=max(1,r.get('flag',1))if op=='prepareFixedWindow'else 1
  def storage(key,length):
   raw=C.create_string_buffer((length+2*prefix)*C.sizeof(word)+32);keep.append(raw);address=(C.addressof(raw)+31)&~31;v=(word*(length+2*prefix)).from_address(address)
   for i in range(len(v)):v[i]=0x12345678 if i<prefix else 0x76543210 if i>=prefix+length else int(r.get(key,[hx(0)]*length)[i-prefix],16)
   return v
  a=storage('a',4*n);b=storage('b',4*n);out=storage('out',4*n*count);alias=r.get('alias','none')
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  pointer=lambda v:C.byref(v,32)
  payload=r.get('bytes',[0]*128);buffer=(C.c_ubyte*130)(17,*payload,34)
  value=lib.dp_run(OPS.index(op),pointer(out),pointer(a),pointer(b),C.c_uint64(int(r.get('mask','0'),16)),C.c_uint64(int(r.get('mask2','0'),16)),C.c_uint64(int(r.get('mask3','0'),16)),C.c_int(r.get('flag',0)),C.byref(buffer,1))
  result=dict(a=list(map(hx,a)),b=list(map(hx,b)),out=list(map(hx,out)),bytes=list(buffer),value=hx(value))
  if r.get('trace'):result['trace']=[dict(label=lib.dp_trace_label(i).decode(),words=[hx(lib.dp_trace_word(i,j))for j in range(n)])for i in range(lib.dp_trace_count())]
  return result
 def expected(self,r):
  original=self.observe(r);observed=self.observe(r,True);assert {k:v for k,v in original.items()if k!='trace'}=={k:v for k,v in observed.items()if k!='trace'},r
  return observed
 def assertion_probes(self):
  result=[]
  for key,path in self.paths.items():
   for which in [0,1]:
    command='import ctypes,resource;resource.setrlimit(resource.RLIMIT_CORE,(0,0));ctypes.CDLL('+repr(str(path))+').dp_assert_probe('+str(which)+')'
    p=subprocess.run([sys.executable,'-c',command],capture_output=True,text=True,cwd=path.parent);assert p.returncode<0 and 'Assertion' in p.stderr,(key,which,p.returncode,p.stderr);result.append(dict(bits=key[0],scalarBits=key[1],case=which,vectorMasks32=key[2],returncode=p.returncode,assertion='crypton_gf_invert: ret'))
  return result

def inputs(oracle):
 rng=random.Random(4482929);rows=[];table=json.loads((D/'TableLiterals.json').read_text());pubs=[]
 # Verbatim original Ed448 public-key literals, independent of Kotlin.
 import re
 text=(ROOT/'vendor/crypton-2.0.1/tests/Ed448Spec.hs').read_text()
 for m in re.finditer(r'vecPub\s*=\s*"([^"]+)"',text):pubs.append([int(x,16)for x in re.findall(r'\\x([0-9a-fA-F]{2})',m[1])])
 assert len(pubs)==8
 for bits,sb,vector in [(32,32,False),(64,64,False),(64,32,False),(64,32,True)]:
  n=16 if bits==32 else 8;radix=28 if bits==32 else 56;dm=(1<<bits)-1;mask=(1<<radix)-1;p=(1<<448)-(1<<224)-1
  digits=lambda v:[hx((v>>(radix*i))&mask)for i in range(n)]
  identity=digits(0)+digits(1)+digits(1)+digits(0);base=sum([digits(sum(v<<(56*i)for i,v in enumerate(a)))for a in table['base']],[])
  vec=[identity,base,digits(0)+digits(p-1)+digits(1)+digits(0),[hx(0)]*(4*n)]
  for v in [0,1,2,p-1,p,p+1,2**448-1]:vec.append(sum([digits(v)]*4,[]))
  vec+=[[hx(rng.getrandbits(radix))for _ in range(4*n)]for _ in range(8)]
  # Same valid base in lazy representations: carry redistribution and +p in each coordinate.
  lazy=[int(v,16)for v in base]
  for coordinate in range(4):
   for j in range(n-1):
    if lazy[coordinate*n+j]==0 and lazy[coordinate*n+j+1]>0:lazy[coordinate*n+j]+=1<<radix;lazy[coordinate*n+j+1]-=1;break
  vec += [list(map(hx,lazy)),[hx(int(v,16)+mask-(1 if i%n==n//2 else 0))for i,v in enumerate(base)]]

  def row(op,**kw):
   r=dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op=op,**kw);e=oracle.expected(r);rows.append(dict(request=r,expected=e));return e
  # Valid projective representatives, including torque and rescale of the original base.
  for op in ['torque','double','negate','pscale']:
   e=row(op,a=base,bytes=list((2).to_bytes(56,'little'))+[0]*72);prefix=32//(bits//8);vec.append(e['out'][prefix:-prefix])
  validset=[v for v in vec if int(oracle.observe(dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='valid',a=v))['value'],16)]
  for i,a in enumerate(vec):
   b=vec[(i*7+1)%len(vec)];out=vec[(i+2)%len(vec)]
   for op in ['invert','deisogenize','encode','sub','add','doubleInternal','double','negate','condNegNiels','toPniels','fromPniels','fromNiels','addNiels','subNiels','addPniels','subPniels','equal','valid','torque','pscale','select','edPublicToX','encodeX','destroy','copy']:
    aliases=['none','a-b']if op in ['deisogenize','toPniels','fromPniels','fromNiels','addNiels','subNiels','addPniels','subPniels']else['none','out-a','out-b','a-b','all']
    for alias in aliases:
     flags=[0,1,-1,2]if op in ['doubleInternal','addNiels','subNiels','addPniels','subPniels']else[0]
     masks=[0,1,dm,dm//3]if op in ['condNegNiels','deisogenize']else[0,1,2,1<<(sb-1),(1<<sb)-1]if op=='select'else[0]
     for flag in flags:
      for m in masks:row(op,a=a,b=b,out=out,alias=alias,flag=flag,mask=hx(m),mask2=hx(m^dm),mask3=hx(dm),bytes=list(rng.randbytes(128)),trace=i<4 and alias in ['none','all'])
  for a in validset:
   for alias in ['none','out-a']:row('encodeEd',a=a,alias=alias)
   for nt in [0,1,2,3,8,16]:row('prepareFixedWindow',a=a,flag=nt,trace=nt in [1,16])
  rawPlusP=[hx(int(v,16)+mask-(1 if i%n==n//2 else 0))for i,v in enumerate(base)]
  row('valid',a=rawPlusP,trace=True,boundary='same-math-base-plus-p')
  row('add',a=rawPlusP,b=rawPlusP,trace=True,boundary='same-math-base-plus-p')
  row('add',a=base,b=base,trace=True,boundary='tight-base-control')
  for a in validset:
   for factor in [0,1,p-1,p,p+1,2**448-1]:row('pscale',a=a,bytes=list(factor.to_bytes(56,'little'))+[165]*72,trace=True)
   e=row('encode',a=a,trace=True);row('decode',bytes=e['bytes'][1:57]+[165]*72,mask=hx(1),trace=True)
  for value in [0,1,2,3,4,p-1,p,p+1,2**448-1]+[rng.getrandbits(448)for _ in range(40)]:
   for allow in [0,1,2,(1<<sb)-1]:row('decode',bytes=list(value.to_bytes(56,'little'))+[165]*72,mask=hx(allow),trace=value in [0,1,2,p,p+1])
  encoded=pubs+[list(v.to_bytes(56,'little'))+[last]for v in [0,1,2,p-1,p,p+1,2**448-1]for last in [0,1,127,128,129,255]]+[list(rng.randbytes(57))for _ in range(40)]
  for i,enc in enumerate(encoded):row('decodeEd',bytes=enc+[165]*71,trace=i<20)
  for a in validset:
   for m in [0,1,dm]:row('deisogenize',a=a,mask=hx(m),mask2=hx(m),mask3=hx(m))
  for index in range(340):row('table',index=index)
  for name in ['ristretto','scalarAdjustment','precomputedAdjustment','xBase','identity']:row('constant',name=name)
  row('profile')
 return rows
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--check-source',action='store_true');a.add_argument('--verify-oracle',action='store_true');args=a.parse_args();sources=pins()
 if args.check_source:
  with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f))
  assert meta['sources']==sources;print('Decaf points source pins PASS');sys.exit()
 with tempfile.TemporaryDirectory(prefix='kotdoc-decaf-points-')as tmp:
  oracle=Oracle(Path(tmp))
  if args.verify_oracle:
   with gzip.open(TARGET,'rt')as f:meta=json.loads(next(f));rows=[json.loads(x)for x in f]
   assert meta['sources']==sources
   for row in rows:assert oracle.expected(row['request'])==row['expected'],row['request']
   assert oracle.assertion_probes()==meta['assertionProbes']
  else:
   rows=inputs(oracle);meta=dict(sources=sources,operations=OPS,assertionProbes=oracle.assertion_probes(),caseHash=hashlib.sha256(json.dumps(rows,sort_keys=True,separators=(',',':')).encode()).hexdigest());TARGET.parent.mkdir(parents=True,exist_ok=True)
   with TARGET.open('wb')as raw:
    with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0)as z:
     for row in [meta]+rows:z.write((json.dumps(row,separators=(',',':'))+'\n').encode())
  print(f'Decaf point geometry native PASS {len(rows)}rows/{len(sources)}pins')
