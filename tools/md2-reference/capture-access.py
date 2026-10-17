from pathlib import Path
import argparse,subprocess,json
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for seed in [[],[1],[1]*15,[1]*16,[1]*17]:
 for operation in ['hashUpdateRoot','hashUpdatesRoot','mutableUpdate','digestMaybeRoot','digestRoot','digestBytes']:
  for fault,twice,bottom in [('',False,False),('key-length',False,False),('key-before',False,False),('key-after',False,False),('',True,False),('',False,True)]:
   requests.append(dict(operation=operation,seed=seed,accessFault=fault,twiceKey=twice,bottomKeyResult=bottom))
rows=[]
for i,r in enumerate(requests):
 (D/'AccessCaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run([a.oracle],input=json.dumps(r),text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
 if i%200==0:print('captured',i,flush=True)
data=json.dumps(rows,separators=(',',':'))+'\n';f=D/'AccessObservations.json'
if a.verify:assert f.read_text()==data
else:f.write_text(data)
print('PASS',len(rows),'original generic hash/access/demand/error/sharing observations')
