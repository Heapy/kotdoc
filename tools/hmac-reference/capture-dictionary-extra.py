from pathlib import Path
import argparse,json,subprocess
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for n in [0,8,9]:
 for op in ['initialize','update','finalize','hmac','ignored','outerBottom','innerBottom','equalBottom','macBottom','getDigestBottom','updatesTail','updatesEmptyTail','updatesMessageBottom','updatesListBottom']:
  for fault in ['', 'init1','init2','update','finalize']:
   requests.append(dict(block=8,digest=4,key=list(range(n)),message=[3,4],operation=op,fault=fault,repeat=2))
for n in [0,8,9]:
 for operation in ['update','hmac']:requests.append(dict(block=8,digest=4,key=list(range(n)),message=[],operation=operation,fault='',repeat=2))
rows=[]
for i,r in enumerate(requests):
 run=subprocess.run([a.oracle],input=json.dumps(r),capture_output=True,text=True,check=True);rows.append(dict(request=r,expected=json.loads(run.stdout)))
 if i%100==0:print('captured',i,flush=True)
blob=json.dumps(rows,separators=(',',':'))+'\n';path=D/'DictionaryExtraObservations.json'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(rows),'original repeated-root/list/newtype probes')
