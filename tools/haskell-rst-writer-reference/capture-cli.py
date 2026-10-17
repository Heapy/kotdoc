#!/usr/bin/env python3
"""Capture complete original App observations; never call Kotlin or overwrite accepted data."""
import pathlib,json,gzip,base64,subprocess,tempfile,os,argparse,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
def rows(p):return[json.loads(x)for x in gzip.decompress(p.read_bytes()).splitlines()]
def main():
 p=argparse.ArgumentParser();p.add_argument('--cli',required=True);p.add_argument('--fixture-dir',type=pathlib.Path,required=True);a=p.parse_args();out=a.fixture_dir/'cli.jsonl.gz';assert not out.exists();records=[];env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data'))
 def add(name,args,input=b'',files=None,created=None):
  files=files or {};created=created or []
  with tempfile.TemporaryDirectory()as tmp:
   for file,data in files.items():(pathlib.Path(tmp)/file).write_bytes(data)
   r=subprocess.run([a.cli,'--emulate',*args],input=input,capture_output=True,cwd=tmp,env=env)
   b64=lambda b:base64.b64encode(b).decode();records.append(dict(name=name,args=args,input=b64(input),files={k:b64(v)for k,v in files.items()},exitCode=r.returncode,stdout=b64(r.stdout),stderr=b64(r.stderr),created={k:b64((pathlib.Path(tmp)/k).read_bytes())for k in created if(pathlib.Path(tmp)/k).exists()}))
 for i,r in enumerate(rows(a.fixture_dir/'original.jsonl.gz')):
  if 'document'not in r:continue
  files={};args=['-f','json','-t','rst']
  if r.get('template')is not None:files['test.rst']=r['template'].encode();args+=['--template','test.rst']
  add('original/'+r['name'],args,json.dumps(r['document'],ensure_ascii=False).encode(),files)
 for source,golden,args in [('testsuite.native','writer.rst',['-s','--columns=80','--variable','pandoc-version=']),('tables.native','tables.rst',['--columns=80','--variable','pandoc-version=']),('lhs-test.native','lhs-test.rst',['--wrap=preserve','--markdown-headings=setext']),('lhs-test.native','lhs-test.rst+lhs',['--wrap=preserve','--markdown-headings=setext'])]:
  args=['-f','native','-t','rst+lhs'if golden.endswith('+lhs')else'rst',*args];add('old/'+golden,args,(ROOT/'upstream/pandoc/test'/source).read_bytes());assert base64.b64decode(records[-1]['stdout'])==(ROOT/'upstream/pandoc/test'/golden).read_bytes()
 document=dict(**{'pandoc-api-version':[1,23,1,2]},meta={'title':{'t':'MetaString','c':'Title'}},blocks=[{'t':'Header','c':[3,['id',[],[]],[{'t':'Str','c':'Head'}]]},{'t':'Para','c':[{'t':'Link','c':[['',[],[]],[{'t':'Strong','c':[{'t':'Str','c':'label'}]}],['url','title']]},{'t':'Space'},{'t':'Image','c':[['',[],[]],[{'t':'Str','c':'alt'}],['img','title']]},{'t':'RawInline','c':['html','<x>']},{'t':'Note','c':[{'t':'Para','c':[{'t':'Str','c':'note'}]}]}]}]);data=json.dumps(document,ensure_ascii=False).encode()
 for flag in ['--list-tables','--reference-links']:
  for suffix in ['', '=true','=false','=TRUE','=FALSE','=True','=False','=','=yes','=1','=Truex']:
   add('boolean/'+flag+suffix,['-f','json','-t','rst',flag+suffix],data)
 for args in [[],['-s'],['--wrap=none'],['--wrap=preserve'],['--columns=12'],['--reference-links'],['--reference-links=false'],['-s','--toc','--toc-depth=2','--number-sections'],['--trace'],['-t','rst+smart'],['-t','rst-smart'],['-t','rst+lhs'],['-t','rst+amuse']]:add('options/'+repr(args),['-f','json','-t','rst',*args],data)
 for template in ['$body$','$titleblock$\n$body$','$toc$:$toc-depth$:$number-sections$:$math$:$rawtex$\n$body$','$if(body)$$body$$endif$','$if(body)$','$for(x)$','$if$','$body$\n$endfor$']:
  for filename in ['custom.rst','custom\nname.rst','Ж😀.rst']:add('template/'+repr((template,filename)),['-f','json','-t','rst','--template',filename],data,{filename:template.encode()})
 for outputflag in [['-o','out.rst'],['--output','out.rst'],['--output=out.rst']]:add('inference/'+repr(outputflag),['-f','json',*outputflag],data,created=['out.rst'])
 add('input-inference',['-t','rst','input.native'],files={'input.native':(ROOT/'upstream/pandoc/test/lhs-test.native').read_bytes()})
 add('output-stdout',['-f','json','-t','rst','--output=-'],data)
 for text in ['<code lua>a</code>','a\n\nb','\\\\','<html>x</html>']:
  add('doku/'+repr(text),['-f','dokuwiki','-t','rst'],text.encode())
 add('doku-malformed-utf8',['--trace','-f','dokuwiki','-t','rst','bad\nfile.dokuwiki'],files={'bad\nfile.dokuwiki':b'\xff'})
 add('doku-binary-control',['-f','dokuwiki','-t','rst','bad\nfile.dokuwiki'],files={'bad\nfile.dokuwiki':b'PK\x03\x04\xff'})
 out.write_bytes(gzip.compress(''.join(json.dumps(r,ensure_ascii=False)+'\n'for r in records).encode(),mtime=0))
 import importlib.util
 spec=importlib.util.spec_from_file_location('rst_recipes',pathlib.Path(__file__).with_name('requests.py'));module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 start=len(records)
 for r in module.display_math_requests():add(r['name'],['-f','json','-t','rst','--wrap='+{'WrapAuto':'auto','WrapPreserve':'preserve'}[r['options']['wrap']]],json.dumps(r['document']).encode())
 (a.fixture_dir/'cli-display-math.jsonl.gz').write_bytes(gzip.compress(''.join(json.dumps(r,ensure_ascii=False)+'\n'for r in [dict(name=r['name'],args=r['args'],input=r['input'],files=r['files'],created=r['created'],exitCode=r['exitCode'],stdout=r['stdout'],stderr=r['stderr'])for r in records[start:]]).encode(),mtime=0))
 print(len(records),'complete original application observations; originalcliSHA',hashlib.sha256(pathlib.Path(a.cli).read_bytes()).hexdigest(),flush=True)
if __name__=='__main__':main()
