#!/usr/bin/env python3
"""Capture unchanged Skylighting syntax-model, map and RE behavior, never Kotlin output."""
import argparse,base64,collections,copy,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/highlighting/syntax.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_SYNTAX_REFERENCE'));args=p.parse_args()
folders=['vendor/skylighting-core-0.15','vendor/utf8-string-1.0.2','vendor/base64-bytestring-1.2.1.0','tools/haskell-syntax-reference','vendor/binary-re-0.8.9.3','vendor/bytestring-re-0.12.2.0','vendor/ghc-9.12.2']
sources=['tools/import-syntax-fixtures.py','licenses/Skylighting-core-BSD-3-Clause.txt','licenses/Base64-bytestring-BSD-3-Clause.txt','licenses/Binary-BSD-3-Clause.txt','licenses/ByteString-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt']
for folder in folders:sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
hashes={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources}
xml=sorted(str(p.relative_to(root)) for p in (root/'vendor/skylighting-core-0.15/xml').glob('*.xml'))
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert [c['xml'] for c in cs if c['op']=='xml-model']==xml
 print(f'Verified {len(cs)} syntax-model/map/RE observations, all {len(xml)} XML models, {len(hashes)} source files');raise SystemExit
assert args.oracle,'--oracle required'
def native(requests):
 r=subprocess.run([args.oracle],cwd=root,input='\n'.join(json.dumps(req) for req in requests)+'\n',text=True,capture_output=True,check=True)
 rows=[json.loads(line) for line in r.stdout.rstrip('\n').split('\n')]
 assert len(rows)==len(requests),(len(rows),len(requests),r.stderr)
 for request,row in zip(requests,rows):assert not isinstance(row,dict) or 'exception' not in row,(request,row)
 return rows
cs=[]
def add(op,input,**kw):cs.append(dict(op=op,input=input,**kw))
models,=native([['xml',xml]])
for path,row in zip(xml,models):add('xml-model',row['input'],expected={k:v for k,v in row.items() if k!='input'},xml=path)
# Every original constructor and field is observed, plus generated comparisons.
rng=random.Random(0x53594e544158)
texts=['','a','A','Z','é','ß','SS','Σ','σ','ς','İ','i\u0307','𐀨','\ue000','😀','\x00\n\t\\\"','SOH']
chars=[0,7,9,10,13,14,31,32,34,39,92,127,233,0xd800,0xffff,0x10000,0x10ffff]
tokens='KeywordTok DataTypeTok DecValTok BaseNTok FloatTok ConstantTok CharTok SpecialCharTok StringTok VerbatimStringTok SpecialStringTok ImportTok CommentTok DocumentationTok AnnotationTok CommentVarTok OtherTok FunctionTok VariableTok ControlFlowTok OperatorTok BuiltInTok ExtensionTok PreprocessorTok AttributeTok RegionMarkerTok InformationTok WarningTok AlertTok ErrorTok NormalTok'.split()
nullary='Int Float HlCOct HlCHex HlCStringChar HlCChar DetectSpaces DetectIdentifier'.split()
def text():return rng.choice(texts)
def kw():return [rng.choice([True,False]),rng.sample(chars,rng.randrange(5))]
def ws():return [rng.choice([True,False]),[text() for _ in range(rng.randrange(5))]]
def ctx():return [text(),text()]
def switch():return [] if rng.randrange(2) else [ctx()]
def item():return ['Item',text()] if rng.randrange(2) else ['IncludeList',ctx()]
def re():return [rng.choice(['','6162','28','5c','00ff','c3a9','eda080']),rng.choice([True,False]),rng.choice([True,False])]
def matcher():
 tag=rng.choice(['DetectChar','Detect2Chars','AnyChar','RangeDetect','StringDetect','WordDetect','RegExpr','Keyword','LineContinue','IncludeRules']+nullary)
 if tag in nullary:return [tag]
 if tag in ['DetectChar','LineContinue']:return [tag,rng.choice(chars)]
 if tag in ['Detect2Chars','RangeDetect']:return [tag,rng.choice(chars),rng.choice(chars)]
 if tag=='AnyChar':return [tag,rng.sample(chars,rng.randrange(5))]
 if tag in ['StringDetect','WordDetect']:return [tag,text()]
 if tag=='RegExpr':return [tag,re()]
 if tag=='Keyword':return [tag,kw(),rng.choice([['Left',text()],['Right',ws()]])]
 return [tag,ctx()]
def rule(depth=0):return [matcher(),rng.choice(tokens),rng.choice([True,False]),rng.sample(chars,rng.randrange(5)),rng.choice([True,False]),rng.choice([True,False]),[rule(depth+1) for _ in range(rng.randrange(3))] if depth<2 else [],rng.choice([True,False]),rng.choice([True,False]),rng.choice([None,-2**63,-1,0,1,2**63-1]),[switch() for _ in range(rng.randrange(3))]]
def context():return [text(),text(),[rule() for _ in range(rng.randrange(3))],rng.choice(tokens),[switch() for _ in range(rng.randrange(3))],[switch() for _ in range(rng.randrange(3))],rng.choice([True,False]),[switch() for _ in range(rng.randrange(3))],rng.choice([True,False])]
def syntax():return [text(),text(),text(),[[text(),[item() for _ in range(rng.randrange(4))]] for _ in range(rng.randrange(4))],[[text(),context()] for _ in range(rng.randrange(3))],text(),text(),text(),[text() for _ in range(rng.randrange(3))],text()]
for op,gen in [('keyword',kw),('switch',switch),('item',item),('word-set',ws),('re',re),('matcher',matcher),('rule',rule),('context',context),('syntax',syntax)]:
 for _ in range(150):
  x=gen();add(op,[x,x]);add(op,[x,gen()])
 # Compare every individual field with a changed version of the same value.
 for _ in range(40):
  x=gen();y=gen()
  if len(x)!=len(y) or op in ['matcher','item'] and x[0]!=y[0]:continue
  for i in range(len(x)):
   z=copy.deepcopy(x);z[i]=y[i];add(op,[x,z])
matchers=[matcher() for _ in range(150)]+[[t] for t in nullary]
for x in matchers:
 for y in matchers[::15]:add('matcher',[x,y])
for sensitive,raw in itertools.product([False,True],repeat=2):
 for _ in range(200):add('words',[sensitive,[text() for _ in range(rng.randrange(7))],texts,raw])
for sensitive in [False,True]:
 for words in [[],[1,2,3,2],[-2**63,2**63-1,0,-1]]:add('words-int',[sensitive,words,[]])
for v in [None,True,42,'',[],{}, {'reString':''},{'reCaseSensitive':True},{'reString':None,'reCaseSensitive':True},
          {'reString':'','reCaseSensitive':None},{'reString':'','reCaseSensitive':'True'}]:add('re-json',v)
for minimal in [None,True,False,'false',0,[],{}]:add('re-json',{'reString':'AA==','reCaseSensitive':True,'reMinimal':minimal})
for _ in range(300):
 b=rng.randbytes(rng.randrange(80));v={'reString':base64.b64encode(b).decode(),'reCaseSensitive':rng.choice([True,False])}
 if rng.randrange(2):v['reMinimal']=rng.choice([True,False])
 add('re-json',v)
# Decoder branch/error priority: padded/unpadded, canonical bits, malformed UTF8 bytes of Text.
for s in ['','A','AA','AAA','====','A===','AA=A','AA==AAAA','AAA=AAAA','A?=A','?A=A','?=AA','AA?=','AA=?','AB==','AAF=','AA-_','AA\n=','é===']:
 add('re-json',{'reString':s,'reCaseSensitive':True})
for xs in itertools.product('A/= ?é',repeat=4):add('re-json',{'reString':''.join(xs),'reCaseSensitive':True})
for _ in range(1000):add('re-json',{'reString':''.join(rng.choice('Ab9+/=?\né') for _ in range(rng.randrange(17))),'reCaseSensitive':True})
# Complete native syntax map, including extensions/names/short names of every syntax.
queries=[]
for row in models:
 s=row['input']
 for typ,name in [('name',s[0]),('name',s[0].upper()),('short',s[2]),('lookup',s[2].lower())]:queries.append([typ,name])
 for ext in s[8]:queries.extend([['filename',ext.replace('*','file')],['extension',ext.removeprefix('*.')]])
queries += [[typ,n] for typ in ['lookup','name','short','extension','filename'] for n in ['','csharp','CSharp','fortran','Fortran','.cs','cs','Makefile','foo.C','foo.cpp','a/b.py','unknown']]
add('map',[[[row['input'][0],row['input']] for row in models],queries],builtin=True)
# Synthetic includes: empty syntax name remains empty, missing list unchanged,
# only top-level rules resolved, and list includes may cross syntaxes.
def simple_rule(m,children=[]):return [m,'NormalTok',False,[],False,True,children,False,False,None,[]]
def simple_context(rules):return ['start','A',rules,'NormalTok',[],[],False,[],False]
def simple_syntax(name,lists,rules,ext=['*.x'],short='X'):return [name,name+'.xml',short,lists,[['start',simple_context(rules)]],'','','',ext,'start']
for sensitive in [False,True]:
 k=['Keyword',[sensitive,[32]],['Left','local']]
 a= simple_syntax('A', [['local',[['Item','Straße'],['IncludeList',['B','foreign']],['IncludeList',['','foreign']],['IncludeList',['A','missing']]]]],
   [simple_rule(k),simple_rule(['Keyword',[True,[]],['Left','missing']]),simple_rule(['Int'],[simple_rule(k)]),simple_rule(['IncludeRules',['Absent','ctx']]),simple_rule(['IncludeRules',['Absent','other']])])
 b= simple_syntax('B',[['foreign',[['Item','Σ'],['Item','SS'],['IncludeList',['C','deep']]]]],[])
 c= simple_syntax('C',[['deep',[['Item','Ω']]]],[])
 add('map',[[['A',a],['B',b],['C',c]],[]])
for _ in range(60):
 rows=[[text(),simple_syntax(text(),[],[],rng.sample(['*','*.x','foo?','[abc]','*.*','a*b*c','😀*','*😀','a/**/b',''],rng.randrange(6)),text())] for _ in range(rng.randrange(8))]
 add('map',[rows,[[typ,text()] for typ in ['name','short','lookup','extension','filename'] for _ in range(5)]+[['filename',s] for s in ['','foo?','foox','[abc]','a/b.py','a/😀','😀']]])
# Exact map keys precede case-insensitive fallback; key order may differ from UTF16.
rows=[[name,simple_syntax(name,[],[],['*.'+name],name)] for name in ['aa','AA','Aa','𐀨','\ue000','İ','i\u0307','Σ','σ','ς','ß','SS']]
add('map',[rows,[[typ,name] for typ in ['name','short','lookup','extension','filename'] for name in ['AA','aa','aA','İ','i\u0307','Σ','σ','ς','ß','ss','\ue000','𐀨']]])

# Native Read RE accepts Haskell Strings, including character-list syntax and Char8 truncation.
for pattern in ['',r'"abc"',r'"\\NUL\\SOH\\SO\\&H"',r'"\\256\\1114111"',r'"\\xD800\\xDC00"']:
 for sensitive,minimal in itertools.product([True,False],repeat=2):
  literal=pattern or '""'
  value='RE {reString = '+literal+', reCaseSensitive = '+str(sensitive)+', reMinimal = '+str(minimal)+'}'
  for shown in [value,' (('+value+')) ',value+' trailing',value.replace('reString','reStrings'),value.replace(' = ',' =+ ',1)]:add('re-read',shown)
def record(literal='""',cs='True',mini='False'):return f'RE {{reString = {literal}, reCaseSensitive = {cs}, reMinimal = {mini}}}'
for literal in ['[]',"['a','b']","[('a'), ('\\x100')]",'("text")','((("text")))','"raw\nnewline"',r'"a\ \b"',r'"a\&\&b"',r'"\SOH"',r'"\SOHA"',r'"\SO\&H"',r'"\SP"',r'"\DEL"',r'"\^@\^_"',r'"\1114111"',r'"\1114112"',r'"\O377\Xff"',r'"\b101"',r'"\o8"',r'"\x"',r'"\^a"',r'"\q"',r'"unterminated',"['']","['ab']","['\\&']",'["a"]',"['a',]",r'"\x100000000000000000000000000000000000"']:
 for bool_text in ['True','(True)','((False))','true','TrueX','True False']:
  add('re-read',record(literal,bool_text))
for space in ['',' ','\n\t','\u0085','\u00a0','\u2000','\u2028','\u2029','\u200b']:
 add('re-read',space+record().replace(' ',space)+space)
for cp in range(0,256):
 for escape in ['\\'+str(cp),'\\x'+format(cp,'x'),'\\O'+format(cp,'o')]:add('re-read',record('"'+escape+'"'))
for _ in range(300):
 r=re();add('re-binary',r)
 b=bytes.fromhex(r[0]);encoded=len(b).to_bytes(8,'big',signed=True)+b+bytes([r[1],r[2]])
 for payload in [encoded,encoded+rng.randbytes(3),encoded[:rng.randrange(len(encoded)+1)]]:add('re-binary-decode',payload.hex())
for length,flags in itertools.product([-2**63,-1,0,1,10,2**63-1], [b'',b'\0',b'\0\1',b'\1\1',b'\2\0',b'\0\xff']):add('re-binary-decode',(length.to_bytes(8,'big',signed=True)+flags).hex())
for _ in range(300):add('re-binary-decode',rng.randbytes(rng.randrange(25)).hex())
json_cases=[c for c in cs if c['op']=='re-json']
for c in json_cases:add('re-json-decode',c['input'])
configs=[]
def visit(value):
 if isinstance(value,list):
  if value and value[0]=='RegExpr':configs.append(value[1])
  for child in value:visit(child)
for row in models:visit(row['input'])
for config in configs[::29]+[re() for _ in range(100)]:add('re-compile',[config,['','61','6162','4142','c3a9','00ff']])

for start in range(0,len(cs),32):
 batch=[c for c in cs[start:start+32] if 'expected' not in c]
 if batch:
  for c,out in zip(batch,native([[c['op'],c['input']] for c in batch])):c['expected']=out
 print(f'{min(start+32,len(cs))}/{len(cs)}',flush=True)
m=dict(upstream='skylighting-core-0.15',sources=hashes,cases=len(cs),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),xmlModels=len(xml))
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',filename='',mtime=0) as f:f.write(('\n'.join(json.dumps(c,ensure_ascii=False,separators=(',',':')) for c in [m]+cs)+'\n').encode())
print(f'Captured {len(cs)} syntax observations: {m["operations"]}')
