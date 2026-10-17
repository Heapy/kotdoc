#!/usr/bin/env python3
"""Durable installed CLI verification: logs and atomic terminal status survive tool loss."""
import pathlib,subprocess,json,os,time,hashlib,argparse
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
p=argparse.ArgumentParser();p.add_argument('--dist',type=pathlib.Path,required=True);p.add_argument('--evidence',type=pathlib.Path,required=True);a=p.parse_args();a.evidence.mkdir(parents=True,exist_ok=True);status=a.evidence/'status.json';log=a.evidence/'stdout-stderr.log';checker=HERE/'check-cli.py';cp=':'.join(str(p.resolve())for p in sorted((a.dist/'lib').glob('*.jar')));command=['python3',str(checker),'--classpath',cp,'--report',str(a.evidence/'report.json')]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(x):
 p=status.with_suffix('.new');p.write_text(json.dumps(x,indent=2)+'\n');p.replace(status)
assert not status.exists(),'Refuse to replace earlier verification evidence'
files=[pathlib.Path(__file__),checker,*list((HERE/'reader-fixtures').rglob('*')),*list((a.dist/'lib').glob('*.jar'))];files=[p for p in files if p.is_file()]
meta=dict(pid=os.getpid(),started=time.time(),command=command,cwd=str(ROOT),state='running',pins={str(p.resolve()):sha(p)for p in files});save(meta)
with log.open('w')as out:result=subprocess.run(command,stdout=out,stderr=subprocess.STDOUT,cwd=ROOT,env=dict(os.environ,JAVA_HOME='/Users/yoda/.sdkman/candidates/java/21.0.12-librca'))
meta.update(state='terminal',exitCode=result.returncode,finished=time.time(),logSHA256=sha(log));save(meta);print('durable terminal exit',result.returncode,flush=True);raise SystemExit(result.returncode)
