#!/usr/bin/env python3
"""Observe the unmodified, pinned http-client PSL modules; never import Kotlin output."""
import argparse
import collections
import gzip
import hashlib
import itertools
import json
from pathlib import Path
import random
import subprocess

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-public-suffix-reference'
target = root / 'core/src/test/resources/public-suffix/data.jsonl.gz'
resource = root / 'core/src/main/resources/io/heapy/kotdoc/http/public-suffix-list.bin'
p = argparse.ArgumentParser()
p.add_argument('--oracle')
p.add_argument('--check-source', action='store_true')
args = p.parse_args()

# This release's one ASCII ByteString literal uses only named NUL escapes.
# Keep the original bytes, rather than adopting a current list or exporting a Kotlin tree.
source = root / 'vendor/http-client-0.7.19/publicsuffixlist/Network/PublicSuffixList/DataStructure.hs'
literal = source.read_text().split('serializedDataStructure = "', 1)[1].split('"\n', 1)[0]
literal = literal.replace('\\NUL', '\0')
assert '\\' not in literal and '"' not in literal and literal.isascii()
snapshot = literal.encode('ascii')
if args.check_source:
    assert resource.read_bytes() == snapshot
else:
    resource.parent.mkdir(parents=True, exist_ok=True)
    resource.write_bytes(snapshot)

with gzip.open(root / 'core/src/test/resources/http-response/data.jsonl.gz', 'rt') as f:
    parent = json.loads(next(f))
sources = list(parent['sources']) + [
    'core/src/test/resources/http-response/data.jsonl.gz',
    'tools/import-public-suffix-fixtures.py', str(resource.relative_to(root)),
]
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes():
    return {s: hashlib.sha256((root / s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()

if args.check_source:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f))
        cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes()
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert manifest['groups'] == dict(collections.Counter(c['group'] for c in cases))
    assert manifest['snapshotBytes'] == len(snapshot)
    print('Verified', len(cases), 'original PSL observations,', len(snapshot), 'exact embedded bytes and', len(manifest['sources']), 'source hashes')
    raise SystemExit

assert args.oracle
proc = subprocess.Popen([args.oracle], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def ask(value):
    proc.stdin.write(json.dumps(value) + '\n')
    proc.stdin.flush()
    line = proc.stdout.readline()
    assert line, value
    return json.loads(line)
cases = []
def add(group, **request):
    cases.append(dict(group=group, request=request, expected=ask(request)))
points = lambda s: list(map(ord, s))
assert bytes(ask(dict(mode='snapshot'))) == snapshot
original_tree = ask(dict(mode='snapshot-tree'))
add('entire-original-snapshot-serialization', mode='snapshot')
add('entire-original-snapshot-tree', mode='snapshot-tree')
def domains(tree, previous=()):
    for key, child in tree:
        path = (''.join(map(chr, key)),) + previous
        yield '.'.join(path), not child
        yield from domains(child, path)
node_counts = []
for name, tree in zip(['rule', 'exception'], original_tree):
    entries = list(domains(tree))
    node_counts.append(len(entries))
    for domain, leaf in entries:
        # Every leaf AND internal node, with wildcard selection, exact prefixes,
        # deeper domains, raw case and leading/trailing empty labels.
        variants = [domain, 'www.' + domain, 'deep.www.' + domain,
                    domain.upper(), '.' + domain, domain + '.']
        if '*' in domain:
            variants += [domain.replace('*', 'chosen'), 'www.' + domain.replace('*', 'chosen')]
        for variant in variants:
            add('all-original-' + name + '-nodes', mode='lookup', domain=points(variant))

for domain in ['', '.', '..', '...', 'com', 'COM', 'www.com', 'co.uk', 'www.co.uk',
               'unknown', 'a.unknown', 'a.b.unknown', 'localhost', 'a.localhost',
               '127.0.0.1', '[::1]', 'www.食狮.公司.cn', '公司.cn', 'xn--55qx5d.cn',
               'www.ck', 'a.ck', 'a.www.ck', 'city.kawasaki.jp', 'a.city.kawasaki.jp',
               'kawasaki.jp', 'a.kawasaki.jp', 'blogspot.com', 'a.blogspot.com',
               'a\0com', 'a\n.com', 'a..com', 'a.COM', '💡.com', '\ud800.com']:
    add('literal-raw-domain-boundaries', mode='lookup', domain=points(domain))

def node(*pairs):
    return [[points(key), child] for key, child in pairs]
mock_trees = [
    [[], []], [node(('com', [])), []], [[], node(('com', []))],
    [node(('*', [])), node(('com', []))],
    [node(('com', node(('a', []), ('*', node(('x', [])))))), node(('com', node(('a', []))))],
    [node(('com', node(('a', node(('b', []))), ('*', [])))), node(('com', node(('*', []))))],
    [node(('a', []), ('a', node(('b', []))), ('', []), ('*', [])), node(('a', node(('b', []))))],
    [node(('💡', []), ('\uffff', []), ('\ue000', []), ('\ud800', []), ('\ufffd', node(('a', [])))), node(('a', []))],
]
for data in mock_trees:
    add('custom-tree-order-duplicates-and-Unicode', mode='encode', data=data)
    for n in range(1, 5):
        for labels in itertools.product(['a', 'b', 'com', '*', ''], repeat=n):
            add('custom-tree-wildcards-exceptions-and-empty-labels', mode='lookup', data=data, domain=points('.'.join(labels)))

rng = random.Random(624)
keys = ['', 'a', 'b', '*', '\0', 'a.b', 'é', '💡', '\ue000', '\uffff', '\ud800', '\udfff', '\ufffd']
def random_tree(depth):
    if depth == 0:
        return []
    return node(*[(rng.choice(keys), random_tree(depth-1)) for _ in range(rng.randrange(5))])
for _ in range(150):
    add('random-original-map-serialization', mode='encode', data=[random_tree(3), random_tree(2)])

encoded = ask(dict(mode='encode', data=mock_trees[4]))['bytes']
inputs = [encoded[:i] for i in range(len(encoded)+1)]
inputs += [[], [0], [0, 0], [0, 0, 255], [255, 0, 0], list(snapshot)]
inputs += [list(snapshot[:i]) for i in [1, 10, 100, 1000, len(snapshot)-1, len(snapshot)-2]]
for i in range(256):
    inputs += [[i], [i, 0, 0], [0, i, 0, 0], [0, 0, i]]
for text in ['é', '💡', '\ufffd', '\U0010ffff']:
    data = list(text.encode())
    inputs += [data[:i] + [0, 0] for i in range(len(data)+1)]
for data in inputs:
    add('binary-truncation-UTF8-EOF-and-ignored-tail', mode='decode', bytes=data)

proc.stdin.close()
assert proc.wait() == 0
manifest = dict(upstream='http-client-0.7.19 bundled PublicSuffixList', sources=hashes(),
                snapshotBytes=len(snapshot), originalTreeNodes=node_counts,
                cases=len(cases), caseHash=digest(cases),
                groups=dict(collections.Counter(c['group'] for c in cases)))
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as f, gzip.GzipFile(filename='', fileobj=f, mode='wb', mtime=0) as gz:
    for value in [manifest] + cases:
        gz.write((json.dumps(value, ensure_ascii=True, separators=(',', ':')) + '\n').encode())
print(json.dumps({k:v for k,v in manifest.items() if k != 'sources'}, indent=2))
