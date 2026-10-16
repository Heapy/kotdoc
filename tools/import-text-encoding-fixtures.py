#!/usr/bin/env python3
"""Independent GHC 9.12.2 TextEncoding/C-string recovery observations."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--original-test');p.add_argument('--platform',required=True,choices=['macos-aarch64','linux-x86_64']);p.add_argument('--check-source',action='store_true');args=p.parse_args()
folder=root/'tools/haskell-text-encoding-reference'
target=root/'core/src/test/resources/ghc-text-encoding'/f'{args.platform}.jsonl.gz'
sources=['tools/import-text-encoding-fixtures.py','licenses/GHC-base-BSD-3-Clause.txt']
for directory in ['vendor/ghc-environment-9.12.2','vendor/ghc-text-encoding-9.12.2','tools/haskell-text-encoding-reference']:
 sources += [str(f.relative_to(root)) for f in (root/directory).rglob('*') if f.is_file()]
for directory in ['vendor/ghc-environment-9.12.2','vendor/ghc-text-encoding-9.12.2']:
 manifest=json.loads((root/directory/'SOURCE.json').read_text());assert manifest['revision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc'
 for f,sha in manifest['files'].items():assert hashlib.sha256((root/directory/f).read_bytes()).hexdigest()==sha,f
assert (root/'vendor/ghc-environment-9.12.2/LICENSE.txt').read_bytes()==(root/'licenses/GHC-base-BSD-3-Clause.txt').read_bytes()
def hashes():return {f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs) and m['platform']==args.platform
 assert m['compiler']=='9.12.2' and m['originalTest']=='encoding005'
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original GHC TextEncoding observations,',args.platform,'and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.original_test
original=subprocess.run([args.original_test],capture_output=True,check=True)
assert original.stdout==(root/'vendor/ghc-text-encoding-9.12.2/libraries/base/tests/IO/encoding005.stdout').read_bytes(),original.stdout
cs=[]
def add(group,encoding,api,batches,terminate=True,char8=False,posixOnly=False):
 cs.append(dict(group=group,request=dict(encoding=encoding,api=api,batches=batches,terminate=terminate,char8=char8,posixOnly=posixOnly)))
builtins=['UTF-8','ASCII','ISO-8859-1','UTF-16','UTF-16LE','UTF-16BE','UTF-32','UTF-32LE','UTF-32BE']
policies=['','//IGNORE','//TRANSLIT','//ROUNDTRIP']
# UTF-8 gets every byte pair; other codecs get every lead against all
# recovery/width boundaries, independently from the existing ROUNDTRIP corpus.
for enc,suffix in itertools.product(builtins,policies):
 trails=range(256) if enc=='UTF-8' else [0,1,9,31,63,65,127,128,159,191,192,223,224,239,254,255]
 pairs=[[lead,trail] for lead in range(256) for trail in trails]
 for start in range(0,len(pairs),1024):
  add('byte-pair-independent-decoder-and-recovery-boundaries',enc+suffix,'decode',pairs[start:start+1024])
 add('every-single-byte-and-empty-input',enc+suffix,'decode',[[]]+[[b] for b in range(256)])
edges=[[],[0],[255],[0xef,0xbb,0xbf],[0xfe,0xff],[0xff,0xfe],[0,0,0xfe,0xff],[0xff,0xfe,0,0],
 [0xed,0xa0,0x80],[0xf0,0x90,0x80,0x80],[0xf4,0x8f,0xbf,0xbf],[0xf4,0x90,0x80,0x80],
 [0xe2,0x82],[0xf0,0x90,0x80],[255,254,0,216,0,220],[254,255,216,0,220,0],
 [27,36,66,36,34,27,40,66,97],[27,36,66,255,36,34,27,40,66]]
points=[[],[0],[65],[0xd800],[0xdc00],[0xdc7f],[0xdc80],[0xdcff],[0xdd00],[0xffff],[0x10000],[0x10ffff],
 [0x7f,0x80,0x81,0xe9],[0xfe,0xff,0x100,0x101,0x100,0xff,0xfe],
 [0x100,0x1f600,0xdcff,65],[0x1f600,0x1f600,0xd800],[0xdc80,0xdcff,0xdc00,0xd800],
 list(range(256)),list(range(0xd780,0xe080)),[65]*63+[0x1f600,0xd800,66]*9]
native=['CP1251','CP1252','SHIFT_JIS','ISO-2022-JP','UCS-2LE','UCS-4BE']
for enc,suffix in itertools.product(builtins+native,policies):
 add('BOM-truncated-surrogates-and-stateful-decoder-recovery',enc+suffix,'decode',edges,posixOnly=enc in native)
 for terminate in [False,True]:
  add('CString-buffer-growth-BOM-state-mutable-transliteration-roundtrip-and-errors',enc+suffix,'encode',points,terminate,posixOnly=enc in native)
 add('charIsRepresentable-NUL-encoding-C-NUL-truncation-and-recovery',enc+suffix,'representable',[list(range(256)),list(range(0xd780,0xe080)),[0xffff,0x10000,0x10ffff]],posixOnly=enc in native)
for name in ['utf8','uTf-16le','U-T-F-3-2','iso-ir-6','IBM367','l1','csISOLatin1','bad-encoding','UTF-8//ignore','UTF-8//TRANSLIT//IGNORE','UTF-8/','UTF-8//','ASCII//ROUNDTRIPx']:
 for suffix in policies:add('canonical-encoding-names-aliases-exact-suffix-and-unknown-encoding',name+suffix,'decode',[[]])
for terminate in [False,True]:add('char8-unchecked-modulo256-all-surrogates-and-range-boundaries','ignored','encode',points+[list(range(0x201))],terminate,char8=True)
add('char8-all-byte-values','ignored','decode',edges+[list(range(256))],char8=True)
add('char8-C-string-representability','ignored','representable',points,char8=True)
rng=random.Random(9122)
for enc,suffix in itertools.product(builtins+native,policies):
 for i in range(4):
  add('deterministic-invalid-encoding-recovery-and-buffer-retries',enc+suffix,'encode',[[rng.choice([rng.randrange(0x110000),0x3f,0xd800,0xdc80,0xdcff,0x100,65]) for _ in range(rng.randrange(1,80))] for _ in range(12)],bool(i%2),posixOnly=enc in native)
  add('deterministic-malformed-decoder-recovery',enc+suffix,'decode',[[rng.randrange(256) for _ in range(rng.randrange(1,80))] for _ in range(12)],posixOnly=enc in native)
# File-based I/O avoids pipe deadlocks with this large independent corpus.
import tempfile
with tempfile.TemporaryFile(mode='w+') as src,tempfile.TemporaryFile(mode='w+') as dst:
 for c in cs:src.write(json.dumps(c['request'])+'\n')
 src.seek(0);process=subprocess.run([args.oracle],stdin=src,stdout=dst,stderr=subprocess.PIPE,text=True)
 assert process.returncode==0,process.stderr
 dst.seek(0);observed=[json.loads(s) for s in dst]
assert len(observed)==len(cs)
for c,r in zip(cs,observed):c['expected']=r
m=dict(upstream='GHC 9.12.2 actual mkTextEncoding, builtin/iconv codecs, all four recovery modes, char8 and Foreign.C.String.Encoding',platform=args.platform,compiler='9.12.2',originalTest='encoding005',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for v in [m]+cs:gz.write((json.dumps(v,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
