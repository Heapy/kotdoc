#!/usr/bin/env python3
"""Bind independently captured Prime fixtures to exact source/tool bytes; never computes expected answers."""
from pathlib import Path
import hashlib,json,gzip,subprocess,sys
R=Path(__file__).resolve().parents[1];D=R/'tools/prime-reference';fixtures=R/'core/src/test/resources/crypto-number-prime';target=fixtures/'Manifest.json'
write='--write'in sys.argv
paths=[R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoNumberPrime.kt',R/'core/src/test/kotlin/io/heapy/kotdoc/crypto/CryptoNumberPrimeTest.kt',R/'core/src/test/kotlin/io/heapy/kotdoc/crypto/CryptoNumberPrimeReference.kt',R/'tools/extract-number-prime-reference.py',Path(__file__).resolve(),R/'licenses/crypton-BSD-3-Clause.txt']
paths += [p for p in D.rglob('*')if p.is_file()and 'work-in-progress'not in p.parts and '__pycache__'not in p.parts]
paths += [p for p in fixtures.iterdir()if p.is_file()and p!=target]
paths += [R/'vendor/crypton-2.0.1'/p for p in ['SOURCE.json','Crypto/Number/Prime.hs','Crypto/Number/Compat.hs','Crypto/Random/Probabilistic.hs','tests/NumberSpec.hs','tests/Utils.hs','tests/Imports.hs']]
sources={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(set(paths))}
counts={p.name:sum(1 for _ in gzip.open(p,'rt'))for p in sorted(fixtures.glob('*.jsonl.gz'))}
manifest=dict(kind='original native expectations; Kotlin never used to derive expected values',cases=sum(counts.values()),corpora=counts,originalNumberSpecExamples=45,trialsPerProperty=100,originalPrimalityExamples=7,originalGenerationProperties=4,sourceTableEntries=420,sources=sources)
if write:target.write_text(json.dumps(manifest,indent=2)+'\n')
else:
 assert json.loads(target.read_text())==manifest,'source/fixture binding changed'
 subprocess.run([sys.executable,str(R/'tools/extract-number-prime-reference.py'),'--check'],check=True)
 subprocess.run([sys.executable,str(D/'math-check.py')],check=True)
if '--verify-oracle'in sys.argv:subprocess.run([sys.executable,str(D/'verify.py')],check=True)
print('PASS',manifest['cases'],'cases /',len(sources),'source/fixture pins')
