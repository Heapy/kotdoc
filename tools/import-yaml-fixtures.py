#!/usr/bin/env python3
"""Native libyaml reader state and original YAML scalar schema; never runs Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/yaml/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--reader',default=os.environ.get('KOTDOC_YAML_READER_REFERENCE'));p.add_argument('--scalar',default=os.environ.get('KOTDOC_YAML_SCALAR_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-yaml-fixtures.py','upstream/pandoc/src/Text/Pandoc/Translations.hs','upstream/pandoc/src/Text/Pandoc/Translations/Types.hs','vendor/xml-parser-attoparsec-0.14.4/Data/Attoparsec/Text.hs','vendor/xml-parser-attoparsec-0.14.4/LICENSE','licenses/Yaml-BSD-2-Clause.txt','licenses/Libyaml-BSD-2-Clause.txt','licenses/Libyaml-MIT.txt']
for folder in ['vendor/yaml-0.11.11.2','vendor/libyaml-0.1.4','vendor/libyaml-clib-0.2.5','tools/haskell-yaml-reader-reference','tools/haskell-yaml-scalar-reference']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file())
original_paths=[]
for f in sorted((root/'upstream/pandoc/data/translations').glob('*.yaml')):
 path=str(f.relative_to(root));sources.append(path);original_paths.append(path)
def hashes():
 return {f:hashlib.sha256((os.readlink(root/f).encode() if (root/f).is_symlink() else (root/f).read_bytes())).hexdigest() for f in sorted(sources)}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert sum(c['request'][2]-c['request'][1] for c in cs if c['request'][0]=='domain')==0x110000
 assert sum(c['request'][2]-c['request'][1] for c in cs if c['request'][0]=='units')==2*65536
 assert sum(c['group']=='bytepairs' for c in cs)==65536
 assert set(c['path'] for c in cs if c.get('path'))==set(original_paths)
 print(f'Verified {len(cs)} YAML cases, complete UTF8/UTF16 domains and {len(sources)} source hashes');raise SystemExit
assert args.reader and args.scalar,'both native oracles required'
procs={name:subprocess.Popen([exe],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True) for name,exe in [('reader',args.reader),('scalar',args.scalar)]}
def cleanup():
 for p in procs.values():
  if p.poll() is None:p.terminate();p.wait()
atexit.register(cleanup);cs=[]
def add(req,group,**flags):
 proc=procs['reader' if req[0] in ['reader','domain','units'] else 'scalar']
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 cs.append(dict(request=req,expected=json.loads(line),group=group,**flags))
 if len(cs)%10000==0:print('Captured',len(cs),'YAML cases',flush=True)
def rd(bs,chunks=(),encoding=0,offset=0,steps=((0,2),(1,1),(0,4)),group='reader',**flags):add(['reader',list(bs),list(chunks),encoding,str(offset),list(steps)],group,**flags)
for start in range(0,0x110000,1024):add(['domain',start,min(start+1024,0x110000)],'unicode-domain')
for encoding in [2,3]:
 for start in range(0,65536,1024):add(['units',start,start+1024,encoding],'utf16-domain')
for a,b in itertools.product(range(256),repeat=2):rd([a,b],group='bytepairs')
samples=[b'',b'A',b'ABC',b'A\0B',b'\xef\xbb\xbf',b'\xff\xfe',b'\xfe\xff',b'\xef\xbb\xbfABC',b'\xff\xfeA\0B\0',b'\xfe\xff\0A\0B',b'\xf0\x90\x80\x80',b'\xf4\x8f\xbf\xbf',b'\xed\xa0\x80',b'\xe0\x80\x80',b'\xf4\x90\x80\x80',b'\xf7\xbf\xbf\xbf',b'\xc2',b'\xc2A',b'\xe0\xa0',b'\xe0A\x80',b'\xf0\x90A\x80',b'\xe2\x82\xac']
samples += [s.encode(e) for s,e in itertools.product(['🙂漢é\nABC','A\r\nB\u0085\u2028\u2029','\ufeffA\ufeff','\ufffe','\uffff'],['utf-8','utf-16le','utf-16be'])]
for bs,encoding,chunks in itertools.product(samples,range(4),[[],[1]*8,[2,1,3,4],[0],[-1],[1,-1],[2,-1],[3,-1],[3,0]]):rd(bs,chunks,encoding,group='chunks')
for bs,offset,chunks in itertools.product([b'',b'A',b'ABC',b'\xef\xbb\xbfABC',b'A'*30],[0,2**63-5,2**63-1,2**63,2**64-1],[[],[3,3],[1,1,1,1]]):rd(bs,chunks,1,offset,group='offset')
# Actual buffer capacities, compaction, trailing partial encodings and lookahead.
for size,tail in itertools.product([16382,16383,16384,16385,32768,40000],[b'',b'\xe2\x82\xac',b'\xe2\x82',b'\xff']):
 bs=b'A'*size+tail
 steps=[(0,1),(1,16000),(0,8),(1,1000),(0,3),(1,16000),(0,4)]
 rd(bs,steps=steps,group='buffers')
for path in original_paths:
 bs=(root/path).read_bytes()
 for chunks in [[],[1]*10,[2,3,5,7]]:rd(bs,chunks,steps=[(0,4),(1,4),(0,4)],group='pandoc-files',path=path)
rng=random.Random(0x025011112)
for _ in range(2500):
 bs=rng.randbytes(rng.randrange(24));encoding=rng.randrange(4)
 chunks=[rng.choice([-1,0,1,2,3,4,8]) for _ in range(rng.randrange(8))]
 rd(bs,chunks,encoding,steps=[(rng.randrange(2),rng.randrange(8)) for _ in range(rng.randrange(2,12))],group='reader-fuzz')
def cp(s):return list(map(ord,s)) if isinstance(s,str) else s
texts=['','y','Y','yes','Yes','YES','yEs','n','No','off','Off','OFF','True','TRUE','tRUE','Null','NULL','null','~','*','\n','\r','null\n','null ',' false','0','-0','+0','01','1.','1.e3','.1','-1.2e-3','NaN','.nan','.inf','Infinity','0xFF','0XFF','-0xFF','+0xFF','0o17','0o8','1:00','1_000','0b01','fo"o',"fo'o","fo\\'o",'fo: o','foo\nbar\nbaz\n','漢🙂',[0xd800],[0xd800,0xdc00]]
for t in texts:add(['scientific',cp(t)],'scientific');add(['string',cp(t),None],'strings')
for coefficient,exponent in itertools.product(['1','-1','+1','0','12345','12345.00','-0.001'],['','e0','e+3','E-3','e9223372036854775807','e9223372036854775808','e-9223372036854775809','e18446744073709551616','e'+str(10**100)]):add(['scientific',cp(coefficient+exponent)],'scientific')
for prefix,tail in itertools.product(['','+','-','0x','0o','1.','1e','1e+','1e-','1.E'],['','0','1','9','a','F','é','\0','\n',' ','+','-','.','e']):add(['scientific',cp(prefix+tail)],'scientific')
for _ in range(3500):
 s=''.join(rng.choice('0123456789abcdefxop+-._ eE\t\n') for _ in range(rng.randrange(40)))
 add(['scientific',cp(s)],'numeric-fuzz')
for text,style,tag in itertools.product([t for t in texts if isinstance(t,str)],range(7),['StrTag','FloatTag','NullTag','BoolTag','SetTag','IntTag','SeqTag','MapTag','NoTag','UriTag']):add(['scalar',list(text.encode()),style,tag],'scalar')
for a,b in itertools.product(range(256),repeat=2):
 if (a*256+b)%17==0:add(['scalar',[a,b],1,'NoTag'],'scalar-bytes')
for anchor,text in itertools.product([None,'','name','漢🙂',[0xd800],[0xd800,0xdc00]],texts):add(['string',cp(text),None if anchor is None else cp(anchor)],'strings')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),unicodeObservations=0x110000+2*65536,oracle='Unchanged complete libyaml 0.2.5 api/reader C source and yaml 0.11.11.2 scalar helpers/actual event decoding, GHC 9.12.2 frozen original packages')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'YAML cases plus',m['unicodeObservations'],'native domain observations; SHA',m['caseHash'],flush=True)
