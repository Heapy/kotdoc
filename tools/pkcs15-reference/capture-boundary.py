from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[dict(op=op,length=k,messageLength=n)for op in ['signature','pad']for k in [-(2**63),-(2**63)+10,-1,0,10,11,12,2**31-1,2**31,2**63-12,2**63-1]for n in [0,1,3,11]]
v=subprocess.run(['/tmp/kotdoc-pkcs15-boundary-oracle'],input=''.join(json.dumps(r)+'\n'for r in requests),text=True,capture_output=True,check=True);values=[json.loads(x)for x in v.stdout.splitlines()];assert len(values)==len(requests)
blob=json.dumps([dict(request=r,expected=e)for r,e in zip(requests,values)],separators=(',',':'))+'\n';f=D/'BoundaryObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS88 original root-only bounds; no giant payload or RNG')
