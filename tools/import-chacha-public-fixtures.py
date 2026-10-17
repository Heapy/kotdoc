#!/usr/bin/env python3
"""Actual pinned Haskell public API expectations; Kotlin is never consulted."""
import argparse, pathlib,json,subprocess,gzip,hashlib,itertools
ROOT=pathlib.Path(__file__).resolve().parents[1]; D=ROOT/'tools/chacha-public-reference';OUT=ROOT/'core/src/test/resources/crypto-chacha-public/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify-oracle',action='store_true');a=p.parse_args()
def pattern(n,s=0):return[(i*73+s)%256 for i in range(n)]
requests=[]
for kind in ['state','x']:
 for rounds,keylen,noncelen in itertools.product([8,12,20],[16,32]if kind=='state'else[32],[8,12]if kind=='state'else[24]):
  for counter in ['0','4294967294','4294967295','4294967296','18446744073709551615']:
   for length in [0,1,15,16,17,63,64,65,127,128,129,513]:
    requests.append(dict(kind=kind,rounds=rounds,key=pattern(keylen,7),nonce=pattern(noncelen,11),steps=[dict(op='set64',value=counter),dict(op='generate',length=length),dict(op='set32',value='4294967295'),dict(op='combine',bytes=pattern(65,17)),dict(op='generate',length=0),dict(op='generate',length=-1),dict(op='generate',length=65)]))
for kind in ['state','x']:
 for offset in range(1,64):
  for length in [0,1,64-offset,65-offset,64,65]:
   requests.append(dict(kind=kind,rounds=20,key=pattern(32,offset),nonce=pattern(24 if kind=='x'else 12,offset+1),steps=[dict(op='generate',length=offset),dict(op='set64',value='18446744073709551615'),dict(op='generate',length=length),dict(op='combine',bytes=pattern(129,offset)),dict(op='generate',length=65)]))
for seedlen in [0,1,31,39,40,41,64,100]:
 for length in [0,1,15,16,17,31,32,33,63,64,65,129]:
  requests.append(dict(kind='simple',seed=pattern(seedlen,19),steps=[dict(op='generate',length=length),dict(op='generate',length=0),dict(op='set64',value='18446744073709551615'),dict(op='block',rounds=8),dict(op='set32',value='4294967295'),dict(op='block',rounds=12),dict(op='block',rounds=20),dict(op='generate',length=17)]))
for rounds in [-2147483648,-1,0,1,7,8,12,20,21,255,256,264,268,276,2147483647]:
 for kind in ['state','x']:
  for keylen,noncelen in [(0,0),(15,7),(16,8),(32,8),(32,12),(32,24),(33,25)]:requests.append(dict(kind=kind,rounds=rounds,key=pattern(keylen),nonce=pattern(noncelen)))
 requests.append(dict(kind='simple',seed=pattern(40),steps=[dict(op='block',rounds=rounds)]))
# Reuse actual original generators/literals, not generated Kotlin data.
for row in json.loads((ROOT/'tools/chacha-core-reference/HaskellObservations.json').read_text()):
 r=row['request'];sizes=r['sizes'];start=0;steps=[]
 for n in sizes:
  if r['operation']=='combine':steps.append(dict(op='combine',bytes=r['bytes'][start:start+n]));start+=n
  else:steps.append(dict(op='generate',length=n))
 requests.append(dict(kind='x'if r.get('xchacha')else'state',rounds=r['rounds'],key=r['key'],nonce=r['iv'],steps=[dict(op='set32',value=str(r.get('counter',0)))]+steps,original=row['expected']['bytes']))
for name in ['generate-zero','generate-negative','combine-empty','initialize-key','initialize-nonce','initialize-rounds','initializeX-key','initializeX-nonce','initializeSimple-seed','block-invalid','simple-zero-bottom']:
 requests.append(dict(kind='demand',name=name))
proc=subprocess.Popen([a.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
rows=[]
for i,r in enumerate(requests):
 proc.stdin.write(json.dumps(r,separators=(',',':'))+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,(i,r)
 expected=json.loads(line)
 if 'original'in r:
  assert sum((step['bytes']for step in expected['steps']),[])==r['original'],i
 rows.append(dict(request=r,expected=expected))
proc.terminate();proc.wait()
paths=[D/'ChaChaPublicReference.hs',D/'kotdoc-chacha-public-reference.cabal',D/'cabal.project',D/'cabal.project.freeze',pathlib.Path(__file__),ROOT/'vendor/crypton-2.0.1/Crypto/Cipher/ChaCha.hs',ROOT/'tools/chacha-core-reference/HaskellObservations.json',ROOT/'tools/chacha-core-reference/OriginalChaChaCoreSpec.hs',ROOT/'tools/chacha-core-reference/OriginalSuite.txt',ROOT/'licenses/crypton-BSD-3-Clause.txt',ROOT/'licenses/ram-BSD-3-Clause.txt',D/'README.md',D/'CompilerProfile.json',D/'SourceMatrix.json',ROOT/'tools/extract-chacha-public-reference.py',D/'original/Crypto/Cipher/ChaCha.hs',D/'original/tests/StreamCipher/ChaChaSpec.hs',ROOT/'vendor/ram-0.22.1/Data/ByteArray/Methods.hs',ROOT/'vendor/crypton-2.0.1/SOURCE.json']
header=dict(cases=len(rows),sources={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths})
data='\n'.join(json.dumps(r,separators=(',',':'))for r in [header]+rows)+'\n'
if a.verify_oracle:assert gzip.decompress(OUT.read_bytes()).decode()==data
else:OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_bytes(gzip.compress(data.encode(),mtime=0))
print('PASS',len(rows),'actual Haskell public observations;',len(paths),'pins')
