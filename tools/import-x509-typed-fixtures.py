#!/usr/bin/env python3
"""Observe unchanged X509 typed extensions and retain source provenance, with original test generators and round-trip property."""
import argparse,collections,gzip,hashlib,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
target=root/'core/src/test/resources/x509-typed/data.jsonl.gz'
sources=[Path(__file__).resolve()]
for name,archive,notice in [
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
sources += [f for f in (root/'tools/haskell-x509-typed-reference').rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
def groups(cs):return dict(collections.Counter(c['group'] for c in cs))
original=(root/'vendor/crypton-x509-1.9.2/Tests/Tests.hs').read_text()
instances=original[original.index('instance Arbitrary ExtKeyUsageFlag'):original.index('instance Arbitrary Certificate')]
prop=original[original.index('property_extension_id ::'):original.index('main =')]
adapter='-- Unchanged generators/property from crypton-x509 1.9.2 Tests/Tests.hs.\nmodule OriginalTypedExtensionTests where\nimport Test.QuickCheck\nimport Data.List(nub,sort)\nimport Data.X509.Ext\n\n'+instances+prop
assert (root/'tools/haskell-x509-typed-reference/OriginalTypedExtensionTests.hs').read_text()==adapter

if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
 assert manifest['sources']==hashes() and manifest['caseHash']==digest(cases) and manifest['cases']==len(cases) and manifest['groups']==groups(cases)
 assert sum(c['request']['api']=='property' and c['group'].startswith('original-') for c in cases)==2000
 if a.check_source:print('Verified',len(cases),'X509 typed-extension observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
 assert a.oracle,'--oracle required'
 cases=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()];assert len(cases)==2000
 rng=random.Random(0x5835303954595045)
 def add(group,api,**v):cases.append(dict(group=group,request=dict(api=api,**v)))
 def o(xs):return list(map(str,xs))
 oids=[o([2,5,29,n]) for n in [19,15,37,14,17,35,31,30]]+[o([2,16,840,1,113730,1,13])]
 st=['start',['sequence']];en=['end',['sequence']]
 def ctx(n):return ['container',2,n]
 def other(n,b):return ['other',2,n,b]
 texts=[[],[0,1,7,8,9,10,13,27,31,32,34,92,127,128,255],[256,0x20ac,0x1f600],[0xd800,0xdfff],[0x10ffff]]
 dn=[[[o([2,5,4,3]),1,[97]]],[],[[[],0,[]],[o([1,2,840]),5,[65,66]]]]
 names=[[ty,t] for ty in ['rfc822','dns','uri','xmpp','srv'] for t in texts]+[['ip',[]],['ip',list(range(256))]]+[['dn',d] for d in dn]
 purposes=list(range(6))+[['unknown',oi] for oi in [[],['0'],oids[0],o([1,3,6,1,5,5,7,3,1]),o([2**256,-2**128])]]
 subs=[[n,mi,ma] for n in names[:3]+names[-3:] for mi,ma in [('0',None),('-1','0'),('1','2'),(str(2**256),str(-2**256))]]
 points=[['full',[]],['full',names[:3]],['relative',dn[0]],['relative',[]]]
 payloads=[[],list(range(256))]+[[n] for n in range(256)]+[[rng.randrange(256) for _ in range(n)] for n in [2,7,8,9,15,16,17,31,32,63,64,127,128,129,255,256,257]]
 values=[[[b,n] for b,n in itertools.product([False,True],[None,'-1','0','1',str(2**512)])],
         [[],list(range(9)),list(range(8,-1,-1)),[0,0,8]]+[[i] for i in range(9)],
         [[],purposes[:6],purposes, purposes[::-1]]+[[p] for p in purposes],
         payloads,[[],names,names[::-1]]+[[n] for n in names],payloads,
         [[],points]+[[p] for p in points],
         [[[],[]],[subs[:1],[]],[[],subs[-1:]],[subs,subs[::-1]]]+[[[s],[]] for s in subs],payloads]
 add('recognized-OID-order','recognized')
 for k in range(9):
  add('metadata-independent-of-value','metadata',kind=k)
  for v in values[k]:
   add('derived-model-Show-Eq-and-Ord','value',kind=k,value=v,other=v)
   add('derived-model-inequality','value',kind=k,value=v,other=values[k][0])
   for part in ['values','bytes']:add('extension-ASN1-and-DER-encoding','encode',kind=k,value=v,part=part,count=9999)
   for api in ['roundtrip','roundtripBytes']:add('original-roundtrip-including-upstream-asymmetries',api,kind=k,value=v,part='all')
   for critical in [False,True]:add('extensionEncode-critical-raw-content','encodeRaw',kind=k,value=v,critical=critical,part='all')
 for typ,vs in [('alt',names),('purpose',purposes),('subtree',subs),('point',points)]:
  for v,w in itertools.product(vs,repeat=2):add('derived-'+typ+'-model-pairwise','model',type=typ,value=v,other=w)
 for reason,fr,nx in itertools.product([False,True],range(-1,10),range(9)):
  add('derived-Enum-Show-Eq-Ord-and-out-of-range','enum',reason=reason,method='value',**{'from':fr},next=nx)
 for reason,fr,nx,end,mode in itertools.product([False,True],range(9),range(9),range(9),['from','then','to','thenTo']):
  add('derived-enumeration-ranges-including-zero-step','enum',reason=reason,method='range',**{'from':fr},next=nx,end=end,mode=mode,count=12)
 tokens=[['null'],['boolean',False],['boolean',True],['integer','-1'],['integer','0'],['oid',[]],['oid',oids[0]],['octets',[]],['bits','9',[0,0]],['real','0'],['enumerated','0'],['string',1,[]],other(0,[]),other(1,[]),other(2,[97]),st,en,['start',['set']],['end',['set']],['start',ctx(0)],['end',ctx(0)],['end',ctx(9)]]
 canonical=[[st,en],[st,['boolean',True],['integer','0'],en],[['bits','9',[255,128]]],[st,['oid',o([1,3,6,1,5,5,7,3,1])],['oid',oids[0]],en],[['octets',[0,255]]],[st,other(2,[97]),other(7,[127,0,0,1]),en],[st,other(0,[1,2]),en],[st,['start',ctx(0)],st,other(2,[97]),en,['end',ctx(9)],en]]
 for k in range(9):
  def dec(group,vs,tailError=False,parts=['tag','all']):
   for part in parts:add(group,'decode',kind=k,values=vs,part=part,tailError=tailError,count=2)
  for vs in canonical:
   for n in range(len(vs)+1):dec('every-canonical-truncation',vs[:n])
   dec('canonical-with-failing-tail',vs,True)
   for i,t in itertools.product(range(len(vs)),tokens):dec('every-position-constructor-mutation',vs[:i]+[t]+vs[i+1:])
  for _ in range(120):dec('random-malformed-ASN1',rng.choices(tokens,k=rng.randrange(15)))
  for n,part in itertools.product(range(9),['spine','kinds','values','shown']):
   for v in values[k][:4]:add('encoding-prefix-demand','encode',kind=k,value=v,count=n,part=part)
 # Separately observe list spine, payload and re-encoding demand on invalid fields.
 lazytokens=[['oidError'],['integerError'],['bitsError'],['stringError'],['octetsError'],['otherError',2,2],['otherError',2,4],['otherError',2,7]]
 for k in range(9):
  for vs in [[t] for t in lazytokens]+[[st,t,en] for t in lazytokens]+[[st,['oidError'],['null'],en],[st,other(2,[97]),['null'],en]]:
   for part,n in itertools.product(['tag','all','payloadSpine','payloadPrefix','reencode','rekinds'],[0,1,2]):add('deferred-fields-list-payload-and-constructor-demand','decode',kind=k,values=vs,tailError=False,part=part,count=n)
 # GeneralNames composed forms, unsupported encodings, malformed DN and parser remainders.
 for oid,enc,b in itertools.product([o([1,3,6,1,5,5,7,8,n]) for n in [5,7,99]],range(12),[[],[0],[0xc0,0xaf],[0xf0,0x9f,0x98,0x80],[0xd8,0]]):
  vs=[st,['start',ctx(0)],['oid',oid],['start',ctx(0)],['string',enc,b],['end',ctx(0)],['end',ctx(0)],en]
  for part in ['tag','all']:add('composed-XMPP-DNSSRV-encoding-and-malformed-text','decode',kind=4,values=vs,tailError=False,part=part,count=2)
 for b in payloads[:4]+[[0x30,0],[0x30,2,0x31,0],[0x30,4,0x31,2,5,0]]:
  add('GeneralNames-nested-DN-DER','decode',kind=4,values=[st,other(4,b),en],tailError=False,part='all',count=2)
 # NameConstraints accepts implicit subtrees, mismatched Context End, duplicates and trailing input.
 for tag,endtag,mi,ma in itertools.product([0,1],[0,1,9],[None,[],[2,1,0],[2,1,255],[5,0]],[None,[2,1,3],[2,1,255],[]]):
  subtree=[st,other(2,[97])]+([] if mi is None else [other(0,mi)])+([] if ma is None else [other(1,ma)])+[en]
  vs=[st,['start',ctx(tag)]]+subtree+[['end',ctx(endtag)],en,['oidError']]
  for part in ['tag','all']:add('NameConstraints-implicit-subtrees-distance-End-and-ignored-tail','decode',kind=7,values=vs,tailError=True,part=part,count=2)
 ber=[[],[5,0],[2,0],[2,1,7],[6,0],[4,0],[3,2,7,128],[0x30,0],[0x30,0x80,0,0],[0xff],[0,0]]+[[n,0] for n in range(256)]
 ber += [[rng.randrange(256) for _ in range(rng.randrange(25))] for _ in range(100)]
 for k,b in itertools.product(range(9),ber):
  for part in ['tag','all']:add('BER-framing-versus-typed-errors','decodeBytes',kind=k,bytes=b,part=part,count=2)
 # Every valid key flag subset, storage/length mismatches and out-of-enum set bits.
 for mask in range(512):
  flags=[i for i in range(9) if mask & (1<<i)]
  add('all-512-key-usage-subsets','property',kind=1,value=flags)
 for length,b in itertools.product([0,1,7,8,9,10,15,16,17,64,2**64-1],[[],[0],[128],[255],[0,0],[0,64],[0,128],[255,255]]):
  for part,n in itertools.product(['tag','all','payloadSpine','payloadPrefix','rekinds'],[0,1,2]):
   # A long zero scan is intentionally not forced: the original enumerates Word64 indices.
   if length>64 and part not in ['tag','rekinds'] and not (part.startswith('payload') and n==0):continue
   add('bit-length-storage-and-undefined-flag-demand','decode',kind=1,values=[['bits',str(length),b]],tailError=False,part=part,count=n)
 for k,v in [(1,'listError'),(2,'listError'),(3,None),(5,None),(8,None)]:
  for n,part in itertools.product(range(5),['spine','kinds','values','shown','bytes']):add('deferred-encoding-list-and-byte-payload','encode',kind=k,value=v,count=n,part=part)
  for part in ['oid','critical','all']:add('raw-encoder-independent-fields','encodeRaw',kind=k,value=v,critical=True,part=part)
 for k in [3,5,8]:
  for part in ['tag','all']:add('decodeBytes-outer-constructor-before-content','decodeBytes',kind=k,bytes=None,part=part,count=0)
 for first,second in itertools.product([0,1],repeat=2):
  vs=[st,['start',ctx(first)],st,other(2,[97]),en,['end',ctx(9)],['start',ctx(second)],st,other(2,[98]),en,['end',ctx(8)],en]
  add('NameConstraints-duplicate-groups-last-wins','decode',kind=7,values=vs,tailError=True,part='all',count=2)
 # Successful raw values are obtained exclusively from the unchanged upstream encoder.
 requests=[dict(api='encodeRaw',kind=k,value=vs[0],critical=False,part='all') for k,vs in enumerate(values)]
 outputs=subprocess.check_output([a.oracle],input=''.join(json.dumps(r)+'\n' for r in requests).encode(),timeout=120).splitlines()
 for k,line in enumerate(outputs):
  raw=json.loads(line)
  if isinstance(raw,dict):continue
  raws=[raw,[oids[k],None,raw[2]],[oids[k],None,None],[oids[k],False,[255]],[oids[(k+1)%9],None,None],[None,None,None]]
  for r,part in itertools.product(raws,['maybe','tag','all']):add('raw-OID-gating-Maybe-Either-and-field-demand','decodeRaw',kind=k,raw=r,part=part,count=2)
  for ext,api,part in itertools.product([None,[],[raw],[raw,raw],[[oids[k],False,[255]],raw],[[oids[(k+1)%9],None,None],raw],'listError'],['get','getE'],['maybe','tag','all']):add('accessors-first-match-versus-first-success','get' if api=='get' else 'getE',kind=k,extensions=ext,part=part,count=2)
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cases).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=240);out.seek(0);outputs=[json.loads(x) for x in out]
assert len(outputs)==len(cases)
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cases,outputs)):assert c['expected']==o,(i,c,o)
 fresh=[json.loads(x) for x in subprocess.check_output([a.oracle,'--generate-tests','1000'],timeout=120).splitlines()]
 assert fresh==[{k:v for k,v in c.items() if k!='expected'} for c in cases if c['request']['api']=='property' and c['group'].startswith('original-')]
 print('Original X509 typed extensions verified',len(cases),'observations and 2000 original generator inputs');raise SystemExit
for c,o in zip(cases,outputs):c['expected']=o
manifest=dict(cases=len(cases),sources=hashes(),groups=groups(cases),caseHash=digest(cases))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as z:z.write(('\n'.join(json.dumps(c,separators=(',',':')) for c in [manifest]+cases)+'\n').encode())
print('Generated',len(cases),'X509 typed-extension observations; SHA256',manifest['caseHash'])
