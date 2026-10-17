#!/usr/bin/env python3
import pathlib,json,gzip,subprocess,itertools
from requests import n,s,attr,doc,para
HERE=pathlib.Path(__file__).resolve().parent;TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer');qs=[]
for levels,cl,outerid,innerid,kvs in itertools.product([(1,2,3,2,4,1),(2,1),(0,-1,3),(99,100),(1,1)],[[],['section'],['column'],['columns'],['fragment'],['cell'],['other']],['','outer'],['','inner'],[[],[['number','9'],['epub:type','x'],['role','note'],['role','heading']]]):
 bs=[n('Header',[level,attr(innerid,['unnumbered']if index==1 else[],kvs),[s('title')]])for index,level in enumerate(levels)]
 bs.insert(1,para([s('body')]))
 qs.append(dict(name='sections/'+repr((levels,cl,outerid,innerid,kvs)),document=doc([n('Div',[attr(outerid,cl,[['role','tab'],['other','value']]),bs])]+[n('Header',[1,attr('last'),[s('end')]])])))
# Verbatim Tests.Shared explicit idempotence input, independent Builder-free JSON representation.
qs.append(dict(name='Tests.Shared/makeSections is idempotent for test case',document=doc([n('Header',[lev,attr(),[s(title)]])for lev,title in[(1,'H1'),(2,'H2'),(3,'H3'),(2,'H2a'),(4,'H4'),(1,'H1a')]])))
raw=subprocess.run([str(TMP/'sections-oracle')],input=b''.join((json.dumps(q)+'\n').encode()for q in qs),capture_output=True,check=True).stdout
answers=[json.loads(x)for x in raw.splitlines()];assert len(qs)==len(answers)
p=HERE/'fixtures/sections.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(dict(request=q,expected=a))+'\n').encode()for q,a in zip(qs,answers)),mtime=0));print(len(qs),'original sections observations captured')
