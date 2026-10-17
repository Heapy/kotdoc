#!/usr/bin/env python3
"""Original RSA core expectations, no Kotlin imports or derived expected outputs."""
from pathlib import Path
import subprocess,json,gzip,itertools
D=Path(__file__).resolve().parent;R=D.parents[1];exe=Path('/tmp/kotdoc-rsa-core-reference')
def execute(requests):
 p=subprocess.run([str(exe)],input='\n'.join(json.dumps(r,separators=(',',':'))for r in requests)+'\n',text=True,capture_output=True,check=True)
 out=[json.loads(x)for x in p.stdout.splitlines()];assert len(out)==len(requests),(len(out),len(requests),p.stderr);return out
vectors=execute([dict(op='vector',index=i)for i in range(3)])
(D/'VectorInputs.json').write_text(json.dumps([v['value']for v in vectors],indent=2)+'\n')
req=[]
def add(op,**args):req.append(dict(op=op,**args))
for v in vectors:
 k=v['value']['private'];args={a:k[a]for a in ['d','p','q','dP','dQ','qinv']};args.update(k['public'])
 add('with',p=args['p'],q=args['q'],size=args['size'],e=args['e'])
 for message in [[],[0],[1],[7]*32,[255]*257]:
  for op in ['ep','dp','slow','fastNo']:
   add(op,**args,input=message)
  add('dp',**args,input=message,blinder='some',r='2',rinv=str(pow(2,-1,int(args['n']))))
primePairs=[(11,13),(61,53),(10007,10009),(1000003,1000033),(0xfffffffffffffffffffffffffffffffeffffffffffffffff,0xffffffff00000001000000000000000000000000ffffffffffffffffffffffff+4294967295)]
for p,q in primePairs:
 for e in [3,5,17,257,65537,9,15,2]:add('with',p=str(p),q=str(q),size='64',e=str(e),part='d')
for p,q,e in itertools.product([-3,0,1,2,3,4,9,11,61],[-3,0,1,2,3,9,13,53],[-3,0,1,2,3,9,17]):
 for part in ['constructor','all']:add('with',p=str(p),q=str(q),e=str(e),part=part)
for n,e,message,size in itertools.product([-5,0,1,2,15,3233],[-3,0,1,2,17],[[],[0],[1],[0,65],[255]*5],[-1,0,1,2,3]):add('ep',n=str(n),e=str(e),input=message,size=str(size))
for p,q,blinder,op in itertools.product([-3,0,1,2,61],[-3,0,1,2,53],['none','some'],['dp','slow','fastNo','fast']):
 if op=='fast'and blinder=='none':continue
 add(op,p=str(p),q=str(q),blinder=blinder,input=[10,230])
for a,b,m in itertools.product([-15,-1,0,1,15],[-15,-1,0,1,15],[-5,-1,0,1,5]):add('multiply',a=str(a),b=str(b),m=str(m))
for size,e,seed in itertools.product([-1,0,1,2,3,4,8,16],[0,1,2,3,9,17,65537],[0,1,255]):add('generate',size=str(size),e=str(e),seed=seed,budget=40)
for n,seed,part in itertools.product([-3,0,1,2,3,4,15,3233,65537],[0,1,2,127,255],['all','state']):add('blinder',n=str(n),seed=seed,part=part)
# Demand/error ordering; the fields are lazy in the pinned key records.
for op,field in itertools.product(['ep','dp','slow','fastNo','fast'],['size','n','e','d','p','q','dP','dQ','qinv']):
 add(op,**{field:'bottom:'+field},input=[10,230],blinder='some')
for op in ['ep','dp','slow','fastNo','fast']:add(op,inputBottom='input',blinder='some')
for p in ['0','61']:add('dp',p=p,q='bottom:q',blinder='bottom',input=[10,230])
for e,field,part in itertools.product(['0','1','17'],['p','q','size'],['constructor','public','all']):add('with',e=e,**{field:'bottom:'+field},part=part)
result=execute(req)
out=R/'core/src/test/resources/crypto-rsa-core';out.mkdir(parents=True,exist_ok=True)
with gzip.GzipFile(filename=str(out/'original.jsonl.gz'),mode='wb',mtime=0)as f:
 for request,expected in zip(req,result):f.write((json.dumps(dict(request=request,expected=expected),separators=(',',':'))+'\n').encode())
print('Captured',len(req),'independent original/controlled RSA core rows')
