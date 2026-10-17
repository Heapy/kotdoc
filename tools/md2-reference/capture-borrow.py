from pathlib import Path
import json,random,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();rng=random.Random(6132769);requests=[dict(message=list(rng.randbytes(n)))for n in [0,1,15,16,17,63,64,65,100,1024]]
r=subprocess.run([a.oracle],input=''.join(json.dumps(x)+'\n'for x in requests),capture_output=True,text=True,check=True);values=[json.loads(x)for x in r.stdout.splitlines()];assert len(values)==len(requests);blob=json.dumps([dict(request=r,expected=e)for r,e in zip(requests,values)],separators=(',',':'))+'\n';p=D/'BorrowObservations.json'
if a.verify:assert p.read_text()==blob
else:p.write_text(blob)
print('PASS',len(requests),'actual Context/Digest/MutableContext borrowed-storage/copy observations')
