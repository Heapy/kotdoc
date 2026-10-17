from pathlib import Path
import argparse,subprocess,json,random
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();rng=random.Random(6134623)
requests=[dict(key=list(rng.randbytes(k)),message=list(rng.randbytes(n)))for k in [0,1,63,64,65,300]for n in [0,1,63,64,1024]]
run=subprocess.run([a.oracle],input=''.join(json.dumps(r)+'\n'for r in requests),capture_output=True,text=True,check=True);expected=[json.loads(s)for s in run.stdout.splitlines()];assert len(expected)==len(requests)
blob=json.dumps([dict(request=r,expected=e)for r,e in zip(requests,expected)],separators=(',',':'))+'\n';path=D/'BorrowObservations.json'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(requests),'actual HMAC/Digest borrowed access and copy observations')
