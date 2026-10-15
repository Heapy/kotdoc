#!/usr/bin/env python3
"""Observe complete original Pandoc mathematical parsers; never invoke Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-parse-math-reference';target=root/'core/src/test/resources/parse-math/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_PARSE_MATH_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=(root/'upstream/pandoc/test/Tests/Helpers.hs').read_text()
assert (folder/'OriginalHelpers.hs').read_text()==original.replace('module Tests.Helpers','module OriginalHelpers',1)
sources=['tools/import-parse-math-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Parsec-BSD-2-Clause.txt','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/test/Tests/Readers/HTML.hs','upstream/pandoc/test/Tests/Readers/Markdown.hs','upstream/pandoc/test/test-pandoc.hs']
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'upstream/pandoc/src',root/'upstream/pandoc/xml-light',root/'upstream/pandoc-types/src',root/'vendor/parsec-3.1.18.0'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
def cp(s):return list(map(ord,s)) if isinstance(s,str) else s
def math_nodes(v):
 if isinstance(v,dict):
  if v.get('t')=='Math':yield v['c']
  else:
   for x in v.values():yield from math_nodes(x)
 elif isinstance(v,list):
  for x in v:yield from math_nodes(x)
syntax=[('\\\\(','\\\\)','inline','tex_math_double_backslash'),('\\\\[','\\\\]','display','tex_math_double_backslash'),('\\(','\\)','inline','tex_math_single_backslash'),('\\[','\\]','display','tex_math_single_backslash'),('$$','$$','display','tex_math_dollars'),('$','$','inline','tex_math_dollars')]
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert len(m['htmlUnits'])==69 and len(m['markdownMathUnits'])==138
 assert len([c for c in cs if c['group']=='original-html-math-parser'])==48
 assert len([c for c in cs if c['group']=='original-markdown-math-parser-prefix'])==138
 for c in cs:
  if c['group']=='original-html-math-parser':
   u=m['htmlUnits'][c['unitIndex']];assert c['request'][1]['input']==cp(u['input'][3:-4])
   expected=list(math_nodes(u['expected']));assert len(expected)==1 and c['expected']['ok'] and c['expected']['value']==expected[0][1]
  if c['group']=='original-markdown-math-parser-prefix':assert c['request'][1]['input']==cp(m['markdownMathUnits'][c['unitIndex']]['input'])
 assert all(not isinstance(c['expected'],dict) or 'host' not in c['expected'] for c in cs)
 print('Verified',len(cs),'complete mathematical parser observations, 48 HTML and 138 Markdown math inputs, five stream instances and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
with tempfile.TemporaryDirectory(prefix='kotdoc-parse-math-units-') as tmp:
 units=[]
 for flag,label in [('--upstream-tests','html'),('--markdown-math-tests','markdown')]:
  capture=Path(tmp)/(label+'.jsonl');env=dict(os.environ,KOTDOC_PARSE_MATH_HTML_CAPTURE=str(capture))
  subprocess.run([args.oracle,flag],env=env,cwd=root,check=True)
  units.append([json.loads(l) for l in capture.read_text().splitlines()])
html_units,markdown_units=units;assert len(html_units)==69 and len(markdown_units)==138,(len(html_units),len(markdown_units))
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 result=json.loads(line);assert not isinstance(result,dict) or 'host' not in result,(req,result)
 return result
def add(req,group,**extra):
 c=dict(request=req,expected=ask(req),group=group,**extra);cs.append(c)
 if len(cs)%1000==0:print('Captured',len(cs),'mathematical parser observations',flush=True)
 return c
options=ask(['defaults'])
def opts(exts):return dict(options,extensions=exts)
all_exts=['tex_math_dollars','tex_math_single_backslash','tex_math_double_backslash']
def request(text,parser=('either',),exts=all_exts,stream='text',position=None,access='default',calls=0):
 inp=cp(text)
 if stream=='bytes':inp=[x&255 for x in inp]
 if stream=='lazy-bytes':inp=[inp[:1],[],inp[1:]];inp=[[x&255 for x in part] for part in inp]
 if stream=='sources':inp=[[[cp('part'),7,3],inp]]
 return ['run',dict(parser=list(parser),input=inp,stream=stream,options=opts(exts),position=position or [cp('source'),1,1],access=access,sentinel=cp('retained\0漢'),calls=calls)]
for u in html_units+markdown_units:
 u['expected']=ask(['native',cp(u['expectedNative'])]);assert 'error' not in u['expected'],u
for i,u in enumerate(html_units):
 nodes=list(math_nodes(u['expected']))
 if not nodes:continue
 inner=u['input'][3:-4];assert u['input']=='<p>'+inner+'</p>'
 op,cl,mode,ext=next(s for s in syntax if inner.startswith(s[0]))
 c=add(request(inner,[mode],[ext]),'original-html-math-parser',unitIndex=i)
 assert len(nodes)==1 and c['expected']['ok'] and c['expected']['value']==nodes[0][1],u
for i,u in enumerate(markdown_units):
 inner=u['input'];op,cl,mode,ext=next(s for s in syntax if inner.startswith(s[0]))
 c=add(request(inner,[mode],[ext]),'original-markdown-math-parser-prefix',unitIndex=i)
 for kind,body in math_nodes(u['expected']):
  start=inner.find(op+body+cl);assert start>=0,(u,body)
  if start>0:
   c=add(request(inner[start:],[mode],[ext]),'original-markdown-later-math-span',unitIndex=i,span=start)
   assert c['expected']['ok'] and c['expected']['value']==body
bodies=['','x','x y',' x','x ','x\t','\tx','x\n','x\ny','x\n\ny','x\n \t\ny','x\r y','{x}','{{x}}','{x','x}','\\{x','x\\}','{x\\\\}','{\\}x}','{a\nb}','{a\n\nb}','x%\ny','x%no-newline','x% $ \\} {\ny','{x%}\ny}','x\\%y','x\\\\%\ny','x%\n%}\ny','\\text{hi $x$ bye}','\\colorbox{aqua}{$F=ma$}','\\\n\nx','x\\  \t\n','漢𝛂🙂','\u00a0x','x\u00a0','\u0000','&amp;']
configs=[list(e for i,e in enumerate(all_exts) if bits&(1<<i)) for bits in range(8)]
for op,cl,mode,ext in syntax:
 for body,exts in itertools.product(bodies,configs):
  add(request(op+body+cl+'tail',[mode],exts),'all-delimiters-extensions-groups-comments')
for op,cl,mode,ext in syntax:
 for ending,body in itertools.product(['','3','٣','９','tail','$','\\','\n'],['x','x ','x\\ ','%\n','{x}']):
  add(request(op+body+cl+ending,[mode],[ext]),'closing-delimiters-digit-and-space-gates')
for stream,opdata,body in itertools.product(['text','chars','bytes','lazy-bytes','sources'],syntax,['x','\tx','x\ny','{x}','x%\ny','x\n\ny','x\r y','x\\%y','x\\ y','']):
 op,cl,mode,ext=opdata
 for wrapper in [lambda p:p,lambda p:['eof',p],lambda p:['fallback',p],lambda p:['look',p]]:
  add(request(op+body+cl+'!',wrapper([mode]),[ext],stream),'all-five-streams-and-parser-composition')
for stream,access,exts,parser,text in itertools.product(['text','chars','sources'],['default','trace','consume','fail'],configs,[['inline'],['display'],['fallback',['either']],['option']],['$x$','!$x$','$$y$$','\\(x\\)','\\\\(x\\\\)','','x']):
 add(request(text,parser,exts,stream,access=access,calls=2**63-1),'reader-options-getter-scope-and-overrides')
for ext,enabled,access in itertools.product(all_exts+['smart','raw_html','unknown'],[False,True],['default','trace','consume','fail']):
 for guard in ['enabled','disabled']:
  add(request('!tail',[guard,ext],[ext] if enabled else [],access=access),'enabled-disabled-guards')
for text in ['$a$$b$','$$a$$$$b$$','\\(a\\)\\(b\\)','\\\\(a\\\\)\\\\(b\\\\)','$x$!','']:
 for access in ['default','trace','consume','fail']:
  add(request(text,['many',['either']],access=access),'repeated-parser-state-and-rest')
for body in ['x','{x}','x%\ny','\tx','x\ny','x\\y']:
 text='$'+body+'$!'
 for at in range(len(text)+1):
  for empties in [False,True]:
   r=request(text,['inline'],['tex_math_dollars'],'sources');parts=[text[:at],text[at:]]
   if empties:parts=['',parts[0],'',parts[1],'']
   r[1]['input']=[[[cp('part-'+str(i)),i*3-2,i*7-4],cp(part)] for i,part in enumerate(parts)]
   add(r,'source-file-chunks-empty-spines-and-position-jumps')
for name,line,col,text in itertools.product([cp(''),cp('漢🙂'),[0xd800],[0xd800,0xdc00]],[-1,1,2**63-1],[-1,1,2**63-1],['$\tx$','$x $','${x}$','']):
 add(request(text,['inline'],['tex_math_dollars'],position=[name,line,col]),'raw-source-name-and-int64-position-boundaries')
for code in list(range(256))+[0xd800,0xdc00,0x10ffff]:
 for context in ['$'+chr(code)+'$','${'+chr(code)+'}$','$x%'+chr(code)+'\ny$']:
  add(request(cp(context),['inline'],['tex_math_dollars'],'chars'),'raw-char-lexical-and-group-builder-boundaries')
for text in ['',' ','\t\n','x ','x\\ ','x\\ \t\n',' \tx\\\n\t','x\\\\  ','x\u00a0','\u00a0x\n','x\r\n','漢🙂\t']:
 add(['trim',cp(text)],'exact-shared-trim-math')
rng=random.Random(0x504d4154);alphabet='abc012$\\[](){}% \t\n\r\u00a0α🙂'
for i in range(3000):
 if i%2:
  op,cl,mode,ext=rng.choice(syntax);text=op+''.join(rng.choices(alphabet,k=rng.randrange(25)))+cl
 else:text=''.join(rng.choices(alphabet,k=rng.randrange(35)))
 add(request(text,rng.choice([['inline'],['display'],['either'],['fallback',['either']],['eof',['either']]]),rng.choice(configs),rng.choice(['text','chars','sources'])),'generated-backtracking-group-comment-boundaries')
for text in ['$'+'{'*1500+'x'+'}'*1500+'$!', '$'+('x'*50000)+'$!', '$'+'\\text{'*1500+'x'+'}'*1500+'$!', '$'+'x% ignored $ { }\n'*3000+'y$!']:
 add(request(text,['inline'],['tex_math_dollars']),'finite-deep-and-long-parser')
for first in range(0,0x110000,65536):add(['unicode',first,min(65536,0x110000-first)],'all-char-inline-display-witnesses')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),htmlUnits=html_units,markdownMathUnits=markdown_units,oracle='Complete unchanged Pandoc library; original HTML/Markdown test modules; observation transport only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'complete mathematical parser observations; SHA',m['caseHash'],flush=True)
