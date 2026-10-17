#!/usr/bin/env python3
"""Package independently captured original observations, verify source bindings."""
from pathlib import Path
import gzip,json,hashlib,argparse,re
R=Path(__file__).resolve().parent.parent;D=R/'tools/sha32-reference';P=R/'core/src/test/resources/crypto-sha32'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args();P.mkdir(exist_ok=True)
for n in ['NativeObservations.tsv','CounterObservations.tsv','AliasObservations.tsv','TraceObservations.tsv','PrefixBoundary.tsv','HaskellObservations.json','LiteralObservations.json','DemandObservations.json']:
 expected=gzip.compress((D/n).read_bytes(),mtime=0)
 target=P/(n+'.gz')
 if a.check:assert gzip.decompress(target.read_bytes())==(D/n).read_bytes(),n
 else:target.write_bytes(expected)
# Compare every C IV/K literal against the translated source, independent of fixture outputs.
kotlin=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoSha32.kt'
if not kotlin.exists():kotlin=D/'work-in-progress/CryptoSha32.kt'
s=kotlin.read_text();c1=(D/'original/cbits/crypton_sha1.c').read_text();c2=(D/'original/cbits/crypton_sha256.c').read_text()
constants={}
constants['iv1']=re.findall(r'ctx->h\[\d\] = (0x[0-9a-f]+);',c1)
for bits in [224,256]:constants['iv'+str(bits)]=re.findall(r'ctx->h\[\d\] = (0x[0-9a-f]+);',c2.split(f'void crypton_sha{bits}_init')[1].split('\n}')[0])
constants['k']=re.findall(r'0x[0-9a-f]+',c2.split('static const uint32_t k[] = {')[1].split('};')[0])
for n,values in constants.items():assert re.findall(r'0x[0-9a-f]+',s.split('private val '+n+'=')[1].split('\n')[0])==values,n
assert len(constants['iv1'])==5 and len(constants['k'])==64
for name in ['K1','K2','K3','K4']:
 value=re.search(r'#define '+name+r'\s+(0x[0-9a-f]+)',c1)[1];assert value in s,value
original=(R/'vendor/crypton-2.0.1/tests/HashSpec.hs').read_text().split('spec :: Spec')[0].replace('module HashSpec (\n    spec,\n) where','module OriginalSha32HashSpec where')
assert (D/'OriginalSha32HashSpec.hs').read_text().startswith(original),'HashSpec literals/generators changed'
paths=[p for p in D.rglob('*')if p.is_file()and '__pycache__'not in p.parts and p.name!='SourcePins.json' and 'work-in-progress'not in p.parts]
paths+=[R/'tools/extract-sha32-reference.py',R/'tools/import-sha32-fixtures.py',R/'licenses/crypton-sha32-BSD-2-Clause.txt',R/'licenses/crypton-BSD-3-Clause.txt']
paths+=list(P.glob('*.gz'))
for n in ['CryptoSha32.kt','CryptoSha32Hash.kt']:
 path=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto'/n
 if path.exists():paths.append(path)
paths+=list((R/'core/src/test/kotlin/io/heapy/kotdoc/crypto').glob('*Sha32*.kt'))
for n in ['Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','Crypto/Hash/SHA1.hs','Crypto/Hash/SHA224.hs','Crypto/Hash/SHA256.hs','tests/HashSpec.hs','tests/Utils.hs','tests/Imports.hs','crypton.cabal']:
 paths.append(R/'vendor/crypton-2.0.1'/n)
pins={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(set(paths))}
target=D/'SourcePins.json'
if a.check:assert json.loads(target.read_text())==pins,'source/fixture bindings differ'
else:target.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS packaged original corpus, all89 constants, verbatim HashSpec body and',len(pins),'source pins')
