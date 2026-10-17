from pathlib import Path
import argparse,json,subprocess,hashlib
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();oracle='/tmp/kotdoc-ed448-haskell-build/build/aarch64-osx/ghc-9.12.2/kotdoc-number-generate-reference-0.1.0.0/x/number-generate-reference/opt/build/number-generate-reference/number-generate-reference'
from Domain import domain
requests=[];excluded=[]
for bits in [-1,0,1,2,7,8,9,15,16,17,31,32,63,64,65,127,128,129,2147483647]:
 requested=max(0,(bits+7)//8)
 lengths=sorted(set([0,1,2,3]+([max(0,requested-1),requested,requested+1]if requested<32 else[])))
 for n in lengths:
  for top in ['none','highest','two','bottom']:
   for odd,oddBottom in [(False,False),(True,False),(False,True)]:
    r=dict(op='params',bits=bits,top=top,odd=odd,oddBottom=oddBottom,chunks=[[7]*n]);d=domain(r)
    (requests if d['defined']else excluded).append(dict(request=r,domain=d))
  for op in ['prefix']:
   r=dict(op=op,bits=bits,chunks=[[7]*n]);requests.append(dict(request=r,domain=domain(r)))
for op in ['max','between']:
 for value in [-1,0,1,2,126,127,128,129,159,255,256,257,2**80-1]:
  for n in [1,2,3,9,11]:
   r=dict(op=op,range=str(value),low='3',high=str(value+2),chunks=[[7]*n]);requests.append(dict(request=r,domain=dict(defined=True,actual=n,access=[0],reason='every positive-bit params call has topNothing/oddFalse and nonempty provider storage; nonpositive range performs no access')))
# All requests are pre-classified; the original executable is never called for excluded bounds.
assert all(x['domain']['defined']for x in requests)
run=subprocess.run([oracle],input=''.join(json.dumps(x['request'])+'\n'for x in requests),capture_output=True,text=True,check=True);values=[json.loads(x)for x in run.stdout.splitlines()];assert len(values)==len(requests)
for row,value in zip(requests,values):row['expected']=value
for name,rows in [('Observations.json',requests),('ExcludedBounds.json',excluded)]:
 blob=json.dumps(rows,separators=(',',':'))+'\n';f=D/name
 if a.verify:assert f.read_text()==blob
 else:f.write_text(blob)
print('PASS',len(requests),'original defined-bound observations;',len(excluded),'bounds excluded BEFORE original execution; four exports')
