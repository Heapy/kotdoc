#!/usr/bin/env python3
import re,argparse,ctypes as C,gzip,hashlib,json,random,struct,subprocess,sys,tempfile
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/sha512-reference';TARGET=R/'core/src/test/resources/crypto-sha512/data.jsonl.gz'
OPS=['init','init384','initT','update','update384','updateT','finalize','finalize384','finalizeT','finalizePrefix','finalizePrefix384','chunkGeneric','chunk','incrementSize','selectDigest','getIndex']
def pins():
 subprocess.run([sys.executable,str(R/'tools/extract-sha512-reference.py'),'--check'],check=True)
 paths=[p for p in D.rglob('*')if p.is_file()and'work-in-progress'not in str(p)and'__pycache__'not in str(p)]+[R/'tools/extract-sha512-reference.py',Path(__file__).resolve(),R/'licenses/crypton-sha512-BSD-2-Clause.txt',R/'licenses/crypton-BSD-3-Clause.txt',R/'vendor/crypton-2.0.1/SOURCE.json']
 return{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths}
class Oracle:
 def __init__(self,tmp,optimization="-O2",arm=False):
  self.libs=[]
  for observed in [False,True]:
   path=tmp/f'{observed}.dylib';subprocess.run(['cc',optimization,'-std=c99','-DNO_INLINE_ASM','-fPIC','-shared',*(['-DOBSERVED']if observed else[]),*(['-DWITH_ARMV8_SHA512','-DWITH_TARGET_ATTRIBUTES']if arm else[]),'-I'+str(D/'original/cbits'),str(D/'Sha512Reference.c'),*([str(D/'original/cbits/sha512_armv8.c')]if arm else[]),'-o',str(path)],check=True);lib=C.CDLL(str(path));lib.ref_trace.restype=C.c_void_p;lib.ref_helper.restype=C.c_uint64;lib.ref_helper.argtypes=[C.c_uint,C.c_uint64,C.c_uint64];self.libs.append(lib)
 def observe(self,r,observed):
  lib=self.libs[observed]
  if 'helper'in r:return dict(value=f"{lib.ref_helper(r['helper'],int(r['a'],16),int(r['b'],16)):016x}")
  ctx=(C.c_ubyte*320)(*([17]*32+[81]*256+[34]*32));lib.ref_reset();contexts=[];buffers=[];outputs=[]
  for step in r['steps']:
   if 'context'in step:ctx[32:288]=step['context']
   data=step.get('bytes',[]);alignment=step.get('alignment',0);size=max(len(data),step.get('length',0),128 if step['op']in ['chunk','chunkGeneric']else 0,64)
   buffer=(C.c_ubyte*(32+alignment+size+32))(*([17]*(32+alignment)+data+[165]*(size-len(data))+[34]*32));out=buffer if step.get('alias')else(C.c_ubyte*128)(*([17]*32+[165]*64+[34]*32));outoffset=32+alignment if step.get('alias')else 32
   lib.ref_run(OPS.index(step['op']),C.byref(ctx,32),C.byref(buffer,32+alignment),C.c_uint(step.get('length',len(data))),C.c_uint(step.get('arg',0)),C.byref(out,outoffset));contexts.append(list(ctx));buffers.append(list(buffer));outputs.append(list(out))
  result=dict(contexts=contexts,buffers=buffers,outputs=outputs)
  if observed:
   count=lib.ref_count();values=list(struct.unpack('<'+'Q'*count,C.string_at(lib.ref_trace(),count*8)))if count else[]
   result['trace']=[f'{v:016x}'for v in values]
  return result
 def digest(self,alg,chunks,n=None,end=None):
  lib=self.libs[0];ctx=(C.c_ubyte*256)();out=(C.c_ubyte*64)();op,arg={'SHA384':(1,0),'SHA512':(0,0),'SHA512/224':(2,224),'SHA512/256':(2,256)}[alg];lib.ref_run(op,ctx,None,0,arg,out)
  for chunk in chunks:
   data=(C.c_ubyte*max(1,len(chunk)))(*chunk);lib.ref_run(3,ctx,data,len(chunk),0,out)
  if n is not None:
   data=(C.c_ubyte*max(1,len(end)))(*end);lib.ref_run(10 if alg=='SHA384'else 9,ctx,data,len(end),C.c_uint(n),out)
  else:lib.ref_run(7 if alg=='SHA384'else 6 if alg=='SHA512'else 8,ctx,None,0,arg,out)
  return list(out)[:{'SHA384':48,'SHA512':64,'SHA512/224':28,'SHA512/256':32}[alg]]
def requests():
 rng=random.Random(613512)
 for init,arg in [('init',0),('init384',0)]+[('initT',n)for n in [0,1,7,8,9,16,127,128,223,224,225,255,256,257,383,384,385,504,511,512,513,0xffffffff]]:
  for length in [0,1,7,8,15,16,63,64,111,112,113,127,128,129,239,240,255,256,257,1024]:
   data=list(rng.randbytes(length))
   for alignment in [0,1,7]:
    for splits in [[length],[0,min(1,length),max(0,length-1),0]]:
     steps=[dict(op=init,arg=arg)];pos=0
     for i,n in enumerate(splits):steps.append(dict(op=['update','update384','updateT'][i%3],bytes=data[pos:pos+n],alignment=alignment));pos+=n
     steps.append(dict(op='finalizeT'if init=='initT'else'finalize384'if init=='init384'else'finalize',arg=min(arg,512)))
     yield dict(steps=steps,trace=length<=1 and alignment==0 and len(splits)==1)
 for init in ['init','init384']:
  for initial in [0,1,111,112,127,128,129]:
   for length in [0,1,7,111,112,113,127,128,129,255,256,257]:
    for n in sorted(set([0,1,max(0,length-1),length,length+1,0xffffffff,0x80000000])):
     for alias in [False,True]:yield dict(steps=[dict(op=init),dict(op='update',bytes=list(rng.randbytes(initial))),dict(op='finalizePrefix384'if init=='init384'else'finalizePrefix',bytes=list(rng.randbytes(length)),arg=n,alias=alias)],trace=initial==0 and length<=1 and n==0 and not alias)
 for _ in range(60):
  state=list(rng.randbytes(256))
  for op in ['chunkGeneric','chunk','getIndex','incrementSize','selectDigest']:
   for arg in [0,1,0xffffffff,0x80000000]:yield dict(steps=[dict(op=op,context=state,bytes=list(rng.randbytes(128)),arg=arg)],trace=op in ['chunk','chunkGeneric'])
 for low in [0,1,111,112,127,128,0x7fffffffffffffff,0xffffffffffffffff,0xfffffffffffffffe]:
  for high in [0,0xffffffffffffffff]:
   state=list(rng.randbytes(256));state[:16]=struct.pack('<QQ',low,high)
   for op in ['update','finalize','finalizePrefix','incrementSize']:
    yield dict(steps=[dict(op=op,context=state,bytes=list(rng.randbytes(129)),arg=129)],trace=False)
 values=[0,1,2,0x7fffffff,0x80000000,0xffffffff,0x100000000,0x7fffffffffffffff,0x8000000000000000,0xffffffffffffffff]
 for op in range(7):
  for a in values:
   for b in values:yield dict(helper=op,a=f'{a:016x}',b=f'{b:016x}')
def literal_cases():
 s=(D/'original/tests/HashSpec.hs').read_text();short=[re.search('^v'+str(i)+' = "(.*)"$',s,re.M).group(1).encode()for i in range(3)]
 # Long vector expressions are preserved from original HashSpec.
 for group,inputs in [('expected',short),('expectedLong',[re.findall(r'"([^"]+)"',s.split('longVectors =',1)[1].split('expectedLong',1)[0])[0].encode(),re.findall(r'"([^"]+)"',s.split('longVectors =',1)[1].split('expectedLong',1)[0])[1].encode(),b'a'*1000000])]:
  section=s.split(group+' =',1)[1].split('\n    ]',1)[0]
  for alg in ['SHA384','SHA512','SHA512/224','SHA512/256']:
   marker='"'+alg+'"'
   if marker not in section:continue
   part=section.split(marker,1)[1].split('\n        )',1)[0];expected=re.findall(r'"([0-9a-f]+)"',part);assert len(expected)==3
   for i,(data,value)in enumerate(zip(inputs,expected)):yield dict(digest=True,algorithm=alg,chunks=[list(data)],literal=group+'/'+alg+'/'+str(i)),list(bytes.fromhex(value))
def main():
 p=argparse.ArgumentParser();p.add_argument('--verify-oracle',action='store_true');p.add_argument('--limit',type=int);p.add_argument('--optimization',default='-O2',choices=['-O0','-O2','-O3']);p.add_argument('--arm',action='store_true');a=p.parse_args();rows=[]
 with tempfile.TemporaryDirectory(prefix='kotdoc-sha512-')as tmp:
  oracle=Oracle(Path(tmp),a.optimization,a.arm)
  if a.arm:
   generic=Path(tmp)/"generic";generic.mkdir();OracleGeneric=Oracle(generic,a.optimization,False);print("ARM SHA512 runtime availability",oracle.libs[0].ref_backend(),flush=True)
  for i,r in enumerate(requests()):
   if a.limit is not None and i>=a.limit:break
   observed=oracle.observe(r,bool(r.get('trace')) and not a.arm);ordinary=oracle.observe(r,False);assert ordinary=={k:v for k,v in observed.items()if k!='trace'},r
   if a.arm and r.get('trace'):observed['trace']=OracleGeneric.observe(r,True)['trace']
   rows.append(dict(request=r,expected=observed))
   if(i+1)%1000==0:print('captured',i+1,flush=True)
  if a.limit is None:
   for r,e in literal_cases():assert oracle.digest(r['algorithm'],r['chunks'])==e,r['literal'];rows.append(dict(request=r,expected=dict(digest=e)))
   artifact=D/'HaskellObservations.json'
   if artifact.exists():
    for row in json.loads(artifact.read_text()):
     r=row['request'];e=row['expected'];assert oracle.digest(r['algorithm'],r['chunks'],r.get('prefix'),r.get('end'))==e['digest'];rows.append(row)
 sourcepins=pins();manifest=dict(sources=sourcepins,operations=OPS,cases=len(rows))
 if a.verify_oracle:
  with gzip.open(TARGET,'rt')as f:old=[json.loads(l)for l in f]
  assert old==[manifest,*rows]
 else:TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_bytes(gzip.compress(('\n'.join(json.dumps(x,separators=(',',':'))for x in [manifest,*rows])+'\n').encode(),mtime=0))
 print('PASS',len(rows),'independent SHA512 rows',len(sourcepins),'pins')
if __name__=='__main__':main()
