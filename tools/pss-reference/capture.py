from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();algorithms={'MD2':16,'MD5':16,'SHA1':20,'SHA224':28,'SHA256':32,'SHA384':48,'SHA512':64,'SHA512t_224':28,'SHA512t_256':32,'SHA3_224':28,'SHA3_256':32,'SHA3_384':48,'SHA3_512':64,'RIPEMD160':20,'Keccak_224':28,'Keccak_256':32,'Keccak_384':48,'Keccak_512':64};requests=[]
for name,h in algorithms.items():
 for k in [-1,0,1,10,h+1,h+2,h+3,64,128]:
  for salt in [[],[7],list(range(20))]:
   for op in ['signWithSalt','signDigestWithSalt']:requests.append(dict(op=op,algorithm=name,size=k,salt=salt,saltLength=-99,message=[1,2,3],digest=[42]*h))
 for op in ['sign','signDigest','signSafer','signDigestSafer']:
  for k in [16,32,64,128]:
   for saltLength in [0,1,h]:requests.append(dict(op=op,algorithm=name,size=k,saltLength=saltLength,message=[1,2,3],digest=[42]*h,seed=17))
 for op in ['verify','verifyDigest']:
  for k in [0,1,32,64]:
   for signature in [[],[0],[255]*max(0,k),[0]*max(0,k),[1]*max(0,k)]:requests.append(dict(op=op,algorithm=name,size=k,signature=signature,message=[1,2,3],digest=[42]*h))
# Defined pure ByteString exceptions for empty encodings, not native pointer access.
for k,n in [(0,1),(1,1),(1,257),(2,257)]:
 for op in ['verify','verifyDigest']:requests.append(dict(op=op,size=k,n=str(n),signature=[0]*k,message=[],digest=[0]*20))
rows=[]
for start in range(0,len(requests),100):
 batch=requests[start:start+100];(D/'CaptureCursor.json').write_text(json.dumps(dict(index=start,request=batch[0])))
 out=subprocess.run(['/tmp/kotdoc-pss-haskell-oracle'],input=''.join(json.dumps(r)+'\n'for r in batch),text=True,capture_output=True,check=True);values=[json.loads(v)for v in out.stdout.splitlines()];assert len(values)==len(batch)
 rows.extend(dict(request=r,expected=v)for r,v in zip(batch,values))
blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/'Observations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(rows),'original PSS ordinary cases/all18 available hash dictionaries/8 operations')
