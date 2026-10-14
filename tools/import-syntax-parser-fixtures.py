#!/usr/bin/env python3
"""Capture original Skylighting parser/loader and Text.XML document behavior."""
import argparse,collections,gzip,hashlib,html,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/highlighting/syntax-parser.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_SYNTAX_PARSER_REFERENCE'));args=p.parse_args()
sources=['tools/import-syntax-parser-fixtures.py','licenses/Skylighting-core-BSD-3-Clause.txt','licenses/xml-conduit-MIT.txt','licenses/Xml-types-MIT.txt','licenses/Attoparsec-BSD-3-Clause.txt','licenses/Conduit-extra-MIT.txt','licenses/text-BSD-2-Clause.txt','licenses/ByteString-BSD-3-Clause.txt','licenses/Filepath-BSD-3-Clause.txt']
for folder in ['vendor/skylighting-core-0.15','vendor/xml-document-conduit-1.10.1.0','vendor/xml-document-types-0.3.8','vendor/xml-parser-attoparsec-0.14.4','vendor/xml-parser-conduit-extra-1.3.8','vendor/xml-parser-text-2.1.3','vendor/xml-parser-bytestring-0.12.2.0','vendor/filepath-1.5.4.0','tools/haskell-syntax-parser-reference']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources}
xml=sorted(str(p.relative_to(root)) for p in (root/'vendor/skylighting-core-0.15/xml').glob('*.xml'))
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cases=[json.loads(line) for line in f]
 assert m['sources']==hashes and m['cases']==len(cases) and m['caseHash']==digest(cases)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cases))
 for op in ['syntax','document','bytes']:assert [c['xml'] for c in cases if c['op']==op and 'xml' in c]==xml
 assert len([c for c in cases if c.get('originalLoop')])==1
 print(f'Verified {len(cases)} XML/syntax parser/loader observations, all {len(xml)} original XML files, {len(hashes)} source files');raise SystemExit
assert args.oracle,'--oracle required'
def native(requests):
 r=subprocess.run([args.oracle],input='\n'.join(json.dumps(req) for req in requests)+'\n',text=True,capture_output=True,check=True,cwd=root)
 rows=[json.loads(line) for line in r.stdout.rstrip('\n').split('\n')]
 assert len(rows)==len(requests),(len(rows),len(requests),r.stderr)
 return rows
cases=[]
def add(op,input,**extra):cases.append(dict(op=op,input=input,**extra))
for path in xml:
 raw=(root/path).read_bytes();text=raw.decode('utf8')
 add('syntax',[path,text],xml=path);add('document',text,xml=path);add('bytes',raw.hex(),xml=path)
# Exact literal inputs in the retained dependency test tree; these are not
# claimed to port its rendering/cursor/stream-combinator assertions.
source=(root/'vendor/xml-document-conduit-1.10.1.0/test/unit.hs').read_text()
literals=list(re.finditer(r'"(?:[^"\\]|\\.)*"',source,re.S))
for start in range(0,len(literals),64):
 batch=literals[start:start+64]
 for match,value in zip(batch,native([['haskell-string',m.group()] for m in batch])):
  if isinstance(value,str) and '<' in value:add('document',value,unitLiteralOffset=match.start())
loop=(root/'vendor/skylighting-core-0.15/test/test-skylighting.hs').read_text().split('loopSyntax =',1)[1].split('\n\ncompareValues',1)[0]
loop_literals=re.findall(r'"(?:[^"\\]|\\.)*"',loop)
decoded=native([['haskell-string',literal] for literal in loop_literals])
assert decoded[0]=='loop.xml' and all(isinstance(x,str) for x in decoded)
add('syntax',[decoded[0],''.join(decoded[1:])],originalLoop=True)
rng=random.Random(0x584d4c53594e)
def attrs(values):return ''.join(' '+name+'="'+html.escape(str(value),quote=True)+'"' for name,value in values.items())
def el(name,values={},children=None):return '<'+name+attrs(values)+('/>' if children is None else '>'+children+'</'+name+'>')
def language(rules='',values={},context={},lists='',itemdata='',general=''):
 return el('language',dict(name='Sample',**values),el('highlighting',{},lists+el('contexts',{},el('context',dict(name='start',**context),rules))+el('itemDatas',{},itemdata))+general)
def syntax(text,path='input.xml',**extra):add('syntax',[path,text],**extra)
syntax('<language/>');syntax('<language><highlighting/></language>');syntax('<other/>')
for name in ['DetectChar','Detect2Chars','AnyChar','RangeDetect','StringDetect','WordDetect','RegExpr','keyword','Int','Float','HlCOct','HlCHex','HlCStringChar','HlCChar','LineContinue','IncludeRules','DetectSpaces','DetectIdentifier','unknown','Keyword']:
 for values in [{},{'char':'a','char1':'b','String':'^abc','context':'other'},{'char':'\\n','char1':'\\x1F600','String':'words','context':'one!two##Other','column':'42x','attribute':'known','dynamic':'yes','minimal':'1','lookAhead':'true','firstNonSpace':'1','includeAttrib':'true'}]:
  syntax(language(el(name,values),itemdata=el('itemData',{'name':'known','defStyleNum':'dsKeyword'})))
bools=['','true','yes','1','false','no','0','TRUE','True','unknown',' true ','2']
for flag,value in itertools.product(['insensitive','includeAttrib','lookAhead','firstNonSpace','dynamic','minimal'],bools):
 for rule in ['RegExpr','keyword','StringDetect']:syntax(language(el(rule,{flag:value,'String':'^abc'}),values={'casesensitive':'false'}))
for language_case,general_case,rule_case in itertools.product(['','true','false','unknown'],['','true','false','unknown'],[None,'','true','false','unknown']):
 values={'String':'words','additionalDeliminator':'😀.','weakDeliminator':'.~'}
 if rule_case is not None:values['insensitive']=rule_case
 syntax(language(el('keyword',values),values={'casesensitive':language_case},general=el('general',{},el('keywords',{'casesensitive':general_case,'additionalDeliminator':'~.','weakDeliminator':' ()'}))))
styles=['ds'+name.removesuffix('Tok') for name in 'KeywordTok DataTypeTok DecValTok BaseNTok FloatTok ConstantTok CharTok SpecialCharTok StringTok VerbatimStringTok SpecialStringTok ImportTok CommentTok DocumentationTok AnnotationTok CommentVarTok OtherTok FunctionTok VariableTok ControlFlowTok OperatorTok BuiltInTok ExtensionTok PreprocessorTok AttributeTok RegionMarkerTok InformationTok WarningTok AlertTok ErrorTok NormalTok'.split()]+['dsOthers','','unknown','DSKeyword']
for style in styles:syntax(language(el('DetectChar',{'char':'a','attribute':'known'}),itemdata=el('itemData',{'name':'known','defStyleNum':style})))
for column in ['', '0','1','001','12tail','-1','+1',' 1','١','9223372036854775807','9223372036854775808','18446744073709551616','9'*1000]:
 for pattern in ['abc','^abc','^^abc']:syntax(language(el('RegExpr',{'String':pattern,'column':column})))
char_values=['','a','\\','\\\\','\\n','\\t','\\x1F600','\\O377','\\256','\\55296','\\1114111','\\1114112','\\&','\\SOH','\\SP','\\^_','ab',"'",'"','😀','\\'+chr(0x10061)]
for tag,char in itertools.product(['DetectChar','Detect2Chars','RangeDetect','LineContinue'],char_values):syntax(language(el(tag,{'char':char,'char1':char})))
for value in ['', '#stay','#stay ',' #stay','#pop','#pop#stay','#pop#pop','A!B!C','!A!!B!','##Other','context##','ctx ## Other ','#pop!A','#!',' ','##','A##B##C','\u2000A\u2000']:
 for flag in ['context','lineEndContext','lineEmptyContext','fallthroughContext']:
  syntax(language(el('DetectChar',{'char':'x',**({flag:value} if flag=='context' else {})}),context={flag:value} if flag!='context' else {}))
 syntax(language(el('IncludeRules',{'context':value})))
for parent,child in itertools.product(['','known','missing'],repeat=2):
 syntax(language(el('Int',{'attribute':parent},el('StringDetect',{'attribute':child,'String':'x'})),context={'attribute':'known'},itemdata=el('itemData',{'name':'known','defStyleNum':'dsKeyword'})+el('itemData',{'name':'','defStyleNum':'dsString'})))
for parent,child in itertools.product(['unknown','Int','IncludeRules'],['unknown','Int','IncludeRules']):syntax(language(el(parent,{},el(child))))
for lists in [el('list',{},el('item',{},'x')),el('list',{'name':''},el('item',{},' x ')),el('list',{'name':'a'},el('unknown')),el('list',{'name':'a'},el('item',{},'one'+el('b',{},'not text')+'two')),el('list',{'name':'a'},'ignored'+el('include',{},' list ## Syntax ')),el('list',{'name':'a'},el('item',{},'one'))+el('list',{'name':'a'},el('item',{},'two'))]:syntax(language(lists=lists))
for prefix in ['x:', '']:
 for ns in ['', ' xmlns:x="u"', ' xmlns="u"', ' xmlns:x=""']:
  syntax('<'+prefix+'language'+ns+'><highlighting><contexts><context name="start"><p:Int xmlns:p="u"/></context></contexts></highlighting></'+prefix+'language>')
for node in ['highlighting','contexts','context','list','itemDatas','itemData','general','keywords']:
 base=language(el('keyword',{'String':'words'}),lists=el('list',{'name':'words'},el('item',{},'a')),itemdata=el('itemData',{'name':'','defStyleNum':'dsKeyword'}),general=el('general',{},el('keywords',{'casesensitive':'false'})))
 syntax(base.replace('<'+node,'<x:'+node).replace('</'+node,'</x:'+node))
 syntax(base.replace('<'+node,'<x:'+node+' xmlns:x="u"').replace('</'+node,'</x:'+node))
for body in ['<language name="A" name="B"><highlighting><contexts><context name="one"/><context name="one"><Int/></context></contexts></highlighting></language>', '<language><highlighting><contexts><context name="first"/></contexts></highlighting><highlighting><contexts><context name="second"/></contexts></highlighting></language>']:
 syntax(body)
paths=['','/','a/','a.xml','.xml','file.tar.xml','foo--bar.xml','foo_-bar.xml','../foo-bar.xml','𐀨-ß.xml','😀a.xml','c:foo.xml','C:\\foo\\bar.xml','\\\\server\\share\\foo.xml','//server/share/name.xml','a.','a..xml']
for path in paths:
 add('syntax',[path,language()]);add('syntax-windows',[path,language()])
# Documents: valid/malformed lexical branches, structural precedence, entities and namespaces.
base_docs=['',' ','\ufeff<a/>','\ufeef<a/>','\ufeef\ufeef<a/>','<','<>','< >','<a','<a>','</a>','<a></b>','<a><b></a>','<a/><b/>','<a/>text','text<a/>','&x;','<a>&x;&z;<b q="&y;"/></a>','<a x="&x;" x="ok"/>','<a>raw\x00text</a>','<😀>\n</x>','<a:foo></b:foo>','<a xmlns:x="u" xmlns:y="u" x:v="1" y:v="2"/>','<a a:v="1" v="2"/>']
base_docs += ['<?foo?>','<?foo x?><a/><?bar y?>','<?xml version="1.0"?>\r\n<a/>','<a/><?xml version="1.0"?>','<?xml wrong?><a/>','<!--x--><!DOCTYPE a SYSTEM "s"><!--y--><a/><!--z-->','<!DOCTYPE a PUBLIC "p" "s"><a/>','<a><!DOCTYPE a></a>','<!DOCTYPE a><!DOCTYPE b><a/>','<![CDATA[x]]><a/>','<a/><![CDATA[ ]]>', '<a><![CDATA[]]></a>','<a>one<![CDATA[two]]><!--x-->three<?x y?>four</a>']
for text in base_docs:add('document',text)
seeds=['<a b="x">text&amp;<b/>more</a>','<?xml version="1.0"?><!DOCTYPE a [<!ENTITY x "text">]><a>&x;</a>','<!--x--><a><![CDATA[x<y]]></a>','<!DOCTYPE a PUBLIC "p" "s"><a x:y="v"/>']
for seed in seeds:
 for n in range(len(seed)+1):add('document',seed[:n],prefix=True)
 for _ in range(350):
  point=rng.randrange(len(seed)+1);mutated=seed[:point]+rng.choice(['','<','>','&','"',"'",'/',']','\n','😀'])+seed[min(point+rng.randrange(3),len(seed)):]
  add('document',mutated,mutated=True)
for _ in range(1200):add('document',''.join(rng.choice('ab01 <>/!?=&;#[]\"\'\r\n:-') for _ in range(rng.randrange(60))),random=True)
for reference in ['&amp;','&lt;','&quot;','&apos;','&unknown;','&foo:bar;','&x','&#0;','&#9;','&#13;','&#xD800;','&#65535;','&#1114111;','&#1114112;','&#18446744073709551625;','&#x10000000000000009;','&#X41;','&#-1;','&#x;']:
 for text in [reference,'<a>'+reference+'</a>','<a x="'+reference+'"/>']:add('document',text)
for declaration in ['', '<!ENTITY a "x">','<!ENTITY a "&a;">','<!ENTITY a "&b;"><!ENTITY b "&a;">','<!ENTITY a "one"><!ENTITY a "two">','<!ENTITY % a "x">','<!ENTITY a SYSTEM "foo">','<!-- [x] --> <!ENTITY a "x">','<!ELEMENT a (#PCDATA)><!ATTLIST a x (a|b) #IMPLIED><!ENTITY a "x">','<?x "[a]"?><!ENTITY a "x">','<!ENTITY a "<p>x&amp;</p>">','<!ENTITY a "<p xmlns=\'u\'>x</p>">','<!ENTITY a "<p>\"><!ENTITY b \"</p>">','<!ENTITY a "x"','<!ENTITY a "x"><BAD','<!ENTITY a "x">junk','<!ENTITY a "&unknown;">']:
 for text in ['<!DOCTYPE r ['+declaration+']><r>&a;</r>','<!DOCTYPE r ['+declaration+']><r a="&a;"/>']:add('document',text)
for char,counts in [('x',[8191,8192,8193]),('é',[4095,4096,4097]),('&amp;',[1638,1639])]:
 for n in counts:add('document','<!DOCTYPE a [<!ENTITY e "'+char*n+'">]><a>&e;</a>',entityLimit=True)
for n in [8179,8180,8181]:add('document','<!DOCTYPE a [<!ENTITY e "<![CDATA['+'x'*n+']]>">]><a>&e;</a>',entityLimit=True)
for n in [0,1,10,32751,32752,32753,65535,65536,100000]:
 for suffix in ['<','&bad','</b>']:
  add('document','<a>'+'x'*n+suffix,long=True)
# Lazy Text chunk backtracking retains conduit-extra's reported position quirks.
add('document','\ufeef')
for n in [16363,16364,16365,16366,32729,32730,32731,40000,70000]:
 for text in ['<a x="'+'a'*n,'<a x="'+'a'*n+'" x=bad/>','<!DOCTYPE a [<!ENTITY e "'+'a'*n]:add('document',text,chunkBacktrack=True)
for n in [4090,4091,4092,8182,10000]:add('document','<a x="'+'😀'*n,chunkBacktrack=True)
chunk_seeds=['<a x="long text','<a x="&bad','<!DOCTYPE a [<!ENTITY e "bad','<a><b/></c>','<a>&entity;</a>','<?instruction bad','\ufeef','\ufeef<a/>','<a xmlns:x="u" x:y="v"/>']
for text in chunk_seeds:
 for at in range(len(text)+1):
  add('document-chunks',[text[:at],text[at:]],chunkSplit=at)
  add('document-chunks',['',text[:at],'',text[at:],''],chunkEmpty=True)
for parts in [['<','a x="','long',' text'],['<!','DOCTYPE a [','<!ENTITY e "','bad'],['<a>','&bad'],['<a/><','b/>'],['<a>','😀','</b>']]:add('document-chunks',parts)
for text in [language(el('IncludeRules')),language(el('unknown',{},el('IncludeRules'))),language(el('keyword',{'String':'words'}))]:
 for at in range(0,len(text)+1,7):add('syntax-chunks',['input.xml',[text[:at],text[at:]]])
for _ in range(500):
 declarations=''.join(rng.choice(['<!ENTITY e "x">','<!ENTITY e "x"','<!ELEMENT a ANY>','<!-- x -->','<!-- x','<?x?>','<!','junk',']','>','"','\n']) for _ in range(rng.randrange(8)))
 add('document','<!DOCTYPE a ['+declarations+']><a>&e;</a>',doctypeFuzz=True)

# File preprocessing and strict UTF8 exceptions, distinct from XML parser Lefts.
base=language(el('DetectChar',{'char':'\r'})).encode()
raws=[b'',b'\xef\xbb\xbf'+base,b'\xef\xbb\xbf\xef\xbb\xbf'+base,b'\r'+base,base.replace(b'<',b'\r<'),b'<a/>\xff',b'<a/><\xff',b'\xc2\r\xa0',bytes(range(256))]
raws += [bytes([n]) for n in range(256)]
raws += [bytes(xs) for xs in itertools.product([0xc0,0xc2,0xe0,0xed,0xf0,0xf4],[0x7f,0x80,0x9f,0xa0,0xbf,0xc0],[0x7f,0x80,0xbf,0xc0])]
raws += [rng.randbytes(rng.randrange(12)) for _ in range(200)]
for raw in raws:
 add('bytes',raw.hex())
for raw in raws[:20]:add('load',raw.hex())
for n in [32751,32752,65535,100000]:
 add('bytes',(b'<a/>'+b'x'*n+b'\xff').hex(),lazyUtf8=True)
# Strict chunk boundaries, incomplete tail forcing and CR/BOM filtering.
for size in [0,1,32740,32741,32742,32743,32744,32745,32746,32747,32748,32749,32750,32751,32752,32753,65520,65521,65522,65523,65524,65525]:
 for tail in [b'\xc2',b'\xe2\x80',b'\xf0\x90\x80',b'\xff',b'\xe2a',b'\xc2\xa0']:
  add('bytes',(b'<a/><b/>'+b'x'*size+tail).hex(),lazyUtf8=True)
for size in range(32745,32756):
 for middle in [b'\xc2\xa0',b'\xe2\x80\x80',b'\xf0\x90\x80\x80',b'\xf0\x90\x80a',b'\xe2\x80a',b'\xc2a']:
  add('bytes',(b'<a>'+b'x'*size+middle+b'</a>').hex(),utf8Chunk=True)
for prefix in [b'',b'\xef\xbb\xbf']:
 for size in [32744,32745,32746,32747,65520]:
  add('bytes',(prefix+b'<a/><b/>'+b'\r'*size+b'\xff').hex(),lazyUtf8=True)

# Filesystem order is observed rather than sorted. Kotlin tests replay the native
# file order for the fold and independently exercise the actual filesystem adapter.
for rows in [[],[['a.xml',language(values={'author':'one'})],['b.XML','bad'],['dir.xml',None],['ignore','bad']],
 [['a.xml',language()],['bad.xml','<a>'],['ignore.XML','bad']],
 [['a.xml',language(values={'author':'one'})],['b.xml',language(values={'author':'two'})]],
 [['a.xml','<language/>'],['b.xml','<bad/>']]]:
 add('directory',[[name,None if content is None else content.encode().hex()] for name,content in rows])
for start in range(0,len(cases),32):
 batch=cases[start:start+32]
 for c,out in zip(batch,native([[c['op'],c['input']] for c in batch])):c['expected']=out
 print(f'{min(start+32,len(cases))}/{len(cases)}',flush=True)
m=dict(upstream='skylighting-core-0.15/xml-conduit-1.10.1.0/xml-types-0.3.8',sources=hashes,cases=len(cases),caseHash=digest(cases),operations=dict(collections.Counter(c['op'] for c in cases)),xmlFiles=len(xml),unitLiterals=sum('unitLiteralOffset' in c for c in cases))
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',filename='',mtime=0) as f:f.write(('\n'.join(json.dumps(c,ensure_ascii=False,separators=(',',':')) for c in [m]+cases)+'\n').encode())
print(f'Captured {len(cases)} original parser observations: {m["operations"]}')
