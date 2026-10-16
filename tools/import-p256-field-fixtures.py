#!/usr/bin/env python3
"""Independent complete raw-limb P256 field observations for both pinned headers."""
import argparse,collections,gzip,hashlib,importlib.util,json,random,re,subprocess,sys,tempfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/p256-field-reference';TARGET=ROOT/'core/src/test/resources/crypto-p256-field/data.jsonl.gz'
P=int('ffffffff00000001000000000000000000000000ffffffffffffffffffffffff',16);R=1<<257

def inputs():
 rng=random.Random(0xF1E1D256);cases=[]
 for bits in [32,64]:
  n=9 if bits==32 else 5;tn=2*n-1;widths=[(29 if i%2==0 else 28) if bits==32 else (51 if i%2==0 else 52) for i in range(n)];wordn=256//bits
  def split(value):
   out=[]
   for w in widths:out.append(value&((1<<w)-1));value>>=w
   return out
  def add(op,a=None,b=None,alias=0,carry=0,word=None,low=None,high=None,group='range-edges'):
   cases.append(dict(group=group,request=dict(bits=bits,op=op,a=a or [0]*n,b=b or [0]*n,word=word or [0]*wordn,low=low or [0]*tn,high=high or [0]*tn,alias=alias,carry=str(carry))))
  for op in ['one','zeroConst','p','2p','precomputed','r','rInv','zeroOffset']:add(op,group='all-constants-and-table-limbs')
  values=[split(x) for x in [0,1,2,P-1,P,P+1,2*P-1,2*P,2*P+1,R-1]]
  for i,w in enumerate(widths):
   for v in [1,(1<<w)-1,1<<w,(1<<(w+1))-1]:
    a=[0]*n;a[i]=v;values.append(a)
  values += [[(1<<w)-1 for w in widths],[(1<<(w+1))-1 for w in widths]]
  values += [[rng.randrange(1<<(w+1)) for w in widths] for _ in range(120)]
  for i,a in enumerate(values):
   b=values[(i*17+3)%len(values)]
   for op in ['sum','diff','square','mul']:
    for alias in [0,1,2]:add(op,a,b,alias=alias,group='documented-expanded-limbs-and-exact-aliases')
   for op in ['scalar3','scalar4','scalar8']:
    for alias in [1,2]:add(op,a,b,alias=alias,group='in-place-scalars')
   for alias in [0,2]:add('assign',a,b,alias=alias,group='disjoint-assign-and-b-output')
   add('zero',a,b,group='redundant-zero-and-normalization-passes')
   add('from',a,b,group='expanded-montgomery-decoding')
  for carry in range(8 if bits==32 else 64):
   for a in [[0]*n,[(1<<w)-1 for w in widths]]:
    add('carry',a,alias=1,carry=carry,group='every-in-range-carry')
  # Direct degree tests retain full raw temporaries, not merely their residue.
  limit=64 if bits==32 else 128
  for index in range(tn):
   for value in [1,(1<<(limit//2))-1,1<<(limit//2),(1<<limit)-1]:
    low=[0]*tn;high=[0]*tn;low[index]=value&((1<<64)-1);high[index]=value>>64
    add('degree',low=low,high=high,group='direct-raw-degree-boundaries')
  for _ in range(100):
   add('degree',low=[rng.getrandbits(64) for _ in range(tn)],high=[rng.getrandbits(64) if bits==64 else 0 for _ in range(tn)],group='direct-full-width-degree-observations')
  for value in [0,1,P-1,P,P+1,(1<<256)-1]+[rng.getrandbits(256) for _ in range(100)]:
   word=[(value>>(bits*i))&((1<<bits)-1) for i in range(wordn)]
   for alias in [0,1,2]:add('to',word=word,alias=alias,group='full-word-montgomery-encoding')
  # Documented no-overflow sums beyond ordinary field ranges: raw C observations only.
  for _ in range(50):
   a=[rng.getrandbits(bits-1) for _ in range(n)];b=[rng.getrandbits(bits-1) for _ in range(n)]
   for alias in [0,1,2]:add('sum',a,b,alias=alias,group='bounded-no-word-overflow-outside-field-range')
 return cases

def source_hashes():
 vendor=ROOT/'vendor/crypton-2.0.1';pin=json.loads((vendor/'SOURCE.json').read_text())
 assert pin['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 sources=['cbits/p256/p256.c','cbits/p256/p256_ec.c','cbits/include32/p256/p256.h','cbits/include64/p256/p256.h','cbits/include32/p256/p256_gf.h','cbits/include64/p256/p256_gf.h','tests/PubKey/P256Spec.hs']
 for p in sources:assert hashlib.sha256((vendor/p).read_bytes()).hexdigest()==pin['files'][p]
 spec=importlib.util.spec_from_file_location('field_extraction',ROOT/'tools/extract-p256-field-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 assert (ROOT/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoP256FieldTables.kt').read_text()==ex.tables()
 assert (ROOT/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoP256Field.kt').read_text().split('    // BEGIN PINNED EXPRESSIONS\n')[1]==ex.generated()+'}\n'
 for bits in [32,64]:assert (FOLDER/f'ObservedField{bits}.h').read_text()==ex.observed(bits)
 notice=ROOT/'licenses/crypton-p256-BSD-3-Clause.txt'
 for bits in [32,64]:assert notice.read_text()==ex.original(bits).split('*/',1)[0]+'*/\n'
 files=[vendor/p for p in sources]+[vendor/'SOURCE.json',notice,Path(__file__).resolve(),ROOT/'tools/extract-p256-field-reference.py',ROOT/'tools/p256-words-reference/ObservedP256.c',ROOT/'tools/extract-p256-words-reference.py']+[p for p in FOLDER.glob('*') if p.is_file()]
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}
def line(r):
 def seq(k):return ','.join(f'{x:x}' for x in r[k])
 return ' '.join([r['op'],str(r['alias']),r['carry'],seq('a'),seq('b'),seq('word'),seq('low'),seq('high')])+'\n'
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def math_check(c,o):
 r=c['request'];bits=r['bits'];op=r['op'];n=len(r['a']);widths=[(29 if i%2==0 else 28) if bits==32 else (51 if i%2==0 else 52) for i in range(n)]
 def value(a):
  out=0;shift=0
  for x,w in zip(a,widths):out+=int(x)<<shift;shift+=w
  return out
 if c['group'].startswith('direct-') or c['group']=='bounded-no-word-overflow-outside-field-range':return
 a=value(r['a']);b=value(r['b']);out=value(o[{0:'c',1:'a',2:'b'}[r['alias']]]);expected=None
 if op=='sum':expected=a+b
 elif op=='diff':expected=a-b
 elif op=='square':expected=a*a*pow(R,-1,P)
 elif op=='mul':expected=a*b*pow(R,-1,P)
 elif op.startswith('scalar'):expected=value(r['a'] if r['alias']==1 else r['b'])*int(op[-1])
 elif op=='carry':expected=a+int(r['carry'])*R
 elif op=='from':assert sum(int(v)<<(bits*i) for i,v in enumerate(o['word']))==a*pow(R,-1,P)%P
 elif op=='to':expected=sum(int(v)<<(bits*i) for i,v in enumerate(r['word']))*R
 elif op=='zero':assert o['ret']==int(a%P==0)
 if expected is not None:
  assert out%P==expected%P,(c,o,expected%P)
  # Record source output-range counterexamples separately; never rewrite native results.

def range_excess(cs):
 result=collections.Counter()
 for c in cs:
  r=c['request'];bits=r['bits'];op=r['op']
  if op not in ['carry','sum','diff','square','mul','scalar3','scalar4','scalar8']:continue
  widths=[(29 if i%2==0 else 28) if bits==32 else (51 if i%2==0 else 52) for i in range(len(r['a']))]
  out=c['expected'][{0:'c',1:'a',2:'b'}[r['alias']]]
  if any(int(x)>=1<<(w+1) for x,w in zip(out,widths)):result[f"{bits}:{op}:{c['group']}"]+=1
 return dict(result)

LIMITS=dict(representation={'32':[29,28,29,28,29,28,29,28,29],'64':[51,52,51,52,51]},montgomeryBits=257,
 excluded=['invalid pointers','undersized buffers','arbitrary partial overlaps','word/field type-punning','self-overlapping memcpy assignment'],
 rawOnlyGroups=['direct-raw-degree-boundaries','direct-full-width-degree-observations','bounded-no-word-overflow-outside-field-range'],
 profileClaim='digit algorithms on captured host, not a real 32-bit OS ABI',timingClaim=False)

def replay(cs):
 with tempfile.TemporaryDirectory(prefix='kotdoc-p256-field-') as tmp:
  for bits in [32,64]:
   binaries=[]
   for observed in [False,True]:
    path=str(Path(tmp)/f'field-{bits}-{observed}');binaries.append(path)
    subprocess.run(['cc','-std=c11','-O2',*(['-DOBSERVED'] if observed else []),f'-I{ROOT}/vendor/crypton-2.0.1/cbits/include{bits}',str(FOLDER/'FieldReference.c'),'-o',path],check=True)
   subset=[c for c in cs if c['request']['bits']==bits]
   def run(binary):
    p=subprocess.run([binary],input=''.join(line(c['request']) for c in subset),capture_output=True,text=True,timeout=120,check=True)
    os=[json.loads(s) for s in p.stdout.splitlines()];assert len(os)==len(subset);return os
   raw=run(binaries[0]);observed=run(binaries[1])
   for c,a,b in zip(subset,raw,observed):
    assert a=={k:b[k] for k in a},(c,a,b)
    assert a['guards'];math_check(c,b)
    if 'expected' in c:assert c['expected']==b,(c,b)
    else:c['expected']=b
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();hashes=source_hashes()
 if a.check_source or a.verify_oracle:
  with gzip.open(TARGET,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
  assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
  assert [{k:v for k,v in c.items() if k!='expected'} for c in cs]==inputs()
  assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
  assert m['rangeExcess']==range_excess(cs) and m['limits']==LIMITS
  if a.verify_oracle:replay(cs)
  print('Verified',len(cs),'field observations,',len(hashes),'source hashes; replay =',a.verify_oracle)
 else:
  cs=inputs();replay(cs);m=dict(cases=len(cs),sources=hashes,caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),rangeExcess=range_excess(cs),limits=LIMITS)
  with TARGET.open('wb') as raw:
   with gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0) as f:f.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cs)+'\n').encode())
  print('Generated',len(cs),'field observations; SHA256',m['caseHash'])
