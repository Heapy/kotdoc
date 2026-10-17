from pathlib import Path
import argparse,subprocess,json
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
import itertools
for d in [0,1,4,12]:
 for op in ['ignore','mgf','encryptRoot','encryptBytes','decryptRoot']:
  for fields in itertools.combinations(['seed','message','length','size','n','e','d','label','cipher'],2):
   requests.append(dict(operation=op,digest=d,size=2*d+8,length=0,seed=[1]*d,message=[2],cipher=[0]*(2*d+8),label=[],**{field+'Bottom':True for field in fields}))
rows=[]
for i,r in enumerate(requests):
 (D/'ExtraCaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run([a.oracle],input=json.dumps(r),text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
 if i%200==0:print('captured',i,flush=True)
data=json.dumps(rows,separators=(',',':'))+'\n';f=D/'DemandExtraObservations.json'
if a.verify:assert f.read_text()==data
else:f.write_text(data)
print('PASS',len(rows),'original generic hash/MGF/demand/error/sharing observations')
