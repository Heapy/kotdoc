#!/usr/bin/env python3
"""Complete original Muse reader application option observations, immutable output."""
import argparse,base64,json,os,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--original-cli',required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();assert not a.output.exists()
cases=[]
for fmt in ['muse','muse-amuse','muse-auto_identifiers','muse+ascii_identifiers','muse+gfm_auto_identifiers','muse+smart']:
 cases.append((['-f',fmt,'-t','json'],'* HÉLLO world!\n\nparagraph [[target][link]]\n'.encode(),{}))
for flags in [[],['--tab-stop=8'],['--preserve-tabs'],['--trace'],['--default-image-extension=png'],['--toc']]:
 cases.append((['-f','muse','-t','json',*flags],b'\t- item\n\n[[image]]\n',{}))
cases += [(['-f','muse'],b'* Heading\n\nbody',{}),(['-t','json','document.muse'],b'',{'document.muse':b'* Heading\n\nbody'}),(['-f','muse-amuse','-t','json','--trace','chunk'],b'',{'chunk':b'[1] note\n\nref [1]\n'}),(['-f','muse+tex_math_dollars','-t','json'],b'$x$',{})]
records=[]
for args,payload,files in cases:
 with tempfile.TemporaryDirectory() as tmp:
  for name,value in files.items():(pathlib.Path(tmp)/name).write_bytes(value)
  r=subprocess.run([a.original_cli,'--emulate',*args],input=payload,capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
 records.append(dict(args=args,files={k:base64.b64encode(v).decode() for k,v in files.items()},input=base64.b64encode(payload).decode(),exitCode=r.returncode,stdout=base64.b64encode(r.stdout).decode(),stderr=base64.b64encode(r.stderr).decode()))
a.output.write_text(json.dumps(records,indent=2)+'\n');print(len(records),'original application options captured')
