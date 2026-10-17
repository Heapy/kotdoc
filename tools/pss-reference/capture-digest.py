from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for row in json.loads((D/'Observations.json').read_text()):
 r=row['request'];v=row['expected']
 if r['op']!='signDigestWithSalt'or'bytes'not in v:continue
 base=dict(r,op='verifyDigest',signature=v['bytes'],saltLength=len(r['salt']))
 requests.append(dict(base,control='valid-source-digest-signature'))
 wrong=base['digest'].copy();wrong[0]^=1;requests.append(dict(base,digest=wrong,control='different-digest'))
 requests.append(dict(base,signature=[0]+v['bytes'],control='leading-byte'))
 requests.append(dict(base,saltLength=len(r['salt'])+1,control='salt-length'))
out=subprocess.run(['/tmp/kotdoc-pss-haskell-oracle'],input=''.join(json.dumps(r)+'\n'for r in requests),text=True,capture_output=True,check=True);values=[json.loads(x)for x in out.stdout.splitlines()];assert len(values)==len(requests)
assert all(v=={'valid':True}for r,v in zip(requests,values)if r['control']=='valid-source-digest-signature')
blob=json.dumps([dict(request=r,expected=e)for r,e in zip(requests,values)],separators=(',',':'))+'\n';f=D/'DigestObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(requests),'original digest API valid/altered/length controls across18 available hashes')
