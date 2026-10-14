#!/usr/bin/env python3
"""Translate GHC 9.12.2 character categories from its original Unicode 16 data."""
import argparse,hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'vendor/ghc-unicode-9.12.2'
target=root/'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json'
assert '16.0.0/ucd/UnicodeData.txt' in (source/'GeneralCategory.hs').read_text()
ghc=(source/'Unicode.hs').read_text()
assert "isDigit c               =  (fromIntegral (ord c - ord '0') :: Word) <= 9" in ghc
flags=bytearray(0x110000)
def flag(category):
    return (3 if category in ['Lu','Ll','Lt','Lm','Lo'] else 2 if category in ['Nd','Nl','No'] else 0) | (4 if category=='Zs' else 0)
first=None
for line in (source/'UnicodeData.txt').read_text().splitlines():
    f=line.split(';');cp=int(f[0],16);name=f[1];bits=flag(f[2])
    if name.endswith(', First>'): first=cp
    elif name.endswith(', Last>'):
        assert first is not None
        flags[first:cp+1]=bytes([bits])*(cp-first+1);first=None
    else:flags[cp]=bits
assert first is None
for cp in range(9,14):flags[cp]|=4
for cp in range(48,58):flags[cp]|=8
ranges=[[0,flags[0]]]
for cp in range(1,len(flags)):
    if flags[cp]!=flags[cp-1]:ranges.append([cp,flags[cp]])
data=dict(sources={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(source.iterdir()) if p.is_file()},ranges=ranges)
expected=json.dumps(data,separators=(',',':'))+'\n'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
if args.check:assert target.read_text()==expected,'GHC parser character table drift'
else:target.write_text(expected)
print('Verified' if args.check else 'Imported',len(ranges),'GHC Unicode 16.0.0 character ranges')
