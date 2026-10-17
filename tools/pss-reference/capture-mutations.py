from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args()
def observe(requests):
 out=subprocess.run(['/tmp/kotdoc-pss-haskell-oracle'],input=''.join(json.dumps(r)+'\n'for r in requests),text=True,capture_output=True,check=True);v=[json.loads(x)for x in out.stdout.splitlines()];assert len(v)==len(requests);return v
base=[]
for bits in [175,176,177,178,183,184,185,191,192,193,255,256,257,511,512,513]:
 for saltLen in [0,1,20]:
  for trailer in [0,188,255]:base.append(dict(op='signWithSalt',size=(bits+7)//8,n=str(2**bits-1),salt=[7]*saltLen,saltLength=-99,trailer=trailer,message=[1,2,3]))
values=observe(base);requests=[]
for req,value in zip(base,values):
 if 'bytes'not in value:continue
 s=value['bytes'];k=req['size'];n=int(req['n']);baseVerify={**req,'op':'verify','saltLength':len(req['salt']),'signature':s}
 variants=[('itself',s),('empty',[]),('leading',[0]+s),('trailing',s+[0]),('short',s[:-1]),('zero',[0]*k),('ff',[255]*k),('equalN',list(n.to_bytes(k,'big')))]
 for index,mask in [(0,1),(0,2),(0,128),(len(s)-1,1),(max(0,len(s)-5),1)]:
  x=s.copy();x[index]^=mask;variants.append((f'flip{index}/{mask}',x))
 if int.from_bytes(bytes(s),'big')+n<2**(8*k):variants.append(('plusN',list((int.from_bytes(bytes(s),'big')+n).to_bytes(k,'big'))))
 for label,x in variants:requests.append(dict(baseVerify,signature=x,mutation=label))
 for saltLength in [-1,0,1,20,32]:requests.append(dict(baseVerify,saltLength=saltLength,mutation='saltLength'))
 for trailer in [0,188,255]:requests.append(dict(baseVerify,trailer=trailer,mutation='trailer'))
rows=[dict(request=r,expected=e)for r,e in zip(base,values)];v=observe(requests);rows +=[dict(request=r,expected=e)for r,e in zip(requests,v)]
blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/'MutationObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(rows),'original bit-profile/trailer/salt/range/malformed-signature observations')
