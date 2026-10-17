#!/usr/bin/env python3
"""Re-execute independent Haskell expectations; recorded ordinary secret is replayed by the distinct controlled observer."""
from pathlib import Path
import gzip,json,subprocess,os,ast,collections,sys,hashlib
D=Path(__file__).resolve().parent;R=D.parents[1]
exe=Path('/tmp/kotdoc-prime-haskell-build/build/aarch64-osx/ghc-9.12.2/kotdoc-prime-reference-0.1.0.0/x/prime-reference/opt/build/prime-reference/prime-reference')
groups=collections.defaultdict(list);count=0
for p in sorted((R/'core/src/test/resources/crypto-number-prime').glob('*.jsonl.gz')):
 for line in gzip.open(p,'rt'):
  row=json.loads(line);groups[tuple(row['secret'])].append(row)
for secret,rows in groups.items():
 env=dict(os.environ,KOTDOC_PRIME_OBSERVER_SECRET=str(list(secret)))
 requests=[('fixed '+r['request'][3:])if r['request'].startswith('mr ')else r['request']for r in rows]
 run=subprocess.run([str(exe)],input='\n'.join(requests)+'\n',text=True,capture_output=True,env=env,check=True)
 lines=run.stdout.splitlines();assert len(lines)==len(rows),(len(lines),len(rows),run.stderr)
 for row,line in zip(rows,lines):
  value,trace=line.split('|');assert value==row['value'],(row['request'],row['value'],value)
  if row['draws'] is not None:assert ast.literal_eval(trace)==[tuple(x)for x in row['draws']],row['request']
  count+=1
print('PASS independent native replay',count)
