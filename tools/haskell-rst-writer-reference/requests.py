"""Finite source-branch requests. Only the pinned original module supplies outputs."""
import itertools,copy
BASE=dict(wrap='WrapAuto',columns=72,extensions=[],referenceLinks=False,listTables=False,toc=False,tocDepth=3,numberSections=False)
def n(t,c=None):return {'t':t,**({}if c is None else {'c':c})}
def s(t):return n('Str',t)
def attr(i='',cs=None,kvs=None):return[i,cs or [],kvs or []]
def doc(bs,meta=None):return{'pandoc-api-version':[1,23,1,2],'meta':meta or {},'blocks':bs}
def para(xs):return n('Para',xs)
def note(text):return n('Note',[para([s(text)])])
def requests(original,documents):
 out=[]
 def add(name,d,opts=None,template=None):
  q=dict(name=name,document=d,options=BASE|dict(opts or {}))
  if template is not None:q['template']=template
  out.append(q)
 for r in original:
  if 'document'not in r:continue
  for wrap,refs,smart in itertools.product(['WrapAuto','WrapNone','WrapPreserve'],[False,True],[False,True]):add('original-options/'+r['name']+'/'+repr((wrap,refs,smart)),r['document'],dict(wrap=wrap,referenceLinks=refs,columns=17,extensions=['smart']if smart else []),r.get('template'))
 for i,d in enumerate(documents):add('native/'+str(i),d,dict(columns=80))
 forms=['Emph','Strong','Underline','Strikeout','Superscript','Subscript','SmallCaps','Span','Quoted','Cite','Link','Image']
 def form(kind,xs):
  return n(kind,[attr(),xs,['loc','title']]if kind in['Link','Image']else[attr(),xs]if kind=='Span'else[n('DoubleQuote'),xs]if kind=='Quoted'else[[],xs]if kind=='Cite'else xs)
 for outer,inner,text in itertools.product(forms,forms,['','x',' x ',' ','x y']):add('flatten/'+repr((outer,inner,text)),doc([para([s('a'),form(outer,[form(inner,[s(text)])]),s('b')])]))
 for a,b,kind in itertools.product(['','a',' ',"'",'"','<','[','{','(',')','–','—','\u00a0','\U0001e6c0'],['','a',' ',"'",'"','>',']','}',')','(','–','—','\u00a0','\U0001e6c0'],['Emph','Code','Math']):
  inner=n('Code',[attr(),'x'])if kind=='Code'else n('Math',[n('InlineMath'),'x'])if kind=='Math'else form(kind,[s('x')]);add('boundary/'+repr((a,b,kind)),doc([para([s(a),inner,s(b)])]))
 strings=['\\','_','*','`','|','--','---','...','....',"'",'"','a_b','a_b_','_*','_* ','a* ',' *x','a|x|','| x','é_Ж','a_\U0001e6c0','x\n\ny','\t','\u00a0']
 for text,smart in itertools.product(strings,[False,True]):add('escape/'+repr((text,smart)),doc([para([s(text)])]),dict(extensions=['smart']if smart else []))
 for fmt in['rst','tex','latex','html','RST','unsupported']:
  add('raw/'+fmt,doc([n('RawBlock',[fmt,'raw\nbody']),para([n('RawInline',[fmt,'raw'])])]))
 for ident,classes in itertools.product(['','id','x:y','_x'],[[],['title'],['note'],['container','x'],['warning','y'],['mark']]):
  h=n('Header',[2,attr(ident,classes),[s('HÉLLO!')]]);add('header-div/'+repr((ident,classes)),doc([h,n('Div',[attr(ident,classes),[n('BlockQuote',[h])]])]),dict(extensions=['auto_identifiers']))
 for classes in[[],['haskell','literate'],['sourceCode','example'],['numberLines','python'],['number-lines','haskell'],['literate','python']]:
  for ext in[[],['literate_haskell']]:add('code/'+repr((classes,ext)),doc([n('CodeBlock',[attr('',classes,[['startFrom','9']]),'x\ny'])]),dict(extensions=ext))
 for text in[' a ','\t','`x`','a\\b','']:
  for a in[attr(),attr('', ['interpreted-text'],[['role','r']]),attr('', ['interpreted-text'],[['x','y'],['role','r']])]:add('inline-code/'+repr((text,a)),doc([para([n('Code',[a,text])])]))
 for math in['x','x\ny','\n','']:
  add('math/'+repr(math),doc([para([n('Math',[n('DisplayMath'),math]),n('Space'),n('Space'),s('tail')]),para([n('Math',[n('InlineMath'),math])])]),template='$math$:$rawtex$\n$body$')
 for refs in[False,True]:
  def link(label,url,title=''):return n('Link',[attr(),label,[url,title]])
  add('link-state/'+str(refs),doc([para([link([s(' label ')],'one'),link([s(' label ')],'one'),link([s(' label ')],'two'),link([s('a:b')],'x'),link([s('https://a')],'https://a'),link([s('a@b')],'mailto:a@b'),link([n('Emph',[s('x')])],'target'),note('note')])]),dict(referenceLinks=refs))
 for cls,width,height in itertools.product([[],['align-top'],['align-middle'],['align-bottom'],['align-center'],['align-right'],['align-left'],['x','y']],['','12px','2cm','33%'],['','50%','2in']):
  image=n('Image',[attr('id',cls,[['width',width],['height',height]]),[n('Emph',[s('alt')])],['img','title']]);add('image/'+repr((cls,width,height)),doc([para([image,image,n('Link',[attr(),[image],['url','']]),n('Image',[attr(),[],['img','']]),n('Image',[attr(),[],['other','']])])]))
 def cell(text,rs=1,cs=1):return[attr(),n('AlignDefault'),rs,cs,[para([s(text),note(text)])]]
 def row(cells):return[attr(),cells]
 for columns,span,rowspan,width,listtable,limit in itertools.product([1,2,3],[1,2],[1,2],[None,.3],[False,True],[12,80]):
  head=[row([cell('head',1,span),cell('second')])];body=[row([cell('body',rowspan),cell('last')]),row([cell('foot')])];specs=[[n('AlignRight'),n('ColWidthDefault')if width is None else n('ColWidth',width)]for _ in range(columns)];table=n('Table',[attr(),[None,[para([s('caption'),note('caption')])]],specs,[attr(),head],[[attr(),0,[],body]],[attr(),[row([cell('footer')])]]]);add('table/'+repr((columns,span,rowspan,width,listtable,limit)),doc([table]),dict(listTables=listtable,columns=limit,referenceLinks=True))
 meta={'title':n('MetaInlines',[s('Title'),note('title')]),'subtitle':n('MetaBlocks',[para([s('Subtitle'),note('subtitle')])]),'body':n('MetaString','metadata-body'),'a':n('MetaInlines',[n('Math',[n('InlineMath'),'x']),n('RawInline',['latex','tex']),note('metadata')])}
 for template in[None,'$titleblock$\n$body$','$toc$:$toc-depth$:$number-sections$:$math$:$rawtex$\n$body$','$meta-json$\n$body$']:
  add('metadata/'+repr(template),doc([para([note('body'),n('Image',[attr(),[],['image','']])]),n('Header',[3,attr(),[s('H')]]),n('Header',[5,attr(),[s('nested')]])],meta),dict(toc=True,numberSections=True,tocDepth=2,referenceLinks=True),template)
 return out
def extra_requests():
 out=[]
 def add(name,bs,options=None,meta=None,template=None):
  q=dict(name='extra/'+name,document=doc(bs,meta),options=BASE|dict(options or {}))
  if template is not None:q['template']=template
  out.append(q)
 for kind,left,right in itertools.product(['Emph','Strong','Superscript','Subscript'],['Space','SoftBreak','LineBreak'],['Space','SoftBreak','LineBreak']):add('inline-whitespace/'+repr((kind,left,right)),[para([n(kind,[n(left),s('x'),n(right)])])])
 for xs in[[],[n('LineBreak')],[n('LineBreak'),n('LineBreak')],[s('x'),n('LineBreak'),n('LineBreak'),s('y')],[n('LineBreak'),s('x'),n('LineBreak')]]:add('splitBy/'+repr(xs),[para(xs)])
 image=lambda label,url='a',title='':n('Image',[attr(),[s(label)]if label is not None else [],[url,title]])
 for labels in[['image1',None,None,'image1'],[None,'image1',None,'image1'],['x','x','image1','x'],['','',None,'image1']]:
  for target in[False,True]:
   xs=[image(label,str(i%2))for i,label in enumerate(labels)]
   if target:xs=[n('Link',[attr(),[im],['target'+str(i%2),'']])for i,im in enumerate(xs)]
   add('image-collision/'+repr((labels,target)),[para(xs)])
 for first in[para([image('alt','src','fig:title')]),para([image('alt')]),n('BlockQuote',[para([s('a')])]),n('RawBlock',['rst','raw'])]:
  add('ligature-consumption/'+repr(first),[first,n('BlockQuote',[para([s('b')])]),n('BlockQuote',[para([s('c')])])])
 for start,style,delimiter in itertools.product([-9223372036854775808,-1,0,1,2,27,4000,9223372036854775807],['DefaultStyle','Example','Decimal','LowerRoman','UpperRoman','LowerAlpha','UpperAlpha'],['DefaultDelim','Period','OneParen','TwoParens']):
  if style in['LowerAlpha','UpperAlpha']and abs(start)>100000:continue
  add('ordered/'+repr((start,style,delimiter)),[n('OrderedList',[[start,n(style),n(delimiter)],[[n('Plain',[s('x')])],[n('Plain',[s('y')])]]])])
 for blocks in[[],[n('Plain',[s('x')])],[para([s('x')])],[n('Plain',[s('x')]),para([s('y')])],[para([s('x')]),n('Plain',[s('y')])],[n('BlockQuote',[para([s('x')])])]]:
  for kind in['BulletList','OrderedList','DefinitionList']:
   b=n(kind,[blocks,blocks]if kind=='BulletList'else[[1,n('DefaultStyle'),n('DefaultDelim')],[blocks,blocks]]if kind=='OrderedList'else[[[s('term')],[blocks,blocks]]]);add('tight/'+repr((kind,blocks)),[b])
 for level in[1,2,5,6,100]:
  for ext in[[],['auto_identifiers'],['ascii_identifiers'],['gfm_auto_identifiers']]:add('headlevel/'+repr((level,ext)),[n('Header',[level,attr('_id'),[s('αЖé x:y')]])],dict(extensions=ext))
 for value in[n('MetaString','t'),n('MetaInlines',[s('t')]),n('MetaBlocks',[n('Plain',[s('t')])]),n('MetaBlocks',[para([s('t')])]),n('MetaBlocks',[]),n('MetaBool',True),n('MetaList',[n('MetaString','t')])]:add('title-meta/'+repr(value),[para([s('body')])],meta={'title':value,'subtitle':value},template='$titleblock$\n$body$')
 for count,caption in itertools.product([0,1,2],[[],[para([note('cap')])]]):
  cell=[attr(),n('AlignDefault'),1,1,[]];row=[attr(),[cell,cell]];table=n('Table',[attr(),[None,caption],[[n('AlignDefault'),n('ColWidthDefault')]for _ in range(count)],[attr(),[row]],[[attr(),0,[],[row,row]]],[attr(),[]]])
  for listtable in[False,True]:add('empty-table/'+repr((count,caption,listtable)),[table],dict(listTables=listtable))
 return out
def boundary_requests():
 out=[]
 def add(name,bs,options=None):out.append(dict(name='boundary-extra/'+name,document=doc(bs),options=BASE|dict(options or {})))
 for cp in[0x10000+v for v in[32,39,42,45,46,47,58,91,93,95,96,123,124]]+[0x1e6c0,0x10ffff,0x1f600]:
  text=chr(cp)
  for smart in[False,True]:add('codepoint/'+repr((cp,smart)),[para([s(text+'_*'+text),n('Emph',[s('x')]),s(text),n('Strong',[s('y')]),s(text)])],dict(extensions=['smart']if smart else []))
 for a,b in itertools.product(['rst','RST','tex','TEX','latex','LATEX','html'],['rst','RST','tex','TEX','latex','LATEX','html']):add('raw-case/'+a+'/'+b,[n('RawBlock',[a,'x']),para([n('RawInline',[b,'y'])])])
 for withLinks in[False,True]:
  image=n('Image',[attr(),[s('same'),note('image-alt-note')],['img','']]);link=n('Link',[attr(),[s('same'),note('link-label-note'),image],['url','title']]);add('state-labels/'+str(withLinks),[para([link,link,image,note('body-note')])],dict(referenceLinks=withLinks))
 return out
def caption_requests(documents):
 kinds=['Plain','Para','LineBlock','CodeBlock','RawBlock','BlockQuote','OrderedList','BulletList','DefinitionList','Header','HorizontalRule','Table','Figure','Div'];pool={}
 def walk(x):
  if isinstance(x,dict):
   if x.get('t')in kinds:pool.setdefault(x['t'],x)
   for k in sorted(x):walk(x[k])
  elif isinstance(x,list):
   for v in x:walk(v)
 for d in documents:walk(d)
 pool['EmptyLineBlock']=n('LineBlock',[])
 out=[]
 for k in kinds+['EmptyLineBlock']:
  caption=[pool[k]];cell=[attr(),n('AlignDefault'),1,1,[n('Plain',[s('cell')])]];row=[attr(),[cell,cell]];table=n('Table',[attr(),[None,caption],[[n('AlignDefault'),n('ColWidthDefault')]]*2,[attr(),[]],[[attr(),0,[],[row]]],[attr(),[]]]);out.append(dict(name='caption-shape/'+k,document=doc([table]),options=BASE))
 return out
def casefold_requests(mapping):
 # Exercise every actually mapped lower-case codepoint from the pinned text table.
 text=''.join(chr(int(k,16))for k in sorted(mapping['lower'],key=lambda k:int(k,16)))
 return[dict(name='raw-format/all-pinned-lowercase-mappings',document=doc([n('RawBlock',[text,'body'])]),options=BASE)]
def identifier_profile_requests(old,new):
 old={int(k,16):v for k,v in old['lower'].items()};new={int(k,16):v for k,v in new['lower'].items()};points=sorted(p for p in old.keys()|new.keys()if old.get(p,chr(p))!=new.get(p,chr(p)))
 return[dict(name='identifier-profile/'+repr(ext),document=doc([n('Header',[1,attr(chr(p)),[s(chr(p))]])for p in points]),options=BASE|dict(extensions=ext))for ext in [['auto_identifiers'],['auto_identifiers','ascii_identifiers'],['gfm_auto_identifiers']]]
def display_math_requests():
 math=lambda x,kind='DisplayMath':n('Math',[n(kind),x]);space=n('Space');soft=n('SoftBreak')
 cases=[('two-spaced',[math('a'),space,space,s('x'),math('b'),space,space,s('y')],'WrapAuto'),('first-unspaced',[math('a'),s('x'),math('b'),space,space,s('y'),math('c'),space,s('z')],'WrapAuto'),('inline-softbreak',[math('inline','InlineMath'),space,math('a'),soft,space,s('x'),math('b'),space,soft,s('y')],'WrapPreserve')]
 return[dict(name='display-math-stop/'+name,document=doc([para(xs)]),options=dict(wrap=wrap,columns=72,extensions=[]))for name,xs,wrap in cases]
