#!/usr/bin/env python3
"""Capture the POSIX calendar environment of X509 oracles, independently of Kotlin."""
import argparse, gzip, hashlib, json, subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--oracle')
p.add_argument('--check-source', action='store_true')
p.add_argument('--verify-oracle', action='store_true')
a = p.parse_args()
target = root/'core/src/test/resources/x509-posix-time/data.json'
sources = {str(Path(__file__).relative_to(root))}
dates = set()

def collect(v):
    if isinstance(v, list):
        if len(v) == 7 and all(type(x) is int for x in v) and 0 <= v[1] < 12:
            dates.add(tuple(v))
        for x in v:
            collect(x)
    elif isinstance(v, dict):
        for x in v.values():
            collect(x)

for name in ['x509-certificate', 'x509-crl']:
    path = root/f'core/src/test/resources/{name}/data.jsonl.gz'
    sources.add(str(path.relative_to(root)))
    with gzip.open(path, 'rt') as f:
        sources.update(json.loads(next(f))['sources'])
        for line in f:
            collect(json.loads(line)['request'])
hashes = {s: hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(sources)}
inputs = [list(d) for d in sorted(dates)]

def query(requests):
    assert a.oracle, '--oracle required'
    return [json.loads(x) for x in subprocess.check_output(
        [a.oracle], input=''.join(json.dumps(r)+'\n' for r in requests).encode(), timeout=120
    ).splitlines()]

if a.check_source or a.verify_oracle:
    data = json.loads(target.read_text())
    assert data['sources'] == hashes and data['inputs'] == inputs
    assert data['modelHash'] == hashlib.sha256(json.dumps(data['model'], sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    if a.check_source:
        print('Verified POSIX X509 time model:', len(inputs), 'date inputs,', len(data['model']), 'native conversions,', len(hashes), 'source hashes')
        raise SystemExit

elapsed = query([dict(api='elapsed', value=d) for d in inputs])
assert len(elapsed) == len(inputs) and all(isinstance(x, str) for x in elapsed)
seconds = sorted(set(elapsed), key=int)
outputs = query([dict(api='nativeDate', seconds=s) for s in seconds])
assert len(outputs) == len(seconds)
model = dict(zip(seconds, outputs))
if a.verify_oracle:
    assert data['elapsed'] == elapsed and data['model'] == model
    print('Replayed POSIX X509 time model:', len(inputs), 'date inputs and', len(model), 'native conversions')
else:
    data = dict(sources=hashes, inputs=inputs, elapsed=elapsed, model=model,
                modelHash=hashlib.sha256(json.dumps(model, sort_keys=True, separators=(',', ':')).encode()).hexdigest())
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, sort_keys=True, separators=(',', ':'))+'\n')
    print('Captured POSIX X509 time model:', len(inputs), 'date inputs,', len(model), 'native conversions, SHA256', data['modelHash'])
