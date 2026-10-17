#!/usr/bin/env python3
from pathlib import Path
import gzip,json,hashlib,argparse,re
R=Path(__file__).resolve().parent.parent;D=R/'tools/md5-reference';P=R/'core/src/test/resources/crypto-md5';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args();P.mkdir(exist_ok=True)
for n in ['NativeObservations.tsv','CounterObservations.tsv','AliasObservations.tsv','TraceObservations.tsv','PrefixBoundary.tsv','HaskellObservations.json','LiteralObservations.json','DemandObservations.json']:
 expected=gzip.compress((D/n).read_bytes(),mtime=0);target=P/(n+'.gz')
 if a.check:assert gzip.decompress(target.read_bytes())==(D/n).read_bytes(),n
 else:target.write_bytes(expected)
kotlin=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoMd5.kt'
if not kotlin.exists():kotlin=D/'work-in-progress/CryptoMd5.kt'
s=kotlin.read_text();c=(D/'original/cbits/crypton_md5.c').read_text();calls=re.findall(r'R\(f(\d), (\w), (\w), (\w), (\w), (\d+), (0x[0-9a-f]+), (\d+)\);',c);assert len(calls)==64
for name,index,pattern in [('constants',6,r'0x[0-9a-f]+'),('indices',5,r'\d+'),('shifts',7,r'\d+')]:
 values=re.findall(pattern,s.split('private val '+name+'=intArrayOf(')[1].split(')')[0])
 # U.toInt() closes each item; parse the full declaration for constants.
 if name=='constants':values=re.findall(pattern,s.split('private val constants=')[1].split('\n')[0])
 assert values==[x[index]for x in calls],name
iv=re.findall(r'ctx->h\[\d\] = (0x[0-9a-f]+);',c);assert len(iv)==4
for value in iv:assert value in s,value
original=(R/'vendor/crypton-2.0.1/tests/HashSpec.hs').read_text().split('spec :: Spec')[0].replace('module HashSpec (\n    spec,\n) where','module OriginalMd5HashSpec where');assert(D/'OriginalMd5HashSpec.hs').read_text().startswith(original),'original HashSpec body changed'
paths=[p for p in D.rglob('*')if p.is_file()and '__pycache__'not in p.parts and p.name!='SourcePins.json'and 'work-in-progress'not in p.parts]
paths+=[R/'tools/extract-md5-reference.py',R/'tools/import-md5-fixtures.py',R/'licenses/crypton-md5-BSD-2-Clause.txt',R/'licenses/crypton-BSD-3-Clause.txt']+list(P.glob('*.gz'))
paths+=list((R/'core/src/main/kotlin/io/heapy/kotdoc/crypto').glob('CryptoMd5*.kt'))+list((R/'core/src/test/kotlin/io/heapy/kotdoc/crypto').glob('*Md5*.kt'))
for n in ['Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','Crypto/Hash/MD5.hs','tests/HashSpec.hs','tests/Utils.hs','tests/Imports.hs','crypton.cabal']:paths.append(R/'vendor/crypton-2.0.1'/n)
pins={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(set(paths))};target=D/'SourcePins.json'
if a.check:assert json.loads(target.read_text())==pins,'source/fixture bindings differ'
else:target.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS original fixtures,68 IV/K words,64 indices/shifts,verbatim test body,',len(pins),'pins')
