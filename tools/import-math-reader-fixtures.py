#!/usr/bin/env python3
"""Capture complete original TeX reader/table observations and original goldens."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root/'vendor/texmath-0.13.3.1';folder=root/'tools/haskell-math-reader-reference';target=root/'core/src/test/resources/math-reader/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_MATH_READER_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
assert (folder/'MathWire.hs').read_bytes()==(root/'tools/haskell-math-reference/MathReference.hs').read_bytes().replace(b'module Main where',b'module MathWire where',1)
assert (folder/'Text/TeXMath/Readers/TeX/Commands.hs').read_bytes()==(source/'src/Text/TeXMath/Readers/TeX/Commands.hs').read_bytes()
sources=['tools/import-math-reader-fixtures.py','tools/import-math-commands.py','tools/import-math-reader-data.py','tools/haskell-math-reference/MathReference.hs','core/src/test/resources/math/data.jsonl.gz','core/src/test/resources/math-tex/data.jsonl.gz','licenses/Texmath-GPL-2.0.txt','licenses/Parsec-BSD-2-Clause.txt']
sources+=sorted(str(p.relative_to(root)) for base in [source,folder,root/'vendor/parsec-3.1.18.0',root/'vendor/ghc-unicode-9.12.2'] for p in base.rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
cp=lambda s:list(map(ord,s)) if isinstance(s,str) else s
def originals():
 out=[]
 for path in sorted((source/'test').rglob('*.test')):
  lines=path.read_bytes().split(b'\n');lines=lines[:-1] if lines[-1]==b'' else lines
  for i,line in enumerate(lines):
   if not line.startswith(b'<<<') or line[3:].strip()!=b'tex':continue
   end=next(j for j in range(i+1,len(lines)) if lines[j].startswith(b'>>>'))
   text=(b'\n'.join(lines[i+1:end])+b'\n' if end>i+1 else b'').decode()
   golden=(b'\n'.join(lines[end+1:])+b'\n' if end<len(lines)-1 else b'').decode()
   out.append((str(path.relative_to(root)),text,lines[end][3:].strip().decode(),golden))
 return out
if args.check_source:
 for tool in ['import-math-commands.py','import-math-reader-data.py']:subprocess.run(['python3',str(root/'tools'/tool),'--check'],check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 cases={c['source']:c for c in cs if c['group']=='original-tex-input'}
 assert len(cases)==149 and len([c for c in cases.values() if 'golden' in c])==69
 for path,text,fmt,golden in originals():
  c=cases[path];assert c['request']==['read',cp(text)]
  if fmt=='native':assert c['golden']==cp(golden)
 assert all(c['expected']!='host' for c in cs)
 print('Verified',len(cs),'complete reader/table observations, 149 original inputs, 69 native goldens and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 result=json.loads(line);assert result!='host',req;return result
def add(req,group,**extra):
 c=dict(request=req,expected=ask(req),group=group,**extra);cs.append(c)
 if len(cs)%1000==0:print('Captured',len(cs),'reader observations',flush=True)
 return c
def read(text,group):return add(['read',cp(text)],group)
for path,text,fmt,golden in originals():
 c=add(['read',cp(text)],'original-tex-input',source=path)
 if fmt=='native':
  expected=ask(['golden',cp(golden)]);assert c['expected']==expected,(path,c['expected'],expected);c['golden']=cp(golden)
tables=add(['tables'],'complete-command-tables')['expected']
# Every callable table entry across representative empty and structured payloads.
exprs=[[],[['EIdentifier','x']],[['ENumber','1'],['ESymbol','Bin','+'],['EIdentifier','y']],[['EGrouped',[['EIdentifier','x']]],['EText','TextItalic','Γ漢🙂']]]
for key,xs in itertools.product(tables['styleKeys']+['unknown'],exprs):add(['style',key,xs],'style-application')
for key,text in itertools.product(tables['textKeys']+['unknown'],['','x','ΓΩ','漢🙂',[0xd800,0xdc00],'{a}\\x%\n']):add(['text',key,cp(text)],'text-application')
# Every symbol/override/unit and every single/multi-character command boundary.
keys=[key for table in ['enclosures','operators','symbols'] for key,_ in tables[table]]
for key in sorted(set(keys)):
 for suffix in ['','{x}','a','_1^2']:read(key+suffix,'every-command-and-boundary')
for key,_ in tables['units']:
 for text in ['\\si{\\'+key+'}','\\SI{1.20(3)e-2}{\\'+key+'}','\\si{\\square\\'+key+'\\cubed}']:read(text,'every-si-unit')
# Full finite grammar branch matrix, ordinary and malformed delimiters.
atoms=['x','1','12.30','.4',r'\alpha',r'\sum',r'\sin',r'\lim','+','-',r'\overbrace{x}',r'\underbrace{x}','{x+y}',r'\text{hello}',r'\operatorname*{foo}',r'\operatorname{{x}}']
for a,b,tail in itertools.product(atoms,atoms,['','_i','^2',"'",'_i^2','^2_i',r'\limits_i^2',r'\nolimits_i^2']):read(a+b+tail,'expression-script-and-bin-classification')
for cmd in ['frac','dfrac','tfrac','binom','overset','underset','stackrel','sqrt','surd','phantom','boxed','cancel','bcancel','xcancel','color','ensuremath','genfrac','substack','xleftarrow','xrightarrow','mathop','mathrel','mathbin','mathord','mathopen','mathclose','mathpunct']:
 for args in ['','x','{x}','{x}{y}','[3]{x}','{(}{)}{0pt}{0}{a}{b}','{a\\\\b}']:
  text='\\'+cmd+args;read(text,'command-branches')
  for end in range(len(text)):read(text[:end],'command-truncations')
envs=['array','eqnarray','align','aligned','alignat','alignedat','flalign','flaligned','cases','matrix','smallmatrix','pmatrix','bmatrix','Bmatrix','vmatrix','Vmatrix','split','multline','multlined','gather','gathered','equation']
for env,star,row,ending in itertools.product(envs,['','*'],['','x','a&b','a&b\\\\c&d','\\hline a&b\\\\\\hline c&d','a\\\\[1em]b','a\\\\ [1em]b'],['','\\\\']):
 head='{lcr}' if env=='array' else '{2}' if env in ['alignat','alignedat'] else ''
 read('\\begin{'+env+star+'}'+head+row+ending+'\\end{'+env+star+'}','all-environments')
for spec in ['lcr','|p{2cm}m{x}b{{x}}|','>{\\bf}l<{z}!{x}r@{x}c','*{2}{lr}','*{2}{*{3}{lr}}','*{0}{l}','*{18446744073709551615}{l}','*{9223372036854775808}{l}','unknown','*{}{}','*{2}{l']:
 read('\\begin{array}{'+spec+'}x&y\\\\z\\end{array}','array-column-grammar')
for a,b,body in itertools.product(['(','.','<',r'\langle',r'\lVert'],[')', '.', '>',r'\rangle',r'\rVert'],['','x',r'x\middle|y',r'\left[x\right]','+x-y=+z']):read('\\left'+a+body+'\\right'+b,'delimiters')
for cmd,body in itertools.product(tables['textKeys'],['', 'a  b\nc','--- -- - ``x\'\' ~','{a{b}}',r'$x^2$ and \(y\)',r'\# \$ \% \& \_ \{ \}',r'\char`a',r'\quad a\!b\,c\;d',r'\textbf{x}',r'\unknown',r'a%not-comment']):read(cmd+'{'+body+'}','text-mode')
for cmd in ['`',"'",'^','~','"','.','=','c','v','u']:
 for c in list('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz')+['漢','🙂','{','}','\\']:
  for payload in [c,'{'+c+'}']:read('\\text{\\'+cmd+payload+'}','text-accents')
for cmd,arg in itertools.product(['si','unit','SI','qty','SIrange','qtyrange','numrange','numlist','num','ang'],['','{}','{1}','{+1.2(03)e-2}','{1;2;3;4}','[a=b]{1}[x]{2}[y]{\\kg}','{1}{2}{\\m}','{1.200(0030)}','{1+-2}','{i,3 x 2e3}','{-1(2)}']):read('\\'+cmd+arg,'si-grammar')
for cmd,arg in itertools.product(['enspace','mspace','hspace'],['','{}','{0mu}','{000018mu}','{2.50cm}','{-.2pt}','{em}','{-em}','{.em}','{1.0in}','{1000000000000000000000000000000mu}']):read('\\'+cmd+arg,'spaces-and-integers')
for cs0 in [list(range(128)),[0x80,0xa0,0x2000,0x2061,0xd800,0xdc00,0x10000,0x10ffff],list(range(0x300,0x370))]:
 for c in cs0:read([c],'raw-character-decisions')
# Original macro corpus inputs also execute the full reader, including failures.
with gzip.open(root/'core/src/test/resources/math-tex/data.jsonl.gz','rt') as f:next(f);earlier=[json.loads(l) for l in f]
for c in earlier:
 r=c['request']
 if r[0]=='round':read(r[1]+[10]+r[2],'macro-to-reader')
rng=random.Random(0x52454144);alphabet='abc0123+-*/\\{}[]_%^\n\t$\' αβ漢🙂'
seeds=[r'\frac{x}{y}',r'\sqrt[3]{x}',r'\begin{array}{lr}x&y\end{array}',r'\text{a $x$ b}',r'\SI{2.0(3)e2}{\kg}',r'\newcommand{\x}[1]{#1}\x{a}',r'\left(x\middle|y\right)']
for _ in range(4000):
 seed=rng.choice(seeds);at=rng.randrange(len(seed)+1);text=seed[:at]+''.join(rng.choices(alphabet,k=rng.randrange(6)))+seed[at+rng.randrange(min(5,len(seed)-at)+1):];read(text,'generated-reader-boundaries')
read('\\operatorname{'+('\\mathrm{'*1500)+'x'+('}'*1501),'finite-deep-styled-operator')
for first in range(0,0x110000,65536):add(['read-range',first,min(65536,0x110000-first)],'all-characters-readTeX')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalInputs=149,originalNativeGoldens=69,oracle='Complete unchanged readTeX, Commands and original Exp Read/Show; transport only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'reader observations; SHA',m['caseHash'],flush=True)
