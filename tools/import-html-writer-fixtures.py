#!/usr/bin/env python3
"""Capture complete unchanged Pandoc HTML writer/library observations and original units."""
import argparse,atexit,collections,difflib,gzip,hashlib,itertools,json,os,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-html-writer-reference';target=root/'core/src/test/resources/html-writer/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_HTML_WRITER_REFERENCE'));p.add_argument('--check-source',action='store_true');p.add_argument('--smoke',action='store_true');p.add_argument('--output',type=Path);args=p.parse_args()
if args.output:target=args.output
assert not args.smoke or args.output, "--smoke requires an alternate --output"
original=(root/'upstream/pandoc/test/Tests/Helpers.hs').read_text();assert (folder/'OriginalHelpers.hs').read_text()==original.replace('module Tests.Helpers','module OriginalHelpers',1)
sources=['tools/import-html-writer-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Skylighting-format-BSD-3-Clause.txt','core/src/test/resources/native/upstream.json','core/src/main/resources/io/heapy/kotdoc/pandoc-xml-data.json','vendor/network-uri-2.6.4.2/Network/URI.hs']
# Actual complete upstream library, test source, generated defaults and frozen native wiring.
sources += sorted(str(f.relative_to(root)) for base in [folder,root/'upstream/pandoc/src',root/'upstream/pandoc/xml-light',root/'upstream/pandoc-types/src',root/'upstream/pandoc/data'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
sources += ['upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/test/Tests/Writers/HTML.hs','upstream/pandoc/test/test-pandoc.hs','upstream/pandoc/test/Tests/Old.hs','upstream/pandoc/test/insert']
sources += sorted(str(f.relative_to(root)) for f in (root/'upstream/pandoc/test').rglob('*') if f.is_file() and f.suffix in ['.html','.html4','.html5','.native'])
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
originals=[f for f in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if f['accepted']]
formats=['html4','html5','html4-markup','html5-markup','epub2','epub3','s5','slidy','slideous','dzslides','revealjs']
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert {(c['source'],c['request'][0]) for c in cs if c['group']=='original-document-all-apis'}=={(f['path'],fmt) for f in originals for fmt in formats}
 assert len([c for c in cs if c['group']=='original-html-unit'])==m['originalUnitCount']==17
 assert len([c for c in cs if c['group']=='original-fixed-golden'])==16
 print('Verified',len(cs),'complete original HTML observations;',len(originals),'original AST documents,',m['originalUnitCount'],'original units and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group,**extra):
 result=ask(req);cs.append(dict(request=req,expected=result,group=group,**extra))
 if len(cs)%500==0:print('Captured',len(cs),'HTML observations',flush=True)
 return result
def tag(t,c=None):return {'t':t,**({} if c is None else {'c':c})}
def doc(blocks=(),meta=None):return {'pandoc-api-version':[1,23,1,2],'meta':meta or {},'blocks':list(blocks)}
def attr(id='',classes=(),kvs=()):return [id,list(classes),[list(p) for p in kvs]]
def s(text):return tag('Str',text)
space=tag('Space');soft=tag('SoftBreak');line=tag('LineBreak')
def para(xs):return tag('Para',xs)
def plain(xs):return tag('Plain',xs)
def header(n,text,a=None):return tag('Header',[n,a or attr(),[s(text)]])
def note(text):return tag('Note',[para([s(text)])])
def image(src='image.png',title='',a=None,alt=None):return tag('Image',[a or attr(),[s('image')] if alt is None else alt,[src,title]])
def link(src='https://example.org',title='',a=None,xs=None):return tag('Link',[a or attr(),[s('link')] if xs is None else xs,[src,title]])
def div(bs,a=None):return tag('Div',[a or attr(),bs])
def run(d,opts=None,fmt='html5',group='branches',**extra):return add([fmt,d,opts or {}],group,**extra)
units=[]
if not args.smoke:
 with tempfile.TemporaryDirectory(prefix='kotdoc-html-units-') as tmp:
  capture=Path(tmp)/'units.jsonl';env=dict(os.environ,KOTDOC_HTML_UNIT_CAPTURE=str(capture))
  subprocess.run([args.oracle,'--upstream-tests'],env=env,cwd=root,check=True)
  units=[json.loads(l) for l in capture.read_text().splitlines()]
 assert len(units)==17
 for u in units:
  opts={}
  name=u['name']
  if 'with q-tags' in name:opts['htmlQTags']=True
  if name=='at the end of a block':opts['referenceLocation']='end-of-block'
  if name=='at the end of a section':opts['referenceLocation']='end-of-section'
  if 'div' in name.lower():opts.update(referenceLocation='end-of-section',sectionDivs=True)
  result=run(u['document'],opts,'html4','original-html-unit',name=name,originalExpected=u['expected'])
  def lines(s):return s[:-1] if s.endswith('\n') else s
  assert lines(result['output'])==lines(u['expected']),(u,result)
# Execute every fixed old-style HTML/literate/S5 golden with its source options.
by_path={f['path']:f['json'] for f in originals}
goldens=[]
for fmt in ['html4','html5']:
 for name in ['testsuite','tables','tables/planets','tables/nordics','tables/students']:
  output='writer.'+fmt if name=='testsuite' else name+'.'+fmt
  opts=dict(columns=80,wrapText='wrap-auto',variables={'pandoc-version':''})
  if name=='testsuite':opts['defaultTemplate']=fmt
  goldens.append((name+'.native',output,fmt,opts))
for lhs in [False,True]:goldens.append(('lhs-test.native','lhs-test.html'+('+lhs' if lhs else ''),'html5',dict(wrapText='wrap-preserve',setextHeaders=True,extensions=['literate_haskell'] if lhs else [])))
for variant in ['basic','fancy','fragment','inserts']:
 fmt='s5' if variant in ['basic','fancy'] else 'html4'
 opts=dict(wrapText='wrap-auto',variables={'pandoc-version':''})
 if variant!='fragment':opts['defaultTemplate']=fmt
 if variant=='fancy':opts.update(mathMethod={'method':'mathjax','url':'https://cdn.jsdelivr.net/npm/mathjax@4/tex-chtml.js'},incremental=True)
 if variant=='inserts':
  text=(root/'upstream/pandoc/test/insert').read_text()
  opts['variables'].update({'header-includes':[text],'include-before':[text],'include-after':[text],'css':['main.css']})
 goldens.append(('s5.native','s5-'+variant+'.html',fmt,opts))
if not args.smoke:
 assert len(goldens)==16
 for source,output,fmt,opts in goldens:
  expected=(root/'upstream/pandoc/test'/output).read_text()
  result=run(by_path['test/'+source],opts,fmt,'original-fixed-golden',source=source,originalExpected=expected)
  actual=result['output']
  assert actual.removesuffix('\n')==expected.removesuffix('\n'), output+'\n'+''.join(difflib.unified_diff(expected.splitlines(True),actual.splitlines(True),fromfile=output,tofile='original-library-output'))
for f in originals[:5] if args.smoke else originals:
 for fmt in formats:run(f['json'],fmt=fmt,group='original-document-all-apis',source=f['path'])
# Rendering order, empty constructors, all inline wrappers, whitespace and quotes.
inline=[s('a <&> "quoted" \' apostrophe ↩ ↔ ↩\ufe0e'),space,soft,line,s('after')]
for t in ['Emph','Underline','Strong','Strikeout','SmallCaps','Superscript','Subscript']:inline.append(tag(t,[s(t),space,s('value')]))
for quote in ['SingleQuote','DoubleQuote']:inline.append(tag('Quoted',[tag(quote),[tag('Span',[attr(kvs=[('cite','https://example.org')]),[s('quoted')]])]]))
inline += [tag('Code',[attr(classes=['haskell','sample']), '>>=']),tag('Code',[attr(classes=['variable']), 'result']),link(xs=[s('a'),link(xs=[s('nested')])]),image(),note('footnote')]
base=doc([header(1,'Title',attr('title')),para(inline),plain([]),para([]),header(7,'Deep'),tag('HorizontalRule')],{'title':tag('MetaString','A title'),'date':tag('MetaString','2024-02-29'),'author':tag('MetaList',[tag('MetaString','A'),tag('MetaInlines',[s('B')])])})
for fmt,wrap,columns,prefix,qtags in itertools.product(formats,['wrap-auto','wrap-none','wrap-preserve'],[10,72],['','pre-'],[False,True]):
 run(base,dict(wrapText=wrap,columns=columns,identifierPrefix=prefix,htmlQTags=qtags),fmt,'all-api-wrap-prefix-quotes')
for classes in [[],['unknown','kbd','mark','tail','smallcaps','underline','after'],['smallcaps','smallcaps'],['csl-no-emph','csl-no-strong','csl-no-smallcaps'],['kbd','csl-no-emph'],['csl-block'],['csl-left-margin'],['csl-right-inline'],['csl-indent']]:
 for fmt in ['html4','html5']:
  run(doc([para([tag('Span',[attr(classes=classes,kvs=[('style','old')]),[s('span')]])])]),fmt=fmt,group='span-like-and-csl')
# Raw HTML/EPUB/slide formats, TeX environments and refs, warnings in original order.
for fmt,raw in itertools.product(formats,['html','html4','html5','epub','epub2','epub3','s5','slidy','slideous','dzslides','revealjs','markdown','latex','tex']):
 for text in ['<b>raw</b>',r'\begin{align}a&=b\end{align}',r'\ref{equation}']:
  run(doc([tag('RawBlock',[raw,text]),para([tag('RawInline',[raw,text])])]),dict(mathMethod={'method':'mathjax','url':'math.js?q=1'}),fmt,'raw-format-math-gates')
# Obfuscation operates on rendered markup, attributes and character entities.
for email,method,fmt in itertools.product(['mailto:name@example.org','mailto:nodomain','mailto:a@','mailto:a@b?subject=x','MAILTO:a@b','mailto:ä@例.example'],['none','references','javascript'],['html4','html5','revealjs']):
 for text in [[s(email.removeprefix('mailto:'))],[tag('Emph',[s('a & b')]),space,s('"quoted" </script>')]]:
  a=attr('email',['a','a'],[('title','\'"<&\\\n\r'),('x','v')])
  run(doc([para([link(email,a=a,xs=text)])]),dict(emailObfuscation=method,identifierPrefix='pre-'),fmt,'email-obfuscation')
for src,fmt in itertools.product(['test.png','test.mp3','test.mp4','test.pdf','test.unknown','file.svg.gz','https://example.org/test.mp3?x=.png#part','é.mp3?x=1','a%20b.mp3','data:image/png;base64,a','data:audio/ogg;base64,a','data:text/plain,hello','//example.org/file.webm','x:video.mp4?x=1','[bad].mp3?x=1','x.mp3#fragment'],formats):
 for a in [attr(),attr('id',['media'],[('width','50%'),('height','20px'),('style','old'),('style','later')]),attr(kvs=[('alt','override')])]:
  run(doc([para([image(src,a=a,alt=[]),image(src,'title',a,[tag('Emph',[s('alt')])])])]),fmt=fmt,group='media-categories-dimensions')
# Slide sections, pauses, speaker notes, wrappers, columns and incremental tasks.
task=tag('BulletList',[[plain([s('☒'),space,s('done')])],[para([s('☐'),space,s('todo')])]])
slideDoc=doc([header(1,'Title',attr('title',['r-fit-text'])),para([s('intro')]),header(2,'First',attr('first')),para([s('a'),note('n1')]),para([s('.'),space,s('.'),space,s('.')]),task,
 div([div([para([s('left')])],attr(classes=['column'],kvs=[('width','40%')])),div([para([s('right')])],attr(classes=['column'],kvs=[('width','60%')]))],attr(classes=['columns'])),
 div([task,para([s('speaker'),note('n2')])],attr(classes=['notes'])),header(3,'Nested'),para([s('nested')]),tag('HorizontalRule'),header(2,'Next'),para([s('b')])])
for fmt,level,incremental,location,number in itertools.product(formats,[None,1,2,3],[False,True],['end-of-document','end-of-block','end-of-section'],[False,True]):
 run(slideDoc,dict(slideLevel=level,incremental=incremental,referenceLocation=location,numberSections=number,sectionDivs=number,numberOffset=[2,3,4],tableOfContents=True),fmt,'sections-slides-and-notes')
for fmt,position in itertools.product(['html4','html5','epub2','epub3'],['above','below']):
 for caption in [[],[plain([s('image')])],[para([s('different')])]]:
  run(doc([tag('Figure',[attr('figure',['float'],[('title','caption')]),[None,caption],[plain([image()])]])]),dict(figureCaptionPosition=position),fmt,'figures-and-alt-captions')
for fmt,method,code,classes in itertools.product(['html4','html5','revealjs'],['default','none','idiomatic'],['x < y\n\nprint "hello"\n','>>=',''],[[],['haskell'],['HASKELL','literate'],['unknown'],['numberLines','python']]):
 run(doc([tag('CodeBlock',[attr(classes=classes,kvs=[('startFrom','3'),('title','code')]),code]),para([tag('Code',[attr(classes=classes),code])])]),dict(highlightMethod=method,extensions=['literate_haskell']),fmt,'highlighting-block-inline')
# Math types, every method, invalid input, raw source and multiple layout paths.
for fmt,method,typ,tex in itertools.product(['html4','html5','html5-markup','revealjs'],['plain','mathml','gladtex',{'method':'mathjax','url':'math.js?q=1'},{'method':'katex','url':'katex/'},{'method':'webtex','url':'web?'}],['InlineMath','DisplayMath'],['x+y',r'\frac{1}{2}',r'\begin{matrix}a&b\end{matrix}',r'\bad','',r'\text{漢🙂}']):
 run(doc([para([tag('Math',[tag(typ),tex])])]),dict(mathMethod=method),fmt,'math-methods-and-fallback')
# Template flags, all Reveal defaults, metadata rendering effects and overrides.
fields=['body','lang','pagetitle','author-meta','date-meta','description-meta','abstract-title','toc','table-of-contents','idprefix','html5','document-css','quotes','math','mathjax','mathjaxurl','displaymath-css','csl-css','csl-entry-spacing','highlighting-css','highlight-js','highlightjs-theme','scrollProgress','scrollProgressAuto']
source=(root/'upstream/pandoc/src/Text/Pandoc/Writers/HTML.hs').read_text()
import re
fields+=re.findall(r'defField "([^"\n]+)"',source[source.index('then -- set boolean options explicitly'):source.index('defField "document-css"')])
fields=list(dict.fromkeys(fields));tpl='\n'.join(k+'=\"$'+k+'$\"' for k in fields)+'\n'
for fmt,vars,meta,math in itertools.product(['html4','html5','html5-markup','revealjs','slideous'],[{},dict(sourcefile=['book.md']),dict(css=False,scrollProgress=True,classoption=['fleqn']),dict(css=[],scrollProgress='auto',controls=False,pagetitle='overridden',lang='ru')],[{},dict(lang=tag('MetaString','!invalid')),dict(author=tag('MetaString','<author>'),date=tag('MetaString','January 1, 2024'),description=tag('MetaInlines',[tag('Strong',[s('desc')])]))],['plain',{'method':'katex','url':'katex/'},{'method':'mathjax','url':'math.js?q=1'}]):
 d=doc(base['blocks']+[div([para([s('reference')])],attr('refs',kvs=[('entry-spacing','2')]))],{**base['meta'],**meta})
 run(d,dict(template=tpl,variables=vars,mathMethod=math,tableOfContents=True,numberSections=True),fmt,'template-context-and-overrides')
for fmt in ['html4','html5','s5','slidy','slideous','dzslides','revealjs']:
 run(base,dict(defaultTemplate=fmt),fmt,'original-default-templates')
# Entry-point ASCII conversion applies to both template and fragment rendering;
# structured HTML returns pre-escaped markup in this branch as in the original.
for fmt,template,wrap in itertools.product(formats,[None,'$pagetitle$|$body$'],['wrap-auto','wrap-none','wrap-preserve']):
 opts=dict(preferAscii=True,wrapText=wrap,columns=20,identifierPrefix='pré-')
 if template is not None:opts['template']=template
 run(base,opts,fmt,'ascii-entry-points')
# Nested notes, citations, empty nodes, custom section/slide attributes and
# duplicate attributes exercise state/effect order independently of old goldens.
edge=doc([header(1,'章',attr('a:b',['unnumbered'],[('class','override')])),
 para([s('a'),tag('Note',[para([s('outer'),note('inner')])]),s('b'),note('last')]),
 div([header(2,'Hidden'),para([s('body')])],attr('section',['section'],[('number','4'),('data-number','5')])),
 para([tag('Cite',[[{'citationId':'ref','citationPrefix':[],'citationSuffix':[],'citationMode':tag('NormalCitation'),'citationNoteNum':0,'citationHash':0}],[link('#ref-ref')]])])])
for fmt,location,division in itertools.product(formats,['end-of-document','end-of-block','end-of-section'],[False,True]):
 run(edge,dict(referenceLocation=location,sectionDivs=division,identifierPrefix='pré-',numberSections=True),fmt,'nested-note-state-and-identifiers')
# All attribute registries, duplicate keys (including empty), identifiers and HTML4/5.
keys=set()
registries=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/pandoc-xml-data.json').read_text())
for name,value in registries.items():
 if 'attribute' in name.lower() or 'rdfa' in name.lower():keys.update(value)
keys.update(['id','class','style','label','unknown','epub:type','xml:lang','data-x','aria-hidden','', 'a:b'])
for h5,closing,key in itertools.product([False,True],[False,True],sorted(keys)):
 add(['tag',dict(identifierPrefix='pre-'),h5,closing,'tag',attr('id',['a','','a','b'],[(key,'first'),(key,'second')])],'tag-attributes-and-duplicates')
# Common-state language caches persist between independent writer sessions.
for fmt in formats:
 add(['sequence',[[fmt,doc([para([s('a')])],{'lang':tag('MetaString','de')}),{}],[fmt,doc([para([note('n')])],{'lang':tag('MetaString','de')}),{}],[fmt,doc([para([s('b')])],{'lang':tag('MetaString','invalid!')}),{}]]],'shared-runtime-sequences')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalUnitCount=len(units),oracle='Complete unchanged pinned Pandoc library/HTML module, original unit-test module and assertions; JSON/state transport only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'complete HTML observations; SHA',m['caseHash'],flush=True)
