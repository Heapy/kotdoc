#!/usr/bin/env python3
import re
import argparse,ctypes as C,gzip,hashlib,json,random,subprocess,tempfile,sys,struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'tools/decaf448-point-multiplication-reference';TARGET=ROOT/'core/src/test/resources/crypto-decaf448-point-multiplication/data.jsonl.gz';hx=lambda x:f'{x&((1<<64)-1):016x}'
OPS=['scalarmul','doubleScalarmul','dualScalarmul','batchInvert','normalizeNiels','precompute','lookupNiels','precomputedScalarmul','directScalarmul','x448','generateKey','derivePublicKey','recodeWnaf','prepareWnafTable','precomputeWnafs','baseDoubleScalarmulNonSecret','precomputedDestroy']
PROFILES=[(32,32,False),(64,64,False),(64,32,False),(64,32,True)]
TABLE_OPS=['normalizeNiels','precompute','lookupNiels','precomputedScalarmul','precomputeWnafs','precomputedDestroy'];POINT_OPS=['scalarmul','doubleScalarmul','dualScalarmul','precomputedScalarmul','baseDoubleScalarmulNonSecret']
def pins():
 subprocess.run([sys.executable,str(ROOT/'tools/extract-decaf448-point-multiplication-reference.py'),'--check'],check=True)
 meta=json.loads((ROOT/'vendor/crypton-2.0.1/SOURCE.json').read_text())
 for p,h in meta['files'].items():assert hashlib.sha256((ROOT/'vendor/crypton-2.0.1'/p).read_bytes()).hexdigest()==h,p
 paths=sorted((D/'original').rglob('*'))+[D/p for p in ['SourceMatrix.json','TableLiterals.json','NormalizedPoints.c','ObservedPoints.c','BoundedWnafPoints.c','MultiplicationReference.c','README.md','CompilerProfile.txt','MultiplicationReference.hs','OriginalEd448Spec.hs','OriginalCurve448Spec.hs','OriginalPublicSuites.txt','HaskellObservations.json','CallerRangeObservations.json','verify-haskell.py','verify-math.py','verify-ranges.py','cabal.project','cabal.project.freeze','kotdoc-decaf448-point-multiplication-reference.cabal']]+[ROOT/p for p in ['tools/extract-decaf448-points-reference.py','tools/extract-decaf448-point-multiplication-reference.py','tools/import-decaf448-point-multiplication-fixtures.py','licenses/decaf-MIT.txt','vendor/crypton-2.0.1/SOURCE.json']]
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths if p.is_file()}
class Oracle:
 def __init__(self,tmp):
  self.libs={};self.paths={}
  for bits,sb,vector in PROFILES:
   arch='arch_32'if bits==32 else'arch_ref64'
   for variant,opt in [(0,'0'),(0,'2'),(0,'3'),(1,'2'),(2,'2'),(3,'2')]:
    path=tmp/f'{bits}-{sb}-{vector}-{variant}-{opt}.dylib';args=['cc',f'-O{opt}','-std=c99','-shared','-fPIC',f'-DFIELD_BITS={bits}',f'-DSCALAR_WORD_BITS={sb}',f'-DVARIANT={variant}']+(['-DNATIVE_VECTOR']if vector else[])
    for p in [f'include/{arch}',f'p448/{arch}','include','p448','']:args+=['-I',str(D/'original'/p)]
    subprocess.run(args+[str(D/'MultiplicationReference.c'),'-o',str(path)],check=True);lib=C.CDLL(str(path));self.libs[bits,sb,vector,variant,opt]=lib;self.paths[bits,sb,vector,variant,opt]=path
    lib.pm_run.restype=C.c_int64;lib.pm_field_blob.restype=C.c_void_p;lib.pm_logic_label.restype=C.c_char_p
    for name in ['pm_logic_word','pm_logic_extra','pm_table','pm_valid_point']:getattr(lib,name).restype=C.c_uint64
 def assertion_probes(self):
  result=[]
  for bits,sb,vector in PROFILES:
   for case in range(3):
    script="import ctypes,resource;resource.setrlimit(resource.RLIMIT_CORE,(0,0));ctypes.CDLL("+repr(str(self.paths[bits,sb,vector,0,'2']))+").pm_assert_probe("+str(case)+")"
    run=subprocess.run([sys.executable,'-c',script],capture_output=True,text=True);assert run.returncode<0 and 'Assertion'in run.stderr,(bits,sb,vector,case,run)
    result.append(dict(bits=bits,scalarBits=sb,vectorMasks32=vector,case=case,returncode=run.returncode))
  return result
 def source(self,bits,sb,vector):return self.libs[bits,sb,vector,0,'2']
 def point(self,bits,sb,vector,which):
  n=16 if bits==32 else 8;word=C.c_uint32 if bits==32 else C.c_uint64;raw=C.create_string_buffer(288);address=(C.addressof(raw)+31)&~31;v=(word*(4*n)).from_address(address);self.source(bits,sb,vector).pm_point_fixture(which,C.c_void_p(address));return list(map(hx,v))
 def encoded(self,bits,sb,vector,which):
  data=(C.c_ubyte*56)();self.source(bits,sb,vector).pm_encoded_fixture(which,data);return list(data)
 def table(self,bits,sb,vector,group=0):
  n=16 if bits==32 else 8;lib=self.source(bits,sb,vector);return[hx(lib.pm_table(group,i))for i in range((240 if group==0 else 96)*n)]
 def observe(self,r,variant=2,opt='2'):
  bits=r['bits'];sb=r['scalarBits'];vector=r['vectorMasks32'];n=16 if bits==32 else 8;word=C.c_uint32 if bits==32 else C.c_uint64;sw=C.c_uint32 if sb==32 else C.c_uint64;prefix=32//C.sizeof(word);sp=32//C.sizeof(sw);lib=self.libs[bits,sb,vector,variant,opt];op=r['op'];count=r.get('count',0);alias=r.get('alias','none');keep=[]
  def storage(key,length,typ=word):
   prefix=32//C.sizeof(typ);raw=C.create_string_buffer((length+2*prefix)*C.sizeof(typ)+32);keep.append(raw);address=(C.addressof(raw)+31)&~31;v=(typ*(length+2*prefix)).from_address(address);provided=[int(x,16)for x in r.get(key,[])]
   for i in range(len(v)):v[i]=0x12345678 if i<prefix else 0x76543210 if i>=prefix+length else provided[i-prefix]if i-prefix<len(provided)else 0x51515151
   return v
  outlen=4*n*max(1,1<<count)if op=='prepareWnafTable'else 4*n
  a=storage('a',4*n);b=storage('b',4*n);out=storage('out',outlen);out2=storage('out2',4*n);s1=storage('s1',448//sb,sw);s2=storage('s2',448//sb,sw)
  table=storage('table',240*n)if op in TABLE_OPS else None
  gfcount=max(2,count);gfin=storage('gfIn',gfcount*n)if op in ['batchInvert','normalizeNiels']else None;gfout=storage('gfOut',gfcount*n)if gfin is not None else None
  control=(C.c_int32*(2*(446//(count+1)+3)+2))(*([0x12345678]+[0x51515151]*(2*(446//(count+1)+3))+[0x76543210]))if op=='recodeWnaf'else None
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  if alias in ['out2-a','out-all']:out2=a
  if alias in ['out-out2','out-all']:out=out2
  if alias=='s1-s2':s2=s1
  if alias=='table-a':
   for i,x in enumerate(r.get('a',[])):table[prefix+i]=int(x,16)
   a=table
  if alias=='out-table':out=table
  if alias=='gf-table':gfin=table
  payloads={k:r.get(k,[165]*56)for k in ['bytes','baseBytes','scalarBytes']};buffers={k:(C.c_ubyte*58)(17,*v,34)for k,v in payloads.items()}
  if alias in ['base-scalar','byte-all']:buffers['scalarBytes']=buffers['baseBytes']
  if alias in ['out-base','byte-all']:buffers['bytes']=buffers['baseBytes']
  elif alias=='out-scalar':buffers['bytes']=buffers['scalarBytes']
  ptr=lambda x:C.byref(x,32)if x is not None else None
  value=lib.pm_run(OPS.index(op),ptr(out),ptr(out2),ptr(a),ptr(b),ptr(s1),ptr(s2),ptr(table),ptr(gfin),ptr(gfout),C.byref(control,4)if control is not None else None,C.c_int(count),C.c_int(r.get('index',0)),C.c_uint64(int(r.get('allow','0'),16)),C.c_uint64(int(r.get('shortCircuit','0'),16)),C.byref(buffers['bytes'],1),C.byref(buffers['baseBytes'],1),C.byref(buffers['scalarBytes'],1))
  result={k:list(map(hx,v))for k,v in dict(a=a,b=b,out=out,out2=out2,s1=s1,s2=s2,table=table,gfIn=gfin,gfOut=gfout).items()if v is not None};result.update({k:list(v)for k,v in buffers.items()});result['value']=hx(value)
  if control is not None:result['control']=list(control)
  # Snapshot all field-write bytes, not a digest synthesized from Kotlin.
  if variant in [2,3]:
   total=lib.pm_field_count();blob=C.string_at(lib.pm_field_blob(),total*160);stats={};states=[]
   for record in struct.iter_unpack('<32s16Q',blob):
    label=record[0].split(b'\0')[0].decode();words=record[1:1+n];s=stats.setdefault(label,dict(calls=0,maxWord=0,aboveRadix=0));s['calls']+=1;s['maxWord']=max(s['maxWord'],*words);s['aboveRadix']+=sum(v>((1<<(28 if bits==32 else 56))-1)for v in words)
    if r.get('details'):states.append(dict(label=label,words=list(map(hx,words))))
   for s in stats.values():s['maxWord']=hx(s['maxWord'])
   result['fieldAudit']=dict(count=total,sha256=hashlib.sha256(blob).hexdigest(),stats=stats)
   if r.get('details'):result['fieldAudit']['states']=states
   if r.get('audit')or op in ['recodeWnaf']or variant==3:result['logic']=[dict(label=lib.pm_logic_label(i).decode(),index=lib.pm_logic_index(i),words=[hx(lib.pm_logic_word(i,j))for j in range(lib.pm_logic_length(i))],extra=[hx(lib.pm_logic_extra(i,j))for j in range(3)])for i in range(lib.pm_logic_count())]
  if op in POINT_OPS:
   ordinary=self.source(bits,sb,vector);encoded=(C.c_ubyte*58)(17,*([165]*56),34);ordinary.pm_encode_point(ptr(out),C.byref(encoded,1));result['encodedOut']=list(encoded);result['validOut']=hx(ordinary.pm_valid_point(ptr(out)))
   if op=='dualScalarmul':
    ordinary.pm_encode_point(ptr(out2),C.byref(encoded,1));result['encodedOut2']=list(encoded);result['validOut2']=hx(ordinary.pm_valid_point(ptr(out2)))
  return result
 def expected(self,r):
  observed=self.observe(r);
  if 'literal'in r:assert observed['bytes'][1:-1]==r['literal'],r
  normalized=self.observe(r,1);base=lambda x:{k:v for k,v in x.items()if k not in ['fieldAudit','logic']};assert base(observed)==base(normalized),r;row=dict(request=r,expected=observed)
  if 'literalTable'in r:
   prefix=32//(r['bits']//8);actual=observed['table'][prefix:prefix+len(r['literalTable'])];row['compiledTableComparison']=dict(equal=actual==r['literalTable'],differentWordIndices=[i for i,(a,b)in enumerate(zip(actual,r['literalTable']))if a!=b])
  if r['op']in ['recodeWnaf','baseDoubleScalarmulNonSecret']:
   variants={f'clang-O{o}':self.observe(r,0,o)for o in ['0','2','3']};row['originalSignedShiftObservations']=variants;row['ordinaryMatchesNormalized']={k:v==normalized for k,v in variants.items()}
   if r['op']=='recodeWnaf':row['boundedOriginalPrefix']=self.observe(r,3)
  else:assert self.observe(r,0)==normalized,r
  return row

def requests(o):
 rng=random.Random(6132718);order=2**446-13818066809895115352007386748515426880336692474882178609894547503885
 for bits,sb,vector in PROFILES:
  n=16 if bits==32 else 8;radix=28 if bits==32 else 56;points=[o.point(bits,sb,vector,i)for i in range(6)];table=o.table(bits,sb,vector)
  scalars=[0,1,2,order-1,order,order+1,2*order-1,2**448-1,2**447,int('aa'*56,16)]+[rng.randrange(order)for _ in range(4)]
  words=lambda x:[hx(x>>i&((1<<sb)-1))for i in range(0,448,sb)]
  def row(op,**kw):
   r=dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op=op,a=points[0],b=points[2],s1=words(1),s2=words(2),alias='none',audit=False)
   if op in TABLE_OPS:r['table']=table
   r.update(kw);return r
  for i,s in enumerate(scalars):
   for alias in ['none','out-a']:
    yield row('scalarmul',s1=words(s),a=points[i%6],alias=alias,audit=i<2)
   for alias in ['none','out-a','out-b','a-b','all']:
    yield row('doubleScalarmul',s1=words(s),s2=words(scalars[(i+3)%len(scalars)]),alias=alias,audit=i==0)
   for alias in ['none','out-a','out2-a','out-out2','out-all']:
    yield row('dualScalarmul',s1=words(s),s2=words(scalars[(i+1)%len(scalars)]),alias=alias,audit=i==0)
   for alias in ['none','out-table']:
    yield row('precomputedScalarmul',s1=words(s),alias=alias,audit=i==1)
   for t in range(6):yield row('recodeWnaf',s1=words(s),count=t,audit=True)
   for alias in ['none','out-a']:
    yield row('baseDoubleScalarmulNonSecret',s1=words(s),s2=words(scalars[(i+2)%len(scalars)]),alias=alias,audit=i<2)
  for i,p in enumerate(points):
   yield row('precompute',a=p,audit=i==0,**(dict(literalTable=table)if i==0 else{}))
   dynamic=o.observe(row('precompute',a=p),0)['table'][32//(bits//8):-32//(bits//8)]
   for scalar in [0,1,2,order-1]:yield row('precomputedScalarmul',a=p,table=dynamic,s1=words(scalar),audit=scalar==1)
   yield row('precomputeWnafs',a=p,audit=i==0,**(dict(literalTable=o.table(bits,sb,vector,1))if i==0 else{}))
   for t in [0,1,2,3,5]:yield row('prepareWnafTable',a=p,count=t,audit=i==0)
  yield row('precompute',alias='table-a',audit=True)
  yield row('precomputeWnafs',alias='table-a',audit=True)
  for count in [0,1,2,16,80]:
   for index in sorted(set([-1,0,count-1,count,2147483647])):yield row('lookupNiels',count=count,index=index)
  for count in [2,3,8,32,80]:
   gf=[hx(v)for i in range(count)for v in [i+1]+[0]*(n-1)]
   yield row('batchInvert',count=count,gfIn=gf,audit=count==2)
   yield row('normalizeNiels',count=count,gfIn=gf,audit=count==2)
  for count in [2,8]:yield row('normalizeNiels',count=count,alias='gf-table',audit=True)
  p=2**448-2**224-1
  encodings=[o.encoded(bits,sb,vector,i)for i in range(6)]+[list(v.to_bytes(56,'little'))for v in [0,1,p-1,p,p+1,2**448-1]]
  for i,encoded in enumerate(encodings):
   for allow in [0,1,2,2**32,1<<(sb-1)]:
    for short in [0,1,2]:yield row('directScalarmul',baseBytes=encoded,allow=hx(allow),shortCircuit=hx(short),s1=words(scalars[i%len(scalars)]),alias='out-base'if i%2 else'none',audit=i==0 and allow==0 and short==0)
  bytevalues=[list(v.to_bytes(56,'little'))for v in [0,1,p-1,p,p+1,5,2**448-1,2**447]]+[list(rng.randbytes(56))for _ in range(4)]
  for i,base in enumerate(bytevalues):
   for alias in ['none','out-base','out-scalar','base-scalar','byte-all']:
    yield row('x448',baseBytes=base,scalarBytes=bytevalues[(i+3)%len(bytevalues)],alias=alias,audit=i==0)
  for data in bytevalues:
   for op in ['generateKey','derivePublicKey']:
    for alias in ['none','out-scalar']:yield row(op,scalarBytes=data,alias=alias)
  source=(D/'OriginalCurve448Spec.hs').read_text();kat={}
  for name in ['alicePrivate','alicePublic','bobPrivate','bobPublic','aliceMultBob']:
   section=re.split(r'\n[a-zA-Z][a-zA-Z0-9_]*(?:\s|=)',source.split(name+' =',1)[1],maxsplit=1)[0];kat[name]=[int(v,16)for v in re.findall(r'\\x([0-9a-f]{2})',section)]
  for base,secret in [('alicePublic','bobPrivate'),('bobPublic','alicePrivate')]:
   for alias in ['none','out-base','out-scalar']:yield row('x448',baseBytes=kat[base],scalarBytes=kat[secret],alias=alias,literal=kat['aliceMultBob'],audit=alias=='none')
  for secret,public in [('alicePrivate','alicePublic'),('bobPrivate','bobPublic')]:
   for op in ['generateKey','derivePublicKey']:
    for alias in ['none','out-scalar']:yield row(op,scalarBytes=kat[secret],literal=kat[public],alias=alias)
  yield row('precomputedDestroy')

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--verify-oracle',action='store_true');parser.add_argument('--limit',type=int);parser.add_argument('--output',type=Path,default=TARGET);args=parser.parse_args()
 sourcepins=pins()
 with tempfile.TemporaryDirectory(prefix='kotdoc-decaf-multiplication-')as tmp:
  oracle=Oracle(Path(tmp));probes=oracle.assertion_probes();rows=[]
  for i,request in enumerate(requests(oracle)):
   if args.limit is not None and i>=args.limit:break
   rows.append(oracle.expected(request))
   if(i+1)%100==0:print(f'captured {i+1}',flush=True)
  if args.verify_oracle:
   with gzip.open(args.output,'rt')as f:old=[json.loads(line)for line in f]
   assert old[0]['pins']==sourcepins,'source pins changed';assert old[1:]==rows,'oracle replay differs'
  else:
   args.output.parent.mkdir(parents=True,exist_ok=True)
   payload='\n'.join(json.dumps(v,separators=(',',':'))for v in [dict(pins=sourcepins,operations=OPS,profiles=PROFILES,assertionProbes=probes),*rows])+'\n'
   args.output.write_bytes(gzip.compress(payload.encode(),mtime=0))
  print(f'PASS {len(rows)} original/normalized/observed rows; {len(sourcepins)} pins',flush=True)
if __name__=='__main__':main()
