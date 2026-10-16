#!/usr/bin/env python3
"""Read-only Muse application checks against independent original observations."""
import argparse,base64,gzip,json,pathlib,shlex,subprocess,tempfile,concurrent.futures,os,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
def rows(p):return [json.loads(x) for x in gzip.decompress(p.read_bytes()).splitlines()]
def main():
 p=argparse.ArgumentParser();p.add_argument('--jobs',type=int,default=4);p.add_argument('--original-cli',action='store_true');p.add_argument('--cli',default=str(ROOT/'cli/build/install/cli/bin/cli'));p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/muse-reader');a=p.parse_args()
 command=[a.cli]
 if a.original_cli:
  assert hashlib.sha256(pathlib.Path(a.cli).read_bytes()).hexdigest()==json.loads((a.fixture_dir/'manifest.json').read_text())['originalCliSha256']
  command+=['--emulate'];os.environ['pandoc_datadir']=str(ROOT/'upstream/pandoc/data')
 for name in ['cli-input.json','cli-warnings.json','cli-options.json']:
  if not (a.fixture_dir/name).exists():continue
  records=json.loads((a.fixture_dir/name).read_text())
  for r in records:
   with tempfile.TemporaryDirectory() as tmp:
    for file,value in r.get('files',{}).items():(pathlib.Path(tmp)/file).write_bytes(base64.b64decode(value))
    result=subprocess.run([*command,*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=tmp)
    assert (result.returncode,result.stdout,result.stderr)==(r['exitCode'],base64.b64decode(r['stdout']),base64.b64decode(r['stderr'])),(name,r['args'],result.returncode,result.stdout,result.stderr)
  print(name,len(records),'exact exit/stdout/stderr passed',flush=True)
 def library(r):
  result=subprocess.run([*command,*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=ROOT)
  assert result.returncode==r['exitCode'] and result.stderr==base64.b64decode(r['stderr']) and json.loads(result.stdout)==json.loads(base64.b64decode(r['stdout'])),(r['name'],result.stdout,result.stderr)
 with concurrent.futures.ThreadPoolExecutor(max_workers=a.jobs) as pool:list(pool.map(library,rows(a.fixture_dir/'cli-library.jsonl.gz')))
 print('250 original library cases through application normalization passed',flush=True)
 count=0
 for r in rows(a.fixture_dir/'commands.jsonl.gz'):
  if pathlib.Path(r['file']).name!='5107.md':continue
  result=subprocess.run([*command,*shlex.split(r['command'])[1:]],input=r['input'].encode(),capture_output=True,cwd=ROOT/'upstream/pandoc/test')
  assert result.returncode==0 and result.stdout==r['expected'].encode() and result.stderr==b'',(r['file'],r['number'],result.stdout,result.stderr)
  count+=1
 assert count==2
 result=subprocess.run([*command,'--list-input-formats'],capture_output=True,check=True);assert 'muse' in result.stdout.decode().splitlines()
 print('2 original 5107 commands and input registry passed; 4172 requires Muse writer',flush=True)
if __name__=='__main__':main()
