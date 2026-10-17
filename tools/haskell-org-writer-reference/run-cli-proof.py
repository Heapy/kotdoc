#!/usr/bin/env python3
"""Durable verifier execution. Status survives loss of tool process handles."""
import argparse,datetime,hashlib,json,os,pathlib,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def atomic(path,data):
 tmp=path.with_suffix('.tmp')
 with tmp.open('w')as f:json.dump(data,f,indent=2);f.write('\n');f.flush();os.fsync(f.fileno())
 tmp.replace(path)
def main():
 p=argparse.ArgumentParser();p.add_argument('--cli',type=pathlib.Path,required=True);p.add_argument('--fixtures',type=pathlib.Path,required=True);p.add_argument('--output-dir',type=pathlib.Path,required=True);p.add_argument('--freeze',type=pathlib.Path);p.add_argument('--regressions',action='store_true');a=p.parse_args();out=a.output_dir;assert not out.exists();out.mkdir(parents=True)
 if a.freeze:
  pins=json.loads(a.freeze.read_text());assert all(sha(ROOT/name)==value for name,value in pins.items())
 paths=[a.cli,a.fixtures,pathlib.Path(__file__),ROOT/'tools/check-org-writer-cli.py',*sorted((a.cli.parent.parent/'lib').glob('*.jar'))];
 for name in ['cli-header-int64.jsonl.gz','cli-tag-width.jsonl.gz']:
  if(a.fixtures.parent/name).exists():paths.append(a.fixtures.parent/name)
 if a.regressions:
  paths += [ROOT/'tools/check-muse-writer-cli.py',ROOT/'tools/check-rst-writer-cli.py']
  for sub,names in [('muse-writer',['cli.jsonl.gz','cli-template-boundaries.jsonl.gz','commands.jsonl.gz']),('rst-writer',['cli.jsonl.gz','cli-display-math.jsonl.gz','commands.jsonl.gz']),('dokuwiki-reader',['cli-options.json','cli-warnings.json'])]:paths += [ROOT/'core/src/test/resources'/sub/name for name in names]
 atomic(out/'inputs.json',dict(started=now(),pid=os.getpid(),argv=sys.argv,paths={str(p):sha(p)for p in paths}))
 jobs=[('org',[sys.executable,str(ROOT/'tools/check-org-writer-cli.py'),'--cli',str(a.cli),'--fixtures',str(a.fixtures),'--report',str(out/'org-report.json')])]
 if a.regressions:jobs += [(name,[sys.executable,str(ROOT/'tools'/checker),'--cli',str(a.cli)])for name,checker in [('muse','check-muse-writer-cli.py'),('rst','check-rst-writer-cli.py')]]
 results=[]
 for name,cmd in jobs:
  status=dict(name=name,command=cmd,started=now(),status='starting')
  with(out/(name+'.stdout')).open('wb')as stdout,(out/(name+'.stderr')).open('wb')as stderr:
   child=subprocess.Popen(cmd,cwd=ROOT,env=dict(os.environ,JAVA_HOME='/Users/yoda/.sdkman/candidates/java/21.0.12-librca'),stdout=stdout,stderr=stderr);status.update(pid=child.pid,status='running');atomic(out/(name+'.json'),status);code=child.wait()
  status.update(finished=now(),exitCode=code,status='passed'if code==0 else'failed',stdoutSha256=sha(out/(name+'.stdout')),stderrSha256=sha(out/(name+'.stderr')));atomic(out/(name+'.json'),status);results.append(status)
  if code:break
 success=len(results)==len(jobs)and all(x['exitCode']==0 for x in results);atomic(out/'terminal.json',dict(finished=now(),allPassed=success,results=results));print(json.dumps(results),flush=True);return 0 if success else 1
if __name__=='__main__':sys.exit(main())
