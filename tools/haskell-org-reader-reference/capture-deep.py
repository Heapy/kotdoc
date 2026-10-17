#!/usr/bin/env python3
"""Original-only finite resource probes; no Kotlin-derived expected output."""
import gzip,json,pathlib,subprocess,sys
sys.setrecursionlimit(100000)
HERE=pathlib.Path(__file__).resolve().parent;OUT=HERE/'fixtures/deep.jsonl.gz'
if OUT.exists():raise SystemExit('refusing to replace independent expected values')
ext=['auto_identifiers','citations','special_strings','task_lists']
requests=[]
def add(name,text):requests.append(dict(name=name,input=text,extensions=ext))
for n in [1024,4096]:
 add(f'author-lines/{n}','#+author: x\n'*n)
 add(f'ignored-author-lines/{n}','#+OPTIONS: author:nil\n'+'#+author: x\n'*n)
 add(f'headlines-breadth/{n}','* heading\nbody\n'*n)
 add(f'table-rows/{n}','| header |\n|--------|\n'+'| cell |\n'*n)
 add(f'inline-balanced/{n}','x_{'+'{'*n+'x'+'}'*n+'}')
 add(f'footnotes/{n}',' '.join(f'[fn:{i}]'for i in range(n))+'\n\n'+''.join(f'[fn:{i}] text\n'for i in range(n)))
for n in [256,1024]:
 add(f'headlines-depth/{n}',''.join('*'*i+' heading\ntext\n'for i in range(1,n+1)))
raw=''.join(json.dumps(q,separators=(',',':'))+'\n'for q in requests).encode();pathlib.Path('/tmp/kotdoc-org-reader/deep-requests.jsonl').write_bytes(raw)
result=subprocess.run(['/tmp/kotdoc-org-reader/oracle','+RTS','-K64m','-RTS'],input=raw,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
pathlib.Path('/tmp/kotdoc-org-reader/deep-oracle.stderr').write_bytes(result.stderr)
answers=[json.loads(line)for line in result.stdout.splitlines()];assert len(answers)==len(requests)
with gzip.GzipFile(filename=str(OUT),mode='wb',mtime=0)as out:
 for q,a in zip(requests,answers):assert q['name']==a.pop('name');out.write((json.dumps({'request':q,'expected':a},separators=(',',':'))+'\n').encode())
print(f'captured {len(answers)} original finite deep observations')
