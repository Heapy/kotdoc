#!/usr/bin/env python3
import pathlib,json,gzip,subprocess,itertools
HERE=pathlib.Path(__file__).resolve().parent;TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer');qs=[]
chars='{+`*#_<>[]\\|ax\n\t'+'ſ'+'\U0001e6c0'
for table in [False,True]:
 for count in [0,1,2,3]:
  for ix,cs in enumerate(itertools.product(chars,repeat=count)):
   qs.append(dict(name=f'escape/{table}/{count}/{ix}',operation='escape',input=''.join(cs),table=table))
for prefix,space,tail in itertools.product(['1.','A.','I.','II.','V.','X.','(1)','#.','@x.','123456789.','[x]','[]','[','x'],['',' ','  ','\t','\n','\u00a0','\u2028'],['','x','[]']):qs.append(dict(name='needs/'+repr((prefix,space,tail)),operation='needs',input=prefix+space+tail))
raw=subprocess.run([str(TMP/'scalar-oracle')],input=b''.join((json.dumps(q)+'\n').encode()for q in qs),capture_output=True,check=True).stdout
answers=[json.loads(x)for x in raw.splitlines()];assert len(qs)==len(answers)
p=HERE/'fixtures/scalars.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(dict(request=q,expected=a))+'\n').encode()for q,a in zip(qs,answers)),mtime=0));print(len(qs),'original scalar helper observations captured')
