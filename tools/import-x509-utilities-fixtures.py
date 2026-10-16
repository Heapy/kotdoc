#!/usr/bin/env python3
"""Observe unchanged X509 EC, chains and facade and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-utilities/data.jsonl.gz'
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
sources += [f for folder in ['haskell-x509-crl-reference','haskell-x509-utilities-reference'] for f in (root/'tools'/folder).rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))

original=(root/"vendor/crypton-2.0.1/tests/PubKey/ECCSpec.hs").read_text()
expected="{-# OPTIONS_GHC -Wno-deprecations #-}\nmodule OriginalEcPointVectors where\nimport qualified Crypto.PubKey.ECC.Types as ECC\nimport Crypto.Number.F2m (squareF2m)\nimport Crypto.Number.Basic (numBits)\nimport Test.QuickCheck\n\n"+original[original.index("instance Arbitrary ECC.Curve"):original.index("doPointValidTest ::")]+original[original.index("outOfSubgroup ::"):original.index("subgroupTests ::")]
assert (root/"tools/haskell-x509-utilities-reference/OriginalEcPointVectors.hs").read_text()==expected
signedSource=root/"core/src/test/resources/x509-signed/data.jsonl.gz"
sources.append(signedSource)
with gzip.open(signedSource,"rt") as f:
 m=json.loads(next(f));sources += [root/p for p in m["sources"]];signedCases=[json.loads(l) for l in f]
types=(root/"vendor/crypton-2.0.1/Crypto/PubKey/ECC/Types.hs").read_text()
parts=re.split(r"getCurveByName (SEC_[a-z0-9]+) =",types)[1:]
named=[]
for name,block in zip(parts[::2],parts[1::2]):
 ns=re.findall(r"(?<![A-Za-z0-9_])(0x[0-9a-fA-F]+|[0-9]+)(?![A-Za-z0-9_])",block);assert len(ns)==7
 p,aa,b,x,y,n,h=[str(int(v,0)) for v in ns]
 named.append((name,["binary" if "CurveBinary" in block else "prime",p,[aa,b,[x,y],n,h]]))
assert len(named)==33
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['group']=='original-ECC-curve-generator' for c in cases)==1000
 assert sum(c['group']=='original-ECC-point-validation-vectors' for c in cases)==12
 assert sum(c['group']=='original-ECC-outside-subgroup-on-curve-assertions' for c in cases)==20
 if a.check_source:print('Verified',len(cases),'X509 utility observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 def query(rs):return [json.loads(x) for x in subprocess.check_output([a.oracle],input=''.join(json.dumps(r)+'\n' for r in rs).encode(),timeout=240).splitlines()]
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--vectors'],timeout=120).splitlines()];assert len(cases)==32
 cases += [json.loads(x) for x in subprocess.check_output([a.oracle,'--generate','1000'],timeout=120).splitlines()]
 def add(group,api,**v):
  request=dict(api=api,part='all',count=2,tailError=False,otherTailError=False);request.update(v);cases.append(dict(group=group,request=request))
 for i,(name,model) in enumerate(named):
  c=['named',i];p,common=model[1:];aa,b,g,n,h=common
  for part in ['tag','bits','modulus','a','b','generator','order','cofactor','shown','all']:add('all-33-pinned-named-curve-parameters','curve',curve=c,part=part)
  add('named-and-explicit-curve-Eq','curveEqual',curve=c,other=model)
  points=[None,g,['0','0'],['-1','0'],['0','-1'],[p,'0'],['0',p]]+[[str(int(g[0])+dx),str(int(g[1])+dy)] for dx,dy in itertools.product([-1,0,1],repeat=2)]
  for point in points:add('prime-and-binary-point-validation','valid',curve=c,point=point)
  width=(int(p).bit_length()-(model[0]=='binary')+7)//8
  encoded=[4]+list(int(g[0]).to_bytes(width,'big'))+list(int(g[1]).to_bytes(width,'big'))
  for tag in range(256):add('all-serialized-point-format-octets','unserialize',curve=c,bytes=[tag]+encoded[1:])
  for length in range(len(encoded)+2):add('every-serialized-generator-truncation-and-extra-byte','unserialize',curve=c,bytes=encoded[:length] if length<=len(encoded) else encoded+[0])
  for at in range(1,len(encoded)):
   bad=encoded.copy();bad[at]^=1;add('every-serialized-generator-byte-mutation','unserialize',curve=c,bytes=bad)
  pub=['named',i,'error'];priv=['named',i,None]
  for api,key in [('publicCurve',pub),('privateCurve',priv)]:
   for part in ['tag','shown','all']:add('named-key-ignores-point-or-scalar','%s'%api,key=key,part=part)
  for api,key in [('publicName',pub),('privateName',priv)]:add('named-key-name-ignores-other-fields',api,key=key)
  if model[0]=='prime':
   for cofactor,seed,generator,point in itertools.product([h,'-1',None],['0',None],[encoded,[],'error'],[[],'error']):
    pub=['prime',point,aa,b,p,generator,n,cofactor,seed];priv=['prime',None,aa,b,p,generator,n,cofactor,seed]
    for api,key in [('publicCurve',pub),('privateCurve',priv)]:
     for part in ['tag','order','cofactor','shown','all']:add('explicit-prime-generator-validation-and-independent-fields',api,key=key,part=part)
    for api,key in [('publicName',pub),('privateName',priv)]:add('explicit-prime-name-ignores-generator-cofactor-seed-secret',api,key=key)
 for ty,p,aa,b,g,n,h in itertools.product(['prime','binary'],['-17','-1','0','1','2','17','19'],['0','2',None],['0','2',None],[None,['0','0'],'error'],['0',None],['1',None]):
  model=[ty,p,[aa,b,g,n,h]]
  for point in [None,['0','0'],['1','1'],['-1','0']]:add('custom-field-moduli-and-ordered-deferred-errors','valid',curve=model,point=point)
  for raw in [[],[0],[2],[3],[4],[4,0,0]]:add('custom-curve-format-and-size-demand','unserialize',curve=model,bytes=raw)
 for curve in [None,['prime',None,None],['binary',None,None],['prime','17',None],['binary','19',None]]:
  for point in [None,'error',['0',None],[None,'0'],['-1',None],['-1','0']]:add('point-and-curve-root-demand','valid',curve=curve,point=point)
  for raw in [[],[0],[2],[3],[4],None]:add('empty-and-compressed-points-ignore-curve','unserialize',curve=curve,bytes=raw)
 for i,(_name,c) in enumerate(named):
  if c[0]!='prime':continue
  for index in [2,3,4,6]:
   key=['prime','error',c[2][0],c[2][1],c[1],[],c[2][3],None,None];key[index]=None
   for api in ['publicName','privateName']:add('curve-name-short-circuit-field-order',api,key=key)
 for oid in [[1,3,132,0,i] for i in range(50)]+[[1,2,840,10045,3,1,i] for i in range(10)]+[[],[-1,2],[1,2,3],[2**128,1]]:add('all-curve-OIDs-and-unknowns','oid',oid=list(map(str,oid)))
 samples=[[],[65,66,90,97,122],list(range(256)),[195,132,195,150,195,156],[208,144,208,145],[0,255,92,34]]
 dns=[[],None]+[[[['2','5','4','3'],en,data]] for en,data in itertools.product(range(12),samples)]
 dns += [[[['1','2','3'],1,[65]],[['2','5','4','3'],1,[66]]],[[['2','5','4','3'],1,[66]],[['1','2','3'],1,[65]]],[[[],1,[]]],[[['-1','2'],1,[0]]],[[['2','5','4','3'],1,[65]],[['2','5','4','3'],1,[65]]]]
 for dn in dns:
  for api in ['hash','hashOld']:add('DN-hash-new-ASCII-lower-forced-UTF8-inner-versus-old-MD5-outer',api,dn=dn)
 # Concrete signed bytes are retained independent outputs of the preceding upstream observer.
 goodCerts=[c['expected'] for c in signedCases if c['group']=='concrete-certificate-key-families-and-CRL-optionals' and c['request']['part']=='bytes' and c['request']['kind']=='certificate']
 goodCrls=[c['expected'] for c in signedCases if c['group']=='concrete-certificate-key-families-and-CRL-optionals' and c['request']['part']=='bytes' and c['request']['kind']=='crl']
 assert len(goodCerts)==96 and len(goodCrls)==36
 rawModels=[[],None,[None],[[]],[goodCerts[0]],[goodCerts[0],goodCerts[1]],[goodCerts[0],None],[[],None],[goodCerts[0],[],None]]
 rawModels += [[b] for b in goodCerts]+[[goodCerts[0]]*i+[[]]+[None] for i in range(12)]
 rawModels += [[goodCerts[0][:n]] for n in range(len(goodCerts[0])+1)]
 rawModels += [[[2,0]],[[3,0]],[[5,0]],[[48,0]],[[48,128,0,0]]]
 for value,tail in itertools.product(rawModels,[False,True]):
  for api in ['rawChain','encodeChain','decodeChain']:
   for part,count in itertools.product(['tag','spine','bytes','shown','index','certificates'],[0,1,2]):add('certificate-chain-spine-head-first-failure-and-tail-demand',api,value=value,tailError=tail,part=part,count=count)
 for value,other,tail,otherTail in itertools.product(rawModels[:9],rawModels[:9],[False,True],[False,True]):
  for api in ['rawEqual','chainEqual']:add('raw-and-decoded-chain-Eq-error-order',api,value=value,other=other,tailError=tail,otherTailError=otherTail)
 for b in goodCerts:add('Data-X509-signed-certificate-facade','certificate',bytes=b)
 for b in goodCrls:add('Data-X509-signed-CRL-facade','crl',bytes=b)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['group']=='original-ECC-curve-generator']
 vectors=[json.loads(x) for x in subprocess.check_output([a.oracle,'--vectors'],timeout=120).splitlines()]
 assert vectors==[c for c in cases if c['group'].startswith('original-ECC-') and c['group']!='original-ECC-curve-generator']
 print('Original X509 utilities verified',len(cases),'observations, 1000 generator inputs and 32 original assertions');raise SystemExit
for c,o in zip(cases,outputs):
 if 'expected' in c:assert c['expected']==o,(c,o)
 c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'X509 utility observations; SHA256',manifest['caseHash'])
