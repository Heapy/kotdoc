#!/usr/bin/env python3
"""Typed actual original Read dictionaries; expectations never use Kotlin."""
import argparse,collections,gzip,hashlib,json,random,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DEST=ROOT/'core/src/test/resources/pandoc-types';PROJECT='tools/haskell-types-read-reference'
KINDS=['pandoc','meta','metaValue','inline','block','style','delim','format','rowHeadColumns','alignment','colWidth','row','head','body','foot','caption','cell','rowSpan','colSpan','quote','math','citation','mode','inlines','blocks','attr','target','listAttributes','colSpec','shortCaption']
def sha(raw):return hashlib.sha256(raw).hexdigest()
def compact(x):return json.dumps(x,ensure_ascii=True,separators=(',',':'))
def requests(examples):
 cases=[]
 def add(kind,s,p=0,op='readsPrec',name='',lean=False):
  r={'kind':kind,'operation':op,'precedence':p,'input':list(map(ord,s)) if isinstance(s,str) else s}
  if lean:r['lean']=True
  cases.append({'name':name,'request':r})
 rename={'tableHead':'head','tableBody':'body','tableFoot':'foot','citationMode':'mode'}
 for e in examples:e['kind']=rename.get(e['kind'],e['kind'])
 for i,e in enumerate(examples):
  k,s=e['kind'],e['input']
  for p in [-1,0,10,11]:
   for wrap in [s,'('+s+')','(('+s+'))']:
    for tail in ['', ' tail',' \t)', '++']:
     for op in ['readsPrec','readPrec']:add(k,wrap+tail,p,op,'original-example-'+str(i))
  for wrap in ['['+s+','+s+'] tail','([]) rest','(['+s+',])']:
   add(k,wrap,0,'readList','list-example-'+str(i))
  for tail in ['', ' tail',' \n']:add(k,s+tail,0,'read','whole-example-'+str(i))
 first={e['kind']:e['input'] for e in reversed(examples)}
 assert set(KINDS)<=set(first)
 for i,e in enumerate(examples):
  s=e['input']
  add(e['kind'],s.replace('(','').replace(')','')+' tail',name='all-field-parentheses-removed-'+str(i))
  for at,c in enumerate(s):
   if c in '()[]{},=':add(e['kind'],s[:at]+s[at+1:]+' tail',name='canonical-delimiter-deletion-'+str(i)+'-'+str(at))
 for k,s in first.items():
  for p in [-2**63,2**63-1]:add(k,s,p,name='precedence-Int-boundary');add(k,'('+s+')',p,name='precedence-Int-boundary-parenthesized')
  for prefix in ['\t\n\r\v\f ',chr(0x85),chr(0xa0),chr(0x1680),chr(0x2000),chr(0x2028),chr(0x2029),chr(0x202f),chr(0x205f),chr(0x3000),'-- comment\n','{- comment -}','{- outer {- inner -} -}']:
   add(k,prefix+s+' \u2003rest',name='leading-whitespace/comment')
  for invalid in ['',s[:-1],s+' )',s+' extra argument','('+s,s+')','UnknownConstructor','()','[', '[]','True','Space','"x"']:
   add(k,invalid,name='arity/rejection/prefix-boundary')
  for other in first.values():add(k,other,name='typed-dictionary-separation')
 for k,s in [('bool','True'),('bool','False'),('bool','true'),('bool','TrueX'),('text','"x"')]:
  for p in [-1,0,10,11,2**63-1]:
   for op in ['readsPrec','readPrec','readList','read']:add(k,('['+s+'] rest' if op=='readList' else s+' tail'),p,op,'primitive-dictionary-methods')
 for k,s in first.items():
  for tail in ['\0',chr(0xd800)+chr(0xdc00),chr(0x1f600),'X','_',"'"]:
   add(k,s+tail,name='untouched-Char-boundaries')
 # String and Char dependency lexer, through both Text and Str constructors.
 strings=['"'+''.join('\\'+str(cp) for cp in range(128))+'"','"raw\nnewline"',"['a','\\xD800','\\x1f600']",'"\\&\\&"','"a\\\n \t\\b"', '"\\O101\\X1f600"','"\\^@\\^_\\^?"', '"\\SOH\\SO\\SP\\DEL"','"\\1114111"','"\\1114112"','"\\x"','"\\o8"','"\\b101"','"unterminated','"\\q"','"\\^a"',"['a',]",'("x")']
 strings+=['"'+chr(0xd800)+chr(0xdc00)+'"']
 for cp in list(range(32))+[127,128,0x19b,0x1c8a,0xd800,0xdc00,0x10000,0x10ffff]:strings+=['"'+chr(cp)+'"']
 names=['NUL','SOH','STX','ETX','EOT','ENQ','ACK','BEL','BS','HT','LF','VT','FF','CR','SO','SI','DLE','DC1','DC2','DC3','DC4','NAK','SYN','ETB','CAN','EM','SUB','ESC','FS','GS','RS','US','SP','DEL']
 strings+=['"\\'+n+'"' for n in names]+['"\\^'+chr(cp)+'"' for cp in range(64,96)]
 for s in strings:
  for k,value in [('text',s),('inline','Str '+s),('format','Format '+s)]:
   for p in [0,11]:add(k,value+' rest',p,name='Read-String/Text-lexer')
 for c in ["'a'","'\\''","'\"'","'\\xD800'","'\\x1f600'","'\\1114112'","''","'ab'","'\\&'","'\\X41'","'\n'"]:
  for op in ['readsPrec','readPrec','readList']:add('char',c if op!='readList' else '['+c+'] rest',0,op,'Read-Char-lexer')
 for s in ['"abc"','("a\\xD800")','(("x"))','""','"\\x1f600"']:
  add('char',s+' rest',0,'readList','Char-special-readList-quoted-string')
 for k,values in [('int',['0','-3','0xFF','9223372036854775808']),('double',['-0.0','NaN','Infinity','1e23','5e-324']),('char',["'a'","'\\xD800'"]),('text',['"x"',"['a']"]),('bool',['True','False'])]:
  for s in values:
   for op in ['readsPrec','readPrec','readList','read']:
    add(k,'['+s+'] rest' if op=='readList' else s,11,op,'dependency-method-completeness')
 # Exact numeric tokens, radix fallback, Int64 wrap, IEEE rounding and exponents.
 numbers=['0','-0','- 3','+3','--3','(-3)','0x10','0XfF','0o77','0O77','0b101','0B101','0x','0o8','0b102','1.0','1e0','1e+','1e-','1.','1..2','1.2.3','NaN','-NaN','Infinity','- Infinity','-Infinity','+Infinity','9223372036854775808','-9223372036854775809',str(2**200),str(-2**200),'1e99999','0e99999','1e-99999','-0.0','4.9406564584124654e-324','2.2250738585072014e-308','1e23','999999999999999999999999999999999.1']
 rng=random.Random(611)
 for _ in range(256):numbers.append(str(rng.randrange(-10**40,10**40))+'e'+str(rng.randrange(-400,401)))
 for n in numbers:
  for k,s in [('int',n),('double',n),('rowSpan','RowSpan '+n),('colWidth','ColWidth '+n)]:
   add(k,s+' tail',11 if k in ['int','double'] else 0,name='numeric-token/IEEE/wrap')
 # Derived record grammar: order, duplicates, missing/extra fields, positional form.
 records=[('meta','Meta {unMeta = fromList []}'),('citation','Citation {citationId="x",citationPrefix=[],citationSuffix=[],citationMode=NormalCitation,citationNoteNum=0,citationHash=0}'),('inlines','Many {unMany=fromList [Space]}'),('blocks','Many {unMany=fromList [HorizontalRule]}')]
 for k,s in records:
  variants=[s,s.replace('=',' = '),s.replace('{','(').replace('}',')'),s.replace('}',',}'),s.replace('}',',extra=0}'),s.replace('unMeta','M.unMeta'),s.replace('citationId="x",citationPrefix=[]','citationPrefix=[],citationId="x"'),s.replace('citationHash=0','citationHash=0,citationHash=1'),s.replace('citationPrefix=[],',''),s.replace('fromList []','fromList [("b",MetaBool True),("a",MetaBool False),("b",MetaString "last")]')]
  for v in variants:add(k,v+' rest',name='derived-record-fields')
 # Deep results retain complete original Show and exact remainder, not nested JSON.
 for depth in [32,256,2048,8192]:
  for k,pre,suffix,base in [('inline','Emph [',']','Space'),('block','BlockQuote [',']','HorizontalRule'),('metaValue','MetaList [',']','MetaBool True'),('inline','Note [Plain [',']]','Space')]:
   add(k,pre*depth+base+suffix*depth+' remainder',name='deep-'+str(depth),lean=True)
  add('inline','('*depth+'Space'+')'*depth+' tail',name='deep-parentheses-'+str(depth),lean=True)
 # The fifth Read method is observed independently, with element reset at all enclosing precedences.
 for e in examples:
  for p in [-2**63,-1,0,10,11,2**63-1]:
   for value in ['['+e['input']+'] rest','(['+e['input']+','+e['input']+']) \u2003tail','([]) rest','['+e['input']+',] rest']:
    add(e['kind'],value,p,'readListPrec','readListPrec-constructor-reset')
 for kind,value in [('char',"'a'"),('text','"x"'),('bool','True'),('int','-3'),('double','-0.0')]:
  for p in [-2**63,-1,0,10,11,2**63-1]:
   for value in ['['+value+'] rest','([]) rest','['+value+',] rest']:
    add(kind,value,p,'readListPrec','readListPrec-dependency-reset')
 for p in [-2**63,-1,0,10,11,2**63-1]:
  for value in ['"ab" rest','("\\xD800\\x1f600") rest',"['a','b'] rest",'"unterminated']:
   add('char',value,p,'readListPrec','readListPrec-Char-override')
 for p in [-2**63,-1,0,10,11,2**63-1]:
  for depth in [0,1,2,3,8]:
   for body in ['[]',"['a']",'"ab"']:
    value='('*depth+body+')'*depth+' rest'
    for op in ['readList','readListPrec']:
     add('char',value,p,op,'Char-list-duplicate-parens')
    for op in ['readsPrec','readPrec','read']:
     add('text',value.removesuffix(' rest') if op=='read' else value,p,op,'Text-Char-list-duplicate-parens')
 for depth in [1,2,3,8]:
  text='('*depth+"['a']"+')'*depth
  for kind,value in [('inline','Str '+text),('target','('+text+','+text+')'),('attr','('+text+',[ '+text+' ],[])')]:
   for op in ['readsPrec','readPrec','readListPrec','read']:
    add(kind,('['+value+'] rest' if op=='readListPrec' else value+('' if op=='read' else ' rest')),0,op,'dependent-Char-list-parse-products')
 # Whole read demands only enough alternatives to detect ambiguity, never a huge ReadS list.
 for n in [2,16,30,31,32,64,256,1024]:
  value='MetaList ['+','.join("MetaString (['a'])" for _ in range(n))+']'
  for tail in (['', ' \u2003', ' trailing'] if n<=16 else ['', ' \u2003']):
   add('metaValue',value+tail,0,'read','whole-read-bounded-ambiguity-'+str(n))
 return cases

SOURCES=['tools/import-types-read-fixtures.py',*[PROJECT+'/'+n for n in ['TypesReadReference.hs','DerivedMetaRead-boundary.iface.txt','SequenceRead-boundary.iface.txt','kotdoc-types-read-reference.cabal','cabal.project','cabal.project.freeze','README.md']],
 *['upstream/pandoc-types/src/Text/Pandoc/'+n+'.hs' for n in ['Definition','Builder']], 'upstream/pandoc-types/test/test-pandoc-types.hs','upstream/pandoc-types/test/Data/String/QQ.hs','upstream/pandoc-types/pandoc-types.cabal','tools/haskell-ast-show-reference/OriginalExamples.hs',
 'vendor/ghc-9.12.2/GHC/Internal/Read.hs','vendor/ghc-9.12.2/GHC/Internal/Text/Read/Lex.hs','vendor/text-2.1.2/src/Data/Text.hs','vendor/text-2.1.2/src/Data/Text/Internal.hs','vendor/containers-0.7/src/Data/Map/Internal.hs','vendor/containers-0.7/LICENSE',
 'licenses/pandoc-types-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','core/src/main/kotlin/io/heapy/kotdoc/ast/PandocRead.kt','core/src/test/kotlin/io/heapy/kotdoc/ast/PandocReadTest.kt', *['core/src/main/kotlin/io/heapy/kotdoc/ast/'+n+'.kt' for n in ['Pandoc','Builder','Format','AstShow','PandocJson']], 'core/src/main/kotlin/io/heapy/kotdoc/formats/NativeWriter.kt',
 *['core/src/main/kotlin/io/heapy/kotdoc/parsing/'+n+'.kt' for n in ['RegexCharacters','ParserCharacters']], *['core/src/main/resources/io/heapy/kotdoc/'+n+'.json' for n in ['regex-char-data','parser-char-flags']]]
API=['Pandoc','Meta','MetaValue','Inline','Block','ListNumberStyle','ListNumberDelim','Format','RowHeadColumns','Alignment','ColWidth','Row','TableHead','TableBody','TableFoot','Caption','Cell','RowSpan','ColSpan','QuoteType','MathType','Citation','CitationMode','Inlines','Blocks','Attr','Target','ListAttributes','ColSpec','ShortCaption']
def hashes():return {f:sha((ROOT/f).read_bytes()) for f in SOURCES}
def original_examples():
 s=(ROOT/'upstream/pandoc-types/test/test-pandoc-types.hs').read_text();part='t_meta ::'+s.split('t_meta ::',1)[1].split('-- headers and rows are padded',1)[0]
 actual=(ROOT/'tools/haskell-ast-show-reference/OriginalExamples.hs').read_text()
 assert actual.split('t_meta ::',1)[1].split('\nentry ::',1)[0]==part.split('t_meta ::',1)[1]
def evidence(rows,census,examples):
 assert len(census)==25 and sum(len(c['constructors']) for c in census)==79
 assert {c['kind'] for c in census}==set(KINDS[:25])
 expected=requests([dict(e) for e in examples]);assert [(r['name'],r['request']) for r in rows]==[(r['name'],r['request']) for r in expected]
 assert len(rows)==16954
 mapping={}
 for kind,api in zip(KINDS,API):
  members=next((c['constructors'] for c in census if c['kind']==kind),[])
  methods={op:[i for i,r in enumerate(rows) if r['request']['kind']==kind and r['request']['operation']==op] for op in ['readsPrec','readPrec','readList','readListPrec','read']}
  assert all(methods.values())
  canonical={}
  for ctor in members:
   for i,r in enumerate(rows):
    req=r['request'];text=''.join(map(chr,req['input']))
    if req['kind']==kind and req['operation']=='readsPrec' and req['precedence']==0 and r['name'].startswith('original-example-') and (text==ctor['name'] or text.startswith(ctor['name']+' ') or text.startswith(ctor['name']+'{')):
     result=json.loads(r['expectedWire'])
     if len(result)==1 and not result[0]['remainder']:canonical[ctor['name']]=i;break
   assert ctor['name'] in canonical,(kind,ctor)
  mapping[kind]={'api':'PandocRead.'+api,'constructors':members,'constructorCases':canonical,'methods':methods}
 counts=collections.Counter();errors=0
 for r in rows:
  exp=json.loads(r['expectedWire'])
  if isinstance(exp,list):counts[len(exp)]+=1
  else:errors+=int('exception' in exp)
 assert set(counts)>={0,1} # Keep duplicate Char-list parses in source order.
 assert sum(len(m['constructorCases']) for m in mapping.values())==79
 return mapping,dict(counts),errors
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--draft',action='store_true');p.add_argument('--check-source',action='store_true');a=p.parse_args();original_examples()
 manifest=DEST/'read-manifest.json'
 if a.check_source:
  m=json.loads(manifest.read_text());assert m['sources']==hashes()
  payloads={n:(gzip.decompress((DEST/(n+'.jsonl.gz')).read_bytes()) if n=='read' else (DEST/(n+'.json'+('l' if n=='read-examples' else ''))).read_bytes()) for n in ['read','read-census','read-examples']}
  assert m['payloads']=={n:sha(b) for n,b in payloads.items()}
  rows=[json.loads(x) for x in payloads['read'].splitlines()];census=json.loads(payloads['read-census']);examples=[json.loads(x) for x in payloads['read-examples'].splitlines()]
  mapping,counts,errors=evidence(rows,census,examples);assert m['dictionaryEvidence']==mapping and m['parseCounts']=={str(k):v for k,v in counts.items()} and m['wholeReadErrors']==errors
  native=('\n'.join(r['expectedWire'] for r in rows)+'\n').encode();assert m['nativeResponsesSha256']==sha(native)
  print('TypedRead16954 original cases;25 dictionaries/79constructors+5aliases; exact source/response hashes verified');return
 if not a.oracle:p.error('--oracle required')
 exraw=subprocess.check_output([a.oracle],input=b'"examples"\n');crraw=subprocess.check_output([a.oracle],input=b'"census"\n')
 examples=[json.loads(x) for x in exraw.splitlines()];cases=requests([dict(e) for e in examples]);proc=subprocess.Popen([a.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
 records=[];native=hashlib.sha256()
 for i,c in enumerate(cases):
  proc.stdin.write(compact(c['request'])+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,(i,c)
  native.update(line.encode());c['expectedWire']=line.removesuffix('\n');records.append(c)
 proc.stdin.close();assert proc.wait()==0
 raw=('\n'.join(compact(c) for c in records)+'\n').encode();mapping,counts,errors=evidence(records,json.loads(crraw),examples)
 if a.draft:Path('/tmp/kotdoc-types-read-draft.jsonl').write_bytes(raw)
 else:
  (DEST/'read.jsonl.gz').write_bytes(gzip.compress(raw,mtime=0));(DEST/'read-census.json').write_bytes(crraw);(DEST/'read-examples.jsonl').write_bytes(exraw)
  manifest.write_text(json.dumps({'sources':hashes(),'payloads':{'read':sha(raw),'read-census':sha(crraw),'read-examples':sha(exraw)},'nativeResponsesSha256':native.hexdigest(),'cases':len(records),'dictionaryEvidence':mapping,'parseCounts':counts,'wholeReadErrors':errors},indent=2)+'\n')
 print('Captured',len(cases),'original typedRead requests;',dict(collections.Counter(c['request']['kind'] for c in cases)))
if __name__=='__main__':main()
