#!/usr/bin/env python3
"""Independent crypton-asn1-types bit/character observations from original modules."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess,tempfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
folder=root/'tools/haskell-asn1-types-reference'
target=root/'core/src/test/resources/asn1-types/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
package=root/'vendor/crypton-asn1-types-0.4.1';manifest=json.loads((package/'SOURCE.json').read_text())
assert (manifest['package'],manifest['version'],manifest['url'],manifest['sha256'])==('crypton-asn1-types','0.4.1','https://hackage-content.haskell.org/package/crypton-asn1-types-0.4.1.tar.gz','61efc63877a0d3ddb992a793839d9427c1597e095a6e1f63c04e05fd3e4e302d')
assert set(manifest['files'])=={str(f.relative_to(package)) for f in package.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for path,h in manifest['files'].items():assert hashlib.sha256((package/path).read_bytes()).hexdigest()==h,path
notice=root/'licenses/crypton-asn1-types-BSD-3-Clause.txt';assert notice.read_bytes()==(package/'LICENSE').read_bytes()
assert set(folder.rglob('*.hs'))=={folder/'Asn1TypesReference.hs'}
cabal=(folder/'kotdoc-asn1-types-reference.cabal').read_text()
assert '../../vendor/crypton-asn1-types-0.4.1/src' in cabal
for name in ['Data.ASN1.BitArray','Data.ASN1.OID','Data.ASN1.Types.String']:assert name in cabal
domain=root/'vendor/crypton-asn1-encoding-0.10.0/tests/Tests.hs'
assert hashlib.sha256(domain.read_bytes()).hexdigest()==json.loads((domain.parents[1]/'SOURCE.json').read_text())['files']['tests/Tests.hs']
sources=[Path(__file__).resolve(),notice,domain]+[f for d in [folder,package] for f in d.rglob('*') if f.is_file()]
def hashes():return {str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source or a.verify_oracle:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 if a.check_source:print('Verified',len(cs),'original ASN.1 type observations and',len(m['sources']),'source hashes');raise SystemExit
else:
 cs=[];rng=random.Random(0x41534e3154595045)
 def add(group,api,**v):cs.append(dict(group=group,request=dict(api=api,**v)))
 points=[0,1,7,8,9,10,13,27,31,32,34,92,127,128,255,256,0x7ff,0x800,0xd7ff,0xd800,0xdbff,0xdc00,0xdfff,0xe000,0xffff,0x10000,0x1f600,0x10ffff]
 strings=[[],list(range(256)),points,[0xd800,0xdc00],[0x10000],[65,0,0x1f600]]+[[c] for c in points]
 strings += [[rng.randrange(0x110000) for _ in range(rng.randrange(65))] for _ in range(128)]
 for ps in strings:
  for enc in range(12):add('all-encodings-Char8-BMP-truncation-UTF8-and-UTF32','encode',encoding=enc,points=ps)
  add('original-IsString-UTF8-constructor','literal',points=ps)
 def decode(group,enc,bs):add(group,'decode',encoding=enc,bytes=bs,prefixes=[0,1,2,3,8])
 for enc in range(12):
  for byte in range(256):decode('every-encoding-every-byte-and-unsupported-encodings',enc,[byte])
  for bs in [[],list(range(256)),[0,65,0xd8,0,0xdc,0],[0,0,0,65,0,1,0xf6,0],[0,0x10,0xff,0xff],[0,0x11,0,0],[0x80,0,0,0],[255]*4]:decode('multi-byte-strings-and-invalid-lengths',enc,bs)
 for head,tail in itertools.product(range(256),[0,65,0x7f,0x80,0x81,0x9f,0xbf,0xc0,0xe0,0xff]):
  decode('UTF8-leading-continuation-validation-priority',1,[head,tail])
 for head,n in itertools.product(range(0xc0,256),range(5)):
  for prefix in [[],[65]]:decode('UTF8-truncation-overlong-range-and-lazy-prefix',1,prefix+[head]+[0x80]*n)
 for point in points+[0x110000,0x1fffff,0x7fffffff,0x80000000,0xffffffff]:
  bs=list(point.to_bytes(4,'big'));decode('UTF32-Char-range-surrogates-and-lazy-prefix',9,bs);decode('UTF32-Char-range-surrogates-and-lazy-prefix',9,[0,0,0,65]+bs)
 for _ in range(1000):
  decode('random-byte-sequences-all-encoding-functions',rng.randrange(12),[rng.randrange(256) for _ in range(rng.randrange(65))])
 variants=[[],[0],[127],[128],[255],[0,255]]
 for ea,eb,bs in itertools.product(range(12),range(12),variants):
  add('derived-Eq-Ord-encoding-then-unsigned-bytes','compare',first=dict(encoding=ea,bytes=bs),second=dict(encoding=eb,bytes=bs))
 for _ in range(500):
  ea=rng.randrange(12);eb=rng.choice([ea,rng.randrange(12)])
  ba=[rng.randrange(256) for _ in range(rng.randrange(8))];bb=rng.choice([ba,ba+[0],[rng.randrange(256) for _ in range(rng.randrange(8))]])
  add('derived-Eq-Ord-byte-lexicographic-boundaries','compare',first=dict(encoding=ea,bytes=ba),second=dict(encoding=eb,bytes=bb))
 def bits(group,bs,**extra):
  length=extra['length'] if extra['constructor']=='raw' else (len(bs)*8-extra['skip'])%(1<<64)
  indices=sorted(set([0,1,2,3,4,5,6,7,8,9,max(0,length-1),length,(1<<64)-1]))
  ops=[dict(op=op,index=i,**({'value':v} if op=='value' else {})) for i in indices for op,v in [('get',False),('set',False),('clear',False),('value',False),('value',True)]]
  add(group,'bits',bytes=bs,operations=ops,**extra)
 for bs,skip in itertools.product([[],[0],[255],[128,1],list(range(256))],[-(1<<63),-9,-1,0,1,7,8,9,17,(1<<63)-1]):
  bits('toBitArray-unchecked-padding-and-Word64-wrap',bs,constructor='padding',skip=skip)
 for length,bs in itertools.product([0,1,7,8,9,16,17,(1<<63)-1,1<<63,(1<<64)-1],[[],[0],[255],[128,1]]):bits('exported-BitArray-constructor-and-distinct-bounds-errors',bs,constructor='raw',length=length)
 for byte in range(256):bits('every-byte-every-bit-set-clear-and-get',[byte],constructor='raw',length=8)
 for _ in range(1000):
  bs=[rng.randrange(256) for _ in range(rng.randrange(530))];bits('upstream-Arbitrary-BitArray-domain-and-persistent-updates',bs,constructor='padding',skip=rng.randrange(8))
assert a.oracle,'--oracle required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cs).encode());inp.seek(0)
 subprocess.run([a.oracle],stdin=inp,stdout=out,check=True,timeout=180);out.seek(0);observed=[json.loads(line) for line in out]
assert len(cs)==len(observed),(len(cs),len(observed))
if a.verify_oracle:
 for i,(c,o) in enumerate(zip(cs,observed)):assert c['expected']==o,(i,c,o)
 print('Independent oracle agrees on all',len(cs),'ASN.1 type observations');raise SystemExit
for c,o in zip(cs,observed):c['expected']=o
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for x in [m]+cs:gz.write((json.dumps(x,separators=(',',':'))+'\n').encode())
print('Wrote',len(cs),'original observations;',target.stat().st_size,'bytes;',m['caseHash'])
