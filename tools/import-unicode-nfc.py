#!/usr/bin/env python3
"""Extract pinned canonical normalization data and verify the original functions."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / 'vendor/unicode-data-0.9.0/lib/Unicode/Internal/Char/UnicodeData'
target = root / 'core/src/main/resources/io/heapy/kotdoc/unicode-nfc-0.9.0.json'
parser = argparse.ArgumentParser()
parser.add_argument('--check-source', action='store_true')
parser.add_argument('--oracle')
args = parser.parse_args()

def literal(s):
    result = []; i = 0
    while i < len(s):
        if s[i] != '\\':
            result.append(ord(s[i])); i += 1
        else:
            i += 1
            match = re.match(r'[0-9]+', s[i:])
            if match:
                result.append(int(match[0])); i += len(match[0])
            else:
                assert s[i] in "\\'\"", s
                result.append(ord(s[i])); i += 1
    return result

char = r"'((?:\\[0-9]+|\\.|[^'\\])*)'"
classes = [[int(cp), int(cc)] for cp, cc in re.findall(r"^  '\\([0-9]+)' -> ([0-9]+)$", (base / 'CombiningClass.hs').read_text(), re.M)]
decompositions = [[literal(cp)[0], literal(value)] for cp, value in re.findall(r'^  ' + char + r' -> "((?:\\.|[^"\\])*)"$', (base / 'Decompositions.hs').read_text(), re.M)]
composition_source = (base / 'Compositions.hs').read_text()
def compositions(name):
    rows = re.findall(r'^' + name + ' ' + char + ' ' + char + ' = Just ' + char + '$', composition_source, re.M)
    assert len(rows) == len(re.findall(r'^' + name + r' .* = Just ', composition_source, re.M))
    return [list(map(lambda x: literal(x)[0], row)) for row in rows]

data = dict(classes=classes, combining=[cp for cp, cc in classes],
            decompositions=decompositions, compositions=compositions('compose'),
            starterCompositions=compositions('composeStarters'))
data['starters'] = sorted({b for a, b, c in data['starterCompositions']})
sources = [p for package in ['unicode-data-0.9.0', 'unicode-transforms-0.4.0.1']
           for p in (root / 'vendor' / package).rglob('*') if p.is_file()]
sources += [Path(__file__).resolve(), root / 'tools/haskell-latex-inline-reference/LaTeXInlineReference.hs',
            root / 'tools/haskell-latex-inline-reference/kotdoc-latex-inline-reference.cabal']
hashes = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(sources)}
if args.check_source:
    captured = json.loads(target.read_text())
    assert captured['sources'] == hashes
    assert captured['data'] == data
else:
    assert args.oracle, 'An independently compiled original oracle is required'
    request = ['nfc-data', [[a, b] for a, b, c in data['compositions']],
               [[a, b] for a, b, c in data['starterCompositions']]]
    observed = json.loads(subprocess.check_output([args.oracle], input=json.dumps(request) + '\n', text=True))
    assert observed == data, {k: (len(observed.get(k, [])), len(v)) for k, v in data.items() if observed.get(k) != v}
    target.write_text(json.dumps(dict(sources=hashes, data=data), separators=(',', ':')) + '\n')
print('Verified original NFC data:', {k: len(v) for k, v in data.items()})
