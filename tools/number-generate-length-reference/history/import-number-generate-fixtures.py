#!/usr/bin/env python3
"""Actual pinned Haskell RNG traces/results; never Kotlin-derived expected values."""
import argparse,gzip,hashlib,json,random,re,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/number-generate-reference';TARGET=R/'core/src/test/resources/crypto-number-generate/data.jsonl.gz'
def inputs():
 rows=[]
 def add(group,**request):rows.append(dict(group=group,request=request))
 for top in ['highest','two']:
  for other in ['highest','two']:
   for precedence in [-1,0,10,11,100]:add('GenTopPolicy-source-Show-Eq',op='policy',top=top,other=other,precedence=precedence)
 for bits in list(range(-2,18))+[31,32,33,63,64,65,127,128,129,255,256,257,511,512,513,1023,1024,1025,2047,2048,2049,2901,4096]:
  values=range(256)if 1<=bits<=17 else[0,1,127,128,254,255]
  for byte in values:
   for top in ['none','highest','two']:
    for odd in [False,True]:add('parameter-byte-mask-boundaries',op='params',bits=bits,top=top,odd=odd,byte=byte,logicalOutOfBoundsStore=bits==1 and top=='two')
   add('prefix-most-significant-bits',op='prefix',bits=bits,byte=byte)
 ranges=list(range(-2,131))+[(1<<k)+d for k in [7,8,9,15,16,31,32,63,64,127,128,255,256,511,512,1024]for d in [-1,0,1,(1<<(k-3))-1,1<<(k-3),(1<<(k-2))-1,1<<(k-2)]]
 for n in sorted(set(ranges)):
  for byte in [0,1,63,127,128,159,160,191,192,223,254,255]:add('simple-normal-over-range-boundaries',op='max',range=str(n),byte=byte)
 for n in [127,128,129,159]:
  bits=n.bit_length();over=bits>3 and not(n&(1<<(bits-2)))and not(n&(1<<(bits-3)));size=(bits+int(over)+7)//8
  for candidate in range(1<<(bits+int(over))):add('exhaustive-small-candidate-branch',op='max',range=str(n),chunks=[list(candidate.to_bytes(size,'big')),[0]*size])
 for n,invalid,valid in [(127,[127],[126]),(128,[1,255],[0,127]),(159,[1,255],[0,158])]:
  for rejected in [0,1,2,98,99,100]:add('exact100-attempt-retry-limit',op='max',range=str(n),chunks=[invalid]*rejected+[valid],exhaust=True)
 for low,high in [(-10,-20),(-10,-10),(-10,-9),(-1,0),(0,-1),(0,0),(0,1),(1,0),(100,100),(100,226),(0,126),(0,127),(0,128),(-(1<<100),-(1<<100)+159),(1<<512,-(1<<512))]:
  for byte in [0,1,127,255]:add('inclusive-unsorted-between',op='between',low=str(low),high=str(high),byte=byte)
 for bits in [-(1<<31),-1000000000]+list(range((1<<31)-9,1<<31)):
  for op in ['params','prefix']:add('virtual-Int-boundary-request-only',op=op,bits=bits,top='bottom',oddBottom=True,throw=True)
 for op in ['params','prefix','max','between']:
  for at in [0,1,99]:add('RNG-provider-failure',op=op,bits=129,range='127',low='10',high='136',byte=127,throwAt=at)
 for bits in [-1,0,1,9]:
  for top in ['none','highest','two','bottom']:
   for oddBottom in [False,True]:
    for throw in [False,True]:add('parameter-demand-and-effect-order',op='params',bits=bits,top=top,oddBottom=oddBottom,throw=throw,byte=255)
 for op in ['params','prefix']:
  add('bits-bottom-before-RNG',op=op,bitsBottom=True,top='bottom',oddBottom=True,throw=True)
 for mask in range(4):add('between-bound-demand',op='between',low='1',high='10',lowBottom=bool(mask&1),highBottom=bool(mask&2),throw=True)
 add('range-bottom-before-RNG',op='max',rangeBottom=True,throw=True)
 return rows

def pins():
 subprocess.run([sys.executable,str(R/'tools/extract-number-generate-reference.py'),'--check'],check=True)
 paths=[p for p in D.rglob('*')if p.is_file()and'work-in-progress'not in str(p)and'__pycache__'not in str(p)]+[R/'tools/extract-number-generate-reference.py',Path(__file__).resolve(),R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt',R/'vendor/crypton-2.0.1/SOURCE.json',R/'vendor/ram-0.22.1/SOURCE.json']
 return{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(paths)}
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--verify-oracle',action='store_true');p.add_argument('--check-source',action='store_true');a=p.parse_args();rows=inputs()
 if a.check_source or a.verify_oracle:
  with gzip.open(TARGET,'rt')as f:old=[json.loads(line)for line in f]
  assert old[0]['sources']==pins()
 if not a.check_source:
  run=subprocess.run([a.oracle],input=''.join(json.dumps(row['request'])+'\n'for row in rows),capture_output=True,text=True,check=True);values=[json.loads(line)for line in run.stdout.splitlines()];assert len(values)==len(rows)
  for row,value in zip(rows,values):row['expected']=value
  samples=subprocess.run([a.oracle,'--original-samples'],capture_output=True,text=True,check=True);generated=[json.loads(line)for line in samples.stdout.splitlines()];assert len(generated)==800
  for row in generated:row['request']['oracleRandomBytes']=row['expected']['payloads']
  rows+=generated
  probes=subprocess.run([a.oracle,'--guard-probes'],capture_output=True,text=True,check=True);guards=[json.loads(line)for line in probes.stdout.splitlines()];assert len(guards)==120
  for row in guards:
   changed=row['bits']==1 and row['top']=='two';assert row['after'][0]==17;assert row['after'][-1]==(162 if changed else 34)
  guardPath=D/'GuardObservations.json'
  if a.verify_oracle:assert json.loads(guardPath.read_text())==guards
  else:guardPath.write_text(json.dumps(guards,separators=(',',':'))+'\n')
  suite=subprocess.run([a.oracle,'--original','--seed','1729','--qc-max-success','200'],capture_output=True,text=True,check=True);assert '4 examples, 0 failures'in suite.stdout,suite.stdout;baseline=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite.stdout)
  if a.verify_oracle:assert old[1:]==rows;assert(D/'OriginalSuite.txt').read_text()==baseline
  else:(D/'OriginalSuite.txt').write_text(baseline)
 if a.check_source:assert [row['request']for row in old[1:len(rows)+1]]==[row['request']for row in rows];rows=old[1:]
 if not(a.check_source or a.verify_oracle):
  TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_bytes(gzip.compress(('\n'.join(json.dumps(row,separators=(',',':'))for row in[dict(sources=pins(),cases=len(rows),originalFamilies=4,originalSamples=800),*rows])+'\n').encode(),mtime=0))
 print('PASS',len(rows),'original Number.Generate observations',len(pins()),'pins;4families x200')
if __name__=='__main__':main()
