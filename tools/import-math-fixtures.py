#!/usr/bin/env python3
"""Capture original texmath expressions/helpers/Pandoc output; Kotlin is never an oracle."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root/'vendor/texmath-0.13.3.1';folder=root/'tools/haskell-math-reference';target=root/'core/src/test/resources/math/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_MATH_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-math-fixtures.py','tools/import-math-unicode-data.py','licenses/Texmath-GPL-2.0.txt','upstream/pandoc/src/Text/Pandoc/Writers/Math.hs','upstream/pandoc/src/Text/Pandoc/Writers/HTML.hs','upstream/pandoc/test/Tests/Writers/HTML.hs','vendor/ghc-math-9.12.2/GHC/Internal/Real.hs','vendor/ghc-math-9.12.2/LICENSE.txt','vendor/ghc-9.12.2/GHC/Internal/Float/RealFracMethods.hs','vendor/ghc-9.12.2/GHC/Num/Integer.hs','vendor/ghc-9.12.2/rts/StgPrimFloat.c']
sources+=sorted(str(p.relative_to(root)) for base in [source,folder] for p in base.rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 subprocess.run(['python3',str(root/'tools/import-math-unicode-data.py'),'--check'],check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert set(m['originalInputs'])=={str(p.relative_to(root)) for p in (source/'test').rglob('*') if p.suffix in ['.test','.native']}
 assert len([c for c in cs if c['request'][0]=='unicode-range'])==14
 print('Verified',len(cs),'original math observations,',len(m['originalInputs']),'original inputs and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group,**extra):
 cs.append(dict(request=req,expected=ask(req),group=group,**extra))
 if len(cs)%1000==0:print('Captured',len(cs),'math observations',flush=True)
inputs={};seen=set()
for path in sorted((source/'test').rglob('*')):
 if path.suffix not in ['.test','.native']:continue
 key=str(path.relative_to(root));data=path.read_bytes()
 if path.suffix=='.native':fmt='native';text=data.decode();outfmt=None;outtext=None
 else:
  lines=data.split(b'\n');lines=lines[:-1] if lines[-1]==b'' else lines
  start=next(i for i,l in enumerate(lines) if l.startswith(b'<<<'));end=next(i for i,l in enumerate(lines[start+1:],start+1) if l.startswith(b'>>>'))
  fmt=lines[start][3:].decode().strip();text=(b'\n'.join(lines[start+1:end])+b'\n' if end>start+1 else b'').decode()
  outfmt=lines[end][3:].decode().strip();outtext=(b'\n'.join(lines[end+1:])+b'\n' if end<len(lines)-1 else b'').decode()
 response=ask(['parse',fmt,text]);inputs[key]=dict(format=fmt,result=response[0] if isinstance(response,list) else response)
 if isinstance(response,list) and response[0]=='Right':
  es=response[1];encoded=json.dumps(es,ensure_ascii=True)
  if encoded not in seen:
   add(['expressions',es],'original-input',source=key);seen.add(encoded)
  if outfmt=='pandoc':
   golden=ask(['pandocGolden',es]);actual='' if golden is None else golden
   if actual and not actual.endswith('\n'):actual+='\n'
   assert actual==outtext,(key,actual,outtext)
  if outfmt=='native':
   expected=ask(['parse','native',outtext])
   if isinstance(expected,list) and expected[0]=='Right' and json.dumps(expected[1]) not in seen:
    add(['expressions',expected[1]],'original-native-output',source=key);seen.add(json.dumps(expected[1]))
# Every constructor, style, symbol, nesting and spacing branch, with source output only.
types=re.findall(r'\bText\w+\b', (source/'src/Text/TeXMath/Types.hs').read_text().split('data TextType =',1)[1].split('deriving',1)[0]);types=list(dict.fromkeys(types))
symbols='Ord Op Bin Rel Open Close Pun Accent Fence TOver TUnder Alpha BotAccent Rad'.split();a=['EIdentifier','x'];b=['ENumber','2'];empty=['EGrouped',[]]
base=[a,b,empty,['ENumber',''],['ENumber','漢🙂'],['EMathOperator','sin'],['EMathOperator','unknown']]
base += [['ESymbol',t,s] for t,s in itertools.product(symbols,['+','=',',','\u2032\u2033\u2057',"'",'', 'x'])]
base += [['ESpace',[str(n),str(d)]] for n,d in [(-2,1),(-1,2),(0,1),(1,9),(1,6),(2,9),(1,2),(13,18),(3,1)]]
for tag in ['ESub','ESuper','ERoot']:base.append([tag,a,b])
base += [['ESubsup',a,b,a],['EUnderover',True,a,b,a],['EUnderover',False,a,b,a]]
for tag,flag in itertools.product(['EOver','EUnder'],[True,False]):base.append([tag,flag,a,b])
for tag in ['EPhantom','EBoxed','ESqrt']:base.append([tag,a])
base += [['ECancel',s,a] for s in ['ForwardSlash','BackSlash','XSlash']]
base += [['EFraction',f,a,b] for f in ['NormalFrac','DisplayFrac','InlineFrac','NoLineFrac']]
base += [['EScaled',['6','5'],a],['EArray',['AlignLeft','AlignCenter','AlignRight'],[[[a,b],[],[empty]],[[],[a]]]],['EDelimited','(',')',[['Left','|'],['Right',a]]],['EDelimited','','',[['Right',['EDelimited','','',[['Right',a],['Right',b]]]]]],['EGrouped',[empty,a,empty]]]
for tt,text in itertools.product(types,['','x','ABCabc012Γπ','漢🙂']):base.extend([['EText',tt,text],['EStyled',tt,[['EIdentifier',text],b]]])
for chunk in range(0,len(base),10):add(['expressions',base[chunk:chunk+10]],'constructors')
for flag,wrapped,accent,tt in itertools.product([True,False],[False,True],[0x203e,0x304,0xb4,0x301,0x60,0x300,0x2d8,0x306,0x2c7,0x30c,46,0x307,0xb0,0x30a,0x20d7,0x20d6,94,0x302,126,0x303,0x308,0x1f642],types):
 x=['EGrouped',[a]] if wrapped else a;e=['EOver',flag,x,['ESymbol','Accent',chr(accent)]]
 add(['expressions',[['EStyled',tt,[e]]]],'accent-priority')
for t1,t2,rest in itertools.product(symbols,symbols,[[],[b],[['ESpace',['1','6']],b]]):
 add(['expressions',[['ESymbol',t1,'+'],['ESymbol',t2,',']]+rest],'symbol-spacing')
rng=random.Random(0x1331)
def expression(depth=0):
 if depth>3 or rng.randrange(3)==0:return rng.choice(base[:115])
 return rng.choice([['EGrouped',[expression(depth+1) for _ in range(rng.randrange(4))]],['ESub',expression(depth+1),expression(depth+1)],['EStyled',rng.choice(types),[expression(depth+1)]],['EDelimited','','',[['Right',expression(depth+1)],['Left','|']]]])
for _ in range(500):add(['expressions',[expression() for _ in range(rng.randrange(8))]],'expression-fuzz')
# Shared exact-rational boundaries and textual grammar.
ratios=[[str(n),str(d)] for n,d in itertools.product(range(-40,61),[1,2,3,5,9,18,100])]
ratios += [[str(n),'1'] for n in [2**63,2**64,2**64+1,-2**64,-2**64+1]]
texts=['',' ','normal','bold','italic','unknown','Γ','ΑΩ','\u03a2','Γx','\\big','\\Big','\\bigg','\\Bigg']
shared=(source/'src/Text/TeXMath/Shared.hs').read_text()
texts += [chr(int(x,16)) for x in re.findall(r'"\\x([0-9A-Fa-f]+)"',shared)]
texts += re.findall(r'\("([^"\\]*)",\s*"\\\\math',shared)
texts += [str(n)+u for n,u in itertools.product([-2,-0.5,0,0.027777777777777776,0.08333333333333333,0.25,1,100,1e100],['pt','mm','cm','in','ex','em','mu','dd','bp','pc','bad',''])]
texts += ['.5em','+1em',' 1em','1 em','1.em','1.0em','-0em','1e2em','1\nem','1.2.3em','漢em']
texts += [sign+'1'+'0'*n+unit for sign,n,unit in itertools.product(['','-'],[50,100,300,307,308,309,400],['em','pt','in'])]
texts += [''.join(rng.choice('0123456789') for _ in range(rng.randrange(1,400)))+'.'+''.join(rng.choice('0123456789') for _ in range(rng.randrange(1,50)))+rng.choice(['em','pt','mu','mm']) for _ in range(500)]
als=[list(x) for n in range(6) for x in itertools.product(['AlignLeft','AlignCenter','AlignRight'],repeat=n)]
for i in range(0,len(ratios),50):add(['shared',ratios[i:i+50],texts,list(range(32,256))+list(range(0x2000,0x2070)),als],'shared-boundaries')
add(['styles',types,[[],['amsmath','amssymb'],['unicode-math'],['UNICODE-MATH'],['unicode-math','other']]],'text-styles')
add(['tables'],'public-tables')
records=[['operator',s,s,form,str(n),str(-n-1),str(n//2),[s,'duplicate','duplicate']] for s,form,n in itertools.product(['','text 漢🙂','a\n\t'],['FPrefix','FPostfix','FInfix'],[-2**63,-1,0,2**63-1])]
records += [['unicode',cp,[['amsmath','\\foo'],['','text']],ty,'text 漢🙂'] for cp,ty in itertools.product([0,10,34,39,127,0xd800,0x1f642,0x10ffff],symbols)]
add(['records',records],'record-show')
for tt in types:
 add(['unicode-range',tt,0,0x110000],'all-characters-unicode')
 add(['unicode',tt,list(range(128))+list(range(0x390,0x3d7))+[0x2102,0x210d,0xd800,0xdc00,0x10000,0x1d400,0x1d7ff,0x10ffff]],'unicode-text')
add(['escape-range',0,0x110000],'all-characters-escape')
for i in range(0,0x110000,65536):add(['escape',list(range(i,min(i+32,0x110000)))],'escape-text-boundaries')
controls=['','\\','\\ ','\\a','\\x1','\\123','\\🙂','\\漢','\\a b','\\a\n','a','\\_']
controls += ['\\'+chr(cp) for cp in range(0,0x110000,997)]
add(['control',[list(map(ord,s)) for s in controls]],'control-sequences')
texbase=[['Space'],['ControlSeq','\\alpha'],['ControlSeq','\\ '],['ControlSeq',''],['Literal',''],['Literal','abc'],['Literal','a!'],['Literal','\\textasciicircum']]+[['Token',cp] for cp in [32,65,48,94,95,0x391,0x1f642,0xd800]]
add(['tex-meta',texbase+[['Token',cp] for cp in range(128)]+[['Grouped',texbase],['Grouped',[]]]],'tex-instances')
for suffix in ['', 'abc','1','漢','🙂','^x','_x','\\limits','\\lim',' x','\n','\ud800']:
 add(['tex',texbase+[['Grouped',[a,b]] for a,b in itertools.product(texbase,repeat=2)]+[['Grouped',[['Grouped',[['Space'],['Literal','x'],['Space']]]]]],list(map(ord,suffix))],'tex-rendering')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalInputs=inputs,oracle='Complete original texmath package; only JSON protocol adapters, source readers supply defined input trees; no Kotlin invocation')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'math observations, original inputs',len(inputs),'SHA',m['caseHash'],flush=True)
