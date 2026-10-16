#!/usr/bin/env python3
"""Independent raw Edwards Donna field and tables from ordinary/observed original C."""
import argparse,collections,ctypes as C,gzip,hashlib,importlib.util,json,random,subprocess,sys,tempfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/edwards25519-field-reference';TARGET=ROOT/'core/src/test/resources/crypto-edwards25519-field/data.jsonl.gz'
hx=lambda x:format(x&((1<<64)-1),'016x')
BINARY=['add','add_after_basic','add_reduce','sub','sub_after_basic','sub_reduce','mul','mul_noinline']
UNARY=['copy','neg','square','square_times','recip','pow252']
TABLES=['ge25519_basepoint','ge25519_ecd','ge25519_ec2d','ge25519_sqrtneg1','ge25519_niels_sliding_multiples']
def inputs():
 rng=random.Random(2551915163);rows=[]
 def add(bits,op,**kw):rows.append(dict(request=dict(bits=bits,op=op,**kw)))
 for bits,n in [(32,10),(64,5)]:
  reduced=[[0]*n,[1]*n,[(1<<(26-i%2 if bits==32 else 51))-1 for i in range(n)]]
  reduced += [[rng.randrange(1<<(26-i%2 if bits==32 else 51))for i in range(n)]for _ in range(35)]
  raw=[[(1<<bits)-1]*n,[1<<(bits-1)]*n]+[[rng.getrandbits(bits)for _ in range(n)]for _ in range(8)]
  vec=reduced+raw
  for i,a in enumerate(vec):
   b=vec[(i*7+3)%len(vec)]
   for op in BINARY+UNARY:
    for alias in ['none','out-a','out-b','a-b','all']:
     for count in ([1,2,5,10]if op=='square_times'else[1]):
      add(bits,op,a=list(map(hx,a)),b=list(map(hx,b)),alias=alias,count=count,domain='reduced'if i<len(reduced)and(i*7+3)%len(vec)<len(reduced)else'raw-word',trace=op in ['recip','pow252']and i<5)
   add(bits,'pow250',a=list(map(hx,a)),trace=i<5)
   for flag in [0,1]:
    for alias in ['none','same']:add(bits,'swap',a=list(map(hx,a)),b=list(map(hx,b)),flag=hx(flag),alias=alias)
   add(bits,'contract',a=list(map(hx,a)))
  for i in range(100):
   data=list(rng.randbytes(96));add(bits,'expand',bytes=data)
   for flag in [0,1,2,(1<<bits)-1]:
    for alias in ['none','same']:add(bits,'move',bytes=data,other=list(rng.randbytes(96)),flag=hx(flag),alias=alias)
  p=(1<<255)-19
  for v in [0,1,p-1,p,p+1,(1<<255)-1,1<<255,(1<<256)-1]:
   data=list(v.to_bytes(32,'little'))+[165]*64;add(bits,'expand',bytes=data)
   for op in ['contract','recip','pow252','pow250']:
    digits=[];left=v&((1<<255)-1)
    for j in range(n):w=26-j%2 if bits==32 else 51;digits.append(left&((1<<w)-1));left>>=w
    add(bits,op,a=list(map(hx,digits)),trace=op in ['recip','pow252','pow250'])
  constants=['reduce_mask_25','reduce_mask_26','twoP0','twoP13579','twoP2468','fourP0','fourP13579','fourP2468']if bits==32 else['reduce_mask_40','reduce_mask_51','reduce_mask_56','twoP0','twoP1234','fourP0','fourP1234']
  for i,name in enumerate(constants):add(bits,'constant',index=i,name=name)
  for i,name in enumerate(TABLES):add(bits,'table',group=i,name=name)
  for i in range(256):add(bits,'packed',index=i)
  for a in reduced[:12]:add(bits,'operation_trace',a=list(map(hx,a)),b=list(map(hx,reduced[5])),steps=['add','sub_after_basic','mul','square_times','contract','expand'],count=5)
 return rows
class Oracle:
 def __init__(self,folder):
  self.libs={}
  for bits in (32,64):
   for observed in (False,True):
    path=folder/f'{bits}-{observed}.dylib';subprocess.run(['clang','-O2','-std=c99',f'-DFIELD_BITS={bits}']+(['-DOBSERVED']if observed else[])+['-dynamiclib',str(FOLDER/'FieldReference.c'),'-o',str(path)],check=True)
    lib=C.CDLL(str(path));lib.ef_trace_label.restype=C.c_char_p;lib.ef_trace_word.restype=C.c_uint64;lib.ef_constant.restype=C.c_uint64;lib.source_table_word.restype=C.c_uint64;self.libs[bits,observed]=lib
 def observe(self,r,observed):
  bits=r['bits'];n=10 if bits==32 else 5;word=C.c_uint32 if bits==32 else C.c_uint64;lib=self.libs[bits,observed];op=r['op'];mask=(1<<bits)-1
  if op=='operation_trace':
   step=dict(r);trace=[]
   for name in r['steps']:
    step['op']=name;state=self.observe(step,observed);trace.append(dict(op=name,state=state));step['a']=state['out'][1:1+n];step['out']=state['out'][1:1+n];step['bytes']=state['bytes'][8:104]
   return dict(trace=trace)
  if op=='constant':return dict(value=hx(lib.ef_constant(r['index'])))
  if op=='table':
   count=[4*n,n,n,n,32*3*n][r['group']];return dict(words=[hx(lib.source_table_word(r['group'],i))for i in range(count)])
  if op=='packed':return dict(bytes=[lib.source_packed_byte(r['index']*96+i)for i in range(96)])
  def storage(v=()):return(word*(n+3))(0x12345678,*[int(x,16)for x in v],*([0xa5a5a5a5&mask]*(n-len(v))),0x76543210,0x88776655)
  def ptr(a):return C.cast(C.byref(a,C.sizeof(word)),C.POINTER(word))
  def by(v=None):return(C.c_uint8*112)(*([17]*8),*(v if v is not None else[165]*96),*([34]*8))
  def bp(a):return C.cast(C.byref(a,8),C.POINTER(C.c_uint8))
  a=storage(r.get('a',[]));b=storage(r.get('b',[]));out=storage(r.get('out',[]));data=by(r.get('bytes'));other=by(r.get('other'));alias=r.get('alias','none')
  if alias in ['a-b','all']:b=a
  if alias in ['out-a','all']:out=a
  elif alias=='out-b':out=b
  if op in BINARY:getattr(lib,'ef_'+op)(ptr(out),ptr(a),ptr(b))
  elif op in ['copy','neg','square','recip','pow252']:getattr(lib,'ef_'+op)(ptr(out),ptr(a))
  elif op=='square_times':lib.ef_square_times(ptr(out),ptr(a),C.c_uint64(r['count']))
  elif op=='pow250':lib.ef_pow250(ptr(a))
  elif op=='swap':
   if alias=='same':b=a
   lib.ef_swap(ptr(a),ptr(b),C.c_uint64(int(r['flag'],16)))
  elif op=='expand':lib.ef_expand(ptr(out),bp(data))
  elif op=='contract':lib.ef_contract(bp(data),ptr(a))
  elif op=='move':
   if alias=='same':other=data
   lib.ef_move(bp(data),bp(other),C.c_uint64(int(r['flag'],16)))
  else:raise ValueError(op)
  result=dict(a=list(map(hx,a)),b=list(map(hx,b)),out=list(map(hx,out)),bytes=list(data),other=list(other))
  if r.get('trace')and observed:
   trace=[dict(label=lib.ef_trace_label(i).decode(),limbs=[hx(lib.ef_trace_word(i,j))for j in range(n)])for i in range(lib.ef_trace_count())]
   sq=sum(int(t['label'].split(':')[0][6:])for t in trace if t['label'].startswith('square'));mul=sum(t['label'].startswith('mul')for t in trace);assert(sq,mul)==({'pow250':(245,7),'recip':(254,11),'pow252':(251,11)}[op])
   result['powerTrace']=trace
  return result
 def both(self,r):
  plain=self.observe(r,False);traced=self.observe(r,True)
  def strip(v):
   if isinstance(v,dict):return{k:strip(x)for k,x in v.items()if k!='powerTrace'}
   if isinstance(v,list):return[strip(x)for x in v]
   return v
  assert plain==strip(traced),r
  return traced

def hashes():
 spec=importlib.util.spec_from_file_location('extract',ROOT/'tools/extract-edwards25519-field-reference.py');ex=importlib.util.module_from_spec(spec);spec.loader.exec_module(ex)
 for p,data in ex.prepared().items():assert p.read_bytes()==data,p
 files=[ROOT/'vendor/crypton-2.0.1/SOURCE.json',ROOT/'licenses/ed25519-donna-public-domain.txt',Path(__file__).resolve(),ROOT/'tools/extract-edwards25519-field-reference.py']+[p for p in FOLDER.rglob('*')if p.is_file()and'work-in-progress'not in p.parts]
 return{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(set(files))}
def digest(rows):return hashlib.sha256(''.join(json.dumps(r,sort_keys=True,separators=(',',':'))for r in rows).encode()).hexdigest()
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args();pins=hashes()
 if args.check_source or args.verify_oracle:
  with gzip.open(TARGET,'rt')as f:manifest=json.loads(next(f));rows=[json.loads(l)for l in f]
  assert manifest['sources']==pins and manifest['caseHash']==digest(rows)and manifest['cases']==len(rows)
  assert[r['request']for r in rows]==[r['request']for r in inputs()]
  if args.verify_oracle:assert manifest['compiler']==subprocess.check_output(['clang','--version'],text=True).splitlines()[0]
 else:rows=inputs()
 if not args.check_source or args.verify_oracle:
  with tempfile.TemporaryDirectory(prefix='kotdoc-edwards-field-')as tmp:
   oracle=Oracle(Path(tmp))
   for row in rows:
    expected=oracle.both(row['request'])
    if'expected'in row:assert row['expected']==expected,row['request']
    else:row['expected']=expected
 if not(args.check_source or args.verify_oracle):
  manifest=dict(cases=len(rows),caseHash=digest(rows),sources=pins,compiler=subprocess.check_output(['clang','--version'],text=True).splitlines()[0],functions={'32':15,'64':16,'powerHelpers':3},limits='Unsigned32/64 raw arithmetic. Raw-word observations outside source-produced redundant bounds have no mathematical reduction guarantee. square_times32 count<=0 eventually signed decrement UB;64count0 requires2^64iterations; finite positive counts only. Swap masks0/1 avoid signed-negationINT_MIN UB; move masks arbitrary unsigned bitvectors. Native byte storage offsets aligned8; original word-pointer casts retain C effective-type assumptions. No real32OS ABI/timing/physical erasure proof.')
  TARGET.parent.mkdir(parents=True,exist_ok=True)
  with TARGET.open('wb')as raw:
   with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0)as f:f.write(('\n'.join(json.dumps(r,separators=(',',':'))for r in[manifest]+rows)+'\n').encode())
 print('Verified'if args.check_source or args.verify_oracle else'Generated',len(rows),'Edwardsfield observations;',len(pins),'pins')
