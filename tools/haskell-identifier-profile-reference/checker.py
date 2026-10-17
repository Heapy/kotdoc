#!/usr/bin/env python3
"""Finite read-only provenance/oracle/application checker and durable proof runner."""
import argparse,base64,concurrent.futures,datetime,gzip,hashlib,json,os,pathlib,subprocess,sys
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];FIX=ROOT/'core/src/test/resources/identifier-profile'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def rows(name):return[json.loads(l)for l in gzip.decompress((FIX/(name+'.jsonl.gz')).read_bytes()).splitlines()]
def source():
 m=json.loads((HERE/'manifest.json').read_text())
 for path,pin in m['pins'].items():assert sha(ROOT/path)==pin,('pin',path)
 for path,pin in m['runtimePins'].items():
  if pathlib.Path(path).exists():assert sha(pathlib.Path(path))==pin,('runtime',path)
 old=json.loads((HERE/'integration.json').read_text());assert sha(HERE/'DokuWikiIdentifiers-before.kt')==old['historicalHelperSha256'];assert sha(ROOT/old['currentHelper'])==old['currentHelperSha256']
 p=subprocess.run(['git','show',old['historicalCommit']+':'+old['currentHelper']],cwd=ROOT,capture_output=True,check=True);assert hashlib.sha256(p.stdout).hexdigest()==old['historicalHelperSha256']
 # Exact whole original Shared source and selected runtime profile are pinned above.
 print('Read-only current integration/source/tool/helper/old-payload pins passed',flush=True)
def verify(oracle):
 source()
 for kind,binary in [('helper',oracle),('dokuwiki-reader','/tmp/kotdoc-dokuwiki-reader/oracle'),('muse-reader','/tmp/kotdoc-muse-reader/oracle'),('muse-writer','/tmp/kotdoc-muse-writer/oracle')]:
  rs=rows(kind);p=subprocess.run([str(binary)],input=('\n'.join(json.dumps(r['request'],ensure_ascii=False)for r in rs)+'\n').encode(),capture_output=True,check=True);assert[json.loads(l)for l in p.stdout.splitlines()]==[r['expected']for r in rs],kind
 lower=json.loads(subprocess.check_output([str(oracle),'--lowercase-mappings']));assert lower==json.loads((FIX/'lowercase-mappings.json').read_text());assert len(lower)==1433
 print('648 native original observations and exhaustive1433 lowercase mapping enumeration passed',flush=True)
def cli_check(cli,original=False):
 command=[str(cli)]+(['--emulate']if original else[])
 def check(r):
  p=subprocess.run(command+r['args'],input=base64.b64decode(r['input']),capture_output=True,cwd=ROOT/'upstream/pandoc/test');assert(p.returncode,p.stdout,p.stderr)==(r['exitCode'],base64.b64decode(r['stdout']),base64.b64decode(r['stderr'])),(r['name'],p.returncode,p.stdout,p.stderr)
 rs=rows('cli')
 with concurrent.futures.ThreadPoolExecutor(max_workers=4)as pool:list(pool.map(check,rs))
 print(len(rs),'exact original CLI exit/stdout/stderr observations passed',flush=True)
def atomic(path,data):
 tmp=path.with_suffix('.tmp')
 with tmp.open('w')as out:json.dump(data,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
 tmp.replace(path)
def durable(cli,out):
 source();assert not out.exists()or not any(out.iterdir());out.mkdir(parents=True,exist_ok=True)
 cmd=[sys.executable,str(HERE/'checker.py'),'--check-cli','--cli',str(cli)];data=dict(started=datetime.datetime.now(datetime.timezone.utc).isoformat(),command=cmd,cwd=str(ROOT),status='starting',pins={str(p):sha(p)for p in [*sorted((cli.parent.parent/'lib').glob('*.jar')),HERE/'manifest.json',HERE/'checker.py',FIX/'cli.jsonl.gz']})
 with(out/'stdout.log').open('wb')as stdout,(out/'stderr.log').open('wb')as stderr:
  child=subprocess.Popen(cmd,cwd=ROOT,env=dict(os.environ,JAVA_HOME='/Users/yoda/.sdkman/candidates/java/21.0.12-librca'),stdout=stdout,stderr=stderr);data.update(pid=child.pid,status='running');atomic(out/'status.json',data);code=child.wait()
 data.update(exitCode=code,status='passed'if code==0 else'failed',finished=datetime.datetime.now(datetime.timezone.utc).isoformat(),stdoutSha256=sha(out/'stdout.log'),stderrSha256=sha(out/'stderr.log'));atomic(out/'terminal.json',data);print(json.dumps(data),flush=True);return code
if __name__=='__main__':
 p=argparse.ArgumentParser();g=p.add_mutually_exclusive_group(required=True);g.add_argument('--check-source',action='store_true');g.add_argument('--verify-oracle',action='store_true');g.add_argument('--check-cli',action='store_true');g.add_argument('--durable-cli',action='store_true');p.add_argument('--oracle',type=pathlib.Path,default=pathlib.Path('/tmp/kotdoc-identifier-profile/oracle'));p.add_argument('--cli',type=pathlib.Path);p.add_argument('--original-cli',action='store_true');p.add_argument('--output-dir',type=pathlib.Path);a=p.parse_args()
 if a.check_source:source()
 elif a.verify_oracle:verify(a.oracle)
 elif a.check_cli:cli_check(a.cli,a.original_cli)
 else:sys.exit(durable(a.cli,a.output_dir))
