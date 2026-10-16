#!/usr/bin/env python3
"""Observe complete crypton-connection 0.4.6 algorithms, with real file IO/TLS effects."""
import argparse, collections, gzip, hashlib, itertools, json, random, subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-connection-reference'
target = root / 'core/src/test/resources/connection/data.jsonl.gz'
parser = argparse.ArgumentParser()
parser.add_argument('--oracle')
parser.add_argument('--check-source', action='store_true')
args = parser.parse_args()

source = root / 'vendor/crypton-connection-0.4.6'
manifest = json.loads((root / 'vendor/http-tls-sources.json').read_text())['crypton-connection']
assert manifest['version'] == '0.4.6'
assert {str(f.relative_to(source)) for f in source.rglob('*') if f.is_file()} == set(manifest['files'])
for path, expected_hash in manifest['files'].items():
    assert hashlib.sha256((source / path).read_bytes()).hexdigest() == expected_hash, path
s = (source / 'Network/Connection.hs').read_bytes()
a, b = s.index(b'module Network.Connection'), s.index(b'import Control.Concurrent.MVar')
s = s[:a] + b'module OriginalConnection where\n\n' + s[b:]
s = s.replace(b'import Network.Connection.Types', b'import OriginalConnectionTypes')
s = s.replace(b'import qualified Network.TLS as TLS', b'import qualified ConnectionTlsEffects as TLS')
assert (folder / 'OriginalConnection.hs').read_bytes() == s
s = (source / 'Network/Connection/Types.hs').read_bytes()
s = s.replace(b'module Network.Connection.Types', b'module OriginalConnectionTypes')
s = s.replace(b'import qualified Network.TLS as TLS', b'import qualified ConnectionTlsEffects as TLS')
assert (folder / 'OriginalConnectionTypes.hs').read_bytes() == s
assert b'test-suite' not in (source / 'crypton-connection.cabal').read_bytes()

sources = ['tools/import-connection-fixtures.py', 'vendor/http-tls-sources.json',
           'licenses/crypton-connection-BSD-3-Clause.txt']
for base in (source, folder):
    sources += [str(f.relative_to(root)) for f in base.rglob('*') if f.is_file()]
def hashes():
    return {s: hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cases):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cases).encode()).hexdigest()
if args.check_source:
    with gzip.open(target, 'rt') as f:
        metadata = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert metadata['sources'] == hashes()
    assert metadata['cases'] == len(cases) and metadata['caseHash'] == digest(cases)
    assert metadata['groups'] == dict(collections.Counter(c['group'] for c in cases))
    print('Verified', len(cases), 'original connection programs and', len(metadata['sources']), 'source hashes')
    raise SystemExit
assert args.oracle
cases = []
def cmd(op, **v): return dict(op=op, **v)
def add(group, data, commands, **v):
    cases.append(dict(group=group, request=dict(input=list(data), commands=commands, **v)))
drain = [cmd('get', size=1), cmd('chunk'), cmd('chunk'), cmd('line', limit=10), cmd('get', size=0)]
inputs = [b'', b'a', b'abc', b'\n', b'\r\n', b'a\nb\nrest', bytes(range(256))]
sizes = [-2**63, -1, 0, 1, 2, 3, 255, 1500, 16384, 2**63-1]
for data, op, size in itertools.product(inputs, ['get', 'exact', 'split'], sizes):
    add('read-sizes-EOF-and-Int64', data, [cmd(op, size=size)] + drain)
for data, mode, size in itertools.product(inputs, ['normal', 'pair-fail', 'rest-fail', 'value-fail'], [-1, 0, 1, 3, 2**63-1]):
    for buffered in [False, True]:
        add('lazy-pair-result-remainder-and-modifyMVar-rollback', b'z' if buffered else data,
            [cmd('split', size=size, mode=mode)] + drain, **(dict(buffer=list(data)) if buffered else {}))
for data, limit in itertools.product(inputs + [b'a'*16384 + b'\nX', b'a'*16383+b'\nX',
                                              b'a'*16385, b'a'*32768+b'\n'], [-2**63, -1, 0, 1, 3, 16383, 16384, 16385, 2**63-1]):
    add('line-LF-only-first-EOF-limit-and-16384-byte-boundaries', data,
        [cmd('line', limit=limit), cmd('line', limit=limit)] + drain)
for data, buffer, milliseconds in itertools.product(inputs, [None, [], [97, 10]], [-2**63, -1, 0, 1, 1000, 2**63-1]):
    # Positive file waits have deterministic immediate input. Negative and wrapped
    # timeouts run the source action without a timer; zero does not touch it.
    add('wait-nonconsumption-EOF-zero-and-Int64-microsecond-overflow', data,
        [cmd('wait', milliseconds=milliseconds)] + drain, buffer=buffer)
for data, buffer, tls in itertools.product(inputs[:4], [None, [], [1, 2, 3]], [False, True]):
    for failure in [None, 'contextNew-io', 'contextNew-fatal', 'handshake-io', 'handshake-fatal']:
        upgrade = cmd('upgrade', **({} if failure is None else dict(failure=failure)))
        add('TLS-upgrade-cell-rollback-buffer-discard-and-already-secure', data,
            [cmd('secure'), upgrade, cmd('secure'), cmd('get', size=1),
             cmd('upgrade', paramsFail=True), cmd('secure')] + drain,
            buffer=buffer, tls=tls, tlsChunks=[[10, 20], [30]])
for tls, buffer in itertools.product([False, True], [None, [], [1, 2]]):
    add('upgrade-parameters-remain-lazy-on-TLS-and-fail-before-contextNew', b'abc',
        [cmd('upgrade', paramsFail=True), cmd('secure')] + drain, tls=tls, buffer=buffer)
for failures in [[], ['bye-io'], ['bye-fatal'], ['close-io'], ['close-fatal'],
                 ['bye-fatal', 'close-io'], ['bye-io', 'close-fatal']]:
    add('TLS-close-catches-only-IO-and-always-finalizes-with-replacement', b'',
        [cmd('put', bytes=[0, 255]), cmd('close', failures=failures), cmd('secure'),
         cmd('chunk'), cmd('close')], tls=True, tlsChunks=[[9]])
for byte in range(256):
    add('all-byte-values-with-line-and-remainder', bytes([byte, 10, 255-byte]),
        [cmd('line', limit=0), cmd('split', size=1), cmd('line', limit=1), cmd('chunk')],
        host=[0, byte, 0x1f600, 0xd800], port=65536+byte)
rng = random.Random(0x434f4e4e)
for i in range(250):
    data = bytes(rng.choice([0, 1, 10, 13, 97, 128, 255]) for _ in range(rng.randrange(120)))
    commands = []
    for j in range(15):
        op = rng.choice(['get', 'exact', 'chunk', 'line', 'split', 'wait'])
        commands.append(cmd(op, **(dict(size=rng.choice([-1, 0, 1, 3, 10, 50])) if op in ['get', 'exact', 'split']
                                   else dict(limit=rng.choice([-1, 0, 5, 100])) if op=='line'
                                   else dict(milliseconds=rng.choice([-1, 0])) if op=='wait' else {})))
    add('seeded-stateful-reading-programs', data, commands)
for secure, params_fail, failure in itertools.product([False, True], [False, True],
        [None, 'contextNew-io', 'contextNew-fatal', 'handshake-io', 'handshake-fatal']):
    add('connectFromHandle-ignores-SOCKS-lazy-context-and-handshake-error-ownership', b'', [],
        factory=True, secure=secure, paramsFail=params_fail, **({} if failure is None else dict(failure=failure)))
for k1, k2 in itertools.product([[], [0], [1], [255], [1, 0]], repeat=2):
    commands = [cmd('resume', id=k1), cmd('establish', id=k1, value='first'),
                cmd('resume', id=k1), cmd('resume', id=k1), cmd('establish', id=k2, value='second'),
                cmd('once', id=k1), cmd('once', id=k1), cmd('resume', id=k2),
                cmd('invalidate', id=k2), cmd('resume', id=k2), cmd('establish', id=k1, value='third'),
                cmd('invalidate', id=k2), cmd('once', id=k1)]
    add('original-session-insert-overwrite-lookup-delete-and-no-tickets', b'', commands, session=True)

# Each program resets TLS effects; all stateful operations remain in one original
# connection. The process evaluates the unchanged upstream algorithms.
process = subprocess.run([args.oracle], input=''.join(json.dumps(c['request'])+'\n' for c in cases),
                         text=True, capture_output=True)
assert process.returncode == 0, process.stderr
observed = [json.loads(line) for line in process.stdout.rstrip('\n').split('\n')]
assert len(observed) == len(cases)
for c, result in zip(cases, observed): c['expected'] = result
metadata = dict(upstream='crypton-connection 0.4.6 complete original module/types; GHC 9.12.2; real binary files and controlled TLS effects',
                cases=len(cases), caseHash=digest(cases), sources=hashes(),
                groups=dict(collections.Counter(c['group'] for c in cases)))
target.parent.mkdir(parents=True, exist_ok=True)
with target.open('wb') as f, gzip.GzipFile(filename='', fileobj=f, mode='wb', mtime=0) as gz:
    for v in [metadata] + cases:
        gz.write((json.dumps(v, ensure_ascii=True, separators=(',', ':'))+'\n').encode())
print(json.dumps({k:v for k,v in metadata.items() if k!='sources'}, indent=2))
