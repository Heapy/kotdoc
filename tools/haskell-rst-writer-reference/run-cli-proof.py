#!/usr/bin/env python3
"""Durable installed CLI proof; atomically retain status independently of tool sessions."""
import argparse,concurrent.futures,datetime,hashlib,json,os,pathlib,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def atomic(path,data):
 temporary=path.with_suffix('.tmp')
 with temporary.open('w')as out:json.dump(data,out,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
 temporary.replace(path)
def main():
 p=argparse.ArgumentParser();p.add_argument('--cli',type=pathlib.Path,required=True);p.add_argument('--output-dir',type=pathlib.Path,required=True);p.add_argument('--freeze',type=pathlib.Path,required=True);p.add_argument('--rst-only',action='store_true');p.add_argument('--java-home',default='/Users/yoda/.sdkman/candidates/java/21.0.12-librca');a=p.parse_args();out=a.output_dir;assert not out.exists()or not any(out.iterdir()),'Refuse overwrite of durable proof';out.mkdir(parents=True,exist_ok=True)
 frozen=json.loads(a.freeze.read_text());assert all(sha(ROOT/path)==pin for path,pin in frozen.items()),'Frozen source mismatch'
 paths=[*sorted((a.cli.parent.parent/'lib').glob('*.jar')),ROOT/'tools/check-rst-writer-cli.py',ROOT/'tools/check-muse-writer-cli.py',pathlib.Path(__file__),a.freeze]
 for sub,names in [('rst-writer',['cli.jsonl.gz','cli-display-math.jsonl.gz','commands.jsonl.gz','manifest.json']),('muse-writer',['cli.jsonl.gz','cli-template-boundaries.jsonl.gz','commands.jsonl.gz']),('dokuwiki-reader',['cli-options.json','cli-warnings.json'])]:paths += [ROOT/'core/src/test/resources'/sub/name for name in names]
 atomic(out/'inputs.json',dict(started=now(),wrapperPid=os.getpid(),javaHome=a.java_home,cli=str(a.cli),frozenFiles=len(frozen),pins={str(path):sha(path)for path in paths}))
 def run(spec):
  name,checker,counts=spec;cmd=[sys.executable,str(ROOT/'tools'/checker),'--cli',str(a.cli)];status=out/(name+'.json');data=dict(command=cmd,cwd=str(ROOT),started=now(),status='starting',expectedCounts=counts)
  with(out/(name+'.stdout')).open('wb')as stdout,(out/(name+'.stderr')).open('wb')as stderr:
   child=subprocess.Popen(cmd,cwd=ROOT,env=dict(os.environ,JAVA_HOME=a.java_home),stdout=stdout,stderr=stderr);data.update(pid=child.pid,status='running');atomic(status,data);code=child.wait()
  data.update(exitCode=code,finished=now(),status='passed'if code==0 else'failed',stdoutSha256=sha(out/(name+'.stdout')),stderrSha256=sha(out/(name+'.stderr')));atomic(status,data);return data
 specs=[('rst','check-rst-writer-cli.py',dict(app=96,fullCommands=21,pendingMissingReaders=22)),('muse-regression','check-muse-writer-cli.py',dict(app=204,dokuControls=6,commands4172=2))]
 if a.rst_only:specs=specs[:1]
 with concurrent.futures.ThreadPoolExecutor(max_workers=2)as pool:results=list(pool.map(run,specs))
 success=all(r['exitCode']==0 for r in results);atomic(out/'terminal.json',dict(finished=now(),allPassed=success,results=results));print(json.dumps(results),flush=True);return 0 if success else 1
if __name__=='__main__':sys.exit(main())
