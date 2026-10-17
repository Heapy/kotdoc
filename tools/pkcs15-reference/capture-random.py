from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for op in ['pad','encrypt','signSafer','decryptSafer']:
 for mode in ['normal','pairBottom','generatorBottom','fieldsBottom','bytesBottom','stateBottom','zero','empty','short','long','retrySuccess']:
  for projection in ['ignore','root','state','bytes']:
   for size in [10,64]:requests.append(dict(op=op,mode=mode,projection=projection,size=size))
 for projection in ['ignore','root','state','bytes']:
  for mode in ['normal','pairBottom','fieldsBottom','retrySuccess']:
   requests.append(dict(op=op,mode=mode,projection=projection,size=64,bottom='message'))
for op in ['pad','encrypt']:
 for projection in ['ignore','root','state','bytes']:
  for retries in [0,10,100,5000]:requests.append(dict(op=op,mode='retrySuccess',projection=projection,size=32,retries=retries,budget=retries+3))
  requests.append(dict(op=op,mode='zero',projection=projection,size=32,budget=5001))
# Historical full456 are immutable. Never execute the four known out-of-bounds
# empty safer/state requests: only their preserved historical observations remain.
original=json.loads((D/'RandomObservations.json').read_text())
assert [row['request']for row in original]==requests
rows=[]
for i,r in enumerate(requests):
 if r['op']in['signSafer','decryptSafer']and r['mode']=='empty'and r['projection']=='state':continue
 (D/'RandomCaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run(['/tmp/kotdoc-pkcs15-random-oracle'],input=json.dumps(r)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
 if i%100==0:print('captured',i,flush=True)
assert len(rows)==452
expected=[r for r in original if not(r['request']['op']in['signSafer','decryptSafer']and r['request']['mode']=='empty'and r['request']['projection']=='state')]
assert rows==expected
print('PASS452 original defined DRG observations; historical456 untouched; unsafe4 never executed')
