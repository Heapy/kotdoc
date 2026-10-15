#!/usr/bin/env python3
"""Capture original MathML reader/legacy XML observations; never invoke Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root/'vendor/texmath-0.13.3.1';folder=root/'tools/haskell-mathml-reader-reference'
target=root/'core/src/test/resources/mathml-reader/data.jsonl.gz';data=root/'core/src/main/resources/io/heapy/kotdoc/mathml-entities.json'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_MATHML_READER_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
entity_source=source/'src/Text/TeXMath/Readers/MathML/EntityMap.hs'
assert (folder/'Text/TeXMath/Readers/MathML/EntityMap.hs').read_bytes()==entity_source.read_bytes()
keys=sorted(set(re.findall(r'\("([^"\n]+)",',entity_source.read_text())))
sources=['tools/import-mathml-reader-fixtures.py','tools/haskell-math-reader-reference/MathWire.hs','licenses/Texmath-GPL-2.0.txt','licenses/XML-Light-BSD-3-Clause.txt',str(data.relative_to(root))]
sources+=sorted(str(f.relative_to(root)) for base in [source,root/'vendor/xml-1.3.14',folder] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
def originals():
 out=[]
 for path in sorted((source/'test').rglob('*.test')):
  lines=path.read_bytes().split(b'\n');lines=lines[:-1] if lines[-1]==b'' else lines
  starts=[i for i,l in enumerate(lines) if l.startswith(b'<<<')]
  if not starts:continue
  start=starts[0];fmt=lines[start][3:].strip()
  if fmt not in [b'mml',b'mathml']:continue
  end=next(i for i in range(start+1,len(lines)) if lines[i].startswith(b'>>>'))
  text=(b'\n'.join(lines[start+1:end])+b'\n' if end>start+1 else b'').decode()
  golden=(b'\n'.join(lines[end+1:])+b'\n' if end<len(lines)-1 else b'').decode()
  out.append((str(path.relative_to(root)),text,lines[end][3:].strip().decode(),golden))
 return out
def cp(s):return list(map(ord,s)) if isinstance(s,str) else s
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 original={c['source']:c for c in cs if c['group']=='original-mathml-input'}
 inputs=originals();assert len(inputs)==len(original)
 fixed=[x for x in inputs if '/test/reader/mml/' in x[0]];assert len(fixed)==576
 for path,text,fmt,golden in inputs:
  c=original[path];assert c['request']==['read',cp(text)]
  if fmt=='native':assert c['golden']==cp(golden)
 entries=next(c['expected'] for c in cs if c['group']=='complete-entity-map')
 assert sorted(dict(entries))==keys and json.loads(data.read_text())==dict(entries)
 assert all(not isinstance(c['expected'],dict) or 'host' not in c['expected'] for c in cs)
 print('Verified',len(cs),'complete MathML/XML observations,',len(inputs),'original inputs, 576 original reader goldens,',len(keys),'entities and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
binary=Path(args.oracle).resolve();native_tests=binary.parents[4]/'original-texmath-tests/opt/build/original-texmath-tests/original-texmath-tests'
subprocess.run([str(native_tests),'--pattern','($2 == "reader" && $3 == "mml") || ($2 == "regression" && $3 == "252")'],cwd=source,check=True)
proc=subprocess.Popen([str(binary)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 result=json.loads(line);assert not isinstance(result,dict) or 'host' not in result,(req,result)
 return result
def add(req,group,**extra):
 c=dict(request=req,expected=ask(req),group=group,**extra);cs.append(c)
 if len(cs)%1000==0:print('Captured',len(cs),'MathML/XML observations',flush=True)
 return c
entries=add(['entities',keys],'complete-entity-map')['expected'];assert all(v is not None for k,v in entries)
data.write_text(json.dumps(dict(entries),ensure_ascii=True,separators=(',',':'))+'\n')
for path,text,fmt,golden in originals():
 c=add(['read',cp(text)],'original-mathml-input',source=path)
 add(['xml-text',cp(text)],'original-mathml-xml-input',source=path)
 if fmt=='native':
  actual=ask(['golden',cp(text)]);actual=actual if not actual or actual.endswith('\n') else actual+'\n'
  assert actual==golden,(path,actual,golden)
  c['golden']=cp(golden)
  if c['expected'][0]=='Right':assert c['expected'][1]==ask(['native',cp(golden)]),path
# Every entity is exercised through all token categories and style-independent
# unknown/case-sensitive paths, not just read from a generated table.
for key in keys+['unknown','epsilon-unknown','AMPx','']:
 for tag in ['mi','mn','mo','mtext','ms']:
  add(['read',cp('<math><'+tag+'>&'+key+';</'+tag+'></math>')],'every-entity-in-tokens')
tags=['mi','mn','mo','mtext','ms','mspace','mrow','mstyle','mfrac','msqrt','mroot','merror','mpadded','mphantom','mfenced','menclose','msub','msup','msubsup','munder','mover','munderover','mtable','maction','semantics','maligngroup','malignmark','mmultiscripts','none','unknown']
payloads=['','<mi>x</mi>','<mi>x</mi><mi>y</mi>','<mi>x</mi><mn>1</mn><mn>2</mn>','text &amp; &unknown;','<mo>(</mo><mi>x</mi><mo>)</mo>']
for tag,body,pre in itertools.product(tags,payloads,['','\n\n']):add(['read',cp('<math>'+pre+'<'+tag+'>'+body+'</'+tag+'></math>')],'all-element-arity-and-lines')
styles=['normal','bold','italic','monospace','sans-serif','double-struck','script','fraktur','bold-italic','bold-sans-serif','sans-serif-bold-italic','bold-script','bold-fraktur','sans-serif-italic','unknown']
for style,tag,parent in itertools.product(styles,['mi','mtext','ms','mstyle'],['mrow','mstyle','math']):
 text='<'+tag+' mathvariant="'+style+'">ℬℂ𝔸𝛂Γαx 𝟙</'+tag+'>'
 add(['read',cp('<math><'+parent+' mathvariant="bold">'+text+'</'+parent+'></math>')],'style-inheritance-and-unicode')
for key,values in {'form':['prefix','postfix','infix','unknown',''], 'fence':['true','false','TRUE',''], 'accent':['true','false'], 'accentunder':['true','false'], 'stretchy':['true','false']}.items():
 for value,op,around in itertools.product(values,['(',')','|','+','∑','wide','sin'],['','<mi>x</mi>']):
  add(['read',cp('<math>'+around+'<mo '+key+'="'+value+'">'+op+'</mo>'+around+'</math>')],'operator-forms-flags-and-positions')
for op,script,kind in itertools.product(['(',')','|','∑','x'],['<mn>2</mn>','<mo>^</mo>','<mrow><mi>a</mi><mi>b</mi></mrow>'],['msub','msup']):
 add(['read',cp('<math><mo>(</mo><'+kind+'><mo stretchy="true">'+op+'</mo>'+script+'</'+kind+'><mi>x</mi><mo>)</mo></math>')],'stretchy-script-reordering')
for key,vals in {'width':['thinmathspace','negativeverythickmathspace','1em','0pt','0','bad'], 'linethickness':['thin','medium','thick','0','0px','bad'], 'selection':['0','1','2','3','-1','9223372036854775808','18446744073709551617','bad'], 'notation':['box','updiagonalstrike','downdiagonalstrike','updiagonalstrike downdiagonalstrike','unknown']}.items():
 tag={'width':'mspace','linethickness':'mfrac','selection':'maction','notation':'menclose'}[key]
 for val in vals:add(['read',cp('<math><'+tag+' '+key+'="'+val+'"><mi>x</mi><mi>y</mi></'+tag+'></math>')],'attributes-numbers-and-enclosures')
for align,row,cell in itertools.product(['left','center','right','left right','unknown'],['mtr','mlabeledtr','wrong'],['mtd','wrong']):
 add(['read',cp('<math><mtable columnalign="'+align+'"><'+row+'><'+cell+'><mi>x</mi></'+cell+'></'+row+'><mtr><mtd><mn>2</mn></mtd><mtd/></mtr></mtable></math>')],'table-alignment-and-padding')
for body in ['', '<mi>x</mi>', '<merror/>', '<merror/><annotation encoding="MathML-Presentation"><mi>y</mi></annotation>', '<merror/><annotation encoding="MathML-Presentation"><mi>y</mi></annotation><annotation encoding="MathML-Presentation"><wrong/></annotation>']:
 add(['read',cp('<math><semantics>'+body+'</semantics></math>')],'semantic-annotation-effects')
xmls=['','text','<','<!','<!--','<![CDATA[x','<!DOCTYPE x [<!ENTITY x "y">]>','<?xml version="1.0"?><math/>','<?xml?><math/>','<?xml-stylesheet a="x"?><math/>', '<MATH><MI>X</MI></MATH>', '<math><mo>&epsilon;</mo></math>', '<math/><wrong/>', '<wrong/><math/>', 'text<math/>tail', '<math><mi x=y>x','<a><b></a>tail', '<a>x</u>y</a>','<a xmlns="x" xmlns="y"><b p:x="1" xmlns:p="u"/></a>', '<p:a xmlns:p="u"><p:b xmlns:p="v"></p:a>', '<math><mi>&#xD800;&#0;&#18446744073709551681;</mi></math>']
for text in xmls:
 add(['xml',cp(text)],'xml-permissive-original-branches');add(['read',cp(text)],'reader-xml-permissive-branches')
 add(['xml-scanner',cp(text)],'polymorphic-custom-scanner-source')
add(['scanner-first'],'scanner-deferred-tail')
for key,value in itertools.product(['xmlns','xmlns:p','xml:lang','p:a','a'],['','u','&unknown;','&#xD800;','a&b;','"<>']):
 for quote in ['"',"'",'']:
  text='<a '+key+'='+quote+value+quote+'><p:b/>x</q:a></a>'
  add(['xml',cp(text)],'xml-attributes-and-namespaces')
for text,ending in itertools.product(xmls,['\n','\r','\r\n','\n\r']):add(['xml',cp(ending+text.replace('>', '>'+ending))],'xml-line-numbers')
for n in [-1,0,9,13,32,65,0xd800,0x10ffff,0x110000,2**63-1,2**63,2**64+65,-2**64+65]:
 for spelling in [str(n),' '+str(n)+' ','('+str(n)+')','+'+str(n),'x'+format(n,'x'),'0x'+format(n,'x')]:add(['lex-utils',cp(spelling)],'numeric-reference-boundaries')
for text in ['&lt;&gt;&amp;&apos;&quot;','a&b;c&d;','&missing','&&;','&#xD800;','&#0;','&epsilon;']+[str(x) for x in range(128)]:add(['lex-utils',cp(text)],'lexical-decoding')
for value,space in itertools.product(['65','-1','0x41','0o101','0b1000001','(65)','( 65 )','((65))','65--x','65{-x-}','x41','x 41','x41 '],['',' ','\t','\r','\n','\u00a0','\u2003','\u202f','\u200b']):
 for spelling in [space+value,value+space,space+value+space]:add(['lex-utils',cp(spelling)],'numeric-read-remainders')
for first,text in itertools.product(['1','-2',str(2**100)],['a:b c="d"/>rest','a x=y &foo;','/a>tail','?xml version="1.0"?>x','a\r\nb\nc','x="&lt;&unknown;"/>','a/>']):add(['lexer',first,cp(text)],'all-lexer-helpers')
for byte in range(256):add(['xml-bytes',[60,97,62,byte,60,47,97,62]],'all-octet-source-values')
add(['xml',[60,97,62,0xd800,0xdc00,60,47,97,62]],'raw-surrogate-char-source')
add(['xml-text',[60,97,62,0xd800,0xdc00,60,47,97,62]],'text-surrogate-replacement-source')
rng=random.Random(0x4d4d4c);seeds=xmls+['<math><mfrac><mi>x</mi><mn>2</mn></mfrac></math>'];alphabet='abc012<>/!?=&;"\' :\n\r\tα漢🙂'
for i in range(2500):
 text=rng.choice(seeds);at=rng.randrange(len(text)+1);text=text[:at]+''.join(rng.choices(alphabet,k=rng.randrange(6)))+text[at+rng.randrange(min(5,len(text)-at)+1):]
 add(['xml',cp(text)],'generated-legacy-xml-boundaries');add(['read',cp(text)],'generated-mathml-boundaries')
for opening,closing,body in [('<mrow>','</mrow>','<mi>x</mi>'),('<msqrt>','</msqrt>','<mi>x</mi>'),('<mstyle mathvariant="bold">','</mstyle>','<mi>𝐱</mi>'),('<semantics>','</semantics>','<mi>x</mi>')]:
 add(['read-show',cp('<math>'+opening*1500+body+closing*1500+'</math>')],'finite-deep-semantic-reader')
add(['read-show',cp('<math>'+'<mo>(</mo>'*1500+'<mi>x</mi>'+'<mo>)</mo>'*1500+'</math>')],'finite-deep-delimiter-reader')
for first in range(0,0x110000,65536):add(['read-range',first,min(65536,0x110000-first)],'all-unicode-reader-witnesses')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalInputs=len(originals()),originalReaderGoldens=576,entities=len(keys),oracle='Complete unchanged texmath/XML.Light libraries, original full Tasty registration, exact hidden EntityMap; observation transport only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'MathML/XML observations; SHA',m['caseHash'],flush=True)
