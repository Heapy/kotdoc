#!/usr/bin/env python3
"""Exact text-2.1.2 upper/lower/title tables for LaTeX case commands."""
import argparse,hashlib,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'vendor/text-2.1.2/src/Data/Text/Internal/Fusion/CaseMapping.hs'
text=source.read_text();out={'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest()}
for kind in ['upper','lower','title']:
 section=text.split(kind+'Mapping = \\case\n',1)[1].split('  _ -> unI64 0',1)[0]
 entries={}
 for cp,packed in re.findall(r"'\\x([0-9a-f]+)'# -> unI64 ([0-9]+)",section):
  value=int(packed);points=[]
  while value:points.append(value&((1<<21)-1));value>>=21
  assert cp not in entries;entries[cp]=''.join(map(chr,points))
 out[kind]=entries
expected=json.dumps(out,ensure_ascii=False,indent=2)+'\n'
target=root/'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-case.json'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
if args.check:assert target.read_text()==expected,'text-2.1.2 case table drift'
else:target.write_text(expected)
print('Verified' if args.check else 'Translated',{k:len(out[k]) for k in ['upper','lower','title']})
