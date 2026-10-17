"""Source-branch inputs only. All expected outputs come from the pinned module."""
import itertools
BASE=dict(wrap='WrapAuto',columns=72,extensions=[])
def n(t,c=None):return dict(t=t,**({}if c is None else dict(c=c)))
def s(x):return n('Str',x)
def attr(i='',cs=(),kv=()):return[i,list(cs),list(kv)]
def doc(bs,meta=None):return{'pandoc-api-version':[1,23,1,2],'meta':meta or {},'blocks':bs}
def para(xs):return n('Para',xs)
def requests(original):
 out=[]
 def add(name,bs,options=None,template=None,meta=None):
  q=dict(name=name,document=doc(bs,meta),options=BASE|dict(options or {}))
  if template is not None:q['template']=template
  out.append(q)
 def note(x):return n('Note',[para([s(x)])])
 def form(t,x):return n(t,[attr(),x,['target','title']]if t in['Link','Image']else[attr(),x]if t=='Span'else[n('DoubleQuote'),x]if t=='Quoted'else[[],x]if t=='Cite'else x)
 for r in original:
  for wrap,ext in itertools.product(['WrapAuto','WrapNone','WrapPreserve'],[[],['smart'],['special_strings'],['smart_quotes'],['citations','task_lists']]):
   q=dict(name='original-options/'+r['name']+'/'+repr((wrap,ext)),document=r['document'],options=BASE|dict(wrap=wrap,extensions=ext,columns=12));out.append(q)
 for outer,inner in itertools.product(['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps','Span','Quoted','Cite','Link','Image'],repeat=2):add('nested/'+outer+'/'+inner,[para([s('a'),form(outer,[form(inner,[s('*x|')])]),s('z')])])
 for text,wrap,ext in itertools.product(['','*','#','|','a* #x|','\u00ad–—’…','a\nb','a\t b','\U0001e6c0*'],['WrapAuto','WrapNone','WrapPreserve'],[[],['smart'],['special_strings'],['smart_quotes']]):add('escape/'+repr((text,wrap,ext)),[para([s(text),n('Space'),s('*tail')])],dict(wrap=wrap,extensions=ext,columns=4))
 for fmt,txt in itertools.product(['org','tex','latex','html','ORG','unsupported'],['x','\\begin{x}\ny','']):add('raw/'+repr((fmt,txt)),[n('RawBlock',[fmt,txt]),para([n('RawInline',[fmt,txt])])])
 for cl in[[],['drawer'],['drawer','foo'],['drawer','foo','bar'],['drawer','drawer','foo'],['center'],['QUOTE'],['Warning'],['warning'],['tip','quote','foo'],['cell','code'],['output','execute_result']]:
  for ident,kv in itertools.product(['','id'],[[],[['k','v'],['x','y']]]):
   title=n('Div',[attr('', ['title']),[para([s('title')])]])
   add('div/'+repr((cl,ident,kv)),[n('Div',[attr(ident,cl,kv),[title,para([s('body')])]])])
 for ext in[[],['citations']]:add('refs/'+repr(ext),[n('Div',[attr('refs'),[para([s('hidden')])]])],dict(extensions=ext))
 add('ipynb/code',[n('Div',[attr('', ['cell','code']),[n('CodeBlock',[attr('', ['python']),'x']),para([s('tail')])]])])
 add('ipynb/output',[n('Div',[attr('', ['output','execute_result']),[n('CodeBlock',[attr(),'x\ny\n'])]])])
 for level,cols in itertools.product([-1,0,1,3,999,1000],[1,20,80]):
  tag=lambda x:n('Span',[attr('',[],[['tag-name',x]]),[s('hidden')]])
  add('header/'+repr((level,cols)),[n('Header',[level,attr('id',['cls'],[['x','v']]),[s('title'),n('Space'),tag('foo'),form('Emph',[tag('bar')])]])],dict(columns=cols))
 for cl,kv,text in itertools.product([[],['example'],['c'],['commonlisp'],['r'],['bash'],['python','code'],['numberLines','continuedSourceBlock','haskell'],['numberLines']],[[],[['startFrom',' 9  '],['org-language','unused'],['x','y']]],['','x','x\n','*x\n #+x\n\t,,*y\n']) :add('code/'+repr((cl,kv,text)),[n('CodeBlock',[attr('id',cl,kv),text])])
 for lines in[[],[[]],[[],[]],[[s('a')],[],[s('b')],[]],[[s('x')],[s('y')]]]:add('verse/'+repr(lines),[n('LineBlock',lines)])
 for start,delim,style in itertools.product([-9223372036854775808,-1,0,1,2,9223372036854775807],['DefaultDelim','Period','OneParen','TwoParens'],['DefaultStyle','LowerRoman']):add('ordered/'+repr((start,delim,style)),[n('OrderedList',[[start,n(style),n(delim)],[[n('Plain',[s('one')])],[n('Plain',[s('two')])]]])])
 for item,ext in itertools.product([[],[n('Plain',[s('x')])],[para([s('x')])],[n('BlockQuote',[para([s('x')])])],[n('Plain',[s('☐'),n('Space'),s('task')])],[n('Plain',[s('☒'),n('Space'),s('task')])],[n('Plain',[s('☐')])]], [[],['task_lists']]):
  for kind in['BulletList','OrderedList','DefinitionList']:add('list/'+repr((kind,item,ext)),[n(kind,[item,item]if kind=='BulletList'else[[2,n('DefaultStyle'),n('TwoParens')],[item,item]]if kind=='OrderedList'else[[[s('term')],[item,item]]])],dict(extensions=ext))
 for url,label in itertools.product(['','#id','/x','./x','../x','file:x','https:x',':x','x_y:z','α:x','a[b]','a\\[b]','a\\','a\\x','x'],['x','a]]]', ']', 'a\\b', 'https:x']):add('link/'+repr((url,label)),[para([n('Link',[attr(),[s(label)],[url,'title']]),n('Image',[attr(),[note('ignored')],[url,'']])])])
 for mode,pref,suffix in itertools.product(['NormalCitation','AuthorInText','SuppressAuthor'],[[],[s('see'),note('prefix')]],[[],[s('p.'),n('Space'),s('3')],[s('{chapter 3}')],[s('other')]]):
  c=dict(citationId='id',citationPrefix=pref,citationSuffix=suffix,citationMode=dict(t=mode),citationNoteNum=0,citationHash=0)
  for ext in[[],['citations']]:add('cite/'+repr((mode,pref,suffix,ext)),[para([n('Cite',[[c],[s('fallback')]])])],dict(extensions=ext))
 add('notes/nested',[para([note('one'),n('Note',[para([note('nested')])]),note('three')])])
 for ext in[[],['smart_quotes'],['special_strings'],['smart']]:add('metadata/'+repr(ext),[para([n('Math',[n('DisplayMath'),'x']),note('body')])],dict(extensions=ext),"$options$:$option-special-strings$:$math$\n$title$\n$body$",{'title':n('MetaInlines',[s('title'),note('meta')])})
 return out
