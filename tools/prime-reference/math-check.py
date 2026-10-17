#!/usr/bin/env python3
"""Independent exact trial-division/sieve checks, not a proof for arbitrary large probable primes."""
from pathlib import Path
import gzip,json,math
R=Path(__file__).resolve().parents[2];rows=[]
for path in (R/'core/src/test/resources/crypto-number-prime').glob('*.jsonl.gz'):
 rows += [json.loads(line)for line in gzip.open(path,'rt')]
def prime(n):return n>=2 and all(n%d for d in range(2,math.isqrt(n)+1))
count=0
for row in rows:
 words=row['request'].split();op=words[0]
 if op=='table':
  assert json.loads(row['value'])==[n for n in range(2,2904)if prime(n)];count+=420
 if op in ['naive','fixedProbably']:
  n=int(words[1])
  if n>=0 and n<=5001:assert row['value']==str(prime(n)),row['request'];count+=1
 if op=='fixed'and int(words[1])==30 and 5<=int(words[2])<=5001:
  assert row['value']==str(prime(int(words[2]))),row['request'];count+=1
print('PASS',count,'exact bounded mathematical observations; no universal large-number primality claim')
