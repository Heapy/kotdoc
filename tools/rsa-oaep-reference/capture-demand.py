from pathlib import Path
import argparse,subprocess,json
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for d in [0,1,4,12]:
 for op in ['ignore','mgf','encryptRoot','encryptBytes','decryptRoot']:
  for size in [1,2*d+2,2*d+8]:
   for fault in ['', 'blockSize','digestSize','contextSize','init1','init2','update','finalize','mask']:
    requests.append(dict(operation=op,digest=d,size=size,length=9,seed=[1]*d,message=[2],cipher=[0]*size,label=[],fault=fault))
   for field in ['seed','message','length','size','n','e','d','label','cipher']:
    requests.append(dict(operation=op,digest=d,size=size,length=0,seed=[1]*d,message=[2],cipher=[0]*size,label=[],**{field+'Bottom':True}))
for d in [0,1,4,12]:
 for n in [-33,-1,0,1,4,13]:requests.append(dict(operation='mgf',digest=d,size=32,length=n,seed=[1,2,3],message=[],cipher=[],label=[]))
rows=[]
for i,r in enumerate(requests):
 (D/'CaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run([a.oracle],input=json.dumps(r),text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
 if i%200==0:print('captured',i,flush=True)
data=json.dumps(rows,separators=(',',':'))+'\n';f=D/'DemandObservations.json'
if a.verify:assert f.read_text()==data
else:f.write_text(data)
print('PASS',len(rows),'original generic hash/MGF/demand/error/sharing observations')
