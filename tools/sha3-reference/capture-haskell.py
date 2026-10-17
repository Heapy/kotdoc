#!/usr/bin/env python3
import argparse,json,random,re,subprocess
from pathlib import Path
D=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--check',action='store_true');a=p.parse_args();rng=random.Random(613448256);requests=[]
for security in [128,256]:
 for pad in [31,4]:
  for length in [0,1,7,135,136,137,167,168,169,1024]:
   data=list(rng.randbytes(length))
   for bits in sorted(set(list(range(18))+[31,32,33,63,64,65,127,128,129,255,256,257,511,512,513,1024]+list(range(4088,4097)))):
    for chunks in [[data],[[],data,[]],[data[:1],data[1:]]]:requests.append(dict(digest=True,securityBits=security,outputBits=bits,pad=pad,chunks=chunks))
run=subprocess.run([a.oracle],input=''.join(json.dumps(v)+'\n'for v in requests),capture_output=True,text=True,check=True);outputs=[json.loads(line)for line in run.stdout.splitlines()];assert len(outputs)==len(requests)
artifact=dict(kind='actual pinned Haskell SHAKE/HashSHAKE bit-truncation and cSHAKE finalize methods',requests=requests,observations=outputs)
path=D/'HaskellObservations.json'
if a.check:assert json.loads(path.read_text())==artifact
else:path.write_text(json.dumps(artifact,separators=(',',':'))+'\n')
suite=subprocess.run([a.oracle,'--suite','--seed','1729','--qc-max-success','100'],text=True,capture_output=True,check=True);assert '48 examples, 0 failures'in suite.stdout,suite.stdout;baseline=re.sub(r'Finished in [0-9.]+ seconds','Finished in <elapsed> seconds',suite.stdout)
path=D/'OriginalSuite.txt'
if a.check:assert path.read_text()==baseline
else:path.write_text(baseline)
print('PASS',len(outputs),'Haskell bit-adapter rows + original48examples (30KAT/10chunk/8truncation)')
