from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for n in [0,8,9]:
 for operation in ['initialize','update','finalize','hmac']:
  for unitBottom in ['', 'init','update','finalize','all']:requests.append(dict(block=8,digest=4,key=list(range(n)),message=[3,4],operation=operation,fault='',unitBottom=unitBottom,repeat=2))
  for fault in ['init1','update','finalize']:requests.append(dict(block=8,digest=4,key=list(range(n)),message=[3,4],operation=operation,fault=fault,unitBottom='all',repeat=2))
rows=[]
for r in requests:
 run=subprocess.run([a.oracle],input=json.dumps(r),capture_output=True,text=True,check=True);rows.append(dict(request=r,expected=json.loads(run.stdout)))
blob=json.dumps(rows,separators=(',',':'))+'\n';path=D/'UnitObservations.json'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(rows),'original IO effect versus Unit-root probes')
