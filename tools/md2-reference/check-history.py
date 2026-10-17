from pathlib import Path
import json,gzip,subprocess,argparse,hashlib
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--oracle',default='/tmp/kotdoc-md2-haskell-oracle');p.add_argument('--caf-oracle',default='/tmp/kotdoc-md2-caf-oracle');a=p.parse_args()
first=gzip.decompress((D/'history/first-mixed-dispatch.json.gz').read_bytes());assert hashlib.sha256(first).hexdigest()==json.loads((D/'CaptureHistory.json').read_text())['firstCaptureSha256']
for path,exe in [('first-mixed-dispatch.json.gz',a.oracle),('borrow-shared-caf.json.gz',a.caf_oracle)]:
 rows=json.loads(gzip.decompress((D/'history'/path).read_bytes()));result=subprocess.run([exe],input=''.join(json.dumps(r['request'])+'\n'for r in rows),capture_output=True,text=True,check=True);values=[json.loads(s)for s in result.stdout.splitlines()];assert values==[r['expected']for r in rows],path
 print('PASS historical',path,len(rows),'original observations; excluded from MD2 current counts')
