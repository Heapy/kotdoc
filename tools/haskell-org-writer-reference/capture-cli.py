"""Complete original application observations, with immutable expected bytes."""
import pathlib,json,base64,subprocess,tempfile,os,shlex
ROOT=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-org-writer');p=r/'cli-observations.jsonl';assert not p.exists();records=[]
cli='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc'
def add(name,args,input=b'',files=None,created=None,source=None):
 files=files or {};created=created or []
 with tempfile.TemporaryDirectory()as tmp:
  for file,data in files.items():(pathlib.Path(tmp)/file).write_bytes(data)
  proc=subprocess.run([cli,'--emulate',*args],input=input,capture_output=True,cwd=tmp,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
  b64=lambda b:base64.b64encode(b).decode();records.append(dict(name=name,args=args,input=b64(input),files={k:b64(v)for k,v in files.items()},created={k:b64((pathlib.Path(tmp)/k).read_bytes())for k in created if(pathlib.Path(tmp)/k).exists()},exitCode=proc.returncode,stdout=b64(proc.stdout),stderr=b64(proc.stderr),source=source))
for rec in map(json.loads,(r/'original8.jsonl').read_text().splitlines()):
 args=['-f','json','-t','org'];ext=rec['options']['extensions']
 if not ext:args[-1]='org-auto_identifiers-citations-special_strings-task_lists'
 add('original/'+rec['name'],args,json.dumps(rec['document']).encode())
for source,golden,flags in [('testsuite.native','writer.org',['-s']),('tables.native','tables.org',[])]:
 add('Old/'+golden,['-f','native','-t','org','--columns=80','--variable','pandoc-version=',*flags],(ROOT/'upstream/pandoc/test'/source).read_bytes());assert base64.b64decode(records[-1]['stdout'])==(ROOT/'upstream/pandoc/test'/golden).read_bytes()
doc=json.loads((r/'original8.jsonl').read_text().splitlines()[0])['document'];doc['meta']={'title':dict(t='MetaString',c='Title')};data=json.dumps(doc).encode()
for flags in[[],['-s'],['--wrap=none'],['--wrap=preserve'],['--columns=4'],['-t','org+smart'],['-t','org+smart_quotes'],['-t','org-special_strings'],['-t','org+amuse'],['--trace'],['--reference-links'],['--list-tables']]:add('options/'+repr(flags),['-f','json','-t','org',*flags],data)
for text in['$body$','$title$\n$math$:$options$:$option-special-strings$\n$body$','$if(body)$','$for(x)$','$body$\n$endfor$']:
 for filename in['custom.org','custom\nname.org','Ж😀.org']:add('template/'+repr((text,filename)),['-f','json','-t','org','--template',filename],data,{filename:text.encode()})
for flag in[['-o','out.org'],['--output','out.org'],['--output=out.org']]:add('inference/'+repr(flag),['-f','json',*flag],data,created=['out.org'])
add('input-inference',['-t','org','input.native'],files={'input.native':(ROOT/'upstream/pandoc/test/testsuite.native').read_bytes()})
for fmt,text in[('dokuwiki','====== H ======\n\nbody'),('muse','* H\n\nbody'),('rst','H\n=\n\nbody')]:add('consumer/'+fmt,['-f',fmt,'-t','org'],text.encode())
for q in map(json.loads,(r/'writer-observations.jsonl').read_text().splitlines()):
 if q['request']['name'].startswith('raw/')and "'unsupported'"in q['request']['name']:add('diagnostics/'+q['request']['name'],['-f','json','-t','org'],json.dumps(q['request']['document']).encode())
for rec in json.loads((r/'commands-inventory.json').read_text()):
 args=shlex.split(rec['command'])[1:]
 if '-f'in args and args[args.index('-f')+1]in['native','rst']:
  add('command/'+rec['id'],args,rec['stdin'].encode(),source=rec)
  actual=base64.b64decode(records[-1]['stdout']).decode();assert actual==rec['expected_stdout']and records[-1]['exitCode']==rec['expected_exit_status'],rec['id']
p.write_text(''.join(json.dumps(q,ensure_ascii=False,separators=(',',':'))+'\n'for q in records));print(len(records),'complete original App observations',flush=True)
