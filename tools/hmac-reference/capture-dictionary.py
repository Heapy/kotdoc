from pathlib import Path
import argparse,json,subprocess
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
def row(b,d,n,operation,**extra):requests.append(dict(block=b,digest=d,key=list(range(n)),message=[3,4],operation=operation,fault='',**extra))
for b,d in [(0,4),(1,1),(8,4),(8,8),(8,12)]:
 for n in sorted(set([0,max(0,b-1),b,b+1,2*b+3])):
  for op in ['initialize','update','finalize','hmac','ignored']:row(b,d,n,op)
for n in [0,8,9]:
 for op in ['initialize','update','finalize','hmac']:
  for fault in ['init1','init2','init3','update','finalize','blockSize','digestSize','contextSize']:
   row(8,4,n,op);requests[-1]['fault']=fault
for op in ['ignored','initialize','hmac']:
 for keyBottom,messageBottom in [(True,True),(False,True),(True,False)]:
  row(8,4,1,op,keyBottom=keyBottom,messageBottom=messageBottom)
for op in ['outerBottom','innerBottom','equalBottom']:row(8,4,1,op)
rows=[]
for i,r in enumerate(requests):
 result=subprocess.run([a.oracle],input=json.dumps(r),capture_output=True,text=True,check=True);rows.append(dict(request=r,expected=json.loads(result.stdout)))
 if i%100==0:print('captured',i,flush=True)
blob=json.dumps(rows,separators=(',',':'))+'\n';path=D/'DictionaryObservations.json'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(rows),'actual original custom HashAlgorithm dictionary probes')
