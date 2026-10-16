#!/usr/bin/env python3
"""Original App.OutputSettings template failure and writer diagnostic ordering."""
import argparse,base64,gzip,json,os,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--original-cli',required=True);p.add_argument('--fixture-dir',type=pathlib.Path,required=True);a=p.parse_args();out=a.fixture_dir/'cli-template-boundaries.jsonl.gz';assert not out.exists()
def math(tex):return dict(t='Math',c=[dict(t='InlineMath'),tex])
plain=dict(meta={},blocks=[dict(t='Para',c=[dict(t='Str',c='body')])],**{'pandoc-api-version':[1,23,1,2]})
warning=dict(meta={k:dict(t='MetaInlines',c=[math('\\'+k+'bad')])for k in ['title','author']},blocks=[dict(t='Para',c=[math('\\bodybad\n')])],**{'pandoc-api-version':[1,23,1,2]})
cases=[]
for template in ['$body$','$if(title)$$title$$endif$\n$body$','$if(title)$broken','$for(foo)$x','$body$ $']:
 for document in [plain,warning]:cases.append(('custom.muse',template,document))
for name in ['bad\nfile.muse','Ж😀.muse']:cases.append((name,'$if(title)$broken',warning))
records=[]
for name,template,document in cases:
 args=['-f','json','-t','muse','--template='+name];payload=json.dumps(document).encode();files={name:template.encode()}
 with tempfile.TemporaryDirectory()as tmp:
  for file,value in files.items():(pathlib.Path(tmp)/file).write_bytes(value)
  result=subprocess.run([a.original_cli,'--emulate',*args],input=payload,capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
 records.append(dict(name='template-boundary/'+repr((name,template,document['meta'])),args=args,input=base64.b64encode(payload).decode(),files={k:base64.b64encode(v).decode()for k,v in files.items()},created={},exitCode=result.returncode,stdout=base64.b64encode(result.stdout).decode(),stderr=base64.b64encode(result.stderr).decode()))
with gzip.GzipFile(filename=str(out),mode='wb',mtime=0)as f:f.write(''.join(json.dumps(r,ensure_ascii=False)+'\n'for r in records).encode())
print(len(records),'original template/ordered-warning observations captured')
