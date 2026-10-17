#!/usr/bin/env python3
"""Package independently captured HMAC observations and bind current source bytes."""
from pathlib import Path
import argparse,gzip,hashlib,json
R=Path(__file__).resolve().parent.parent;D=R/'tools/hmac-reference';P=R/'core/src/test/resources/crypto-hmac'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args()
fixtureRoot=P if a.install or P.exists() else D/'fixtures/crypto-hmac'
if not a.check:fixtureRoot.mkdir(parents=True,exist_ok=True)
for name in ['Haskell','Literal','Dictionary','DictionaryExtra','Access','Unit','Borrow']:
 source=D/(name+'Observations.json');target=fixtureRoot/(source.name+'.gz')
 if a.check:assert gzip.decompress(target.read_bytes())==source.read_bytes(),name
 else:target.write_bytes(gzip.compress(source.read_bytes(),mtime=0))
paths=[f for f in D.rglob('*')if f.is_file()and 'work-in-progress'not in f.parts and 'fixtures'not in f.parts and '__pycache__'not in f.parts and f.name!='SourcePins.json']
paths += [R/'tools/extract-hmac-reference.py',R/'tools/import-hmac-fixtures.py',R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt']
paths += list(fixtureRoot.glob('*.gz'))
for name in ['CryptoHmac.kt','CryptoHmacAlgorithms.kt']:
 f=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/name
 paths.append(f if f.exists() else D/'work-in-progress'/name)
for name in ['CryptoHmacTest.kt','HmacReplay.kt','HmacDictionaryReplay.kt','HmacBorrowReplay.kt']:
 f=R/'core/src/test/kotlin/io/heapy/kotdoc/crypto'/name
 paths.append(f if f.exists() else D/'work-in-progress'/name)
for name in ['CryptoMd5.kt','CryptoSha32.kt','CryptoSha512.kt','CryptoSha3.kt','CryptoByteSpan.kt']:paths.append(R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/name)
for base,names in [('crypton-2.0.1',['Crypto/MAC/HMAC.hs','Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','Crypto/Internal/ByteArray.hs','tests/MAC/HMACSpec.hs','tests/Imports.hs','tests/Utils.hs','crypton.cabal']),('ram-0.22.1',['Data/ByteArray/Methods.hs','Data/ByteArray/Types.hs','Data/Memory/PtrMethods.hs'])]:
 paths += [R/'vendor'/base/name for name in names]
pins={str(f.relative_to(R)):hashlib.sha256(f.read_bytes()).hexdigest()for f in sorted(set(paths))};target=D/'SourcePins.json'
if a.check:assert json.loads(target.read_text())==pins,'current source/fixture bindings differ'
else:target.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS 7 original corpora,',len(pins),'source/fixture pins')
