#!/usr/bin/env python3
"""Original complete Pandoc Data module; real TH catalog and controlled primitive effects."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-data-reference';target=root/'core/src/test/resources/pandoc-data/data.jsonl.gz';resources=root/'core/src/main/resources/io/heapy/kotdoc/pandoc-data'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_DATA_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
original=(root/'upstream/pandoc/src/Text/Pandoc/Data.hs').read_bytes()
for name,embedded,win in [('DataEmbedded',True,False),('DataInstalled',False,False),('DataEmbeddedWindows',True,True),('DataInstalledWindows',False,True)]:
 v=original.replace(b'module Text.Pandoc.Data (',f'module Text.Pandoc.{name} ('.encode())
 if embedded:v=v.replace(b'{-# LANGUAGE CPP #-}\n',b'{-# LANGUAGE CPP #-}\n#define EMBED_DATA_FILES 1\n',1)
 if win:v=v.replace(b'import System.FilePath\n',b'import System.FilePath.Windows\n').replace(b'import Text.Pandoc.Class (',b'import Text.Pandoc.ClassWindows (').replace(b'import Text.Pandoc.Shared (makeCanonical)',b'import Text.Pandoc.SharedWindows (makeCanonical)').replace(b'import Codec.Archive.Zip\n',b'import Codec.Archive.ZipWindows\n')
 assert (folder/f'Text/Pandoc/{name}.hs').read_bytes()==v
assert (folder/'Text/Pandoc/Data/BakedIn.hs').read_bytes()==(root/'upstream/pandoc/src/Text/Pandoc/Data/BakedIn.hs').read_bytes()
cls=(root/'upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs').read_text();projected=(folder/'Text/Pandoc/Class.hs').read_text()
for start,end in [('  getCommonState ::','\n-- * Functions defined'),('report ::','-- | Run an action'),('getTimestamp ::','-- | Get the zoned time.'),('isRelativeToParentDir ::','-- | Read metadata file')]:
 a=cls.index(start);b=cls.index(end,a);assert cls[a:b] in projected,start
for module in ['Zip','ZipWindows']:assert (folder/f'Codec/Archive/{module}.hs').read_bytes()==(root/f'tools/haskell-zip-reference/Codec/Archive/{module}.hs').read_bytes()
shared=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text();projected_shared=(folder/'Text/Pandoc/Shared.hs').read_text()
for start,end in [('makeCanonical ::','-- | Remove intermediate'),('safeRead ::',None)]:
 a=shared.index(start);b=shared.index(end,a) if end else len(shared)
 assert shared[a:b] in projected_shared,start
assert (folder/'Text/Pandoc/SharedWindows.hs').read_text()==projected_shared.replace('module Text.Pandoc.Shared ','module Text.Pandoc.SharedWindows ').replace('import System.FilePath (','import System.FilePath.Windows (')
a=cls.index('isRelativeToParentDir ::');b=cls.index('-- | Read metadata file',a)
assert cls[a:b] in (folder/'Text/Pandoc/ClassWindows.hs').read_text()
sources=['tools/import-pandoc-data-fixtures.py','licenses/pandoc-GPL-2.0.md','licenses/zip-archive-BSD-3-Clause.txt','upstream/pandoc/src/Text/Pandoc/Data.hs','upstream/pandoc/src/Text/Pandoc/Data/BakedIn.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs','upstream/pandoc/src/Text/Pandoc/Class/CommonState.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/Translations.hs','upstream/pandoc/src/Text/Pandoc/Translations/Types.hs','upstream/pandoc/src/Text/Pandoc/Error.hs','upstream/pandoc/src/Text/Pandoc/Logging.hs','upstream/pandoc/src/Text/Pandoc/UTF8.hs','upstream/pandoc/src/Text/Pandoc/MediaBag.hs','upstream/pandoc/src/Text/Pandoc/MIME.hs','upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/MANUAL.txt','vendor/ghc-9.12.2/GHC/Internal/Read.hs','vendor/ghc-9.12.2/GHC/Internal/Text/Read/Lex.hs','vendor/filepath-1.5.4.0/System/FilePath/Internal.hs','core/src/test/resources/zip-codec/data.jsonl.gz','upstream/pandoc/test/docx/inline_images.docx']
sources+=sorted(str(p.relative_to(root)) for p in (root/'upstream/pandoc/data').rglob('*') if p.is_file())
sources+=sorted(str(p.relative_to(root)) for p in folder.rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
sources+=['tools/haskell-data-reference/data']
def hashes():return {s:hashlib.sha256(os.readlink(root/s).encode() if (root/s).is_symlink() else (root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 catalog=next(c['expected'] for c in cs if c['request']==['catalog'])
 names=[]
 for cps,bs in catalog:
  name=''.join(map(chr,cps));names.append(name);assert (resources/name).read_bytes()==bytes(bs),name
 assert (resources/'all-data-index.txt').read_text()==''.join(n+'\n' for n in names)
 assert {str(p.relative_to(resources)) for p in resources.rglob('*') if p.is_file()}==set(names)|{'all-data-index.txt','templates.txt'}
 assert {c['request'][1] for c in cs if c['request'][0]=='run'}=={0,1,2,3}
 print(f'Verified {len(cs)} original Data/effect observations, all {len(names)} actual TH catalog files and {len(m["sources"])} source hashes');raise SystemExit
assert args.oracle,'--oracle required'
proc=subprocess.Popen([args.oracle],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def cleanup():
 if proc.poll() is None:proc.terminate();proc.wait()
atexit.register(cleanup);cs=[]
def ask(req):
 proc.stdin.write(json.dumps(req,ensure_ascii=True)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,req
 return json.loads(line)
def add(req,group):
 cs.append(dict(request=req,expected=ask(req),group=group))
 if len(cs)%1000==0:print('Captured',len(cs),'Data observations',flush=True)
def cp(s):return list(map(ord,s)) if isinstance(s,str) else s
def ok(value,ops=()):return ['ok',value,list(ops)]
def typed(s='effect-error',ops=()):return ['typed',cp(s),list(ops)]
def host(ops=()):return ['host',list(ops)]
def spec(name,*values,args=None):return [name,None if args is None else [cp(s) for s in args],list(values)]
def run(mode,ops,specs=(),group='runtime'):add(['run',mode,ops,list(specs)],group)
add(['catalog'],'actual-TH-catalog');catalog=cs[-1]['expected'];data={''.join(map(chr,p)):b for p,b in catalog}
for name,bs in data.items():
 path=resources/name;path.parent.mkdir(parents=True,exist_ok=True)
 if path.exists():assert path.read_bytes()==bytes(bs),name
 else:path.write_bytes(bytes(bs))
(resources/'all-data-index.txt').write_text(''.join(n+'\n' for n in data))
snap=['snapshot']
# Every actual embedded byte and both platform/build branches.
for name,bs in data.items():
 for mode in range(4):
  p='installed/'+(('data\\' if mode>=2 else 'data/')+name if name!='MANUAL.txt' else name)
  specs=[spec('exists',ok(True)),spec('strict',ok(bs),args=[p])]
  run(mode,[['default',cp(name)]],specs,'all-default-files')
for mode in range(4):run(mode,[['names']],group='all-file-names')
paths=['','file','MANUAL.txt','translations/en.yaml','./translations/en.yaml','x/../translations/en.yaml','../file','x/../../file','/file','/../file','C:foo','C:/file','C:\\file','\\file','\\\\host\\share\\file','//?/C:/file','./','..','a/..','a/../..','/a/../../file','reference.docx','x/../reference.docx','missing','漢🙂','a\0b',[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000]]
for mode,directory,path in itertools.product(range(4),[None,[],cp('user'),cp('/user'),cp('C:\\user'),[0xd800,0xdc00]],paths):
 run(mode,[['user',directory],['check',cp(path)],['caught',[['read',cp(path)]]],snap],group='path-routing')
# A present user override must be strict and must not fall back after a read failure.
for mode,response in itertools.product(range(4),[ok([0,255,1]),typed(),host()]):
 run(mode,[['user',cp('user')],['caught',[['read',cp('translations/en.yaml')]]],snap],[spec('exists',ok(True)),spec('strict',response)],'user-override')
# Source fixed part lists; generate complete ZIP bytes through actual original algorithms.
for mode,kind,epoch in itertools.product(range(4),['docx','pptx','odt'],[None,'0','315532801','1700000001','4354819198','-1']):
 specs=[spec('env',ok(None if epoch is None else cp(epoch)))]
 if mode%2:
  specs+=[spec('exists',ok(True))]
  for name,bs in data.items():
   path='installed/'+('data\\' if mode>=2 else 'data/')+name
   specs.append(spec('strict',ok(bs),args=[path]))
 run(mode,[['default',cp('reference.'+kind)],snap],specs,'reference-assembly')
# Use existing actual original archive bytes for the lazy parse/re-encode path.
reference=list((root/'upstream/pandoc/test/docx/inline_images.docx').read_bytes())
for mode,kind,response in itertools.product(range(4),['docx','pptx','odt'],[ok(reference),ok([]),typed(),host()]):
 run(mode,[['user',cp('user')],['caught',[['default',cp('reference.'+kind)]]],snap],[spec('exists',ok(True)),spec('lazy',response)],'reference-override')
# Timestamp lookups happen for every part; these callbacks can change user selection.
mutate=[['user',cp('changed')],['header',cp('X'),cp('value')]]
for mode,kind,method in itertools.product(range(4),['docx','odt','pptx'],['env','time','exists','strict','fileName']):
 specs=[spec(method,ok(None if method=='env' else ['40587','0'] if method=='time' else False if method=='exists' else cp('file') if method=='fileName' else [1,2,3],mutate))]
 run(mode,[['user',cp('old')],['caught',[['default',cp('reference.'+kind)]]],snap],specs,'callback-state-order')
for mode,kind,method,response in itertools.product(range(4),['docx','odt','pptx'],['env','time','exists','strict','fileName'],[typed(ops=mutate),host(mutate)]):
 run(mode,[['user',cp('old')],['caught',[['default',cp('reference.'+kind)]]],snap],[spec(method,response)],'primitive-failures')
# Reproducible-time grammar, whitespace, parentheses, radix and fractional/leap boundaries.
epochs=['','0','-1','+1','- 1','--1','1.0','1e0','0x10','0Xf','0o10','0O7','0b101','0B1','01','(1)','(( - 0x10 ))','( - (1))','1 -- comment','{-x-}1','1_000','0x','(1) x','NaN','Infinity','1.','0b2','0o8']
epochs += [str(x) for x in [-10**100,-2**63,-1,1,2**63,10**100]]
for a,b,c in itertools.product(['',' ','\t','\n','\u00a0','\u2003'],['-','+','','(-','(('],['1','0xA','0b10','1.0','1e0']):epochs.append(a+b+c+')'*b.count('(')+a)
rng=random.Random(0x312104)
for _ in range(1000):epochs.append(''.join(rng.choice('0123456789xXoObBeE+-.() abc\t\n') for _ in range(rng.randrange(25))))
for value in epochs:
 add(['epoch',cp(value)],'safeRead-Integer')
 run(0,[['timestamp'],['posix']],[spec('env',ok(cp(value)))],'source-date-epoch')
for day,picos in itertools.product(['-1000000000000000000000','0','40586','40587','60000','1000000000000000000000'],['-1','0','1','999999999999','1000000000000','86400000000000000','86400000000000001','86401000000000000']):
 run(0,[['timestamp'],['posix']],[spec('time',ok([day,picos]))],'UTC-picoseconds')
# Directory recursion and default-user-directory IO branch/exception order.
for mode in range(4):
 slash='\\' if mode>=2 else '/';rootdir='installed'+slash+'data';child=rootdir+slash+'folder'
 specs=[spec('list',ok([cp('b'),cp('folder'),cp('.hidden')]),args=[rootdir]),spec('directory',ok(True),args=[child]),spec('list',ok([cp('c'),cp('a')]),args=[child])]
 run(mode,[['names']],specs,'directory-recursion')
 for method in ['dataDir','list','directory']:
  for result in [typed(),host()]:run(mode,[['names'],snap],[spec(method,result)],'directory-IO-failures')
for xdg,legacy,xe,le in itertools.product([ok(cp('xdg')),typed(),host()],[ok(cp('legacy')),typed(),host()],[False,True],[False,True]):
 specs=[spec('xdg',xdg),spec('legacy',legacy),spec('directory',ok(xe),ok(le))]
 run(0,[['userDefault'],snap],specs,'default-user-directory')
for result in [typed(),host()]:run(0,[['userDefault'],snap],[spec('directory',result)],'default-directory-existence-failure')
for mode in range(4):run(mode,[['translations','en-US'],snap],group='translation-integration')
# Independently exercise each source part position, not just one repeated clock.
for mode,kind,function in itertools.product(range(4),['docx','odt','pptx'],['sequence','strict-typed','strict-host','env-typed','env-host']):
 source_name={'docx':'getDefaultReferenceDocx','odt':'getDefaultReferenceODT','pptx':'getDefaultReferencePptx'}[kind]
 body=original.decode().split(source_name+' = do',1)[1].split('  let pathToEntry',1)[0]
 parts=re.findall(r'"([^"]+)"',body.split('let paths =',1)[1].split('  let toLazy',1)[0])
 base=[spec('exists',ok(False),args=['user'+('\\' if mode>=2 else '/')+'reference.'+kind]),spec('exists',ok(True))]
 if function=='sequence':
  times=[ok(cp(str(315532800+i*86401))) if i%2 else ok(None) for i in range(len(parts))]
  clocks=[ok(['60000',str(10**12*i+999999999999)]) for i in range(len(parts))]
  payloads=[ok([i,0,255]) for i in range(len(parts))]
  run(mode,[['user',cp('user')],['default',cp('reference.'+kind)],snap],base+[spec('env',*times),spec('time',*clocks),spec('strict',*payloads)],'per-part-clock-sequence')
 else:
  method,kind_of_failure=function.split('-');failure=typed() if kind_of_failure=='typed' else host()
  for position in range(len(parts)):
   normal=ok(None) if method=='env' else ok([0,1,255])
   run(mode,[['user',cp('user')],['caught',[['default',cp('reference.'+kind)]]],snap],base+[spec(method,*([normal]*position+[failure]))],'every-part-failure')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),catalogFiles=len(catalog),oracle='Complete original Data bodies in all four CPP/platform builds, real BakedIn TH embedding, original ZIP and timestamp/safeRead projections; controlled primitive effects only')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'Data observations;',len(catalog),'actual catalog files; SHA',m['caseHash'],flush=True)
