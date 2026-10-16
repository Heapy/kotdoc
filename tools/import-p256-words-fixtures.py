#!/usr/bin/env python3
"""Independent pinned-C 32/64-bit word observations, exact replay and source verification."""
import argparse,collections,gzip,hashlib,importlib.util,json,random,subprocess,tempfile,sys
sys.dont_write_bytecode=True
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'tools/p256-words-reference'
TARGET=ROOT/'core/src/test/resources/crypto-p256-words/data.jsonl.gz'
N=int('ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551',16)
P=int('ffffffff00000001000000000000000000000000ffffffffffffffffffffffff',16)
HIGH=1<<256

def inputs():
 rng=random.Random(0x2562026);cases=[]
 def add(bits,op,a=0,b=0,m=N,top=0,mask=0,alias=0,shift=1,group='edges'):
  cases.append(dict(group=group,request=dict(bits=bits,op=op,a=f'{a:064x}',b=f'{b:064x}',m=f'{m:064x}',top=str(top),mask=str(mask),alias=alias,shift=shift)))
 values=sorted(set([0,1,2,3,99,100,101,N-101,N-100,N-1,N,N+1,N+100,P-1,P,P+1,HIGH-101,HIGH-100,HIGH-1,int('aa'*32,16),int('55'*32,16)]+[1<<i for i in range(256)]+[(1<<i)-1 for i in range(1,257)]))
 points=[
 ('55a8b00f8da1d44e62f6b3b25316212e39540dc861c89575bb8cf92e35e0986b','5421c3209c2d6c704835d82ac4c3dd90f61a8a52598b9e7ab656e9d8c8b24316'),
 ('de2444bebc8d36e682edd27e0f271508617519b3221a8fa0b77cab3989da97c9','c093ae7ff36e5380fc01a5aad1e66659702de80f53cec576b6350b243042a256'),
 ('72b13dd4354b6b81745195e98cc5ba6970349191ac476bd4553cf35a545a067e','8d585cbb2e1327d75241a8a122d7620dc33b13315aa5c9d46d013011744ac264'),
 ('0000000000000000000000000000000000000001000000000000000000000000','7d12de58d54423eb85ae8d157ae416fb004a7eb522ac1b67047ef3cdf9acdc3f'),
 ('8000003ffffff0000007fffffe000000ffffffc000001ffffff8000003fffffc','0c3527bd081c1c07b313bc1a0c3f845fb2fe22557699ccc8f1354e61a27b7f88'),
 ('0','66485c780e2f83d72433bd5d84a06bb6541c2af31dae871728bf856a174f93f4'),
 ('0','99b7a386f1d07c29dbcc42a27b5f9449abe3d50de25178e8d7407a95e8b06c0b'),
 ('09e78d4ef60d05f750f6636209092bc43cbdd6b47e11a9de20a9feb2a50bb96c','1'),
 ('8d0177ebab9c6e9e10db6dd095dbac0d6375e8a97b70f611875d877f0069d2c7','1'),
 ('6916fac45e568b6b9e2e2ecd611b282e5fcc40a3067d601057f879ce5a8a73cc','1')]
 for bits in [32,64]:
  limb=(1<<bits)-1
  for op in ['n','p','b','r2','one','init','clear']:
   for alias in range(4):add(bits,op,a=HIGH-1,b=HIGH-2,alias=alias,group='constants-and-clearing')
  for a in values:
   for op in ['zero','odd','even','from','to']:add(bits,op,a=a,group='bit-boundaries-and-marshalling')
   for index in [0,1,bits-1,bits,127,128,254,255]:add(bits,'bit',a=a,shift=index,group='every-limb-bit-boundaries')
  for index in range(256):add(bits,'bit',a=1<<index,shift=index,group='every-single-bit')
  for a in [0,1,HIGH-1,int('aa'*32,16)]:
   for shift in range(1,bits):
    for alias in [0,1]:
     for op in ['shl','shr']:add(bits,op,a=a,shift=shift,alias=alias,group='every-defined-shift-and-in-place')
   for shift in [bits+1,2*bits-1,2*bits+7,2147483647]:
    for op in ['shl','shr']:add(bits,op,a=a,shift=shift,alias=1,group='positive-shift-modulo-width')
   for shift in [0,1]:
    for alias in [0,1]:add(bits,'shr1',a=a,shift=shift,alias=alias,group='single-bit-high-carry')
  pairs=[(a,b) for a in values[:22]+values[-12:] for b in [0,1,N-1,N,P-1,HIGH-1]]
  pairs += [(rng.randrange(HIGH),rng.randrange(HIGH)) for _ in range(200)]
  # Preserve original P256Scalar's six intervals, each with independently seeded values.
  intervals=[(1,100),(101,N-101),(N-100,N-1),(N+1,N+100),(N+101,HIGH-101),(HIGH-100,HIGH-1)]
  pairs += [(rng.randint(lo,hi),rng.randint(lo2,hi2)) for lo,hi in intervals for lo2,hi2 in intervals for _ in range(4)]
  for i,(a,b) in enumerate(pairs):
   for alias in [0,1,2,4]:
    for op in ['add','sub','addd']:add(bits,op,a,b,alias=alias,top=b&limb,group='carry-borrow-null-and-aliases')
   add(bits,'cmp',a,b,group='comparisons')
   for m in [N,P]:
    for alias in [0,1,2,3]:
     for op in ['modadd','modsub','mul']:add(bits,op,a,b,m=m,alias=alias,group='full-width-masked-modular-and-aliases')
    add(bits,'mod',a,b,m=m,alias=i%4,group='single-subtraction-reduction')
   for op in ['muladd','subtop','subm','addm']:
    for mask in [0,limb,limb>>1,1]:add(bits,op,a,b,top=a&limb,mask=mask,alias=i%4,group='internal-helper-masks-and-top-wrap')
   if i<160:
    for alias in [0,1,2]:add(bits,'mont',a,b,alias=alias,group='montgomery-full-width-aliases')
   if i<80:
    for top in [1,limb,1<<(bits-1),1<<(bits//2)]:add(bits,'mul',a,b,top=top,group='modmul-nonzero-top')
  for a in values[:22]+values[-12:]+[rng.randrange(1,HIGH) for _ in range(100)]:
   for alias in [0,1]:
    add(bits,'safe',a,alias=alias,group='original-safe-inverse-including-zero-and-n')
    if a not in [0,N]:
     for m in [N,P]:
      if a%m:add(bits,'inv',a,m=m,alias=alias,shift=0,group='variable-inverse')
  # Intermediate states observed before iteration limit+1; zero is never assigned a finite inverse.
  for m in [0,1,2,4,6,9,N,P]:
   for a in [0,1,2,3,6,m]:
    for limit in [1,2,7,64,512]:add(bits,'inv',a,m=m,shift=limit,group='bounded-inverse-checkpoints')
  for x,y in points:
   x=int(x,16);y=int(y,16)
   add(bits,'valid',x,y,group='original-P256Spec-valid-point-literals')
   add(bits,'valid',x,y^1,group='invalid-neighbours-of-original-points')
  for x in [0,1,P-1,P,HIGH-1]:
   for y in [0,1,P-1,P,HIGH-1]:add(bits,'valid',x,y,group='validation-domain-rejections')
 return cases

def source_hashes():
 d=ROOT/'vendor/crypton-2.0.1';pin=json.loads((d/'SOURCE.json').read_text())
 assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 paths=['cbits/p256/p256.c','cbits/include32/p256/p256.h','cbits/include64/p256/p256.h','tests/PubKey/P256Spec.hs','Crypto/PubKey/ECC/P256.hs']
 for path in paths:assert hashlib.sha256((d/path).read_bytes()).hexdigest()==pin['files'][path],path
 spec=importlib.util.spec_from_file_location('extract',ROOT/'tools/extract-p256-words-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 assert (FOLDER/'ObservedP256.c').read_text()==ex.extract()
 notice=ROOT/'licenses/crypton-p256-BSD-3-Clause.txt'
 assert notice.read_text()==(d/'cbits/p256/p256.c').read_text().split('*/',1)[0]+'*/\n'
 files=[d/path for path in paths]+[d/'SOURCE.json',notice,Path(__file__).resolve(),ROOT/'tools/extract-p256-words-reference.py']+list(FOLDER.glob('*'))
 return {str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(files)) if f.is_file()}
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
def line(r):return ' '.join(str(r[k]) for k in ['op','alias','shift','a','b','m','top','mask'])+'\n'
def math_check(c,result):
 r=c['request'];op=r['op'];a=int(r['a'],16);b=int(r['b'],16);m=int(r['m'],16)
 if r['alias']==3 or result['stopped']:return
 out=result[{0:'c',1:'a',2:'b',4:'c'}[r['alias']]]
 value=int(out,16);expected=None
 if op=='modadd':expected=(a+b)%m
 elif op=='modsub':expected=(a-b)%m
 elif op=='mul':expected=a*(b+(int(r['top'])<<256))%m
 elif op=='mod':expected=a%m
 elif op=='safe':expected=pow(a,N-2,N)
 elif op=='inv' and c['group']!='bounded-inverse-checkpoints':expected=pow(a,-1,m)
 elif op=='mont':expected=a*b*pow(HIGH,-1,N)%N
 elif op=='valid':
  expected=int(a<P and b<P and b!=0 and (b*b-a*a*a+3*a-int('5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b',16))%P==0)
  assert int(result['ret'])==expected,(c,result)
 if expected is not None and op!='valid':assert value==expected,(c,result,expected)

def replay(cases):
 with tempfile.TemporaryDirectory(prefix='kotdoc-p256-') as tmp:
  for bits in [32,64]:
   subset=[c for c in cases if c['request']['bits']==bits]
   binaries=[]
   for observed in [False,True]:
    binary=str(Path(tmp)/f'p256-{bits}-{observed}');binaries.append(binary)
    subprocess.run(['cc','-std=c11','-O2',*(['-DOBSERVED'] if observed else []),f'-I{ROOT}/vendor/crypton-2.0.1/cbits/include{bits}',str(FOLDER/'WordsReference.c'),'-o',binary],check=True)
   def run(binary,cs):
    p=subprocess.run([binary],input=''.join(line(c['request']) for c in cs),text=True,capture_output=True,check=True,timeout=120)
    result=[json.loads(s) for s in p.stdout.splitlines()];assert len(result)==len(cs);return result
   results=run(binaries[1],subset)
   ordinary=[c for c in subset if c['group']!='bounded-inverse-checkpoints']
   original=run(binaries[0],ordinary)
   for c,result in zip(subset,results):
    if c['group']!='bounded-inverse-checkpoints':
     raw=original.pop(0);assert raw=={k:result[k] for k in raw},(c,raw,result)
    assert result['guards']
    math_check(c,result)
    if 'expected' in c:assert c['expected']==result,(c,result)
    else:c['expected']=result
   if bits==64:assert any(c['expected']['corrections'] for c in subset),'missing reducer correction witness'
   # Independent timeout establishes only nontermination within the budget, not a mathematical return.
   try:subprocess.run([binaries[0]],input=line(dict(op='inv',alias=0,shift=0,a='0'*64,b='0'*64,m=f'{N:064x}',top=0,mask=0)),text=True,stdout=subprocess.DEVNULL,timeout=.2,check=True)
   except subprocess.TimeoutExpired:pass
   else:raise AssertionError('original inverse(0) unexpectedly returned')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
 hashes=source_hashes()
 if args.check_source or args.verify_oracle:
  with gzip.open(TARGET,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(l) for l in f]
  assert manifest['sources']==hashes
  assert manifest['cases']==len(cases) and manifest['caseHash']==digest(cases)
  assert [{k:v for k,v in c.items() if k!='expected'} for c in cases]==inputs()
  assert manifest['groups']==dict(collections.Counter(c['group'] for c in cases))
  if args.verify_oracle:replay(cases)
  print('Verified',len(cases),'P256 word observations,',len(hashes),'source hashes; oracle replay =',args.verify_oracle)
 else:
  cases=inputs();replay(cases)
  manifest=dict(cases=len(cases),caseHash=digest(cases),sources=hashes,groups=dict(collections.Counter(c['group'] for c in cases)))
  with TARGET.open('wb') as raw:
   with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0) as f:f.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
  print('Generated',len(cases),'P256 word observations; SHA256',manifest['caseHash'])
