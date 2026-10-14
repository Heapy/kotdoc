#!/usr/bin/env python3
"""Capture original Sources/Parsec observations; production Kotlin is not an oracle."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/parsing/sources.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_SOURCES_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-sources-fixtures.py','upstream/pandoc/src/Text/Pandoc/Sources.hs',
 'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json','licenses/pandoc-GPL-2.0.md',
 'licenses/Parsec-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/Unicode-3.0.txt','licenses/text-BSD-2-Clause.txt','licenses/pandoc-types-BSD-3-Clause.txt',
 'upstream/pandoc/src/Text/Pandoc/Parsing/Lists.hs','upstream/pandoc/src/Text/Pandoc/Parsing/State.hs',
 'upstream/pandoc-types/pandoc-types.cabal','tools/haskell-list-parsing-reference/Text/Pandoc/Parsing/State.hs']
for folder in ['tools/haskell-sources-reference','vendor/parsec-3.1.18.0','vendor/ghc-9.12.2','upstream/pandoc-types/src']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
documents=[]
for f in sorted((root/'upstream/pandoc/test').rglob('*')):
 if f.suffix not in ['.md','.markdown','.rst','.org','.text','.txt']:continue
 try:t=f.read_text()
 except UnicodeDecodeError:continue
 documents.append((str(f.relative_to(root)),t));sources.append(str(f.relative_to(root)))
sources=sorted(set(sources))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 assert not (root/'tools/haskell-sources-reference/Text/Pandoc/Sources.hs').exists()
 assert '../../upstream/pandoc/src' in (root/'tools/haskell-sources-reference/kotdoc-sources-reference.cabal').read_text()
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 assert m['documents']==len(documents)
 assert [(c['path'],c['request'][2]) for c in cs if c.get('document')]==[(path,list(map(ord,t))) for path,t in documents]
 print(f'Verified {len(cs)} original Sources observations, {len(documents)} source documents and {len(sources)} source/input hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(*request,**extra):
 proc.stdin.write(json.dumps(request,ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,request
 cs.append(dict(request=request,expected=json.loads(line),**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'Sources observations',flush=True)
def ps(t):return list(map(ord,t))
def chunk(t,p=['',1,1]):return [p,ps(t)]
def run(parser,input,type='sources',position=['parser',3,7],user=23,**extra):
 add('run',dict(parser=parser,input=input,type=type,position=position,user=user),**extra)
add('classes',allCharacters=True)
for path,t in documents:add('from','text',ps(t),document=True,path=path)
positions=[['',1,1],['a',7,13],['b',1,1],['back',-2,-5],['max',2**63-1,2**63-1],['min',-2**63,-2**63]]
texts=['','a','ab','\t','a\t','\n','\r','\r\n','\n\n','a\n\t!','漢🙂','\ud800','\ud800\udfff','\U00010000']
predicates=[['true'],['false'],['space'],['letter'],['alphaNum'],['digit'],['hexDigit'],['oneOf',ps('a\n\t')],['noneOf',ps('!')]]
for t in texts:
 for typ in ['string','text']:add('from',typ,ps(t))
 for files in [[],[['a',ps(t)]],[['a',ps(t)],['b',[]]],[['a',ps(t)],['a',ps(t)]]]:add('from','files',files)
streams=[[],[chunk('')]]
for a,b in itertools.product(texts,texts):
 for p in positions[:4]:streams.append([chunk(a,['first',3,7]),chunk(b,p)])
for s in streams:
 add('source',s);add('uncons',s)
 for n in [-3,0,1,2,4]:add('ensure',n,s)
 for f in predicates:
  for op in ['take','take1','manyText','many1Text']:run([op,f],s,span=True)
 for p in [['any'],['spaces'],['eof'],['string',ps('ab')],['sequence',[['any'],['any']]],['fallback',['try',['string',ps('ab')]]]]:run(p,s,primitive=True)
# Every partition of short stretches, including retained empty boundaries.
for length in range(5):
 for chars in itertools.product('a\n\t!',repeat=length):
  t=''.join(chars)
  for cut in range(length+1):
   s=[chunk(t[:cut],['a',9,12]),chunk('', ['empty',2,3]),chunk(t[cut:],['b',1,1]),chunk('')]
   for f in [['true'],['space'],['oneOf',ps('a\n\t')]]:
    for op in ['take','take1','manyText','many1Text']:run([op,f],s,partition=True)
for typ in ['sources','text','chars','bytes','lazyBytes']:
 for p,c in itertools.product(positions,[0,9,10,13,ord('a'),0x1f642]):
  for s in [[],[chunk('')],[chunk(''),chunk('', ['next',3,8])],[chunk('x'),chunk('')]]:
   add('update',typ,p,c,s,positionUpdate=True)
points=sorted({0,0x10ffff,*[p for row in json.loads((root/'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json').read_text())['ranges'] for p in [row[0]-1,row[0],row[0]+1] if 0<=p<=0x10ffff]})
for point in points:
 for typ in ['sources','text','chars']:
  inp=[chunk(chr(point))] if typ=='sources' else [point]
  for name in ['letter','alphaNum','space','digit','hexDigit','any']:run([name],inp,typ,unicodeBoundary=point)
for point in range(256):
 for typ in ['bytes','lazyBytes']:
  inp=[[],[point],[],[65,9,10],[]] if typ=='lazyBytes' else [point,65,9,10]
  for name in ['letter','alphaNum','space','digit','hexDigit','any','spaces']:run([name],inp,typ,byteBoundary=point)
for typ in ['text','chars','bytes','lazyBytes','sources']:
 for text in texts:
  pts=ps(text)
  if typ in ['bytes','lazyBytes'] and any(x>255 for x in pts):continue
  inp=pts if typ in ['text','chars','bytes'] else [[],pts,[]] if typ=='lazyBytes' else [chunk(text)]
  parsers=[['any'],['newline'],['ordinary',9],['spaces'],['oneOf',ps('abc\n')],['noneOf',ps('ab')],['string',ps('ab')]]
  for parser in parsers:
   for wrapper in [lambda x:x,lambda x:['many',x],lambda x:['fallback',x],lambda x:['look',x],lambda x:['label','custom',x]]:
    # many spaces would correctly throw Parsec's empty-parser error; retain it too.
    run(wrapper(parser),inp,typ,composition=True)
for s in streams[::13]:
 for p,t in itertools.product(positions,['','x','\r\n','\t','漢🙂']):
  for op in [['add',p,ps(t)],['prepend',ps(t)]]:
   for suffix in [[],[['any']],[['take',['true']]],[['user',-41],['take',['space']]]]:
    run(['sequence',[op]+suffix],s,inputExpansion=True)
 for b in streams[:12]:add('append',s,b,append=True)
for xs in [[],streams[:20],streams[-20:],streams[::13]]:add('concat',xs,monoid=True)
partials=['initial-ignore-text-tail','initial-ignore-tail','concat-ignore-position','uncons-ignore-position',
 'uncons-ignore-empty-position','uncons-ignore-char','append-ignore-rhs','append-ignore-left-fields',
 'update-ignore-single-text','update-ignore-next-text','update-demand-tail','update-demand-next-pair',
 'ensure-ignore-earlier-pair','ensure-ignore-earlier-text','take-false-ignore-tail','any-ignore-last-position',
 'any-demand-tail','prepend-demand-spine','prepend-demand-pair','prepend-ignore-fields',
 'add-ignore-old-spine','add-ignore-old-pair','ensure-zero-demand-last-text','take-whole-demand-next-position',
 'take-prefix-ignore-tail','take-empty-retain-pos','mconcat-ignore-tail','update-jump-ignore-pos-char','update-fallback-demand-char','infinite-initial','infinite-prefix']
for name in partials:add('partial',name,partial=True)
rng=random.Random(0x50A2CE)
for n in range(3500):
 s=[chunk(''.join(rng.choice('abc0F \r\n\t!漢🙂') for _ in range(rng.randrange(12))),rng.choice(positions)) for _ in range(rng.randrange(8))]
 f=rng.choice(predicates)
 parser=rng.choice([['take',f],['take1',f],['manyText',f],['many1Text',f],['string',ps('abc')],['spaces']])
 for _ in range(rng.randrange(4)):parser=[rng.choice(['try','look','fallback']),parser]
 run(parser,s,position=rng.choice(positions),seeded=True)
for n in [1,1000,10000,100000]:
 s=[chunk('x',['deep',2,9]) for _ in range(n)]
 for op in ['take','manyText']:run([op,['true']],s,deep=True)
 run(['string',[120]*n],s,deep=True)
 add('ensure',2,s,deep=True)
for t in ['1.','123)','(2)','@label.','@漢字.','@𐐀.','I.','IV.','V.','i.','a.','#.','(1.','(I)','99@label.','@a__b.','broken','9'*1000+'.']:
 for cut in range(min(len(t)+1,20)):
  input=[chunk('', ['empty',9,12]),chunk(t[:cut],['left',3,8]),chunk('', ['empty-next',5,7]),chunk(t[cut:],['right',1,1])]
  for parser in ['any','roman','decimal','eof']:
   add('list',dict(input=input,parser=parser,position=['parser',3,7]),listIntegration=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['request'][0] for c in cs)),documents=len(documents),unicodeBoundaries=len(points),oracle='GHC 9.12.2, original Pandoc 3.12.1 Sources and Parsec 3.1.18.0; full Char classifications')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} Sources observations; SHA {meta["caseHash"]}',flush=True)
