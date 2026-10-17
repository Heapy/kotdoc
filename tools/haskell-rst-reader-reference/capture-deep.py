#!/usr/bin/env python3
import json,pathlib,subprocess
r=pathlib.Path('/tmp/kotdoc-rst-reader');qs=[]
def add(name,text,**kw):qs.append(dict(name='deep/'+name,input=text,**kw))
add('paragraph-10000','word '*10000+'\n')
add('code-65536','.. code:: text\n\n   '+'x'*65536+'\n')
add('lines-4096','| x\n'*4096)
add('grid-1024','+---+---+\n'+('| a | b |\n+---+---+\n'*1024)+'\n')
add('references-4096',''.join(f'.. _k{i}: k{i+1}_\n'for i in range(4096))+'.. _k4096: https://example.com\n\nk0_\n')
add('substitutions-1024',''.join(f'.. |k{i}| replace:: |k{i+1}|\n\n'for i in range(1024))+'.. |k1024| replace:: end\n\ntext |k0| here\n')
add('quotes-128',''.join('  '*i+'para\n\n'for i in range(128)))
add('containers-256',''.join('   '*i+'.. container::\n\n'for i in range(256))+'   '*256+'body\n')
add('note-4096','x [1]_\n\n.. [1]\n\n'+''.join('   para\n\n'for i in range(4096)))
path=r/'deep-observations.jsonl';assert not path.exists()
p=subprocess.run([str(r/'full-oracle'),'+RTS','-K128m','-RTS'],input=('\n'.join(json.dumps(q)for q in qs)+'\n').encode(),capture_output=True,check=True)
rs=[json.loads(l)for l in p.stdout.splitlines()];assert len(rs)==len(qs)
path.write_text(''.join(json.dumps({'request':q,'expected':o},ensure_ascii=False,separators=(',',':'))+'\n'for q,o in zip(qs,rs)));print(len(rs),'independent deep reader inputs observed')
