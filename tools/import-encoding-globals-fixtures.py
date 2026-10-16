#!/usr/bin/env python3
"""Pinned original mkGlobal, lazy initial encodings and real GHC global consumers."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-encoding-globals-reference'
target=root/'core/src/test/resources/ghc-encoding-globals/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
source=root/'vendor/ghc-environment-9.12.2/GHC/Internal/IO/Encoding.hs';s=source.read_text()
body=s[s.index('mkGlobal ::'):s.index('-- | @since base-4.5.0.0\ninitLocaleEncoding')]
bindings=s[s.index('(getLocaleEncoding, setLocaleEncoding)'):s.index('\nmkGlobal ::')]
staged=(folder/'OriginalEncodingGlobals.hs').read_text()
assert staged.split('mkGlobal ::',1)[1].split('windowsArgvEncoding ::',1)[0]==body.split('mkGlobal ::',1)[1]
assert '\n'.join(line[2:] for line in staged.split('\n where\n',1)[1].split('\n\nmkGlobal ::',1)[0].splitlines())==bindings.rstrip()
assert 'argvEncoding = return utf8' in s and 'windowsArgvEncoding = return utf8' in staged
assert 'argvEncoding = getFileSystemEncoding' in s and 'posixArgvEncoding globals = getFileSystemEncoding' in staged
factory=s[s.index("mkTextEncoding' ::"):s.index('\n\nlatin1_encode ::')]
a=factory.index('#if defined(mingw32_HOST_OS)');b=factory.index('#endif',a)+len('#endif')
factory=factory[:a]+factory[a:b].split('#else\n',1)[1].rsplit('#endif',1)[0]+factory[b:]
unknown=s[s.index('unknownEncodingErr ::'):]
staged=(folder/'OriginalInitialEncoding.hs').read_text()
assert staged.split("mkTextEncoding' ::",1)[1]==(factory+'\n\n'+unknown).split("mkTextEncoding' ::",1)[1]
manifest=json.loads((root/'vendor/ghc-environment-9.12.2/SOURCE.json').read_text());assert manifest['revision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc'
for name,sha in manifest['files'].items():assert hashlib.sha256((root/'vendor/ghc-environment-9.12.2'/name).read_bytes()).hexdigest()==sha,name
network_manifest=json.loads((root/'vendor/ghc-network-9.12.2/SOURCE.json').read_text())
assert network_manifest['revision']==manifest['revision']
for name,sha in network_manifest['files'].items():assert hashlib.sha256((root/'vendor/ghc-network-9.12.2'/name).read_bytes()).hexdigest()==sha,name
assert (root/'licenses/GHC-base-BSD-3-Clause.txt').read_bytes()==(root/'vendor/ghc-environment-9.12.2/LICENSE.txt').read_bytes()
sources=['tools/import-encoding-globals-fixtures.py','tools/haskell-environment-reference/NativeEnvironment.c','licenses/GHC-base-BSD-3-Clause.txt']
for d in ['vendor/ghc-environment-9.12.2','vendor/ghc-network-9.12.2','tools/haskell-encoding-globals-reference']:
 sources += [str(f.relative_to(root)) for f in (root/d).rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source or args.verify_oracle:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 if args.check_source:
  print('Verified',len(cs),'original GHC encoding-global observations and',len(m['sources']),'source hashes');raise SystemExit
else:
 cs=[]
 def add(group,actions,charset='UTF-8',windows=False,**other):cs.append(dict(group=group,request=dict(api='controlled',charset=charset,windows=windows,actions=actions,**other)))
 slots=['locale','filesystem','foreign'];bases=['UTF-8','ASCII','ISO-8859-1','UTF-16LE','UTF-32BE'];modes=['','//IGNORE','//TRANSLIT','//ROUNDTRIP'];encodings=[b+m for b,m in itertools.product(bases,modes)]
 def use(op,slot='filesystem',source='current',**kw):return dict(op=op,slot=slot,source=source,**kw)
 def install(slot,encoding='UTF-8',tag='new',**kw):return dict(op='install',slot=slot,encoding=encoding,tag=tag,**kw)
 for charset,windows,order in itertools.product(bases,[False,True],itertools.permutations(slots)):
  actions=[use('read',slot,key=slot) for slot in slots]+[dict(op='charset',value='ISO-8859-1')]
  for slot in order:
   actions += [use('name',saved=slot),use('decode',slot,bytes=[255,65]),use('encode',slot,points=[0xdcff,255,0xd800],terminate=True)]
  actions += [use('name',source='argv')]
  add('lazy-read-shared-charset-first-demand-initial-modes-and-argv',actions,charset,windows)
 for slot,first,second,windows in itertools.product(slots,encodings,encodings,[False,True]):
  actions=[install(slot,first,'first'),use('read',slot,key='snapshot'),install(slot,second,'second'),use('read',slot,key='second'),
   use('name',saved='snapshot'),use('decode',saved='snapshot',bytes=[255,0xc3,0xbf,0]),use('encode',saved='second',points=[255,0xdcff,0xd800,0x1f600],terminate=False),
   use('name',source='argv'),use('name',slot),dict(op='restoreInitial',slot=slot),use('name',slot)]
  add('independent-refs-snapshots-lazy-setters-restoration-and-all-recovery-modes',actions,windows=windows)
 for slot,failure,windows in itertools.product(slots,['io:initial-failed','fatal-initial-failed'],[False,True]):
  actions=[use('read',slot,key='initial'),install(slot,'ASCII','override'),use('name',slot),use('name',saved='initial'),use('name',saved='initial'),
   use('representable',saved='initial',point=65),use('name',slot),use('name',source='argv')]
  add('initial-failure-memoization-initial-alias-and-strict-charIsRepresentable',actions,windows=windows,charsetFailure=failure)
 for slot,failure,windows in itertools.product(slots,['io:installed-failed','fatal-installed-failed',None],[False,True]):
  actions=[install(slot,'no-such-encoding','broken',**({'failure':failure} if failure else {})),use('read',slot,key='broken'),install(slot,'UTF-8','replacement'),
   use('name',saved='broken'),use('name',saved='broken'),use('representable',saved='broken',point=65),use('name',slot),use('name',source='argv')]
  add('setter-never-forces-value-synchronous-failure-cached-and-replacement-independent',actions,windows=windows)
 for charset,windows in itertools.product(['no-such-encoding','UTF-8'],[False,True]):
  actions=[use('name',slot) for slot in slots]+[dict(op='charset',value='ASCII')]+[use('name',slot) for slot in slots]
  add('shared-initial-charset-memoization-and-unknown-encoding-records',actions,charset,windows)
 for windows in [False,True]:
  actions=[install('filesystem',failure='fatal-filesystem'),use('read',source='argv',key='argv'),install('filesystem','ISO-8859-1','filesystem-latin'),
   use('name',saved='argv'),use('decode',source='argv',bytes=[255]),use('decode',saved='argv',bytes=[255]),use('name','locale'),use('name','foreign')]
  add('Windows-argv-is-UTF8-and-POSIX-argv-is-current-filesystem-snapshot',actions,windows=windows)
 # Native calls use actual process-wide GHC refs and actual libc environ.
 records=[[65,61,255],[66,61,195,191],[75,255,61,254],[69,61]]
 for foreign,filesystem,locale in itertools.product(encodings,encodings,['ASCII','ISO-8859-1']):
  actions=[dict(op='set',slot=slot,encoding=enc) for slot,enc in [('foreign',foreign),('filesystem',filesystem),('locale',locale)]]
  actions += [dict(op='environment'),dict(op='lookup',name=[65]),dict(op='lookup',name=[75,255]),dict(op='lookup',name=[65,0,66]),dict(op='lookup',name=[69]),dict(op='lookup',name=[90]),
   dict(op='foreignEncode',points=[0,255,0xdcff,0xd800,65]),dict(op='foreignDecode',bytes=[0,255,195,191]),dict(op='errno',code=13)]
  cs.append(dict(group='actual-GHC-shared-settings-affect-environment-getenv-and-foreign-C-strings',request=dict(api='native',records=records,actions=actions,posixOnly=True)))
 for recs,mode in itertools.product([None,[],[[65,61,255],[66,61,254]]],['io:poison','fatal-poison','during']):
  setup=dict(op='setDeferred',slot='filesystem',encoding='UTF-8//ROUNDTRIP',**(dict(during='ISO-8859-1') if mode=='during' else dict(failure=mode)))
  actions=[dict(op='set',slot='foreign',encoding='UTF-8//IGNORE'),setup,dict(op='lookup',name=[90]),dict(op='environment'),dict(op='lookup',name=[65]),dict(op='name',slot='filesystem')]
  cs.append(dict(group='actual-GHC-empty-null-environment-and-stable-snapshot-during-decoder-side-effect',request=dict(api='native',records=recs,actions=actions,posixOnly=True)))
 if not args.verify_oracle:
  actions=[dict(op='set',slot='foreign',encoding='UTF-8//IGNORE'),dict(op='setDeferred',slot='filesystem',encoding='UTF-8//ROUNDTRIP',swapSecondWithThird=True),dict(op='environment'),dict(op='environment')]
  cs.append(dict(group='actual-GHC-enumerates-all-pointers-before-demanding-encoding-CAF',request=dict(api='native',records=[[65,61,49],[66,61,50],[67,61,51]],actions=actions,posixOnly=True)))
  for failure in ['io:foreign-poison','fatal-foreign-poison']:
   actions=[dict(op='set',slot='filesystem',encoding='UTF-8//ROUNDTRIP'),dict(op='setDeferred',slot='foreign',failure=failure),dict(op='environment'),dict(op='lookup',name=[90]),dict(op='lookup',name=[65])]
   cs.append(dict(group='actual-GHC-getEnvironment-ignores-foreign-but-getenv-forces-it-even-when-missing',request=dict(api='native',records=records,actions=actions,posixOnly=True)))
assert args.oracle
with tempfile.TemporaryFile(mode='w+') as src,tempfile.TemporaryFile(mode='w+') as dst:
 for c in cs:src.write(json.dumps(c['request'])+'\n')
 src.seek(0);r=subprocess.run([args.oracle],stdin=src,stdout=dst,stderr=subprocess.PIPE,text=True);assert r.returncode==0,r.stderr
 dst.seek(0);observations=[json.loads(line) for line in dst]
assert len(observations)==len(cs)
if args.verify_oracle:
 for i,(c,value) in enumerate(zip(cs,observations)):assert c['expected']==value,(i,c,value)
 print('Independent oracle matched all',len(cs),'retained GHC encoding-global observations');raise SystemExit
for c,result in zip(cs,observations):c['expected']=result
m=dict(upstream='GHC 9.12.2 original mkGlobal/initial encoding dispatch; real Haskell thunk/IORef semantics; actual GHC global setters and native POSIX environment/C strings/errno records',cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for v in [m]+cs:gz.write((json.dumps(v,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
