#!/usr/bin/env python3
"""Capture original writer metadata/context helpers with independent dictionaries."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/writers/metadata.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_WRITER_METADATA_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-writer-metadata-fixtures.py','tools/prepare-writer-metadata-reference.py','tools/prepare-writer-html-reference.py',
 'tools/haskell-model-show-reference/PandocOptionsModels.hs','tools/haskell-model-show-reference/TemplateAdapter.hs',
 'upstream/pandoc/src/Text/Pandoc/Options.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs',
 'upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/src/Text/Pandoc/XML.hs','upstream/pandoc/src/Text/Pandoc/CSS.hs',
 'core/src/test/resources/native/upstream.json','vendor/tagsoup-0.14.8/src/Text/HTML/TagSoup/Entity.hs',
 'licenses/pandoc-GPL-2.0.md','licenses/pandoc-types-BSD-3-Clause.txt','licenses/Doctemplates-BSD-3-Clause.txt','licenses/DocLayout-BSD-3-Clause.txt',
 'licenses/Skylighting-core-BSD-3-Clause.txt','licenses/Utf8-string-BSD-3-Clause.txt','licenses/Commonmark-BSD-3-Clause.txt','licenses/tagsoup-BSD-3-Clause.txt']
for folder in ['tools/haskell-writer-metadata-reference','tools/haskell-writer-html-reference','upstream/pandoc-types/src',
 'vendor/doclayout-0.6','vendor/doctemplates-0.11.1','vendor/skylighting-core-0.15/src','vendor/utf8-string-1.0.2','vendor/commonmark-0.3.1']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
originals=json.loads((root/'core/src/test/resources/native/upstream.json').read_text())
metas=[(r['path'],r['json']['meta']) for r in originals if r['accepted']]
sources+=['upstream/pandoc/'+path for path,_ in metas]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 subprocess.run(['python3','tools/prepare-writer-metadata-reference.py','--check'],cwd=root,check=True)
 subprocess.run(['python3','tools/prepare-writer-html-reference.py','--check'],cwd=root,check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert m['targets']==dict(collections.Counter(c['target'] for c in cs))
 assert [(c['path'],c['input'][2]) for c in cs if c.get('originalMeta') and c['target']=='text' and c['input'][0]]==metas
 for kind in ['text','string','wrapped']:
  assert [cp for c in cs if c['op']=='unicode' and c['target']==kind for cp in c['input']]==list(range(0x110000))
 print(f'Verified {len(cs)} original metadata/context captures, {len(metas)} upstream metadata maps, all Haskell Chars on three targets and {len(set(sources))} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(op,v,kind='text',**extra):
 proc.stdin.write(json.dumps([op,kind,v],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,v)
 result=json.loads(line)
 assert not isinstance(result,dict) or 'error' not in result,(op,kind,v,result)
 cs.append(dict(op=op,target=kind,input=v,expected=result,**extra))
 if len(cs)%1000==0:print('Captured',len(cs),'metadata observations',flush=True)
def tag(t,c=None):return {'t':t,**({'c':c} if c is not None else {})}
def mv(t,c):return tag('Meta'+t,c)
def str_(s):return tag('Str',s)
def val(doc):return ['simple',doc]
def context(entries=()):return ['map',list(entries)]
kinds=['text','string','wrapped']
vars=context([['title',val('override')],['meta-json',val('wrong')],['lang',val('fr-FR')],['list',['list',[val('one'),val('two')]]]])
defaultBlock=['prefix','> ',['concat',['text',1,'a'],['concat',['space'],['bold',['text',1,'b']]]]]
defaultInline=['concat',['text',1,'i'],['concat',['newline'],['link','u',['text',1,'j']]]]
for path,m in metas:
 for kind,enabled in itertools.product(kinds,[False,True]):add('metadata',[enabled,vars,m,defaultBlock,defaultInline],kind,originalMeta=True,path=path)
 for key in list(m)+['missing']:add('lookup',[key,m],path=path,originalLookup=True)
texts=['','a','a  b\r\n c','\t\n','\v\f\u00a0\u2007\u2028','Привет😀','"\\\0\x1b\x7f','\ufeff\ufffe\uffff']
for s in texts:
 m={'z':mv('String',s),'a':mv('Map',{'z':mv('String',s),'a':mv('Blocks',[tag('Para',[str_(s)])])}),
    'mid':mv('List',[mv('Bool',False),mv('Inlines',[]),mv('String',s),mv('List',[]),mv('Map',{})])}
 for kind,enabled in itertools.product(kinds,[False,True]):add('metadata',[enabled,context(),m,defaultBlock,defaultInline],kind,metadataEdge=True)
metaValues=[mv('String',''),mv('String','en-US'),mv('String','a b'),mv('Inlines',[]),mv('Inlines',[str_('en-US')]),mv('Inlines',[str_(''),tag('Space')]),
 mv('Blocks',[]),mv('Blocks',[tag('Plain',[])]),mv('Blocks',[tag('Plain',[str_('en-US')])]),mv('Blocks',[tag('Para',[str_('en-US')])]),
 mv('Blocks',[tag('Para',[str_('en'),tag('Space'),str_('US')])]),mv('Blocks',[tag('Para',[str_('en-US')]),tag('Plain',[])]),
 mv('Bool',False),mv('Bool',True),mv('List',[]),mv('List',[mv('String','en-US')]),mv('Map',{}),mv('Map',{'x':mv('String','en-US')})]
variableValues=[None,['null'],['bool',False],['bool',True],val(['empty']),val(''),val('de-DE'),val(['concat',['raw',-7,'de'],['raw',0,'-DE']]),['list',[val('de-DE')]],context([['x',val('de-DE')]])]
for m,var in itertools.product(metaValues,variableValues):
 meta={'lang':m};vs=context([] if var is None else [['lang',var]])
 add('lookup',['lang',meta],metadataLookup=True)
 add('metadata',[True,vs,meta,['empty'],['empty']],languageEdge=True)
# Every concrete Doc constructor survives variable lifting, independently of
# FromText, ToText, and the cached width stored in the original document.
base=[False,False,0,False,None,None,None]
part=[None,base,'é😀']
docs=[['empty'],['space'],['cr'],['newline'],['blanklines',2],['text',-7,'a'],['vfill',1,'|'],['cooked',2,[part]],
 ['block',2,[[part],[]]],['prefix','> ',['text',1,'x']],['before',['text',1,'x']],['flush',['text',1,'x']],['after','start'],
 ['concat',['text',1,'a'],['text',1,'b']],['styled',['weight',True],['text',1,'x']],['link','url',['text',1,'x']]]
oldValues=[['null'],['bool',False],['bool',True],val('old'),val(['empty']),['list',[]],['list',[val('one'),val('two')]],context(),context([['a',['null']]])]
conversions=[['val',x] for x in oldValues]+[['target',s] for s in texts]+[['doc',d] for d in docs]+[['bool',True],['bool',False],
 ['list',[]],['list',[val('one')]],['list',[val('one'),['list',[val('two')]]]],['map',context()],['map',context([['a',val('x')],['z',['bool',False]]])]]
conversions += [['json',x] for x in [None,True,False,'','JSON',1,1.5,-3,10**100,1e-50,{'x':1,'y':['text',2.5,False,None]},[1,'x',False,None]]]
for kind,mode,old,x in itertools.product(kinds,['set','reset','default'],[None]+oldValues,conversions):
 add('field',[mode,context([['other',val('preserved')]]+([] if old is None else [['key',old]])),'key',x],kind,fieldGrid=True)
for kind,d in itertools.product(kinds,docs):
 c=context([['nested',context([['list',['list',[val(d),['null'],['bool',False]]]]])],['meta-json',val('metadata value')]])
 vs=context([['variable',val(d)],['meta-json',val('variable value')]])
 add('add',[vs,c],kind,docConstructor=True)
for kind in kinds:
 for a in [['',[],[]],['id',['a','b'],[['lang','en'],['label','x'],['custom','<&']]],['\"<&',['a\nb',''],[['markdown','true'],['epub:type','footnote']]]]:
  add('html-dictionary',['span',a],kind,fromTextDictionary=True)
 for n in [0,1,40,1000,5000]:add('deep',n,kind,deep=True)
for reason in ['default-present','missing-field','no-template']:add('partial',reason,partial=True)
rng=random.Random(0x4E7A)
def genMeta(depth=0):
 case=rng.randrange(6 if depth<3 else 4)
 if case==0:return mv('Bool',bool(rng.randrange(2)))
 if case==1:return mv('String',rng.choice(texts))
 if case==2:return mv('Inlines',[str_(rng.choice(texts)),tag('Note',[tag('Para',[str_('note')])])])
 if case==3:return mv('Blocks',[tag(rng.choice(['Para','Plain']),[tag('Quoted',[tag('DoubleQuote'),[str_(rng.choice(texts))]])])])
 if case==4:return mv('List',[genMeta(depth+1) for _ in range(rng.randrange(5))])
 return mv('Map',{key:genMeta(depth+1) for key in rng.sample(['z','a','😀','\ue000','lang','meta-json'],rng.randrange(7))})
for i in range(600):
 m={key:genMeta() for key in rng.sample(['a','z','😀','\ue000','lang','meta-json','title'],rng.randrange(8))}
 add('metadata',[bool(rng.randrange(2)),vars,m,rng.choice(docs),rng.choice(docs)],rng.choice(kinds),seeded=True)
 for key in list(m)+['missing']:add('lookup',[key,m],seededLookup=True)
for start in range(0,0x110000,4096):
 ps=list(range(start,min(start+4096,0x110000)))
 for kind in kinds:add('unicode',ps,kind,exhaustiveUnicode=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),targets=dict(collections.Counter(c['target'] for c in cs)),oracle='GHC 9.12.2 original Pandoc 3.12.1 metadata/context bodies; doctemplates 0.11.1, DocLayout 0.6, pandoc-types 1.23.1.2; Text/String/independent Wrapped dictionaries')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'metadata observations; SHA',meta['caseHash'],flush=True)
