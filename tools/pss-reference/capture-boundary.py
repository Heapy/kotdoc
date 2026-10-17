from pathlib import Path
import json,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();requests=[]
for bits in list(range(-17,41))+[-9223372036854775808,-9223372036854775807,9223372036854775806,9223372036854775807]:
 requests.append(dict(op='truncate',bits=bits))
 for xs in [[],[0],[255],[255,128,1],list(range(256))]:requests.append(dict(op='normalize',bits=bits,input=xs))
for k in [-9223372036854775808,-9223372036854775807,-1,0,1,21,22,23,2147483647,2147483648,9223372036854775807]:
 for h in [-9223372036854775808,-1,0,1,20,2147483647,2147483648,9223372036854775806,9223372036854775807]:
  for saltBytes in [0,1,20]:
   for modulusBits in [1024,1025]:requests.append(dict(op='guard',length=k,hashLength=h,saltBytes=saltBytes,modulusBits=modulusBits))
for bits in [0,'bottom']:
 for xs in [[],[255]]:
  for bytesMode in ['normal','bottom']:requests.append(dict(op='normalize',bits=bits,input=xs,bytes=bytesMode))
rows=[]
for i,r in enumerate(requests):
 (D/'BoundaryCaptureCursor.json').write_text(json.dumps(dict(index=i,request=r)))
 out=subprocess.run(['/tmp/kotdoc-pss-boundary-oracle'],input=json.dumps(r)+'\n',text=True,capture_output=True,check=True);rows.append(dict(request=r,expected=json.loads(out.stdout)))
blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/'BoundaryObservations.json'
if a.verify:assert f.read_text()==blob
else:f.write_text(blob)
print('PASS',len(rows),'original helpers/signed64 guards/root-only virtual size observations; no giant allocation/hash/RNG')
