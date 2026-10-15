#!/usr/bin/env python3
"""Independent original HTMLState/HTMLLocal/URI observations, not Kotlin outputs."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-html-state-reference';target=root/'core/src/test/resources/html-state/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_HTML_STATE_REFERENCE'));p.add_argument('--prepared',type=Path);p.add_argument('--check-source',action='store_true');args=p.parse_args()
modules=['Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Parsing.Capabilities','Text.Pandoc.TeX']
sources=['tools/import-text-212-lowercase.py','core/src/main/resources/io/heapy/kotdoc/text-2.1.2-lowercase.json','tools/import-html-state-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/Parsec-BSD-2-Clause.txt','licenses/GHC-base-BSD-3-Clause.txt','licenses/text-BSD-2-Clause.txt','licenses/Network-uri-BSD-3-Clause.txt','licenses/Network-uri-Graham-Klyne-BSD-3-Clause.txt','licenses/Transformers-BSD-3-Clause.txt','licenses/Mtl-BSD-3-Clause.txt','vendor/text-2.1.3/CaseMapping.hs','core/src/main/resources/io/heapy/kotdoc/case-mappings.json','upstream/pandoc/pandoc.cabal','upstream/pandoc-types/pandoc-types.cabal']
fixtures=subprocess.check_output(['git','ls-files','test/*.native','test/**/*.native'],cwd=root/'upstream/pandoc').decode().splitlines()
sources+=['upstream/pandoc/'+x for x in fixtures]
sources+=sorted(str(f.relative_to(root)) for base in [folder,root/'upstream/pandoc/src',root/'upstream/pandoc-types/src',root/'vendor/parsec-3.1.18.0',root/'vendor/ghc-9.12.2',root/'vendor/text-2.1.2',root/'vendor/network-uri-2.6.4.2',root/'vendor/mtl-future-2.3.1',root/'vendor/transformers-future-0.6.1.2'] for f in base.rglob('*') if f.is_file() and 'dist-newstyle' not in f.parts and '__pycache__' not in f.parts)
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
 print('Verified',len(cs),'original HTML-state observations and',len(m['sources']),'source hashes');raise SystemExit
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
 result=json.loads(line);assert not isinstance(result,dict) or 'exception' not in result or ('scope-fail' in json.dumps(req) or ('uri-map'==req[0] and req[1]=='fail') or ('many' in json.dumps(req))),(req,result)
 return result
def add(req,group,**extra):
 cs.append(dict(request=req,expected=ask(req),group=group,**extra))
 if len(cs)%2000==0:print('Captured',len(cs),'HTML-state observations',flush=True)
position=[list(map(ord,'source')),1,1];environment=[2,False,False,False]
def run(p,stream='characters',input=None,pos=position,env=environment,changes=()):return ['run',stream,input if input is not None else [97,9,10,98],pos,list(env),list(changes),p]
def seq(*ps):return ['sequence',list(ps)]
def ch(*x):return ['change',list(x)]
def scope(p,*changes):return ['scope',list(changes),p]
def raw(s):return list(map(ord,s))
def uri(s='',a=None,p='',q='',f=''):return [raw(s),None if a is None else [raw(x) for x in a],raw(p),raw(q),raw(f)]
default=ask(['state',[]]);opts=default['readerOpts']
for n in [0,-1,-9223372036854775808,9223372036854775807]:
 for standalone in [False,True]:add(['initial',dict(opts,standalone=standalone,columns=n),n],'exact-html-initialization')
attr=['id',['one','two'],[['x','1'],['x','2']]];plain=[{'t':'Plain','c':[{'t':'Str','c':'content'}]}];meta={'t':'MetaString','c':'title'}
nested=[['quote',1],['identifier','nested'],['macro','shared',3],['log','nested'],['note-number',-9223372036854775808],['set-meta','old',meta],['future-meta-state'],['parsed-note-state','late',position],['role','custom'],['header',True,0xd800],['note','nested','body'],['key','[A]', ['/nested','title'],attr],['caption',plain[0]['c']],['substitution','[A]',plain],['citation','cite','body'],['include','nested'],['note-ref','nested'],['in-note',True],['context',True],['allow-links',False],['allow-line-breaks',False],['highlight','kotlin'],['custom-role','role',['base','html',attr]],['html-block','div'],['fenced-level',-1],['markdown',True],['example','ex',42],['next-example',9223372036854775807],['position',[[0xd800,0xdc00],0,-1]]]
changes=[['nested',x] for x in nested]+[['note','n',plain],['note-pos','n',[[0xd800,0xdc00],-9223372036854775808,9223372036854775807]],['base',uri('https:',('user:pass@','example.org',':99'),'/path','?q','#f')],['identifier','outer'],['macro','shared',-9223372036854775808],['log','first'],['log','second'],['reader-opts',dict(opts,columns=999,extensions=['smart'])],['options',dict(opts,columns=-17,extensions=['raw_html'])],['set-meta','title',meta],['position',position],['footnotes',True],['pre',True],['depth',9223372036854775807]]
for i in range(len(changes)+1):add(['state',changes[:i]],'all-html-and-nested-state-fields')
for c in changes:add(['state',[c]],'independent-html-state-record-updates')
for c in [['base',None],['clear-identifiers'],['clear-macros'],['delete-meta','old'],['delete-meta','title'],['position',None]]:add(['state',changes+[c]],'capability-updates-preserve-other-fields')
for stream,old,q,nestedq,flags in itertools.product(['characters','tokens'],range(3),range(3),range(3),[False,True]):
 env=[old,flags,not flags,flags];seed=[['nested',['quote',nestedq]],['depth',-9223372036854775808],['identifier','root'],['nested',['identifier','nested']],['nested',['log','nested']]]
 inside=seq(['quote'],['local'],ch('nested',['quote',(nestedq+1)%3]),ch('identifier','inside'),['log','inside'],ch('depth',42),['consume'],['local'])
 for kind in ['success','look','empty-fail','consumed-fail','fallback','attempt-fallback','nested']:
  p=['with-quote',q,inside]
  if kind=='look':p=['look',p]
  elif kind=='empty-fail':p=['with-quote',q,['fail','empty']]
  elif kind=='consumed-fail':p=['with-quote',q,seq(['consume'],['fail','consumed'])]
  elif kind=='fallback':p=['or',['before',p,['fail','after']],['local']]
  elif kind=='attempt-fallback':p=['or',['attempt',['before',p,['fail','after']]],['local']]
  elif kind=='nested':p=['with-quote',q,seq(['local'],['with-quote',(q+1)%3,inside],['local'])]
  add(run(seq(p,['quote'],['local']),stream,env=env,changes=seed),'reader-quote-scope-versus-nested-parser-state')
for stream,q,flags in itertools.product(['characters','tokens'],range(3),itertools.product([False,True],repeat=3)):
 env=[q,*flags]
 for p in [seq(['local'],scope(seq(['local'],['quote']),['chapter',True],['plain',True],['list',True]),['local']),seq(['with-quote',0,scope(['local'],['plain',False],['list',False])],['local']),seq(['fail-in-quote',q],['local'])]:
  add(run(p,stream,env=env),'all-local-flags-and-source-failure-message')
for stream in ['characters','tokens']:
 for p in [['pure',42],['asks-constant'],['state'],['get-options'],['change',['identifier','without-env']],['quote'],['local'],['fail-in-quote',0],['with-quote',0,['pure',42]],['with-quote',0,['quote']]]:
  add(run(['scope-fail',p],stream),'local-environment-demand-and-ignored-context')
 for n in [0,1,2,1000]:
  seed=[['log',str(i)] for i in range(n)]+[['nested',['log','nested']]]
  for p in [seq(['get-logs'],['report'],['report'],['get-logs']),['or',['before',seq(['log','speculative'],['report']),['fail','rollback']],['local']],['or',['attempt',seq(['consume'],['log','consumed'],['report'],['fail','rollback'])],['local']]]:
   add(run(p,stream,changes=seed),'separate-chronological-html-logs-and-external-effects')
 for inner,outer,b,ext in itertools.product([False,True],[False,True],[False,True],['smart','raw_html','unknown-custom']):
  seed=[['options',dict(opts,extensions=[ext] if inner else [],columns=-17)],['reader-opts',dict(opts,extensions=[ext] if outer else [],columns=999)]]
  add(run(seq(['get-options'],['guard',b,ext]),stream,changes=seed),'options-are-read-from-nested-parser-state')
 for n,q in itertools.product([0,1,2,3,1500,10000],range(3)):
  add(run(['nest',n],stream,env=[q,True,False,True],changes=[['depth',9223372036854775807],['nested',['quote',1]]]),'finite-deep-local-scopes-and-int64-unwind')
 for input in [[],[97],[9,10,0xd800,0xdc00,0x1f600]]:
  for p in [['many',['consume']],['many1',['consume']],['skip-many',['consume']],['optional',['consume']],['option','default',['consume']],['before',['many',['consume']],['eof']],['many',['pure',42]]]:
   add(run(p,stream,input=input),'reader-wrapped-parsec-combinators')
for stream,q,flags in itertools.product(['characters','tokens'],range(3),[False,True]):
 env=[q,flags,not flags,flags]
 for p in [seq(['trace'],['with-quote',(q+1)%3,scope(['trace'],['chapter',not flags],['plain',flags])],['trace']),['or',['before',scope(['trace'],['quote',(q+1)%3],['list',not flags]),['fail','rollback']],['trace']],['or',['attempt',scope(seq(['consume'],['trace'],['fail','rollback']),['quote',(q+2)%3])],['trace']],seq(['look',['with-quote',(q+1)%3,['trace']]],['trace'])]:
  add(run(p,stream,env=env,changes=changes),'scoped-reader-effects-retained-across-parser-backtracking')
 for action in ['ask-ignored','quote-ignored']:
  for p in [['scope-fail',[action]],['scope-fail',['with-quote',(q+1)%3,[action]]]]:
   add(run(p,stream),'lazy-reader-and-quote-result-adapters')
for stream,kind,n in itertools.product(['characters','tokens'],['map','bind','then','before','local'],[1500,10000,100000]):
 add(run(['deep',kind,n],stream,env=[1,True,False,True]),'finite-deep-reader-map-bind-local-composition')
for rawname,line,column in itertools.product([[],raw('source'),raw('a\n"\\\0'),[0xd800,0xdc00],[0x1f600]],[-9223372036854775808,-1,1,9223372036854775807],[0,1,9223372036854775807]):
 for stream in ['characters','tokens']:
  pos=[rawname,line,column]
  add(run(seq(['boundary'],['update-last'],['boundary'],['get-last'],['consume'],['boundary']),stream,pos=pos),'nested-last-string-position-raw-names-int64')
# Original Network.URI show assertions, preserving their literal expected strings.
uri_source=(root/'vendor/network-uri-2.6.4.2/tests/uri001.hs').read_text()
import re
def original_uri(name):
 section=uri_source.split(name+' = URI',1)[1].split('\n                }',1)[0]
 def field(n):return re.search(n+r'\s*=\s*"([^"]*)"',section)[1]
 auth=re.search(r'URIAuth "([^"]*)" "([^"]*)" "([^"]*)"',section).groups()
 return uri(field('uriScheme'),auth,field('uriPath'),field('uriQuery'),field('uriFragment'))
for name,u,field,literal in [('testShowURI01',uri(),'show',''),('testShowURI02',original_uri('ts02URI'),'show',re.search(r'ts02str = "([^"]*)"',uri_source)[1]),('testShowURI03',original_uri('ts02URI'),'full',re.search(r'ts03str = "([^"]*)"',uri_source)[1]),('testShowURI04',original_uri('ts04URI'),'show',re.search(r'ts04str = "([^"]*)"',uri_source)[1])]:
 req=['uri',u,0];expected=ask(req);assert expected[field]==raw(literal)
 cs.append(dict(request=req,expected=expected,group='original-network-uri-show-assertions',source='vendor/network-uri-2.6.4.2/tests/uri001.hs',name=name,literalField=field,literalExpected=raw(literal)))
components=['','a',':','@',':@','u@','u:p@','u:','u:@','u:p','u:p:q@','u@p','u:p@@','é','\ud800','\ud800\udc00','\U00010000','\U0001f600','\n\0','//','?','#','http:']
uris=[uri()]
for i in range(5):
 for c in components:
  u=uri()
  if i==1:u[i]=[raw(c),raw(c),raw(c)]
  else:u[i]=raw(c)
  uris.append(u)
for u in uris:
 for p in [-9223372036854775808,0,10,11,9223372036854775807]:add(['uri',u,p],'full-uri-components-show-rectify-and-predicates')
for left,right in itertools.product(uris[:48],uris[:48]):add(['uri-compare',left,right],'uri-derived-eq-and-lexicographic-char-order')
for user_info,mode,suffix in itertools.product(components,['identity','empty','constant','fail'],['','tail','\ud800\udc00']):
 add(['uri-map',mode,uri('s:',(user_info,'host',':80'),'/p','?q','#f'),raw(suffix)],'uri-user-info-callback-demand-and-raw-suffix')
for mode in ['identity','empty','constant','fail']:add(['uri-map',mode,uri(),raw('suffix')],'uri-absent-authority-does-not-call-user-map')
for cp in range(0,0x110000,65536):add(['uri-unicode',cp,min(65536,0x110000-cp)],'every-haskell-char-through-uri-and-authority-show')
rng=random.Random(0x48535441)
for i in range(1500):add(['state',[rng.choice(changes) for _ in range(rng.randrange(25))]],'generated-html-state-transitions')
for path in fixtures:
 text=(root/'upstream/pandoc'/path).read_bytes().decode('utf-8')
 add(['native-state',text],'all-original-native-documents-in-html-note-state',source='upstream/pandoc/'+path)
proc.stdin.close();assert proc.wait()==0
metadata=dict(sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),**info)
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw_file:
 with gzip.GzipFile(fileobj=raw_file,mode='wb',mtime=0) as gz:
  for value in [metadata,*cs]:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'original HTML-state observations',metadata['caseHash'])
