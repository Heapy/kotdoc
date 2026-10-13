#!/usr/bin/env python3
"""Transcribe original TagSoup parser/position/entity assertions, never using Kotlin results."""
import argparse
import hashlib
import html.entities
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = root / 'vendor/tagsoup-0.14.8/test/TagSoup/Test.hs'
target = root / 'core/src/test/resources/html/tagsoup.json'
s = source.read_text()

# Strict reader for the small Haskell literal/Tag subset in the original assertions.
class Reader:
    def __init__(self, s): self.s, self.i = s, 0
    def space(self):
        while self.i < len(self.s) and self.s[self.i].isspace(): self.i += 1
    def peek(self):
        self.space()
        return self.s[self.i:self.i+1]
    def take(self, s):
        self.space(); assert self.s.startswith(s, self.i), (s, self.s[self.i:]); self.i += len(s)
    def string(self):
        quote = self.peek(); assert quote in ('"', "'"); self.i += 1
        result = ''
        while self.s[self.i] != quote:
            c = self.s[self.i]; self.i += 1
            if c != '\\': result += c; continue
            c = self.s[self.i]; self.i += 1
            if c in '0123456789':
                digits = c
                while self.s[self.i].isdigit(): digits += self.s[self.i]; self.i += 1
                result += chr(int(digits))
            elif c == 'x':
                digits = ''
                while self.s[self.i] in '0123456789abcdefABCDEF': digits += self.s[self.i]; self.i += 1
                result += chr(int(digits, 16))
            else: result += {'n':'\n', 'r':'\r', 't':'\t', '"':'"', "'":"'", '\\':'\\'}[c]
        self.i += 1
        return result
    def sequence(self, left='[', right=']'):
        self.take(left); result = []
        if self.peek() != right:
            while True:
                result.append(self.value())
                if self.peek() != ',': break
                self.take(',')
        self.take(right); return result
    def value(self):
        p = self.peek()
        if p in ('"', "'"): return self.string()
        if p == '[': return self.sequence()
        if p == '(': return self.sequence('(', ')')
        m = re.match(r'[0-9]+|[A-Za-z]+', self.s[self.i:]); assert m, self.s[self.i:]
        name = m[0]; self.i += len(name)
        if name.isdigit(): return int(name)
        if name == 'Nothing': return None
        if name == 'Just': return self.value()
        count = {'TagOpen': 2, 'TagClose': 1, 'TagText': 1, 'TagComment': 1, 'TagWarning': 1, 'TagPosition': 2}[name]
        values = [self.value() for _ in range(count)]
        if name == 'TagText' and isinstance(values[0], list): values[0] = ''.join(values[0])
        return [name] + values
    def finish(self): self.space(); assert self.i == len(self.s), self.s[self.i:]

cases = []
for section, end, name, positions in [('parseTests = do', 'optionsTests ::', 'parseTags', False),
                                     ('positionTests = do', 'warnTests ::', 'p', True)]:
    block = s.split(section, 1)[1].split(end, 1)[0]
    lines = re.split(r'\n\s*(?=' + name + r' ")', block)[1:]
    for line in lines:
        line = line.split('\n\n', 1)[0].strip()
        r = Reader(line); r.take(name); text = r.string(); r.take('==='); expected = r.value(); r.finish()
        cases.append({'input': text, 'positions': positions, 'expected': expected})
assert len(cases) == 53, len(cases)
entities = []
for line in s.split('entityTests = do', 1)[1].split('combiTests ::', 1)[0].splitlines():
    if not line.strip(): continue
    r = Reader(line)
    m = re.match(r'\s*(\w+)', line); function = m[1]; r.take(function)
    arg = r.string(); r.take('==='); expected = r.value(); r.finish()
    entities.append({'function': function, 'input': arg, 'expected': expected})
assert len(entities) == 12
hashes = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
          for p in sorted((root / 'vendor/tagsoup-0.14.8').rglob('*')) if p.is_file()}
# Independent Python standard-library HTML5 list, checked against the original table.
html5 = sorted(html.entities.html5.items())
assert dict(html5) == dict(json.loads((root / 'core/src/main/resources/io/heapy/kotdoc/html-entities.json').read_text())['entities'])
result = {'sources': hashes, 'parse': cases, 'entities': entities, 'html5': html5}
expected = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
p = argparse.ArgumentParser(); p.add_argument('--check', action='store_true'); args = p.parse_args()
if args.check:
    assert target.read_text() == expected, 'TagSoup fixture drift'
else:
    target.parent.mkdir(exist_ok=True, parents=True); target.write_text(expected)
print(f"{'Verified' if args.check else 'Imported'} {len(cases)} parse/position and {len(entities)} entity assertions; {len(html5)} independent HTML5 entries")
