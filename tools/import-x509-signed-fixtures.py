#!/usr/bin/env python3
"""Observe unchanged X509 signed objects and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-signed/data.jsonl.gz'
sources=[Path(__file__).resolve()]
for name,archive,notice in [
 ('crypton-2.0.1','9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914','crypton-BSD-3-Clause.txt'),
 ('crypton-asn1-parse-0.10.0','35c0f278d8fcb720b4ad89c2a7f256f7fb098b9f71a0aeb493a4c3c0fe7728d9','crypton-asn1-parse-BSD-3-Clause.txt'),
 ('crypton-x509-1.9.2','7da68d3b512dbb42261f37f16337741677f40409d33fe6b3b508e49f861c1a4f','crypton-x509-BSD-3-Clause.txt'),
 ('crypton-asn1-types-0.4.1','61efc63877a0d3ddb992a793839d9427c1597e095a6e1f63c04e05fd3e4e302d','crypton-asn1-types-BSD-3-Clause.txt'),
 ('crypton-asn1-encoding-0.10.0','49813a0ee253f628cb8102dd6857905609b52e490c73c554d7cdf0cbce2926d2','crypton-asn1-encoding-BSD-3-Clause.txt'),
 ('time-hourglass-0.3.0','9c7c7c74dda5033160328606cb3e8724201abadf9a140271c73c89a2612b9d22','time-hourglass-BSD-3-Clause.txt')]:
 d=root/'vendor'/name;m=json.loads((d/'SOURCE.json').read_text());assert m['sha256']==archive
 assert m['package']+'-'+m['version']==name and m['url']=='https://hackage-content.haskell.org/package/'+name+'.tar.gz'
 assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
 for f,h in m['files'].items():assert hashlib.sha256((d/f).read_bytes()).hexdigest()==h,(name,f)
 assert (root/'licenses'/notice).read_bytes()==(d/'LICENSE').read_bytes()
 sources += [root/'licenses'/notice]+[f for f in d.rglob('*') if f.is_file()]
sources += [f for folder in ['haskell-x509-crl-reference','haskell-x509-signed-reference'] for f in (root/'tools'/folder).rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['group']=='original-generators-signed-object-field' for c in cases)==2000
 if a.check_source:print('Verified',len(cases),'X509 Signed observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 def query(rs):return [json.loads(x) for x in subprocess.check_output([a.oracle],input=''.join(json.dumps(r)+'\n' for r in rs).encode(),timeout=240).splitlines()]
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate','1000'],timeout=120).splitlines()];assert len(cases)==2000
 def add(group,api,**values):
  request=dict(api=api,kind='probe',field='all',part='all',signer='constant',signature=[0,1,255],algorithm=['with',4,0],result=17)
  request.update(values);cases.append(dict(group=group,request=request))
 algs=[['with',h,k] for h,k in itertools.product(range(7),[0,1,2,3])]+[['intrinsic',6],['intrinsic',7],['unknown',['1','2','3']],['unknown',[]],None]
 objects=[str(x) for x in [-2**256,-2**63,-1,0,1,2**63,2**256]]+[None,'objectError']
 signatures=[[],[0],[1,2,3],list(range(256)),'error']
 signed=[[o,s,b] for o,s,b in itertools.product(objects[:7],algs[:4]+algs[-5:],signatures[:4])]
 signed += [None,[None,algs[0],[]],['objectError',algs[0],[]],['1',None,[]],['1',algs[0],'error']]
 for value in signed:
  for field in ['tag','object','algorithm','signature','shown','all']:add('Signed-record-fields-Eq-Show-and-demand','signed',value=value,field=field)
  for part in ['tag','signed','raw','bytes','shown','all']:add('signedToExact-shared-fields-and-DER','toExact',value=value,part=part)
 for value,other in itertools.product(signed[:12]+signed[-5:],repeat=2):add('Signed-Eq-short-circuit-order','equal',value=value,other=other)
 for obj,signer,alg,sig in itertools.product(objects,['constant','echo','length','throw'],[algs[4],algs[-3],None],[[],[0,255],'error']):
  for part in ['tag','signed','raw','bytes','result']:
   add('signer-object-algorithm-signature-result-independent-demand','create',value=obj,signer=signer,algorithm=alg,signature=sig,part=part)
  for field in ['tag','object','algorithm','signature','shown']:
   add('objectToSigned-lazy-projections','objectToSigned',value=obj,signer=signer,algorithm=alg,signature=sig,field=field,part='signed')
 for obj,signer,effect,part in itertools.product(objects,['constant','echo','throw'],['list','maybe','writer'],['tag','signed','raw','bytes']):
  for n in [0,1,3]:add('arbitrary-Functor-list-Maybe-writer','functor',value=obj,signer=signer,effect=effect,part=part,count=n,present=n!=0,log='signing log')
 def tlv(tag,body,form=0):
  n=len(body)
  if form==0:header=[n] if n<128 else [129,n]
  elif form==1:header=[129,n]
  elif form==2:header=[130,0,n]
  else:header=[128]
  return [tag]+header+body+([0,0] if form==3 else [])
 algorithm=tlv(48,tlv(6,[42,134,72,134,247,13,1,1,11])+[5,0])
 signature=tlv(3,[0,0,1,255])
 canonical=tlv(48,tlv(48,[2,1,7])+algorithm+signature)
 def decode(group,b,kind='probe'):
  for part in ['tag','signed','raw','bytes','shown','all']:add(group,'decode',bytes=b,kind=kind,part=part)
 decode('ordinary-probe-SignedExact',canonical)
 for form1,form2,tag in itertools.product(range(4),range(4),[48,49,160,161,224]):
  b=tlv(tag,tlv(tag,[2,1,7],form2)+algorithm+signature,form1)
  decode('BER-header-lengths-container-kinds-and-EOC-omission',b)
  add('SignedExact-Eq-includes-original-whole-bytes','decodeEqual',bytes=b,other=canonical)
 for n in range(len(canonical)+1):decode('every-signed-byte-truncation',canonical[:n])
 for i,b in itertools.product(range(len(canonical)),[0,1,2,3,4,5,6,16,17,31,32,48,49,127,128,129,130,160,255]):decode('every-signed-byte-position-mutation',canonical[:i]+[b]+canonical[i+1:])
 for obj,alg,sig,extra in itertools.product([[2,1,7],[2,1,7,5,0],[2,0],[5,0],[]],[algorithm,tlv(48,[6,2,42,3]),tlv(49,[]),[]],[signature,tlv(3,[7,255]),tlv(3,[48,255]),tlv(3,[]),[5,0],tlv(48,signature),[]],[[],[2,0],[5,0]]):
  decode('object-algorithm-signature-remainder-and-partial-pattern-errors',tlv(48,tlv(48,obj)+alg+sig+extra))
 for tail in [[],[5,0],[2,0],[2,1,7],[3,0],[255],[0,0],[5,0,2,0],[5,0,3,0]]:decode('outer-one-cell-lookahead-versus-raw-parse-failure',canonical+tail)
 rng=random.Random(0x58353039534947)
 for _ in range(200):decode('random-malformed-signed-bytes',rng.choices(range(256),k=rng.randrange(60)))
 # Actual upstream-generated certificate/CRL values also exercise concrete signing.
 for c in cases[:30]:
  r=c['request']
  for part in ['signed','raw','bytes','all']:add('original-generated-certificate-and-CRL-signing','create',kind=r['kind'],value=r['value'],part=part)
 for kind in ['certificate','crl']:
  for c in [c for c in cases[:2000] if c['request']['kind']==kind][:8]:
   r=dict(c['request'],part='bytes');encoded=query([r])[0]
   if isinstance(encoded,list):
    decode('actual-signed-certificate-and-CRL-decoding',encoded,kind)
    for n in range(len(encoded)+1):
     add('concrete-signed-certificate-CRL-truncation','decode',kind=kind,bytes=encoded[:n],part='tag')
 # Guarantee concrete valid DER examples independently of arbitrary DN OIDs.
 def dt(y,m=0,d=1,h=0,mi=0,s=0,ns=0):return [y,m,d,h,mi,s,ns]
 dn=[[['2','5','4','3'],1,[116,101,115,116]]]
 sig=['with',4,0];ext=[['2','5','29','15'],True,[3,2,7,128]]
 keys=[['rsa',1,'127','3'],['dsa','17','2','3','5'],['ecNamed',12,[4,1,2]],['unknown',['1','2','3'],[0,255]]]+[[t,[0]*n] for t,n in [('x25519',32),('x448',56),('ed25519',32),('ed448',57)]]
 concrete=[]
 for key,date,exts in itertools.product(keys,[dt(1950),dt(2049),dt(2050),dt(2050,1,29,25,-1,61,-1)],[None,[],[ext]]):
  concrete.append(('certificate',['2','123',sig,dn,[date,dt(2060)],dn,key,exts]))
 for date,nextDate,revoked,exts in itertools.product([dt(1950),dt(2050)],[None,dt(2060)],[[],[['7',dt(2049),None]],[['7',dt(2049),[ext]],['8',dt(2050),[]]]],[None,[],[ext]]):
  concrete.append(('crl',['1',sig,dn,date,nextDate,revoked,exts]))
 for kind,value in concrete:
  for part in ['signed','raw','bytes','all']:add('concrete-certificate-key-families-and-CRL-optionals','create',kind=kind,value=value,part=part)
 encoded=query([dict(api='create',kind=k,value=v,part='bytes',field='all',signer='constant',signature=[0,1,255],algorithm=sig,result=17) for k,v in concrete])
 assert len(encoded)==len(concrete) and all(isinstance(b,list) for b in encoded)
 for (kind,value),b in zip(concrete,encoded):decode('concrete-certificate-key-families-and-CRL-decoding',b,kind)
 for kind in ['certificate','crl']:
  b=next(b for (k,v),b in zip(concrete,encoded) if k==kind)
  for n in range(len(b)+1):decode('every-concrete-signed-byte-truncation',b[:n],kind)
  for i,x in itertools.product(range(len(b)),[0,48,255]):
   for part in ['tag','all']:add('every-concrete-signed-position-mutation','decode',kind=kind,bytes=b[:i]+[x]+b[i+1:],part=part)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['group']=='original-generators-signed-object-field']
 print('Original X509 Signed verified',len(cases),'observations and 2000 generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'Signed observations; SHA256',manifest['caseHash'])
