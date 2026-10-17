#!/usr/bin/env python3
import pathlib,json,gzip,subprocess,itertools
HERE=pathlib.Path(__file__).resolve().parent;TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer')
def node(kind,**kw):return dict(kind=kind,**kw)
leaves=[node('empty'),node('text',text='x'),node('text',text=''),node('text',text='a\nb'),node('space'),node('cr'),node('newline'),node('blank',count=0),node('blank',count=1),node('blank',count=-1)]
for kind in ['prefix','flush','before','link']:
 for d in [node('empty'),node('space'),node('text',text='x')]:leaves.append(node(kind,doc=d,**({'prefix':'  '}if kind=='prefix'else{})))
qs=[]
for count in [1,2,3]:
 for ix,parts in enumerate(itertools.product(leaves,repeat=count)):
  # Both left and right associativity must yield the exact source structure.
  left=parts[0]
  for d in parts[1:]:left=node('concat',left=left,right=d)
  qs.append(dict(name=f'chomp/{count}/{ix}',doc=left))
raw=subprocess.run([str(TMP/'chomp-oracle')],input=b''.join((json.dumps(q)+'\n').encode()for q in qs),capture_output=True,check=True).stdout;answers=[json.loads(x)for x in raw.splitlines()];assert len(qs)==len(answers)
p=HERE/'fixtures/chomp.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(dict(request=q,expected=a))+'\n').encode()for q,a in zip(qs,answers)),mtime=0));print(len(qs),'original chomp structure/render observations captured')
