#!/usr/bin/env python3
"""Import complete emojis-0.1.5 tables; no network or Haskell runtime needed."""
import argparse
import hashlib
import json
from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
source=root/'vendor/emojis-0.1.5/src/Text/emojis.inc'
target=root/'core/src/main/resources/io/heapy/kotdoc/emojis.json'
s=source.read_text()
aliases,rest=s.split('baseEmojis =',1)
base,zwj=rest.split('zwjEmojis =',1)
def decode(s):
    # The generated file uses printable ASCII and decimal Haskell escapes only.
    assert re.fullmatch(r'(?:[^\\"\x00-\x1f]|\\[0-9]+)*',s),repr(s)
    return re.sub(r'\\([0-9]+)',lambda m:chr(int(m[1])),s)
def strings(s):return [decode(x) for x in re.findall(r'"([^"\n]*)"',s)]
a=strings(aliases)
assert len(a)%2==0
result={'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
        'emojis':list(zip(a[::2],a[1::2])), 'base':strings(base),'zwj':strings(zwj)}
expected=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
if args.check:assert target.read_text()==expected,'Emoji table drift'
else:target.write_text(expected)
print(f"{'Verified' if args.check else 'Imported'} {len(result['emojis'])} aliases, {len(result['base'])} base and {len(result['zwj'])} ZWJ emojis")
