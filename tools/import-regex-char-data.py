#!/usr/bin/env python3
"""Generate GHC simple character maps/categories from pinned UnicodeData, not expectations."""
import argparse,hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root/'vendor/ghc-unicode-9.12.2'
categories=['Lu','Ll','Lt','Lm','Lo','Mn','Mc','Me','Nd','Nl','No','Pc','Pd','Ps','Pe','Pi','Pf','Po','Sm','Sc','Sk','So','Zs','Zl','Zp','Cc','Cf','Cs','Co','Cn']
cat=bytearray([29])*0x110000;lower={};upper={};first=None
for file in ['SimpleLowerCaseMapping.hs','SimpleUpperCaseMapping.hs']:
 assert '16.0.0/ucd/UnicodeData.txt' in (root/'vendor/ghc-regex-unicode-9.12.2'/file).read_text()
for line in (source/'UnicodeData.txt').read_text().splitlines():
 f=line.split(';');cp=int(f[0],16);kind=categories.index(f[2])
 if f[1].endswith(', First>'):first=cp
 elif f[1].endswith(', Last>'):
  assert first is not None;cat[first:cp+1]=bytes([kind])*(cp-first+1);first=None
 else:cat[cp]=kind
 if f[12]:upper[str(cp)]=int(f[12],16)
 if f[13]:lower[str(cp)]=int(f[13],16)
assert first is None
ranges=[[0,cat[0]]]+[[cp,cat[cp]] for cp in range(1,len(cat)) if cat[cp]!=cat[cp-1]]
files=[source/name for name in ['Unicode.hs','UnicodeData.txt','GeneralCategory.hs']]+[root/'vendor/ghc-regex-unicode-9.12.2'/name for name in ['SimpleLowerCaseMapping.hs','SimpleUpperCaseMapping.hs']]
data=dict(sources={str(file.relative_to(root)):hashlib.sha256(file.read_bytes()).hexdigest() for file in files},categories=categories,ranges=ranges,lower=lower,upper=upper)
expected=json.dumps(data,separators=(',',':'))+'\n';target=root/'core/src/main/resources/io/heapy/kotdoc/regex-char-data.json'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
if a.check:assert target.read_text()==expected
else:target.write_text(expected)
print(f'{"Verified" if a.check else "Generated"} {len(ranges)} GHC categories, {len(lower)} simple lower and {len(upper)} simple upper mappings')
