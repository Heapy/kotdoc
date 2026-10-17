#!/usr/bin/env python3
"""Durable verification child status; never changes source or expected payloads."""
import argparse,pathlib,json,subprocess,datetime,os,hashlib
p=argparse.ArgumentParser();p.add_argument('--directory',type=pathlib.Path,required=True);p.add_argument('--command',nargs=argparse.REMAINDER,required=True);p.add_argument('--pins',nargs='*',default=[]);a=p.parse_args();a.directory.mkdir(parents=True,exist_ok=True)
status=a.directory/'status.json'
assert not status.exists(),'Refuse replacing a previous proof status'
def utc():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def save(value):
 tmp=status.with_suffix('.tmp');tmp.write_text(json.dumps(value,indent=2)+'\n');tmp.replace(status)
state=dict(state='starting',wrapperPid=os.getpid(),start=utc(),command=a.command,pins={s:hashlib.sha256(pathlib.Path(s).read_bytes()).hexdigest()for s in a.pins});save(state)
with open(a.directory/'stdout.log','wb')as out,open(a.directory/'stderr.log','wb')as err:
 child=subprocess.Popen(a.command,stdout=out,stderr=err);state.update(state='running',pid=child.pid);save(state);code=child.wait()
state.update(state='terminal',exitCode=code,end=utc(),logs={s:dict(bytes=(a.directory/s).stat().st_size,sha256=hashlib.sha256((a.directory/s).read_bytes()).hexdigest())for s in ['stdout.log','stderr.log']});save(state);print(json.dumps(state),flush=True);raise SystemExit(code)
