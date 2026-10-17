#!/usr/bin/env python3
"""Byte-exact complete App/command expectations captured solely from original Pandoc."""
import argparse,base64,json,pathlib,subprocess,tempfile,os,gzip,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument('--cli',required=True);p.add_argument('--fixtures',type=pathlib.Path,default=ROOT/'tools/haskell-org-writer-reference/fixtures/cli-observations.jsonl.gz');p.add_argument('--report',type=pathlib.Path);a=p.parse_args();data=a.fixtures.read_bytes();text=gzip.decompress(data)if a.fixtures.suffix=='.gz'else data;records=[json.loads(x)for x in text.splitlines()];additional=[a.fixtures.parent/name for name in ['cli-header-int64.jsonl.gz','cli-tag-width.jsonl.gz']]
 for path in additional:
  if path.exists()and path!=a.fixtures:records += [json.loads(x)for x in gzip.decompress(path.read_bytes()).splitlines()]
 results=[]
 for r in records:
  with tempfile.TemporaryDirectory()as tmp:
   for name,value in r['files'].items():pathlib.Path(tmp,name).write_bytes(base64.b64decode(value))
   actual=subprocess.run([a.cli,*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=tmp,env=os.environ.copy());expected=(r['exitCode'],base64.b64decode(r['stdout']),base64.b64decode(r['stderr']));observed=(actual.returncode,actual.stdout,actual.stderr)
   assert observed==expected,(r['name'],expected,observed)
   for name,value in r['created'].items():assert pathlib.Path(tmp,name).read_bytes()==base64.b64decode(value),(r['name'],name)
   results.append(dict(name=r['name'],exitCode=actual.returncode,stdoutSha256=hashlib.sha256(actual.stdout).hexdigest(),stderrSha256=hashlib.sha256(actual.stderr).hexdigest()))
   print('PASS',r['name'],flush=True)
 report=dict(comparisons=len(results),commands=sum(r['name'].startswith('command/')for r in records),fixtureSha256=hashlib.sha256(data).hexdigest(),results=results)
 if a.report:a.report.write_text(json.dumps(report,indent=2)+'\n')
 print('PASS',len(results),'complete byte-exact Org App comparisons;',report['commands'],'original full commands',flush=True)
if __name__=='__main__':main()
