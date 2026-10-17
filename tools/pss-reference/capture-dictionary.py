from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[];ops=['root','sign','signDigest','verify','defaultRoot','defaultSalt'];bottoms=['','params','witness','message','salt','key','size','maskField','trailer','saltLength','digest'];faults=['','digestSize','contextSize','init','update','finalize','mask','maskLength']
for op in ops:
 for mode in ['normal','ignore','ignoreSeed','short','long','empty','valid']:
  for k in [1,7,8,32]:requests.append(dict(op=op,maskMode=mode,size=k))
 for bottom in bottoms:
  for fault in faults:requests.append(dict(op=op,bottom=bottom,fault=fault,maskMode='valid',size=32))
 for label in ['init','update','finalize']:requests.append(dict(op=op,returnedBottom=label,size=32,maskMode='valid'))
 for bottom in ['','params','witness']:
  requests.append(dict(op=op,strictWitness='yes',bottom=bottom,size=32))
rows=[]
for i,r in enumerate(requests):
 k=r['size'];r['signature']=[0]*max(0,k-5)+[26,14,0,0,188]if k>=5 else[188]*k
 (D/'DictionaryCaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run(['/tmp/kotdoc-pss-dictionary-oracle'],input=json.dumps(r)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/'DictionaryObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(rows),'original PSS generic hash/MGF/default fields/effect/demand observations')
