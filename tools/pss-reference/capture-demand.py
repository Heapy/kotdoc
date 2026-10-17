from pathlib import Path
import json,subprocess,itertools,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();faults=['params','hash','mask','saltLength','trailer','key','size','n','e','d','salt','message','digest','signature','blinder'];requests=[]
for op in ['signRoot','signBytes','digestRoot','digestBytes','verify','verifyDigest']:
 for fs in [[]]+[[x]for x in faults]+[list(x)for x in itertools.combinations(faults,2)]:
  for signature in [[],[0]*128]:requests.append(dict(op=op,faults=fs,salt=[7]*20,message=[1,2,3],signature=signature))
out=subprocess.run(['/tmp/kotdoc-pss-demand-oracle'],input=''.join(json.dumps(r)+'\n'for r in requests),text=True,capture_output=True,check=True);values=[json.loads(s)for s in out.stdout.splitlines()];assert len(values)==len(requests);blob=json.dumps([dict(request=r,expected=e)for r,e in zip(requests,values)],separators=(',',':'))+'\n';f=D/'DemandObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(values),'original PSS paired-demand rows')
