#!/usr/bin/env python3
"""Finite callback and rollback expectations from original PandocMonad instance."""
import gzip,json,pathlib,subprocess
HERE=pathlib.Path(__file__).resolve().parent
OUT=HERE/'fixtures/effects.jsonl.gz'
if OUT.exists():raise SystemExit('refusing to replace independent expectations')
ext=['auto_identifiers','citations','special_strings','task_lists']
requests=[]
def add(name,text,files=[]):requests.append(dict(name=name,input=text,extensions=ext,files=files))
for command in ['\\today','\\alpha{}','\\textbf{x}','\\unknown{x}','\\input{a}','\\input{missing}','\\begin{equation}x\\end{equation}','\\begin{math}x\\end{math}']:
 for setting in ['t','nil','verbatim']:
  add(f'latex/{command}/{setting}',f'#+OPTIONS: tex:{setting}\n{command}\n',[dict(path='a',bytes=list(b'content'))])
for text in ['#+INCLUDE: "a"\n','#+INCLUDE: "a" example\n','#+INCLUDE: "a" src python\n','#+INCLUDE: "a" export html\n','#+INCLUDE: "a" :lines "1-2"\n','#+INCLUDE: "a" :minlevel 2\n','#+INCLUDE: "missing"\n','#+INCLUDE: "bad"\n','#+OPTIONS: unknown:t\n','#+OPTIONS: unknown:t\n#+INCLUDE: "missing"\n','* A\n:PROPERTIES:\n:CUSTOM_ID: x\n:END:\n* B\n:PROPERTIES:\n:CUSTOM_ID: x\n:END:\n','* H :noexport:\n#+INCLUDE: "missing"\n','#+begin_comment\n#+INCLUDE: "missing"\n#+end_comment\n','#+INCLUDE: "loop"\n']:
 add('effects/'+text,text,[dict(path='a',bytes=list(b'* A\ntext\n** B\n')),dict(path='bad',bytes=[255]),dict(path='loop',bytes=list(b'#+INCLUDE: "loop"\n'))])
raw=''.join(json.dumps(q,ensure_ascii=True,separators=(',',':'))+'\n'for q in requests).encode()
result=subprocess.run(['/tmp/kotdoc-org-reader/effect-oracle'],input=raw,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
pathlib.Path('/tmp/kotdoc-org-reader/effects-oracle.stderr').write_bytes(result.stderr)
answers=[json.loads(line)for line in result.stdout.splitlines()];assert len(answers)==len(requests)
with gzip.GzipFile(filename=str(OUT),mode='wb',mtime=0)as out:
 for request,answer in zip(requests,answers):assert request==answer['request'];out.write((json.dumps(answer,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'captured {len(answers)} original runtime observations')
