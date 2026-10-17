from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[];excluded=[]
for op in ['sign','signDigest','signSafer','signDigestSafer']:
 for mode in ['normal','pairBottom','generatorBottom','fieldsBottom','bytesBottom','stateBottom','zero','empty','short','long','ignoreLength']:
  for projection in ['ignore','root','state','bytes']:
   for size in [10,64]:
    r=dict(op=op,mode=mode,projection=projection,size=size)
    if op in ['signSafer','signDigestSafer']and mode=='empty'and projection!='ignore':excluded.append(dict(request=r,reason='not executed: successor salt draw demands Generate p0 on empty blinder RNG buffer'));continue
    requests.append(r)
 for bottom in ['params','saltLength','message','key','n','size','digest','trailer']:
  for projection in ['ignore','root','state','bytes']:
   for mode in ['normal','pairBottom','ignoreLength']:requests.append(dict(op=op,mode=mode,projection=projection,size=64,bottom=bottom))
 for n in [-1,0,1,32,2147483648,9223372036854775807]:
  for projection in ['ignore','root','state','bytes']:
   requests.append(dict(op=op,mode='ignoreLength',projection=projection,size=64,saltLength=n))
key=json.loads((D/'SourceCases.json').read_text())[0]['request'];fields={k:v for k,v in key.items()if k in ['size','n','e','d','p','q','dP','dQ','qinv']}
for op in ['signSafer','signDigestSafer']:
 for mode in ['normal','pairBottom','bytesBottom','stateBottom','short','long']:
  for projection in ['ignore','root','state','bytes']:requests.append(dict(fields,op=op,mode=mode,projection=projection,keyProfile='sourceCRT'))
rows=[]
for i,r in enumerate(requests):
 (D/'RandomCaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run(['/tmp/kotdoc-pss-random-oracle'],input=json.dumps(r)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
for name,data in [('RandomObservations',rows),('ExcludedRandomDomains',excluded)]:
 blob=json.dumps(data,separators=(',',':'))+'\n';f=D/(name+'.json')
 if a.verify:assert f.read_text()==blob
 else:f.write_text(blob)
print('PASS',len(rows),'original PSS pure RNG/length laziness/state/sharing/CRT traces;',len(excluded),'unsafe empty safer requests excluded BEFORE execution')
