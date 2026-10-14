#!/usr/bin/env python3
"""Original Blaze tree operations/renderers and unchanged Pandoc layoutMarkup."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/markup/blaze.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_BLAZE_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-blaze-fixtures.py','licenses/text-BSD-2-Clause.txt','upstream/pandoc/src/Text/Pandoc/Writers/Blaze.hs','licenses/Blaze-BSD-3-Clause.txt','licenses/blaze-builder-BSD-3-Clause.txt','licenses/pandoc-GPL-2.0.md']
assert (root/'tools/haskell-blaze-reference/PandocBlaze.hs').read_text()==(root/'upstream/pandoc/src/Text/Pandoc/Writers/Blaze.hs').read_text().replace('module Text.Pandoc.Writers.Blaze ( layoutMarkup )','module PandocBlaze ( layoutMarkup )')
for folder in ['vendor/blaze-markup-format-0.8.3.0','vendor/blaze-html-format-0.9.2.0','vendor/doclayout-0.6','vendor/text-syntax-2.1.2','vendor/blaze-builder-0.4.4.1','tools/haskell-blaze-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert len([c for c in cs if c.get('originalHtml')])==25
 assert len([c for c in cs if c.get('bytePair')])==65536
 print(f'Verified {len(cs)} original Blaze observations and {len(sources)} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(n,op='render',**extra):
 proc.stdin.write(json.dumps([op,n],ensure_ascii=True)+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,n)
 expected=json.loads(line);cs.append(dict(op=op,input=n,expected=expected,**extra))
 if len(cs)%10000==0:print('Captured',len(cs),'Blaze cases',flush=True)
def txt(s,n=0):return ['content',['text',s],n]
def string(s,n=0):return ['content',['string',list(map(ord,s))],n]
def par(tag,n):return ['parent',tag,'<'+tag,'</'+tag+'>',n]
def leaf(tag):return ['leaf',tag,'<'+tag,'>',0]
def app(*ns):return ['concat',list(ns),0]
def attr(n,key,val):return ['attribute',key,' '+key+'="', ['text',val],n]
def custom(n,key,val):return ['custom-attribute',['static',key],['text',val],n]
# All 25 unchanged original Html tests, represented as their source constructor trees.
originals=[
 attr(par('div',app(par('p',string('banana')),par('span',string('banana')))),'id','foo'),
 attr(attr(leaf('img'),'src','foo.png'),'alt','bar'),string('"&"'),txt('<img>'),string('"\'"'),attr(leaf('img'),'src','&'),par('u',string('hello')),
 ['content',['pre',['string',list(map(ord,'<3 Haskell'))]],0],['content',['pre',['text','<script />']],0],
 ['attribute','class',' class="',['pre',['string',list(map(ord,"'&!;"))]],par('p',string('bad'))],
 attr(par('span',string('λ')),'id','&'),string('∀x. x ∈ A'),string('$6, €7.01, £75'),app(*[par('li',string(str(n))) for n in [4,5,6]]),app(leaf('br'),leaf('img'),leaf('area')),
 custom(par('p',string('A paragraph')),'data-foo','bar'),par('p',string('Hello')),attr(attr(leaf('img'),'src','foo.png'),'alt','foo'),app(*[string(str(n)) for n in range(1,11)]),
 ['attribute','src',' src="',['append',['string',list(map(ord,'funny-picture-'))],['append',['string',[52]],['string',list(map(ord,'.png'))]]],leaf('img')],
 app(*[string(chr(n)) for n in range(97,123)]),['custom-parent',['static','p'],string('A paragraph')],['attribute','src',' src="',['text','foo.png'],['custom-leaf',['static','img'],False,0]],
 ['custom-leaf',['static','img'],True,0],custom(par('p',string('A paragraph')),'dojoType','select')]
assert len(originals)==25
for i,n in enumerate(originals):add(n,originalHtml=i+1)
# The five original BlazeMarkup assertions.
for n in [app(attr(par('p',txt('Hello')),'class','foo'),attr(par('p',txt('World')),'id','2nd')),
 ['contents',par('div',app(attr(par('p',txt('Hello ')),'id','para'),['comment',['pre',['text','Test test']],0],attr(leaf('img'),'name','An image'),par('p',txt('World!'))))],
 app(txt(''),txt(''),['empty',0]),app(txt(''),txt(''),par('p',txt('a'))),par('div',app(txt('Hello '),['comment',['pre',['text','Test']],0],txt(' World!')))]:add(n,originalMarkup=True)
texts=['','<>"\'&','a b\nc','a  b\n\nc','λ😀é','</script>','x</y','< /','\0\r\t','↔','a\r\nb']
choices=[['empty']]+[['text',s] for s in texts]+[['string',list(map(ord,s))] for s in texts]+[['string',ps] for ps in [[0xd800],[0xdc00],[0xd800,0xdc00],[0x10ffff],[14,72,127]]]+[['bytes',s.encode().hex()] for s in texts]
choices += [['bytes',h] for h in ['ff','80','c0af','e282','eda080','efbbbf','f48fbfbf']]
base=choices[:]
for c in base:choices += [['pre',c],['external',c],['pre',['external',c]],['external',['pre',c]]]
for a,b in zip(base,base[::-1]):choices.append(['append',a,b])
for c in choices:
 add([c,[120,0xd800,121]],'choice')
 for n in [['content',c,3],['comment',c,4],['custom-leaf',c,True,5],['custom-parent',c,txt('inner')],['attribute','raw',' key="',c,par('p',txt('a b'))],['attribute','raw',' key="',c,txt('ignored')]]:add(n)
for tag,s in itertools.product(['p','code','pre','style','script','textarea','custom'],texts):
 n=par(tag,txt(s))
 for op in [None,'external','contents']:add(n if op is None else [op,n])
# StaticString backends and arbitrary suffix functions stay independent.
for mode in ['prefix','constant','reverse','bomb']:
 ss=[[60,83,62,55296],'3c553e','<T>',mode]
 for n in [['parent',ss,ss,ss,txt('x y')],['leaf',ss,ss,ss,'bomb'],['attribute',ss,ss,['text','v'],leaf('img')]]:add(n)
 add([['static',ss],[97,98]],'choice')
# Return values and map/apply/bind: const callbacks must not force discarded values.
for a,b in itertools.product([0,1,-1,'bomb'],repeat=2):
 x=txt('x',a);y=txt('y',b)
 for n in [['append',x,y],['before',x,y],['then',x,y],['apply',['add',3],x,y],['apply-ignore',x,y],['bind-ignore',x,y]]:add(n)
 for f in [['add',2],['const',5],['bomb']]:add(['map',f,x])
 add(['bind',x,2])
# Full byte-pair matrix pins strict UTF-8 errors, lenient behavior and Latin-1
# String output independently of the existing tokenizer/decoder corpora.
for a,b in itertools.product(range(256),repeat=2):add(['content',['bytes',bytes([a,b]).hex()],0],bytePair=True)
for c in range(256):add(['content',['string',[c]],0])
rng=random.Random(0xB1a2e)
def gen(depth):
 if depth==0:return rng.choice([txt(rng.choice(texts),rng.choice([0,1,'bomb'])),['empty',0],leaf('br'),['comment',rng.choice(choices),0]])
 tag=rng.choice(['parent','append','attribute','custom-attribute','external','contents','map','content','bind-ignore'])
 if tag=='parent':return par(rng.choice(['p','div','pre','script']),gen(depth-1))
 if tag=='append':return ['append',gen(depth-1),gen(depth-1)]
 if tag=='attribute':return ['attribute','data-x',' data-x="',rng.choice(choices),gen(depth-1)]
 if tag=='custom-attribute':return ['custom-attribute',rng.choice(choices),rng.choice(choices),gen(depth-1)]
 if tag in ['external','contents']:return [tag,gen(depth-1)]
 if tag=='map':return ['map',rng.choice([['add',1],['const',2]]),gen(depth-1)]
 if tag=='bind-ignore':return ['bind-ignore',gen(depth-1),gen(depth-1)]
 return ['content',rng.choice(choices),0]
for _ in range(2000):add(gen(4))
for kind in ['text','pre-text','bytes','text-comment','bytes-comment','text-value','pre-text-value','bytes-value']:
 for chunks in [[],[''],['','a','','b',''],['a ',' b'],['<','&','>'],['a','!bottom!'],['!bottom!']]:
  if 'bytes' in kind:chunks=[s if s=='!bottom!' else s.encode().hex() for s in chunks]
  n=['chunks',kind,chunks]
  for wrapper in [lambda n:n,lambda n:['contents',n],lambda n:['external',n],lambda n:['attribute','x',' x="',['text','v'],n]]:add(wrapper(n),lazyChunks=True)
# Truncated three/four-byte prefixes and malformed complete sequences exercise
# the source strict-decoder error byte, not just successful UTF-8 decoding.
for h in ['e282','e282ac','e282ff','f09080','f0908080','f09080ff','e0a0','e080','eda0','f080','f490','ffeda0','00f09080']:
 add(['content',['bytes',h],0])
# Distinct failures in keys, values, siblings and children pin native evaluation
# order; comparing only successful bytes would not detect eager traversal drift.
for a,b,c in itertools.product(['','61','ff','80','e282','c0af'],repeat=3):
 bad=lambda h:['bytes',h]
 content=lambda h:['content',bad(h),0]
 value=['custom-attribute',bad(a),bad(b),leaf('br')]
 nested=['custom-attribute',['text','outer'],bad(a),['custom-attribute',bad(b),bad(c),leaf('br')]]
 for n in [value,nested,['append',content(a),content(b)],['content',['append',bad(a),bad(b)],0],['custom-attribute',bad(a),bad(b),par('p',content(c))]]:add(n,errorOrder=True)
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),oracle='GHC 9.12.2 unchanged Blaze Markup 0.8.3.0/Html 0.9.2.0 and Pandoc 3.12.1 Blaze layout')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for row in [meta]+cs:z.write((json.dumps(row,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} Blaze observations; SHA {meta["caseHash"]}',flush=True)
