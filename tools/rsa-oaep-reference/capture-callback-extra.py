from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for op in ['decryptSafer']:
 for k in [1,64]:
  for mode in ['ff','retrySuccess']:
   for projection in ['ignore','root','value']:
    requests.append(dict(operation=op,size=k,mode=mode,projection=projection,messageBottom=False,message=[7],cipher=[0]*k))
rows=[]
for i,r in enumerate(requests):
 out=subprocess.run([a.oracle],input=json.dumps(r),capture_output=True,text=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
 if i%200==0:print('captured',i,flush=True)
f=D/'CallbackExtraObservations.json';data=json.dumps(rows,separators=(',',':'))+'\n'
if a.verify:assert f.read_text()==data
else:f.write_text(data)
print('PASS',len(rows),'actual original callback RNG/projection/sharing rows')
