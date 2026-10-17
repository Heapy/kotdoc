#!/usr/bin/env python3
import pathlib,json,subprocess,argparse,itertools
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent;qs=[]
def paths(n,p=''):
 if n==1:return[],['field:'+p]
 l=n//2;lp,lf=paths(l,p+'L');rp,rf=paths(n-l,p+'R');return[p]+lp+rp,lf+rf
for t,n in [('PublicKey',3),('PrivateKey',7)]:
 ps,fs=paths(n)
 for op in ['genericToRoot','genericToShow']+(['genericToDebug']if n==7 else[]):
  for errors in [[]]+[[x]for x in ps+fs]+[list(x)for x in itertools.combinations(ps,2)]:qs.append(dict(type=t,op=op,repErrors=errors))
r=subprocess.run(['/tmp/kotdoc-rsa-types-oracle'],input=''.join(json.dumps(q)+'\n'for q in qs),capture_output=True,text=True,check=True);lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)];f=d/'GenericObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'source Generic to product-pattern/field observations')
