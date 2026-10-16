#!/usr/bin/env python3
"""Independent pinned C modm raw buffers/stages and original Haskell scalar generators."""
import argparse,ctypes as C,gzip,hashlib,importlib.util,json,random,re,subprocess,sys,tempfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/edwards25519-scalar-reference';TARGET=ROOT/'core/src/test/resources/crypto-edwards25519-scalar/data.jsonl.gz';hx=lambda x:format(x&((1<<64)-1),'016x')
OPS=['lt','reduce','barrett','add','mul','expand','expand_raw','contract','window4','sliding','sub_batch','lt_batch','lte_batch','zero','one','128']
def inputs(samples):
 rng=random.Random(255191616);rows=[]
 def add(bits,op,**kw):rows.append(dict(request=dict(bits=bits,op=op,**kw)))
 for bits,n,w in [(32,9,30),(64,5,56)]:
  vec=[[0]*n,[1]+[0]*(n-1),[(1<<w)-1]*(n-1)+[(1<<(256-(n-1)*w))-1],[(1<<bits)-1]*n,[1<<(bits-1)]*n]
  vec += [[rng.randrange(1<<w)for _ in range(n-1)]+[rng.randrange(1<<(256-(n-1)*w))]for _ in range(35)]
  vec += [[rng.getrandbits(bits)for _ in range(n)]for _ in range(6)]
  for i,a in enumerate(vec):
   b=vec[(i*13+3)%len(vec)]
   for op in ['reduce','barrett','add','mul','sub_batch']:
    for alias in ['none','out-a','out-b','a-b','all']:
     for size in (list(range(n))+[-1,n,n+1]if op=='sub_batch'else[n-1]):add(bits,op,a=list(map(hx,a)),b=list(map(hx,b)),out=list(map(hx,a)),alias=alias,size=size,trace=op in ['reduce','barrett','add','mul']and i<8)
   for op in ['zero','one','128','contract','window4']:
    for alias in (['none','byte-a']if op in ['contract','window4']else['none']):add(bits,op,a=list(map(hx,a)),alias=alias)
   for window in [1,2,3,4,5,6,7,8]:
    for alias in ['none','byte-a']:add(bits,'sliding',a=list(map(hx,a)),window=window,alias=alias)
   for size in list(range(n))+[-1,n,n+1]:
    for op in ['lt_batch','lte_batch']:add(bits,op,a=list(map(hx,a)),b=list(map(hx,b)),size=size)
   add(bits,'lt',left=hx(a[0]),right=hx(b[0]))
  for length in range(65):
   payload=list(rng.randbytes(length))
   for alias in ['none','byte-out']:add(bits,'expand',bytes=payload,length=length,alias=alias,trace=True)
  for i in range(80):
   for alias in ['none','byte-out']:add(bits,'expand_raw',bytes=list(rng.randbytes(32)),alias=alias)
  for v in [0,1,(1<<128)-1,1<<128,(1<<252)-1,(1<<256)-1,(1<<512)-1]:add(bits,'expand',bytes=list(v.to_bytes(64,'little')),length=64,trace=True)
  # Precise source-boundaries, raw high-bit mask quirks, and matched Barrett factors.
  text=(ROOT/f'vendor/crypton-2.0.1/cbits/ed25519/modm-donna-{bits}bit.h').read_text();mwords=[int(x,16)for x in re.findall(r'0x[0-9a-f]+',text.split('modm_m = {',1)[1].split('};',1)[0])];order=sum(x<<(w*i)for i,x in enumerate(mwords))
  def digits(value):return[(value>>(w*i))&((1<<(bits if i==n-1 else w))-1)for i in range(n)]
  for value in [0,1,order-1,order,order+1,2*order-1,2*order,(1<<256)-1]:
   a=digits(value)
   for op in ['reduce','mul']:
    for alias in ['none','out-a','out-b','a-b','all']:add(bits,op,a=list(map(hx,a)),b=list(map(hx,a)),out=list(map(hx,a)),alias=alias,trace=True)
  for bit in ([7,8,29,30,31]if bits==32 else[15,16,55,56,63]):
   a=[0]*n;a[4 if bits==32 else 2]=1<<bit;add(bits,'128',a=list(map(hx,a)),alias='none')
  for left in [0,1,2,(1<<(bits-1))-1,1<<(bits-1),(1<<bits)-1]:
   for right in [0,1,2,(1<<(bits-1))-1,1<<(bits-1),(1<<bits)-1]:add(bits,'lt',left=hx(left),right=hx(right))
  for value in [0,order-1,order,(1<<256)-1,(1<<512)-1]+[rng.getrandbits(512)for _ in range(3)]:
   a=digits(value>>248);b=digits(value&((1<<264)-1))
   for alias in ['none','out-a','out-b','a-b','all']:add(bits,'barrett',a=list(map(hx,a)),b=list(map(hx,b)),alias=alias,trace=True)
  for name in ['modm_m','modm_mu']:add(bits,'constant',name=name,group=0 if name=='modm_m'else 1)
  for i,sample in enumerate(samples):add(bits,'original_sample',sample=sample,index=i)
 return rows
class Oracle:
 def __init__(self,tmp):
  self.libs={}
  for bits in (32,64):
   for observed in (False,True):
    path=tmp/f'{bits}-{observed}.dylib';subprocess.run(['clang','-O2','-std=c99',f'-DSCALAR_BITS={bits}']+(['-DOBSERVED']if observed else[])+['-dynamiclib',str(FOLDER/'ScalarReference.c'),'-o',str(path)],check=True)
    lib=C.CDLL(str(path));lib.sm_lt.restype=C.c_uint64;lib.sm_constant.restype=C.c_uint64;lib.sm_trace_word.restype=C.c_uint64;lib.sm_label.restype=C.c_char_p;self.libs[bits,observed]=lib
 def observe(self,r,observed):
  bits=r['bits'];n=9 if bits==32 else 5;w=C.c_uint32 if bits==32 else C.c_uint64;size=C.sizeof(w);lib=self.libs[bits,observed];op=r['op']
  if op=='constant':return dict(words=[hx(lib.sm_constant(r['group'],i))for i in range(n)])
  if op=='original_sample':
   result={}
   for key in ['a','b','c']:
    value=(w*n)();payload=(C.c_uint8*64)(*r['sample'][key]);out=(C.c_uint8*32)();lib.sm_expand(value,payload,C.c_size_t(64));lib.sm_contract(out,value);result[key]=list(out)
   assert result['a']==r['sample']['encoded']
   return result
  def storage(values=()):return(w*(n+3))(0x12345678,*[int(x,16)for x in values],*([0xa5a5a5a5]*(n-len(values))),0x76543210,0x88776655)
  def ptr(a):return C.cast(C.byref(a,size),C.POINTER(w))
  payload=r.get('bytes',[]);data=(C.c_uint8*336)(*([17]*8),*payload,*([165]*(320-len(payload))),*([34]*8));bp=C.cast(C.byref(data,8),C.POINTER(C.c_uint8))
  av=storage(r.get('a',[]));bv=storage(r.get('b',[]));ov=storage(r.get('out',[]));a=ptr(av);b=ptr(bv);out=ptr(ov);alias=r.get('alias','none')
  if alias=='byte-a':
   for i,v in enumerate(r['a']):
    for j,byte in enumerate(int(v,16).to_bytes(size,'little')):data[8+i*size+j]=byte
   a=C.cast(bp,C.POINTER(w))
  if alias=='byte-out':out=C.cast(bp,C.POINTER(w))
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  value=None
  if op=='lt':value=lib.sm_lt(C.c_uint64(int(r['left'],16)),C.c_uint64(int(r['right'],16)))
  elif op=='reduce':lib.sm_reduce(out)
  elif op in ['barrett','add','mul']:getattr(lib,'sm_'+op)(out,a,b)
  elif op=='sub_batch':lib.sm_sub_batch(out,a,b,C.c_size_t(r['size']))
  elif op in ['lt_batch','lte_batch']:value=getattr(lib,'sm_'+op)(a,b,C.c_size_t(r['size']))
  elif op in ['zero','one','128']:value=getattr(lib,'sm_'+op)(a)
  elif op=='expand':lib.sm_expand(out,bp,C.c_size_t(r['length']))
  elif op=='expand_raw':lib.sm_expand_raw(out,bp)
  elif op in ['contract','window4']:getattr(lib,'sm_'+op)(bp,a)
  elif op=='sliding':lib.sm_sliding(bp,a,C.c_int(r['window']))
  else:raise ValueError(op)
  def values(p):return[hx(p[i])for i in range(-1,n+2)]
  result=dict(a=values(a),b=values(b),out=values(out),bytes=list(data))
  if value is not None:result['value']=hx(value)
  if r.get('trace')and observed:result['trace']=[dict(label=lib.sm_label(i).decode(),limbs=[hx(lib.sm_trace_word(i,j))for j in range(n)])for i in range(lib.sm_count())]
  return result
 def both(self,r):
  p=self.observe(r,False);o=self.observe(r,True);assert p=={k:v for k,v in o.items()if k!='trace'},r;return o

def sources():
 spec=importlib.util.spec_from_file_location('ex',ROOT/'tools/extract-edwards25519-scalar-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 for p,data in ex.sources().items():assert p.read_bytes()==data,p
 # Authenticate every actual local package byte against its immutable source map,
 # not just its Cabal version. Selected direct dependency identities are also JUnit pins.
 for package,archive in [('crypton-2.0.1','9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'),('ram-0.22.1','72474962349dd101b970cc46bcc06c799f7311d228e7b59f1a44f81427159f70')]:
  folder=ROOT/'vendor'/package;metadata=json.loads((folder/'SOURCE.json').read_text());assert metadata['sha256']==archive
  for name,expected in metadata['files'].items():assert hashlib.sha256((folder/name).read_bytes()).hexdigest()==expected,(package,name)
 assert (ROOT/'licenses/ram-BSD-3-Clause.txt').read_bytes()==(ROOT/'vendor/ram-0.22.1/LICENSE').read_bytes()
 files=[ROOT/'vendor/crypton-2.0.1'/name for name in ['SOURCE.json','tests/ECC/Edwards25519Spec.hs','Crypto/ECC/Edwards25519.hs','tests/Imports.hs','tests/Utils.hs','Crypto/Internal/ByteArray.hs','crypton.cabal']]+[ROOT/'vendor/ram-0.22.1/SOURCE.json',ROOT/'vendor/ram-0.22.1/LICENSE',ROOT/'licenses/ram-BSD-3-Clause.txt',ROOT/'licenses/ed25519-donna-public-domain.txt',Path(__file__).resolve(),ROOT/'tools/extract-edwards25519-scalar-reference.py']+[p for p in FOLDER.rglob('*')if p.is_file()and'work-in-progress'not in p.parts]
 return{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(set(files))}
def digest(rows):return hashlib.sha256(''.join(json.dumps(r,sort_keys=True,separators=(',',':'))for r in rows).encode()).hexdigest()
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
 if not(args.check_source or args.verify_oracle):
  assert args.oracle
  sampleText=subprocess.check_output([args.oracle],text=True);samples=json.loads(sampleText);(FOLDER/'OriginalSamples.json').write_text(sampleText)
  suite=subprocess.check_output([args.oracle,'--original','--match','scalar arithmetic','--seed','1729','--qc-max-success','100'],text=True);assert'10 examples, 0 failures'in suite;s=(FOLDER/'OriginalScalarSuite.txt').write_text(suite)
 else:samples=json.loads((FOLDER/'OriginalSamples.json').read_text())
 pins=sources()
 if args.check_source or args.verify_oracle:
  with gzip.open(TARGET,'rt')as f:m=json.loads(next(f));rows=[json.loads(l)for l in f]
  assert m['sources']==pins and m['caseHash']==digest(rows)and m['cases']==len(rows)
  assert[r['request']for r in rows]==[r['request']for r in inputs(samples)]
  if args.verify_oracle:assert m['compiler']==subprocess.check_output(['clang','--version'],text=True).splitlines()[0]
 else:rows=inputs(samples)
 if not args.check_source or args.verify_oracle:
  with tempfile.TemporaryDirectory(prefix='kotdoc-modm-')as tmp:
   oracle=Oracle(Path(tmp))
   for row in rows:
    value=oracle.both(row['request'])
    if'expected'in row:assert row['expected']==value,row['request']
    else:row['expected']=value
  if args.verify_oracle and args.oracle:
   assert json.loads(subprocess.check_output([args.oracle],text=True))==samples
   assert'10 examples, 0 failures'in subprocess.check_output([args.oracle,'--original','--match','scalar arithmetic','--seed','1729','--qc-max-success','100'],text=True)
 if not(args.check_source or args.verify_oracle):
  m=dict(cases=len(rows),caseHash=digest(rows),sources=pins,compiler=subprocess.check_output(['clang','--version'],text=True).splitlines()[0],helpers=OPS,originalScalarProperties=9,originalScalarLiterals=1,originalSamples=len(samples),limits='Both nativealgorithmprofiles on64host, not actual32OSABI. limb MSBlt andsingle reduce are bounded-source algorithms, not genericunsigned comparison/modulo. Rawfullword/unrelatedBarrett factors/subBatch a<b preserve bitvectors without mathematicalguarantee. windows1..8 finite signedbyte range; invalidsignedshift windows excluded. expandlen<=64 only; largerlen causes originalstack overwrite. Byte-word aliases use aligned8nativeoffsets and explicit sharedbyte storage; no physicalmemory/timing/erasure guarantee. Scalar32payload36bytes, Haskell40-byte allocationpadding is notvalue.')
  TARGET.parent.mkdir(parents=True,exist_ok=True)
  with TARGET.open('wb')as raw:
   with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0)as f:f.write(('\n'.join(json.dumps(r,separators=(',',':'))for r in[m]+rows)+'\n').encode())
 print('Verified'if args.check_source or args.verify_oracle else'Generated',len(rows),'modm cases;',len(pins),'pins')
