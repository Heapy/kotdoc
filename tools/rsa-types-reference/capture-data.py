#!/usr/bin/env python3
import pathlib,json,subprocess,argparse
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent
ops=['shape','gfoldl','gfoldlIgnore','gunfold','gunfoldIgnore','gmapT','gmapTRoot','gmapQl','gmapQr','gmapQ','gmapQi','gmapM','gmapMp','gmapMo','gmapMpFail','gmapMoFail','dataCast1','dataCast2']
qs=[]
for t in ['PublicKey','PrivateKey','KeyPair','Integer']:
 for op in ops:
  for bad in [[],['root'],['query'],['transform'],['constr'],['z'],['root','query'],['root','transform'],['constr','z']]:qs.append(dict(type=t,op=op,bad=bad))
 for index in [-1,0,1,2,3,6,7]:qs.append(dict(type=t,op='gmapQi',index=str(index)))
for value in ['0','-1',str(2**256+17)]:
 for op in ['shape','gunfold','gunfoldIgnore']:
  for bad in [[],['constructorValue'],['z']]:qs.append(dict(type='Integer',op=op,integer=value,constructor='integer',bad=bad))
r=subprocess.run(['/tmp/kotdoc-rsa-types-data-oracle'],input=''.join(json.dumps(q)+'\n'for q in qs),capture_output=True,text=True,check=True)
lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)]
f=d/'DataObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'original Data method/default/domain observations')
