#!/usr/bin/env python3
from pathlib import Path
import gzip,json,hashlib,argparse
R=Path(__file__).resolve().parent.parent;D=R/'tools/md2-reference';P=R/'core/src/test/resources/crypto-md2';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args();P=P if a.install or P.exists()else D/'fixtures/crypto-md2'
if not a.check:P.mkdir(parents=True,exist_ok=True)
for n in ['NativeObservations','TraceObservations','CounterObservations','HaskellObservations','LiteralObservations','DemandObservations','BorrowObservations','AccessObservations']:
 f=D/(n+'.json');target=P/(f.name+'.gz')
 if a.check:assert gzip.decompress(target.read_bytes())==f.read_bytes(),n
 else:target.write_bytes(gzip.compress(f.read_bytes(),mtime=0))
paths=[f for f in D.rglob('*')if f.is_file()and '__pycache__'not in f.parts and 'fixtures'not in f.parts and 'work-in-progress'not in f.parts and f.name!='SourcePins.json']+list(P.glob('*.gz'))
paths +=[R/'tools/extract-md2-reference.py',R/'tools/import-md2-fixtures.py',R/'licenses/crypton-md2-BSD-2-Clause.txt',R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt',R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoByteSpan.kt',R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoHmac.kt']
for n in ['CryptoMd2.kt','CryptoMd2Hash.kt']:
 f=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/n;paths.append(f if f.exists()else D/'work-in-progress'/n)
for n in ['CryptoMd2Test.kt','Md2Replay.kt','Md2BorrowReplay.kt','Md2AccessReplay.kt']:
 f=R/'core/src/test/kotlin/io/heapy/kotdoc/crypto'/n;paths.append(f if f.exists()else D/'work-in-progress'/n)
for n in ['cbits/crypton_md2.c','cbits/crypton_md2.h','cbits/crypton_bitfn.h','Crypto/Hash/MD2.hs','Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','tests/HashSpec.hs','tests/Utils.hs','tests/Imports.hs','crypton.cabal']:paths.append(R/'vendor/crypton-2.0.1'/n)
pins={str(f.relative_to(R)):hashlib.sha256(f.read_bytes()).hexdigest()for f in sorted(set(paths))};target=D/'SourcePins.json'
if a.check:assert json.loads(target.read_text())==pins,'source bindings differ'
else:target.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS 8 current corpora;',len(pins),'source pins; histories separate')
