#!/usr/bin/env python3
"""Immutable original application observations for the complete Muse writer."""
import argparse,base64,gzip,json,os,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
def b64(data):return base64.b64encode(data).decode()
p=argparse.ArgumentParser();p.add_argument('--original-cli',required=True);p.add_argument('--fixture-dir',type=pathlib.Path,required=True);a=p.parse_args();out=a.fixture_dir/'cli.jsonl.gz';assert not out.exists()
def document(xs):return dict(meta={},blocks=[dict(t='Para',c=xs)],**{'pandoc-api-version':[1,23,1,2]})
def string(s):return dict(t='Str',c=s)
cases=[]
for r in map(json.loads,gzip.decompress((a.fixture_dir/'original164.jsonl.gz').read_bytes()).splitlines()):
 o=r['options'];fmt='muse-amuse-auto_identifiers'+''.join('+'+e for e in o['extensions']);args=['-f','json','-t',fmt,'--wrap='+o['wrap'][4:].lower(),'--columns='+str(o['columns']),'--reference-location='+o['referenceLocation'][5:].lower()]
 cases.append(('original/'+r['name'],args,json.dumps(r['document'],ensure_ascii=False).encode(),{},[]))
plain=json.dumps(document([string('one two three four five six seven eight nine')])).encode()
for flags in [[],['-s'],['-s','-V','title=Example'],['--template=default.muse','-V','title=Example'],['--wrap=none','--columns=10'],['--wrap=auto','--columns=10'],['--wrap=preserve','--columns=10'],['--reference-location=block'],['--reference-location=section']]:cases.append(('options/'+repr(flags),['-f','json','-t','muse',*flags],plain,{},[]))
for fmt in ['muse-amuse','muse+auto_identifiers','muse+ascii_identifiers','muse+gfm_auto_identifiers','muse+smart','muse+tex_math_dollars']:
 cases.append(('extension/'+fmt,['-f','json','-t',fmt],plain,{},[]))
for tex in ['\\badcommand','\\frac{','a\n\\notacommand','x\x00\\badcommand']:
 payload=json.dumps(document([dict(t='Math',c=[dict(t='InlineMath'),tex])])).encode();cases.append(('warning/'+repr(tex),['-f','json','-t','muse'],payload,{},[]))
for contents in [[dict(t='SmallCaps',c=[string('x')])],[dict(t='Cite',c=[[],[string('nested')]])]]:
 payload=json.dumps(document([dict(t='Cite',c=[[],contents])])).encode();cases.append(('error/'+repr(contents),['-f','json','-t','muse'],payload,{},[]))
for template in ['$title$:$body$','$if(title)$broken']:
 cases.append(('template/'+template,['-f','json','-t','muse','--template=custom.muse','-V','title=Title'],plain,{'custom.muse':template.encode()},[]))
cases += [('inference/json-to-muse-file',['document.json','--output=out.muse'],b'',{'document.json':plain},['out.muse']),('inference/muse-to-muse-file',['document.muse','--output=out.muse'],b'',{'document.muse':b'* Heading\n\nbody\n'},['out.muse']),('inference/muse-default-html',['document.muse'],b'',{'document.muse':b'* Heading\n\nbody\n'},[])]
for source,golden,standalone in [('testsuite.native','writer.muse',True),('tables.native','tables.muse',False)]:
 cases.append(('old/'+golden,['-f','native','-t','muse','--columns=80','--variable','pandoc-version=']+(['-s']if standalone else[])+[source],b'',{source:(ROOT/'upstream/pandoc/test'/source).read_bytes()},[]))
records=[]
for name,args,payload,files,outputs in cases:
 with tempfile.TemporaryDirectory() as tmp:
  for file,value in files.items():(pathlib.Path(tmp)/file).write_bytes(value)
  result=subprocess.run([a.original_cli,'--emulate',*args],input=payload,capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
  created={file:b64((pathlib.Path(tmp)/file).read_bytes())for file in outputs}
 records.append(dict(name=name,args=args,input=b64(payload),files={k:b64(v)for k,v in files.items()},created=created,exitCode=result.returncode,stdout=b64(result.stdout),stderr=b64(result.stderr)))
with gzip.GzipFile(filename=str(out),mode='wb',mtime=0)as f:f.write(''.join(json.dumps(r,ensure_ascii=False)+'\n'for r in records).encode())
print(len(records),'original complete application observations captured')
