#!/usr/bin/env python3
"""Read-only RST App checks: original full commands remain distinct from writer-only AST probes."""
import argparse,base64,concurrent.futures,gzip,json,os,pathlib,shlex,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def rows(p):return[json.loads(l)for l in gzip.decompress(p.read_bytes()).splitlines()]
def main():
 p=argparse.ArgumentParser();p.add_argument('--cli',required=True);p.add_argument('--original-cli',action='store_true');p.add_argument('--jobs',type=int,default=4);p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/rst-writer');a=p.parse_args();command=[a.cli]
 if a.original_cli:command+=['--emulate'];os.environ['pandoc_datadir']=str(ROOT/'upstream/pandoc/data')
 def check(r):
  with tempfile.TemporaryDirectory()as tmp:
   for name,data in r.get('files',{}).items():(pathlib.Path(tmp)/name).write_bytes(base64.b64decode(data))
   result=subprocess.run([*command,*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=tmp)
   assert(result.returncode,result.stdout,result.stderr)==(r['exitCode'],base64.b64decode(r['stdout']),base64.b64decode(r['stderr'])),(r['name'],r['args'],result.returncode,result.stdout,result.stderr)
   for name,data in r.get('created',{}).items():assert(pathlib.Path(tmp)/name).read_bytes()==base64.b64decode(data),(r['name'],name)
 observations=rows(a.fixture_dir/'cli.jsonl.gz')+rows(a.fixture_dir/'cli-display-math.jsonl.gz')
 with concurrent.futures.ThreadPoolExecutor(max_workers=a.jobs)as pool:list(pool.map(check,observations))
 print(len(observations),'RST original complete App exit/stdout/stderr/file observations passed',flush=True)
 passed=0;pending=[]
 for r in rows(a.fixture_dir/'commands.jsonl.gz'):
  if r['reader']not in ['native','latex','dokuwiki']:pending.append(r);continue
  result=subprocess.run([*command,*shlex.split(r['command'])[1:]],input=r['input'].encode(),capture_output=True,cwd=ROOT/'upstream/pandoc/test');combined=(''.join('2> '+l+'\n'for l in result.stderr.decode().splitlines())+result.stdout.decode()).replace('\r','')+(f'=> {result.returncode}\n'if result.returncode else'');assert combined==r['expected'],(r['file'],r['number'],result.returncode,combined,r['expected']);passed+=1
 assert passed==21 and len(pending)==22
 print('21 complete original RST commands passed;22 commands require missing readers and remain pending (all43 AST writer probes separate)',flush=True)
 for flag in ['--list-output-formats']:
  result=subprocess.run([*command,flag],capture_output=True,check=True);assert'rst'in result.stdout.decode().splitlines()
 print('RST output registry passed',flush=True)
if __name__=='__main__':main()
