#!/usr/bin/env python3
"""Independent complete Donna C observations, with separately verified trace observers."""
import argparse,re,ctypes as C,gzip,hashlib,importlib.util,json,random,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/x25519-reference';TARGET=ROOT/'core/src/test/resources/crypto-x25519/data.jsonl.gz';MASK=(1<<64)-1
hx=lambda x:format(x&MASK,'016x')
def inputs():
 rng=random.Random(25519256311);rows=[]
 def add(bits,op,**kw):rows.append(dict(request=dict(bits=bits,op=op,**kw)))
 # Verbatim Curve25519Spec values; source-pin and four assertions additionally checked below.
 alice='77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a';bob='5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb'
 ap='8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a';bp='de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f';base='09'+'00'*31
 literals=re.findall(r'"((?:\\x[0-9a-f]{2})+)"',(ROOT/'vendor/crypton-2.0.1/tests/Curve25519Spec.hs').read_text())
 assert [v.replace('\\x','')for v in literals]==[alice,ap,bob,bp,'4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742']
 for bits in (32,64):
  n=10 if bits==32 else 5;w=25 if bits==32 else 51
  vec=[[0]*n,[1]*n,[(1<<w)-1]*n]+[[rng.randrange(-(1<<24),1<<24)if bits==32 else rng.randrange(1<<51)for _ in range(n)]for _ in range(30)]
  for i,a in enumerate(vec):
   for alias in ('none','same'):
    for bit in (0,1):add(bits,'swap',a=list(map(hx,a)),b=list(map(hx,vec[(i+1)%len(vec)])),alias=alias,bit=hx(bit))
   for alias in ('none','out-a'):add(bits,'reciprocal',a=list(map(hx,a)),alias=alias,trace=i<5)
   add(bits,'monty',vectors=[list(map(hx,vec[(i+j)%len(vec)]))for j in range(5)])
  for i in range(40):
   scalar=rng.randbytes(32).hex();q=rng.randbytes(32).hex()
   for alias in ('none','out-q','x-z'):add(bits,'multiply',secret=scalar,point=q,alias=alias,trace=i<3)
  secrets=[bytes(32).hex(),('ff'*32),alice,bob]+[rng.randbytes(32).hex()for _ in range(50)]
  points=[bytes(32).hex(),(1).to_bytes(32,'little').hex(),base,ap,bp]+[(v).to_bytes(32,'little').hex()for v in [(1<<255)-20,(1<<255)-19,(1<<255)-18,(1<<255)-1,1<<255,(1<<256)-1]]
  for i,s in enumerate(secrets):
   for j,q in enumerate(points if i<8 else [rng.randbytes(32).hex()]):
    for alias in ('none','out-secret','out-point','all'):
     add(bits,'donna',secret=s,point=q,alias=alias,trace=i<2 and j<3 and alias=='none')
  for name,s,q,expected in [('0',bob,ap,'4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742'),('1',alice,bp,'4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742'),('2',alice,base,ap),('3',bob,base,bp)]:
   add(bits,'donna',secret=s,point=q,alias='none',trace=True,kat=name,katExpected=expected)
 return rows
class Oracle:
 def __init__(self,folder):
  self.libs={}
  for bits in (32,64):
   for observed in (False,True):
    p=folder/f'{bits}-{observed}.dylib';subprocess.run(['clang','-O2','-std=c99',f'-DFIELD_BITS={bits}']+(['-DOBSERVED']if observed else[])+['-dynamiclib',str(FOLDER/'Reference.c'),'-o',str(p)],check=True)
    lib=C.CDLL(str(p));lib.x25_round_data.restype=C.POINTER(C.c_uint64);lib.x25_inverse_data.restype=C.POINTER(C.c_uint64);lib.x25_inverse_label.restype=C.c_char_p;self.libs[bits,observed]=lib
 def observe(self,r,observed):
  bits=r['bits'];n=10 if bits==32 else 5;lib=self.libs[bits,observed];op=r['op']
  def storage(v=()):return(C.c_uint64*22)(0x0123456789abcdef,*[int(x,16)for x in v],*([0xa5a5a5a5a5a5a5a5]*(19-len(v))),0xfedcba9876543210,0x8877665544332211)
  def ptr(a):return C.cast(C.byref(a,8),C.POINTER(C.c_uint64))
  def vals(a):return list(map(hx,a))
  def bytebuf(s):return(C.c_uint8*39)(17,34,51,*bytes.fromhex(s),68,85,102,119)
  def bp(a):return C.cast(C.byref(a,3),C.POINTER(C.c_uint8))
  a=storage(r.get('a',[]));b=storage(r.get('b',[]));out=storage();z=storage();alias=r.get('alias','none')
  result={}
  if op=='swap':
   if alias=='same':b=a
   lib.x25_swap(ptr(a),ptr(b),C.c_uint64(int(r['bit'],16)));result=dict(a=vals(a),b=vals(b))
  elif op=='reciprocal':
   if alias=='out-a':out=a
   lib.x25_recip(ptr(out),ptr(a));result=dict(out=vals(out),a=vals(a))
  elif op=='monty':
   v=[storage(x)for x in r['vectors']];outs=[storage()for _ in range(4)]
   lib.x25_monty(*[ptr(x)for x in outs+v]);result=dict(vectors=[vals(x)for x in v],out=[vals(x)for x in outs])
  elif op=='multiply':
   sec=bytebuf(r['secret']);point=bytebuf(r['point']);q=storage();lib.x25_expand(ptr(q),bp(point))
   if alias=='out-q':out=q
   if alias=='x-z':z=out
   lib.x25_cmult(ptr(out),ptr(z),bp(sec),ptr(q));result=dict(out=vals(out),z=vals(z),q=vals(q),secret=list(sec),point=list(point))
  elif op=='donna':
   sec=bytebuf(r['secret']);point=bytebuf(r['point']);dst=bytebuf('a5'*32)
   if alias=='all':point=sec;dst=sec
   elif alias=='out-secret':dst=sec
   elif alias=='out-point':dst=point
   lib.x25_donna(bp(dst),bp(sec),bp(point));result=dict(out=list(dst),secret=list(sec),point=list(point))
   if 'katExpected'in r:assert bytes(dst[3:35]).hex()==r['katExpected'],r
  else:raise ValueError(op)
  if observed and r.get('trace'):
   if op in ('multiply','donna'):
    count=lib.x25_round_count();assert count==256;p=lib.x25_round_data();result['rounds']=[[[hx(p[(i*4+k)*n+j])for j in range(n)]for k in range(4)]for i in range(count)]
   if op in ('reciprocal','donna'):
    count=lib.x25_inverse_count();p=lib.x25_inverse_data();result['inverse']=[dict(label=lib.x25_inverse_label(i).decode(),limbs=[hx(p[i*n+j])for j in range(n)])for i in range(count)]
    sq=sum(int(v['label'].split(':')[0][6:]or'1')for v in result['inverse']if v['label'].startswith('square'));mul=sum(v['label'].startswith('mul')for v in result['inverse']);assert(sq,mul)==(254,11),(bits,sq,mul)
  return result
 def both(self,r):
  raw=self.observe(r,False);obs=self.observe(r,True);assert raw=={k:v for k,v in obs.items()if k not in ('rounds','inverse')},r
  return obs
def sources():
 spec=importlib.util.spec_from_file_location('ex',ROOT/'tools/extract-x25519-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 files=[Path(__file__).resolve(),ROOT/'tools/extract-x25519-reference.py',ROOT/'vendor/crypton-2.0.1/SOURCE.json',ROOT/'vendor/crypton-2.0.1/tests/Curve25519Spec.hs']
 meta=json.loads(files[2].read_text());assert meta['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 for bits in (32,64):
  original,observed=ex.extract(bits)
  assert(FOLDER/f'Original{bits}.h').read_text()==original and(FOLDER/f'Observed{bits}.h').read_text()==observed
  p=ROOT/f'vendor/crypton-2.0.1/cbits/curve25519/curve25519-donna{"-c64"if bits==64 else""}.c';assert hashlib.sha256(p.read_bytes()).hexdigest()==meta['files'][str(p.relative_to(ROOT/'vendor/crypton-2.0.1'))];files.append(p)
 files += [p for p in FOLDER.glob('*')if p.is_file()]
 return{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(files)}
def digest(rows):return hashlib.sha256(''.join(json.dumps(r,sort_keys=True,separators=(',',':'))for r in rows).encode()).hexdigest()
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args();pins=sources()
 if args.check_source or args.verify_oracle:
  with gzip.open(TARGET,'rt')as f:manifest=json.loads(next(f));rows=[json.loads(l)for l in f]
  assert manifest['sources']==pins and manifest['cases']==len(rows)and manifest['caseHash']==digest(rows)
  if args.verify_oracle:assert manifest['compiler']==subprocess.check_output(['clang','--version'],text=True).splitlines()[0]
  assert[r['request']for r in rows]==[r['request']for r in inputs()]
 else:rows=inputs()
 if not args.check_source or args.verify_oracle:
  with tempfile.TemporaryDirectory(prefix='kotdoc-x25519-')as folder:
   oracle=Oracle(Path(folder))
   for row in rows:
    expected=oracle.both(row['request'])
    if'expected'in row:assert row['expected']==expected,row['request']
    else:row['expected']=expected
 if not(args.check_source or args.verify_oracle):
  manifest=dict(cases=len(rows),caseHash=digest(rows),sources=pins,compiler=subprocess.check_output(['clang','--version'],text=True).splitlines()[0],limits='Both algorithms run on native64 host, not actual32 OS ABI. Signed32 profile C left shifts have ISO-C UB; selected clang bitvector observations only. Swap bit must be0/1, reduced32 signed limbs; monty disjoint source slots, bounded inputs. No timing or hardware erasure proof. Public Haskell/modern adapters pending.',rounds=256,inversion={'squares':254,'multiplications':11})
  TARGET.parent.mkdir(parents=True,exist_ok=True)
  with TARGET.open('wb')as raw:
   with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0)as f:f.write(('\n'.join(json.dumps(r,separators=(',',':'))for r in[manifest]+rows)+'\n').encode())
 print('Verified'if args.check_source or args.verify_oracle else'Generated',len(rows),'raw Donna cases;',len(pins),'pins; native replay',args.verify_oracle)
