import pathlib,json,subprocess,base64
r=pathlib.Path('/tmp/kotdoc-rst-reader');qs=[]
def add(name,url,**kw):qs.append(dict(name='fetch/'+name,input='.. csv-table::\n   :file: '+url+'\n\n',fetchProfile=True,**kw))
body=list(b'a,b\nc,d\n')
for url in ['data:text/csv,a%2Cb%0Ac%2Cd%0A','data:text/csv;base64,YSxiCmMsZAo=','DATA:text/csv;base64,YSxiCmMsZAo=','data:;base64,YSxiCmMsZAo=','data:text/csv;charset=utf-8;base64,YSxiCmMsZAo=','data:text/csv;base64,@@YSxiCmMsZAo=','data:text/csv;BASE64,a,b','data:text/csv,a%FFb','data:text/csv,a%00b','data:text/csv,a%2','data:,','data:bad']:
 if '%FF' not in url:add(str(len(qs)),url)
for url in ['a.csv','./a.csv','././a.csv','dir/a.csv','dir//a.csv','a%20b.csv','file:a.csv','file:./a.csv','file:/a.csv','FILE:/a.csv','file:a%20b.csv','C:/a.csv','x:a.csv','//example.com/a.csv','//?foo','http://example.com/a.csv','https://u:password@example.com/a.csv','a\\b.csv']:
 for paths in [['.'],['missing','assets'],[]]:add(str(len(qs)),url,resourcePath=paths,files=[['a.csv',body],['assets/a.csv',body],['a b.csv',body],['/a.csv',body],['dir/a.csv',body],['a/b.csv',body]])
for url in ['same.csv','./same.csv','data:text/csv,a,b','https://example.com/a.csv']:
 add(str(len(qs)),url,media=[[url,body]])
for source in ['https://example.com/dir/file.rst?x#frag','http://example.com/a/../b.rst']:
 for url in ['a.csv','../a.csv','/a.csv','//other.test/x','data:text/csv,a%2Cb%0Ac%2Cd%0A','file:a.csv','x:a.csv']:
  add(str(len(qs)),url,sourceURL=source)
p=r/'fetch-observations.jsonl';assert not p.exists();proc=subprocess.run([str(r/'full-oracle')],input=''.join(json.dumps(q)+'\n'for q in qs).encode(),capture_output=True);(r/'fetch-native.stderr').write_bytes(proc.stderr);proc.check_returncode();rs=[json.loads(x)for x in proc.stdout.splitlines()];assert len(rs)==len(qs);p.write_text(''.join(json.dumps(dict(request=q,expected=e),ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(qs,rs)));print(len(qs),'original runtime fetch observations')
