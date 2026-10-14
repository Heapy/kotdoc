#!/usr/bin/env python3
"""Import every Pandoc XML attribute and Commonmark entity in source order."""
import argparse, hashlib, json, re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
xml = root / 'upstream/pandoc/src/Text/Pandoc/XML.hs'
entity = root / 'vendor/commonmark-0.3.1/src/Commonmark/Entity.hs'
target = root / 'core/src/main/resources/io/heapy/kotdoc/pandoc-xml-data.json'
data = {'sources': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in [xml, entity]}}
for key in ['html4Attributes', 'html5Attributes', 'rdfaAttributes']:
    block = xml.read_text().split(key + ' = Set.fromList\n', 1)[1].split(']', 1)[0]
    data[key] = re.findall(r'"([a-z0-9-]+)"', block)
    assert len(data[key]) == len(set(data[key]))
rows = []
for line in entity.read_text().split('htmlEntities =\n', 1)[1].split('\ncharEntity ::', 1)[0].splitlines():
    if not line.strip() or line.strip() == ']': continue
    m = re.fullmatch(r'\s*[\[,]\("([A-Za-z0-9]+;?)", "((?:\\x[0-9A-Fa-f]+)+)"\)\s*', line)
    assert m, repr(line)
    rows.append([m[1], ''.join(chr(int(cp, 16)) for cp in re.findall(r'\\x([0-9A-Fa-f]+)', m[2]))])
assert len(rows) == 2231 and len(dict(rows)) == len(rows)
data['entities'] = rows
expected = json.dumps(data, ensure_ascii=True, separators=(',', ':')) + '\n'
p = argparse.ArgumentParser(); p.add_argument('--check', action='store_true'); args = p.parse_args()
if args.check: assert target.read_text() == expected, 'Pandoc XML data drift'
else: target.write_text(expected)
print('Verified' if args.check else 'Imported', len(rows), 'entities and', sum(len(data[k]) for k in ['html4Attributes','html5Attributes','rdfaAttributes']), 'attribute entries')
