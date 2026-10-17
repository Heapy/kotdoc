from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for operation in ['sign','signDigest','signSafer','signDigestSafer']:
 for mode in ['normal','callbackBottom','callbackBeforeLength','bytesBottom','ignoreLength','short','long','zero']:
  for projection in ['ignore','root','bytes']:
   for size in [10,64]:requests.append(dict(operation=operation,mode=mode,projection=projection,size=size,saltLength=20,budget=8))
 for bottom in ['params','saltLength','message','key','n','size','digest','trailer']:
  for mode in ['normal','callbackBeforeLength','ignoreLength']:
   for projection in ['ignore','root','bytes']:requests.append(dict(operation=operation,mode=mode,projection=projection,size=64,saltLength=20,budget=8,bottom=bottom))
 for n in [-9223372036854775808,-1,0,2147483648,9223372036854775807]:
  for projection in ['ignore','root','bytes']:requests.append(dict(operation=operation,mode='ignoreLength',projection=projection,size=64,saltLength=n,budget=8))
rows=[]
for i,r in enumerate(requests):
 (D/'CallbackCaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run(['/tmp/kotdoc-pss-callback-oracle'],input=json.dumps(r),text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/'CallbackObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(rows),'original PSS IO callback effects/ignored lengths/int64/projections/sharing')
