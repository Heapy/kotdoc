#!/usr/bin/env python3
import argparse,ctypes as C,gzip,hashlib,json,random,struct,subprocess,sys,tempfile
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/chacha-core-reference';T=R/'core/src/test/resources/crypto-chacha-core/data.jsonl.gz'
OPS=['initKeyState','initCore','hinitCore','init','hchacha','initX','core','hcore','combine','generate','counter64','counter32','setCounter64','setCounter32','simpleBlock','random']
def pins():
 subprocess.run([sys.executable,str(R/'tools/extract-chacha-core-reference.py'),'--check'],check=True)
 paths=[p for p in D.rglob('*')if p.is_file()and'work-in-progress'not in str(p)and'__pycache__'not in str(p)]+[R/'tools/extract-chacha-core-reference.py',Path(__file__).resolve(),R/'licenses/crypton-chacha-BSD-3-Clause.txt',R/'licenses/crypton-BSD-3-Clause.txt',R/'vendor/crypton-2.0.1/SOURCE.json']
 return{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths}
class Oracle:
 def __init__(self,tmp,opt='-O2'):
  self.libs=[]
  for observed in [False,True]:
   lib=tmp/f'{observed}.dylib';subprocess.run(['cc',opt,'-std=c99','-DNO_INLINE_ASM','-shared','-fPIC',*(['-DOBSERVED']if observed else[]),'-I'+str(D/'original/cbits'),str(D/'ChaChaCoreReference.c'),'-o',str(lib)],check=True);api=C.CDLL(str(lib));api.ref_trace.restype=C.c_void_p;api.ref_run.argtypes=[C.c_uint,C.c_void_p,C.c_void_p,C.c_void_p,C.c_uint32,C.c_void_p,C.c_uint32,C.c_void_p,C.c_uint32,C.c_uint32,C.c_uint64];assert api.ref_size()==136;self.libs.append(api)
 def observe(self,r,observed):
  api=self.libs[observed];ctx=(C.c_ubyte*200)(*([17]*32+[81]*(132 if r.get('logical132')else 136)+[34]*(36 if r.get('logical132')else 32)));api.ref_reset();snapshots=[]
  for s in r['steps']:
   if 'context'in s:ctx[32:168]=s['context']
   op=s['op'];data=s.get('bytes',[]);length=s.get('length',len(data));alignment=s.get('alignment',0);shift=s.get('outShift',0);size=max(len(data),length+shift,64+shift);buffer=(C.c_ubyte*(64+alignment+size))(*([17]*(32+alignment)+data+[165]*(size-len(data))+[34]*32));out=(C.c_ubyte*(64+alignment+size))(*([17]*(32+alignment)+[165]*size+[34]*32));key=s.get('key',list(range(32)));iv=s.get('iv',list(range(24)));kb=(C.c_ubyte*(64+len(key)))(*([17]*32+key+[34]*32));ib=(C.c_ubyte*(64+len(iv)))(*([17]*32+iv+[34]*32));assert C.addressof(buffer)%8==0 and C.addressof(out)%8==0 and C.addressof(ctx)%8==0
   dest=C.byref(ctx,32+s['outCtx'])if'outCtx'in s else C.byref(buffer,32+alignment+shift)if s.get('alias')else C.byref(out,32+alignment+shift)
   keyptr=C.byref(ctx,32+s['keyCtx'])if'keyCtx'in s else C.byref(kb,32);ivptr=C.byref(ctx,32+s['ivCtx'])if'ivCtx'in s else C.byref(ib,32)
   api.ref_run(OPS.index(op),C.byref(ctx,32),dest,C.byref(ctx,32+s['inputCtx'])if'inputCtx'in s else C.byref(buffer,32+alignment),length,keyptr,s.get('keyLength',32),ivptr,s.get('ivLength',8),s.get('rounds',20),int(s.get('value','0'),16));snapshots.append(dict(context=list(ctx),buffer=list(buffer),output=list(out),key=list(kb),iv=list(ib)))
  result=dict(snapshots=snapshots)
  if observed:
   count=api.ref_count();blob=C.string_at(api.ref_trace(),count*4);result['trace']=[f'{v:08x}'for(v,)in struct.iter_unpack('<I',blob)]
  return result

 def haskell(self,r):
  sizes=r['sizes'];steps=[dict(op='initX'if r.get('xchacha')else'init',rounds=r['rounds'],key=r['key'],iv=r['iv'],keyLength=len(r['key']),ivLength=len(r['iv'])),dict(op='setCounter32',value=f"{r.get('counter',0):08x}")];pos=0
  for n in sizes:steps.append(dict(op=r['operation'],bytes=r['bytes'][pos:pos+n],length=n));pos+=n
  snapshots=self.observe(dict(steps=steps),False)['snapshots'][2:];out=[]
  for n,snapshot in zip(sizes,snapshots):out+=snapshot['output'][32:32+n]
  e=dict(bytes=out)
  if r.get('sha256'):e['sha256']=list(hashlib.sha256(bytes(out)).digest())
  return e

def requests():
 rng=random.Random(613830);key=list(rng.randbytes(32));iv=list(rng.randbytes(24))
 for op in ['initKeyState','initCore','hinitCore','init','hchacha','initX','core','hcore']:
  for rounds in [0,1,2,3,7,8,12,20,21,255,0xffffffff,0x80000000]:
   for kl in ([0,15,16,17,31,32,33,0xffffffff]if op in ['initKeyState','initCore','init']else[32]):
    for il in ([0,7,8,9,12,13,0xffffffff]if op in ['initCore','init']else[16]):
     yield dict(steps=[dict(op=op,key=key,iv=iv,keyLength=kl,ivLength=il,rounds=rounds)],trace=kl==32 and il in [8,16] and rounds in [0,1,8,20])
 for rounds in [0,1,8,12,20,255]:
  for kl in [16,32]:
   for il in [8,12]:
    init=dict(op='init',rounds=rounds,keyLength=kl,ivLength=il,key=key,iv=iv)
    for n in [0,1,15,16,17,31,32,63,64,65,127,128,129,191,192,193,255,256,257,511,512,513,1024,4096]:
     data=list(rng.randbytes(n))
     for alignment in [0,1,7]:
      for op in ['generate','combine','random']:
       yield dict(steps=[init,dict(op=op,bytes=data,length=n,alignment=alignment,rounds=rounds)],trace=n in [0,1,16,17] and alignment==0)
      for shift in [0,1,7]:yield dict(steps=[init,dict(op='combine',bytes=data,length=n,alignment=alignment,alias=True,outShift=shift)])
    for counter in [0,1,0xfffffffe,0xffffffff,0x100000000,0xffffffffffffffff]:
     yield dict(steps=[init,dict(op='setCounter64',value=f'{counter:016x}'),dict(op='counter64'),dict(op='counter32'),dict(op='generate',length=1),dict(op='setCounter32',value='ffffffff'),dict(op='generate',length=63),dict(op='generate',length=65),dict(op='counter64')],trace=rounds==8)
    for op in ['generate','combine']:
     yield dict(steps=[init,*[dict(op=op,bytes=list(rng.randbytes(n)),length=n)for n in [1,0,1,62,0,63,1,64,65,0,129]]])
 # Exhaust every reachable pending-byte phase and its consumption boundaries.
 for rounds in [8,12,20]:
  for prefix in range(1,64):
   remaining=64-prefix
   for length in sorted(set([0,1,remaining-1,remaining,remaining+1,64,65])):
    for op in ['generate','combine']:yield dict(steps=[dict(op='init',rounds=rounds,ivLength=12),dict(op='generate',length=prefix),dict(op=op,bytes=list(rng.randbytes(length)),length=length)])
 # Also cover valid manually supplied cache endpoints, including the full64 case.
 for offset in [0,1,31,63,64]:
  state=list(rng.randbytes(136));state[128:131]=[offset,64-offset,8]
  for length in sorted(set([0,1,64-offset,65-offset,64,65])):
   for op in ['generate','combine']:yield dict(steps=[dict(op=op,context=state,bytes=list(rng.randbytes(length)),length=length)])
 for rounds in [0,1,3,8,12,20,255,0xffffffff,0x80000000]:
  for length in [0,1,16,17]:yield dict(steps=[dict(op='init'),dict(op='setCounter64',value='ffffffffffffffff'),dict(op='random',rounds=rounds,length=length),dict(op='counter64')],trace=True)
  sequence=[dict(op='init'),dict(op='setCounter64',value='ffffffffffffffff')]
  for length in [0,1,0,15,16,17,31,32,0]:sequence += [dict(op='random',rounds=rounds,length=length),dict(op='counter64')]
  yield dict(steps=sequence,trace=True)
 for op in ['init','initX']:
  for rounds in [8,12,20]:yield dict(logical132=True,steps=[dict(op=op,rounds=rounds)])
 for initial in [[],[dict(op='generate',length=1)]]:
  for src,dst in [(0,None),(1,None),(64,None),(0,0),(0,1),(1,0)]:
   for length in [0,1,63,64]:
    step=dict(op='combine',inputCtx=src,length=length)
    if dst is not None:step['outCtx']=dst
    yield dict(steps=[dict(op='init'),*initial,step],trace=True)
 for op in ['initKeyState','initCore','hinitCore','init','initX']:
  for keyctx,ivctx in [(0,16),(16,0),(0,0)]:yield dict(steps=[dict(op=op,keyCtx=keyctx,ivCtx=ivctx)])
 for op in ['core','hcore','hchacha','simpleBlock','generate','combine','random']:
  for offset in ([0]if op in ['core','hcore']else[0,1]):
   for n in ([64]if op in ['core','simpleBlock','generate','combine']else[32]if op in ['hcore','hchacha']else[0,1,16,17,32,64]):yield dict(steps=[dict(op='init'),dict(op=op,bytes=list(rng.randbytes(n)),length=n,outCtx=offset,rounds=8)],trace=True)
 for _ in range(40):
  state=list(rng.randbytes(136))
  for op in ['core','hcore','simpleBlock','random','counter64','counter32','setCounter64','setCounter32']:
   yield dict(steps=[dict(op=op,context=state,length=64,rounds=8,value=f'{rng.getrandbits(64):016x}')],trace=op in ['core','hcore','random'])

def main():
 p=argparse.ArgumentParser();p.add_argument('--verify-oracle',action='store_true');p.add_argument('--optimization',choices=['-O0','-O2','-O3'],default='-O2');a=p.parse_args();rows=[]
 with tempfile.TemporaryDirectory(prefix='kotdoc-chacha-core-')as tmp:
  oracle=Oracle(Path(tmp),a.optimization)
  for i,r in enumerate(requests()):
   e=oracle.observe(r,bool(r.get('trace')));ordinary=oracle.observe(r,False);assert ordinary=={k:v for k,v in e.items()if k!='trace'},r;rows.append(dict(request=r,expected=e))
   if(i+1)%1000==0:print('captured',i+1,flush=True)
   artifact=D/'HaskellObservations.json'
  if artifact.exists():
   for row in json.loads(artifact.read_text()):assert oracle.haskell(row['request'])==row['expected'],row['request'];rows.append(row)
 manifest=dict(cases=len(rows),sources=pins(),operations=OPS)
 if a.verify_oracle:
  with gzip.open(T,'rt')as f:old=[json.loads(x)for x in f]
  assert old==[manifest,*rows]
 else:T.parent.mkdir(parents=True,exist_ok=True);T.write_bytes(gzip.compress(('\n'.join(json.dumps(x,separators=(',',':'))for x in [manifest,*rows])+'\n').encode(),mtime=0))
 print('PASS',len(rows),'ordinary/observed portable ChaCha rows',len(manifest['sources']),'pins')
if __name__=='__main__':main()
