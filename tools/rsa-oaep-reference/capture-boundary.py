from pathlib import Path
import argparse,json,subprocess
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[dict(op='plan',length=n,digest=d)for n in [-2147483648,-2147483647,-1025,-33,-1,0,1,31,32,33,2147483503,2147483646,2147483647]for d in [0,1,4,16,20,28,32,48,64,2147483647]]
run=subprocess.run([a.oracle],input=''.join(json.dumps(r)+'\n'for r in requests),capture_output=True,text=True,check=True);values=[json.loads(s)for s in run.stdout.splitlines()];assert len(values)==len(requests);data=json.dumps([dict(request=r,expected=v)for r,v in zip(requests,values)],separators=(',',':'))+'\n';f=D/'BoundaryObservations.json'
if a.verify:assert f.read_text()==data
else:f.write_text(data)
print('PASS',len(values),'original arithmetic/4byte-counter virtual boundaries; no giant allocation')
