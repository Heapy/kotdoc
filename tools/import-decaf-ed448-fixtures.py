#!/usr/bin/env python3
import argparse,ctypes as C,gzip,hashlib,json,random,re,subprocess,sys,tempfile
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/decaf-ed448-reference';TARGET=R/'core/src/test/resources/crypto-decaf-ed448/data.jsonl.gz'
OPS=['clamp','hashInitWithDom','prehashInit','convertPrivateKeyToX448','derivePublicKey','signInternal','sign','signPrehash','deriveKeypair','keypairExtractPublicKey','keypairExtractPrivateKey','keypairDestroy','keypairSign','keypairSignPrehash','verify','verifyPrehash','prehashUpdate','prehashDestroy']
PROFILES=[(32,32,False),(64,64,False),(64,32,False),(64,32,True)]
def vectors():
 text=(D/'original/tests/Ed448Spec.hs').read_text();parts=text.split('vecSec =')[1:];result=[]
 for part in parts:
  values=[]
  for name in ['','vecPub','vecMsg','vecSig']:
   segment=part if not name else part.split(name+' =',1)[1]
   value=re.search(r'"([^"\n]*)"',segment).group(1);values.append([int(x,16)for x in re.findall(r'\\x([0-9a-f]{2})',value)])
  result.append(dict(zip(['secret','public','message','signature'],values)))
 assert len(result)==8;return result
class Oracle:
 def __init__(self,tmp,optimization="2"):
  self.libs={};self.paths={}
  for bits,sb,vector in PROFILES:
   arch='arch_32'if bits==32 else'arch_ref64'
   for normalized in [0,1]:
    path=tmp/f'{bits}-{sb}-{vector}-{normalized}.dylib';args=['cc',f'-O{optimization}','-std=c99','-shared','-fPIC',f'-DFIELD_BITS={bits}',f'-DSCALAR_WORD_BITS={sb}',f'-DNORMALIZED={normalized}']+(['-DNATIVE_VECTOR']if vector else[])
    for p in [f'include/{arch}',f'p448/{arch}','include','p448','']:args+=['-I',str(D/'original'/p)]
    args+=['-I',str(D/'original/cbits'),str(D/'Ed448Reference.c'),'-o',str(path)];subprocess.run(args,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE);self.libs[bits,sb,vector,normalized]=C.CDLL(str(path));self.paths[bits,sb,vector,normalized]=path
 def abort_probes(self):
  result=[]
  for profile in PROFILES:
   path=self.paths[*profile,0];code="import ctypes,resource;resource.setrlimit(resource.RLIMIT_CORE,(0,0));ctypes.CDLL("+repr(str(path))+").ed_abort_probe()"
   run=subprocess.run([sys.executable,"-c",code],capture_output=True,text=True);assert run.returncode==-6,(profile,run);result.append(dict(bits=profile[0],scalarBits=profile[1],vectorMasks32=profile[2],returncode=run.returncode,reason="original sign_prehash public-key mismatch abort"))
  return result
 def observe(self,r,normalized=0):
  api=self.libs[r['bits'],r['scalarBits'],r['vectorMasks32'],normalized];frames={};inputs=dict(out=r.get('out',[165]*114),secret=r['secret'],public=r['public'],signature=r['signature'],keypair=r.get('keypair',r['secret']+r['public']),message=r['message'],context=r.get('context',[]),ctx=[81]*344)
  for name,data in inputs.items():
   size=344 if name=='ctx'else max(114,len(data));frames[name]=(C.c_ubyte*(size+64))(*([17]*32+data+[165]*(size-len(data))+[34]*32))
  if r.get('alias'):frames['out']=frames[r['alias']]
  ptr=lambda k:C.byref(frames[k],32)
  def call(op,message=None,length=None):
   msg=ptr('message')if message is None else message
   return api.ed_run(OPS.index(op),ptr('out'),ptr('secret'),ptr('public'),ptr('signature'),ptr('keypair'),ptr('ctx'),msg,C.c_size_t(len(r['message'])if length is None else length),r.get('prehashed',0),r.get('forPrehash',0),ptr('context'),len(r.get('context',[])))
  if r.get('prehash'):
   call('prehashInit')
   for chunk in r.get('prehashChunks',[r['message']]):
    v=(C.c_ubyte*max(1,len(chunk)))(*chunk);call('prehashUpdate',v,len(chunk))
  ret=call(r['op']);return dict(ret=ret,frames={k:list(v)for k,v in frames.items()})
def requests(smoke=False):
 for bits,sb,vector in PROFILES:
  for i,v in enumerate(vectors()):
   for op in ['derivePublicKey','sign','verify']:
    yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op=op,**v,kat=i)
  if smoke:continue
  rng=random.Random(448613)
  for i in range(12):
   v=vectors()[i%8];v={**v,'secret':list(rng.randbytes(57)),'message':list(rng.randbytes([0,1,56,57,64,135,136,137,255,256,1024,4096][i]))}
   # Public/signature initialization is independently filled by main using original C.
   for op in OPS:
    for context in [[],list(range(255))]if op in ['hashInitWithDom','sign','signInternal','signPrehash','keypairSign','keypairSignPrehash','verify','verifyPrehash']else[[]]:
     yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op=op,**v,context=context,prehashed=[0,1,2,255][i%4],forPrehash=i%2,prehash=op in ['signPrehash','keypairSignPrehash','verifyPrehash','prehashUpdate','prehashDestroy'],generated=True)
  v=vectors()[0]
  for byte in range(256):yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='clamp',**v,out=[byte]*114)
  for ph in [0,1,255]:
   for fp in [0,1,255]:
    for n in [0,1,2,254,255]:yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='hashInitWithDom',**v,prehashed=ph,forPrehash=fp,context=list(range(n)))
  prime=(1<<448)-(1<<224)-1;order=(1<<446)-13818066809895115352007386748515426880336692474882178609894547503885
  encodings=[list(n.to_bytes(56,'little'))+[sign]for n in [0,1,prime-1,prime,prime+1,(1<<448)-1]for sign in [0,128]]+[[0]*56+[b]for b in [1,127,129,255]]
  for encoding in encodings:
   for target in ['public','signature']:
    altered={**v,target:encoding if target=='public'else encoding+v['signature'][57:]};yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='verify',**altered,edge='smallorder/noncanonical-point')
  for n in [0,1,order-1,order,order+1,(1<<448)-1]:
   for last in [0,1,255]:
    yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='verify',**{**v,'signature':v['signature'][:57]+list(n.to_bytes(56,'little'))+[last]},edge='response-canonicality')
  for encoding in encodings[:4]:
   yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='verify',**{**v,'public':encoding,'signature':encoding+[0]*57},edge='smallorder-zero-response')
  for op in ['sign','signInternal','keypairSign','keypairSignPrehash']:
   yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op=op,**{**v,'public':[0]*57},context=[1,2,3],prehash=op=='keypairSignPrehash',edge='mismatched-public-no-rederive')
  for v in vectors():
   for context in [[],[1],list(range(255))]:
    for chunks in [[v['message']],[[],v['message'],[]],[v['message'][:1],v['message'][1:]]]:
     yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='verifyPrehash',**v,context=context,prehash=True,prehashChunks=chunks,prehashSignature=True)
  for i,v in enumerate(vectors()):
   for op in ['derivePublicKey','convertPrivateKeyToX448','sign','signInternal','keypairSign','signPrehash','keypairSignPrehash']:
    for alias in ['secret','public','message','context','keypair','ctx']:
     yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op=op,**v,context=[3,2,1],alias=alias,prehash=op in ['signPrehash','keypairSignPrehash'])
   for target,length in [('public',57),('signature',114)]:
    for index in range(length):
     tampered={**v,target:v[target].copy()};tampered[target][index]^=1
     yield dict(bits=bits,scalarBits=sb,vectorMasks32=vector,op='verify',**tampered,tamper=[target,index])
def main():
 p=argparse.ArgumentParser();p.add_argument('--smoke',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();subprocess.run([sys.executable,str(R/'tools/extract-decaf-ed448-reference.py'),'--check'],check=True);rows=[]
 with tempfile.TemporaryDirectory(prefix='kotdoc-ed448-')as tmp:
  oracle=Oracle(Path(tmp));probes=oracle.abort_probes();probePath=D/'AbortObservations.json'
  if a.verify_oracle:assert json.loads(probePath.read_text())==probes
  else:probePath.write_text(json.dumps(probes,indent=2)+'\n')
  for i,r in enumerate(requests(a.smoke)):
   if r.get('generated'):
    derive=oracle.observe({**r,'op':'derivePublicKey','alias':None});r['public']=derive['frames']['out'][32:89]
    sig=oracle.observe({**r,'op':'sign','alias':None});r['signature']=sig['frames']['out'][32:146]
   if r.get('prehashSignature'):
    sig=oracle.observe({**r,'op':'signPrehash','alias':None});r['signature']=sig['frames']['out'][32:146]
   expected=oracle.observe(r);other=oracle.observe(r,1);assert expected==other,('ordinary/normalized',r)
   if 'kat'in r:
    if r['op']=='derivePublicKey':assert expected['frames']['out'][32:89]==r['public']
    if r['op']=='sign':assert expected['frames']['out'][32:146]==r['signature']
    if r['op']=='verify':assert expected['ret']==-1
   rows.append(dict(request=r,expected=expected))
   if(i+1)%500==0:print('captured',i+1,flush=True)
 paths=[p for p in D.rglob('*')if p.is_file()and'work-in-progress'not in str(p)and'__pycache__'not in str(p)]+[R/'tools/extract-decaf-ed448-reference.py',Path(__file__).resolve(),R/'licenses/decaf-MIT.txt',R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/crypton-sha3-BSD-2-Clause.txt',R/'vendor/crypton-2.0.1/SOURCE.json',R/'tools/extract-decaf448-point-multiplication-reference.py',R/'tools/extract-decaf448-points-reference.py'];pins={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths}
 if a.verify_oracle:
  with gzip.open(TARGET,'rt')as f:old=[json.loads(line)for line in f]
  assert old[0]['sources']==pins;assert old[1:]==rows
 else:
  TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_bytes(gzip.compress(('\n'.join(json.dumps(v,separators=(',',':'))for v in [dict(sources=pins,operations=OPS),*rows])+'\n').encode(),mtime=0))
 print('PASS',len(rows),'ordinary/normalized Ed448 core rows',len(pins),'pins')
if __name__=='__main__':main()
