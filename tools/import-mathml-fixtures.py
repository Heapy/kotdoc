#!/usr/bin/env python3
"""Execute complete original MathML writer/dictionary and XML.Light output APIs."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,struct,subprocess
from pathlib import Path
from fractions import Fraction
root=Path(__file__).resolve().parents[1];source=root/'vendor/texmath-0.13.3.1';folder=root/'tools/haskell-mathml-reference';target=root/'core/src/test/resources/mathml/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_MATHML_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
assert (folder/'MathWire.hs').read_bytes()==(root/'tools/haskell-math-reference/MathReference.hs').read_bytes().replace(b'module Main where',b'module MathWire where',1)
sources=['tools/import-mathml-fixtures.py','tools/import-mathml-dictionary.py','tools/haskell-math-reference/MathReference.hs','core/src/test/resources/math/data.jsonl.gz','licenses/Texmath-GPL-2.0.txt','licenses/XML-Light-BSD-3-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','vendor/ghc-9.12.2/GHC/Internal/Float.hs','vendor/ghc-math-9.12.2/GHC/Internal/Real.hs']
sources+=sorted(str(p.relative_to(root)) for base in [source,root/'vendor/xml-1.3.14',root/'vendor/ghc-mathml-9.12.2',folder] for p in base.rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
def witness(text):
 h=hashlib.sha256()
 for cp in map(ord,text):h.update(struct.pack('>I',cp))
 return dict(length=len(text),sha=h.hexdigest())
if args.check_source:
 subprocess.run(['python3',str(root/'tools/import-mathml-dictionary.py'),'--check'],check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 golden_files={str(p.relative_to(root)) for p in (source/'test/writer/mml').glob('*.test')}
 assert {c['source'] for c in cs if c['group']=='original-writer-golden'}==golden_files
 assert len(golden_files)==561
 for c in cs:
  if c['group']!='original-writer-golden':continue
  lines=(root/c['source']).read_bytes().split(b'\n');lines=lines[:-1] if lines[-1]==b'' else lines
  at=next(i for i,l in enumerate(lines) if l.startswith(b'>>>'))
  golden=(b'\n'.join(lines[at+1:])+b'\n' if at<len(lines)-1 else b'').decode()
  assert c['expected']['top']==witness(golden),c['source']
 assert {c['request'][1] for c in cs if c['request'][0]=='dictionary-range'}=={'FPrefix','FPostfix','FInfix'}
 print('Verified',len(cs),'original MathML/XML observations, all 561 writer goldens and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group,**extra):
 case=dict(request=req,expected=ask(req),group=group,**extra);cs.append(case)
 if len(cs)%1000==0:print('Captured',len(cs),'MathML/XML observations',flush=True)
 return case
# Every original MathML writer golden remains an independent byte expectation.
for path in sorted((source/'test/writer/mml').glob('*.test')):
 lines=path.read_bytes().split(b'\n');lines=lines[:-1] if lines[-1]==b'' else lines
 start=next(i for i,l in enumerate(lines) if l.startswith(b'<<<'));end=next(i for i,l in enumerate(lines[start+1:],start+1) if l.startswith(b'>>>'))
 fmt=lines[start][3:].decode().strip();text=(b'\n'.join(lines[start+1:end])+b'\n' if end>start+1 else b'').decode();outfmt=lines[end][3:].decode().strip()
 assert outfmt in ['mml','mathml'],path
 result=ask(['parse',fmt,text]);assert result[0]=='Right',(path,result)
 c=add(['mml','DisplayBlock',result[1]],'original-writer-golden',source=str(path.relative_to(root)))
 golden=(b'\n'.join(lines[end+1:])+b'\n' if end<len(lines)-1 else b'').decode()
 assert c['expected']['top']==witness(golden),(path,c['expected']['top'],witness(golden))
# Reuse earlier independently parsed trees and original constructor/fuzz inputs.
with gzip.open(root/'core/src/test/resources/math/data.jsonl.gz','rt') as f:next(f);earlier=[json.loads(l) for l in f]
for c in earlier:
 if c['request'][0]!='expressions':continue
 for display in ['DisplayBlock','DisplayInline']:add(['mml',display,c['request'][1]],'math-expression-inputs')
add(['dictionary-table'],'complete-operator-table')
forms=['FPrefix','FPostfix','FInfix']
for form in forms:add(['dictionary-range',form,0,0x110000],'all-characters-dictionary')
# Every listed multi-character/operator query in every requested form; fallback is original.
operators=[r['oper'] for r in cs[-4]['expected']]
for i in range(0,len(operators),100):add(['dictionary',[[s,f] for s,f in itertools.product(operators[i:i+100]+['','unknown',[0xd800]],forms)]],'operator-lookup')
types=list(dict.fromkeys(re.findall(r'\bText\w+\b',(source/'src/Text/TeXMath/Types.hs').read_text().split('data TextType =',1)[1].split('deriving',1)[0])))
a=['EIdentifier','x'];symbols='Ord Op Bin Rel Open Close Pun Accent Fence TOver TUnder Alpha BotAccent Rad'.split()
for tt,text in itertools.product(types,['','x','ΓΩ','\u03a2','Γx','π','ABCabc012',' \tx\t ','\n\rx\n','漢🙂']):
 for kind in ['ENumber','EIdentifier','EMathOperator']:
  add(['mml','DisplayBlock',[['EStyled',tt,[[kind,text]]]]],'style-variants')
for form,tt,text in itertools.product(symbols,[None]+types,['"','\u20d7','\u2192','^','~','+', 'unknown', '\u2061']):
 e=['EUnderover',False,a,['ESymbol',form,text],['ESymbol',form,text]]
 if tt is not None:e=['EStyled',tt,[e]]
 add(['mml','DisplayBlock',[e]],'accent-classification')
for xs in [[],[['EMathOperator','sin']],[['EMathOperator','sin'],a],[['EMathOperator','sin'],['ESymbol','Ord','\u2061']],[['EMathOperator','sin'],['ESymbol','Ord','\u2061'],a],[['EMathOperator','sin'],['EMathOperator','cos'],a]]:
 add(['mml','DisplayBlock',xs],'function-application')
 for tag in ['ESub','ESuper','ESubsup']:
  if len(xs)==(3 if tag=='ESubsup' else 2):add(['mml','DisplayBlock',[[tag]+xs]],'function-application')
for n in range(5):
 for als in itertools.product(['AlignLeft','AlignCenter','AlignRight'],repeat=n):
  add(['mml','DisplayBlock',[['EArray',list(als),[[[a],[],[a,a]],[],[[['EMathOperator','sin'],a]]]]]],'array-alignment')
# Exact Double rounding, format boundaries, underflow/overflow and Int wrapping.
ratios=[Fraction(n,d) for n,d in itertools.product(range(-3001,3002),[2000,3,18,1000])]
ratios += [Fraction(sign*((1<<n)+delta),1<<d) for sign,n,delta,d in itertools.product([-1,1],[0,1,52,53,54,1023,1024,1074,2048,4096],[-1,0,1],[0,1,52,53,1022,1074,1075,4096]) if (1<<n)+delta>0]
ratios += [Fraction(((1<<63)+delta)*sign,den) for sign,delta,den in itertools.product([-1,1],range(-3,4),[1,100,200])]
rng=random.Random(0x4d4d4c)
ratios += [Fraction(rng.getrandbits(rng.randrange(1,4200))*rng.choice([-1,1]),rng.getrandbits(rng.randrange(1,4200)) or 1) for _ in range(1000)]
wire=lambda r:[str(r.numerator),str(r.denominator)]
for i in range(0,len(ratios),100):add(['numbers',list(map(wire,ratios[i:i+100]))],'rational-floating-rounding')
for r in ratios[::137]:add(['mml','DisplayBlock',[['ESpace',wire(r)],['EScaled',wire(r),['EScaled',['6','5'],['ESymbol','Open','(']]]]],'space-and-scale')
# Raw XML names, attributes, every CData kind and configuration branches.
cp=lambda s:list(map(ord,s)) if isinstance(s,str) else s
name=lambda s,u=None,p=None:[cp(s),None if u is None else cp(u),None if p is None else cp(p)]
text=lambda s,k='CDataText',line=None:['text',k,cp(s),line]
element=lambda n,attrs=(),content=(),line=None:['element',n,list(attrs),list(content),line]
raws=['','x','?xml','throw','?throw','empty','漢🙂',[0xd800,0xdc00],[0x10000],[0xd800],'<&"\'\n\t\r',chr(0)+chr(127)]
ns=[name(s,u,p) for s,u,p in itertools.product(raws,[None,'','uri'],[None,'','p'])]
for i in range(0,len(ns),12):add(['names',ns[i:i+12]],'qualified-names')
attrs=[[n,cp(raws[i%len(raws)])] for i,n in enumerate(ns)]
for i in range(0,len(attrs),12):add(['attributes',attrs[i:i+12]],'xml-attributes')
for raw,kind in itertools.product(raws+['a\nb\n',']]>]]>', '\u2000\u2001\u2061'],['CDataText','CDataVerbatim','CDataRaw']):
 t=text(raw,kind,'-1000000000000000000000');add(['xml',t],'xml-cdata')
 for content in [[t],[],[t,['ref',cp('name')],element(name('child'),content=[t]),t]]:
  e=element(name('root',p='p'),attrs[:3]+attrs[:2],content,'100000000000000000000000')
  add(['xml',e],'xml-elements')
for n,pretty,mode,content in itertools.product(['empty','x','throw','?throw'],[False,True],['even','named','throw','none'],[[],[text('')],[element(name('empty'))]]):
 add(['config',element(name(n),content=content),pretty,mode],'xml-config-predicates')
for first in range(0,0x110000,65536):add(['escape-range',first,min(65536,0x110000-first)],'all-characters-xml-escape')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalWriterGoldens=561,operatorRows=1107,oracle='Complete unchanged texmath MathML/dictionary and xml1.3.14 output; exact trees and raw-Char serialization witnesses; original numeric functions; transport only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'MathML/XML observations; SHA',m['caseHash'],flush=True)
