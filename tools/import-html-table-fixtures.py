#!/usr/bin/env python3
"""Observe original HTML parsing modules; no Kotlin conversion or expectations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-html-table-reference';target=root/'core/src/test/resources/html-table/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_HTML_TABLE_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=(root/'upstream/pandoc/test/Tests/Helpers.hs').read_text();assert (folder/'OriginalHelpers.hs').read_text()==original.replace('module Tests.Helpers','module OriginalHelpers',1)
sources=['tools/import-html-table-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Parsec-BSD-2-Clause.txt','licenses/tagsoup-BSD-3-Clause.txt','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/test/Tests/Readers/HTML.hs','upstream/pandoc/test/test-pandoc.hs','upstream/pandoc/test/html-reader.html','upstream/pandoc/test/html-reader.native']
sources+=['licenses/GHC-base-BSD-3-Clause.txt']
fixture_paths=subprocess.check_output(['git','ls-files','test/*.native','test/**/*.native','test/*.html*','test/**/*.html*'],cwd=root/'upstream/pandoc').decode().splitlines()
sources+=['upstream/pandoc/'+f for f in fixture_paths]
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'upstream/pandoc/src',root/'upstream/pandoc/xml-light',root/'upstream/pandoc-types/src',root/'vendor/parsec-3.1.18.0',root/'vendor/tagsoup-0.14.8'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts and '__pycache__' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert len(m['htmlUnits'])==69 and all(not isinstance(c['expected'],dict) or 'host' not in c['expected'] for c in cs)
 assert m['exposedModules']==['Text.Pandoc.Readers.HTML.Parsing','Text.Pandoc.Readers.HTML.TagCategories','Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Readers.HTML.Table']
 base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
 for name in m['exposedModules']:changed=changed.replace('                   '+name+',\n','')
 changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+name+',\n' for name in m['exposedModules']))
 assert m['originalCabalHash']==hashlib.sha256(base.encode()).hexdigest() and m['exposedCabalHash']==hashlib.sha256(changed.encode()).hexdigest()
 tracked=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').split(b'\0')
 assert m['verifiedOriginalBuildFiles']==sum(bool(x) for x in tracked)
 print('Verified',len(cs),'original HTML table observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared,'--oracle and --prepared required'
# Independently verify that the reference build changed only four module exposure lines.
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
exposed=['Text.Pandoc.Readers.HTML.Parsing','Text.Pandoc.Readers.HTML.TagCategories','Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Readers.HTML.Table']
for name in exposed:changed=changed.replace('                   '+name+',\n','')
changed=changed.replace('  exposed-modules: Text.Pandoc,\n','  exposed-modules: Text.Pandoc,\n'+''.join('                   '+name+',\n' for name in exposed))
files=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0');verified=0
for name in filter(None,files):
 expected=changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()
 assert (args.prepared/'pandoc'/name).read_bytes()==expected,name;verified+=1
with tempfile.TemporaryDirectory(prefix='kotdoc-html-table-units-') as tmp:
 capture=Path(tmp)/'units.jsonl';env=dict(os.environ,KOTDOC_HTML_TABLE_CAPTURE=str(capture))
 subprocess.run([args.oracle,'--upstream-tests'],env=env,cwd=root,check=True)
 units=[json.loads(l) for l in capture.read_text().splitlines()];assert len(units)==69
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
 if len(cs)%5000==0:print('Captured',len(cs),'HTML table observations',flush=True)
 return c
position=[list(map(ord,'source')),1,1]
def run(ts,p=('any',),pos=position,calls=9223372036854775807):return ['run',list(p),ts,pos,calls]
# Standalone numeric observations cover safeRead independently of table allocation.
numbers=['','0','-0','1','+1','- 2','-(2)','(-2)','((2))','(- (2))','1.','1.0','.5','1e2','1e-2','1e+2','0x10','0Xff','0o17','0b11','0x1.2p3','0x1p3','1_000','NaN','-NaN','Infinity','-Infinity','inf','nan','1e309','-1e309','1e-325','4.9406564584124654e-324','2.4703282292062327e-324','0.10000000000000001','9007199254740993','9223372036854775807','9223372036854775808','18446744073709551616','-18446744073709551617','1 --comment','1\u00a0','\u20031','1\u2028']
for number in numbers:
 for surrounding in ['', ' ', '\t\r\n', '\u00a0']:
  add(['numbers',surrounding+number+surrounding],'exact-safe-read-int-and-double')
rng=random.Random(0x5441424c)
for i in range(1500):
 mantissa=str(rng.randrange(10**30));number=rng.choice(['','-','+'])+rng.choice([mantissa,mantissa+'.'+str(rng.randrange(10000)),rng.choice(['0x','0o','0b'])+mantissa])
 if i%2:number+=rng.choice(['e','E'])+str(rng.randrange(-2000,2001))
 if i%3==0:number='('+number+')'
 if i%5==0:number+=' '
 add(['numbers',number],'generated-read-integer-and-floating-boundaries')
def o(n,a=()):return [0,n,list(a)]
def c(n):return [1,n]
def text(s='content'):return [2,s]
def table(rows,cols=(),prefix=(),suffix=(),a=()):return [o('table',a),*prefix,*cols,*rows,*suffix,c('table')]
def cell(kind='td',a=(),body=None,end=True):return [o(kind,a),*(body if body is not None else [text()]),*([c(kind)] if end else [])]
def row(cells,a=(),end=True):return [o('tr',a),*cells,*([c('tr')] if end else [])]
plain=['tick',['plain']]
def add_table(ts,group,content=plain,parser=None,**extra):return add(run(ts,parser or ['table',content]),group,**extra)
for ts in [[],[o('table')],[o('table'),c('table')],[o('table'),o('tbody'),c('tbody'),c('table')],table(row([])),table(row(cell())),table(row(cell('th'))),table(row(cell('th')+cell())),table(row(cell('td',end=False)+cell('td',end=False),end=False)),table(row(cell('th',end=False)+cell('td',end=False),end=False)),table(row(cell()),a=[('id','t'),('class','a b')])]:
 for wrapper in ['plain','fallback','look','eof']:
  p=['table',plain]
  if wrapper=='eof':p=['before',p,['eof']]
  elif wrapper!='plain':p=[wrapper,p]
  add_table(ts,'empty-simple-and-parser-composition',parser=p)
widths=['','*','0*','1*','2*','-1*','(2)*','x*','0x2*','18446744073709551617*','0%','-0%','10%','33.3333333333333333%','100%','150%','NaN%','Infinity%','-Infinity%','1e309%','1e-325%','(25)%','0x10%','10% ',' 10%','10px','10','x%']
styles=['width: 20%;','width:50%','width: 1.2e2%;','width:NaN%;','width:100%;color:red',' width:50%','WIDTH:50%','width :50%','width:"25%";','width:\u00a025%;','width:0%;','width:-0%;','width:10px','width:0x10%;']
for w in widths:
 for w2 in widths:
  cols=[o('col',[('width',w)]),o('col',[('width',w2)]),o('col',[('width','1*')])]
  add_table(table(row(cell()+cell()+cell()),cols),'absolute-relative-nonfinite-and-overflow-widths')
for style in styles:
 for extra in [[],[('width','50%')],[('width','bad')],[('style','width:90%')]]:
  add_table(table(row(cell()+cell()),[o('col',[('style',style),*extra])]),'column-style-prefix-and-width-precedence')
for groups,closed,blanks in itertools.product(range(4),[False,True],[False,True]):
 cols=[]
 for i in range(groups):cols += [o('colgroup',[('span','10')]),o('col',[('width',str(i+1)+'*')]),*([[2,' \n'],[3,'c']] if blanks else []),*([c('colgroup')] if closed else [])]
 add_table(table(row(cell()+cell()+cell()),cols),'colgroups-omission-and-extra-column-padding')
spans=['','0','1','2','3','-1','x','(2)','0x2','18446744073709551616','18446744073709551617','18446744073709551615']
rowspans=spans+['9223372036854775807','-9223372036854775808']
alignments=[[],[('align','left')],[('align','right')],[('align','center')],[('align','CENTER')],[('align',''),('style','text-align: right; color: red')],[('style','color: blue; text-align: center; width: 20%')],[('style','text-align:right; text-align:left')],[('text-align','left'),('data-x','value')]]
for rs,cspan,a in itertools.product(rowspans,spans,alignments):
 cells=cell('th',[('rowspan',rs),('colspan',cspan),*a])+cell('td',[('align','right')])
 rows=row(cells)+row(cell('td')+cell('td'))+row(cell('th')+cell('td'))
 add_table(table(rows),'cell-spans-alignment-row-heading-vector-and-css-cleanup')
for h,b,top,bottom,omit in itertools.product([False,True],range(3),[False,True],[False,True],[False,True]):
 head=([o('thead',[('id','head')]),*row(cell('th')+cell('th')),*([] if omit else [c('thead')])] if h else [])
 bodies=[]
 for i in range(b):bodies += [o('tbody',[('id','body'+str(i))]),*row(cell('th')+cell('td')),*row(cell('td')+cell('td')),*([] if omit else [c('tbody')])]
 foot=lambda name:[o('tfoot',[('id',name)]),*row(cell('td')+cell('td')),*([] if omit else [c('tfoot')])]
 add_table(table(head+ (foot('top') if top else [])+bodies+(foot('bottom') if bottom else []),prefix=[o('caption'),text('caption'),c('caption')]),'caption-head-body-top-and-bottom-foot-order')
# Full independent original Native corpus supplies arbitrary cell/caption blocks.
original_native=[]
for file in fixture_paths:
 if not file.endswith('.native'):continue
 document=ask(['native',(root/'upstream/pandoc'/file).read_bytes().decode('utf-8')]);original_native.append(dict(path=file,result=document))
 assert 'parseError' not in document,file
 payload=['payload',document['blocks']]
 add_table(table(row(cell()+cell())), 'all-original-native-blocks-through-cell-and-width-classification',content=payload,path=file)
 add(['simple-cells',[[document['blocks']]]],'all-original-native-simple-cell-classification',path=file)
# Walk every original HTML fixture through the original tokenizer and select all
# table spans, including nested and unterminated spans, without rewriting source.
fixture_tables=[]
for file in fixture_paths:
 if not file.endswith(('.html','.html4','.html5')):continue
 source=(root/'upstream/pandoc'/file).read_bytes().decode('utf-8')
 if '<table' not in source.lower():continue
 tags=ask(['canonical-tags',source]);starts=[]
 for i,t in enumerate(tags):
  if t[:2]==[0,'table']:starts.append(i-1 if i>0 and tags[i-1][0]==5 else i)
  elif t[:2]==[1,'table'] and starts:
   start=starts.pop();chunk=tags[start:i+1];fixture_tables.append(dict(path=file,start=start,end=i+1))
   for content in [['ignore'],['or',['nested',['para']],['ignore']]]:add_table(chunk,'all-original-html-fixture-table-spans',content=content,path=file,start=start,end=i+1)
 for start in starts:
  add_table(tags[start:],'original-unterminated-html-fixture-table-spans',content=['ignore'],path=file,start=start)
for i,u in enumerate(units):
 add_table(ask(['canonical-tags',u['input']]),'all-original-html-unit-inputs-at-table-boundary',unitIndex=i)
# Every block shape and LineBreak under non-text fields is covered directly.
leaf={'t':'LineBreak'};strx={'t':'Str','c':'x'}
inline_values=[leaf,strx,{'t':'Emph','c':[leaf]},{'t':'Note','c':[{'t':'Para','c':[leaf]}]},{'t':'Cite','c':[[{'citationId':'x','citationPrefix':[leaf],'citationSuffix':[],'citationMode':{'t':'NormalCitation'},'citationNoteNum':0,'citationHash':0}],[strx]]}]
payloads=[[],[{'t':'Plain','c':[]}],[{'t':'Para','c':[]}],[{'t':'Para','c':[strx]},{'t':'Para','c':[strx]}]]+[[{'t':kind,'c':[inline]}] for kind,inline in itertools.product(['Plain','Para'],inline_values)]+[[{'t':'CodeBlock','c':[['',[],[]],'x']}],[{'t':'Div','c':[['',[],[]],[{'t':'Para','c':[strx]}]]}]]
for blocks in payloads:
 add(['simple-cells',[[blocks]]],'all-block-and-nested-inline-simple-classification')
 add_table(table(row(cell()+cell())), 'simple-versus-normal-implicit-widths',content=['payload',blocks])
for depth in [1500,10000]:
 for br in [False,True]:add(['deep-simple',depth,br],'finite-deep-linebreak-query')
for name,line,col in itertools.product([[],[0xd800],[0xd800,0xdc00],list(map(ord,'漢🙂'))],[-1,1,9223372036854775807],[-1,1,9223372036854775807]):
 for ts in [table(row(cell())),table(row(cell('th')+cell())),[o('table'),c('table')]]:
  add(run(ts,['table',plain],[name,line,col]),'raw-source-names-and-int64-positions')
for i in range(1500):
 ts=[rng.choice([o('table'),c('table'),o('tr'),c('tr'),o('td',[('colspan',rng.choice(spans))]),o('th'),c('td'),c('th'),o('tbody'),c('tbody'),[5,2,3],[3,'c'],text(),[2,' ']]) for _ in range(rng.randrange(30))]
 if i%2:ts=[o('table'),*ts,c('table')]
 p=rng.choice([['table',plain],['fallback',['table',plain]],['look',['table',plain]],['table',['fail-after','content failure']]])
 add(run(ts,p),'generated-malformed-tables-and-backtracking')
for depth in [100,1500]:
 ts=[]
 for i in range(depth):ts += [o('table'),o('tr'),o('td')]
 ts += [text()]
 for i in range(depth):ts += [c('td'),c('tr'),c('table')]
 add(run(ts,['table-show',['nested',['plain']]]),'finite-deep-nested-table-models')
add_table(table(sum((row(cell()+cell()) for _ in range(1500)),[])),'finite-tall-table')
for first in range(0,0x110000,65536):add(['unicode-numbers',first,min(65536,0x110000-first)],'all-char-numeric-prefix-and-remainder-witnesses')
for ts in [table(row(cell()+cell())),table(row(cell('th')+cell())),table(row(cell('th')+cell('th'))+row(cell()+cell())),table(row(cell()),prefix=[o('caption'),text('cap'),c('caption')]),table(row(cell()))[:-1],table(row(cell()))+[text('tail')]]:
 for content in [['trace',['plain']],['trace',plain],['then-fail',['trace',plain],'content failure']]:
  for wrapper in ['plain','look','fallback']:
   parser=['table',content]
   if wrapper!='plain':parser=[wrapper,parser]
   add_table(ts,'external-effects-retained-across-parser-state-backtracking',parser=parser)
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),htmlUnits=units,originalNative=original_native,fixtureTables=fixture_tables,exposedModules=exposed,verifiedOriginalBuildFiles=verified,originalCabalHash=hashlib.sha256(base.encode()).hexdigest(),exposedCabalHash=hashlib.sha256(changed.encode()).hexdigest(),oracle='Complete original Pandoc table module; complete unchanged implementation; only Cabal exposure changes')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for case in [m]+cs:z.write((json.dumps(case,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original HTML table observations; SHA',m['caseHash'],flush=True)
