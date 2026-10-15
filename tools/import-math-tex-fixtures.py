#!/usr/bin/env python3
"""Capture original complete TeX macro and Unicode.ToTeX observations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root/'vendor/texmath-0.13.3.1';folder=root/'tools/haskell-math-tex-reference';target=root/'core/src/test/resources/math-tex/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_MATH_TEX_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['tools/import-math-tex-fixtures.py','tools/import-math-tex-data.py','licenses/Texmath-GPL-2.0.txt','licenses/Parsec-BSD-2-Clause.txt','licenses/LPPL-1.3c.txt','licenses/Math-TeX-Mapping-NOTICE.txt']
sources+=sorted(str(p.relative_to(root)) for base in [source,folder,root/'vendor/parsec-3.1.18.0',root/'vendor/ghc-unicode-9.12.2'] for p in base.rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
def tex_inputs():
 out={}
 for path in sorted((source/'test').rglob('*.test')):
  lines=path.read_bytes().split(b'\n');lines=lines[:-1] if lines[-1]==b'' else lines
  for i,line in enumerate(lines):
   if not line.startswith(b'<<<') or line[3:].strip()!=b'tex':continue
   end=next(j for j in range(i+1,len(lines)) if lines[j].startswith(b'>>>'))
   text=(b'\n'.join(lines[i+1:end])+b'\n' if end>i+1 else b'').decode()
   out[str(path.relative_to(root))]=text
 return out
cp=lambda s:list(map(ord,s)) if isinstance(s,str) else s
if args.check_source:
 subprocess.run(['python3',str(root/'tools/import-math-tex-data.py'),'--check'],check=True)
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['caseHash']==digest(cs) and m['cases']==len(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 originals={c['source']:c['request'][1] for c in cs if c['group']=='original-tex-preprocessing'}
 assert originals=={s:cp(t) for s,t in tex_inputs().items()}
 assert len(next(c['expected'] for c in cs if c['request']==['records']))==2755
 assert all(c['expected']!='host' for c in cs)
 print('Verified',len(cs),'original macro/Unicode observations,',len(originals),'verbatim TeX inputs and',len(m['sources']),'source hashes');raise SystemExit
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
 if len(cs)%1000==0:print('Captured',len(cs),'macro/Unicode observations',flush=True)
 return c
for path,text in tex_inputs().items():add(['document',cp(text)],'original-tex-preprocessing',source=path)
records=add(['records'],'complete-record-table')['expected'];assert len(records)==2755
symbols=add(['symbol-map'],'complete-symbol-map')['expected']
envs=[[],['base'],['amsmath','amssymb'],['unicode-math'],['unknown'],['mathabx','stmaryrd'],['stmaryrd','mathabx']]
# Entire Char domain, including non-scalar raw Chars (Text replaces surrogates).
add(['types-range',0,0x110000],'all-characters-category')
for env in envs:
 for start in range(0,0x110000,65536):add(['unicode-range',list(map(cp,env)),start,min(65536,0x110000-start)],'all-characters-conversion')
for record in records:
 c=record['char'];packages=list(dict.fromkeys(x[0] for x in record['commands'] if x[0]!='base'))
 for env in [[],*[[pkg] for pkg in packages],packages,list(reversed(packages)),['unicode-math'],['unknown']]:
  add(['unicode',list(map(cp,env)),[c,ord('a'),c]],'every-record-package-selection')
for text,env in itertools.product(['','x y','{}\\$&%_#^~','αβΓ∞→𝒜𝔄𝐀𝟘','x\ufe00y\ufe0ez','\t\r\n\x00\x7f',[0xd800,0xdc00,0x10000,0xdfff],''.join(chr(r['char']) for r in records)],envs):
 add(['unicode',list(map(cp,env)),cp(text)],'unicode-adjacency-and-normalization')
# All constructor variants, names and argument counts; canonical Show is native.
names=['\\x','\\xyz','\\','\\!','\\α','\\漢','\\𝒜','\\a1','\\a b','\\{','bad','']
bodies=['','x','#0#1#2#9','{x{y}}','\\{x\\}','\\#1#1','x%comment\ny','\\alpha','漢🙂']
def command(name='\\x',n=0,default=None,body='x',kind='newcommand',star=''):
 return '\\'+kind+star+'{'+name+'}'+('['+str(n)+']' if n else '')+('['+default+']' if default is not None else '')+'{'+body+'}'
def parse(s,group='macro-parser',name='source',line=1,col=1,user='123456789012345678901234567890'):
 return add(['parse',cp(name),str(line),str(col),user,cp(s)],group)
valid=[]
for name,n in itertools.product(names,range(10)):
 for default in [None,'','default','{x}\\] y'] if n else [None]:
  definition=command(name,n,default,'#0#1#2#3#4#5#6#7#8#9\\#1')
  parse(definition+' tail','macro-counts-and-names');add(['definitions',cp(definition+' tail')],'macro-definitions')
  add(['round',cp(definition),cp(name+('[opt]' if default is not None else '')+'{a}\\b c{d}{e}{f}{g}{h}{i} end')],'macro-arguments')
  if name.startswith('\\'):valid.append(definition)
for kind,star,body,name in itertools.product(['newcommand','renewcommand','providecommand'],['','*'],bodies,names):
 definition=command(name,body=body,kind=kind,star=star);parse(definition,'macro-command-constructors')
for star,name,body in itertools.product(['','*'],names,bodies):
 definition='\\DeclareMathOperator'+star+'{'+name+'}{'+body+'}';parse(definition,'macro-operator-constructors');add(['round',cp(definition),cp(name+' x '+name+'a '+name+'_1')],'macro-operator-expansion')
for kind,star,n,default in itertools.product(['newenvironment','renewenvironment'],['','*'],range(10),[None,'','d']):
 if n==0 and default is not None:continue
 definition='\\'+kind+star+'{env}'+('['+str(n)+']' if n else '')+('['+default+']' if default is not None else '')+'{<#1#2}{#2#1>}'
 parse(definition,'macro-environment-constructors');valid.append(definition)
 for body in ['x','\\begin{env}{a}{b}{c}{d}{e}{f}{g}{h}{i}x\\end{env}','\\end{env }','%x\ny']:
  add(['round',cp(definition),cp('pre\\begin {env }'+('[v]' if default is not None else '')+'{1}{2}{3}{4}{5}{6}{7}{8}{9}'+body+'\\end {env}post')],'macro-environment-expansion')
# Prefixes and malformed delimiters test original consumed/empty/error details.
seeds=[command(),command(n=2,default=' a %x\n b ',body='{a}\\{b\\}#1'),r'\DeclareMathOperator*\foo{bar}',r'\newenvironment{env}[2][d]{#1}{#2}',r'\renewcommand\foo\bar',r'\newcommand{\x}[1]{a\%b}',r'\newcommand{\x}[10]{x}',r'\newcommand{\x}[١]{x}']
for seed in seeds:
 for end in range(len(seed)+1):
  for tail in ['', '\\', '%x', '%x\n', '}', '\n\t']:
   parse(seed[:end]+tail,'macro-prefix-and-error')
for rawname in ['', '漢🙂',[0xd800,0xdc00],[0x10000]]:
 for line,col in [(1,1),(-1,-9),(2**63-2,2**63-2),(-2**63,-2**63)]:
  for s in [r'\newcommand{\x}{y}', '\\newcommand\n\t{\\x}[1]{#1}\tend', '\\newcommand\n\t{\\x}[', [92,110,101,119,99,111,109,109,97,110,100,123,92,0xd800,0xdc00,125,123,0xd800,0xdc00,125]]:parse(s,'macro-raw-position-state',rawname,line,col)
for ws in ['',' ','\t','\r','\n','%a\n',' %a\n\t%b\n','%no-newline']:
 for defn in seeds[:4]:
  add(['definitions',cp(ws+defn+ws+'tail')],'macro-space-comment-boundaries')
  parse(defn.replace('}{','}'+ws+'{'),'macro-space-comment-parser')
# Empty macro list differs from a nonmatching nonempty list for comments.
for defs in [[],[command()],[command(body='\\x')],[command(body='\\y'),command('\\y',body='\\x')],[command(body='a'),command(body='b')],[command(body='\\y'),command('\\y',body='z')]]:
 for text in ['','\\','%end','%line\n','a%line\nb','\\x%line\n','\\x%end','\\x\\x','\\xabc \\xα \\x𝒜 \\x_1','\\% literal \\#1']:
  add(['apply',list(map(cp,defs)),cp(text)],'macro-rewrite-limit-and-shadowing')
  add(['round',cp('\n'.join(defs)),cp(text)],'macro-definition-shadow-order')
for n in range(1,25):
 defs=[command('\\'+('a'*i),body=('\\'+('a'*(i+1)) if i<n else 'z')) for i in range(1,n+1)]
 add(['apply',list(map(cp,defs)),cp('\\a')],'macro-transitive-chain')
for inv in ['\\x','\\x{a}','\\x[a]{b}','\\x [a] {b}','\\x[a[b]c]{d}','\\x[ a%q\nb ]{c}','\\x[\\]x]{\\{y\\}}','\\x{a%q\nb}','\\x{\\#1}','\\x{#2}', '\\x{a%end}']:
 for n,default in itertools.product(range(4),[None,'d']):
  if n==0 and default is not None:continue
  add(['round',cp(command(n=n,default=default,body='#0#1#2#3#9\\#1')),cp(inv)],'macro-substitution-delimiters')
# Deterministic parser/expansion fuzz; results supplied exclusively by upstream.
rng=random.Random(0x544558);alphabet='abc\\{}[]%\n\t#0129* α🙂'
for i in range(2500):
 seed=rng.choice(seeds+valid);at=rng.randrange(len(seed)+1)
 mutated=seed[:at]+''.join(rng.choices(alphabet,k=rng.randrange(6)))+seed[at+rng.randrange(min(4,len(seed)-at)+1):]
 parse(mutated,'macro-parser-generated')
 if i%3==0:add(['definitions',cp(mutated+'\nrest')],'macro-definition-generated')
 if i%5==0:add(['round',cp(rng.choice(valid)),cp(''.join(rng.choices(alphabet,k=30))+'\\x{a}[d]')],'macro-expansion-generated')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),originalTeXInputs=len(tex_inputs()),records=2755,symbols=len(symbols),oracle='Complete unchanged texmath Macros and Unicode.ToTeX; Parsec reply transport and raw-Char output witnesses only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'macro/Unicode observations; SHA',m['caseHash'],flush=True)
