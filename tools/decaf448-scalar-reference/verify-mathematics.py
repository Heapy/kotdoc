#!/usr/bin/env python3
"""Independent integer specification checks; never generates native expectations."""
import gzip,json
from pathlib import Path
P=2**446-13818066809895115352007386748515426880336692474882178609894547503885
F=Path(__file__).resolve().parents[2]/'core/src/test/resources/crypto-decaf448-scalar/data.jsonl.gz'
def integer(words,bits):return sum(int(x,16)<<(bits*i)for i,x in enumerate(words))
count=0
with gzip.open(F,'rt')as f:
 next(f)
 for line in f:
  row=json.loads(line);r=row['request'];e=row['expected'];op=r['op'];bits=r['bits']
  if r.get('alias','none')!='none' or op in ['constant','profile']:continue
  a=integer(r.get('a',[]),bits);b=integer(r.get('b',[]),bits);out=integer(e['out'][1:-2],bits)
  expected=None
  if op in ['add','sub','multiply','halve','invert'] and a<P and (op in ['halve','invert'] or b<P):
   expected={'add':lambda:(a+b)%P,'sub':lambda:(a-b)%P,'multiply':lambda:a*b%P,'halve':lambda:a*pow(2,-1,P)%P,'invert':lambda:pow(a,-1,P)if a else 0}[op]()
   if op=='invert':assert int(e['value'],16)==((1<<64)-1 if a else 0)
  elif op in ['decode','decodeLong']:
   expected=int.from_bytes(bytes(r['bytes'][:r['length']]),'little')%P
   if op=='decode':assert int(e['value'],16)==((1<<64)-1 if int.from_bytes(bytes(r['bytes'][:56]),'little')<P else 0)
  elif op=='setUnsigned':expected=int(r['word'],16)
  if expected is not None:assert out==expected,(r,out,expected);count+=1
print(f'Independent canonical scalar mathematics PASS {count}')
