#!/usr/bin/env python3
"""Observe original Eq/Ord instances; expected relations/selection never use Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/templates/order.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_MODEL_ORDER_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
assert (root/'tools/haskell-model-order-reference/TemplateAdapter.hs').read_bytes()==(root/'tools/haskell-model-show-reference/TemplateAdapter.hs').read_bytes()
sources=['tools/import-model-order-fixtures.py','tools/haskell-model-show-reference/TemplateAdapter.hs','core/src/test/resources/options/model-show.jsonl.gz','core/src/test/resources/templates/runtime.jsonl.gz','licenses/DocLayout-BSD-3-Clause.txt','licenses/Doctemplates-BSD-3-Clause.txt']
for folder in ['vendor/doctemplates-0.11.1','vendor/doclayout-0.6','vendor/parsec-3.1.18.0','vendor/filepath-1.5.4.0','tools/haskell-model-order-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert m['targets']==dict(collections.Counter(c['target'] for c in cs))
 assert len([c for c in cs if c.get('originalTemplate')])==62
 assert all(set(c['expected'])=={'compare','equal','notEqual','less','lessEqual','greater','greaterEqual','minimum','maximum'} for c in cs)
 print(f'Verified {len(cs)} original model Eq/Ord observations and {len(sources)} source/input files');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(op,a,b,kind='text',**extra):
 proc.stdin.write(json.dumps([op,kind,[a,b]])+'\n');proc.stdin.flush()
 line=proc.stdout.readline();assert line,(op,a,b)
 e=json.loads(line);assert len(e)==9,e
 cs.append(dict(op=op,target=kind,input=[a,b],expected=e,**extra))
 if len(cs)%2000==0:print('Captured',len(cs),'model order cases',flush=True)
with gzip.open(root/'core/src/test/resources/options/model-show.jsonl.gz','rt') as f:
 next(f);old=[json.loads(line) for line in f]
def unique(op):
 d={json.dumps(c['input'],sort_keys=True):c['input'] for c in old if c['op']==op}
 return list(d.values())
rng=random.Random(0x30d)
for op in ['doc','template','font','style','variable','pipe','border']:
 values=unique(op)
 for a in values:
  add(op,a,a)
  add(op,a,rng.choice(values))
 # Constructor grid includes every original source constructor.
 first={}
 for a in values:first.setdefault(json.dumps(a[0] if isinstance(a,list) and a else a,sort_keys=True),a)
 for a,b in itertools.product(list(first.values()),repeat=2):add(op,a,b)
 for _ in range(300):add(op,rng.choice(values),rng.choice(values))
for a,b in itertools.product(range(3),repeat=2):add('alignment',a,b)
# Complete original compiled golden/unit trees, in equality and paired order modes.
originals=[c for c in old if c['op']=='template' and c.get('originalTemplate') and c['precedence']==0]
for c in originals:
 add('template',c['input'],c['input'],originalTemplate=c['originalTemplate'])
 add('template',c['input'],rng.choice(originals)['input'],originalTemplate=c['originalTemplate'])
base=[False,False,0,False,None,None,None]
def part(x,link=None,font=None):return [link,font or base,x]
def text(x,w=0):return ['text',w,x]
def cooked(xs):return ['cooked',0,[part(x) for x in xs]]
def wraps(x):
 d=text(x)
 return [d,['vfill',0,x],cooked([x]),['block',0,[[part(x)]]],['prefix','p',d],['before',d],['flush',d],['concat',d,['empty']],['concat',['empty'],d],['styled',['weight',False],d],['link','url',d]]
# GHC Double targets: both zero signs, infinities, subnormals, signed quiet and
# signaling NaNs with distinct payloads. Inputs/results carry raw Word64 bits.
bits=[0,0x8000000000000000,0x3ff0000000000000,0xbff0000000000000,1,0x8000000000000001,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000001,0x7ff8000000000013,0xfff8000000000001,0x7ff0000000000001]
for a,b in itertools.product(map(str,bits),repeat=2):
 for x,y in zip(wraps(a),wraps(b)):
  add('doc',x,y,'double')
  add('template',['literal',x],['literal',y],'double')
 add('part',part(a),part(b),'double')
 for xs,ys in [([a],[b]),([a,'0'],[b,'0']),(['0',a],['0',b]),([a],[b,'0'])]:add('attributed',[part(x) for x in xs],[part(x) for x in ys],'double')
 for x,y in [(['concat',text(a),text('0')],['concat',text(b),text('0')]),(['concat',text('0'),text(a)],['concat',text('0'),text(b)])]:
  add('template',['concat',['literal',x],['empty']],['concat',['literal',y],['empty']],'double')
ints=[-2**63,-1,0,1,2**63-1]
for a,b in itertools.product(ints,repeat=2):
 for w in ints:add('doc',text(str(a),w),text(str(b),w),'int64')
 add('template',['literal',text(str(a))],['literal',text(str(b))],'int64')
points=[[],[0],[65],[233],[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000],[0x10ffff],[0x10ffff,49]]
for a,b in itertools.product(points,repeat=2):
 for x,y in zip(wraps(json.dumps(a,separators=(',',':'))),wraps(json.dumps(b,separators=(',',':')))):
  add('doc',x,y,'string-points')
 add('template',['literal',text(json.dumps(a,separators=(',',':')))],['literal',text(json.dumps(b,separators=(',',':')))],'string-points')
# Different list sizes and early widths/fonts/links must avoid unreachable partial
# target comparisons. Probe's Eq/compare throw only if a target is actually used.
for a,b in itertools.product(['!','L!','R!','a','b'],repeat=2):
 for x,y in zip(wraps(a),wraps(b)):
  add('doc',x,y,'probe');add('template',['literal',x],['literal',y],'probe')
 for w,z in itertools.product([-1,0,1],repeat=2):add('doc',text(a,w),text(b,z),'probe')
 for xs,ys in itertools.product([[],[a],[a,'!'],['x',a]],[[],[b],[b,'!'],['x',b]]):
  add('attributed',[part(x) for x in xs],[part(x) for x in ys],'probe')
  add('doc',cooked(xs),cooked(ys),'probe')
 for link1,link2 in itertools.product([None,'a','b'],repeat=2):add('part',part(a,link1),part(b,link2),'probe')
add('doc',['empty'],text('!'),'probe');add('doc',text('!'),['empty'],'probe')
for n in [1,2,3,4,7,12,31]:
 for _ in range(10):
  xs=[part(rng.choice(['!','a','b'])) for _ in range(n)]
  ys=[part(rng.choice(['!','a','b'])) for _ in range(rng.choice([n,n+1,max(0,n-1)]))]
  add('attributed',xs,ys,'probe')
# Nested random shapes exercise lexicographic prefix and final-field differences.
def tree(depth,payload):
 if depth==0 or rng.randrange(4)==0:return rng.choice(wraps(payload))
 tag=rng.choice(['concat','flush','prefix','styled','link','before'])
 if tag=='concat':return [tag,tree(depth-1,payload),tree(depth-1,rng.choice([payload,'0']))]
 if tag in ['flush','before']:return [tag,tree(depth-1,payload)]
 return [tag,['weight',bool(rng.randrange(2))] if tag=='styled' else rng.choice(['','a','z']),tree(depth-1,payload)]
for _ in range(700):
 a=tree(3,rng.choice(list(map(str,bits))));b=a if rng.randrange(3)==0 else tree(3,rng.choice(list(map(str,bits))))
 add('doc',a,b,'double')
meta=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),targets=dict(collections.Counter(c['target'] for c in cs)),oracle='GHC 9.12.2 original doctemplates 0.11.1/doclayout 0.6 Eq/Ord instances; containers 0.7')
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for row in [meta]+cs:z.write((json.dumps(row,separators=(',',':'))+'\n').encode())
print(f'Captured {len(cs)} comparisons; {meta["operations"]}; {meta["targets"]}; SHA {meta["caseHash"]}',flush=True)
