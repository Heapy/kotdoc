#!/usr/bin/env python3
"""Original reader source/option/trace boundary observations, immutable."""
import gzip,json,pathlib,subprocess
H=pathlib.Path(__file__).resolve().parent;out=H/'fixtures/boundary.jsonl.gz'
if out.exists():raise SystemExit('refusing to replace independent expectations')
defaults=json.loads(subprocess.check_output(['/tmp/kotdoc-org-reader/oracle','--defaults']))
base=['auto_identifiers','citations','special_strings','task_lists'];qs=[]
def add(name,input='',**kw):qs.append(dict(name=name,input=input,extensions=base,**kw))
for n in [1,2,4,8]:
 for text in ['\t* heading\n','\tword\n\t\tcontinued\n','#+begin_src x\n\tcode\n#+end_src\n','- item\n\tcontinued\n']:
  add(f'tab/{n}/{text}',text,options={**defaults,'tab-stop':n})
for trace in [False,True]:
 for text in ['#+OPTIONS: unknown:t\n* A\n:PROPERTIES:\n:ID: dup\n:END:\n* B\n:PROPERTIES:\n:ID: dup\n:END:\n','#+include: "missing"\n#+OPTIONS: unknown:t\n','#+include: "loop"\n']:
  add(f'trace/{trace}/{text}',text,trace=trace,files=[dict(path='loop',bytes=list(b'#+include: "loop"\n'))])
for file in ['', 'a.org','Ж😀\nfile.org']:
 for line,col in [(1,1),(2147483648,4294967296),(9223372036854775806,1)]:
  for text in ['* Head\n:PROPERTIES:\n:ID: same\n:END:\n* Head\n:PROPERTIES:\n:ID: same\n:END:\n','#+include: "missing"\n','#+begin_quote\ntext\n']:
   add(f'position/{file}/{line}/{col}/{text}',chunks=[[file,line,col,text]])
raw=''.join(json.dumps(q,separators=(',',':'))+'\n'for q in qs).encode();r=subprocess.run(['/tmp/kotdoc-org-reader/oracle'],input=raw,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
answers=[json.loads(x)for x in r.stdout.splitlines()];assert len(qs)==len(answers)
with gzip.GzipFile(filename=str(out),mode='wb',mtime=0)as f:
 for q,a in zip(qs,answers):assert q['name']==a.pop('name');f.write((json.dumps(dict(request=q,expected=a),separators=(',',':'))+'\n').encode())
print('captured',len(qs),'independent boundary observations')
