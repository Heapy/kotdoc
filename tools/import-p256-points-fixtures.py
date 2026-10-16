#!/usr/bin/env python3
"""Independent both-profile point source, helpers, selectors and all actual C exports."""
import argparse,collections,gzip,hashlib,importlib.util,json,random,subprocess,sys,tempfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/p256-points-reference';TARGET=ROOT/'core/src/test/resources/crypto-p256-points/data.jsonl.gz'
P=int('ffffffff00000001000000000000000000000000ffffffffffffffffffffffff',16);N=int('ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551',16);R=1<<257
GX=int('6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296',16);GY=int('4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5',16)
ORIGINAL_POINTS=[(GX,GY),(int('de2444bebc8d36e682edd27e0f271508617519b3221a8fa0b77cab3989da97c9',16),int('c093ae7ff36e5380fc01a5aad1e66659702de80f53cec576b6350b243042a256',16)),(int('55a8b00f8da1d44e62f6b3b25316212e39540dc861c89575bb8cf92e35e0986b',16),int('5421c3209c2d6c704835d82ac4c3dd90f61a8a52598b9e7ab656e9d8c8b24316',16)),(int('72b13dd4354b6b81745195e98cc5ba6970349191ac476bd4553cf35a545a067e',16),int('8d585cbb2e1327d75241a8a122d7620dc33b13315aa5c9d46d013011744ac264',16)),(1<<96,int('7d12de58d54423eb85ae8d157ae416fb004a7eb522ac1b67047ef3cdf9acdc3f',16)),(int('8000003ffffff0000007fffffe000000ffffffc000001ffffff8000003fffffc',16),int('0c3527bd081c1c07b313bc1a0c3f845fb2fe22557699ccc8f1354e61a27b7f88',16)),(0,int('66485c780e2f83d72433bd5d84a06bb6541c2af31dae871728bf856a174f93f4',16)),(0,int('99b7a386f1d07c29dbcc42a27b5f9449abe3d50de25178e8d7407a95e8b06c0b',16)),(int('09e78d4ef60d05f750f6636209092bc43cbdd6b47e11a9de20a9feb2a50bb96c',16),1),(int('8d0177ebab9c6e9e10db6dd095dbac0d6375e8a97b70f611875d877f0069d2c7',16),1),(int('6916fac45e568b6b9e2e2ecd611b282e5fcc40a3067d601057f879ce5a8a73cc',16),1)]

def plus(a,b):
 if a is None:return b
 if b is None:return a
 x,y=a;u,v=b
 if x==u and (y+v)%P==0:return None
 slope=((3*x*x-3)*pow(2*y,-1,P) if a==b else (v-y)*pow(u-x,-1,P))%P
 outx=(slope*slope-x-u)%P;return outx,(slope*(x-outx)-y)%P

def times(k,p):
 out=None
 while k:
  if k&1:out=plus(out,p)
  p=plus(p,p);k>>=1
 return out

def inputs():
 cases=[];rng=random.Random(0xEC2562026)
 for bits in [32,64]:
  n=9 if bits==32 else 5;wn=256//bits;widths=[(29 if i%2==0 else 28) if bits==32 else (51 if i%2==0 else 52) for i in range(n)];lm=(1<<bits)-1
  def word(x):return [(x>>(bits*i))&lm for i in range(wn)]
  def split(x):
   out=[]
   for w in widths:out.append(x&((1<<w)-1));x>>=w
   return out
  def mont(x):return split(x*R%P)
  def jac(p,z=1):
   if p is None:return [mont(0),mont(0),mont(0)]
   x,y=p;return [mont(x*z*z%P),mont(y*z*z*z%P),mont(z)]
  def add(op,a=None,b=None,k=0,l=0,p=None,q=None,alias=0,index=0,mask=0,limit=0,group='raw-helpers',math=None):
   a=a or jac((GX,GY));b=b or jac(ORIGINAL_POINTS[1]);p=p or (GX,GY);q=q or ORIGINAL_POINTS[1]
   cases.append(dict(group=group,request=dict(bits=bits,op=op,fields=a+b,words=[word(k),word(l),word(p[0]),word(p[1]),word(q[0]),word(q[1])],alias=alias,index=index,mask=str(mask),limit=limit),math=math,mathDomain='independent-affine-supplement' if math else 'raw-source-parity-only'))
  for x in [0,1,2,P-1,P,2*P,R-1]+[rng.randrange(P) for _ in range(12)]:
   for alias in [0,1]:add('inv',a=[split(x),mont(0),mont(1)],alias=alias,limit=12,group='exact-fixed-inversion-chain')
  raw=[jac(None),jac((GX,GY)),jac((GX,GY),2),jac((GX,GY),P-1)]
  raw += [jac(p,z) for p,z in zip(ORIGINAL_POINTS,[1,2,3,7,11,13,17,19,23,29,31])]
  raw += [[[rng.randrange(1<<(w+1)) for w in widths] for _ in range(3)] for _ in range(8)]
  for i,a in enumerate(raw):
   for b in [raw[(i+1)%len(raw)],a,jac((GX,(-GY)%P)),jac(None)]:
    for op in ['double','mixed','add','adddouble','affine']:
     for alias in [0,1,2,3,4]:add(op,a=a,b=b,alias=alias,limit=4,group='raw-formula-domain-and-coordinate-aliases')
  for index in range(32):
   for alias in [0,1,3]:add('selectaffine',index=index,alias=alias,limit=4,group='all-affine-table-selections-and-read-schedules')
  for index in range(16):
   for alias in [0,1,3]:add('selectjacobian',index=index,alias=alias,limit=4,group='all-jacobian-table-selections-and-read-schedules')
  for mask in [0,lm,1,lm>>1]:
   for alias in [0,1,2,3,4]:add('copy',mask=mask,alias=alias,limit=4,group='masked-copy-raw-word-semantics')
  scalars=sorted(set([0,1,2,3,15,16,17,255,256,257,N-1,N,N+1,(1<<256)-1]+[1<<i for i in range(256)]+[rng.randrange(N) for _ in range(12)]))
  for k in scalars:
   add('base',k=k,group='every-scalar-bit-base-export',math=['base',str(k)])
  for lo,hi in [(1,100),(101,N-101),(N-100,N-1),(N+1,N+100),(N+101,(1<<256)-101),((1<<256)-100,(1<<256)-1)]:
   for k in [lo,hi,rng.randint(lo,hi)]:
    for op in ['base','pointmul']:add(op,k=k,p=ORIGINAL_POINTS[3],group='original-full-256bit-scalar-generator-six-intervals',math=['base',str(k)] if op=='base' else ['mul',str(k),*map(str,ORIGINAL_POINTS[3])])
  for k in range(256):
   add('pointmul',k=k,p=ORIGINAL_POINTS[1],group='every-byte-nonbase-export',math=['mul',str(k),*map(str,ORIGINAL_POINTS[1])])
  for k in [0,1,2,15,16,N-1,N,N+1,(1<<256)-1]:
   for alias in [0,1,2,3,4]:
    add('base',k=k,alias=alias,limit=4,group='base-export-scalar-edges-and-output-aliases',math=['base',str(k)] if alias<3 else None)
    add('pointmul',k=k,p=ORIGINAL_POINTS[2],alias=alias,limit=4,group='point-export-scalar-edges-and-output-aliases',math=['mul',str(k),*map(str,ORIGINAL_POINTS[2])] if alias<3 else None)
   for op in ['baseraw','scalarraw']:add(op,k=k,group='raw-projective-scalar-results-and-precomputation',limit=4)
  add('pointadd',p=ORIGINAL_POINTS[1],q=ORIGINAL_POINTS[2],group='original-P256Spec-point-add-literal',math=['add',*map(str,ORIGINAL_POINTS[1]),*map(str,ORIGINAL_POINTS[2])])
  for p in ORIGINAL_POINTS:
   for q in [p,(p[0],(-p[1])%P),ORIGINAL_POINTS[2],(0,0)]:
    for alias in [0,1,2,3,4]:add('pointadd',p=p,q=q,alias=alias,limit=4,group='raw-public-add-equality-inverse-infinity')
   for alias in [0,2,3,4]:add('negate',p=p,alias=alias,limit=4,group='raw-negation-and-cross-coordinate-aliases')
  for p in [(0,0),(0,P),(P,P),(P-1,0)]:
   for op in ['pointadd','negate','pointmul']:add(op,p=p,k=0,group='unchecked-coordinate-and-encoded-infinity-quirks',limit=4)
  for k,l in [(0,0),(0,1),(1,0),(1,1),(1,N-1),(2,2),(N-1,1),(N,1),(N+1,N-1)]+[(rng.randrange(N),rng.randrange(N)) for _ in range(12)]:
   for alias in [0,1,3]:add('points',k=k,l=l,p=(GX,GY),alias=alias,group='double-scalar-branches-equality-cancellation',limit=4,math=['points',str(k),str(l),str(GX),str(GY)] if alias<3 and k<N and l<N else None)
 return cases

def source_hashes():
 d=ROOT/'vendor/crypton-2.0.1';pin=json.loads((d/'SOURCE.json').read_text());assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 paths=['cbits/p256/p256.c','cbits/p256/p256_ec.c','cbits/include32/p256/p256.h','cbits/include64/p256/p256.h','cbits/include32/p256/p256_gf.h','cbits/include64/p256/p256_gf.h','Crypto/PubKey/ECC/P256.hs','Crypto/ECC.hs','tests/PubKey/P256Spec.hs','tests/ECCSpec.hs']
 for p in paths:assert hashlib.sha256((d/p).read_bytes()).hexdigest()==pin['files'][p]
 spec=importlib.util.spec_from_file_location('extract',ROOT/'tools/extract-p256-points-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 assert (FOLDER/'ObservedPoints.c').read_text()==ex.observed()
 files=[d/p for p in paths]+[d/'SOURCE.json',ROOT/'licenses/crypton-p256-BSD-3-Clause.txt',Path(__file__).resolve(),ROOT/'tools/extract-p256-points-reference.py',ROOT/'tools/p256-words-reference/ObservedP256.c',ROOT/'tools/p256-field-reference/ObservedField32.h',ROOT/'tools/p256-field-reference/ObservedField64.h']+[f for f in FOLDER.glob('*') if f.is_file()]
 return {str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(files))}
def line(r):
 return ' '.join([r['op'],str(r['alias']),str(r['index']),str(r['limit']),r['mask']]+[','.join(f'{x:x}' for x in a) for a in r['fields']+r['words']])+'\n'
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def math_check(c,o):
 if not c['math']:return
 kind,*args=c['math'];args=list(map(int,args));r=c['request'];bits=r['bits']
 if kind=='add':p=plus((args[0],args[1]),(args[2],args[3]))
 elif kind=='base':p=times(args[0],(GX,GY))
 elif kind=='mul':p=times(args[0],(args[1],args[2]))
 else:p=plus(times(args[0],(GX,GY)),times(args[1],(args[2],args[3])))
 x,y=(6,7) if r['alias']==0 else (2,3) if r['alias']==1 else (4,5)
 actual=tuple(sum(int(v)<<(bits*i) for i,v in enumerate(o['words'][index])) for index in [x,y])
 assert actual==(p or (0,0)),(c,actual,p)
LIMITS=dict(rawParity='every observation on both captured digit profiles',mathSupplement='explicit per-case mathDomain and math operands',
 scalarSourcePrecondition='scalar_base_mult/scalar_mult/exports document scalar<n; above-order inputs retain raw observations only unless explicitly supplemented',
 selectorSourcePrecondition='indices0..15; affine table has omitted zero entry; jacobian table has explicit zero entry',
 knownOutsideScalarDomain='points_mul_vartime(n,1,G) observed(0,0) rather than G; preserve source output, public wrapper contract remains pending',
 excluded=['invalid pointers','undersized buffers','partial overlaps','selector table/output overlaps','negate memcpy exact self-alias'],
 profileClaim='both digit algorithms on captured host; not a real32bit OS ABI',timingClaim=False)

def replay(cs):
 with tempfile.TemporaryDirectory(prefix='kotdoc-p256-points-') as tmp:
  for bits in [32,64]:
   subset=[c for c in cs if c['request']['bits']==bits];results=[]
   for observed in [False,True]:
    binary=str(Path(tmp)/f'points-{bits}-{observed}')
    subprocess.run(['cc','-std=c11','-O2',*(['-DOBSERVED'] if observed else []),f'-I{ROOT}/vendor/crypton-2.0.1/cbits/include{bits}',str(FOLDER/'PointsReference.c'),'-o',binary],check=True)
    p=subprocess.run([binary],input=''.join(line(c['request']) for c in subset),text=True,capture_output=True,check=True,timeout=240)
    result=[json.loads(s) for s in p.stdout.splitlines()];assert len(result)==len(subset);results.append(result)
   for c,raw,observed in zip(subset,*results):
    assert raw=={k:observed[k] for k in raw},(c,raw,observed)
    assert raw['guards'];math_check(c,observed)
    if 'expected' in c:assert c['expected']==observed,(c,observed)
    else:c['expected']=observed
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();hashes=source_hashes()
 if a.check_source or a.verify_oracle:
  with gzip.open(TARGET,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
  assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
  assert [{k:v for k,v in c.items() if k!='expected'} for c in cs]==inputs()
  assert m['limits']==LIMITS and m['groups']==dict(collections.Counter(c['group'] for c in cs))
  if a.verify_oracle:replay(cs)
  print('Verified',len(cs),'point observations,',len(hashes),'source hashes; replay =',a.verify_oracle)
 else:
  cs=inputs();replay(cs);m=dict(cases=len(cs),sources=hashes,caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),limits=LIMITS)
  with TARGET.open('wb') as raw:
   with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0) as f:f.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cs)+'\n').encode())
  print('Generated',len(cs),'point observations; SHA256',m['caseHash'])
