#!/usr/bin/env python3
"""Original App.Input default-charset boundary, separate immutable observations."""
import argparse,base64,json,os,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--original-cli',required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();assert not a.output.exists()
payloads=[b'\xff',b'a\xff\r\nb\rc',b'\xc0\xaf',b'\xe2\x82',b'\x80',b'\xef\xbb\xbftext\r\n',b'\xef\xbb\xbf\xff\r',b'a\x00b',b'\r\n\t* item\r\n','=== \U0001e6c0 ===\r\n'.encode()]
for sig in [b'PK\x03\x04',b'%PDF-',bytes.fromhex('d0cf11e0a1b11ae1'),b'AT&TFORM1234DJV']:
 payloads += [sig,sig+b'\xff',sig+b'\r\n',sig[:-1]+b'\xff',b'prefix'+sig+b'\xff']
payloads += [b'AT&TFORMDJV\xff',b'AT&TFORM123DJV\xff',b'AT&TFORM12345DJV\xff',b'PK\x03\xff',b'%PDF\xff']
records=[]
for n,payload in enumerate(payloads):
 args=['-f','muse','-t','json'];files={}
 if n in [0,1,11,16,21,26]:args+=['bad.muse'];files={'bad.muse':base64.b64encode(payload).decode()}
 with tempfile.TemporaryDirectory() as tmp:
  for name,value in files.items():(pathlib.Path(tmp)/name).write_bytes(base64.b64decode(value))
  r=subprocess.run([a.original_cli,'--emulate',*args],input=b'' if files else payload,capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
 records.append(dict(args=args,files=files,input=base64.b64encode(b'' if files else payload).decode(),exitCode=r.returncode,stdout=base64.b64encode(r.stdout).decode(),stderr=base64.b64encode(r.stderr).decode()))
a.output.write_text(json.dumps(records,indent=2)+'\n');print(len(records),'original input boundary observations captured')
