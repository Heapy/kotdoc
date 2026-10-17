#!/usr/bin/env python3
"""Check independent RSA expectations; optionally replay the unchanged dev oracle."""
from pathlib import Path
import gzip,json,hashlib,subprocess,argparse
R=Path(__file__).resolve().parents[1];F=R/'core/src/test/resources/crypto-rsa-core'
p=argparse.ArgumentParser();p.add_argument('--verify-oracle',action='store_true');p.add_argument('--oracle',default='/tmp/kotdoc-rsa-core-reference');p.add_argument('--demand-oracle',default='/tmp/kotdoc-rsa-demand');p.add_argument('--callback-oracle',default='/tmp/kotdoc-rsa-callback');p.add_argument('--root-oracle',default='/tmp/kotdoc-rsa-root-demand');p.add_argument('--callback-root-oracle',default='/tmp/kotdoc-rsa-callback-root');p.add_argument('--retry-oracle',default='/tmp/kotdoc-rsa-retry');p.add_argument('--blinder-retry-oracle',default='/tmp/kotdoc-rsa-blinder-retry');a=p.parse_args()
m=json.loads((F/'Manifest.json').read_text())
for path,digest in m['sources'].items():assert hashlib.sha256((R/path).read_bytes()).hexdigest()==digest,path
count=0
for name in ['original.jsonl.gz','original-samples.jsonl.gz','extra-demand.jsonl.gz']:
 rows=[json.loads(s)for s in gzip.open(F/name,'rt')];count+=len(rows)
 if a.verify_oracle:
  child=subprocess.Popen([a.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  for i,row in enumerate(rows):
   child.stdin.write(json.dumps(row['request'])+'\n');child.stdin.flush();line=child.stdout.readline();assert line,(name,i,child.poll());assert json.loads(line)==row['expected'],(name,i,row['request'])
  child.stdin.close();assert child.wait()==0
assert count==m['requestCases']
root_count=sum(len((F/name).read_text().splitlines())for name in ['root-demand.jsonl','callback-root.jsonl']);retry_count=sum(sum(1 for _ in gzip.open(F/name,'rt'))for name in ['retry.jsonl.gz','blinder-retry.jsonl.gz']);assert count+root_count+retry_count==m['cases']
if a.verify_oracle:
 samples=[json.loads(line)for line in subprocess.check_output([a.oracle,'--samples'],text=True).splitlines()]
 stored=[json.loads(line)['request']for line in gzip.open(F/'original-samples.jsonl.gz','rt')]
 assert samples==stored,'original Utils.TestDRG sample generator changed'
 vectors=json.loads((R/'tools/rsa-core-reference/VectorInputs.json').read_text())
 response=subprocess.check_output([a.oracle],input=''.join(json.dumps(dict(op='vector',index=i))+'\n'for i in range(3)),text=True)
 assert [json.loads(s)['value']for s in response.splitlines()]==vectors,'original literal vectors changed'
 for executable,name in [(a.demand_oracle,'demand.txt'),(a.callback_oracle,'callback.txt'),(a.root_oracle,'root-demand.jsonl'),(a.callback_root_oracle,'callback-root.jsonl')]:assert subprocess.check_output([executable])==(F/name).read_bytes(),name
 for executable,name in [(a.retry_oracle,'retry.jsonl.gz'),(a.blinder_retry_oracle,'blinder-retry.jsonl.gz')]:assert subprocess.check_output([executable])==gzip.open(F/name,'rb').read(),name
print('PASS',count,'original request rows +',root_count,'strict-root +',retry_count,'finite retry rows,',len(m['sources']),'pins; native replay='+str(a.verify_oracle))
