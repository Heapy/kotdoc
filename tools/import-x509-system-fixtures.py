#!/usr/bin/env python3
"""Original system-CA loader policies with controlled native effects and real ASN1 decoding."""
import argparse,base64,collections,copy,gzip,hashlib,itertools,json,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-x509-system-reference'
target=root/'core/src/test/resources/x509-system/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
sources=[Path(__file__).resolve(),root/'vendor/http-tls-sources.json',root/'licenses/crypton-x509-system-BSD-3-Clause.txt']
m=json.loads((root/'vendor/http-tls-sources.json').read_text())['crypton-x509-system'];d=root/'vendor/crypton-x509-system-1.9.0'
assert m['archiveSha256']=='89ee819b78963a5c925fdaad80e2e64be6c0fedf4f24fc2b95eddb649b86cefe'
assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file()}
for f,h in m['files'].items():assert hashlib.sha256((d/f).read_bytes()).hexdigest()==h
assert (root/'licenses/crypton-x509-system-BSD-3-Clause.txt').read_bytes()==(d/'LICENSE').read_bytes()
sources += [f for f in d.rglob('*') if f.is_file()]
for name in ['System/X509.hs','System/X509/Common.hs','System/X509/Unix.hs','System/X509/MacOS.hs','System/X509/Win32.hs']:
 s=(d/name).read_text().replace('import Data.X509.CertificateStore','import StoreEffects')
 if name.endswith('Win32.hs'):
  i=s.index('foreign import stdcall');j=s.index('certOpenSystemStore ::');s=s[:i]+s[j:]
  s=s.replace('import System.Win32.Types','import SystemEffects (c_CertOpenSystemStore,c_CertCloseStore,c_CertEnumCertificatesInStore)\nimport System.Win32.Types')
 assert (folder/'controlled'/name).read_text()==s,name
sources += [f for f in folder.rglob('*') if f.is_file()]
prior=root/'core/src/test/resources/x509-store/data.jsonl.gz';sources.append(prior)
with gzip.open(prior,'rt') as f:
 old=json.loads(next(f));sources += [root/p for p in old['sources']];storeCases=[json.loads(l) for l in f]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cases=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cases) and m['cases']==len(cases) and m['groups']==groups(cases)
 if a.check_source:print('Verified',len(cases),'system X509 observations and',len(m['sources']),'source hashes');raise SystemExit
else:
 cases=[]
 def add(group,platform,**kw):
  r=dict(platform=platform,part='bytes',environment={},stores={},contexts=[],open=True,output=[],exit=0,failures=[]);r.update(kw);cases.append(dict(group=group,request=copy.deepcopy(r)))
 certs=[]
 for c in storeCases:
  if c['group']=='original-memory-certificate-read':certs.append(list(base64.b64decode(b''.join(bytes(c['request']['bytes']).splitlines()[1:-1]))))
 assert len(certs)==8
 def pem(raw,label='CERTIFICATE'):return list(b'-----BEGIN '+label.encode()+b'-----\n'+base64.b64encode(bytes(raw))+b'\n-----END '+label.encode()+b'-----\n')
 paths=['/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem','/etc/ssl/certs/','/system/etc/security/cacerts/','/usr/local/share/certs/','/etc/ssl/cert.pem']
 stores=dict(zip(paths,[[certs[0]],[certs[1]],None,[certs[2],certs[0]],[certs[3]]]))
 stores.update({'file':[certs[0]],'dir':[certs[1]],'system':[certs[2]],'empty':[],'bad':None,'root':'rootError','maybe':'maybeError','lazy':[None]})
 for platform,part,file,dir,system in itertools.product(['unix','mac','windows'],['ignore','empty','count','bytes'],[None,'','file','bad','root','maybe','lazy'],[None,'','dir','bad'],[None,'','system']):
  env={k:v for k,v in [('SSL_CERT_FILE',file),('SSL_CERT_DIR',dir),('SYSTEM_CERTIFICATE_PATH',system)] if v is not None}
  add('override-precedence-both-lookups-empty-literal-paths-and-lazy-results',platform,part=part,environment=env,stores=stores,contexts=[dict(encoding=1,bytes=certs[0])],output=pem(certs[0]))
 for platform,stage,tag,file,part in itertools.product(['unix','mac','windows'],['lookup:SSL_CERT_FILE','lookup:SSL_CERT_DIR'],['io','asn1','fatal'],[None,'file'],['ignore','bytes']):
  add('both-environment-effects-precede-selection-and-propagate',platform,part=part,environment={} if file is None else {'SSL_CERT_FILE':file},stores=stores,failures=[[stage,1,tag]])
 for stage,tag,part in itertools.product(['get:SYSTEM_CERTIFICATE_PATH']+['read:'+p for p in paths],['io','asn1','fatal'],['ignore','empty','bytes']):
  add('unix-only-getEnv-IO-fallback-and-ordered-store-effects','unix',part=part,stores=stores,failures=[[stage,1,tag]])
 for path,value,part in itertools.product(paths,[None,[],[certs[0]],'rootError','maybeError',[None]],['ignore','empty','count','bytes']):
  ss=copy.deepcopy(stores);ss[path]=value;add('unix-all-default-paths-lazy-Maybe-versus-store-roots','unix',part=part,stores=ss)
 for left,right,part in itertools.product(['rootError','maybeError',[None],[]],['rootError','maybeError',[None],[]],['ignore','empty','bytes']):
  ss={paths[0]:left,paths[-1]:right};add('unix-multiple-deferred-failure-order','unix',part=part,stores=ss)
 outputs=[[],pem(certs[0]),pem(certs[0])+pem(certs[1]),pem(certs[0],'PRIVATE KEY'),pem(certs[0],'certificate'),list(b'-----BEGIN broken'),pem([]),pem([48,0]),pem(certs[0])+list(b'-----BEGIN broken')]
 for output,exit,part in itertools.product(outputs,[0,1,127,-1],['ignore','empty','count','bytes']):add('mac-command-PEM-label-filter-whole-parse-and-ignored-exit','mac',part=part,output=output,exit=exit)
 for stage,tag,part in itertools.product(['process','wait'],['io','asn1','fatal'],['ignore','empty','bytes']):add('mac-process-and-wait-error-boundaries','mac',part=part,output=pem(certs[0]),failures=[[stage,1,tag]])
 rows=[dict(encoding=t,bytes=b) for t,b in itertools.product([0,1,2,65536,65537,2**32-1],[[],[48,0],certs[0]])]
 for row,part in itertools.product(rows,['ignore','empty','count','bytes']):add('windows-exact-encoding-tag-not-bitmask','windows',part=part,contexts=[row])
 for opened,stage,call,tag,part in itertools.product([False,True],['open','enum','close'],range(1,5),['io','asn1','fatal'],['ignore','empty','bytes']):
  add('windows-open-enumeration-catch-stack-and-normal-close','windows',part=part,open=opened,contexts=[dict(encoding=1,bytes=c) for c in certs[:3]],failures=[[stage,call,tag]])
 for n in [0,1,2,8,64,1024]:add('windows-long-enumeration-preserves-context-and-store-lifetimes','windows',part='count',contexts=[dict(encoding=1,bytes=certs[i%8]) for i in range(n)])
 for c in certs:
  raws=[c[:n] for n in range(len(c)+1)]
  for at in range(len(c)):
   b=c.copy();b[at]^=255;raws.append(b)
  for raw in raws:
   for before in [False,True]:
    cs=([certs[0]] if before else [])+[raw]
    add('mac-every-DER-truncation-mutation-before-versus-after-wait','mac',output=sum((pem(b) for b in cs),[]))
    add('windows-every-DER-truncation-mutation-and-deferred-map-errors','windows',contexts=[dict(encoding=1,bytes=b) for b in cs])
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=300);out.seek(0);results=[json.loads(l) for l in out]
assert len(results)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,results)):assert c['expected']==o,(i,c,o)
 print('Original system X509 verified',len(cases),'effect/decoder observations');raise SystemExit
for c,o in zip(cases,results):c['expected']=o
m=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases));target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cases)+'\n').encode())
print('Generated',len(cases),'system X509 observations; SHA256',m['caseHash'])
