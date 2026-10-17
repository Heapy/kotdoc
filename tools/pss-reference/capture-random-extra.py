from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();key=json.loads((D/'SourceCases.json').read_text())[0]['request'];fields={k:v for k,v in key.items()if k in ['size','n','e','d','p','q','dP','dQ','qinv']};rows=[]
for profile in [dict(size=64),dict(fields,keyProfile='sourceCRT')]:
 for op in ['signSafer','signDigestSafer']:
  for projection in ['state','bytes']:
   for mode,retries in [('long',0),('longThenNormal',99),('longThenNormal',100)]:
    r=dict(profile,op=op,projection=projection,mode=mode,retries=retries,budget=200)
    out=subprocess.run(['/tmp/kotdoc-pss-random-oracle'],input=json.dumps(r)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/'RandomExtraObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(rows),'original PSS safer max100 retry/error/eventual102draw controls')
