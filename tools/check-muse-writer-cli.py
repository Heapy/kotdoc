#!/usr/bin/env python3
"""Read-only complete Muse writer application observations and original goldens/commands."""
import argparse,base64,concurrent.futures,gzip,hashlib,json,os,pathlib,shlex,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def rows(p):return [json.loads(l)for l in gzip.decompress(p.read_bytes()).splitlines()]
def main():
 p=argparse.ArgumentParser();p.add_argument('--cli',default=str(ROOT/'cli/build/install/cli/bin/cli'));p.add_argument('--original-cli',action='store_true');p.add_argument('--jobs',type=int,default=4);p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/muse-writer');a=p.parse_args();command=[a.cli]
 if a.original_cli:
  assert hashlib.sha256(pathlib.Path(a.cli).read_bytes()).hexdigest()==json.loads((a.fixture_dir/'manifest.json').read_text())['originalCliSha256'];command+=['--emulate'];os.environ['pandoc_datadir']=str(ROOT/'upstream/pandoc/data')
 def check(r):
  with tempfile.TemporaryDirectory()as tmp:
   for file,value in r['files'].items():(pathlib.Path(tmp)/file).write_bytes(base64.b64decode(value))
   result=subprocess.run([*command,*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=tmp)
   assert(result.returncode,result.stdout,result.stderr)==(r['exitCode'],base64.b64decode(r['stdout']),base64.b64decode(r['stderr'])),(r['name'],r['args'],result.returncode,result.stdout,result.stderr)
   for file,value in r['created'].items():assert(pathlib.Path(tmp)/file).read_bytes()==base64.b64decode(value),(r['name'],file)
 records=rows(a.fixture_dir/'cli.jsonl.gz')+rows(a.fixture_dir/'cli-template-boundaries.jsonl.gz')
 with concurrent.futures.ThreadPoolExecutor(max_workers=a.jobs)as pool:list(pool.map(check,records))
 controls=[]
 for filename,indices in [('cli-options.json',[5,6,7,13]),('cli-warnings.json',[0,1])]:
  source=json.loads((ROOT/'core/src/test/resources/dokuwiki-reader'/filename).read_text())
  for i in indices:controls.append(dict(source[i],name=f'dokuwiki-regression/{filename}/{i}',files=source[i].get('files',{}),created={}))
 with concurrent.futures.ThreadPoolExecutor(max_workers=a.jobs)as pool:list(pool.map(check,controls))
 print('6 accepted DokuWiki application regression controls passed',flush=True)
 print(len(records),'complete original application exit/stdout/stderr/file observations passed',flush=True)
 for r in rows(a.fixture_dir/'commands.jsonl.gz'):
  result=subprocess.run([*command,*shlex.split(r['command'])[1:]],input=r['input'].encode(),capture_output=True,cwd=ROOT/'upstream/pandoc/test');assert(result.returncode,result.stdout,result.stderr)==(0,r['expected'].encode(),b''),(r['number'],result.stdout,result.stderr)
 for flag in ['--list-input-formats','--list-output-formats']:
  result=subprocess.run([*command,flag],capture_output=True,check=True);assert'muse'in result.stdout.decode().splitlines()
 print('2 complete4172 commands and both format registries passed',flush=True)
if __name__=='__main__':main()
