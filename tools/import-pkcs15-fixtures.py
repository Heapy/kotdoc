#!/usr/bin/env python3
from pathlib import Path
import json,gzip,hashlib,argparse,re
R=Path(__file__).resolve().parent.parent;D=R/'tools/pkcs15-reference';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args()
names=['Observations','SourceCases','DemandObservations','DictionaryObservations','DefinedRandomObservations','CallbackObservations','BoundaryObservations'];target=R/'core/src/test/resources/crypto-rsa-pkcs15'if a.install or(R/'core/src/test/resources/crypto-rsa-pkcs15').exists()else D/'fixtures/crypto-rsa-pkcs15'
if not a.check:target.mkdir(parents=True,exist_ok=True)
for name in names:
 b=(D/(name+'.json')).read_bytes();f=target/(name+'.json.gz')
 if a.check:assert gzip.decompress(f.read_bytes())==b,name
 else:f.write_bytes(gzip.compress(b,mtime=0))
source=(R/'vendor/crypton-2.0.1/Crypto/PubKey/RSA/PKCS15.hs').read_text();prefixes={name:[int(x.strip(),0)for x in body.split(',')]for name,body in re.findall(r'instance HashAlgorithmASN1 (\w+) where\s+hashDigestASN1 =\s+addDigestPrefix\s+\[(.*?)\]',source,re.S)};assert len(prefixes)==14;assert prefixes==json.loads((D/'Prefixes.json').read_text())
for rel in ['Crypto/PubKey/RSA/PKCS15.hs','tests/PubKey/RSASpec.hs']:assert(D/'original'/rel).read_bytes()==(R/'vendor/crypton-2.0.1'/rel).read_bytes()
whole=json.loads((D/'RandomObservations.json').read_text());defined=json.loads((D/'DefinedRandomObservations.json').read_text());excluded=[i for i,r in enumerate(whole)if r['request']['op']in['signSafer','decryptSafer']and r['request']['mode']=='empty'and r['request']['projection']=='state'];assert len(excluded)==4
assert defined==[dict(index=i,**r)for i,r in enumerate(whole)if i not in excluded]
paths=[f for f in D.rglob('*')if f.is_file()and'__pycache__'not in f.parts and'fixtures'not in f.parts and'work-in-progress'not in f.parts and f.name!='CurrentPins.json']+list(target.glob('*.gz'))+[Path(__file__).resolve(),R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt']
for rel in ['Crypto/PubKey/RSA/PKCS15.hs','Crypto/PubKey/RSA.hs','Crypto/PubKey/RSA/Prim.hs','Crypto/Number/Generate.hs','tests/PubKey/RSASpec.hs']:paths.append(R/'vendor/crypton-2.0.1'/rel)
for name in ['CryptoRsaPkcs15.kt','CryptoRsaPkcs15Test.kt','Pkcs15Replay.kt','Pkcs15DemandReplay.kt','Pkcs15DictionaryReplay.kt','Pkcs15RandomReplay.kt','Pkcs15CallbackReplay.kt','Pkcs15BoundaryReplay.kt']:
 f=D/'work-in-progress'/name;paths.append(f if f.exists()else R/('core/src/main/kotlin/io/heapy/kotdoc/crypto/'if name=='CryptoRsaPkcs15.kt'else'core/src/test/kotlin/io/heapy/kotdoc/crypto/')/name)
paths +=[R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/name for name in ['CryptoNumberGenerate.kt','CryptoRsa.kt','CryptoHmac.kt','CryptoRipemd160.kt','CryptoMd2.kt','CryptoRsaOaep.kt','CryptoMaskGen.kt','CryptoHmacAlgorithms.kt','CryptoMd5.kt','CryptoSha32.kt','CryptoSha512.kt','CryptoSha3.kt','CryptoByteSpan.kt','CryptoNumberSerialize.kt']]
paths += [R/'tools/rsa-oaep-reference'/name for name in ['SourcePins.json','SourcePins.pre-generate-length.json','README.md']]
pins={str(f.relative_to(R)):hashlib.sha256(f.read_bytes()).hexdigest()for f in sorted(set(paths))};f=D/'CurrentPins.json'
if a.check:assert json.loads(f.read_text())==pins,'current source binding changed'
else:f.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS',len(pins),'pins; full14ASN1/sourcecopies/7corpora; 4unsafe historical only')
