#!/usr/bin/env python3
import pathlib,json,subprocess,argparse
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent
qs=[dict(type=t,op='typeInfo',bad=bad)for t in ['PublicKey','PrivateKey','KeyPair','Integer','Int']for bad in [[],['publicRoot','privateRoot','pairRoot']]]
r=subprocess.run(['/tmp/kotdoc-rsa-types-oracle'],input=''.join(json.dumps(q)+'\n'for q in qs),capture_output=True,text=True,check=True);lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)];f=d/'TypeInfoObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'source Typeable identity/root observations')
