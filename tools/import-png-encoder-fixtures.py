#!/usr/bin/env python3
"""Capture original JuicyPixels PNG writers, including complete PNG bytes."""
import argparse,gzip,hashlib,itertools,json,os,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/images/png-encoder.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_PNG_ENCODER_REFERENCE'));a=p.parse_args()
sources=['tools/import-png-encoder-fixtures.py','vendor/JuicyPixels-tests-v3.3.8/test-src/main.hs']
for folder in ['vendor/JuicyPixels-3.3.9','vendor/JuicyPixels-png-tests-v3.3.8','vendor/JuicyPixels-png-writer-tests-v3.3.8','vendor/zlib-0.7.1.1','vendor/ghc-png-gamma-9.12.2','tools/haskell-png-encoder-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 for c in cs:
  if 'source' in c:assert hashlib.sha256((root/c['source']).read_bytes()).hexdigest()==c['sourceHash']
 print(f'Verified {len(cs)} original PNG encoder observations');raise SystemExit()
if not a.oracle:p.error('--oracle must name png-encoder-reference')
info=json.loads(subprocess.check_output([a.oracle,'--reference-info']));assert info==['ghc','9.12','JuicyPixels','3.3.9','vector','0.13.2.0','zlib','0.7.1.1']
cs=[];types={'y8':1,'y16':1,'ya8':2,'ya16':2,'rgb8':3,'rgb16':3,'rgba8':4,'rgba16':4}
def add(name,typ='rgb8',op='typed',mode='normal',observe='data',w=3,h=2,offset=0,length=None,values=None,pw=2,ph=1,po=0,pl=None,palette=None,entries=None,source=None):
 if values is None:values=[(n*71+13)%256 for n in range(max(0,w*h*types.get(typ,1))+3)]
 if palette is None:palette=[(n*37+71)%256 for n in range(max(0,pw)*types.get(typ,1)+3)]
 if length is None:length=len(values)-offset
 if pl is None:pl=len(palette)-po
 c=dict(name=name,typ=typ,op=op,mode=mode,observe=observe,params=[w,h,offset,length,pw,ph,po,pl],values=values,palette=palette,entries=entries or [])
 if source:c.update(source=source,sourceHash=hashlib.sha256((root/source).read_bytes()).hexdigest())
 cs.append(c)
for typ,(w,h),pattern,op in itertools.product(types,[(0,0),(0,1),(1,0),(1,1),(1,17),(17,1),(3,5),(5,3),(31,33),(129,17)],['zero','max','pattern','random'],['typed','dynamic']):
 maximum=65535 if typ.endswith('16') else 255;n=w*h*types[typ];rng=random.Random(n*31+maximum)
 values=[0 if pattern=='zero' else maximum if pattern=='max' else (i*1031+71)% (maximum+1) if pattern=='pattern' else rng.randrange(maximum+1) for i in range(n)]
 add(f'{op} {typ} {w}x{h} {pattern}',typ,op,w=w,h=h,values=values)
for typ,mode,observe,(w,h) in itertools.product(types,['normal','bad-image','bad-data','bad-metadata'],['either','data'],[(0,0),(0,1),(1,0),(2,3),(-2,0),(-2,-3),(0xffffffff,0),(2**63-1,0)]):
 add(f'demand {typ}/{mode}/{observe} {w}x{h}',typ,mode=mode,observe=observe,w=w,h=h,values=list(range(64)),length=64)
for typ in types:
 for offset,length,w,h in [(1,12,1,2),(3,20,1,2),(0,0,0,1),(3,0,1,0),(-1,0,1,0),(0,-1,1,0),(70,0,1,1),(0,70,1,1)]:
  add(f'slice {typ}/{offset}/{length}/{w}/{h}',typ,w=w,h=h,offset=offset,length=length,values=list(range(64)))
for typ,mode,observe in itertools.product(['y32','yf','rgbf','ycbcr8','cmyk8','cmyk16'],['normal','bad-image','bad-data'],['either','data']):add(f'unsupported {typ}/{mode}/{observe}',typ,'dynamic',mode,observe)
def entry(k,arg='',kind='string',ns=None):return [k,arg,kind,ns or []]
def string(k,s,arg=''):return entry(k,arg,ns=list(map(ord,s)))
def bits(v):return struct.unpack('<Q',struct.pack('<d',v))[0]
metadata=[entry('DpiX',ns=[93]),entry('DpiY',ns=[93])]+[string(k,v) for k,v in [('Comment','Test of comment'),('Description',"let's see the results"),('Copyright','meh'),('Title','A metadata test'),('Software','JuicyPixels test suite'),('Author',"It's a me")]]
add('original metadataWriteTest',w=16,h=16,values=[255]*768,entries=metadata)
add('original palettedPngCreation','rgba8','palette',w=256,h=128,values=list(range(256))*128,pw=256,palette=list(itertools.chain.from_iterable((255,128,128,255-x) for x in range(256))))
for component in ['luma','alpha','combined']:
 values=list(itertools.chain.from_iterable(([x] if component=='luma' else [((y//4)%2)*255] if component=='alpha' else [x,((y//4)%2)*255]) for y in range(256) for x in range(256)))
 add('original planeSeparationYA8Test '+component,'ya8' if component=='combined' else 'y8',w=256,h=256,values=values)
for name,count in [('planeseparation.png',3),('planeseparationrgba.png',4)]:
 for c in range(count):add(f'original plane separation {name} component {c}',op='plane',w=c,h=0,values=[],source='vendor/JuicyPixels-png-writer-tests-v3.3.8/'+name)
for path in sorted((root/'vendor/JuicyPixels-png-tests-v3.3.8/tests/pngsuite').glob('*.png')):
 if path.name not in ['xd0n2c08.png','xd9n2c08.png']:add('original PNGSuite re-encode '+path.name,op='transcode',w=0,h=0,values=[],source=str(path.relative_to(root)))
for key,text in itertools.product(['Title','Author','Description','Copyright','Software','Disclaimer','Warning','Source','Comment','Unknown'],['','ASCII','\0embedded\0','\x80\xff\u0100\U0001f600']):
 add(f'metadata text {key}/{text!r}',entries=[string(key,text,'custom\U0001f600')])
for values in itertools.product([None,0,1,72,93,2**32-1,2**63,2**64-1],repeat=2):
 entries=[entry(k,ns=[v]) for k,v in zip(['DpiX','DpiY'],values) if v is not None];add(f'metadata DPI {values}',entries=entries)
gammas=[0,0x8000000000000000,1,0x8000000000000001,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000001]+[bits(v) for v in [-1,-0.1,0.1,0.45455,1,2.2,42949.67295,1e20,-1e20,1e300]]
gammas+=sorted({bits((sign*float(2**e)+delta)/100000.0) for e,sign,delta in itertools.product([31,32,52,53,62,63,64,65,80,100],[-1,1],[-4096,-1,0,1,4096])})
for g in gammas:add(f'metadata gamma {g:016x}',entries=[entry('Gamma',ns=[g])])
for key in ['Width','Height','Format','ColorSpace','Exif','Unknown']:
 add('ignored metadata '+key,entries=[entry(key,arg='0' if key in ['Format','Exif'] else 'SRGB' if key=='ColorSpace' else 'ignored',kind='int' if key=='Unknown' else 'none',ns=[17])])
for order in itertools.permutations([string('Title','first'),string('Author','writer'),string('Title','last')]):add('metadata ordering '+str(order),entries=list(order))
for typ,pw,ph,mode,observe in itertools.product(['rgb8','rgba8'],[-1,0,1,2,16,255,256,257],[0,1,2],['normal','bad-image','bad-data','bad-palette','bad-palette-data','bad-metadata'],['either','data']):
 add(f'palette {typ}/{pw}x{ph}/{mode}/{observe}',typ,'palette',mode,observe,w=4,h=1,values=[0,0,0,0],pw=pw,ph=ph)
for typ,pw,index in itertools.product(['rgb8','rgba8'],[1,2,16,255,256],range(256)):
 add(f'palette index {typ}/{pw}/{index}',typ,'palette',w=1,h=1,values=[0,index],pw=pw)
for typ,offset,pl in itertools.product(['rgb8','rgba8'],[0,1,3],[0,3,6,8,11]):
 # RGB exports the whole logical palette vector; RGBA maps declared pixels.
 if typ=='rgba8' and pl<8:continue
 add(f'palette slice {typ}/{offset}/{pl}',typ,'palette',w=1,h=1,values=[1],pw=2,po=offset,pl=pl,palette=list(range(20)))
for typ,size in itertools.product(['y8','y16'],[16383,16384,16385,32767,32768,32769,65535,65536,65537,100003]):
 rng=random.Random(size);maximum=65535 if typ=='y16' else 255
 add(f'zlib boundary {typ}/{size}',typ,w=size,h=1,values=[rng.randrange(maximum+1) for _ in range(size)])
for mode in ['typed','dynamic']:add('file error '+mode,op='write',mode=mode,w=0,h=0,values=[])
def hs(v):
 if isinstance(v,str):return '"'+''.join('\\'+c if c in '\\"' else c if 32<=ord(c)<127 else '\\'+str(ord(c))+'\\&' for c in v)+'"'
 if isinstance(v,list):return '['+','.join(hs(x) for x in v)+']'
 return str(v)
for start in range(0,len(cs),8):
 batch=cs[start:start+8];req=[]
 for c in batch:
  args=[c['op'],c['typ'],str(root/c['source']) if 'source' in c else c['mode'],c['observe'],c['params'],c['values'],c['palette'],c['entries']]
  req.append('('+','.join(hs(v) for v in args[:-1])+',['+','.join('('+','.join(hs(v) for v in e)+')' for e in c['entries'])+'])')
 lines=subprocess.check_output([a.oracle],input=('\n'.join(req)+'\n').encode()).splitlines();assert len(lines)==len(batch)
 for c,line in zip(batch,lines):
  expected=json.loads(line)
  for key in ['error','exception']:
   if key in expected:expected[key]=''.join(map(chr,expected[key]))
  if 'hex' in expected:
   b=bytes.fromhex(expected['hex']);expected['length']=len(b);expected['sha256']=hashlib.sha256(b).hexdigest()
   if len(b)>256:del expected['hex']
  c['expected']=expected
 if start%512==0:print(f'Captured {start+len(batch)}/{len(cs)} PNG encoder results',flush=True)
target.write_bytes(gzip.compress(('\n'.join(json.dumps(c,separators=(',',':')) for c in [dict(sources=hashes,oracle=info,cases=len(cs),caseHash=digest(cs))]+cs)+'\n').encode(),mtime=0))
print(f'Captured {len(cs)} original PNG encoder results')
