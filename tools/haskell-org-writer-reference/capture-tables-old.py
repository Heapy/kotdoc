"""Original module/table branch and verbatim Tests.Old expectations; no Kotlin."""
import pathlib,json,subprocess,itertools
from requests import n,s,attr,doc,para,BASE
ROOT=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-org-writer');qs=[]
def cell(bs,rs=1,cs=1):return[attr(),n('AlignDefault'),rs,cs,bs]
def row(cs):return[attr(),cs]
for columns,head,width,multiline,cap in itertools.product([0,1,2,3],[False,True],[None,.3],[False,True],[False,True]):
 content=[para([s('a')]),para([s('b')])]if multiline else[n('Plain',[s('cell')])]
 caption=[para([s('caption')])]if cap else[]
 table=n('Table',[attr(),[None,caption],[[n('AlignRight'),n('ColWidthDefault')if width is None else n('ColWidth',width)]]*columns,[attr(),[row([cell([para([s('head')])])]*columns)]if head else[]],[[attr(),0,[],[row([cell(content)]*columns),row([cell([para([s('tail')])])]*columns)]]],[attr(),[]]])
 qs.append(dict(name='table/'+repr((columns,head,width,multiline,cap)),document=doc([table]),options=BASE))
for caption in[[],[para([s('caption')])]]:
 qs.append(dict(name='figure/'+repr(caption),document=doc([n('Figure',[attr('id'),[None,caption],[para([n('Image',[attr(),[s('alt')],['x','']])])]])]),options=BASE))
p=r/'tables-observations.jsonl';assert not p.exists();out=subprocess.run([str(r/'oracle')],input=''.join(json.dumps(q)+'\n'for q in qs).encode(),capture_output=True,check=True);rows=[json.loads(l)for l in out.stdout.splitlines()];assert len(rows)==len(qs);p.write_text(''.join(json.dumps(dict(request=q,expected=e),separators=(',',':'))+'\n'for q,e in zip(qs,rows)));print(len(qs),'table/figure source cases',flush=True)
exe='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc'
goldens=[]
for name,golden,standalone in [('testsuite.native','writer.org',True),('tables.native','tables.org',False)]:
 source=ROOT/'upstream/pandoc/test'/name
 d=json.loads(subprocess.check_output([exe,'--emulate','-f','native','-t','json',str(source)]))
 q=dict(name='Old/'+name,document=d,options=BASE|dict(columns=80,extensions=['auto_identifiers','citations','special_strings','task_lists'],variables={'pandoc-version':''}))
 if standalone:q['template']=(ROOT/'core/src/main/resources/io/heapy/kotdoc/pandoc-data/templates/default.org').read_text()
 actual=json.loads(subprocess.check_output([str(r/'oracle')],input=(json.dumps(q)+'\n').encode()))
 expected=(ROOT/'upstream/pandoc/test'/golden).read_text();assert actual['output']==expected,(name,'original mismatch')
 goldens.append(dict(request=q,expected=actual,golden=golden))
p=r/'goldens-observations.jsonl';assert not p.exists();p.write_text(''.join(json.dumps(x,separators=(',',':'))+'\n'for x in goldens));print('2 original Old goldens exact',flush=True)
