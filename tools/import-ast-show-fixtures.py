#!/usr/bin/env python3
"""Independent Definition Show/showsPrec observations from original pandoc-types."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/ast-show/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_AST_SHOW_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
source=root/'upstream/pandoc-types/test/test-pandoc-types.hs'
part=source.read_text().split('t_meta ::',1)[1].split('-- headers and rows are padded',1)[0]
part='t_meta ::'+part
original=(root/'tools/haskell-ast-show-reference/OriginalExamples.hs').read_text()
assert original.split('t_meta ::',1)[1].split('\nentry ::',1)[0]==part.split('t_meta ::',1)[1]
assert len(re.findall(r'^t_\w+ ::',part,re.M))==56
native=json.loads((root/'core/src/test/resources/native/upstream.json').read_text())
assert all(hashlib.sha256((root/'upstream/pandoc'/x['path']).read_bytes()).hexdigest()==x['sha256'] for x in native)
sources=['tools/import-ast-show-fixtures.py','core/src/test/resources/native/upstream.json','licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt']
# The whole pinned source package includes its original Arbitrary and unit tests.
sources+=['upstream/pandoc-types/'+s for s in subprocess.check_output(['git','-C',str(root/'upstream/pandoc-types'),'ls-files'],text=True).splitlines()]
sources+=['upstream/pandoc/'+x['path'] for x in native]
for folder in ['tools/haskell-ast-show-reference','vendor/ghc-9.12.2','vendor/text-2.1.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
sources=sorted(set(sources))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 documents=[c for c in cs if c.get('document')]
 accepted=[x for x in native if x['accepted']]
 assert m['documents']==len(accepted)
 assert [(c['path'],c['request'][3]) for c in documents]==[(x['path'],x['json']) for x in accepted for _ in range(2)]
 assert len([c for c in cs if c.get('originalExample')])==56*6
 assert '../../upstream/pandoc-types' in (root/'tools/haskell-ast-show-reference/cabal.project').read_text()
 print(f'Verified {len(cs)} original AST Show observations, {len(accepted)} Native documents and {len(sources)} hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def call(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(*req,**extra):
 cs.append(dict(request=req,expected=call(req),**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'AST Show observations',flush=True)
for c in call(['examples']):cs.append(dict(c,originalExample=True))
prec=[-1,0,6,7,10,11,12]
def shows(kind,v,**extra):
 for p in prec:add('show',kind,p,v,**extra)
def tag(name,c=None):return dict(t=name,**({} if c is None else dict(c=c)))
for x in native:
 if x['accepted']:
  for p in [0,11]:add('show','pandoc',p,x['json'],document=True,path=x['path'])
nums=[-2**63,-1,0,1,2**63-1]
texts=['','x','a"\\\n\t','\x0eH','\x7f','🙂1','漢字','ß','Σςσ','\ue000','\U00010000']
attrs=[['',[],[]],['id',['a','a'],[['k','1'],['k','2']]],[texts[2],texts,[[x,y] for x,y in zip(texts,texts[::-1])]]]
ils=[tag('Str',t) for t in texts]+[tag(n) for n in ['Space','SoftBreak','LineBreak']]
atomicIls=ils[:]
for name in ['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps']:
 for content in [[],[ils[0]],atomicIls]:ils.append(tag(name,content))
for q in ['SingleQuote','DoubleQuote']:
 for content in [[],ils[:3]]:ils.append(tag('Quoted',[tag(q),content]))
cites=[]
for t,mode,n in itertools.product(texts,['AuthorInText','SuppressAuthor','NormalCitation'],nums):
 cites.append(dict(citationId=t,citationPrefix=ils[:3],citationSuffix=ils[3:6],citationMode=tag(mode),citationNoteNum=n,citationHash=-n if n!=-2**63 else n))
for a,t in itertools.product(attrs,texts):ils.append(tag('Code',[a,t]));ils.append(tag('Span',[a,ils[:3]]))
for t,typ in itertools.product(texts,['DisplayMath','InlineMath']):ils.append(tag('Math',[tag(typ),t]))
for fmt,t in itertools.product(['HTML','html','ß','Σςσ',''],texts):ils.append(tag('RawInline',[fmt,t]))
for a,t in itertools.product(attrs,texts):
 for name in ['Link','Image']:ils.append(tag(name,[a,ils[:3],[t,t]]))
for content in [[],ils[:3]]:
 for c in [[],[cites[0]],cites[:3]]:ils.append(tag('Cite',[c,content]))
bs=[tag('HorizontalRule')]
for name in ['Plain','Para']:
 for content in [[],ils[:3],ils[:14]]:bs.append(tag(name,content))
for a,t in itertools.product(attrs,texts):bs.append(tag('CodeBlock',[a,t]))
for fmt,t in itertools.product(['HTML','html','ß',''],texts):bs.append(tag('RawBlock',[fmt,t]))
for a,n in itertools.product(attrs,nums):bs.append(tag('Header',[n,a,ils[:3]]))
for content in [[],bs[:3],bs[:10]]:bs.append(tag('BlockQuote',content));ils.append(tag('Note',content))
for items in [[],[[]],[bs[:3],bs[3:5]]]:
 bs.append(tag('BulletList',items));bs.append(tag('DefinitionList',[[ils[:3],items],[[],[]]]))
 for style,delim,n in itertools.product(['DefaultStyle','Example','Decimal','LowerRoman','UpperRoman','LowerAlpha','UpperAlpha'],['DefaultDelim','Period','OneParen','TwoParens'],nums):
  bs.append(tag('OrderedList',[[n,tag(style),tag(delim)],items]))
for kind,names in [('style',['DefaultStyle','Example','Decimal','LowerRoman','UpperRoman','LowerAlpha','UpperAlpha']),('delim',['DefaultDelim','Period','OneParen','TwoParens']),('alignment',['AlignLeft','AlignRight','AlignCenter','AlignDefault']),('quote',['SingleQuote','DoubleQuote']),('math',['DisplayMath','InlineMath']),('citationMode',['AuthorInText','SuppressAuthor','NormalCitation'])]:
 for n in names:shows(kind,tag(n))
for a in attrs:shows('attr',a)
for t in texts:shows('target',[t,t]);shows('format',t)
for name,n in itertools.product(['rowHeadColumns','rowSpan','colSpan'],nums):shows(name,n)
for c in cites:shows('citation',c)
for v in ils:shows('inline',v)
for v in bs:shows('block',v)
capts=[[None,[]],[[],[]],[ils[:3],bs[:3]]]
rows=[]
for a,alignment,n in itertools.product(attrs,['AlignLeft','AlignRight','AlignCenter','AlignDefault'],nums):
 cell=[a,tag(alignment),n,n,bs[:3]];shows('cell',cell)
 row=[a,[cell]];rows.append(row);shows('row',row)
for a,cap,n in itertools.product(attrs,capts,nums):
 h=[a,rows[:2]];b=[a,n,[],rows[:2]];f=[a,rows[-2:]]
 shows('caption',cap);shows('tableHead',h);shows('tableBody',b);shows('tableFoot',f)
 columns=[[tag('AlignDefault'),tag('ColWidthDefault')],[tag('AlignLeft'),tag('ColWidth',0.25)]]
 shows('block',tag('Table',[a,cap,columns,h,[b],f]))
 shows('block',tag('Figure',[a,cap,bs[:3]]));shows('block',tag('Div',[a,bs[:3]]))
for cap in capts:shows('caption',cap)
for a,w in itertools.product(['AlignLeft','AlignRight','AlignCenter','AlignDefault'],[tag('ColWidthDefault'),tag('ColWidth',-0.0),tag('ColWidth',1e23)]):shows('colSpec',[tag(a),w])
metas=[tag('MetaBool',False),tag('MetaBool',True)]+[tag('MetaString',t) for t in texts]+[tag('MetaInlines',ils[:3]),tag('MetaBlocks',bs[:3]),tag('MetaList',[]),tag('MetaList',[tag('MetaBool',True)])]
for keys in [[],['z','a','漢','🙂'],['\ue000','\U00010000']]:
 meta={k:metas[i%len(metas)] for i,k in enumerate(keys)};shows('meta',meta);metas.append(tag('MetaMap',meta))
for v in metas:shows('metaValue',v)
rng=random.Random(0xA575A0)
# Vary constructor/field placement throughout mixed ASTs, not just root examples.
for i in range(1500):
 v=rng.choice(ils)
 for _ in range(rng.randrange(6)):
  v=rng.choice([tag('Emph',[v,rng.choice(ils)]),tag('Note',[tag('Para',[v]),rng.choice(bs)]),tag('Span',[rng.choice(attrs),[v]]),tag('Cite',[[rng.choice(cites)],[v]])])
 add('show','inline',rng.choice(prec),v,seeded=True)
# Text.pack replaces every isolated surrogate; transport code points to test it.
points=list(range(256))+list(range(0xd800,0xe000))+[0x100,0x7ff,0x800,0xd7ff,0xe000,0xffff,0x10000,0x10ffff]
for cp in points:
 for kind in ['inline','metaValue','format']:
  for p in [0,11]:add('text',kind,p,[cp,ord('1'),14,ord('H')],textPoints=True)
# IEEE bit transport retains NaN payloads, infinities, subnormals and signed zero.
bits=[0,1,2**63,2**63+1,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000000,0xfff8000000000001,0x7ff0000000000001,0x7fefffffffffffff,0x0010000000000000]
bits += [struct.unpack('>Q',struct.pack('>d',x))[0] for x in [1e23,1e-7,1e-6,1e6,1e7,-1e23,-1.5]]
bits += [rng.getrandbits(64) for _ in range(1500)]
for b in bits:
 for p in [0,6,7,10,11]:add('double',p,b)
for kind in ['inline-head','inline-tail','caption-head','citation-head','infinite-list','infinite-node','infinite-block']:
 for n in [-1,0,*range(1,101),1000]:add('partial',kind,n,partial=True)
for name in ['inline','block','metaValue','mixed']:
 for n in [0,1,10,1000,10000,100000]:add('deep',name,n,deep=True)
for v in ils[:25]:
 for p in [0,11]:
  for n in range(40):add('prefix','inline',p,n,v)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['request'][0] for c in cs)),documents=sum(x['accepted'] for x in native),oracle='GHC 9.12.2; unchanged source pandoc-types 1.23.1.2, Text 2.1.2; Show/showsPrec')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} AST Show observations; SHA {meta["caseHash"]}',flush=True)
