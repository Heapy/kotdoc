#!/usr/bin/env python3
"""Verify the complete pinned module, verbatim tests, mechanical observers and literal table."""
from pathlib import Path
import hashlib,json,re,gzip,subprocess,sys
R=Path(__file__).resolve().parents[1];D=R/'tools/prime-reference';V=R/'vendor/crypton-2.0.1';check='--check'in sys.argv
manifest=json.loads((V/'SOURCE.json').read_text());assert manifest['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
for name in ['Crypto/Number/Prime.hs','Crypto/Number/Compat.hs','tests/NumberSpec.hs','tests/Utils.hs','tests/Imports.hs']:
 data=(V/name).read_bytes();assert hashlib.sha256(data).hexdigest()==manifest['files'][name]
 out=D/'original'/name
 if check:assert out.read_bytes()==data,name
 else:out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
source=(V/'Crypto/Number/Prime.hs').read_text()
def module(text,original,replacement):
 start=text.index('module '+original+' (');end=text.index(') where',start)+len(') where');return text[:start]+replacement+text[end:]
for name,imp in [('ObservedPrime','PrimeProbabilistic'),('ProcessPrime','ProcessProbabilistic')]:
 expected=module(source,'Crypto.Number.Prime','module '+name+' where').replace('import Crypto.Random.Probabilistic','import '+imp)
 assert (D/(name+'.hs')).read_text()==expected,name
prob=(V/'Crypto/Random/Probabilistic.hs').read_text();assert (D/'ProcessProbabilistic.hs').read_text()==module(prob,'Crypto.Random.Probabilistic','module ProcessProbabilistic (probabilisticFrom, secret) where')
observer=(D/'PrimeProbabilistic.hs').read_text()
for expression in ['digest = hashWith SHA512 (B.append secret (B.convert material) :: ScrubbedBytes)','B.take seedLength (B.convert digest :: ScrubbedBytes)','seedLength = 40']:
 assert expression in prob and expression in observer,expression
matrix=json.loads((D/'SourceMatrix.json').read_text());definitions=re.findall(r'^([a-zA-Z][a-zA-Z0-9]*) ::',source,re.M)
# Multiline Fermat signature is still an original top-level definition.
definitions+=re.findall(r'^([a-zA-Z][a-zA-Z0-9]*)\n    ::',source,re.M)
assert set(definitions)==set(matrix['exports']+matrix['privateDefinitions']),(definitions,matrix)
primes=list(map(int,re.findall(r'\d+',source.split('firstPrimes =')[1].split(']')[0])));assert len(primes)==420 and primes[0]==2 and primes[-1]==2903
production=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoNumberPrime.kt'
if not production.exists():production=D/'work-in-progress/CryptoNumberPrime.kt'
kotlin=list(map(int,re.findall(r'\d+',production.read_text().split('internal val firstPrimes: List<Int> = listOf(')[1].split(')')[0])));assert kotlin==primes
rows=[json.loads(x)for x in gzip.open(R/'core/src/test/resources/crypto-number-prime/original.jsonl.gz','rt')]
assert json.loads(next(x['value']for x in rows if x['request']=='table'))==primes
spec=(V/'tests/NumberSpec.hs').read_text();literals=json.loads((D/'OriginalPrimalityLiterals.json').read_text())
for name,values in literals.items():assert list(map(int,re.findall(r'\d+',spec.split(name+' =')[1].split(']')[0])))==values
subprocess.run([sys.executable,str(D/'prepare-haskell-oracle.py'),'--check'],check=True)
print('PASS complete 15-definition matrix, 420 source/Kotlin/native table entries, verbatim tests and mechanical observers')
