#!/usr/bin/env python3
import json,pathlib,subprocess,argparse
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent;qs=[]
for t,fields in [('PublicKey',['size','n','e']),('PrivateKey',['size','n','e','d','p','q','dP','dQ','qinv']),('KeyPair',['size','n','e','d','p','q','dP','dQ','qinv'])]:
 for op in ['eq','ne']:
  qs.append(dict(type=t,op=op))
  for field in fields:
   qs.append(dict(type=t,op=op,right={field:'99'}))
   for later in fields[fields.index(field)+1:]:qs.append(dict(type=t,op=op,bad=[later],right={field:'99'}))
  roots=['publicRoot']if t=='PublicKey'else['privateRoot','pub','publicRoot']if t=='PrivateKey'else['pairRoot','privateRoot','pub','publicRoot']
  for left in roots+fields:
   for right in roots+fields:qs.append(dict(type=t,op=op,bad=[left],errorPrefix='left:',right=dict(bad=[right],errorPrefix='right:')))
r=subprocess.run(['/tmp/kotdoc-rsa-types-oracle'],input=''.join(json.dumps(q)+'\n'for q in qs),capture_output=True,text=True,check=True);lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)];f=d/'EqObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'source Eq and neq field/root order observations')
