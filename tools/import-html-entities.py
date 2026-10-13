#!/usr/bin/env python3
"""Import TagSoup's complete entity list, preserving order and semicolon distinctions."""
import argparse
import hashlib
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = root / 'vendor/tagsoup-0.14.8/src/Text/HTML/TagSoup/Entity.hs'
target = root / 'core/src/main/resources/io/heapy/kotdoc/html-entities.json'
s = source.read_text().split('htmlEntities = let (*) = (,) in', 1)[1]
rows = []
for line in s.splitlines():
    if line.strip() in ('', ']'):
        continue
    match = re.fullmatch(r'\s*[\[,]"([A-Za-z0-9]+;?)" \* "((?:\\x[0-9A-Fa-f]+)+)"\s*', line)
    assert match, repr(line)
    rows.append([match[1], ''.join(chr(int(cp, 16)) for cp in re.findall(r'\\x([0-9A-Fa-f]+)', match[2]))])
assert len(rows) == 2231 and len(dict(rows)) == 2231
expected = json.dumps({'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'entities': rows}, ensure_ascii=False, indent=2) + '\n'
p = argparse.ArgumentParser(); p.add_argument('--check', action='store_true'); args = p.parse_args()
if args.check:
    assert target.read_text() == expected, 'HTML entity table drift'
else:
    target.write_text(expected)
print(f"{'Verified' if args.check else 'Imported'} {len(rows)} TagSoup entities")
