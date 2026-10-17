from pathlib import Path
import argparse,subprocess,random,json,re
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();rng=random.Random(6132768);requests=[]
for n in [0,1,7,15,16,17,31,32,33,63,64,65,255,256,1024,4096]:
 for i in range(12):
  data=list(rng.randbytes(n));requests.append(dict(algorithm='MD2',chunks=[data],public=True,read=['','00','ab'*16+'tail','AB'*16,' '+ '00'*16,'z0'*16][i%6],witnessBottom=i%2==0))
  requests.append(dict(algorithm='MD2',chunks=[[],data[:i],[],data[i:],[]],mutable=True,witnessBottom=i%2==0))
run=subprocess.run([a.oracle],input=''.join(json.dumps(r)+'\n'for r in requests),capture_output=True,text=True,check=True);values=[json.loads(x)for x in run.stdout.splitlines()];assert len(values)==len(requests);rows=[dict(request=r,expected=e)for r,e in zip(requests,values)]
samples=[json.loads(x)for x in subprocess.check_output([a.oracle,'--samples'],text=True).splitlines()];assert len(samples)==100;rows+=samples
literals=[json.loads(x)for x in subprocess.check_output([a.oracle,'--literals'],text=True).splitlines()];assert len(literals)==3
cases=[json.loads(x)for x in subprocess.check_output([a.oracle,'--demand'],text=True).splitlines()];assert len(cases)==8
suite=subprocess.check_output([a.oracle,'--suite','--seed=1729','--qc-max-success=100','--no-color'],text=True);suite=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite);assert '4 examples, 0 failures'in suite,suite
for name,content in [('HaskellObservations.json',rows),('LiteralObservations.json',literals),('DemandObservations.json',cases),('OriginalSuite.txt',suite)]:
 data=(json.dumps(content,separators=(',',':'))+'\n')if not isinstance(content,str)else content;f=D/name
 if a.verify:assert f.read_text()==data,name
 else:f.write_text(data)
print('PASS',len(rows),'actualHaskell rows;3literals/8demand;original4examples/1property×100')
