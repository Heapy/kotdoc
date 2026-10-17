#!/usr/bin/env python3
from pathlib import Path
import json,gzip,hashlib,argparse,sys
R=Path(__file__).resolve().parent.parent;D=R/'tools/number-generate-length-reference';sys.path.insert(0,str(D));from Domain import domain
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--install',action='store_true');a=p.parse_args();target=R/'core/src/test/resources/crypto-number-generate-length';target=target if a.install or target.exists()else D/'fixtures/crypto-number-generate-length'
if not a.check:target.mkdir(parents=True,exist_ok=True)
for name in ['Observations','ExcludedBounds','PkcsDefinedRepros']:
 b=(D/(name+'.json')).read_bytes();f=target/(name+'.json.gz')
 if a.check:assert gzip.decompress(f.read_bytes())==b
 else:f.write_bytes(gzip.compress(b,mtime=0))
for name,want in [('Observations',True),('ExcludedBounds',False)]:
 for row in json.loads((D/(name+'.json')).read_text()):
  if row['request']['op']in['params','prefix']:assert domain(row['request'])==row['domain']
  assert row['domain']['defined']==want
  if not want:assert'expected'not in row
old=R/'core/src/test/resources/crypto-number-generate/data.jsonl.gz';assert old.read_bytes()==(D/'history/data.jsonl.gz').read_bytes(),'historical gzip changed'
base=json.loads((D/'Baseline.json').read_text());assert hashlib.sha256(old.read_bytes()).hexdigest()==base['files']['core/src/test/resources/crypto-number-generate/data.jsonl.gz']
whitelist={'tools/number-generate-reference/README.md':'tools/number-generate-length-reference/history/README.md','tools/import-number-generate-fixtures.py':'tools/number-generate-length-reference/history/import-number-generate-fixtures.py'};assert json.loads((D/'HistoricalBindings.json').read_text())==whitelist
header=json.loads(gzip.decompress(old.read_bytes()).split(b'\n')[0])
for path,want in header['sources'].items():assert hashlib.sha256((R/whitelist.get(path,path)).read_bytes()).hexdigest()==want,path
source=(R/'vendor/crypton-2.0.1/Crypto/Number/Generate.hs').read_text()
for text in ['tweak orig = B.copyAndFreeze orig','Nothing -> return ()','p0 |= (1 `shiftL` bit)','p0 $= 0x1','p1 |= 0x80','p0 &= (complement $ mask)','when generateOdd (pEnd |= 0x1)','bytes = (bits + 7) `div` 8'] :assert text in source
assert source.index('case genTopPolicy of')<source.index('p0 &=')<source.index('when generateOdd')
paths=[f for f in D.rglob('*')if f.is_file()and'__pycache__'not in f.parts and'fixtures'not in f.parts and'work-in-progress'not in f.parts and f.name!='CurrentPins.json']+list(target.glob('*.gz'))
paths +=[Path(__file__).resolve(),R/'core/src/test/resources/crypto-number-generate/data.jsonl.gz',R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt',R/'vendor/crypton-2.0.1/Crypto/Number/Generate.hs',R/'vendor/crypton-2.0.1/Crypto/PubKey/RSA.hs']
for current,draft in [('core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoNumberGenerate.kt','CryptoNumberGenerate.kt'),('core/src/test/kotlin/io/heapy/kotdoc/crypto/CryptoNumberGenerateTest.kt','CryptoNumberGenerateTest.kt'),('core/src/test/kotlin/io/heapy/kotdoc/crypto/CryptoNumberGenerateLengthTest.kt','CryptoNumberGenerateLengthTest.kt'),('tools/import-number-generate-fixtures.py','import-number-generate-fixtures.py'),('tools/number-generate-reference/README.md','README.md')]:
 f=D/'work-in-progress'/draft;paths.append(f if f.exists()else R/current)
pins={str(f.relative_to(R)):hashlib.sha256(f.read_bytes()).hexdigest()for f in sorted(set(paths))};f=D/'CurrentPins.json'
if a.check:assert json.loads(f.read_text())==pins,'current source bindings changed'
else:f.write_text(json.dumps(pins,indent=2)+'\n')
print('PASS current',len(pins),'pins; all historical pins resolved with exact2-path whitelist; old gzip byte-identical; defined-access classifier')
