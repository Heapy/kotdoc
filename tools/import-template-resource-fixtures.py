#!/usr/bin/env python3
"""Observe pinned original Pandoc template loaders, aliases, UTF-8 and wrappers."""
import argparse,collections,gzip,hashlib,itertools,json,os,random,re,shutil,subprocess,tempfile,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/templates/resources.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--paths-oracle',default=os.environ.get('KOTDOC_TEMPLATE_PATHS_REFERENCE'));p.add_argument('--pandoc',default=os.environ.get('KOTDOC_PANDOC_REFERENCE','pandoc'));a=p.parse_args()
sources=['tools/import-template-resource-fixtures.py','tools/import-template-data.py','tools/template-resources-oracle.lua','tools/template-original-tests.lua',
'upstream/pandoc/src/Text/Pandoc/Templates.hs','upstream/pandoc/src/Text/Pandoc/Data.hs',
'upstream/pandoc/src/Text/Pandoc/Data/BakedIn.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs',
'upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/Error.hs',
'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Template.hs',
'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Pandoc.hs',
'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-template.lua',
'upstream/pandoc/pandoc-lua-engine/test/lua/module/partial.test']
for directory in ['upstream/pandoc/data/templates','tools/haskell-template-resource-reference','vendor/filepath-1.5.4.0']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/directory).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
hashes={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
# Verify unchanged makeCanonical definitions used by the native path adapter.
shared=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text()
body='makeCanonical :: FilePath -> FilePath\n'+shared.split('makeCanonical :: FilePath -> FilePath\n',1)[1].split('\n-- | Remove intermediate',1)[0].rstrip()+'\n'
for module in ['CanonicalPosix','CanonicalWindows']:
 assert (root/f'tools/haskell-template-resource-reference/{module}.hs').read_text().endswith(body)
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert m['bytePairs']==65536
 assert [c['items'] for c in cs if c['op']=='decode-batch']==[[bytes([first,second]).hex() for second in range(256)] for first in range(256)]
 assert len(m['originalTests'])==16
 assert {c['writer'] for c in cs if c['op']=='default' and not c.get('user')} >= {f.name.removeprefix('default.') for f in (root/'upstream/pandoc/data/templates').glob('default.*')}
 print(f'Verified {len(cs)} template resource cases, all 65536 byte pairs and {len(hashes)} source files');raise SystemExit
assert a.paths_oracle,'--paths-oracle required'
assert subprocess.check_output([a.pandoc,'--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
provenance=json.loads((root/'tools/haskell-template-resource-reference/PANDOC-REFERENCE.json').read_text())
assert hashlib.sha256(Path(shutil.which(a.pandoc) or a.pandoc).read_bytes()).hexdigest()==provenance['executableSHA256'],'Use the retained official HTTP-capable oracle build'
original=subprocess.run([a.pandoc,'-f','native','-t',str(root/'tools/template-original-tests.lua')],input='[]',text=True,capture_output=True,
 cwd=root/'upstream/pandoc/pandoc-lua-engine/test',env={**os.environ,'KOTDOC_ORIGINAL_TEMPLATE_TEST':str(root/'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-template.lua')})
assert original.returncode==0,original.stderr
original_tests=json.loads(original.stdout)
assert len(original_tests)==len(re.findall(r'\btest\(', (root/'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-template.lua').read_text())),original_tests
cs=[]
def add(op,**kw):cs.append(dict(op=op,**{k:v for k,v in kw.items() if v is not None}))
setup={
 'resources/local.tpl':'LOCAL $title$\r\n',
 'resources/dir/main.tpl':'$sub/child()$\n',
 'resources/dir/sub/child.tpl':'CHILD $last()$\n',
 'resources/dir/last.tpl':'ROOT\n',
 'resources/dir/sub/last.tpl':'WRONG\n',
 'resources/dir/partial.test':(root/'upstream/pandoc/pandoc-lua-engine/test/lua/module/partial.test').read_text(),
 'resources/partial':'RESOURCE\n',
 'resources/default.html5':'LOCAL HTML\n',
 'resources/second/local.tpl':'SECOND\n',
 'user/templates/local.tpl':'USER LOCAL\n',
 'user/templates/fallback.tpl':'USER FALLBACK\r\n',
 'user/templates/partial':'USER PARTIAL\n',
 'user/templates/default.html5':'USER HTML $title$\r\n',
 'user/templates/default.commonmark':'$partial()$\n',
 'user/templates/partial.gfm':'GFM\n',
 'user/templates/partial.commonmark':'COMMONMARK\n',
 'user/templates/loop.tpl':'$loop()$\n',
 'user/templates/bad.tpl':'$x/nope$\n',
 'user/templates/default.markdown':'$if(x)$bad',
 'user/templates/default.native':'IGNORED\n',
}
setup={name:text.encode().hex() for name,text in setup.items()}
setup['resources/invalid.tpl']='ff';setup['resources/bom.tpl']='efbbbf610d0a'
writers=sorted({f.name.removeprefix('default.') for f in (root/'upstream/pandoc/data/templates').glob('default.*')} |
 set('native csljson json xml fb2 pptx ipynb asciidoctor asciidoc_legacy docx odt fodt html docbook epub jats markdown_strict multimarkdown markdown_github markdown_mmd markdown_phpextra gfm commonmark_x bbcode_phpbb bbcode_fluxbb bbcode_steam bbcode_hubzilla bbcode_xenforo'.split()))
contexts=[{},dict(title='Title 😀',body='BODY\n',author=['Alice','Боб'],date='2026',lang='en',toc=True,**{'header-includes':['HEADER'],'include-before':['BEFORE'],'include-after':['AFTER']})]
for writer in writers+['nosuchformat','HTML','html5+smart','','../html5']:
 add('default',writer=writer)
 if writer in writers:
  for context in contexts:add('compile-default',writer=writer,context=context)
for writer in ['html','html5','gfm','commonmark','native','markdown','markdown_strict']:
 add('default',writer=writer,user=True)
 for context in contexts:add('compile-default',writer=writer,user=True,context=context)
for path in ['default.html5','nested/default.html5','/missing/default.html5','default.jats_archiving','styles.html','local.tpl','dir/../local.tpl',
             'dir/main.tpl','bom.tpl','invalid.tpl','fallback.tpl','nested/fallback.tpl','/missing/fallback.tpl','missing.nope','missing/',r'dir\default.html5']:
 for user in [False,True]:add('get',path=path,user=user)
add('get',path='local.tpl',resourcePaths=['resources/second','resources'])
add('get',path='local.tpl',media=[['local.tpl','4d454449410d0a']])
add('get',path='data:text/plain,%EF%BB%BFhello%0D%0A')
add('get',path='data:application/octet-stream,%FF')
add('get',path='@WORK@/missing/default.html5')
add('get',path='@WORK@/resources/local.tpl')
# All original behavioral assertions (Lua type/module presence assertions are binding-specific).
add('compile',text='$title$',context={},original='returns a Template')
add('compile',text='${ partial() }',path='dir/default.test',original='partial with file path')
add('compile',text='${ nosuchpartial() }',original='missing partial')
add('compile',text=(root/'upstream/pandoc/data/templates/default.jats_archiving').read_text(),original='default jats with partials')
add('compile',text='placeholder',context={},original='returns a Doc')
add('compile',text='song: $title$',context={'title':'Along Comes Mary'},original='applies given context')
add('compile',text='Song $number$',context={'number':'2'},original='accepts string as template')
for path in [None,'main.tpl','dir/main.tpl','templates/default']:
 for text in ['$partial()$','$sub/child()$','$fallback()$','$loop()$','$bad()$','$missing()$']:
  for user in [False,True]:add('compile',path=path,text=text,user=user)
# Single bytes, full two-byte space, UTF-8 boundary triples/quads, truncation and CR/BOM offsets.
for byte in range(256):add('decode',path='probe',hex=bytes([byte]).hex())
for first in range(256):
 add('decode-batch',items=[bytes([first,second]).hex() for second in range(256)])
for lead in [0xe0,0xe1,0xec,0xed,0xee,0xef,0xf0,0xf1,0xf3,0xf4,0xf5]:
 for second,third,fourth in itertools.product([0,13,0x7f,0x80,0x8f,0x90,0x9f,0xa0,0xbf,0xc0,0xff],[0x7f,0x80,0xbf,0xc0],[None,0x7f,0x80,0xbf,0xc0]):
  data=bytes([lead,second,third]+([] if fourth is None else [fourth]))
  add('decode',path="file'😀",hex=data.hex())
for data in [b'\xc2',b'\xe2',b'\xe2\x82',b'\xf0\x9f\x98',b'\xe2\r\x82\xac\xff',b'\xe2\r\x82\xac',b'\xef\xbb\xbf'*2+b'x',b'\xef\xbb\xbf\rA\xff']:
 add('decode',path='offset',hex=data.hex())
rng=random.Random(0x7EAA)
for _ in range(1000):add('decode',path='random',hex=bytes(rng.randrange(256) for _ in range(rng.randrange(16))).hex())
paths=['','.', '..','../..','a/..','a/../../b','/../x','//','///a//b/../','a\\b','C:foo','C:/foo','C:\\foo','\\\\share\\dir\\file','\\\\?\\UNC\\share\\dir','\\\\?\\C:\\x','/','a/./b','../a','/a/../../b']
for style,path,dir in itertools.product(['posix','windows'],paths,['','dir','a/','C:','\\\\share','/']):add('path',style=style,path=path,directory=dir)
for _ in range(2000):add('path',style=rng.choice(['posix','windows']),path=rng.choice(paths)+''.join(rng.choice('aC:/\\.?') for _ in range(rng.randrange(15))),directory=rng.choice(paths))
requests_seen=[]
class Handler(BaseHTTPRequestHandler):
 def do_GET(self):
  requests_seen.append(self.path)
  if self.path in ['/missing/default.html5','/missing/fallback.tpl']:
   self.send_error(404);return
  if self.path=='/redirect':
   self.send_response(302);self.send_header('Location','/remote.tpl');self.end_headers();return
  body=b'[]' if self.path=='/document.native' else b'REMOTE\r\n' if self.path=='/remote.tpl' else b'$child()$' if self.path=='/main.tpl' else b'CHILD\r\n'
  self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
server=ThreadingHTTPServer(('127.0.0.1',0),Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
origin=f'http://127.0.0.1:{server.server_port}'
for path in ['/remote.tpl','/redirect','/missing/default.html5','/missing/fallback.tpl']:add('get',path='@ORIGIN@'+path,http=True,user=True)
add('compile',path='@ORIGIN@/main.tpl',text='$child()$',http=True)
with tempfile.TemporaryDirectory() as directory:
 work=Path(directory)
 for name,hex in setup.items():
  file=work/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(bytes.fromhex(hex))
 (work/'empty-user').mkdir()
 (work/'resources/dir').mkdir(exist_ok=True)
 native=[c for c in cs if c['op']!='path'];flat=[];spans=[]
 for c in native:
  start=len(flat)
  flat.extend([dict(op='decode',path='pair',hex=hex) for hex in c['items']] if c['op']=='decode-batch' else [c])
  spans.append((start,len(flat)))
 observed=[]
 # Batches bound Lua memory; no Kotlin result is used to generate an expectation.
 for start in range(0,len(flat),4096):
  request=work/'request.json';request.write_text(json.dumps(flat[start:start+4096]).replace('@WORK@',str(work)).replace('@ORIGIN@',origin))
  result=subprocess.run([a.pandoc,'-f','native','-t',str(root/'tools/template-resources-oracle.lua'),origin+'/document.native'],
      cwd=work,env={**os.environ,'KOTDOC_TEMPLATE_REQUEST':str(request)},capture_output=True)
  assert result.returncode==0,result.stderr.decode()
  values=json.loads(result.stdout);assert len(values)==len(flat[start:start+4096]);observed.extend(values)
 for c,(start,end) in zip(native,spans):c['expected']=observed[start:end] if c['op']=='decode-batch' else observed[start]
 assert requests_seen.count('/remote.tpl')==2,requests_seen # get plus redirect
 assert all(path in ['/document.native','/remote.tpl','/redirect','/missing/default.html5','/missing/fallback.tpl','/child.tpl'] for path in requests_seen),requests_seen
 native_paths=[c for c in cs if c['op']=='path']
 result=subprocess.run([a.paths_oracle],input='\n'.join(json.dumps([c['style'],c['path'],c['directory']]) for c in native_paths)+'\n',text=True,capture_output=True,check=True)
 values=[json.loads(line) for line in result.stdout.splitlines()];assert len(values)==len(native_paths)
 for c,value in zip(native_paths,values):c['expected']=value
 # Normalize only environmental paths/ports, preserving exact diagnostics otherwise.
 cs=json.loads(json.dumps(cs).replace(str(work),'<work>').replace(origin,'<origin>'))
server.shutdown();server.server_close();thread.join()
m=dict(upstream='pandoc-3.12.1/filepath-1.5.4.0',cases=len(cs),caseHash=digest(cs),sources=hashes,setup=setup,bytePairs=65536,originalTests=original_tests,operations=dict(collections.Counter(c['op'] for c in cs)))
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',filename='',mtime=0) as f:f.write(('\n'.join(json.dumps(c,ensure_ascii=False,separators=(',',':')) for c in [m]+cs)+'\n').encode())
print(f'Captured {len(cs)} original template resource cases: {m["operations"]}')
