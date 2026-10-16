#!/usr/bin/env python3
"""Original Class.IO.logOutput warning formatting; preserves other diagnostics."""
import argparse,base64,json,os,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--original-cli',required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();assert not a.output.exists()
names=['bad\nfile.muse','bad\n\nfile.muse','bad\r\nfile.muse','bad\rfile.muse','bad\tfile.muse','Ж😀.muse','\nleading.muse','trailing.muse\n','one\ntwo\nthree.muse','chunk']
records=[]
for name in names:
 for payload in [b'\xff',b'PK\x03\x04\xff']:
  args=['--trace','-f','muse','-t','json',name];files={name:base64.b64encode(payload).decode()}
  with tempfile.TemporaryDirectory() as tmp:
   (pathlib.Path(tmp)/name).write_bytes(payload)
   r=subprocess.run([a.original_cli,'--emulate',*args],capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
  records.append(dict(args=args,files=files,input='',exitCode=r.returncode,stdout=base64.b64encode(r.stdout).decode(),stderr=base64.b64encode(r.stderr).decode()))
for suffix in [[],['-']]:
 args=['--trace','-f','muse','-t','json',*suffix];payload=b'\xff\n\nb\n'
 r=subprocess.run([a.original_cli,'--emulate',*args],input=payload,capture_output=True,cwd=ROOT,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
 records.append(dict(args=args,files={},input=base64.b64encode(payload).decode(),exitCode=r.returncode,stdout=base64.b64encode(r.stdout).decode(),stderr=base64.b64encode(r.stderr).decode()))
a.output.write_text(json.dumps(records,indent=2)+'\n');print(len(records),'original warning/error/trace observations captured')
