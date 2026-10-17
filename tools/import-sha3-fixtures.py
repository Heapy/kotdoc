#!/usr/bin/env python3
"""Unmodified and mechanically observed original-C expectations, never Kotlin."""
import re
import argparse,ctypes as C,gzip,hashlib,json,random,struct,subprocess,sys,tempfile
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/sha3-reference';TARGET=R/'core/src/test/resources/crypto-sha3/data.jsonl.gz'
OPS=['init','update','finalizePad','output','finalize','finalizeShake','finalizeCshake','keccakInit','keccakUpdate','keccakFinalize','decafInit','decafUpdate','decafOutput','decafFinal','decafDestroy']
def pins():
 subprocess.run([sys.executable,str(R/'tools/extract-sha3-reference.py'),'--check'],check=True)
 files=[p for p in (D/'original').rglob('*')if p.is_file()]+[D/p for p in ['ObservedSha3.c','Sha3Reference.c','HeaderChunksReference.c','SourceMatrix.json','CompilerProfile.json','README.md','Sha3Reference.hs','OriginalSha3HashSpec.hs','OriginalSuite.txt','HaskellObservations.json','HaskellOracleProvenance.json','prepare-haskell-oracle.py','capture-haskell.py','cabal.project','cabal.project.freeze','kotdoc-sha3-reference.cabal']]+[R/'tools/extract-sha3-reference.py',Path(__file__).resolve(),R/'licenses/crypton-sha3-BSD-2-Clause.txt',R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/decaf-MIT.txt',R/'vendor/crypton-2.0.1/SOURCE.json']
 return{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in files}
class Oracle:
 def __init__(self,tmp):
  header=tmp/'header.dylib';subprocess.run(['cc','-O2','-std=c99','-fPIC','-shared','-I'+str(D/'original/cbits'),'-I'+str(D/'original/cbits/decaf/include'),str(D/'HeaderChunksReference.c'),'-o',str(header)],check=True);self.header=C.CDLL(str(header));self.header.ref_header_offset.restype=C.c_uint64;self.header.ref_header_length.restype=C.c_uint64
  self.libs=[]
  for observed in [False,True]:
   lib=tmp/f'{observed}.dylib';subprocess.run(['cc','-O2','-std=c99','-fPIC','-shared',*(['-DOBSERVED']if observed else[]),'-I'+str(D/'original/cbits'),'-I'+str(D/'original/cbits/decaf/include'),str(D/'Sha3Reference.c'),'-o',str(lib)],check=True);api=C.CDLL(str(lib));api.ref_trace.restype=C.c_void_p;self.libs.append(api)
 def observe(self,r,observed):
  api=self.libs[observed];raw=C.create_string_buffer(512);address=(C.addressof(raw)+31)&~31;ctx=(C.c_ubyte*440).from_address(address);ctx[:]=[17]*32+[81]*376+[34]*32;api.ref_reset();snapshots=[];buffers=[]
  for step in r['steps']:
   data=step.get('bytes',[]);length=step.get('length',len(data));alignment=step.get('alignment',0);body=max(len(data),length,(step.get('arg',0)//8 if step['op']in ['finalize','keccakFinalize']else 0));buffer=(C.c_ubyte*(32+alignment+body+32))(*([17]*(32+alignment)+data+[165]*(body-len(data))+[34]*32));api.ref_run(OPS.index(step['op']),C.byref(ctx,32),C.byref(buffer,32+alignment),C.c_uint(length),C.c_uint(step.get('arg',0)));buffers.append(list(buffer));snapshots.append(list(ctx))
  result=dict(contexts=snapshots,buffers=buffers)
  if observed:
   count=api.ref_count();blob=C.string_at(api.ref_trace(),count*216);result['trace']=dict(count=count,sha256=hashlib.sha256(blob).hexdigest())
   if r.get('trace'):result['trace']['states']=[dict(label=v[0].split(b'\0')[0].decode(),index=v[1],state=[f'{x:016x}'for x in v[3:]])for v in struct.iter_unpack('<8siI25Q',blob)]
  return result
 def chunks(self,r):
  n=self.header.ref_header_chunks(r['output'],C.c_uint64(r['length']));assert n>=0,'virtualmappingfailed'
  return dict(offsets=[self.header.ref_header_offset(i)for i in range(n)],lengths=[self.header.ref_header_length(i)for i in range(n)])
 def digest(self,r):
  api=self.libs[0];ctx=(C.c_ubyte*376)();api.ref_run(0,ctx,None,0,r['securityBits'])
  for chunk in r['chunks']:
   data=(C.c_ubyte*max(1,len(chunk)))(*chunk);api.ref_run(1,ctx,data,len(chunk),0)
  api.ref_run(2,ctx,None,0,r['pad']);bits=r['outputBits'];length=(bits+7)//8;out=(C.c_ubyte*max(1,length))();api.ref_run(3,ctx,out,length,0);value=list(out)[:length]
  if bits%8:value[bits//8]&=(1<<(bits%8))-1
  return value
 def hash(self,r):
  data=r['bytes'];outlen=r['length'];alias=r.get('alias',False);size=max(len(data),outlen);source=(C.c_ubyte*(size+2))(17,*(data+[165]*(size-len(data))),34);out=source if alias else(C.c_ubyte*(outlen+2))(17,*([165]*outlen),34);self.libs[0].ref_hash(C.byref(out,1),C.c_size_t(outlen),C.byref(source,1),C.c_size_t(len(data)));return dict(source=list(source),output=list(out))
def requests():
 rng=random.Random(613448)
 for bits in [128,129,136,144,160,224,248,255,256,257,384,511,512]:
  rate=200-2*(bits//8)
  for length in sorted(set([0,1,7,8,rate-1,rate,rate+1,2*rate-1,2*rate,2*rate+1,1024])):
   data=list(rng.randbytes(length))
   for alignment in [0,1,7]:
    for pad in [1,4,6,31,128,255]:
     for splits in [[length],[0,length,0],[min(1,length),max(0,length-1)]]:
      steps=[dict(op='init',arg=bits)];pos=0
      for size in splits:steps.append(dict(op='update',bytes=data[pos:pos+size],alignment=alignment));pos+=size
      steps.append(dict(op='finalizePad',arg=pad))
      steps += [dict(op='output',length=size)for size in [0,1,rate-1,0,rate,rate+1,0]]
      yield dict(steps=steps,trace=length==0 and alignment==0 and pad==31 and len(splits)==1)
  for op in ['finalize','keccakFinalize']:
   yield dict(steps=[dict(op='keccakInit'if op=='keccakFinalize'else'init',arg=bits),dict(op='keccakUpdate'if op=='keccakFinalize'else'update',bytes=list(rng.randbytes(rate+1))),dict(op=op,arg=bits)])
  # Source-defined raw phase reuse, including zero-length calls at a full output boundary.
  yield dict(steps=[dict(op='init',arg=bits),dict(op='output',length=rate),dict(op='output',length=0),dict(op='update',bytes=[]),dict(op='finalizeShake'),dict(op='finalizeCshake'),dict(op='output',length=rate+1)])
 for length in [0,1,55,56,57,64,113,114,135,136,137,271,272,1024,4096]:
  data=list(rng.randbytes(length));yield dict(steps=[dict(op='decafInit'),dict(op='decafUpdate',bytes=data),dict(op='decafFinal',length=114),dict(op='decafUpdate',bytes=data),dict(op='decafFinal',length=0),dict(op='decafOutput',length=136),dict(op='decafDestroy'),dict(op='decafInit')])
  for outlen in [0,1,56,57,64,114,136,137,512]:
   for alias in [False,True]:yield dict(hash=True,bytes=data,length=outlen,alias=alias)
def literal_cases():
 source=(D/'original/tests/HashSpec.hs').read_text();inputs=[re.search(r'^v'+str(i)+r' = "(.*)"$',source,re.M).group(1).encode()for i in range(3)]
 for name,security,bits,pad in [(f'{family}-{bits}',bits,bits,pad)for family,pad in [('Keccak',1),('SHA3',6)]for bits in [224,256,384,512]]+[(f'SHAKE{security}_4096',security,4096,31)for security in [128,256]]:
  section=source.split('"'+name+'"',1)[1].split('\n        )',1)[0];expected=re.findall(r'"([0-9a-f]+)"',section);assert len(expected)==3,(name,expected)
  for i,value in enumerate(expected):yield dict(digest=True,securityBits=security,outputBits=bits,pad=pad,chunks=[list(inputs[i])],literal=name+'/'+str(i)),list(bytes.fromhex(value))
def main():
 p=argparse.ArgumentParser();p.add_argument('--verify-oracle',action='store_true');p.add_argument('--limit',type=int);a=p.parse_args();sourcepins=pins();rows=[]
 with tempfile.TemporaryDirectory(prefix='kotdoc-sha3-')as tmp:
  oracle=Oracle(Path(tmp))
  for i,r in enumerate(requests()):
   if a.limit is not None and i>=a.limit:break
   if r.get('hash'):expected=oracle.hash(r)
   else:
    expected=oracle.observe(r,True);ordinary=oracle.observe(r,False);assert ordinary=={k:v for k,v in expected.items()if k!='trace'},r
   rows.append(dict(request=r,expected=expected))
   if(i+1)%1000==0:print('captured',i+1,flush=True)
  if a.limit is None:
   for request,expected in literal_cases():
    assert oracle.digest(request)==expected,request;rows.append(dict(request=request,expected=dict(digest=expected)))
   artifact=D/'HaskellObservations.json'
   if artifact.exists():
    data=json.loads(artifact.read_text())
    for request,expected in zip(data['requests'],data['observations']):assert oracle.digest(request)==expected['digest'],request;rows.append(dict(request=request,expected=expected))
  if a.limit is None:
   for length in [0,1,2147483647,2147483648,4294967295,4294967296,4294967297,8589934590,8589934591,10737418240]:
    for output in [0,1]:
     request=dict(chunkPlan=True,length=length,output=output);rows.append(dict(request=request,expected=oracle.chunks(request)))
 if a.verify_oracle:
  with gzip.open(TARGET,'rt')as f:old=[json.loads(line)for line in f]
  assert old[0]['sources']==sourcepins;assert old[1:]==rows
 else:
  TARGET.parent.mkdir(parents=True,exist_ok=True);payload='\n'.join(json.dumps(v,separators=(',',':'))for v in [dict(sources=sourcepins,operations=OPS),*rows])+'\n';TARGET.write_bytes(gzip.compress(payload.encode(),mtime=0))
 print('PASS',len(rows),'independent C/literal/Haskell/header-observer rows',len(sourcepins),'pins')
if __name__=='__main__':main()
