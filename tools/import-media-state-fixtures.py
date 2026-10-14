#!/usr/bin/env python3
"""Original immutable MediaBag and containers tree behavior; never calls Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/media-state/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_MEDIA_STATE_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
shared=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text();a=shared.index('makeCanonical ::');b=shared.index('\n\n',a)
assert shared[a:b] in (root/'tools/haskell-media-state-reference/Text/Pandoc/Shared.hs').read_text()
sources=['tools/import-media-state-fixtures.py','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/MediaBag.hs','upstream/pandoc/src/Text/Pandoc/MIME.hs','upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/test/Tests/MediaBag.hs','upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-mediabag.lua','vendor/containers-syntax-0.7/src/Data/Map/Internal.hs','vendor/containers-syntax-0.7/LICENSE','vendor/mime-types-0.1.2.2/Network/Mime.hs','licenses/pandoc-GPL-2.0.md','licenses/Containers-BSD-3-Clause.txt','licenses/ByteString-BSD-3-Clause.txt','core/src/test/resources/media/oracle.json','core/src/test/resources/media/operations.json']
sources.append('licenses/Network-uri-BSD-3-Clause.txt')
for folder in ['tools/haskell-media-state-reference','vendor/ghc-9.12.2','vendor/ghc-translations-9.12.2','vendor/filepath-1.5.4.0','vendor/network-uri-2.6.4.2']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file())
def hashes():return {f:hashlib.sha256((root/f).read_bytes()).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['request'][0] for c in cs))
 assert set(c['original'] for c in cs if c.get('original'))=={'harmless parent substring','parent component','canonical aliases','escaped collision'}
 print(f'Verified {len(cs)} original MediaBag/tree observations and {len(sources)} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup)
cs=[]
def add(*req,**flags):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 result=json.loads(line);cs.append(dict(request=req,expected=result,**flags))
 if len(cs)%1000==0:print('Captured',len(cs),'MediaBag observations',flush=True)
 return result
def ps(s):return list(map(ord,s)) if isinstance(s,str) else s
def ent(p,m=None,bs=b'oracle'):return [ps(p),None if m is None else ps(m),bs.hex()]
for name,es,qs in [('harmless parent substring',[ent('foo..bar.png',bs=b'contents')],['foo..bar.png']),('parent component',[ent('../evil.png',bs=b'contents')],['../evil.png']),('canonical aliases',[ent('img/../sub/./lalune.png',bs=b'contents')],['sub/lalune.png','img/../sub/lalune.png']),('escaped collision',[ent('a b.png',bs=b'contents2'),ent('a%20b.png',bs=b'contents1')],['a b.png','a%20b.png'])]:add('bag',es,[ps(q) for q in qs],original=name)
paths=['','.','./','/','//','///','////a//b','/..','//../x','a/..','a/../b.png','../../x.png','a\\b.png','C:x.png','C:/x.png','\\\\server\\x.png','unknown:thing.png','https://example.invalid/x.png','https://example.invalid/é.png','unknown:é.png','http://example.invalid/🙂','a%20b.png','a b.png','a%2Fb.png','a%5Cb.png','%2e%2e/x.png','x%FF.png','x%E2%82.png','x%ED%A0%80.png','foo..bar.png','image.png.gz','file.','file','data:image/png;base64,YQ==','DATA:not valid','data:,abc', '漢🙂.png','\uffff.png','\U00010000.png']
paths += [[cp,*ps('.png')] for cp in [0,1,9,32,127,0xd800,0xdc00,0x10000,0x10ffff]]+[[0xd800,0xdc00,*ps('.png')]]
paths += ['a.b:foo','a.b:foo?query=.jpg','http:','http://[::1]/x.png','http://[fe80::1%25eth0]/x.png','https:///x.png','http://host:invalid/x.png','http://host:999999/x.png','http://[v1.abc]/x.png','http://[v12.abc]/x.png']
paths += [f'http://{host}/x.png' for host in ['a'*n for n in [254,255,256]]]
mimes=[None,'','image/png','image/png;charset=utf-8','IMAGE/PNG','application/unknown','text/plain; charset=utf-8']
for path,mime in itertools.product(paths,mimes):add('bag',[ent(path,mime)],[ps(path)])
for path in paths:add('uri',ps(path),uri=True)
for host,port,path in itertools.product(['host','','localhost','[::]','[::1]','[1::2]','[1:2:3:4:5:6:7:8]','[::ffff:192.168.001.1]','[v1.a]','[v12.a]','[V1.a]','[v1.a:b]','[fe80::1%25eth0]','[fe80::1%eth0]','a'*255,'a'*256],['',':',':0',':999999',':bad'],['','/x.png','/a?x=1#f']):
 add('uri',ps('http://'+host+port+path),uri=True)
for cp in range(256):
 for s in ['http://h/'+chr(cp),'x:'+chr(cp),'http://h?'+chr(cp),'http://'+chr(cp)+'/x']:
  add('uri',ps(s),uri=True)
old=json.loads((root/'core/src/test/resources/media/oracle.json').read_text())
for c in old['media']:add('bag',[ent(c['path'],c.get('mime'))],[ps(c['path'])],registry=True)
items=[[ps(m),ps(path),bs.hex()] for m,path,bs in itertools.product(['','image/png','IMAGE/PNG','漢🙂'],['','a','z',[0xd800],[0xd800,0xdc00],[0x10000]], [b'',b'\0',b'\xff',b'a',b'aa'])]
for i in items:
 for prec in [-2**63,0,10,11,2**63-1]:add('item',*i,prec)
rng=random.Random(0x20142024)
for _ in range(1500):add('compare',rng.choice(items),rng.choice(items))
for b in range(256):add('item',ps('image/png'),ps('path'),bytes([b,49]).hex(),0,byteShow=True)
for _ in range(300):
 es=[ent(rng.choice(paths),rng.choice(mimes),rng.randbytes(rng.randrange(12))) for _ in range(rng.randrange(12))]
 qs=[ps(p) for p in rng.sample(paths,8)]
 add('bag',es,qs,multiple=True);add('delete',es,rng.choice(qs),qs,multiple=True)
 ys=[ent(rng.choice(paths),rng.choice(mimes),rng.randbytes(rng.randrange(12))) for _ in range(rng.randrange(12))]
 add('union',es,ys,qs,multiple=True)
for path,mime,op in itertools.product(paths,mimes,['mime','path','delete','directory','show']):add('demand',ps(path),None if mime is None else ps(mime),op)
for n in [-10**100,-1,0,1,2,10**100]:add('times',[ent('a.png'),ent('b.png')],str(n),[ps('a.png'),ps('missing')])
# Inspect the actual original containers tree after every operation and keep all
# prior roots alive; this validates rotations and union shape, beyond sorted values.
for count in [1,2,3,4,5,10,32,100,300]:
 for order in [list(range(count)),list(range(count-1,-1,-1)),rng.sample(range(count),count)]:
  ops=[['insert',ps(f'k{k:04}'),k] for k in order]
  ops += [['delete',ps(f'k{k:04}')] for k in rng.sample(range(count),count)]
  add('map',ops,tree=True)
for _ in range(120):
 ops=[]
 for i in range(rng.randrange(5,60)):
  op=rng.choice(['insert','delete','union'])
  k=rng.choice(['a','b','z','漢','\uffff','\U00010000',str(rng.randrange(20))])
  if op=='insert':ops.append([op,ps(k),rng.randrange(100)])
  elif op=='delete':ops.append([op,ps(k)])
  else:ops.append([op,[[ps(str(rng.randrange(30))),rng.randrange(100)] for _ in range(rng.randrange(20))]])
 add('map',ops,tree=True)
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),operations=dict(collections.Counter(c['request'][0] for c in cs)),oracle='Unchanged Pandoc 3.12.1 MediaBag/MIME/URI/UTF8, containers 0.7, GHC 9.12.2 and frozen original dependencies')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'MediaBag observations; SHA',m['caseHash'],flush=True)
