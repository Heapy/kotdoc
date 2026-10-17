#!/usr/bin/env python3
import pathlib,json,gzip,subprocess,itertools
from requests import n,s,attr
HERE=pathlib.Path(__file__).resolve().parent;TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer');mappings=json.loads((HERE/'fixtures/lower-mappings.json').read_text());qs=[]
extensions=[[],['gfm_auto_identifiers'],['ascii_identifiers'],['gfm_auto_identifiers','ascii_identifiers']]
for point,_ in mappings:
 for exts in extensions:qs.append(dict(name='identifier/'+str(point)+'/'+repr(exts),inlines=[s(chr(point)+' X')],extensions=exts,used=[]))
for xs,exts,used in itertools.product([[s('x')],[s('123')],[s('a b\u00a0c')],[n('Emph',[s('Ab')])],[n('Span',[attr('', ['emoji'],[['data-emoji','smile']]),[s('😀')]])],[s('☃ 😀')],[n('Code',[attr(),'AB'])],[n('Image',[attr(),[s('ALT')],['x','ignored']])],[n('Note',[]),s('x')]],extensions,[[],['x','x-1','x-2','section','section-1']]):qs.append(dict(name='identifier/inline/'+str(len(qs)),inlines=xs,extensions=exts,used=used))
raw=subprocess.run([str(TMP/'identifier-oracle')],input=b''.join((json.dumps(q)+'\n').encode()for q in qs),capture_output=True,check=True).stdout;answers=[json.loads(x)for x in raw.splitlines()];assert len(qs)==len(answers)
p=HERE/'fixtures/identifiers.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(dict(request=q,expected=a))+'\n').encode()for q,a in zip(qs,answers)),mtime=0));print(len(qs),'original identifier profile/flags/inline observations captured')
