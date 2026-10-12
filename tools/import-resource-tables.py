#!/usr/bin/env python3
"""Reproduce MIME/URI tables from pinned sources, without installed Pandoc."""
import argparse
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
network = (root/'vendor/mime-types-0.1.2.2/Network/Mime.hs').read_text()
pandoc = (root/'upstream/pandoc/src/Text/Pandoc/MIME.hs').read_text()
uri = (root/'upstream/pandoc/src/Text/Pandoc/URI.hs').read_text()
pairs = re.compile(r'\(\s*"([^"\\]*)"\s*,\s*"([^"\\]*)"\s*\)')
base = sorted(dict(pairs.findall(network.split('mimeAscList =', 1)[1])).items())
overrides = pairs.findall(pandoc.split('mimeTypesList =', 1)[1])
schemes = re.findall(r'"([a-z0-9.+-]+)"', uri.split('schemes = Set.fromList', 1)[1].split('\n  ]', 1)[0])
outputs = {
    'mime-types.tsv': ''.join(f'{extension}\t{mime}\n' for extension, mime in base+overrides),
    'uri-schemes.txt': '\n'.join(schemes)+'\n',
}
for name, content in outputs.items():
    path = root/'core/src/main/resources/io/heapy/kotdoc'/name
    if args.check:
        assert path.read_text() == content, f'Generated resource drift: {name}'
    else:
        path.write_text(content)
print(f'{"Verified" if args.check else "Imported"} {len(base)} MIME entries, {len(overrides)} overrides, {len(schemes)} URI schemes')
