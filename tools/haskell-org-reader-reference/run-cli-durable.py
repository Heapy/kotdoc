#!/usr/bin/env python3
"""Durable verification wrapper: logs and atomic terminal code survive tool sessions."""
import argparse,hashlib,json,os,pathlib,subprocess,sys,time
HERE=pathlib.Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--directory',required=True);p.add_argument('--classpath',required=True);p.add_argument('--fixtures',nargs='+',default=['app.jsonl.gz','app-io.jsonl.gz']);a=p.parse_args()
directory=pathlib.Path(a.directory);directory.mkdir(parents=True,exist_ok=False)
def sha(path):return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
def atomic(name,value):
 path=directory/name;tmp=path.with_suffix(path.suffix+'.tmp');tmp.write_text(json.dumps(value,indent=2)+'\n');tmp.replace(path)
command=[sys.executable,str(HERE/'check-cli.py'),'--classpath',a.classpath,'--report',str(directory/'report.json'),'--fixtures',*a.fixtures]
state=dict(pid=os.getpid(),start=time.time(),command=command,classpath=[dict(path=path,sha256=sha(path))for path in a.classpath.split(':')],checker=dict(path=str(HERE/'check-cli.py'),sha256=sha(HERE/'check-cli.py')),fixtures=[dict(path=str(HERE/'fixtures'/name),sha256=sha(HERE/'fixtures'/name))for name in a.fixtures],stage='running')
atomic('status.json',state)
try:
 with (directory/'stdout.log').open('wb')as stdout,(directory/'stderr.log').open('wb')as stderr:
  child=subprocess.Popen(command,stdout=stdout,stderr=stderr);state['childPid']=child.pid;atomic('status.json',state);code=child.wait()
 state.update(stage='terminal',exitCode=code,finished=time.time(),stdoutSha256=sha(directory/'stdout.log'),stderrSha256=sha(directory/'stderr.log'))
 atomic('status.json',state);atomic('exit.json',dict(exitCode=code,finished=state['finished']));raise SystemExit(code)
except BaseException as error:
 if isinstance(error,SystemExit):raise
 state.update(stage='wrapper-error',error=repr(error),finished=time.time());atomic('status.json',state);raise
