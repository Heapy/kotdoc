#!/usr/bin/env python3
"""Capture original style semantics; the Kotlin implementation is never consulted."""
import argparse,base64,gzip,hashlib,itertools,json,math,os,random,struct,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/highlighting/styles.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_HIGHLIGHT_STYLE_REFERENCE'));a=p.parse_args()
sources=['tools/import-highlight-style-fixtures.py','tools/import-highlight-style-data.py','licenses/Aeson-BSD-3-Clause.txt','licenses/Skylighting-core-BSD-3-Clause.txt','upstream/pandoc/stack.yaml','upstream/pandoc/pandoc.cabal','upstream/pandoc/src/Text/Pandoc/Options.hs','upstream/pandoc/src/Text/Pandoc/Highlighting.hs','upstream/pandoc/src/Text/Pandoc/App/CommandLineOptions.hs']
for folder in ['vendor/skylighting-core-0.15','vendor/aeson-highlight-errors-2.2.3.0','tools/haskell-highlight-style-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
original=(root/'upstream/pandoc/src/Text/Pandoc/Options.hs').read_text();excerpt=original[original.index('data HighlightMethod ='):original.index('-- | Varieties of HTML slide shows.')]
assert (root/'tools/haskell-highlight-style-reference/PandocHighlightMethod.hs').read_text().split('data HighlightMethod =',1)[1]=='data HighlightMethod ='.join(excerpt.split('data HighlightMethod =')[1:])
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert {c['input'] for c in cs if c['op']=='builtin'}=={'pygments','tango','espresso','zenburn','kate','monochrome','breezedark','haddock'}
 assert len(m['cliStyles'])==8
 assert len(m['cliLookups'])==25 and m['cliHash']==digest([m['cliStyles'],m['cliLookups']])
 print(f'Verified {len(cs)} original highlight style observations and eight Pandoc CLI themes');raise SystemExit()
if not a.oracle:p.error('--oracle must name highlight-style-reference')
info=json.loads(subprocess.check_output([a.oracle,'--reference-info']));assert info==['ghc','9.12','skylighting-core','0.15','aeson','2.2.3.0']
cs=[]
def add(op,v,name=None,**extra):cs.append(dict(name=f'{len(cs)} '+(name or op),op=op,input=v,**extra))
def native(reqs):
 lines=subprocess.check_output([a.oracle],input=('\n'.join(json.dumps(v,ensure_ascii=False,separators=(',',':')) for v in reqs)+'\n').encode()).splitlines()
 assert len(lines)==len(reqs);result=[json.loads(l) for l in lines];assert all('exception' not in r for r in result);return result
names=['pygments','tango','espresso','zenburn','kate','monochrome','breezedark','haddock']
cli={name:json.loads(subprocess.check_output(['pandoc','--print-highlight-style',name])) for name in names}
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
cliLookups=[]
for name in names+['PYGMENTS','KaTe','KATE','unknown','none','idiomatic','pygments ','../kate','kate.THEME']:
 r=subprocess.run(['pandoc','--print-highlight-style',name],capture_output=True)
 cliLookups.append(dict(name=name,value=json.loads(r.stdout)) if r.returncode==0 else dict(name=name,error=r.stderr.decode().rstrip('\n')))
with tempfile.TemporaryDirectory() as d:
 for text in [(root/'vendor/skylighting-core-0.15/test/default.theme').read_text(),'{}','{','{"text-styles":{"bad":{"bold":5}}}']:
  path=Path(d)/'request.theme';path.write_text(text)
  r=subprocess.run(['pandoc','--print-highlight-style',str(path)],capture_output=True)
  cliLookups.append(dict(name='request.theme',theme=text,value=json.loads(r.stdout)) if r.returncode==0 else dict(name='request.theme',theme=text,error=r.stderr.decode().rstrip('\n').replace(str(path),'request.theme')))
 for data in [b'{"text-styles":{}}',b'\xff',b'{"text-styles":{},"ignored":"\xc3"}',b'\xef\xbb\xbf{"text-styles":{}}']:
  path=Path(d)/'request.theme';path.write_bytes(data)
  r=subprocess.run(['pandoc','--print-highlight-style',str(path)],capture_output=True)
  c=dict(name='request.theme',themeBytes=base64.b64encode(data).decode())
  if r.returncode==0:c['value']=json.loads(r.stdout)
  else:c['error']=r.stderr.decode().rstrip('\n').replace(str(path),'request.theme')
  cliLookups.append(c)
for name,result in zip(names,native([['builtin',n] for n in names])):
 assert cli[name]==result['value'],name;add('builtin',name)
tokenNames=['Keyword','DataType','DecVal','BaseN','Float','Constant','Char','SpecialChar','String','VerbatimString','SpecialString','Import','Comment','Documentation','Annotation','CommentVar','Other','Function','Variable','ControlFlow','Operator','BuiltIn','Extension','Preprocessor','Attribute','RegionMarker','Information','Warning','Alert','Error','Normal']
bad=[None,True,False,0,1,1.5,'',[],{},'invalid']
spaces=[chr(n) for n in list(range(33))+[133,160,5760,8192,8193,8202,8232,8233,8239,8287,12288,8203,0xfeff]]
for name in tokenNames:
 for s in [name,name+'Tok',' '+name,name+' ',name.lower(),name.upper(),'\t'+name,'('+name+')',name+'Tok --',name+'Tok {-x-}',name+'Tok\n--']:
  add('token',s,'token '+repr(s))
for c in spaces:add('token',c+'Keyword','token leading U+%04X'%ord(c))
for v in ['--x\nKeyword','{-x-}Keyword']:add('token',v,'token leading comment '+repr(v))
for v in bad:add('token',v)
colors=['#15ffa0','#ABCDEF','#0015ffa0','#??15ffa0','#😀😺15ffa0','#00abcdef','#0 0 0 ','#F 0102','#a\tb\nc\r','#000000','#ffffff','#ffffffff','15ffa0','#123','#12345','#1234567','#123456789','#gg0000']
for v in colors+bad:
 add('color',v)
 if isinstance(v,str):add('color-string',v)
for c,pos in itertools.product(spaces+list('0123456789abcdefABCDEFxXgG+-()'),range(6)):
 s=list('#1a2b3c');s[pos+1]=c;v=''.join(s);add('color',v,f'color U+{ord(c):04X} at {pos}')
for c in ['\0','\n','\t','\U0001f600','\U0010ffff','\ue000','x','?']:
 add('color-string','#'+c+c+'12abEF','ignored ARGB alpha '+repr(c))
for v in [-2**63,-2**63+1,-1,0,1,0xffffff,0x1000000,2**31-1,2**32-1,2**63-1]+[i<<(8*c) for i,c in itertools.product(range(256),range(3))]:add('color-int',v)
def bits(x):return struct.unpack('<Q',struct.pack('<d',x))[0]
doubles=[0,0x8000000000000000,1,bits(-1),bits(0.5),bits(1),bits(math.nextafter(1,0)),bits(math.nextafter(1,2)),bits(math.nextafter(0,-1)),0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000001]
for triple in itertools.product(doubles,repeat=3):add('color-double',list(triple))
for i in range(256):
 for delta in [-1,0,1]:
  x=i/255.0;x=math.nextafter(x,-math.inf) if delta<0 else math.nextafter(x,math.inf) if delta>0 else x
  add('color-double',[bits(x)]*3,'double channel quantization '+str((i,delta)))
validColors=[None,'#000000','#ffffff','#15ffa0','#FF15ffa0','#f 0102']
for c,b,flags in itertools.product(validColors,validColors,itertools.product([False,True],repeat=3)):
 add('token-style',dict(zip(['text-color','background-color','bold','italic','underline'],[c,b,*flags])))
for key in ['text-color','background-color','bold','italic','underline']:
 for v in bad+['#123456']:
  add('token-style',{key:v},'token style field '+key)
for v in bad:add('token-style',v)
for v in bad:add('style',v)
for v in bad+[{'Normal':{}},{'Normal':{'text-color':'#12abEF'}},{'Other':{},'unknown':{'bold':True}},{'a':{'bold':True},'z':{'italic':True}}]:add('style',{'text-styles':v})
validStyles=[]
for styles in [{},{'Normal':{'text-color':'#111111'}},{' Normal':{'text-color':'#222222'}},{'Other':{'bold':True},'unknown':{'italic':True}},{'\ue000':{'bold':True},'😀':{'italic':True}},{'z':{'underline':True},'a':{'bold':True}}]:
 for editor in [None,{}, {'background-color':'#445566','line-numbers':'#778899'}]:
  for override in [None,'#aabbcc']:
   v={'text-styles':styles,'editor-colors':editor,'text-color':override,'background-color':override,'line-number-color':override,'line-number-background-color':override}
   validStyles.append(v);add('style',v)
for key in ['editor-colors','text-color','background-color','line-number-color','line-number-background-color']:
 for v in bad+['#123456']:
  add('style',{'text-styles':{},key:v},'style field '+key)
for name in ['a-b','1','_','a_b','é','a²','aⅧ','a١','²a','Ⅷa','\u1c89','a\u1c89','\U00011380','a\U00011380','😀','\ue000',"has'quote",'has"quote','a.b','a\\b','tab\tkey','']:
 add('style',{'text-styles':{name:5}},'error path '+repr(name))
for v in [None,[],0,False,'bad',{'bad':None},{'bad':'#zzzzzz'}]:add('style',{'text-styles':{},'editor-colors':v})
theme=(root/'vendor/skylighting-core-0.15/test/default.theme').read_text();add('theme',theme,'original default.theme')
for raw in ['','{','[] trailing','{"text-styles":{},}', '{/* comment */ "text-styles":{}}','{"text-styles":{"Other":{"bold":false,"bold":true}}}', 'null','[]','{"text-styles":{}}']:
 add('theme',raw,'theme syntax '+repr(raw),syntax=raw in ['','{','[] trailing','{"text-styles":{},}', '{/* comment */ "text-styles":{}}'])
for raw in [
 '{"text-styles":{},"text-styles":null}',
 '{"text-styles":null,"text-styles":{}}',
 '{"text-styles":{},"text-color":"#123456","text-color":"bad"}',
 '{"text-styles":{},"text-color":null,"text-color":"#123456"}',
 '{"text-styles":{"Normal":{},"Normal":{"bold":true}}}',
 '{"text-styles":{"Other":{"bold":true,"bo\\u006cd":false}}}',
 '{"text-styles":{"Other":{"bold":5,"bold":true}}}',
 '{"text-styles":{},"editor-colors":{"background-color":"#123456","background-color":"bad"}}',
 '{"text-styles":{},"ignored":[{"x":1,"x":2},null,true,"escaped\\\"quote"]}',
]:add('theme',raw,'duplicate/escaped theme '+raw)
for raw in [
 '{"text-styles":{},"ignored":1e99999}',
 '{"text-styles":{},"ignored":"\\u0000"}',
 '{"text-styles":{},"ignored":"\\ud83d\\ude00"}',
]:add('theme',raw,'JSON literal '+raw)
for raw in [
 '{"text-styles":{"\\ud800":5}}',
 '{"text-styles":{"\\udc00":5}}',
 '{"text-styles":{},"ignored":"\\ud800"}',
 '{"text-styles":{},"ignored":"\\ud800","ignored":"valid"}',
 '{"text-styles":{},"ignored":"valid","ignored":"\\udc00"}',
 '{"text-styles":{},"ignored":"\\ud800x"}',
 '{"text-styles":{},"ignored":"\\ud800\\ud800"}',
 '{"text-styles":{},"ignored":NaN}',
 '{"text-styles":{},"ignored":Infinity}',
 '{"text-styles":{},"ignored":undefined}',
 '{"text-styles":{},"ignored":01}',
 '{"text-styles":{},"ignored":-01}',
 '{"text-styles":{},"ignored":+1}',
 '{"text-styles":{},"ignored":.1}',
 '{"text-styles":{},"ignored":1.}',
 '{"text-styles":{},"ignored":1e}',
]:add('theme',raw,'invalid JSON literal '+raw,syntax=True)
for n in range(32):
 add('theme','{"text-styles":{},"ignored":"a'+chr(n)+'b"}',f'unescaped JSON control U+{n:04X}',syntax=True)
 add('theme',json.dumps({'text-styles':{},'ignored':'a'+chr(n)+'b'}),f'escaped JSON control U+{n:04X}')
for v in ['none','default','idiomatic','NONE','Default','IdIoMaTiC','default ',True,False,None,0,1,[],{},'']+validStyles+list(cli.values()):add('highlight',v)
for a1,a2 in itertools.product(tokenNames,repeat=2):add('token-order',[a1,a2])
for a1,a2 in itertools.product(['#000000','#010000','#000100','#000001','#ffffff','#ff00ff'],repeat=2):add('color-order',[a1,a2])
for a1,a2 in itertools.product(list(cli.values()),repeat=2):add('style-order',[a1,a2])
rng=random.Random(0x57a1e)
tokenStyleInputs=[c['input'] for c in cs if c['op']=='token-style' and isinstance(c['input'],dict) and len(c['input'])==5]
for _ in range(256):add('token-style-order',[rng.choice(tokenStyleInputs),rng.choice(tokenStyleInputs)])
for start in range(0,len(cs),256):
 batch=cs[start:start+256]
 for c,expected in zip(batch,native([[c['op'],c['input']] for c in batch])):c['expected']=expected
 if start%1024==0:print(f'Captured {start+len(batch)}/{len(cs)} style observations',flush=True)
target.parent.mkdir(parents=True,exist_ok=True)
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [dict(sources=hashes,oracle=info,cliStyles=cli,cliLookups=cliLookups,cliHash=digest([cli,cliLookups]),cases=len(cs),caseHash=digest(cs))]+cs)+'\n').encode(),mtime=0))
print(f'Captured {len(cs)} original highlight style observations')
