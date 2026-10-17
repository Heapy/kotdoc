#!/usr/bin/env python3
import json,pathlib,re,shlex,subprocess
root=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-rst-reader');defaults=json.loads((r/'default-options.json').read_text());qs=[]
def native(q):
 p=subprocess.run([str(r/'full-oracle')],input=(json.dumps(q,ensure_ascii=False)+'\n').encode(),capture_output=True,check=True);return json.loads(p.stdout)
for row in json.loads((r/'rst-input-commands-inventory.json').read_text()):
 args=shlex.split(row['command']);standalone='-s'in args or'--standalone'in args;columns=72
 for i,arg in enumerate(args):
  if arg=='--columns':columns=int(args[i+1])
  if arg.startswith('--columns='):columns=int(arg.split('=',1)[1])
 q=dict(name=row['id'],input=row['stdin'],extensions=['auto_identifiers'],options=dict(defaults,standalone=standalone,columns=columns))
 q['input']=native(dict(q,kind='app-tabs'))['result']
 names=re.findall(r'^\s*(?:\.\. include::\s*|:file:\s*)(.*?)\s*$',q['input'],re.M);files=[]
 for name in names:
  path=root/'upstream/pandoc/test'/name
  if path.is_file():files.append([name,list(path.read_bytes())])
 if files:q['files']=files
 qs.append(q)
p=subprocess.run([str(r/'full-oracle')],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in qs)+'\n').encode(),capture_output=True,check=True)
rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(qs)
path=r/'command-reader85.jsonl';assert not path.exists();path.write_text(''.join(json.dumps({'request':q,'expected':e},ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(qs,rs)));print(len(rs),'original reader observations for complete command inputs; distinct from full command outputs')
