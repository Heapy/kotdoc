#!/usr/bin/env python3
from pathlib import Path
import json,gzip,hashlib,argparse
R=Path(__file__).resolve().parent.parent;D=R/'tools/rsa-types-reference'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args()
target=R/'core/src/test/resources/crypto-rsa-types'if a.install or(R/'core/src/test/resources/crypto-rsa-types').exists()else D/'fixtures/crypto-rsa-types'
names=['Observations','DataObservations','EqObservations','StreamObservations','GenericObservations','ProjectedObservations','MemberObservations','TypeInfoObservations','SecrecyCases','LexObservations']
if not a.check:target.mkdir(parents=True,exist_ok=True)
for n in names:
 b=(D/(n+'.json')).read_bytes();f=target/(n+'.json.gz')
 if a.check:assert gzip.decompress(f.read_bytes())==b,n
 else:f.write_bytes(gzip.compress(b,mtime=0))
assert(D/'original/Crypto/PubKey/RSA/Types.hs').read_bytes()==(R/'vendor/crypton-2.0.1/Crypto/PubKey/RSA/Types.hs').read_bytes()
s=(R/'vendor/crypton-2.0.1/tests/PubKey/SecrecySpec.hs').read_text();assert(D/'original/SecrecySpec.hs').read_text()==s;assert(D/'OriginalSecrecySpec.hs').read_text()==s.replace('module PubKey.SecrecySpec (spec) where','module OriginalSecrecySpec where')
assert len(json.loads((D/'SecrecyCases.json').read_text())['rows'])==6
raw=json.loads((D/'GenericObservations.json').read_text());assert raw==json.loads((D/'history/generic-imprecise-context/RawGenericObservations.json').read_text())and len(raw)==101
paths=[f for f in D.rglob('*')if f.is_file()and'__pycache__'not in f.parts and'work-in-progress'not in f.parts and'fixtures'not in f.parts and f.name!='CurrentPins.json']+list(target.glob('*.gz'))+[Path(__file__).resolve()]
main=['CryptoRsaTypes','CryptoRsaTypesData','CryptoRsaTypesGeneric','CryptoRsaTypesRead','CryptoRsaTypesShow','CryptoRsaTypesEq'];tests=['CryptoRsaTypesTest','RsaTypesReplay','RsaTypesDataReplay','RsaTypesGenericProof','RsaTypesSecrecyProof','RsaTypesLexProof','CryptoRsaTypesLexTest']
for n in main+tests:
 draft=D/'work-in-progress'/(n+'.kt');installed=R/('core/src/main/kotlin/io/heapy/kotdoc/crypto/'if n in main else'core/src/test/kotlin/io/heapy/kotdoc/crypto/')/(n+'.kt')
 assert not(draft.exists()and installed.exists()),n
 paths.append(draft if draft.exists()else installed)
for base,rels in {
'vendor/crypton-2.0.1':['Crypto/PubKey/RSA/Types.hs','Crypto/Debug.hs','Crypto/Internal/DeepSeq.hs','Crypto/Internal/Imports.hs','crypton.cabal','tests/PubKey/RSASpec.hs','tests/PubKey/SecrecySpec.hs'],
'core/src/main/kotlin/io/heapy/kotdoc/crypto':['CryptoRsa.kt','CryptoRsaOaep.kt','CryptoRandom.kt'],
'core/src/main/kotlin/io/heapy/kotdoc/x509':['X509PublicKeys.kt','X509PrivateKeys.kt'],
'core/src/main/kotlin/io/heapy/kotdoc/ast':['PandocData.kt','DataDictionaries.kt','GenericData.kt','PandocGenericRep.kt','DeepSeqData.kt','PandocDeepSeq.kt','PandocManyDerived.kt','ManyDerivedRead.kt','ManyReadLex.kt'],
'core/src/test/kotlin/io/heapy/kotdoc/ast':['DataMonadProof.kt'],
'licenses':['crypton-BSD-3-Clause.txt','GHC-base-BSD-3-Clause.txt','GHC-Read-Show-Attribution.txt','GHC-Data-Attribution.txt','deepseq-BSD-3-Clause.txt'],
'tools/haskell-types-data-reference':['OriginalData.hs','OriginalInteger.hs'],
'tools/haskell-types-many-derived-reference':['OriginalRead.hs','OriginalLex.hs']}.items():paths.extend(R/base/f for f in rels)
pins={str(f.relative_to(R)):hashlib.sha256(f.read_bytes()).hexdigest()for f in sorted(set(paths))};f=D/'CurrentPins.json'
if a.check:assert json.loads(f.read_text())==pins,'current source binding changed'
else:f.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS',len(pins),'current pins; original source/export-only tests; eight4215row corpora+6900lexicalrows+six original assertions;101raw Generic observations retained')
