#!/usr/bin/env python3
"""Pinned original Haskell public API expectations; no Kotlin input to oracle."""
import argparse,gzip,hashlib,json,random,re,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/ed448-public-reference';TARGET=R/'core/src/test/resources/crypto-ed448-public/data.jsonl.gz'
def inputs():
 rows=[];rng=random.Random(44813613)
 def add(group,op,**kw):rows.append(dict(group=group,request=dict(op=op,**kw)))
 add('source-size-constants','sizes')
 for n in [0,1,31,32,55,56,57,58,111,112,113,114,115,255,256]:
  for op in ['secretKey','publicKey','signature']:add('exact-source-errors',op,bytes=[i%256 for i in range(n)])
 data=[[b]*57 for b in range(256)]+[list(rng.randbytes(57))for _ in range(32)]
 for i,b in enumerate(data):
  for op in ['secretKey','publicKey','debugShow','secretForce','publicForce']:add('all-raw-byte-values',op,bytes=b)
  for prec in [-1,0,10,11,100]:
   for op in ['secretShow','publicShow']:add('original-RAM-show',op,bytes=b,precedence=prec)
  for op in ['secretEq','publicEq']:
   for other in [b,data[(i+1)%len(data)]]:add('original-Eq',op,bytes=b,other=other)
  add('RNG-one57byte-request-no-clamp','generate',bytes=b)
 wide=[[b]*114 for b in range(256)]+[list(rng.randbytes(114))for _ in range(32)]
 for i,b in enumerate(wide):
  for op in ['signature','signatureForce']:add('arbitrary-signature-bytes',op,bytes=b)
  for prec in [-1,0,10,11,100]:add('signature-RAM-show','signatureShow',bytes=b,precedence=prec)
  for other in [b,wide[(i+1)%len(wide)]]:add('signature-Eq','signatureEq',bytes=b,other=other)
  add('actual-original-copy-before-mutable-source-change','ownership',bytes=b)
 add('RNG-exception','generateThrow')
 for i,b in enumerate(data[-32:]+data[:16]):
  message=list(rng.randbytes([0,1,56,57,64,113,114,135,136,137,255,256,1024,4096][i%14]))
  add('source-public-derivation','toPublic',bytes=b)
  for point in [[0]*57,[255]*57,data[(i+8)%len(data)]]:add('safe-sign-rederives-unsafe-uses-supplied','signVerify',bytes=b,point=point,message=message)
 text=(R/'vendor/crypton-2.0.1/tests/Ed448Spec.hs').read_text();vectors=[]
 for part in text.split('vecSec =')[1:]:
  vals=[]
  for name in ['','vecPub','vecMsg','vecSig']:
   segment=part if not name else part.split(name+' =',1)[1];value=re.search(r'"([^"\n]*)"',segment).group(1);vals.append([int(v,16)for v in re.findall(r'\\x([0-9a-f]{2})',value)])
  vectors.append(dict(zip(['bytes','point','message','signature'],vals)))
 assert len(vectors)==8
 for i,v in enumerate(vectors):
  for op in ['toPublic','sign','unsafeSign','verify']:add('original-Ed448Spec-literal',op,**v,kat=i,katExpected=v['point']if op=='toPublic'else True if op=='verify'else v['signature'])
  for target in ['point','signature','message']:
   if not v[target]:continue
   for index in sorted(set([0,len(v[target])//2,len(v[target])-1])):
    changed={**v,target:v[target].copy()};changed[target][index]^=1;add('tamper-no-extra-validation','verify',**changed)
 v=vectors[0]
 for op in ['secretBottomShow','publicBottomShow','signatureBottomShow','secretBottomForce','publicBottomForce','signatureBottomForce']:
  for prec in [-1,0,10,11,100]:add('explicit-bottom-demand',op,precedence=prec)
 for op in ['signDemand','unsafeSignDemand','verifyDemand']:
  for mask in range(16):
   flags={'bottom_'+name:bool(mask&(1<<i))for i,name in enumerate(['secret','public','message','signature'])};add('source-demand-order',op,**v,**flags)
 return rows

def pins():
 subprocess.run([sys.executable,str(R/'tools/extract-ed448-public-reference.py'),'--check'],check=True)
 paths=[p for p in D.rglob('*')if p.is_file()and'work-in-progress'not in str(p)and'__pycache__'not in str(p)]+[R/'tools/extract-ed448-public-reference.py',Path(__file__).resolve(),R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt',R/'licenses/decaf-MIT.txt',R/'licenses/crypton-sha3-BSD-2-Clause.txt',R/'vendor/crypton-2.0.1/SOURCE.json',R/'vendor/ram-0.22.1/SOURCE.json']
 return{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(paths)}
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--verify-oracle',action='store_true');p.add_argument('--check-source',action='store_true');a=p.parse_args();rows=inputs()
 if a.verify_oracle or a.check_source:
  with gzip.open(TARGET,'rt')as f:old=[json.loads(line)for line in f]
  assert old[0]['sources']==pins();assert [r['request']for r in old[1:]]==[r['request']for r in rows]
 if not a.check_source:
  run=subprocess.run([a.oracle],input=''.join(json.dumps(r['request'])+'\n'for r in rows),text=True,capture_output=True,check=True);values=[json.loads(line)for line in run.stdout.splitlines()];assert len(rows)==len(values)
  for row,value in zip(rows,values):
   row['expected']=value;r=row['request']
   if 'katExpected'in r:assert(r['katExpected']==value if r['op']=='verify'else r['katExpected']==value['bytes']),r
  suite=subprocess.run([a.oracle,'--original','--seed','1729','--qc-max-success','100'],capture_output=True,text=True,check=True);assert '25 examples, 0 failures'in suite.stdout;baseline=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite.stdout)
  if a.verify_oracle:assert old[1:]==rows;assert(D/'OriginalSuite.txt').read_text()==baseline
  else:(D/'OriginalSuite.txt').write_text(baseline)
 if not(a.verify_oracle or a.check_source):
  payload=[dict(sources=pins(),cases=len(rows),originalExamples=25,literalVectors=8,scope='3types3sizes8exports;purepubliconly;no modernEd448 instance'),*rows];TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_bytes(gzip.compress(('\n'.join(json.dumps(r,separators=(',',':'))for r in payload)+'\n').encode(),mtime=0))
 print('PASS',len(rows),'independent publicEd448 observations',len(pins()),'pins; original25')
if __name__=='__main__':main()
