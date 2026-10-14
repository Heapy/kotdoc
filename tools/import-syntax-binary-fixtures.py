#!/usr/bin/env python3
"""Original Binary instances and baked Skylighting map; no Kotlin expectations."""
import argparse,collections,gzip,hashlib,json,os,random,re,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/highlighting/syntax-binary.jsonl.gz'
data=root/'core/src/main/resources/highlighting/default-syntax-map'
provenance=data/'provenance.json'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_SYNTAX_BINARY_REFERENCE'));args=p.parse_args()
folders=['vendor/skylighting-syntax-0.15','vendor/skylighting-core-0.15','vendor/binary-syntax-0.8.9.3','vendor/containers-syntax-0.7','vendor/text-syntax-2.1.2','tools/haskell-syntax-binary-reference']
source_names=['tools/import-syntax-binary-fixtures.py','licenses/Skylighting-core-BSD-3-Clause.txt','licenses/Binary-BSD-3-Clause.txt','licenses/Containers-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','core/src/test/resources/highlighting/syntax.jsonl.gz']
for folder in folders:source_names+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
source_names += [str((data/'index.json').relative_to(root)),str(provenance.relative_to(root))]
source_names += sorted(str((data/(path.stem+'.bin.gz')).relative_to(root)) for path in (root/'vendor/skylighting-syntax-0.15/src/Skylighting/Syntax').glob('*.hs'))
def source_hashes():return {name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in source_names}
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
def compressed(path,bytes):
 path.parent.mkdir(parents=True,exist_ok=True)
 with path.open('wb') as out:
  with gzip.GzipFile(filename='',fileobj=out,mode='wb',mtime=0) as f:f.write(bytes)
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cases=[json.loads(line) for line in f]
 assert m['sources']==source_hashes() and m['cases']==len(cases) and m['caseHash']==digest(cases)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cases))
 assert m['kinds']==dict(collections.Counter(c.get('kind','') for c in cases))
 assert len([c for c in cases if c['op']=='baked'])==195
 info=json.loads(provenance.read_text())
 assert m['binarySha256']==info['binarySha256'] and len(info['modules'])==195
 for case in cases:
  if case['op']=='baked':
   raw=gzip.decompress((data/(Path(case['module']).stem+'.bin.gz')).read_bytes())
   assert raw.hex()==case['expected']['encoded']
 index=json.loads((data/'index.json').read_text())
 assert index==[[name,Path(info['modules'][name]).stem] for name in sorted(info['modules'])]
 print(f'Verified {len(cases)} Binary/default-map observations, 195 baked syntaxes, {len(m["sources"])} source/resource files');raise SystemExit
assert args.oracle,'--oracle required'
process=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
requests_count=0
def native(op,input):
 global requests_count
 requests_count+=1
 if requests_count%5000==0:print(f'Native requests: {requests_count}',flush=True)
 process.stdin.write(json.dumps([op,input],ensure_ascii=True)+'\n');process.stdin.flush()
 line=process.stdout.readline();assert line,(op,input)
 value=json.loads(line);assert not isinstance(value,dict) or 'exception' not in value,(op,input,value)
 return value
cases=[]
def add(op,input,kind='',**extra):
 expected=native(op,[kind,input] if op in ['encode','decode','equal'] else input)
 cases.append(dict(op=op,input=input,kind=kind,expected=expected,**extra));return expected
with gzip.open(root/'core/src/test/resources/highlighting/syntax.jsonl.gz','rt') as f:old=[json.loads(line) for line in f][1:]
seen=set();encoded=[]
for case in old:
 op=case['op']
 if op not in ['keyword','switch','item','word-set','matcher','rule','context','syntax','xml-model','words-int']:continue
 kind='syntax' if op=='xml-model' else 'word-set-int' if op=='words-int' else op
 value=case['input'] if op=='xml-model' else case['input'][:2] if op=='words-int' else case['input'][0]
 key=(kind,json.dumps(value,sort_keys=True));
 if key in seen:continue
 seen.add(key);binary=add('encode',value,kind);encoded.append((kind,binary));add('decode',binary,kind)
for token in 'KeywordTok DataTypeTok DecValTok BaseNTok FloatTok ConstantTok CharTok SpecialCharTok StringTok VerbatimStringTok SpecialStringTok ImportTok CommentTok DocumentationTok AnnotationTok CommentVarTok OtherTok FunctionTok VariableTok ControlFlowTok OperatorTok BuiltInTok ExtensionTok PreprocessorTok AttributeTok RegionMarkerTok InformationTok WarningTok AlertTok ErrorTok NormalTok'.split():
 b=add('encode',token,'token');add('decode',b,'token')
for points in [[],[0,10,92,34,127],[0xd800,0xdc00],[0xdc00,0xd800],[0x10ffff,ord('3')],[0x10000],list(range(256))]:
 b=add('encode',points,'string');add('decode',b,'string')
for point in [0,127,128,0x7ff,0x800,0xd800,0xdfff,0xffff,0x10000,0x10ffff]:
 b=add('encode',point,'char');add('decode',b,'char')
for text in ['','é','😀','\x00\r\n','Straße','\ud800\udc00']:
 b=add('encode',text,'text');add('decode',b,'text')
# All two-byte sequences exercise Binary Char's intentionally unchecked decoder.
for a in range(256):
 add('decode',bytes([a]).hex(),'char')
 for b in range(256):add('decode',bytes([a,b]).hex(),'char')
rng=random.Random(0x42494e415259)
for _ in range(2000):add('decode',rng.randbytes(rng.randrange(7)).hex(),'char')
# Exact GHC String characters inside Syntax, independently of JSON Text normalization.
for points in [[0xd800,0xdc00],[0xdc00,0xd800],[0xd800,ord('3')],[0x10000]]:
 filename=bytes.fromhex(native('encode',['string',points]))
 raw=struct.pack('>q',1)+b'x'+filename+struct.pack('>q',0)*8
 add('decode',raw.hex(),'syntax')
for points in [[0xd800,0xdc00],[0x10000],[ord('*'),0x10000],[ord('*'),0xd800,0xdc00]]:
 extension=bytes.fromhex(native('encode',['string',points]))
 raw=struct.pack('>q',1)+b'x'+struct.pack('>q',0)*7+struct.pack('>q',1)+extension+struct.pack('>q',0)
 add('decode',raw.hex(),'syntax')
 add('syntax-filenames',[raw.hex(),['𐀀','a𐀀','unknown']])
# Every derived constructor tag and representative prefixes/mutations.
for kind in ['token','matcher','switch','item','word-set','word-set-int']:
 for tag in range(256):add('decode',bytes([tag]).hex(),kind)
for kind,blob in encoded[::17]:
 raw=bytes.fromhex(blob)
 for n in sorted(set(range(min(len(raw),30))) | {max(0,len(raw)-1),len(raw)}):add('decode',raw[:n].hex(),kind)
 add('decode',(raw+b'\x00\xfftail').hex(),kind)
for kind in ['keyword','switch','item','word-set','matcher','rule','context','syntax','context-name','syntax-map','word-set-int','text','string','set-char','map-int']:
 for _ in range(100):add('decode',rng.randbytes(rng.randrange(81)).hex(),kind)
def i(n):return struct.pack('>q',n)
def text(t):b=t.encode('utf8');return i(len(b))+b
for b in [bytes([x]) for x in range(256)]+[bytes.fromhex(x) for x in ['e280','f09f80','e220','f09f00','eda080','e08080','f4908080','f09f9880']]+[rng.randbytes(rng.randrange(16)) for _ in range(300)]:
 add('decode',(i(len(b))+b+b'left').hex(),'text')
for n in [-2**63,-2,-1,0,1,2,2**63-1]:
 for kind in ['text','string','set-char','map-int']:
  for tail in [b'',b'abc',b'\x00'*24]:add('decode',(i(n)+tail).hex(),kind)
# Raw fromDistinctAscList preserves duplicates/order and original weight-balanced lookup.
for n in list(range(17))+[31,32,33,63,64,65,127,128,129,255]:
 for _ in range(5):
  values=[rng.randrange(97,104) for _ in range(n)]
  raw=i(n)+b''.join(bytes.fromhex(native('encode',['char',v])) for v in values)
  add('decode',raw.hex(),'set-char');add('equal',[raw.hex(),raw.hex()],'set-char');add('set-members',[raw.hex(),list(range(95,106))])
  rows=[(chr(v),index) for index,v in enumerate(values)]
  raw=i(n)+b''.join(text(k)+i(v) for k,v in rows)
  add('decode',raw.hex(),'map-int');add('equal',[raw.hex(),raw.hex()],'map-int');add('map-lookups',[raw.hex(),[chr(v) for v in range(95,106)]])
for sensitive in [True,False]:
 for values in [[],['b','a','a'],['Straße','SS','é','a'],['a','a','a'],['z','b','a','c','b']]:
  raw=bytes([0 if sensitive else 1])+i(len(values))+b''.join(text(v) for v in values)
  add('decode',raw.hex(),'word-set');add('word-members',[raw.hex(),values+['SS','ss','a','b','z','unknown']])
for kind in ['map-int','set-char']:
 same=[c['input'][0] for c in cases if c['op']=='equal' and c['kind']==kind]
 for a,b in zip(same,same[1:]):add('equal',[a,b],kind)
# Original baked bytes, not a Kotlin-generated substitute or reparse of the XML.
source=root/'vendor/skylighting-syntax-0.15/src/Skylighting/Syntax.hs'
rows=re.findall(r'\(\s*("(?:[^"\\]|\\.)*")\s*,\s*Skylighting\.Syntax\.(\w+)\.syntax\)',source.read_text())
assert len(rows)==195
requests=[];modules={};models={}
for key,module in rows:
 path='vendor/skylighting-syntax-0.15/src/Skylighting/Syntax/'+module+'.hs'
 quoted=(root/path).read_text().split('syntax = decode ',1)[1].strip()
 name=native('key',key);requests.append([key,quoted]);modules[name]=path
 result=native('baked',quoted);assert 'value' in result and result['remaining']==''
 raw=bytes.fromhex(native('unquote',quoted));assert raw.hex()==result['encoded']
 compressed(data/(module+'.bin.gz'),raw)
 models[name]=result['value'];cases.append(dict(op='baked',kind='syntax',input=name,module=path,expected=result))
default=native('default',requests);binary=bytes.fromhex(default['encoded']);sha=hashlib.sha256(binary).hexdigest()
(data/'index.json').write_text(json.dumps([[name,Path(modules[name]).stem] for name in default['names']],ensure_ascii=True,separators=(',',':'))+'\n')
info=dict(upstream='skylighting-0.15',source='vendor/skylighting-syntax-0.15/src/Skylighting/Syntax.hs',modules=modules,binarySha256=sha,bytes=len(binary),capture='Original generated module ByteStrings, original Data.Binary.decode, Map.fromList and whole-map encode observation')
provenance.write_text(json.dumps(info,indent=2,ensure_ascii=False)+'\n')
xml_models={c['input'][0]:c['expected']['resolved'] for c in old if c['op']=='xml-model'}
differences=[name for name,value in models.items() if xml_models[name]!=value]
cases.append(dict(op='default',kind='syntax-map',input=None,expected=dict(names=default['names'],missing=default['missing'],binarySha256=sha),xmlDifferences=differences))
process.stdin.close();assert process.wait()==0
for old in ['core/src/main/resources/highlighting/default-syntax-map.bin.gz','core/src/main/resources/highlighting/default-syntax-map.bin.provenance.json']:
 (root/old).unlink(missing_ok=True)
meta=dict(upstream='skylighting-0.15/skylighting-core-0.15/binary-0.8.9.3/containers-0.7/text-2.1.2',cases=len(cases),operations=dict(collections.Counter(c['op'] for c in cases)),kinds=dict(collections.Counter(c.get('kind','') for c in cases)),sources=source_hashes(),caseHash=digest(cases),binarySha256=sha,xmlDifferences=differences)
compressed(target,('\n'.join(json.dumps(c,ensure_ascii=True,separators=(',',':')) for c in [meta]+cases)+'\n').encode())
print(f'Captured {len(cases)} original observations; {len(binary)} baked map bytes; XML differences: {differences}',flush=True)
