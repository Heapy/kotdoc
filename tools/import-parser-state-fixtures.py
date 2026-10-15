#!/usr/bin/env python3
"""Independent original ParserState/Capabilities/Key observations, not Kotlin outputs."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-parser-state-reference';target=root/'core/src/test/resources/parsing/state.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_PARSER_STATE_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
modules=['Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX']
sources=['tools/import-text-212-lowercase.py','core/src/main/resources/io/heapy/kotdoc/text-2.1.2-lowercase.json','tools/import-parser-state-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Parsec-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','vendor/text-2.1.3/CaseMapping.hs','core/src/main/resources/io/heapy/kotdoc/case-mappings.json','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal']
fixtures=subprocess.check_output(['git','ls-files','test/*.native','test/**/*.native'],cwd=root/'upstream/pandoc').decode().splitlines()
sources+=['upstream/pandoc/'+x for x in fixtures]
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'upstream/pandoc/src',root/'upstream/pandoc-types/src',root/'vendor/parsec-3.1.18.0',root/'vendor/ghc-9.12.2',root/'vendor/text-2.1.2'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts and '__pycache__' not in f.parts)
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
base=(root/'upstream/pandoc/pandoc.cabal').read_text();changed=base
for name in modules:
 line='                   '+name+',\n';assert changed.count(line)==1;changed=changed.replace(line,'')
needle='  exposed-modules: Text.Pandoc,\n';assert changed.count(needle)==1
changed=changed.replace(needle,needle+''.join('                   '+name+',\n' for name in modules))
tracked=[x for x in subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0') if x]
info={'exposedModules':modules,'originalCabalHash':hashlib.sha256(base.encode()).hexdigest(),'exposedCabalHash':hashlib.sha256(changed.encode()).hexdigest(),'verifiedOriginalBuildFiles':len(tracked)}
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert all(m[k]==v for k,v in info.items())
 assert all(not isinstance(c['expected'],dict) or 'host' not in c['expected'] for c in cs)
 print('Verified',len(cs),'original parser-state observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle and args.prepared,'--oracle and --prepared required'
for name in tracked:
 expected=changed.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()
 assert (args.prepared/'pandoc'/name).read_bytes()==expected,name
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,cwd=root)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 result=json.loads(line);assert not isinstance(result,dict) or 'host' not in result,(req,result)
 return result
def add(req,group,**extra):
 cs.append(dict(request=req,expected=ask(req),group=group,**extra))
 if len(cs)%2000==0:print('Captured',len(cs),'parser-state observations',flush=True)
position=[list(map(ord,'source')),1,1]
def run(p,stream='characters',input=None,pos=position,changes=()):return ['run',stream,input if input is not None else [97,9,10,98],pos,list(changes),p]
def seq(*ps):return ['sequence',list(ps)]
def ch(*x):return ['change',list(x)]
add(['state',[]],'complete-default-record')
opts=ask(['state',[]])['options']
strings=['','[]','[ ]','[A]','[[A]]','[A B]',' [A] ','[A','A]','[A]\n','A  B','a\t\nB','[\tA\u00a0B ]','İ','ΟΣ','ẞ','Straße','Σςσ','\U00010400','\u200bA\u200b','\u2028A\u2029','\ufeff A','[\0A]','\u00a0[ABC]\u00a0']
for s,p in itertools.product(strings,[-9223372036854775808,-1,0,10,11,9223372036854775807]):add(['key',s,p],'reference-key-unbracketing-whitespace-case-show')
old=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-lowercase.json').read_text())['lower']
new=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/case-mappings.json').read_text())['lower']
# Source-version boundaries are explicit observations, not Kotlin expectations.
for cp in sorted({*old,*new},key=lambda x:int(x,16)):
 if old.get(cp)!=new.get(cp):add(['key','['+chr(int(cp,16))+']',0],'text-212-versus-213-lowercase-boundaries')
for left,right in itertools.product(strings,strings):add(['compare',left,right],'key-derived-eq-and-text-order')
reads=['Key "x"','Key"x"tail','Key ("x")','(Key "x")','((Key (("x")))) rest','Key [\'a\',\'b\']','Key []','Key [((\'a\'))]','Key "a\\n\\t\\x10ffff\\1114111" rest','Key "\\SOH\\SO\\&H"','Key "a\\ \n \\b"','Key "\\&\\&x"','Key "\\55296\\56320"','Key "\\xD800\\xDC00"','Key "\\o177"','Key "\\^@\\^_"','Key "\\O177\\X7f"','Key "\n"','Key "\\1114112"','Key "\\x110000"','Key "\\b10"','Key "\\q"','Key "\\&"','Key "\\NUL"','Key "\\SOHx"','Key "\\SOH\\&x"','Key {-comment-} "x"','Key --comment\n"x"','Keyx "x"','key "x"','Key\' "x"','Key "x" )','(Key "x"','Key ("x"','Key [\'a\',]','Key [\'\']','Key "','Key','(Key)','Key 1','Key "x"+','( Key "x" )more','Key "x"\ud800\udc00']
for r,p in itertools.product(reads,[-1,0,10,11,12,9223372036854775807]):add(['read',list(map(ord,r)),p],'key-derived-read-grammar-and-raw-remainders')
for n in [1500,10000]:
 for p in [0,11]:
  add(['read',list(map(ord,'('*n+'Key ("deep")'+')'*n+'tail')),p],'finite-deep-derived-key-read')
for r in ['[]','[Key "a"]','([Key "a", (Key "b")])tail','[Key "a",]','[Key "a" Key "b"]','[Key [\'a\']]','[Key "a"]\n','( [ ] )','[Key "\\55296\\56320"]']:
 add(['read-list',list(map(ord,r))],'key-derived-read-list')
for kind,cp,p in itertools.product([False,True],[0,7,9,10,13,14,31,32,34,39,92,127,0xd800,0xdc00,0xffff,0x10000,0x1f600,0x10ffff],[-1,0,10,11,9223372036854775807]):
 add(['header',kind,cp,p],'header-type-derived-show-and-raw-char')
for c in range(0,0x110000,65536):add(['unicode',c,min(65536,0x110000-c)],'all-char-reference-key-witnesses')
attr=['id',['one','two'],[['x','1'],['x','2']]];target_url=['/a','title'];plain=[{'t':'Plain','c':[{'t':'Str','c':'content'}]}];meta={'title':{'t':'MetaString','c':'title'}}
changes=[['options',dict(opts,columns=-9223372036854775808,standalone=True)],['quote',0],['context',True],['allow-links',False],['allow-line-breaks',False],['position',[[0xd800,0xdc00],-9223372036854775808,9223372036854775807]],['key','[A B]',target_url,attr],['header-key','A\tB',target_url,attr],['substitution','[A]',plain],['note','n','body'],['parsed-note','n',position,plain],['parsed-note-state','late',position],['note-ref','n'],['in-note',True],['note-number',9223372036854775807],['set-meta','title',meta['title']],['future-meta-state'],['citation','cite','body'],['header',False,0xd800],['header',True,0x1f600],['identifier','id'],['next-example',-9223372036854775808],['example','ex',9223372036854775807],['macro','macro',-9223372036854775808],['macro','second',9223372036854775807],['role','custom'],['highlight','kotlin'],['custom-role','raw',['base','html',attr]],['caption',plain[0]['c']],['html-block','div'],['fenced-level',-9223372036854775808],['include','a'],['include','b'],['log','first'],['log','second'],['markdown',True]]
for i in range(len(changes)+1):add(['state',changes[:i]],'all-record-fields-and-deferred-final-state')
for change in changes:add(['state',[change]],'independent-record-field-updates')
for change in [['delete-meta','title'],['future-meta',meta],['future-meta',{}],['caption',None],['highlight',None],['html-block',None],['position',None],['drop-include'],['clear-identifiers'],['clear-macros']]:add(['state',changes+[change]],'metadata-optionals-and-capability-record-updates')
for s in strings:add(['state',[['key',s,target_url,attr],['key','[A]',target_url,attr],['key','a',['/b','other'],['',[],[]]]]],'normalized-reference-key-map-replacement')
for rawname in [[],list(map(ord,'source')),list(map(ord,'a\n"\\\0')), [0xd800,0xdc00], [0x1f600]]:
 for line,column in [(1,1),(-1,0),(-9223372036854775808,9223372036854775807)]:
  for stream in ['characters','tokens']:
   pos=[rawname,line,column]
   for p in [seq(['boundary'],['update-last'],['boundary'],['consume'],['boundary']),seq(['update-last'],['set-position',position],['boundary']),seq(ch('position',pos),['boundary'])]:
    add(run(p,stream,pos=pos),'last-string-position-raw-name-and-int64')
for stream,old,inner,other in itertools.product(['characters','tokens'],range(3),range(3),[False,True]):
 modify=seq(['get-quote'],ch('quote',2),ch('next-example',42),['log','inside'],ch('include','inner'),['consume'],['get-quote']) if other else ['get-quote']
 for wrapper in ['success','look','empty-fail','consumed-fail','fallback','attempt-fallback','nested']:
  p=['with-quote',inner,modify]
  if wrapper=='look':p=['look',p]
  elif wrapper=='empty-fail':p=['with-quote',inner,['fail','empty']]
  elif wrapper=='consumed-fail':p=['with-quote',inner,seq(['consume'],['fail','consumed'])]
  elif wrapper=='fallback':p=['or',['before',p,['fail','after']],['state']]
  elif wrapper=='attempt-fallback':p=['or',['attempt',['before',p,['fail','after']]],['state']]
  elif wrapper=='nested':p=['with-quote',inner,seq(['get-quote'],['with-quote',(inner+1)%3,modify],['get-quote'])]
  add(run(seq(p,['get-quote']),stream,changes=[['quote',old]]),'quote-context-scope-backtracking-and-other-state')
 for q in range(3):add(run(['fail-in-quote',q],stream,changes=[['quote',old]]),'quote-context-failure-message')
for stream in ['characters','tokens']:
 for n in [0,1,2,20,1000]:
  logs=[['log',str(i)] for i in range(n)]
  for p in [seq(['get-logs'],['report'],['report'],['get-logs']),['or',['before',seq(['log','speculative'],['report']),['fail','rollback']],['state']],['or',['attempt',seq(['consume'],['log','consumed'],['report'],['fail','rollback'])],['state']]]:
   add(run(p,stream,changes=logs),'chronological-local-and-external-log-effects')
 for n in [0,1,2,1000]:
  includes=[['include',str(i)] for i in range(n)]
  add(run(seq(['get-includes'],ch('drop-include'),['get-includes'],ch('drop-include'),['get-includes']),stream,changes=includes),'include-stack-empty-pop-and-order')
 for ext,enabled,guard in itertools.product(['smart','raw_html','tex_math_dollars','unknown-custom'],[False,True],[False,True]):
  configured=dict(opts,extensions=[ext] if enabled else [])
  add(run(seq(['get-columns'],['guard',guard,ext]),stream,changes=[['options',configured]]),'reader-options-and-extension-guards')
for stream,n,old in itertools.product(['characters','tokens'],[0,1,2,3,1500,10000],range(3)):
 add(run(['nest-quotes',n],stream,changes=[['quote',old],['next-example',9223372036854775807]]),'deep-scoped-quotes-preserving-int64-state')
for enabled,mode,wrapper,input in itertools.product([False,True],['value','consume','fail','consume-fail'],['value','enabled','disabled','fallback','attempt-fallback'],[[],[1,2]]):
 alternate=dict(opts,columns=-9223372036854775808,extensions=['smart'] if enabled else [])
 add(['override',opts,alternate,mode,wrapper,input],'overridden-parser-valued-token-options-getter')
rng=random.Random(0x53544154)
for i in range(1500):
 cs0=[rng.choice(changes) for _ in range(rng.randrange(25))]
 add(['state',cs0],'generated-complete-record-transitions')
for i in range(1000):
 cp=rng.randrange(0x110000);r='Key '+rng.choice(['"\\'+str(cp)+'"','[\'\\'+str(cp)+'\']','("\\x'+hex(cp)[2:]+'")'])+rng.choice(['','tail','\t','\ud800\udc00'])
 add(['read',list(map(ord,r)),rng.choice([0,10,11])],'generated-derived-read-literals')
# Original Native fixtures are supplied verbatim to the original Native reader,
# not parsed or reinterpreted by this producer.
for path in fixtures:
 text=(root/'upstream/pandoc'/path).read_bytes().decode('utf-8')
 add(['native-state',text],'all-original-native-documents-in-state',source='upstream/pandoc/'+path)
proc.stdin.close();assert proc.wait()==0
metadata=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),**info)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw:
 with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as gz:
  for value in [metadata,*cs]:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original parser-state observations',metadata['caseHash'])
