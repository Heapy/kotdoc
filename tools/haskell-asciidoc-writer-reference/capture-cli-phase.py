#!/usr/bin/env python3
"""OutputSettings warning/template/input phase boundaries, original App only."""
import pathlib,subprocess,tempfile,base64,json,gzip,os
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
EXE='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc';rows=[]
def enc(x):return base64.b64encode(x).decode()
def add(name,args,input,files=None):
 with tempfile.TemporaryDirectory()as tmp:
  for filename,data in(files or{}).items():pathlib.Path(tmp,filename).write_bytes(data)
  p=subprocess.run([EXE,'--emulate',*args],input=input,capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
  rows.append(dict(name=name,args=args,input=enc(input),files={k:enc(v)for k,v in(files or{}).items()},created={},exitCode=p.returncode,stdout=enc(p.stdout),stderr=enc(p.stderr)))
for fmt in ['asciidoc','asciidoctor','asciidoc_legacy']:
 for input in [b'broken',b'[Para [Str "valid"]]']:
  for filename,template in [('bad.adoc',b'$if(body)$'),('valid.adoc',b'$body$'),('missing.adoc',None)]:
   add('phase/'+fmt+'/'+repr((input,filename)),['-f','native','-t',fmt,'--template',filename],input,{}if template is None else{filename:template})
 for reader in ['dokuwiki','org','muse','rst']:
  add('warning-before-reader-trace/'+fmt+'/'+reader,['-f',reader,'-t',fmt,'--trace'],b'body\n')
for ext in ['+amuse','+nonexistent']:
 add('warning-before-extension-error/'+ext,['-f','native','-t','asciidoctor'+ext,'--template','bad.adoc'],b'broken',{'bad.adoc':b'$if(body)$'})
p=HERE/'fixtures/cli-phase.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(r)+'\n').encode()for r in rows),mtime=0));print(len(rows),'original App phase observations captured')
