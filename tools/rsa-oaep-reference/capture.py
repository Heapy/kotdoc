from pathlib import Path
import json,subprocess,random,argparse,re
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify',action='store_true');a=p.parse_args();rng=random.Random(6132764)
v=json.loads(subprocess.check_output([a.oracle,'--vectors']));requests=[]
algs=['MD5','SHA1','SHA224','SHA256','SHA384','SHA512','SHA512t_224','SHA512t_256','SHA3_224','SHA3_256','SHA3_384','SHA3_512','Keccak_224','Keccak_256','Keccak_384','Keccak_512'];sizes=[16,20,28,32,48,64,28,32,28,32,48,64,28,32,48,64]
for alg,h in zip(algs,sizes):
 for seedlen in [0,1,h-1,h,h+1,300]:
  for n in [-33,-1,0,1,h-1,h,h+1,h*3,1025]:requests.append(dict(op='mgf',algorithm=alg,seed=list(rng.randbytes(seedlen)),length=n))
 for k in [2*h+1,2*h+2,2*h+3,128,256]:
  for ml in [0,1,max(0,k-2*h-2),max(0,k-2*h-1)]:
   for sl in [h-1,h,h+1]:
    for mask in ['default','empty','short','long','zero','error']:
     requests.append(dict(op='encrypt',algorithm=alg,size=k,n=str(2**(8*k)-1),e='1',seed=list(rng.randbytes(sl)),message=list(rng.randbytes(ml)),mask=mask,labelMode='some',label=list(rng.randbytes(7))))
for x in v:
 key=x['key']|x['private'];requests.append(dict(op='encrypt',**key,seed=x['seed'],message=x['message']));requests.append(dict(op='decrypt',**key,cipher=x['cipher']))
 for pos in [0,3,60,127]:
  # Mutate encoded OAEP through actual RSA public primitive below in helper subprocess? Direct ciphertext mutation also exercises reject paths.
  c=x['cipher'].copy();c[pos]^=1;requests.append(dict(op='decrypt',**key,cipher=c))
 for c in [[],x['cipher'][1:],x['cipher']+[0],list(int(key['n']).to_bytes(key['size'],'big'))]:requests.append(dict(op='decrypt',**key,cipher=c))
for alg,h in zip(algs,sizes):
 for k in [0,1,2*h+1,2*h+2,128]:
  for mask in ['default','empty','short','long','zero','error']:
   for variant in range(3):
    c=list(rng.randbytes(k));requests.append(dict(op='decryptEncoded',algorithm=alg,size=k,cipher=c,mask=mask,labelMode='some',label=list(rng.randbytes(variant))))
sourceCases=json.loads(subprocess.check_output([a.oracle,'--source-cases']));assert len(sourceCases)==23;requests+=sourceCases
run=subprocess.run([a.oracle],input=''.join(json.dumps(r)+'\n'for r in requests),capture_output=True,text=True,check=True);expected=[json.loads(s)for s in run.stdout.splitlines()];assert len(expected)==len(requests)
suite=subprocess.check_output([a.oracle,'--suite'],text=True);suite=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite);assert '23 examples, 0 failures'in suite,suite
for n,data in [('Vectors.json',json.dumps(v,separators=(',',':'))+'\n'),('Observations.json',json.dumps([dict(request=r,expected=e)for r,e in zip(requests,expected)],separators=(',',':'))+'\n'),('OriginalSuite.txt',suite)]:
 f=D/n
 if a.verify:assert f.read_text()==data,n
 else:f.write_text(data)
print('PASS',len(requests),'original rows;7literal vectors;23original examples')
