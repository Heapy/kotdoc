#!/usr/bin/env python3
import pathlib,json,subprocess,argparse
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent;qs=[];base=json.loads((d/'Observations.json').read_text())
for t in ['PublicKey','PrivateKey','KeyPair']:
 value=next(r['expected']for r in base if r['request']==dict(type=t,op=('show'if t=='PublicKey'else'debug'),bad=[],precedence=0))
 for projection in ['ignore','alternative','value','remainder']:
  for bad in [[],['inputRoot'],['inputTail'],['precedence'],['inputRoot','precedence']]:
   for n in ([0,1,4]if projection=='remainder'else[0]):qs.append(dict(type=t,op='readProjected',input=value,projection=projection,bad=bad,prefixLength=n))
# Bounded source-valid numeric/list/parenthesis stress, not a universal resource claim.
pub=next(r['expected']for r in base if r['request']==dict(type='PublicKey',op='show',bad=[],precedence=0))
for value,op in [(pub.replace('public_n = -2','public_n = '+('9'*4096)),'read'),('('*512+pub+')'*512,'read'),('['+','.join([pub]*128)+'] tail','readList')]:qs.append(dict(type='PublicKey',op=op,input=value))
r=subprocess.run(['/tmp/kotdoc-rsa-types-oracle'],input=''.join(json.dumps(q)+'\n'for q in qs),capture_output=True,text=True,check=True);lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)];f=d/'ProjectedObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'source Read projection/partial-input/bounded-depth observations')
