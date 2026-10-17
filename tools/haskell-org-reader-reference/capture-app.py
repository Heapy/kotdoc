#!/usr/bin/env python3
"""Exact original full App bytes; source library and App expectations are distinct."""
import base64,gzip,json,os,pathlib,shlex,subprocess,tempfile,hashlib
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];OUT=HERE/'fixtures/app.jsonl.gz'
if OUT.exists():raise SystemExit('refusing to replace original App expectations')
BINARY=pathlib.Path('/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc')
b64=lambda b:base64.b64encode(b).decode();decode=base64.b64decode
records=[]
def capture(name,args,raw=b'',files={},source=None,cwd=None):
 with tempfile.TemporaryDirectory()as tmp:
  directory=pathlib.Path(cwd or tmp)
  for filename,data in files.items():
   path=directory/filename;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
  p=subprocess.run([str(BINARY),'--emulate',*args],input=raw,capture_output=True,cwd=directory,env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data'),SOURCE_DATE_EPOCH='0',LANG='en_US.UTF-8'))
  record=dict(name=name,args=args,input=b64(raw),files={k:b64(v)for k,v in files.items()},exitCode=p.returncode,stdout=b64(p.stdout),stderr=b64(p.stderr),source=source,cwd='test'if cwd else'temporary')
  records.append(record)
  print(name,p.returncode,flush=True)
DEFAULT={'auto_identifiers','citations','special_strings','task_lists'}
for row in map(json.loads,gzip.open(HERE/'fixtures/original312.jsonl.gz','rt')):
 q=row['request'];extensions=set(q['extensions']);format_='org'+''.join('+'+e for e in sorted(extensions-DEFAULT))+''.join('-'+e for e in sorted(DEFAULT-extensions))
 capture('original312/'+row['name'],['-f',format_,'-t','json','-s'],q['input'].encode(),{f['path']:bytes(f['bytes'])for f in q.get('files',[])},source=dict(kind='original-reader-test',name=row['name']))
for file in ['cli-input.json','cli-warnings.json']:
 for i,old in enumerate(json.load(open(ROOT/'core/src/test/resources/muse-reader'/file))):
  args=[a.replace('muse','org')for a in old['args']];files={k.replace('muse','org'):decode(v)for k,v in old['files'].items()}
  capture(file+'/'+str(i),args,decode(old['input']),files,source=dict(kind='boundary-input',path='core/src/test/resources/muse-reader/'+file,index=i))
for fmt in ['org','org-auto_identifiers','org+ascii_identifiers','org+gfm_auto_identifiers','org+smart','org+old_dashes','org+fancy_lists','org+raw_html','org+tex_math_dollars']:
 capture('extension/'+fmt,['-f',fmt,'-t','json'],b'* HEADING\n\nbody')
for args in [[],['--preserve-tabs'],['--tab-stop=1'],['--tab-stop=8'],['--tab-stop=0'],['--tab-stop=-1'],['--trace'],['--toc'],['-s'],['--default-image-extension=png']]:
 capture('option/'+repr(args),['-f','org','-t','json',*args],b'* Heading\n\n\t- item\n')
capture('Old/org-select-tags',['-r','org','-w','native','-s'],(ROOT/'upstream/pandoc/test/org-select-tags.org').read_bytes(),source=dict(kind='Old',source='org-select-tags.org',golden='org-select-tags.native'))
assert decode(records[-1]['stdout'])==(ROOT/'upstream/pandoc/test/org-select-tags.native').read_bytes()
capture('default-reader-inference',['input.org','-t','json'],files={'input.org':b'* Heading\n\nbody'})
capture('default-writer',['-f','org'],b'* Heading\n\nbody')
capture('roundtrip-org',['-f','org','-t','org'],b'* Heading\n\nbody')
commands=json.load(open('/tmp/kotdoc-org-reader/canonical-commands58.json'))
command_records=[]
for row in commands:
 args=shlex.split(row['command'])[1:]
 # All58 commands are simple argv invocations; no shell pipeline or mutation.
 assert not any(a in ['|','&&',';']for a in args)
 name='command/'+pathlib.Path(row['file']).name+'#'+str(row['number'])
 capture(name,args,row['input'].encode(),source=dict(kind='original-command',**row),cwd=ROOT/'upstream/pandoc/test')
 r=records[-1];actual=''.join('2> '+line+'\n'for line in decode(r['stderr']).decode().splitlines())+decode(r['stdout']).decode()
 if r['exitCode']:actual+='=> '+str(r['exitCode'])+'\n'
 r['originalNormMatches']=actual.replace('\r','')==row['expected']
 command_records.append(dict(name=name,command=row['command'],originalNormMatches=r['originalNormMatches']))
with gzip.GzipFile(filename=str(OUT),mode='wb',mtime=0)as out:
 for row in records:out.write((json.dumps(row,ensure_ascii=True,separators=(',',':'))+'\n').encode())
(HERE/'fixtures/command-app-results.json').write_text(json.dumps(command_records,ensure_ascii=False,indent=2)+'\n')
print('TOTAL',len(records),'original App observations; command norms',sum(r['originalNormMatches']for r in command_records),'/',len(command_records),flush=True)
