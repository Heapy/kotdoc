#!/usr/bin/env python3
"""Immutable original App bytes. No Kotlin or generated expected output."""
import pathlib,json,gzip,base64,subprocess,tempfile,os,shlex
from requests import n,s,attr,doc,para
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
EXE='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc'
rows=[];pending=[]
def b64(b):return base64.b64encode(b).decode()
def add(name,args,input=b'',files=None,created=None,source=None,destination=None):
 with tempfile.TemporaryDirectory()as tmp:
  for filename,data in (files or {}).items():pathlib.Path(tmp,filename).write_bytes(data)
  p=subprocess.run([EXE,'--emulate',*args],input=input,capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
  r=dict(name=name,args=args,input=b64(input),files={k:b64(v)for k,v in(files or {}).items()},created={k:b64(pathlib.Path(tmp,k).read_bytes())for k in(created or[])if pathlib.Path(tmp,k).exists()},exitCode=p.returncode,stdout=b64(p.stdout),stderr=b64(p.stderr),source=source)
  (rows if destination is None else destination).append(r);return r
original=[json.loads(x)for x in gzip.decompress((HERE/'fixtures/original12.jsonl.gz').read_bytes()).splitlines()]
for r in original:
 for fmt in (['asciidoc_legacy']if r['legacy']else['asciidoc','asciidoctor']):add('original/'+fmt+'/'+r['name'],['-f','json','-t',fmt],json.dumps(r['document']).encode())
for fmt in ['asciidoc','asciidoctor','asciidoc_legacy']:
 for filename,golden,flags in [('testsuite.native','writer.'+('asciidoc_legacy'if fmt=='asciidoc_legacy'else'asciidoc'),['-s']),('tables.native','tables.'+('asciidoc_legacy'if fmt=='asciidoc_legacy'else'asciidoc'),[])]:
  args=['-f','native','-t',fmt,'--columns=80','--variable','pandoc-version=',*flags]
  r=add('golden-App-without-global-quiet/'+fmt+'/'+filename,args,(ROOT/'upstream/pandoc/test'/filename).read_bytes());assert base64.b64decode(r['stdout'])==(ROOT/'upstream/pandoc/test'/golden).read_bytes()
  if fmt!='asciidoctor':add('Old-original-global-quiet/'+fmt+'/'+filename,[*args,'--quiet'],(ROOT/'upstream/pandoc/test'/filename).read_bytes(),destination=pending)
d=doc([n('Header',[1,attr('id'),[s('H')]]),para([n('Math',[n('InlineMath'),'x']),n('SoftBreak'),s('a,+|')]),n('RawBlock',['unsupported','raw'])],{'title':n('MetaString','Title'),'author':n('MetaString','Author')});data=json.dumps(d).encode()
for fmt in ['asciidoc','asciidoctor','asciidoc_legacy']:
 for flags in[[],['-s'],['--wrap=none'],['--wrap=preserve'],['--columns=4'],['--toc'],['--toc-depth=2'],['-N'],['--dpi=72'],['--trace'],['--standalone=false'],['--reference-links'],['--list-tables']]:add('options/'+fmt+'/'+repr(flags),['-f','json','-t',fmt,*flags],data)
 for ext in ['-auto_identifiers','+ascii_identifiers','+gfm_auto_identifiers','+amuse','+nonexistent']:
  add('extensions/'+fmt+'/'+ext,['-f','json','-t',fmt+ext],data)
 for template in ['$body$','$titleblock$:$toc$:$math$\n$title$\n$body$','$if(body)$','$for(x)$','$body$\n$endfor$']:
  for filename in ['custom.asciidoc','bad\nname.adoc','Ж😀.adoc']:add('template/'+fmt+'/'+repr((filename,template)),['-f','json','-t',fmt,'--template',filename],data,{filename:template.encode()})
for out in ['out.adoc','out.asciidoc']:
 for opt in [['-o',out],['--output',out],['--output='+out]]:add('inference/'+repr(opt),['-f','json',*opt],data,created=[out])
for fmt in ['asciidoc','asciidoctor','asciidoc_legacy']:add('input-inference/'+fmt,['-t',fmt,'input.native'],files={'input.native':b'[Para [Str "input"]]'})
for reader,text in [('dokuwiki','====== H ======\n\nbody'),('muse','* H\n\nbody'),('org','* H\n\nbody'),('rst','H\n=\n\nbody')]:
 for trace in [False,True]:add('consumer/'+reader+'/'+str(trace),['-f',reader,'-t','asciidoc',*(['--trace']if trace else[])],text.encode())
for input in [b'[Para [Str "\\xD800"]]',b'[Para [Str "\\xDFFF"]]',b'[Para [Str "\\x10FFFF"]]']:
 add('Text/native-pack-boundary/'+repr(input),['-f','native','-t','asciidoc'],input)
# Accepted source-profile delta inputs only; expected bytes are newly captured from original AsciiDoc.
profiles=[json.loads(x)for x in gzip.decompress((ROOT/'tools/haskell-rst-writer-reference/work-in-progress/fixtures/identifier-profiles.jsonl.gz').read_bytes()).splitlines()]
for r in profiles:
 for fmt in ['asciidoc','asciidoctor','asciidoc_legacy']:
  ext=r['request']['options']['extensions'];format=fmt+''.join('+'+e for e in ext if e!='auto_identifiers')
  add('profile27/'+fmt+'/'+r['request']['name'],['-f','json','-t',format],json.dumps(r['request']['document']).encode())
for r in json.loads((HERE/'fixtures/commands-inventory.json').read_text()):
 args=shlex.split(r['command'])[1:];available='-f'in args and args[args.index('-f')+1]in['native','org']
 a=add('command/'+r['file']+'#'+str(r['number']),args,r['input'].encode(),source=r,destination=None if available else pending)
 if available:assert a['exitCode']==0 and base64.b64decode(a['stdout']).decode()==r['expected']
for name,rs in [('cli',rows),('cli-pending',pending)]:
 p=HERE/'fixtures'/(name+'.jsonl.gz');assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(r,ensure_ascii=False)+'\n').encode()for r in rs),mtime=0));print(name,len(rs),flush=True)
