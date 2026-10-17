#!/usr/bin/env python3
from pathlib import Path
import json,gzip,hashlib,re,argparse
R=Path(__file__).resolve().parent.parent;D=R/'tools/pss-reference';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args();target=R/'core/src/test/resources/crypto-rsa-pss'if a.install or(R/'core/src/test/resources/crypto-rsa-pss').exists()else D/'fixtures/crypto-rsa-pss'
names=['SourceCases','Observations','DemandObservations','DictionaryObservations','RandomObservations','RandomExtraObservations','CallbackObservations','MutationObservations','DigestObservations','BoundaryObservations']
if not a.check:target.mkdir(parents=True,exist_ok=True)
for name in names:
 b=(D/(name+'.json')).read_bytes();f=target/(name+'.json.gz')
 if a.check:assert gzip.decompress(f.read_bytes())==b,name
 else:f.write_bytes(gzip.compress(b,mtime=0))
for rel in ['Crypto/PubKey/RSA/PSS.hs','tests/PubKey/PSSSpec.hs']:assert(D/'original'/rel).read_bytes()==(R/'vendor/crypton-2.0.1'/rel).read_bytes()
source=(R/'vendor/crypton-2.0.1/Crypto/PubKey/RSA/PSS.hs').read_text();expected=['defaultPSSParams','defaultPSSParamsSHA1','signDigestWithSalt','signWithSalt','sign','signDigest','signSafer','signDigestSafer','verify','verifyDigest','emTruncate','normalizeToKeySize'];assert re.findall(r'^(\w+)\s*\n?\s*::',source,re.M)==expected
observed=source.replace('module Crypto.PubKey.RSA.PSS (','module ObservedPss (').replace('    PSSParams (..),','    emTruncate,\n    normalizeToKeySize,\n    PSSParams (..),');assert(D/'ObservedPss.hs').read_text()==observed
spec=(R/'vendor/crypton-2.0.1/tests/PubKey/PSSSpec.hs').read_text();assert(D/'OriginalPssSpec.hs').read_text()==spec.replace('module PubKey.PSSSpec (spec) where','module OriginalPssSpec where');assert len(json.loads((D/'SourceCases.json').read_text()))==53
excluded=json.loads((D/'ExcludedRandomDomains.json').read_text());assert len(excluded)==12
for row in excluded:
 q=row['request'];assert q['op']in['signSafer','signDigestSafer']and q['mode']=='empty'and q['projection']in['root','state','bytes']and q['size']in[10,64]and'expected'not in row
for row in json.loads((D/'RandomObservations.json').read_text()):
 q=row['request'];assert not(q['op']in['signSafer','signDigestSafer']and q['mode']=='empty'and q['projection']!='ignore')
paths=[f for f in D.rglob('*')if f.is_file()and'__pycache__'not in f.parts and'fixtures'not in f.parts and'work-in-progress'not in f.parts and f.name!='CurrentPins.json']+list(target.glob('*.gz'))+[Path(__file__).resolve(),R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt']
for rel in ['Crypto/PubKey/RSA/PSS.hs','tests/PubKey/PSSSpec.hs','Crypto/PubKey/RSA.hs','Crypto/Hash.hs','Crypto/Number/Generate.hs']:paths.append(R/'vendor/crypton-2.0.1'/rel)
for name in ['CryptoRsaPss.kt','CryptoRsaPssTest.kt','PssReplay.kt','PssDemandReplay.kt','PssDictionaryReplay.kt','PssRandomReplay.kt','PssCallbackReplay.kt','PssBoundaryReplay.kt']:
 f=D/'work-in-progress'/name;paths.append(f if f.exists()else R/('core/src/main/kotlin/io/heapy/kotdoc/crypto/'if name=='CryptoRsaPss.kt'else'core/src/test/kotlin/io/heapy/kotdoc/crypto/')/name)
for name in ['CryptoRsa.kt','CryptoRsaOaep.kt','CryptoRsaPkcs15.kt','CryptoMaskGen.kt','CryptoHmac.kt','CryptoHmacAlgorithms.kt','CryptoMd2.kt','CryptoMd5.kt','CryptoRipemd160.kt','CryptoSha32.kt','CryptoSha512.kt','CryptoSha3.kt','CryptoNumberBasic.kt','CryptoNumberGenerate.kt','CryptoNumberSerialize.kt','CryptoByteSpan.kt','CryptoRandom.kt']:paths.append(R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/name)
pins={str(f.relative_to(R)):hashlib.sha256(f.read_bytes()).hexdigest()for f in sorted(set(paths))};f=D/'CurrentPins.json'
if a.check:assert json.loads(f.read_text())==pins,'current source binding changed'
else:f.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS',len(pins),'pins; source12definitions/10functions/4fields/53examples; helperexport-only transform/10corpora;12unsafe excluded before execution')
