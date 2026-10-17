#!/usr/bin/env python3
from pathlib import Path
import argparse,json,gzip,hashlib,subprocess
R=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();out=R/'core/src/test/resources/crypto-chacha-drg-secret/data.jsonl.gz';rows=[]
for mode in ['failure','recover','success']:
 result=subprocess.run([a.oracle,mode],text=True,capture_output=True,check=True);rows.append(dict(request=dict(mode=mode),expected=json.loads(result.stdout)))
paths=[Path(__file__),R/'tools/chacha-drg-reference/secret-fix/DrgSecretReference.hs',R/'tools/chacha-drg-reference/ControlledProbabilistic.hs',R/'vendor/crypton-2.0.1/Crypto/Random/Probabilistic.hs',R/'licenses/crypton-BSD-3-Clause.txt',R/'vendor/crypton-2.0.1/Crypto/Internal/Compat.hs',R/'tools/chacha-drg-reference/secret-fix/README.md',R/'tools/chacha-drg-reference/secret-fix/PreviousReview.json']
header=dict(cases=3,observations=30,compiler='GHC9.12.2 -O2 aarch64-osx',sources={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths})
data='\n'.join(json.dumps(x,separators=(',',':'))for x in [header]+rows)+'\n'
if a.verify_oracle:assert gzip.decompress(out.read_bytes()).decode()==data
else:out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(gzip.compress(data.encode(),mtime=0))
print('PASS3processlifetimes/30synchronous failure,identity,count/lazy/success observations',len(paths),'pins')
