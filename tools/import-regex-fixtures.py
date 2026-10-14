#!/usr/bin/env python3
"""Capture original KDE compiler/matcher and their Unicode/byte dependencies."""
import argparse,base64,collections,gzip,hashlib,itertools,json,os,random,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/highlighting/regex.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_REGEX_REFERENCE'));a=p.parse_args()
sources=['tools/import-regex-fixtures.py','tools/import-regex-char-data.py','licenses/Utf8-string-BSD-3-Clause.txt','licenses/Skylighting-core-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/Unicode-3.0.txt']
for folder in ['vendor/skylighting-core-0.15','vendor/utf8-string-1.0.2','vendor/ghc-unicode-9.12.2','vendor/ghc-regex-unicode-9.12.2','vendor/text-utf8-2.1.3','tools/haskell-regex-reference']:
 sources+=sorted(str(f.relative_to(root)) for f in (root/folder).rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
hashes={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources}
s=(root/'vendor/skylighting-core-0.15/test/test-skylighting.hs').read_text();declarations='regexTests ::'+s.split('regexTests ::',1)[1].split('\n\nregexErrorTest ::',1)[0]+'\n'
assert (root/'tools/haskell-regex-reference/OriginalRegexTests.hs').read_text().endswith(declarations)
compile_source=(root/'vendor/skylighting-core-0.15/src/Regex/KDE/Compile.hs').read_text()
horiz=compile_source.split('isHorizSpace c =',1)[1].split('\n\nisSpecial',1)[0]
assert 'isHorizSpace c ='+horiz in (root/'tools/haskell-regex-reference/RegexReference.hs').read_text()
xml=[]
for path in sorted((root/'vendor/skylighting-core-0.15/xml').glob('*.xml')):
 for i,el in enumerate(ET.parse(path).getroot().iter('RegExpr')):
  text=el.attrib.get('String','').removeprefix('^')
  xml.append((str(path.relative_to(root)),i,text,el.attrib.get('insensitive') not in ['true','yes','1'],el.attrib.get('minimal') in ['true','yes','1']))
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(line) for line in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert [(c['xml'],c['element']) for c in cs if 'xml' in c]==[(path,i) for path,i,_,_,_ in xml]
 assert len([c for c in cs if 'original' in c])==186 and len([c for c in cs if 'originalError' in c])==16
 assert [c['input'] for c in cs if c['op']=='chars']==[[n,min(n+4096,0x110000)] for n in range(0,0x110000,4096)]
 pairs=[bytes([i,j]).hex() for i in range(256) for j in range(256)]
 assert [v for c in cs if c.get('bytePairs') and c['op']=='utf8' for v in c['input']]==pairs
 assert [v for c in cs if c.get('bytePairs') and c['op']=='text-decode' for v in c['input']]==pairs
 print(f'Verified {len(cs)} KDE cases, {len(xml)} XML regexes, all Haskell Chars and byte pairs, {len(hashes)} source files');raise SystemExit
assert a.oracle,'--oracle required'
subprocess.run([a.oracle,'--original-tests'],check=True)
def native(requests):
 result=subprocess.run([a.oracle],input='\n'.join(json.dumps([c['op'],c['input']]) for c in requests)+'\n',text=True,capture_output=True,check=True)
 values=[json.loads(line) for line in result.stdout.rstrip('\n').split('\n')]
 assert len(values)==len(requests),(len(values),len(requests),result.stderr)
 for c,value in zip(requests,values):assert not isinstance(value,dict) or 'exception' not in value,(c,value)
 return values
original,=native([dict(op='original',input=None)])
assert all(c['expected']==c['actual'] for c in original['tests'])
cs=[]
def add(op,input,**extra):cs.append(dict(op=op,input=input,**extra))
def compile(text,subjects=(),sensitive=True,minimal=False,**extra):
 add('compile-compact' if extra.pop('compact',False) else 'compile',[text.encode().hex(),sensitive,minimal,[s.encode().hex() if isinstance(s,str) else s.hex() for s in subjects]],**extra)
for i,c in enumerate(original['tests']):compile(c['regex'],[c['input']],original=i,literal=c['expected'])
for i,c in enumerate(original['errors']):compile(c['regex'],originalError=i)
for path,i,text,sensitive,minimal in xml:compile(text,sensitive=True,xml=path,element=i)
for text,sensitive,minimal in sorted(set((text,sensitive,minimal) for _,_,text,sensitive,minimal in xml)):
 if not sensitive or minimal:compile(text,sensitive=sensitive,minimal=minimal,xmlFlags=True)
rng=random.Random(0x4b4445)
atoms=['a','b','c','é','Σ','𐀪','𐀨','.','[ab]','[^a]','[a-z]','\\d','\\w','\\s','\\h','\\pL','\\P{N}','\\b','^','$','%1','%0','%9223372036854775808','\\0','\\041','\\x61','\\g1','\\A','(?:a|b)','(a)','(a?)','(?=a)','(?!a)','(?i:a)','(?U:b+)','(?<=a)','(?<!a)']
subjects=['','a','aa','ab','abc','bba','a b','abAB','éx','Σς','😀','\t\r\n','𐀪','%1']
for _ in range(1800):
 pattern=''.join(rng.choice(atoms)+rng.choice(['','','?','*','+','??','*?','++','{0,2}','{1,3}']) for _ in range(rng.randrange(1,5)))
 compile(pattern,[rng.choice(subjects) for _ in range(3)],sensitive=rng.choice([False,True]),minimal=rng.choice([False,True]))
for _ in range(700):compile(''.join(rng.choice('abcxyz012\\?*+{}[]()|:^$%iU-.,') for _ in range(rng.randrange(24))),[rng.choice(subjects)])
for pattern in ['a{65535}','a{65536}','a{18446744073709551616}','a{9223372036854775808}','a{18446744073709551615,0}',
                'a{9223372036854775808,0}','%18446744073709551615','\\g18446744073709551616','(?18446744073709551616)',
                '(?|(a)(b)|(c))(d)\\2','(?i)(İ)\\1','(?i)[İ]','(?i)[ſ]','(?i)[K]','(?i)[Σ]','a(?<=(a))','é(?<=(é))',
                '(a*)(?<=\\1)','[\\Qabc-]\\E]+','[\\P{^Lu}]','[[:^alpha:]]','\\x{d800}','\\x{10ffff}','\\x{110000}',
                '\\zffff','\\z 061','\\x{61 }','(?i:\\x{130})','𐀪','𐀨','𐀿','(?𐁩)a','[𐀭-𐀿]']:
 compile(pattern,subjects,compact=pattern=='a{65535}')
for n in [1999,2000,2001,3000]:compile('a*a{'+str(n)+'}$',['a'*n],prune=n)
for n in [10,1000,5000]:compile('(?:'*n+'a'+')'*n,['a'],deep=n,compact=n>=1000)
for n in [1000,10000]:compile('a'*n,['a'*n],flat=n,compact=n>=10000)
# Compiler's character predicates: complete scalar+surrogate range, not host Unicode tables.
classes=['\\'+c for c in 'dDsShHwW']+['\\p{'+c+'}' for c in 'Lu Ll Lt Lm Lo L Mn Mc Me M Nd Nl No N Pc Pd Ps Pe Pi Pf Po P Sm Sc Sk So S Zs Zl Zp Z Cc Cf Cs Co Cn C unknown ^L'.split()]
classes+=['[[:'+c+':]]' for c in 'alnum alpha ascii blank cntrl digit graph lower upper print punct space word xdigit'.split()]
for pattern in classes+['[a-z]','[^a]','[İ]','[ſ]','[K]','[Σ]','[\\p{Lu}]']:
 for sensitive in ([False,True] if pattern.startswith('[') else [True]):
  add('predicates',[pattern.encode().hex(),sensitive,0,0x110000])
for start in range(0,0x110000,4096):add('chars',[start,min(start+4096,0x110000)])
pairs=[bytes([i,j]).hex() for i in range(256) for j in range(256)]
for start in range(0,len(pairs),256):
 for op in ['utf8','text-decode']:add(op,pairs[start:start+256],bytePairs=True)
raw=[b'',bytes(range(256))]+[bytes([b]) for b in range(256)]
raw += [bytes(xs) for xs in itertools.product([0xe0,0xed,0xef,0xf0,0xf4,0xf7],[0x7f,0x80,0x8f,0x90,0x9f,0xa0,0xbf,0xc0],[0x7f,0x80,0xbf,0xc0])]
raw += [rng.randbytes(rng.randrange(10)) for _ in range(500)]
for start in range(0,len(raw),128):
 for op in ['utf8','text-decode']:add(op,[b.hex() for b in raw[start:start+128]])
for pattern in raw[::11]:add('compile',[pattern.hex(),True,False,[b.hex() for b in raw[:8]]],invalidPattern=True)
for pattern in ['.','.*','(.)*','(.*)\\1','(?i:(.)\\1)','.(?<=.)','.(?<!.)','\\w','\\p{Cn}','\\x{fffe}']:
 for start in range(0,len(raw),128):compile(pattern,raw[start:start+128],invalidSubject=True)
base=[['any'],['null'],['start'],['end'],['boundary'],['char',97],['char',0x1f600],['char',0xd800],['sat','word'],['sat','space'],['dyn',-1],['dyn',42],['ref',1,False],['ref',0,True],['sub',0],['sub',1],['sub',2]]
def ast(n):
 if n==0:return rng.choice(base)
 tag=rng.choice(['concat','alt','some','lazy','poss','cap','positive','negative'])
 if tag in ['concat','alt']:return [tag,ast(n-1),ast(n-1)]
 if tag=='cap':return [tag,rng.choice([-1,0,1,2]),ast(n-1)]
 if tag in ['positive','negative']:return [tag,rng.choice(['forward','backward']),ast(n-1)]
 return [tag,ast(n-1)]
for _ in range(1600):
 add('ast',[ast(3),rng.choice([None,[],[[0,['char',97]]],[[1,['some',['char',97]]]],[[1,['sub',1]],[2,['any']]]]),rng.choice(raw[:258]).hex()])
for node in [['some',['null']],['lazy',['some',['null']]],['lazy',['alt',['char',97],['null']]],['cap',1,['positive','backward',['cap',2,['any']]]]]:
 for text in [b'',b'a',b'aa',b'\xff',bytes.fromhex('eda080')]:add('ast',[node,None,text.hex()])
for start in range(0,len(cs),32):
 batch=cs[start:start+32]
 for c,value in zip(batch,native(batch)):
  if c['op']=='compile-compact' and 'flat' in value:
   value['flat']=hashlib.sha256(base64.b64decode(value['flat'])).hexdigest()
   value['groups']=[[n,hashlib.sha256(base64.b64decode(data)).hexdigest()] for n,data in value['groups']]
  if c['op']=='chars':value=hashlib.sha256(base64.b64decode(value)).hexdigest()
  if c['op']=='predicates':value={'count':value['count'],'sha256':hashlib.sha256(base64.b64decode(value['bytes'])).hexdigest()}
  c['expected']=value
  if 'literal' in c:
   expected=c['literal'];match=value['matches'][0]
   observed=None if match is None else [bytes.fromhex(match['bytes']).decode(),[[row[0],row[4]] for row in match['captures']]]
   assert expected==observed,(c,observed)
 print(f'{min(start+32,len(cs))}/{len(cs)}',flush=True)
m=dict(upstream='skylighting-core-0.15/utf8-string-1.0.2/GHC-9.12.2/text-2.1.3',sources=hashes,cases=len(cs),caseHash=digest(cs),operations=dict(collections.Counter(c['op'] for c in cs)),originalTests=[186,16],xmlRegexes=len(xml),xmlCompileErrors=sum('error' in c['expected'] for c in cs if 'xml' in c))
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',filename='',mtime=0) as f:f.write(('\n'.join(json.dumps(c,ensure_ascii=False,separators=(',',':')) for c in [m]+cs)+'\n').encode())
print(f'Captured {len(cs)} original KDE observations: {m["operations"]}; XML compile errors {m["xmlCompileErrors"]}')
