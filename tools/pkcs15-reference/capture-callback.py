from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for operation in ['pad','encrypt','signSafer','decryptSafer']:
 for mode in ['normal','callbackBottom','bytesBottom','zero','retrySuccess']:
  for projection in ['ignore','root','bytes']:
   for size in [10,32,64]:
    for bottom in [False,True]:requests.append(dict(operation=operation,mode=mode,projection=projection,size=size,message=[1,2,3],messageBottom=bottom,cipher=[],budget=8,retries=2))
for operation in ['pad','encrypt']:
 for projection in ['ignore','root','bytes']:
  for mode in ['zero','retrySuccess']:
   requests.append(dict(operation=operation,mode=mode,projection=projection,size=32,message=[1,2,3],cipher=[],budget=5001,retries=5000))
rows=[]
for r in requests:
 out=subprocess.run(['/tmp/kotdoc-pkcs15-callback-oracle'],input=json.dumps(r),text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/'CallbackObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(rows),'original IO callbacks/effects/projections/long retries')
