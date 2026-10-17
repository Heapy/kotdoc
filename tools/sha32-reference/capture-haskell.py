#!/usr/bin/env python3
from pathlib import Path
import argparse,json,random,subprocess,re,hashlib
D=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args()
rng=random.Random(6131224256);requests=[]
for alg,size in [('SHA1',20),('SHA224',28),('SHA256',32)]:
 for n in [0,1,2,7,63,64,111,112,113,127,128,129,255,256,257,2901,4096]:
  data=list(rng.randbytes(n))
  for chunks in [[data],[[],data,[]],[data[:1],[],data[1:]]]:requests.append(dict(digest=True,algorithm=alg,chunks=chunks))
  for text in ['', '00'*(size-1),'00'*size,'AB'*size+'suffix',' '+ '00'*size,'g0'*size,'0x'+'00'*size,'00'*size+' extra']:
   requests.append(dict(digest=True,public=True,witnessBottom=True,algorithm=alg,chunks=[data],read=text))
  if alg in ['SHA1','SHA224','SHA256']:
   for initial in [[],list(rng.randbytes(127))]:
    for count in [-2147483648,-1,0,1,max(0,n-1),n,n+1,2147483647]:requests.append(dict(digest=True,algorithm=alg,chunks=[initial],end=data,prefix=count))
for alg in ['SHA1','SHA224','SHA256']:
 for n in [0,1,55,56,63,64,65,127,128,129,2901]:
  data=list(rng.randbytes(n));requests.append(dict(mutable=True,witnessBottom=True,algorithm=alg,chunks=[[],data[:1],data[1:],[]]))
blob=''.join(json.dumps(r,separators=(',',':'))+'\n'for r in requests)
run=subprocess.run([a.oracle],input=blob,text=True,capture_output=True,check=True);outputs=[json.loads(x)for x in run.stdout.splitlines()];assert len(outputs)==len(requests)
rows=[dict(request=r,expected=e)for r,e in zip(requests,outputs)]
samples=subprocess.run([a.oracle,'--samples'],text=True,capture_output=True,check=True);sampleRows=[json.loads(x)for x in samples.stdout.splitlines()];assert len(sampleRows)==900;rows+=sampleRows
suite=subprocess.run([a.oracle,'--suite','--qc-max-success=100','--seed=1729','--no-color'],text=True,capture_output=True,check=True).stdout;suite=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite);assert '27 examples, 0 failures'in suite,suite
literals=subprocess.run([a.oracle,'--literals'],text=True,capture_output=True,check=True);literalRows=[json.loads(x)for x in literals.stdout.splitlines()];assert len(literalRows)==18
demand=subprocess.run([a.oracle,'--demand'],text=True,capture_output=True,check=True);demandRows=[json.loads(x)for x in demand.stdout.splitlines()];assert len(demandRows)==36
for name,data in [('LiteralObservations.json',json.dumps(literalRows,separators=(',',':'))+'\n'),('DemandObservations.json',json.dumps(demandRows,separators=(',',':'))+'\n'),('HaskellObservations.json' ,json.dumps(rows,separators=(',',':'))+'\n'),('OriginalSuite.txt',suite)]:
 path=D/name
 if a.verify:assert path.read_text()==data,name
 else:path.write_text(data)
print('PASS',len(rows),'actual Haskell rows including900 actual generator samples;27 original examples with9 properties x100')
