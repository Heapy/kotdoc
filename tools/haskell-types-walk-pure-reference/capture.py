#!/usr/bin/env python3
"""Capture/replay actual pinned Walk/Generic calls; never compute expected values."""
import argparse,json,re,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
FIXTURE=ROOT/'core/src/test/resources/pandoc-types/walk-pure/demand-probes.jsonl'
def requests():
    names=re.findall(r'^ "([^"]+)"->',(HERE/'TypesWalkPureReference.hs').read_text(),re.M)
    assert len(names)==len(set(names))
    return ''.join(json.dumps({'probe':p},separators=(',',':'))+'\n'for p in names).encode()
def combined(requests,responses):
    req=requests.splitlines();res=responses.splitlines();assert len(req)==len(res)
    return ''.join(json.dumps({'request':json.loads(q),'response':json.loads(r)},separators=(',',':'))+'\n'for q,r in zip(req,res)).encode()
def main():
    p=argparse.ArgumentParser();p.add_argument('--binary',required=True);p.add_argument('--write',action='store_true');a=p.parse_args()
    req=requests();r=subprocess.run([a.binary],input=req,capture_output=True,check=True);res=r.stdout
    stream=combined(req,res)
    if a.write:
        (HERE/'original-requests.jsonl').write_bytes(req);(HERE/'original-responses.jsonl').write_bytes(res)
        FIXTURE.parent.mkdir(parents=True,exist_ok=True);FIXTURE.write_bytes(stream)
    else:
        assert req==(HERE/'original-requests.jsonl').read_bytes()
        assert res==(HERE/'original-responses.jsonl').read_bytes(),'original byte-exact replay differs'
        assert stream==FIXTURE.read_bytes(),'paired fixture differs from original payload'
    rows=[json.loads(x)for x in req.splitlines()]
    represented=sum(r['probe'].startswith('walk')for r in rows)
    print(f'Original byte-exact demand replay PASS: {len(rows)} responses, {represented} Walk observations, {len(rows)-represented} Generic contrast probes')
if __name__=='__main__':main()
