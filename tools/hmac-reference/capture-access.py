from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for n in [0,7,8,9,19]:
 for op in ['ignored','initialize','update','finalize','hmac','updatesAccess','hmacLazyAccess']:
  for fault in ['', 'key-length','key-before','key-after','message-length','message-before','message-after']:
   requests.append(dict(block=8,digest=4,key=list(range(n)),message=[3,4],operation=op,fault='',accessFault=fault,repeat=2))
 for twiceKey,twiceMessage in [(True,False),(False,True),(True,True)]:requests.append(dict(block=8,digest=4,key=list(range(n)),message=[3,4],operation='hmac',fault='',accessFault='',twiceKey=twiceKey,twiceMessage=twiceMessage,repeat=2))
for n in [0,7,8,9,19]:
 for operation in ['initialize','update','hmac']:
  for bottomKeyResult,bottomMessageResult in [(True,False),(False,True),(True,True)]:requests.append(dict(block=8,digest=4,key=list(range(n)),message=[3,4],operation=operation,fault='',accessFault='',bottomKeyResult=bottomKeyResult,bottomMessageResult=bottomMessageResult,repeat=2))
rows=[]
for i,r in enumerate(requests):
 run=subprocess.run([a.oracle],input=json.dumps(r),capture_output=True,text=True,check=True);rows.append(dict(request=r,expected=json.loads(run.stdout)))
 if i%100==0:print('captured',i,flush=True)
blob=json.dumps(rows,separators=(',',':'))+'\n';path=D/'AccessObservations.json'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(rows),'original ByteArrayAccess callback/metadata probes')
