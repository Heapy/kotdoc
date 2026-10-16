#!/usr/bin/env python3
"""Exact field prefixes; static helpers are exposed only by the separate dev shim."""
from pathlib import Path
import hashlib,json,re,sys
ROOT=Path(__file__).resolve().parents[1]
def sources():
 result={}
 for bits,name in [(32,'curve25519-donna.c'),(64,'curve25519-donna-c64.c')]:
  text=(ROOT/'vendor/crypton-2.0.1/cbits/curve25519'/name).read_text()
  marker='/* Input: Q, Q\', Q-Q\'';assert text.count(marker)==1
  prefix=text[:text.index(marker)]
  result[f'OriginalField{bits}.h']=prefix
 return result
if __name__=='__main__':
 folder=ROOT/'tools/x25519-field-reference'
 for name,text in sources().items():
  p=folder/name
  if '--check' in sys.argv:assert p.read_text()==text,name
  else:p.write_text(text)
 print('Verified exact Donna32/64 field prefixes')
