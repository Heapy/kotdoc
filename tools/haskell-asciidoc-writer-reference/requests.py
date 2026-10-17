"""Inputs derived from every source branch, never from Kotlin output."""
import itertools
BASE=dict(wrap='WrapAuto',columns=72,extensions=[])
def n(t,c=None):return dict(t=t,**({}if c is None else dict(c=c)))
def s(x):return n('Str',x)
def attr(i='',cs=(),kv=()):return[i,list(cs),list(kv)]
def doc(bs,meta=None):return{'pandoc-api-version':[1,23,1,2],'meta':meta or {},'blocks':bs}
def para(xs):return n('Para',xs)
def cell(bs,rs=1,cs=1,alignment='AlignDefault'):return[attr(),n(alignment),rs,cs,bs]
def row(cs):return[attr(),cs]
def requests(original):
 out=[]
 def add(name,bs,options=None,template=None,meta=None):
  for legacy in [False,True]:
   q=dict(name=name+('/legacy'if legacy else'/modern'),legacy=legacy,document=doc(bs,meta),options=BASE|dict(options or {}))
   if template is not None:q['template']=template
   out.append(q)
 def form(t,x):return n(t,[attr(),x,['http://x/a--b','title']]if t in['Link','Image']else[attr(),x]if t=='Span'else[n('DoubleQuote'),x]if t=='Quoted'else[[],x]if t=='Cite'else x)
 for outer,inner in itertools.product(['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps','Span','Quoted','Cite','Link','Image'],repeat=2):add('inline/nested/'+outer+'/'+inner,[para([s('a'),form(outer,[form(inner,[s('x,+|')])]),s('z')])])
 for text,wrap,columns in itertools.product(['','{+`*#_<>[]\\|','a++b|c','a\nb','a\tb','\U0001e6c0*','☐','A. item','A.  item','I. item','I.  item','II. item','(1) x','#. x','[x]','[]','[','1.\u00a0x'],['WrapAuto','WrapNone','WrapPreserve'],[1,12]):add('text/'+repr((text,wrap,columns)),[para([s(text),n('SoftBreak'),s('tail')])],dict(wrap=wrap,columns=columns))
 for fmt,text in itertools.product(['asciidoc','asciidoctor','ASCIIDOC','html','unsupported'],['','raw\nx']):add('raw/'+repr((fmt,text)),[n('RawBlock',[fmt,text]),para([n('RawInline',[fmt,text])])])
 for cl,title,ident in itertools.product([[],['sidebar'],['SIDEBAR'],['note'],['Note'],['warning','other'],['section'],['other']],[None,'title','note','sidebar','  sidebar  '],['','id']):
  bs=[]if title is None else[n('Div',[attr('', ['title']),[para([s(title)])]])]
  add('div/'+repr((cl,title,ident)),[n('Div',[attr(ident,cl),bs+[para([s('body')])]])])
 for classes,text in itertools.product([[],['haskell'],['numberLines'],['numberLines','numberLines','python'],['python','numberLines']],['','x','x\n','a\nb']):add('code/'+repr((classes,text)),[n('CodeBlock',[attr('id',classes),text])])
 for lines in[[],[[]],[[],[]],[[s('a')],[],[s('b')],[]]]:add('lineblock/'+repr(lines),[n('LineBlock',lines)])
 for kind in ['BlockQuote','Div']:
  inner=n(kind,[para([s('x')])]if kind=='BlockQuote'else[attr(),[para([s('x')])]])
  add('nestedblock/'+kind,[n('BlockQuote',[inner])])
 for blocks in[[],[para([s('x')])],[n('Plain',[s('☐'),n('Space'),s('task')])],[para([n('Math',[n('DisplayMath'),'a'])])],[n('CodeBlock',[attr(),'x'])],[para([s('x')]),n('CodeBlock',[attr(),'x']),n('BulletList',[[para([s('nested')])]])]]:
  for kind in['BulletList','OrderedList','DefinitionList']:
   add('list/'+repr((kind,blocks)),[n(kind,[blocks,blocks]if kind=='BulletList'else[[2,n('UpperRoman'),n('TwoParens')],[blocks,blocks]]if kind=='OrderedList'else[[[s('term')],[blocks,blocks]]])])
 for start,style in itertools.product([-1,0,1,2],['DefaultStyle','Example','Decimal','LowerAlpha','UpperAlpha','LowerRoman','UpperRoman']):add('ordered/'+repr((start,style)),[n('OrderedList',[[start,n(style),n('DefaultDelim')],[[n('Plain',[s('x')])]]])])
 for url,label in itertools.product(['','mailto:x@y','http://x','https://x/a--b','irc:x','ftp:x','file:x','relative','HTTP://x','x y','x, y','.name','.a.b','a/.b','dir/x.png'],['','x','a,b','a,,b','http://x']):add('media/'+repr((url,label)),[para([n('Link',[attr(),[s(label)],[url,'title']]),n('Image',[attr(),[s(label)],[url,'title']])])])
 for dim,value,dpi in itertools.product(['width','height'],['10px','50%','1in','2em','bogus'],[72,96]):add('dimension/'+repr((dim,value,dpi)),[para([n('Image',[attr('',[],[[dim,value]]),[],['dir/img.png','']])])],dict(dpi=dpi))
 for bs in[[],[para([s('x')])],[n('Plain',[s('x')])],[para([s('one')]),para([s('two')])],[n('RawBlock',['unsupported','ignored'])]]:add('note/'+repr(bs),[para([n('Note',bs)])])
 for title,author,date,toc in itertools.product([False,True],repeat=4):
  meta={k:n('MetaString',v)for k,v,on in[('title','title',title),('author','author',author),('date','date',date)]if on}
  add('meta/'+repr((title,author,date,toc)),[para([n('Math',[n('InlineMath'),'x']),n('Math',[n('DisplayMath'),'y'])])],dict(toc=toc),'$titleblock$|$toc$|$math$\n$title$:$author$:$date$\n$body$',meta)
 for count,span,empty,content in itertools.product([1,2],[1,2,3],[False,True],[[],[n('Plain',[s('a|b')])],[para([s('x')]),para([s('y')])]]):
  cs=[cell(content,span,1)]*count;rs=[row(cs),row([]if empty else cs),row(cs)]
  for width in[None,.3]:
   table=n('Table',[attr(),[None,[para([s('caption')])]],[[n('AlignRight'),n('ColWidthDefault')if width is None else n('ColWidth',width)]]*count,[attr(),rs],[[attr(),0,[],rs]],[attr(),rs]])
   add('table/'+repr((count,span,empty,content,width)),[table])
 return out
