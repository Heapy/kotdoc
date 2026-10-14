#!/usr/bin/env python3
"""Capture unchanged Parsec/doctemplates parsing, loader effects and path operations."""
import argparse,collections,gzip,hashlib,itertools,json,os,random,re,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/templates/parser.jsonl.gz'
sys.setrecursionlimit(20000)
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--oracle',default=os.environ.get('KOTDOC_DOCTEMPLATES_REFERENCE'));a=p.parse_args()
sources=['tools/import-template-parser-fixtures.py','tools/import-parser-char-data.py','licenses/Parsec-BSD-2-Clause.txt','licenses/Filepath-BSD-3-Clause.txt','licenses/Unicode-3.0.txt','upstream/pandoc/stack.yaml','vendor/emojis-0.1.5/src/Text/emojis.inc']
for folder in ['vendor/doctemplates-0.11.1','vendor/doclayout-0.6','vendor/parsec-3.1.18.0','vendor/filepath-1.5.4.0','vendor/ghc-unicode-9.12.2','tools/haskell-doctemplates-reference','core/src/test/resources/templates/files']:
 sources+=sorted(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cs))
 assert sorted(c['original'] for c in cs if 'original' in c)==sorted(p.name for p in (root/'vendor/doctemplates-0.11.1/test').glob('*.test'))
 assert len([c for c in cs if 'originalUnit' in c])==15
 assert [c['input'] for c in cs if c['op']=='char-flags']==[[s,min(s+4096,0x110000)] for s in range(0,0x110000,4096)]
 print(f'Verified {len(cs)} original parser observations and {len(hashes)} source files');raise SystemExit
assert a.oracle,'--oracle required'
cs=[]
def add(op,x,**extra):cs.append(dict(op=op,input=x,**extra))
test=root/'vendor/doctemplates-0.11.1/test'
files=[['test/'+p.name,p.read_bytes().decode()] for p in sorted(test.iterdir()) if p.suffix in ['.txt','.tex']]
def compile(text,path='main.txt',partials=(),**extra):add('compile-only-map',[path,text,list(partials)],**extra)
for path in sorted(test.glob('*.test')):
 j,text,golden=path.read_bytes().decode().split('\n.\n')
 compile(text+'\n','test/'+path.with_suffix('.txt').name,files,original=path.name,golden=golden,context=['json',json.loads(j)])
for path in [test/'name.txt',test/'bad.txt',test/'loop1.txt',test/'loop2.txt',test/'missing.txt',
             root/'core/src/test/resources/templates/files/main.txt',root/'core/src/test/resources/templates/files/bom.txt',
             root/'core/src/test/resources/templates/files/invalid.txt']:
 add('compile-file',str(path.relative_to(root)))
# Extract all six exact parser-error assertions from the retained original source.
unit=(test/'test.hs').read_text();quoted=r'"(?:[^"\\]|\\.)*"'
for block in unit.split('testCase ')[1:7]:
 name=json.loads(re.match(quoted,block)[0]);m=re.search(r'compileTemplate\s+('+quoted+r')\s+('+quoted+r')',block)
 expected=re.search(r'Left\s+('+quoted+r')',block)
 compile(json.loads(m[2]),json.loads(m[1]),files,originalUnit=name,expectedError=json.loads(expected[1]))
compile('$-- hi','foo',originalUnit='comment with no newline',golden='',context=['json',{}])
compile('not breakable and$~$ this is breakable\nok? $foo$$~$','foo',originalUnit='reflow',width=10,
        golden='not breakable and\nthis is\nbreakable\nok? 42',context=['json',{'foo':'42'}])
compile('$foo/nowrap$\n$foo$','foo',originalUnit='nowrap pipe',width=10,
        golden='hello this is a test of the wrapping\nhello this\nis a test\nof the\nwrapping',
        context=['map',[['foo',['simple',['hsep',['hello','this','is','a','test','of','the','wrapping']]]]]])
compile('$one/alpha$ $twentysix/alpha$ $twentyseven/alpha$ $zero/alpha$','foo',originalUnit='alpha pipe',golden='a z a 0',context=['json',{'one':1,'twentysix':26,'twentyseven':27,'zero':0}])
for name,text,variables in [
 ('getVariables','$b$$a$$if(foo.bar)$$b$$else$$baz$$endif$',['b','a','foo.bar','baz']),
 ('loop-bound variables','$for(employee)$$employee.name$: $it.salary$ $company$$sep$$separator$$endfor$',['employee','company','separator']),
 ('bracketed loop','$items[, ]$',['items']),('nested loops','$for(a)$$for(a.b)$$it$$a.c$$x$$endfor$$endfor$',['a','x']),
 ('it outside loops','$it$',['it'])]:compile(text,'',originalUnit=name,variables=variables)

for start in range(0,0x110000,4096):add('char-flags',[start,min(start+4096,0x110000)])
paths=['','main','main.txt','.hidden','a.b.c','./main.txt','/main.txt','//main.txt','dir/main.txt','dir/','../dir/a.tex','a//b.txt','C:','C:main.txt','C:/main.txt',r'C:\main.txt',r'\\share',r'\\share\name.txt',r'\\?\UNC\share\name.txt',r'\\?\A:foo.txt','//?/A:','//?/A:/foo.txt','//?/UNC/share','//?/UNC/share/','😀/中.txt']
names=['','foo','foo.txt','foo.','.foo','..','../foo','a/b','/foo','//foo','/foo.txt',r'\foo',r'\foo.txt',r'a\b',r'\\share\foo',r'\\share\foo.txt','C:foo','C:foo.txt','a./b','😀','中.tex']
for style,path,name in itertools.product(['posix','windows'],paths,names):add('partial-path',[style,path,name])
rng=random.Random(0xD0C112)
for _ in range(2200):
 add('partial-path',[rng.choice(['posix','windows']),rng.choice(paths)+''.join(rng.choice('abC:/\\.?_') for _ in range(rng.randrange(12))),rng.choice(names)])

atoms=['','plain','x y\nz','\r','\r\n','\t','$$','$','$x$','${x}','$ x $','$it$','$x.y$','$x[, ]$',
       '$if(x)$yes$else$no$endif$','$for(x)$$it$$sep$, $endfor$','$~$a b c$~$','$^$a\n b\nz','$-- comment\n',
       '  $x$\n','$foo()$','$x:foo()[, ]/uppercase$','${foo()}','$x/left 3 "|" "|"$',
       '$sep$','$if(x$and$endif$','$x/nope$','$x/left$','$x/left a$','$x..y$','$x.','$${x}','$for(x)$a$endif$']
for text in atoms:
 for prefix,suffix in [('', ''),('a','b'),('\t','\n'),('\r\n ','\r\n'),('$$','$$'),('$if(x)$','$endif$')]:compile(prefix+text+suffix)
for pipe in ['uppercase','lowercase','pairs','length','alpha','roman','reverse','first','last','rest','allbutlast','chomp','nowrap','left','right','center','bad','UPPERCASE','ß','𐐀']:
 for ending in ['$',' 3$',' 0 "[" "]"$','\t8 "a\\\"b" "c\\d"$',' 9223372036854775807$',' 9223372036854775808$',' 18446744073709551616$',' ١$',' １$',' "bad$']:
  compile('$x/'+pipe+ending)
for cp in [0,1,9,10,13,14,31,32,39,34,36,92,127,128,160,0x301,0x2160,0x1c89,0x105c0,0x1e4d0,0x1fae9,0x200b,0x2028,0x2029,0x1f600,0x10ffff]:
 for text in ['$'+chr(cp)+'$','$x'+chr(cp)+'$','$x/'+chr(cp)+'$','${'+chr(cp)+'}','$x/left'+chr(cp)+'3$']:
  compile(text,'file😀\n".txt')
for text in [''.join(rng.choice(atoms) for _ in range(rng.randrange(1,8))) for _ in range(2500)]:compile(text)
for _ in range(1200):compile(''.join(rng.choice('$ {}[]()./:~^\r\n\tabcxyz012_\\"-') for _ in range(rng.randrange(1,90))))
partial_sets=[[],[['foo.txt','value\n']], [['foo.txt','a\r\n']], [['foo.txt','$x$']], [['foo.txt','$-- c\nlast']],
 [['foo.txt','$if(x)$bad']], [['foo.txt','$foo()$']], [['foo.txt','$bar()$'],['bar.txt','$foo()$']],
 [['dir/foo.txt','$sub/bar()$'],['dir/sub/bar.txt','$last()$'],['dir/last.txt','LAST']],
 [['foo.txt','$~$a b'],['bar.txt','c d']], [['foo.txt','$^$x\n y\nz']]]
for partials in partial_sets:
 for text in ['$foo()$','$foo()/uppercase$','$x:foo()[, ]$','  $foo()$\n','$foo()$\n$bar()$','$foo()','$foo()$$foo()$','${foo()}','pre $foo()$ end']:
  compile(text,'dir/main.txt' if partials and partials[0][0].startswith('dir/') else 'main.txt',partials)
for depth in [1,2,3,10,49,50,51,52,60]:
 compile('$p0()$','main.txt',[[f'p{i}.txt',f'$p{i+1}()$'] for i in range(depth)]+[[f'p{depth}.txt','END']],depth=depth)
# Long flat input, deep syntax and backupSourcePos after escaped dollars.
for depth in [1,10,100,500]:compile('$if(x)$'*depth+'body'+'$endif$'*depth,deep=depth)
for count in [10,1000,10000]:compile('$$'*count+'$nope/unknown$',flat=count)

def core(parser,text='',name='',user=0,**meta):add('parsec-run',[parser,name,text,user],**meta)
char=lambda c:['char',ord(c)]
for text in ['', 'a','ab','a b','boot','bool','booz','boomerang','return','\t\r\n😀','x']:
 for parser in [['eof'],['token'],['sat','any'],['many',char('a')],['look',['many1',['sat','alpha']]],
                ['choice',['string','boot'],['string','bool']],['choice',['string-atomic','boot'],['string-atomic','bool']],
                ['label',['choice',char('a'),char('b')],'letter pair'],['notchar',97],['before',['many',['sat','any']],['eof']]]:core(parser,text)
core(['then',['look',['many1',['sat','alpha']]],['fail',"'return' is a reserved keyword"]],'return',originalParsec='Bug6')
core(['choice',['then',['set-position','aaa',9,1],char('a')],['then',['set-position','zzz',1,1],char('b')]],'x',originalParsec='issue175')
for kind in ['many','many-alt']:
 core(['pair',[kind,['before',char('a'),['choice',char('b'),['pure',None]]]],['eof']],'a',regression='distinct repetition error retention')
aa=['many-chars',97];bb=['many-chars',98];empty=['pure','']
for parser in [['concat-string',aa,bb],['concat-string',empty,aa],['concat-string',aa,empty],
               ['concat-string',aa,['concat-string',empty,bb]],['concat-string',aa,['concat-string',empty,bb]],empty,
               ['concat-string',['string','a'],['string','a']],['concat-string',['string','a'],['concat-string',['string','a'],['string','a']]],
               ['concat-string',['string','a'],['concat-string',['string','a'],bb]]]:core(parser,'aabbb','Example',originalParsec='Feature80')
for atomic in [False,True]:
 for text in ['boot','bool','booz']:core(['choice',['string-atomic' if atomic else 'string','boot'],['string-atomic' if atomic else 'string','bool']],text,'Example',originalParsec='Feature150')
core(['choice',['string-atomic','boot'],['choice',['string-atomic','bool'],['then',char('b'),['many',['sat','any']]]]],'boomerang','Example',originalParsec='Feature150')
base=[['pure',None],['pure','ok'],['zero'],['fail',''],['fail','oops'],['unexpected',''],['unexpected','x'],char('a'),char('b'),
      ['string','ab'],['string-atomic','ab'],['sat','alpha'],['sat','space'],['many',char('a')],['many1',char('a')],['eof'],['notchar',97],['position'],['user']]
def random_core(depth):
 if depth==0:return rng.choice(base)
 kind=rng.choice(['try','look','label','then','before','pair','choice'])
 if kind=='label':return [kind,random_core(depth-1),rng.choice(['','A','x or y','😀'])]
 if kind in ['try','look']:return [kind,random_core(depth-1)]
 return [kind,random_core(depth-1),random_core(depth-1)]
for _ in range(2500):core(random_core(4),rng.choice(['','ab','aaa','ab b','xyz','😀','\n','\t','é']),rng.choice(['','file','😀\n"']),rng.choice([0,1,-1,2**63-1,-2**63]))
for line,col,cp in itertools.product([-2**63,-1,0,1,2**63-1],[-2**63,-1,0,1,7,8,9,2**63-1],[9,10,13,97,0x1f600]):
 core(['then',['set-position','raw"name',line,col],['pair',['sat','any'],['position']]],chr(cp))
errors=[[[name,line,col],[[rng.randrange(4),rng.choice(['','a','b','or','\n','😀'])] for _ in range(rng.randrange(8))]]
        for name,line,col in itertools.product(['','aaa','zzz','😀','\uffff'],[0,1,9],[0,1,8])]
for _ in range(300):add('error-values',[rng.choice(errors),rng.choice(errors)])
for request in cs[:]:
 if request['op']=='compile-only-map' and ('original' in request or 'originalUnit' in request or rng.randrange(19)==0):
  add('compile-only-string-map',request['input'])

def native(reqs):
 out=subprocess.run([a.oracle],input='\n'.join(json.dumps([c['op'],str(root/c['input']) if c['op']=='compile-file' else c['input']]) for c in reqs)+'\n',text=True,capture_output=True,check=True,cwd=root/'vendor/doctemplates-0.11.1')
 lines=[json.loads(l) for l in out.stdout.splitlines()];assert len(lines)==len(reqs),(len(lines),len(reqs),out.stderr)
 for req,result in zip(reqs,lines):
  assert not isinstance(result,dict) or 'exception' not in result,(req,result)
  if req['op']=='compile-file' and 'error' in result:result['error']=result['error'].replace(str(root),'<repo>')
 return lines
for start in range(0,len(cs),128):
 batch=cs[start:start+128]
 for c,expected in zip(batch,native(batch)):c['expected']=expected
for c in cs:
 if 'expectedError' in c:assert c['expected']['result']=={'error':c['expectedError']},c
 if 'variables' in c:assert c['expected']['result']['variables']==c['variables'],c
 if 'golden' in c:
  path,text,partials=c['input']
  observed,=native([dict(op='compile-map',input=[path,text,c['context'],partials])])
  assert observed['result']['rendered']['renders'][4 if c.get('width')==10 else 0][0]==c['golden'],c
m=dict(upstream='doctemplates-0.11.1/parsec-3.1.18.0/GHC-9.12.2',cases=len(cs),caseHash=digest(cs),sources=hashes,operations=dict(collections.Counter(c['op'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',filename='',mtime=0) as f:f.write(('\n'.join(json.dumps(c,ensure_ascii=False,separators=(',',':')) for c in [m]+cs)+'\n').encode())
print(f'Captured {len(cs)} original parser observations: {m["operations"]}')
