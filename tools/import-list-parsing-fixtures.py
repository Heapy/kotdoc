#!/usr/bin/env python3
"""Capture original Lists/Parsec replies, state transitions and writer predicate."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/parsing/lists.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_LIST_PARSING_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-list-parsing-fixtures.py','tools/prepare-list-parsing-reference.py',
 'upstream/pandoc/src/Text/Pandoc/Parsing/Lists.hs','upstream/pandoc/src/Text/Pandoc/Parsing/State.hs',
 'upstream/pandoc/src/Text/Pandoc/Sources.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs',
 'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs','upstream/pandoc/test/Tests/Shared.hs',
 'upstream/pandoc/test/Tests/Readers/Markdown.hs','upstream/pandoc-types/pandoc-types.cabal',
 'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json',
 'licenses/pandoc-GPL-2.0.md','licenses/pandoc-types-BSD-3-Clause.txt',
 'licenses/Parsec-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/Unicode-3.0.txt']
for folder in ['tools/haskell-list-parsing-reference','upstream/pandoc-types/src','vendor/parsec-3.1.18.0','vendor/ghc-9.12.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['dist-newstyle','.hie']))
# Source token observations are not claims of full reader test coverage.
upstream=[];seen=set()
for f in sorted((root/'upstream/pandoc/test').rglob('*')):
 if f.suffix not in ['.md','.markdown','.rst','.org','.text','.txt']:continue
 try:lines=f.read_text().splitlines()
 except UnicodeDecodeError:continue
 found=False
 for n,line in enumerate(lines,1):
  match=re.match(r'^[ \t]*(\(?[^\s]+[.)])(?:\s|$)',line)
  if not match:continue
  token=match[1];found=True
  if token not in seen:seen.add(token);upstream.append((str(f.relative_to(root)),n,token))
 if found:sources.append(str(f.relative_to(root)))
sources=sorted(set(sources))
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 subprocess.run(['python3','tools/prepare-list-parsing-reference.py','--check'],cwd=root,check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert [(c['path'],c['line'],c['input']) for c in cs if c.get('upstreamToken')]==upstream
 assert m['upstreamTokens']==len(upstream)
 assert all(c['expected']['untouched']=='retained' for c in cs if c['op']=='run' and c['expected']['ok'])
 print(f'Verified {len(cs)} original list-parser observations, {len(upstream)} distinct upstream tokens and {len(sources)} source/input hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(op,v,**extra):
 proc.stdin.write(json.dumps([op,v],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,v)
 cs.append(dict(op=op,input=v,expected=json.loads(line),**extra))
 if len(cs)%5000==0:print('Captured',len(cs),'list observations',flush=True)
def run(parser,text,next=1,examples=[],position=['',1,1],**extra):
 add('run',dict(parser=parser,text=text,next=next,examples=examples,position=position),**extra)
for path,n,token in upstream:add('marker',token,upstreamToken=True,path=path,line=n)
# Exhaustive Roman sequences retain permissive ordering, consumed prefixes and errors.
for upper in [False,True]:
 alphabet='IVXLCDM' if upper else 'ivxlcdm'
 for length in range(5):
  for xs in itertools.product(alphabet,repeat=length):
   s=''.join(xs)
   run(['roman',upper],s+'!',romanSequence=True)
   for suffix in ['.',')']:
    run(['any'],s+suffix,romanMarker=True)
    add('marker',s+suffix,romanMarker=True)
styles=['DefaultStyle','Example','Decimal','UpperRoman','LowerRoman','UpperAlpha','LowerAlpha']
delims=['DefaultDelim','Period','OneParen','TwoParens']
nums=['','0','01','1','9','26','27','999','a','z','A','Z','i','I','v','V','iv','IV','iiii','IIII','x','X','iiix','IX','MMM','#','@','@label','12@label','@a-b','@a_b','@a__b','@a-','@漢字','@𐐀','١','１','²','-1','+1']
texts=[pre+n+suf+tail for pre,n,suf,tail in itertools.product(['','('],nums,['.',')','',':'],['',' tail'])]
for s in texts:
 run(['any'],s,markerBoundary=True);run(['fallback',['any']],s,rollback=True);add('marker',s,markerBoundary=True)
for style,delim,n in itertools.product(styles,delims,nums):
 for pre,suf in [('', '.'),('',')'),('(',')'),('(','.'),('','')]:
  run(['ordered',style,delim],pre+n+suf,styleDelimiter=True)
for name in ['decimal','lowerAlpha','upperAlpha','lowerRoman','upperRoman']:
 for s in nums+[' ','\t','\n','é','ǅ','𐐀','🙂']:
  for end in [False,True]:run(['eof',[name]] if end else [name],s,primitive=True)
# The digit predicate is independently pinned for all GHC Chars. Include every
# classification interval boundary here as an example-label and initial token.
rows=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/parser-char-flags.json').read_text())['ranges']
points=sorted({0,0x10ffff,*[p for row in rows for p in [row[0]-1,row[0],row[0]+1] if 0<=p<=0x10ffff]})
for point in points:
 c=chr(point) if not 0xd800<=point<=0xdfff else '\ufffd'
 run(['any'],c+'.',unicodeBoundary=point)
 run(['any'],'@'+c+'.',unicodeLabel=point)
# Numeric overflow is original Read Int behavior, not bounded parsing failure.
for s in ['', '0','0001',str(2**63-1),str(2**63),str(2**64-1),str(2**64),str(2**64+1),'9'*1000,'1'+'0'*10000]:
 add('read',s,numericRead=True)
 run(['decimal'],s,overflow=True);run(['any'],s+'.',overflow=True)
 run(['any'],s+'@x.',overflow=True)
for next,examples in itertools.product([1,0,-1,2**63-1,-2**63],[[],[['label',42]],[['',17],['label',-3]]]):
 for s in ['@label.','12@label.','@.','@new.','@label!,@new.','@new.,@label.,@new.,8@label.,@.','@a.,@a.,99@a.,@b.','@label.,broken']:
  for parser in [['any'],['fallback',['any']],['sequence',[['any']]],['eof',['sequence',[['any']]]]]:
   run(parser,s,next,examples,position=['source α',7,13],exampleState=True)
for s in ['@a.@b.','@a.!','@a.12@a.@b.','@a.(@b.)#.', '1.2.3.']:
 run(['many',['any']],s,many=True)
rng=random.Random(0x11575)
chars='0123456789ivxlcdmIVXLCDMabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ@#().,_-: \t\n漢é𐐀🙂١'
for n in range(2500):
 s=''.join(rng.choice(chars) for _ in range(rng.randrange(0,30)))
 if n%3==0:s='@'+s+'.'
 parser=rng.choice([['any'],['roman',True],['roman',False],['ordered',rng.choice(styles),rng.choice(delims)]])
 if n%2==0:parser=['fallback',parser]
 run(parser,s,rng.choice([1,-1,2**63-1]),[['a',9]],position=['random',rng.randrange(1,20),rng.randrange(1,20)],seeded=True)
for kind in ['reuse-next','decimal-state','default-state']:add('partial',kind,partial=True)
for upper,length in itertools.product([False,True],[1,1000,10000]):
 c='M' if upper else 'm'
 run(['roman',upper],c*length+'!',longRoman=True)
 run(['any'],c*length+'.',longRoman=True)
for length in [1,1000,10000]:run(['any'],'@'+('a-b_'*length)+'z.',longLabel=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),
 upstreamTokens=len(upstream),unicodeBoundaries=len(points),oracle='GHC 9.12.2 original Pandoc 3.12.1 Lists/Sources, exact safeRead and writer marker body; test-only two-field state projection')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [meta]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} list observations; SHA {meta["caseHash"]}',flush=True)
