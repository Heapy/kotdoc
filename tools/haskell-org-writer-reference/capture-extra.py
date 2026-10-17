"""Remaining branch/demand inputs; expectations only from the pinned module."""
import pathlib,json,subprocess,itertools
from requests import n,s,attr,doc,para,BASE
r=pathlib.Path('/tmp/kotdoc-org-writer');qs=[]
def add(name,bs,opts=None):qs.append(dict(name=name,document=doc(bs),options=BASE|dict(opts or {})))
def table(caption):return n('Table',[attr(),[None,caption],[[n('AlignDefault'),n('ColWidthDefault')]],[attr(),[]],[[attr(),0,[],[[attr(),[[attr(),n('AlignDefault'),1,1,[para([s('cell')])]]]]]]],[attr(),[]]])
shapes=[n('Plain',[s('p')]),para([s('p')]),n('LineBlock',[[s('a')],[s('b')]]),n('CodeBlock',[attr('id',['code']),'x']),n('RawBlock',['raw','x']),n('BlockQuote',[para([s('x')])]),n('Div',[attr(),[para([s('x')])]]),n('BulletList',[[para([s('x')])],[para([s('y')])]]),n('OrderedList',[[2,n('DefaultStyle'),n('Period')],[[para([s('x')])]]]),n('DefinitionList',[[[s('term')],[[para([s('x')])],[para([s('y')])]]]]),n('Header',[1,attr(),[s('head')]]),n('HorizontalRule'),table([]),n('Figure',[attr(),[None,[para([s('ignored')])]],[para([s('figure')])]])]
for b in shapes:
 add('caption/'+b['t'],[table([b])]);add('figure-caption/'+b['t'],[n('Figure',[attr('id'),[None,[b]],[para([s('body')])]])])
for wrap,width,marker in itertools.product(['WrapAuto','WrapNone','WrapPreserve'],[1,4,8],['-','1.','1)','12)','١.','2x.','.', '']):
 for space in['Space','SoftBreak']:
  add('nowrap-marker/'+repr((wrap,width,marker,space)),[para([s('a'),n(space),s(marker),n('Space'),s('tail')])],dict(wrap=wrap,columns=width))
  add('nowrap-note/'+repr((wrap,width,marker,space)),[para([s('a'),n(space),n('Note',[para([s(marker)])]),s('tail')])],dict(wrap=wrap,columns=width))
def tag(name,content):return n('Span',[attr('',[],[['tag-name',name]]),content])
add('nested-tags',[n('Header',[1,attr(),[s('H'),n('Space'),tag('outer',[tag('inner',[s('in')])]),n('Note',[para([tag('note',[s('n')])])]),n('Cite',[[dict(citationId='id',citationPrefix=[tag('prefix',[])],citationSuffix=[tag('suffix',[])],citationMode=n('NormalCitation'),citationHash=0,citationNoteNum=0)],[tag('cite',[])]])]])])
add('tag-less-span-tail',[n('Header',[1,attr(),[s('H'),n('Span',[attr(),[tag('nested',[s('visible')])]])]])])
for class_ in['warning','Warning','WARNING','note','center','quote']:
 for titleattr in[attr('', ['title']),attr('id',['title']),attr('', ['title','x'])]:
  add('admonition-title/'+repr((class_,titleattr)),[n('Div',[attr('',[class_]),[n('Div',[titleattr,[para([s('title')])]]),para([s('body')])]])])
# Every pinned casefold mapping participates in Format equality and label parsing.
text=''.join(chr(v[0])for v in json.loads((r/'casefold-mappings.json').read_text()))
add('casefold/all-mappings-format',[n('RawBlock',[text,'raw']),para([n('RawInline',[text,'inline'])])])
p=r/'extra-observations.jsonl';assert not p.exists();proc=subprocess.run([str(r/'oracle')],input=''.join(json.dumps(q)+'\n'for q in qs).encode(),capture_output=True,check=True);rows=[json.loads(x)for x in proc.stdout.splitlines()];assert len(rows)==len(qs);p.write_text(''.join(json.dumps(dict(request=q,expected=e),separators=(',',':'))+'\n'for q,e in zip(qs,rows)));print(len(qs),'extra original branch observations')
