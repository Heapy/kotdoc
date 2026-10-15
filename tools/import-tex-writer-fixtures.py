#!/usr/bin/env python3
"""Capture full original TeX writer/round-trip results, never invoke Kotlin."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root/'vendor/texmath-0.13.3.1';folder=root/'tools/haskell-tex-writer-reference'
target=root/'core/src/test/resources/tex-writer/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_TEX_WRITER_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-tex-writer-fixtures.py','tools/haskell-math-reader-reference/MathWire.hs','licenses/Texmath-GPL-2.0.txt','licenses/XML-Light-BSD-3-Clause.txt','licenses/LPPL-1.3c.txt','licenses/Math-TeX-Mapping-NOTICE.txt']
sources+=sorted(str(f.relative_to(root)) for base in [source,root/'vendor/xml-1.3.14',folder] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
def cp(s):return list(map(ord,s))
def newline(s):return s if not s or s.endswith('\n') else s+'\n'
def originals():
 out=[]
 for path in sorted((source/'test').rglob('*.test')):
  lines=path.read_bytes().split(b'\n');lines=lines[:-1] if lines[-1]==b'' else lines
  starts=[i for i,l in enumerate(lines) if l.startswith(b'<<<')]
  if not starts:continue
  a=starts[0];b=next(i for i in range(a+1,len(lines)) if lines[i].startswith(b'>>>'))
  text=(b'\n'.join(lines[a+1:b])+b'\n' if b>a+1 else b'').decode()
  golden=(b'\n'.join(lines[b+1:])+b'\n' if b<len(lines)-1 else b'').decode()
  out.append((str(path.relative_to(root)),lines[a][3:].strip().decode(),text,lines[b][3:].strip().decode(),golden))
 return out
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 goldens={c['source']:c for c in cs if c['group']=='original-tex-writer-golden'}
 regressions={c['source']:c for c in cs if c['group']=='original-tex-output-regression'}
 inputs={c['source']:c for c in cs if c['group']=='every-successful-original-input-through-writer'}
 originals_=originals();assert len(goldens)==561 and len(regressions)==4
 assert set(inputs).isdisjoint(m['readerFailures']) and set(inputs)|set(m['readerFailures'])=={x[0] for x in originals_}
 for path,fmt,text,out,golden in originals_:
  c=inputs.get(path) or m['readerFailures'][path]
  assert c['originalInput']==[fmt,cp(text)]
  if out=='tex':
   c=(goldens if '/test/writer/tex/' in path else regressions)[path]
   assert c['originalInput']==[fmt,cp(text)] and c['golden']==cp(golden)
   assert newline(c['expected'])==golden
 rounds={c['source']:c for c in cs if c['group']=='original-tex-roundtrip'}
 paths=sorted((source/'test/roundtrip').iterdir());assert len(rounds)==len(paths)==561
 for path in paths:
  c=rounds[str(path.relative_to(root))];assert c['golden']==cp(path.read_text())
  e=c['expected'];assert e['original']==['Right',c['request'][1]]
  assert e['matchesOriginal']==(e['actual']==e['original'])
 assert sum(not c['expected']['matchesOriginal'] for c in rounds.values())==m['nativeRoundtrip']['failed']==344
 assert all(not isinstance(c['expected'],dict) or 'host' not in c['expected'] for c in cs)
 print('Verified',len(cs),'TeX writer observations, 561 original goldens, 4 original output regressions, 561 original TeX round-trips and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
binary=Path(args.oracle).resolve();native_tests=binary.parents[4]/'original-texmath-tests/opt/build/original-texmath-tests/original-texmath-tests'
originals_=originals();regressions=[Path(path).stem for path,fmt,text,out,golden in originals_ if out=='tex' and '/test/regression/' in path]
pattern='($2 == "writer" && $3 == "tex") || ($2 == "regression" && ('+' || '.join('$3 == '+json.dumps(s) for s in regressions)+'))'
subprocess.run([str(native_tests),'--pattern',pattern],cwd=source,check=True)
baseline=subprocess.run([str(native_tests),'--roundtrip','--pattern','$2 == "roundtrip" && $3 == "tex"'],cwd=source,capture_output=True,text=True)
print(baseline.stdout,baseline.stderr,flush=True)
match=re.search(r'(\d+) out of (\d+) tests failed',baseline.stdout);assert baseline.returncode==1 and match
assert tuple(map(int,match.groups()))==(344,561)
native_roundtrip=dict(tests=561,failed=344,passed=217,exit=baseline.returncode)
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
 if len(cs)%1000==0:print('Captured',len(cs),'TeX writer observations',flush=True)
 return c
parsed={};reader_failures={}
for path,fmt,text,out,golden in originals_:
 result=ask(['parse',fmt,cp(text)]);parsed[path]=result
 if fmt in ['mml','mathml']:add(['from-mathml',cp(text)],'original-mathml-to-tex-bridge',source=path)
 if result[0]=='Left':
  assert out!='tex',(path,result)
  reader_failures[path]=dict(originalInput=[fmt,cp(text)],error=result[1]);continue
 req=['write',result[1]]
 add(req,'every-successful-original-input-through-writer',source=path,originalInput=[fmt,cp(text)])
 if out=='tex':
  assert result[0]=='Right',(path,result)
  c=add(req,'original-tex-writer-golden' if '/test/writer/tex/' in path else 'original-tex-output-regression',source=path,originalInput=[fmt,cp(text)],golden=cp(golden))
  assert newline(c['expected'])==golden,(path,c['expected'],golden)
for path in sorted((source/'test/roundtrip').iterdir()):
 original=path.read_text();r=ask(['parse','native',cp(original)]);assert r[0]=='Right'
 assert newline(ask(['pretty',r[1]]))==original,path
 c=add(['roundtrip',r[1]],'original-tex-roundtrip',source=str(path.relative_to(root)),golden=cp(original))
 assert c['expected']['original']==r,path
assert sum(not c['expected']['matchesOriginal'] for c in cs if c['group']=='original-tex-roundtrip')==native_roundtrip['failed']
# Package order is semantically significant for Unicode command selection.
envs=[[],['amsmath','amssymb'],['amsmath'],['amssymb'],['unicode-math'],['amssymb','amsmath'],['unicode-math','amsmath'],['amsmath','unicode-math'],['cancel'],['unknown'],['amsmath','amsmath'],['fourier','unicode-math'],['unicode-math','fourier']]
for path,fmt,text,out,golden in originals_:
 if '/test/writer/tex/' in path:
  es=parsed[path][1]
  for env in envs:add(['with',env,es],'all-original-writer-trees-with-packages',source=path)
x=['EIdentifier','x'];y=['ENumber','2'];empty=['EGrouped',[]]
for code,category,packages in ask(['records']):
 s=chr(code)
 es=[['EIdentifier',s+'x'],['ENumber','2'+s],['ESymbol',category,s],['EText','TextNormal',' '+s+' '],['EStyled','TextBold',[['EIdentifier',s]]]]
 for env in [['amsmath','amssymb'],packages,packages[::-1]]:add(['with',env,es],'complete-unicode-record-package-contexts')
for env,base,mark,convert,kind in itertools.product(envs[:8],[x,['EIdentifier','ab'],['EMathOperator','custom'],['EMathOperator','lim'],['ESymbol','Op','→'],['ESymbol','Op','←'],['ESymbol','Op','∑'],['ESuper',x,y]],['̂','̃','⃗','⃖','̿','̲','under','′'],[False,True],['EOver','EUnder','EUnderover']):
 script=['ESymbol','Accent',mark]
 e=[kind,convert,base,script] if kind!='EUnderover' else [kind,convert,base,script,y]
 add(['with',env,[e]],'scripts-accents-arrows-and-convertible-scopes')
for env,op,close,align,rows in itertools.product(envs[:5],['(','[','{','∣','∥','|','⟨','bad',''],[')',']','}','∣','∥','|','⟩','bad',''],[[],['AlignCenter'],['AlignLeft','AlignLeft'],['AlignRight','AlignLeft']],[[],[[]],[[[x]]],[[[x],[]],[],[[y]]]]):
 e=['EDelimited',op,close,[['Right',['EArray',align,rows]]]]
 add(['with',env,[e]],'delimited-arrays-and-row-boundaries')
for env,op,close in itertools.product(envs[:5],['(','[','{','⟨','wrong',''],[')',']','}','⟩','wrong','']):
 add(['with',env,[['EDelimited',op,close,[['Right',['EFraction','NoLineFrac',x,y]]]]]],'binomial-genfrac-and-fallback')
for env,ty,text in itertools.product(envs[:5],['TextNormal','TextBold','TextItalic','TextMonospace','TextSansSerif','TextDoubleStruck','TextScript','TextFraktur','TextBoldItalic','TextSansSerifBold','TextSansSerifBoldItalic','TextBoldScript','TextBoldFraktur','TextSansSerifItalic'],['','abc','αΓℬ𝐱𝛂',' a_b%&#{}~^\\\n\t','\u2061\ufe00\u200b\u200a\u00a0']):
 add(['with',env,[['EText',ty,text],['EStyled',ty,[['EIdentifier',text],['EDelimited',text,text,[['Left',text],['Right',x]]]]]]],'text-and-styled-unicode-fields')
for env,n,d in itertools.product(envs[:3],[-2**100,-2**64,-2**63,-37,-4,-3,-1,0,1,3,4,5,17,18,35,36,37,2**63-1,2**63,2**64,2**100],[1,18,19,100]):
 add(['with',env,[['ESpace',[str(n),str(d)]]]],'rational-floor-and-int64-space-boundaries')
for dt,text in itertools.product(['DisplayInline','DisplayBlock'],['','x','\\frac{a}{b}','\n','\u0000','𝑥','\\(x\\)']):add(['environment',dt,cp(text)],'both-latex-environments')
rng=random.Random(0x544558);leaves=[x,y,empty,['ESymbol','Ord','′'],['ESymbol','Rel','≤'],['EMathOperator','f'],['ESpace',['1','6']]]
def generated(depth):
 if depth==0 or rng.randrange(4)==0:return rng.choice(leaves)
 tag=rng.choice(['EGrouped','EDelimited','ESub','ESuper','ESubsup','EUnder','EOver','EUnderover','EFraction','ERoot','ESqrt','EPhantom','EBoxed','ECancel','EScaled','EStyled'])
 if tag=='EGrouped':return [tag,[generated(depth-1) for _ in range(rng.randrange(4))]]
 if tag=='EDelimited':return [tag,rng.choice(['(','[','|','wrong','']),rng.choice([')',']','|','wrong','']),[['Right',generated(depth-1)] for _ in range(rng.randrange(3))]]
 if tag in ['ESub','ESuper','ERoot']:return [tag,generated(depth-1),generated(depth-1)]
 if tag=='ESubsup':return [tag,*[generated(depth-1) for _ in range(3)]]
 if tag in ['EUnder','EOver','EUnderover']:return [tag,rng.choice([True,False]),*[generated(depth-1) for _ in range(3 if tag=='EUnderover' else 2)]]
 if tag=='EFraction':return [tag,rng.choice(['NormalFrac','DisplayFrac','InlineFrac','NoLineFrac']),generated(depth-1),generated(depth-1)]
 if tag=='ECancel':return [tag,rng.choice(['ForwardSlash','BackSlash','XSlash']),generated(depth-1)]
 if tag=='EScaled':return [tag,[str(rng.randrange(-5,6)),'2'],generated(depth-1)]
 if tag=='EStyled':return [tag,rng.choice(['TextNormal','TextBold','TextItalic']),[generated(depth-1)]]
 return [tag,generated(depth-1)]
for i in range(1500):add(['with',rng.choice(envs),[generated(4) for _ in range(rng.randrange(5))]],'generated-expression-and-scope-boundaries')
for kind,env in itertools.product(['group','sqrt','sub','under','narrow'],[[],['amsmath','amssymb']]):add(['deep',kind,1500,env],'finite-deep-writer')
for first in range(0,0x110000,65536):add(['write-range',['amsmath','amssymb'],first,min(65536,0x110000-first)],'all-char-four-token-reader-independent-witnesses')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalWriterGoldens=561,originalTexOutputRegressions=4,originalTexRoundtrips=561,originalInputs=len(originals_),nativeRoundtrip=native_roundtrip,readerFailures=reader_failures,oracle='Complete unchanged texmath, complete original Tasty registration; JSON observation transport only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'complete TeX writer observations; SHA',m['caseHash'],flush=True)
