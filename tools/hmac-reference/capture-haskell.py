from pathlib import Path
import argparse,json,random,subprocess,re
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();rng=random.Random(6134622);requests=[]
algs={'MD5':64,'SHA1':64,'SHA224':64,'SHA256':64,'SHA384':128,'SHA512':128,'SHA512t_224':128,'SHA512t_256':128,'Keccak_224':144,'Keccak_256':136,'Keccak_384':104,'Keccak_512':72,'SHA3_224':144,'SHA3_256':136,'SHA3_384':104,'SHA3_512':72}
for sec in [128,256]:
 for n in [0,1,7,8,224,1344,2048]:algs[f'SHAKE{sec}_{n}']=200-2*(sec//8)
for name,block in algs.items():
 for keylen in [0,1,block-1,block,block+1,2*block+7]:
  for msglen in [0,1,block-1,block,block+1,1024]:
   key=list(rng.randbytes(keylen));msg=list(rng.randbytes(msglen))
   for chunks in [[msg],[[],msg[:1],[],msg[1:],[]]]:requests.append(dict(algorithm=name,key=key,chunks=chunks,other=list(rng.randbytes(msglen))))
for name in algs:
 for read in ['', '00', 'AB'*256+'suffix', ' '+ '00'*256, 'g0'*256]:requests.append(dict(algorithm=name,key=[1,2],chunks=[[3,4]],other=[],publicDigest=True,read=read))
for n in [0,1,2,7,16,32]:
 data=list(rng.randbytes(n))
 for other in [data,[],data+[0],*[data[:i]+[data[i]^1]+data[i+1:]for i in range(n)]]:requests.append(dict(equalOnly=True,key=data,other=other))
for name in ['SHA256','SHA3_256']:
 requests.append(dict(algorithm=name,key=list(rng.randbytes(300)),chunks=[[]if i%3==0 else[i%256]for i in range(10000)],other=[]))
run=subprocess.run([a.oracle],input=''.join(json.dumps(r,separators=(',',':'))+'\n'for r in requests),capture_output=True,text=True,check=True);observations=[json.loads(s)for s in run.stdout.splitlines()];assert len(observations)==len(requests)
rows=[dict(request=r,expected=e)for r,e in zip(requests,observations)]
samples=subprocess.run([a.oracle,'--samples'],capture_output=True,text=True,check=True);samples=[json.loads(s)for s in samples.stdout.splitlines()];assert len(samples)==1400;rows+=samples
suite=subprocess.run([a.oracle,'--suite','--qc-max-success=100','--seed=1729','--no-color'],capture_output=True,text=True,check=True).stdout;suite=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite);assert '42 examples, 0 failures'in suite,suite
literalRun=subprocess.run([a.oracle,'--literals'],capture_output=True,text=True,check=True);literals=[json.loads(s)for s in literalRun.stdout.splitlines()];assert len(literals)==14
for name,data in [('LiteralObservations.json',json.dumps(literals,separators=(',',':'))+'\n'),('HaskellObservations.json' ,json.dumps(rows,separators=(',',':'))+'\n'),('OriginalSuite.txt',suite)]:
 path=D/name
 if a.verify:assert path.read_text()==data,name
 else:path.write_text(data)
print('PASS',len(rows),'original Haskell rows;full42examples/14properties×100')
