from pathlib import Path
import random,json,subprocess,argparse,re
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',default='/tmp/kotdoc-pkcs15-haskell-oracle');p.add_argument('--verify',action='store_true');a=p.parse_args();r=random.Random(6132770);requests=[]
algorithms=list(json.loads((D/'Prefixes.json').read_text()))
for alg in algorithms:
 for size in [0,1,10,11,20,32,45,46,64,128,256]:
  for n in [0,1,7,20,63,128]:
   m=list(r.randbytes(n));requests +=[dict(op=op,algorithm=alg,size=size,message=m,seed=n)for op in ['padSignature','sign','pad','encrypt']]
   requests.append(dict(op='verify',algorithm=alg,size=size,message=m,signature=list(r.randbytes(size))))
for k in [0,1,2,10,11,12,16,64,128]:
 for n in [0,1,7,8,9,20,40]:
  for message in [[],[1],list(r.randbytes(17))]:
   block=[0,2]+[255]*n+[0]+message
   requests +=[dict(op='unpad',input=block),dict(op='decrypt',size=len(block),cipher=block),dict(op='decryptSafer',size=len(block),cipher=block,seed=k)]
for n in range(130):requests.append(dict(op='unpad',input=list(r.randbytes(n))))
for i in range(30):
 requests.append(dict(op='signSafer',algorithm=algorithms[i%14],size=128,message=list(r.randbytes(i)),seed=i))
rows=[]
for i,req in enumerate(requests):
 (D/'CaptureCursor.json').write_text(json.dumps(dict(index=i,request=req)))
 out=subprocess.run([a.oracle],input=json.dumps(req)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=req,expected=json.loads(out.stdout)))
 if i%500==0:print('captured',i,flush=True)
data=json.dumps(rows,separators=(',',':'))+'\n';f=D/'Observations.json'
if a.verify:assert f.read_text()==data
else:f.write_text(data)
suite=subprocess.check_output([a.oracle,'--suite','--seed=1729','--qc-max-success=100','--no-color'],text=True);suite=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite);assert '37 examples, 0 failures'in suite,suite
if a.verify:assert(D/'OriginalSuite.txt').read_text()==suite
else:(D/'OriginalSuite.txt').write_text(suite)
print('PASS',len(rows),'ordinary original PKCS15 observations; full original RSA37 examples support31PKCS15+6raw')
