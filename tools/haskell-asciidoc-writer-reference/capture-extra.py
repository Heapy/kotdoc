#!/usr/bin/env python3
import pathlib,json,gzip,subprocess,itertools
from requests import n,s,attr,doc,para,cell,row,BASE
HERE=pathlib.Path(__file__).resolve().parent;TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer');qs=[]
def add(name,bs,options=None,meta=None,template=None):
 for legacy in [False,True]:
  q=dict(name=name+'/'+str(legacy),legacy=legacy,document=doc(bs,meta),options=BASE|dict(options or{}))
  if template is not None:q['template']=template
  qs.append(q)
def image(src='x.png',alt=None,kv=(),title='title'):return n('Image',[attr('img',['class'],kv),[]if alt is None else alt,[src,title]])
caption=[n('RawBlock',['unsupported','caption diagnostic']),para([n('Math',[n('InlineMath'),'x'])])]
for body in [[],[n('Plain',[image()])],[para([image()])],[n('Plain',[image()]),n('Plain',[image('y.png')])],[para([s('before')]),n('Plain',[image()]),para([s('after')])]]:add('figure/'+repr(body),[n('Figure',[attr('figure-id',['ignored']),[None,caption],body])],template='$math$\n$body$')
for src in ['.','..','/.','/..','.a','/a/.hidden','a/.b.c','dir.with.dots/no-dots','dir.with.dots/no-dots/','a.b/c.d','a\\b.c','http://x/y.png?q=a.b','a\nb.png']:
 add('filepath/'+repr(src),[para([image(src),image(src,[s('')]),image(src,[s('alt')])])])
for width,height in itertools.product(['10px','50%','2in'],repeat=2):add('image-dimensions/'+repr((width,height)),[para([image(kv=[['width',width],['height',height]])])])
for fmt in ['aſciidoc','AsCiIdOc','asciidoc','asciidoc_legacy']:
 add('format-casefold/'+fmt,[n('RawBlock',[fmt,'raw']),para([n('RawInline',[fmt,'inline'])])])
for raw in ['[x]','[]','A. x','A.  x','IV. x','IV.  x','text']:
 add('paragraph-literal/'+repr(raw),[para([n('RawInline',['asciidoc',raw])])])
for text in ['a`b','a|b','++{x}','a\nb']:
 add('inline-code/'+repr(text),[para([n('Code',[attr(),text])])])
for ext in [[],['auto_identifiers'],['auto_identifiers','gfm_auto_identifiers'],['auto_identifiers','ascii_identifiers']]:
 headers=[n('Header',[1,attr('h'),[s('H')]]),n('Header',[1,attr('h'),[s('H')]])]
 meta={'title':n('MetaBlocks',[n('Header',[1,attr(),[s('H')]])])}
 add('metadata-header-state/'+repr(ext),headers,dict(extensions=ext),meta,'$title$\n$body$')
for al,width,wrap in itertools.product(['AlignLeft','AlignCenter','AlignRight','AlignDefault'],[-.3,0,.3,1e308],['WrapAuto','WrapNone']):
 def rr(label):return row([cell([n('Plain',[n('RawInline',['unsupported',label]),s('x')])],2,1,al)])
 heads=[rr('H1'),rr('H2')];bodies=[rr('B1'),rr('B2')];feet=[rr('F1'),rr('F2')]
 table=n('Table',[attr('table',['class']),[None,[para([s('cap|')])]],[[n(al),n('ColWidth',width)]],[attr('head'),heads],[[attr('body'),1,[],bodies]],[attr('foot'),feet]])
 add('table-order/'+repr((al,width,wrap)),[table],dict(wrap=wrap,columns=3))
# Full nested cutoff, including the whole original Table value in ordered logs.
inner=n('Table',[attr(),[None,[]],[[n('AlignDefault'),n('ColWidthDefault')]],[attr(),[]],[[attr(),0,[],[row([cell([para([s('one')]),para([s('two')])])])]]],[attr(),[]]])
outer=n('Table',[attr(),[None,[]],[[n('AlignDefault'),n('ColWidthDefault')]],[attr(),[]],[[attr(),0,[],[row([cell([inner])])]]],[attr(),[]]])
add('nested-table-cutoff',[outer])
raw=subprocess.run([str(TMP/'oracle')],input=b''.join((json.dumps(q)+'\n').encode()for q in qs),capture_output=True,check=True).stdout;answers=[json.loads(x)for x in raw.splitlines()];assert len(qs)==len(answers)
p=HERE/'fixtures/extra.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(dict(request=q,expected=a))+'\n').encode()for q,a in zip(qs,answers)),mtime=0));print(len(qs),'original extra state/order/helper observations captured')
