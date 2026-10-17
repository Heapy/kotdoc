#!/usr/bin/env python3
from pathlib import Path
import argparse,json,gzip,hashlib
R=Path(__file__).resolve().parent.parent;D=R/'tools/rsa-oaep-reference';P=R/'core/src/test/resources/crypto-rsa-oaep';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args();P=P if a.install or P.exists()else D/'fixtures/crypto-rsa-oaep'
if not a.check:P.mkdir(parents=True,exist_ok=True)
for name in ['Observations','DemandObservations','DemandExtraObservations','RandomObservations','RandomExtraObservations','CallbackObservations','CallbackExtraObservations','BoundaryObservations','AccessObservations']:
 f=D/(name+'.json');target=P/(f.name+'.gz')
 if a.check:assert gzip.decompress(target.read_bytes())==f.read_bytes(),name
 else:target.write_bytes(gzip.compress(f.read_bytes(),mtime=0))
paths=[f for f in D.rglob('*')if f.is_file()and '__pycache__'not in f.parts and 'work-in-progress'not in f.parts and 'fixtures'not in f.parts and f.name!='SourcePins.json']
paths +=[R/'tools/extract-rsa-oaep-reference.py',R/'tools/import-rsa-oaep-fixtures.py',R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt']+list(P.glob('*.gz'))
for name in ['CryptoMaskGen.kt','CryptoRsaOaep.kt']:
 f=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/name;paths.append(f if f.exists()else D/'work-in-progress'/name)
for name in ['CryptoRsaOaepTest.kt','OaepReplay.kt','OaepDemandReplay.kt','OaepRandomReplay.kt','OaepCallbackReplay.kt','OaepBoundaryReplay.kt','MgfAccessReplay.kt']:
 f=R/'core/src/test/kotlin/io/heapy/kotdoc/crypto'/name;paths.append(f if f.exists()else D/'work-in-progress'/name)
for name in ['CryptoRsa','CryptoModular','CryptoNumberSerialize','CryptoRandom','CryptoNumberBasic','CryptoNumberGenerate','CryptoNumberPrime','CryptoProbabilistic','CryptoMontgomery','CryptoHmac','CryptoHmacAlgorithms','CryptoMd5','CryptoSha32','CryptoSha512','CryptoSha3']:
 paths.append(R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/(name+'.kt'))
paths +=[R/'core/src/main/kotlin/io/heapy/kotdoc/x509'/n for n in ['X509PublicKeys.kt','X509PrivateKeys.kt']]
for name in ['Crypto/PubKey/RSA/OAEP.hs','Crypto/PubKey/MaskGenFunction.hs','Crypto/PubKey/Internal.hs','Crypto/PubKey/RSA/Types.hs','Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','Crypto/Random/Types.hs','tests/PubKey/OAEPSpec.hs','tests/Imports.hs','tests/Utils.hs','crypton.cabal']:paths.append(R/'vendor/crypton-2.0.1'/name)
pins={str(f.relative_to(R)):hashlib.sha256(f.read_bytes()).hexdigest()for f in sorted(set(paths))};target=D/'SourcePins.json'
if a.check:assert json.loads(target.read_text())==pins,'source/fixture bindings differ'
else:target.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS 9 original corpora,',len(pins),'source/fixture pins')
