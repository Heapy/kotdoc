#!/usr/bin/env python3
import pathlib,json,subprocess,argparse
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent;qs=[]
for op in ['eq','ne']:
 for x in range(5):
  for y in range(5):qs.append(dict(type='Error',op=op,error=x,right=dict(error=y)))
 for lb in [[],['errorRoot']]:
  for rb in [[],['errorRoot']]:qs.append(dict(type='Error',op=op,bad=lb,errorPrefix='left:',right=dict(bad=rb,errorPrefix='right:')))
 for lb in [[],['r'],['inverse']]:
  for rb in [[],['r'],['inverse']]:qs.append(dict(type='Blinder',op=op,bad=lb,errorPrefix='left:',right=dict(bad=rb,errorPrefix='right:')))
 for f in ['r','inverse']:qs.append(dict(type='Blinder',op=op,right={f:'99'}))
for t in ['Blinder','Error']:
 for prec in [0,10,11]:
  for n in [0,1,7,10,30]:
   for bad in [[],['r'],['inverse'],['errorRoot'],['suffix']]:qs.append(dict(type=t,op='showPrefix',precedence=prec,prefixLength=n,bad=bad,r='-2',inverse='-3'))
for selector in ['pairN','pairSize','pairE']:
 for bad in [[],['pairRoot'],['privateRoot'],['pub'],['publicRoot'],['size'],['n'],['e'],['d']]:qs.append(dict(type='KeyPair',op='selector',selector=selector,bad=bad))
r=subprocess.run(['/tmp/kotdoc-rsa-types-oracle'],input=''.join(json.dumps(q)+'\n'for q in qs),capture_output=True,text=True,check=True);lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)];f=d/'MemberObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'source Error/Blinder methods and KeyPair selector observations')
