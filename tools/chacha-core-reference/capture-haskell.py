#!/usr/bin/env python3
from pathlib import Path
import argparse,json,re,subprocess
D=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();rows=[]
for mode in ['--vectors','--samples']:
 r=subprocess.run([a.oracle,mode],text=True,capture_output=True,check=True);rows += [json.loads(x)for x in r.stdout.splitlines()]
assert sum('family'in r['request']for r in rows)==600
suite=subprocess.run([a.oracle,'--suite','--qc-max-success=200','--seed=1729','--no-color'],text=True,capture_output=True,check=True).stdout;suite=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite);assert '0 failures'in suite,suite
for name,data in [('HaskellObservations.json',json.dumps(rows,separators=(',',':'))+'\n'),('OriginalSuite.txt',suite)]:
 path=D/name
 if a.verify:assert path.read_text()==data,name
 else:path.write_text(data)
print('PASS',len(rows),'actual original ChaCha KAT/property/carry observations;',suite.strip().splitlines()[-1])
