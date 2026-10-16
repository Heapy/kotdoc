"""Deterministic source-branch inputs; output expectations are original-module only."""
import copy,itertools,random
BASE=dict(wrap='WrapAuto',columns=72,extensions=[],referenceLocation='EndOfDocument',dpi=96,tabStop=4)
def node(t,c=None):return dict(t=t,**({} if c is None else dict(c=c)))
def doc(blocks,meta=None):return {'pandoc-api-version':[1,23,1,2],'meta':meta or {},'blocks':blocks}
def para(xs):return node('Para',xs)
def string(s):return node('Str',s)
def attr(i='',cls=None,kv=None):return [i,cls or [],kv or []]
def requests(original,documents):
 out=[]
 def add(name,d,opts=None,template=None):
  q=dict(name=name,document=d,options=BASE|dict(opts or {}))
  if template is not None:q['template']=template
  out.append(q)
 for r in original:
  for wrap,ref in itertools.product(['WrapAuto','WrapNone','WrapPreserve'],['EndOfDocument','EndOfBlock','EndOfSection']):add('original-options/'+r['name']+'/'+wrap+'/'+ref,r['document'],r['options']|dict(wrap=wrap,referenceLocation=ref,columns=19))
 for i,d in enumerate(documents):add('native-corpus/'+str(i),d,dict(extensions=['amuse','auto_identifiers'],columns=80))
 strings=['','-','- x','----','---',';','; x','>','> x','>>>','::','~~','[[',']','[1]','[01]','[10]','[12a]','{9}','{00}','{123}','] [2]','</verbatim>','\n','\r','\t','\u00a0','1.','23. x','a. x','I. x','١. x','\U0001e6c0','a\nb','a\r\nb']
 for i,s in enumerate(strings):
  for context in ['root','link','note','bullet','header','emph']:
   xs=[string(s)];b=para(xs)
   if context=='link':b=para([node('Link',[attr(),xs,['url','']])])
   elif context=='note':b=para([node('Note',[b])])
   elif context=='bullet':b=node('BulletList',[[b]])
   elif context=='header':b=node('Header',[2,attr(),xs])
   elif context=='emph':b=para([node('Emph',xs)])
   add(f'escape/{i}/{context}',doc([b]))
 forms=['Emph','Strong','Strikeout','Superscript','Subscript','SmallCaps','Underline']
 for outer,inner,text in itertools.product(forms,forms,['','word',' word','word ','\t','*']):add('formats/'+outer+'/'+inner+'/'+repr(text),doc([para([string('a'),node(outer,[node(inner,[string(text)])]),string('b')])]),dict(extensions=['amuse']))
 for ext,width,title,classes in itertools.product([[],['amuse']],['0%','1.5%','2.5%','-2.5%','60%','NaN%','Infinity%','1e308%','3px',''],['','fig:title','fig:fig:deep','a]b'],[[],['align-left'],['align-right'],['align-left','align-right']]):add('image/'+repr((ext,width,title,classes)),doc([para([node('Image',[attr('',classes,[['width',width]]),[string('a]b')],['a]b.png',title]])])]),dict(extensions=ext))
 for suffix in ['eps','gif','jpg','jpeg','pbm','png','tiff','xbm','xpm','PNG','png?query','png#fragment','', '.png']:
  for target in ['a.'+suffix,'.'+suffix,'dir/a.'+suffix]:add('link/'+target,doc([para([node('Link',[attr(),[string(target)],[target,'ignored']])])]))
 for tex in ['2^3','abc','\\frac{a}{b}','\\badcommand{','{','\\text{a}','\\sum_{i=1}^n','\\notacommand','\\alpha+\\beta']:
  add('math/'+tex,doc([para([node('Math',[node('InlineMath'),tex]),node('Math',[node('DisplayMath'),tex])])]))
 for content in [[],[string('x')],[node('SmallCaps',[string('x')])],[node('Cite',[[],[string('x')]])]]:add('citation/'+repr(content),doc([para([node('Cite',[[],content])])]))
 for level in [-2,0,1,6,9]:
  for ext in [[],['auto_identifiers'],['auto_identifiers','ascii_identifiers'],['gfm_auto_identifiers']]:add('header/'+repr((level,ext)),doc([node('Header',[level,attr('hello'),[string('HÉLLO!')]]),node('Header',[level,attr('hello-1'),[string('HÉLLO!')]])]),dict(extensions=ext))
 metadata={'title':node('MetaInlines',[string('Title'),node('Note',[para([string('meta note')])])]),'author':node('MetaList',[node('MetaString','A'),node('MetaString','B')]),'custom':node('MetaMap',{'b':node('MetaBlocks',[para([string('block')])]),'a':node('MetaBool',True)})}
 for template in ['$body$','$title$\n$body$','$custom.b$:$for(author)$$author$$sep$;$endfor$\n$body$','$meta-json$\n$body$','$foo$\n$body$']:
  for variables in [{},{'title':'override','foo':'v'},{'foo':['x','y']}]:add('template/'+repr((template,variables)),doc([para([string('body'),node('Note',[para([string('body note')])])])],metadata),dict(variables=variables),template)
 rng=random.Random(618164)
 for n in range(200):
  xs=[]
  for _ in range(rng.randrange(1,15)):
   x=string(rng.choice(strings));k=rng.choice(forms+['Space','SoftBreak','LineBreak','Str'])
   xs.append(node(k) if k in ['Space','SoftBreak','LineBreak'] else x if k=='Str' else node(k,[x]))
  add('mixed/'+str(n),doc([para(xs)]),dict(wrap=rng.choice(['WrapAuto','WrapNone','WrapPreserve']),columns=rng.choice([1,10,72]),extensions=rng.choice([[],['amuse']])))
 return out
def deep_requests():
 return [dict(name=f'deep/{kind}/{depth}',deep=dict(kind=kind,depth=depth),options=BASE|dict(extensions=['amuse','auto_identifiers'])) for kind,depth in [('emph',4096),('note',4096),('div',4096),('quote',4096),('bullet',512),('ordered',512),('definition',256),('table',128),('mixed',128)]]
def extra_requests(ranges,template):
 out=[]
 def add(name,d,options=None,tpl=None):
  q=dict(name='extra/'+name,document=d,options=BASE|dict(options or {}))
  if tpl is not None:q['template']=tpl
  out.append(q)
 def note(s):return node('Note',[para([string(s)])])
 def cell(bs,span=1):return [attr(),node('AlignDefault'),1,span,bs]
 def row(cells):return [attr(),cells]
 def table(columns,head,body,foot,caption=None):return node('Table',[attr(),[None,caption or []],[[node('AlignDefault'),node('ColWidthDefault')]for _ in range(columns)],[attr(),head],[[attr(),0,[],body]],[attr(),foot]])
 for loc,wrap in itertools.product(['EndOfDocument','EndOfBlock','EndOfSection'],['WrapAuto','WrapPreserve']):
  b=table(2,[row([cell([para([string('head'),note('head note')])]),cell([para([string('other')])])])],[row([cell([para([string('body'),note('body note')])]),cell([para([string('last')])])])],[],[para([string('caption'),note('caption note')])])
  add('simple-table-note-state/'+loc+'/'+wrap,doc([b,node('Header',[1,attr(),[string('end')]])]),dict(referenceLocation=loc,wrap=wrap))
 for ncols,span,multi,foot in itertools.product([0,1,2,3],[1,2],[False,True],[False,True]):
  bs=[para([string('a'),node('LineBreak'),string('b')])]if multi else [para([string('cell')])]
  b=table(ncols,[row([cell(bs,span),cell(bs)])],[row([cell(bs),cell(bs)])],[row([cell(bs)])]if foot else [],[para([string('ignored grid caption')])])
  for wrap in ['WrapAuto','WrapNone']:add('grid/'+repr((ncols,span,multi,foot,wrap)),doc([b]),dict(wrap=wrap,columns=31))
 text=''.join(chr(p)for a,b in ranges for p in range(a,b+1))
 for ext in [[],['auto_identifiers'],['ascii_identifiers'],['gfm_auto_identifiers']]:
  add('unicode17/'+repr(ext),doc([node('Header',[1,attr('custom'),[string(text)]]),para([node('Emph',[string(text)]),string(text),node('Code',[attr(),text])])]),dict(extensions=ext))
 meta={'body':node('MetaString','metadata-body'),'title':node('MetaString','Title'),'author':node('MetaList',[node('MetaString','A'),node('MetaString','B')]),'z':node('MetaBlocks',[node('Header',[1,attr('h'),[string('H')]]),para([note('metadata note')])]),'a':node('MetaInlines',[node('Math',[node('InlineMath'),'\\badcommand'])])}
 for tpl in [None,template,'$meta-json$\n$body$','$title$ $body$ $z$']:
  for loc in ['EndOfDocument','EndOfSection','EndOfBlock']:
   add('metadata-state/'+repr(tpl)+'/'+loc,doc([node('Header',[1,attr('h-1'),[string('H')]]),para([note('body note')])],meta),dict(extensions=['auto_identifiers'],referenceLocation=loc),tpl)
 for first,second in itertools.product(['DefaultStyle','Example','Decimal','LowerRoman','UpperRoman','LowerAlpha','UpperAlpha'],repeat=2):
  def ordered(style):return node('OrderedList',[[1,node(style),node('DefaultDelim')],[[para([string('x')])],[para([string('y')])]]])
  add('consecutive-style/'+first+'/'+second,doc([ordered(first),ordered(second)]))
 for style,start,delim in itertools.product(['DefaultStyle','Example','Decimal','LowerRoman','UpperRoman','LowerAlpha','UpperAlpha'],[-9223372036854775808,-1,0,9223372036854775807],['DefaultDelim','OneParen','TwoParens']):
  # Native drop(start-1) over an infinite alphabet cycle is linear; use finite cycles for alpha.
  if style in ['LowerAlpha','UpperAlpha']:start={-9223372036854775808:27,9223372036854775807:52}.get(start,start)
  b=node('OrderedList',[[start,node(style),node(delim)],[[para([string('first')])],[para([string('second')])],[para([string('last')])]]])
  add('marker-boundary/'+repr((style,start,delim)),doc([b]))
 return out
def golden_requests(documents,template):
 result=[]
 for source,golden,standalone in [('testsuite.native','writer.muse',True),('tables.native','tables.muse',False)]:
  q=dict(name='old/'+golden,document=documents['native/'+source],options=BASE|dict(columns=80,extensions=['amuse','auto_identifiers'],variables={'pandoc-version':''}))
  if standalone:q['template']=template
  result.append(q)
 return result
def large_width_requests():
 return [dict(name='large-percent/'+str(i)+'/'+str(bool(ext)),document=doc([para([node('Image',[attr('',[],[['width',width]]),[],['image.png','']])])]),options=BASE|dict(extensions=ext))for i,width in enumerate(['1'+'0'*308+'%','9'*400+'%'])for ext in [[],['amuse']]]
def caption_depth_requests():
 return [dict(name='caption-depth/'+kind,deep=dict(kind=kind,depth=4096),options=BASE)for kind in ['caption','grid-caption']]
def caption_shape_requests(documents):
 kinds=['Plain','Para','LineBlock','CodeBlock','RawBlock','BlockQuote','OrderedList','BulletList','DefinitionList','Header','HorizontalRule','Table','Figure','Div'];pool={}
 def walk(x):
  if isinstance(x,dict):
   if x.get('t')in kinds:pool.setdefault(x['t'],x)
   for k in sorted(x):walk(x[k])
  elif isinstance(x,list):
   for v in x:walk(v)
 for d in documents:walk(d)
 assert set(pool)==set(kinds)
 def table(caption):return node('Table',[attr(),[None,caption],[[node('AlignDefault'),node('ColWidthDefault')]]*2,[attr(),[]],[[attr(),0,[],[[attr(),[[attr(),node('AlignDefault'),1,1,[para([string('cell')])]]]]]]],[attr(),[]]])
 result=[dict(name='caption-shape/'+kind,document=doc([table([para([string('a'),node('Space')]),pool[kind],para([node('Space'),string('b')])])]),options=BASE)for kind in kinds]
 result.append(dict(name='caption-shape/empty-lineblock',document=doc([table([node('LineBlock',[[],[string('x')],[]])])]),options=BASE))
 return result
def caption_demand_requests():
 return [dict(name='caption-demand/grid-poison',deep=dict(kind='grid-caption-poison',depth=0),options=BASE)]
