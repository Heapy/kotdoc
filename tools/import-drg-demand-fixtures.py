#!/usr/bin/env python3
from pathlib import Path
import argparse,json,gzip,hashlib,subprocess,itertools
R=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify-oracle',action='store_true');a=p.parse_args();out=R/'core/src/test/resources/crypto-chacha-drg-demand/data.jsonl.gz'
operations=['withCallbackGood','withCallbackBad','mapCallbackGood','mapCallbackBad','applyCallbackBad','bindCallbackGood','withIgnore','withDemand','get','mapIgnore','mapDemand','applyIgnore','applyDemand','applyTwoIgnore','applyFunctionBad','bindIgnore','bindLazyDemand','bindStrictDemand','bindTwoIgnore','bindFunctionBad','mapActionIgnore','mapActionDemand','bindActionIgnore','applyArgumentRoot','pure']
requests=[dict(mode=mode,operation=operation,projections=projections)for mode,operation,projections in itertools.product(['root','value','state','good'],operations,[['value','value','state','state'],['state','state','value','value'],['value','state','value','state'],['state','value','state','value']])]
proc=subprocess.Popen([a.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True);rows=[]
for r in requests:
 proc.stdin.write(json.dumps(r,separators=(',',':'))+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,(r,proc.stderr.read());rows.append(dict(request=r,expected=json.loads(line)))
proc.stdin.close();proc.wait();assert proc.returncode==0
paths=[Path(__file__),R/'tools/chacha-drg-reference/demand-fix/DrgDemandReference.hs',R/'vendor/crypton-2.0.1/Crypto/Random.hs',R/'vendor/crypton-2.0.1/Crypto/Random/Types.hs',R/'licenses/crypton-BSD-3-Clause.txt',R/'tools/chacha-drg-reference/demand-fix/PreviousReview.json',R/'tools/chacha-drg-reference/demand-fix/README.md']
header=dict(cases=len(rows),compiler='GHC9.12.2 -O2 aarch64-osx',sources={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths})
data='\n'.join(json.dumps(x,separators=(',',':'))for x in [header]+rows)+'\n'
if a.verify_oracle:assert gzip.decompress(out.read_bytes()).decode()==data
else:out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(gzip.compress(data.encode(),mtime=0))
print('PASS',len(rows),'independent custom DRG projection/error/sharing probes',len(paths),'pins')
