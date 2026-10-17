from pathlib import Path
import itertools,subprocess,json,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[];names=['key','size','n','e','d','hash','witness','message','input','blinder'];faults=[[]]+[[x]for x in names]+[list(x)for x in itertools.combinations(names,2)]
for op in ['padSignatureRoot','padSignatureBytes','signRoot','signBytes','decryptRoot','decryptBytes','verify']:
 for hashMode in ['hash','none']:
  for f in faults:requests.append(dict(op=op,hashMode=hashMode,faults=f,input=[0]*128,message=[1,2,3]))
rows=[]
for i,r in enumerate(requests):
 out=subprocess.run(['/tmp/kotdoc-pkcs15-demand-oracle'],input=json.dumps(r)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
 if i%200==0:print('captured',i,flush=True)
blob=json.dumps(rows,separators=(',',':'))+'\n';target=D/'DemandObservations.json'
if a.verify:assert target.read_text()==blob
else:target.write_text(blob)
print('PASS',len(rows),'original paired root/payload/error/witness observations')
