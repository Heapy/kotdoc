from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
variants=[{}]+[dict(fault=x)for x in ['contextSize','digestSize','blockSize','init','update','finalize','asn1']]+[dict(returnedBottom=x)for x in ['init','update','finalize']]+[dict(bottom=x)for x in ['key','size','hash','witness','message']]
for op in ['sign','root','make','encode']:
 for encoding in ['prefix','raw','ignore','empty']:
  for v in variants:requests.append(dict(op=op,encoding=encoding,**v))
  for size in [0,8,11,32,64]:
   for message in [[],[1,2,3]]:requests.append(dict(op=op,encoding=encoding,size=size,message=message))
rows=[]
for i,r in enumerate(requests):
 out=subprocess.run(['/tmp/kotdoc-pkcs15-dictionary-oracle'],input=json.dumps(r)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
 if i%100==0:print('captured',i,flush=True)
blob=json.dumps(rows,separators=(',',':'))+'\n';target=D/'DictionaryObservations.json'
if a.verify:assert target.read_text()==blob
else:target.write_text(blob)
print('PASS',len(rows),'original mechanically exported private/class dictionary observations')
