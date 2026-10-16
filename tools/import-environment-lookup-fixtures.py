#!/usr/bin/env python3
"""Observe pinned GHC POSIX/Windows getEnv, wide conversion, error and C maperrno."""
import argparse, collections, gzip, hashlib, itertools, json, re, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]; folder=root/'tools/haskell-environment-lookup-reference'
target=root/'core/src/test/resources/ghc-environment-lookup/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
base=root/'vendor/ghc-environment-lookup-9.12.2';manifest=json.loads((base/'SOURCE.json').read_text())
assert manifest['ghcRevision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc' and manifest['mingwTag']=='v12.0.0'
for path,digest in manifest['files'].items():assert hashlib.sha256((base/path).read_bytes()).hexdigest()==digest,path
env=root/'vendor/ghc-environment-9.12.2/GHC/Internal/System/Environment.hs'
wide=root/'vendor/ghc-environment-9.12.2/GHC/Internal/Foreign/C/String.hs'
errno_source=root/'vendor/ghc-network-9.12.2/libraries/ghc-internal/src/GHC/Internal/Foreign/C/Error.hs'
s=env.read_text();get=s[s.index('getEnv ::'):s.index('-- | Return the value')];lookup=s[s.index('lookupEnv ::'):s.index('ioe_missingEnvVar ::')];missing=s[s.index('ioe_missingEnvVar ::'):s.index('-- | @setEnv')]
posix_get=get[:get.index('#if defined(mingw32_HOST_OS)')]+get.split('#else\n',1)[1].split('#endif',1)[0]
windows_get=get[:get.index('#if defined(mingw32_HOST_OS)')]+get.split('#if defined(mingw32_HOST_OS)\n',1)[1].split('foreign import',1)[0]
posix_lookup='lookupEnv :: String -> IO (Maybe String)\n'+lookup.split('#else\n',1)[1].split('foreign import',1)[0]
windows_lookup='lookupEnv :: String -> IO (Maybe String)\n'+lookup.split('#if defined(mingw32_HOST_OS)\n',1)[1].split('foreign import',1)[0]
for name,body in [('OriginalPosixGetEnv.hs',posix_get+'\n'+posix_lookup+'\n'+missing),('OriginalWindowsGetEnv.hs',windows_get+'\n'+windows_lookup+'\n'+missing)]:
    assert (folder/name).read_text().split('getEnv ::',1)[1]==body.split('getEnv ::',1)[1],name
s=wide.read_text().split('#if defined(mingw32_HOST_OS)\n\n-- On Windows',1)[1];s=s[s.index('cWcharsToChars ='):s.index('#else /* !mingw32_HOST_OS */')]
assert (folder/'OriginalWindowsWide.hs').read_text().split('cWcharsToChars =',1)[1]==s.split('cWcharsToChars =',1)[1]
s=(base/'GHC/Internal/Windows.hs').read_text();s=s[s.index('throwGetLastError ::'):s.index('failIf ::')].replace('GHC.Internal.Numeric.showHex','Numeric.showHex')
assert (folder/'OriginalWindowsErrors.hs').read_text().split('throwGetLastError ::',1)[1]==s.split('throwGetLastError ::',1)[1]
s=errno_source.read_text();fn=s[s.index('errnoToIOError  ::'):]
assert (folder/'OriginalWindowsErrno.hs').read_text().split('errnoToIOError  ::',1)[1]==fn.split('errnoToIOError  ::',1)[1]
nums={k:int(v) for k,v in re.findall(r'^#define (E\w+)\s+(\d+)\s*$',(base/'mingw/errno.h').read_text(),re.M)};nums['EOK']=0
guards=re.findall(r'errno == (e\w+)\s+= (\w+)',fn)
for constant,_ in guards:
    assert f'{constant} = Errno ({nums.get("E"+constant[1:],-1)})' in (folder/'OriginalWindowsErrno.hs').read_text()
source=(base/'cbits/Win32Utils.c').read_text()
part=source[source.index('struct errentry'):source.index('void maperrno(void)')]+source[source.index('int maperrno_func(DWORD'):source.index('LPWSTR base_getErrorMessage')]
assert (folder/'OriginalMapErrno.c').read_text().split('struct errentry',1)[1]==part.split('struct errentry',1)[1]
win={k:int(v) for k,v in re.findall(r'^#define (ERROR_\w+) __MSABI_LONG\((\d+)\)',(base/'mingw/winerror.h').read_text(),re.M)}
def error_type(n):return next((t for c,t in guards if nums.get('E'+c[1:],-1)==n),'OtherError')
entries=re.findall(r'\{\s*(ERROR_\w+),\s*(E\w+)\s*\}',source)
asset=dict(table=[[win[w],nums[e],error_type(nums[e])] for w,e in entries],accessRange=[win['ERROR_WRITE_PROTECT'],win['ERROR_SHARING_BUFFER_EXCEEDED']],execRange=[win['ERROR_INVALID_STARTING_CODESEG'],win['ERROR_INFLOOP_IN_RELOC_CHAIN']],access=[nums['EACCES'],error_type(nums['EACCES'])],exec=[nums['ENOEXEC'],error_type(nums['ENOEXEC'])],default=[nums['EINVAL'],error_type(nums['EINVAL'])])
asset_path=root/'core/src/main/resources/io/heapy/kotdoc/errors/windows-errors.json'
assert json.loads(asset_path.read_text())==asset
assert (root/'licenses/mingw-w64-header-notices.txt').read_bytes()==(base/'mingw/COPYING').read_bytes()+b'\n'+(base/'mingw/DISCLAIMER.PD').read_bytes()
sources=[str(f.relative_to(root)) for d in [base,folder] for f in d.rglob('*') if f.is_file()]
sources += [str(f.relative_to(root)) for f in [env,wide,errno_source,asset_path,Path(__file__).resolve(),root/'licenses/mingw-w64-header-notices.txt',root/'vendor/ghc-environment-9.12.2/SOURCE.json',root/'licenses/GHC-base-BSD-3-Clause.txt']]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
    assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
    assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
    print('Verified',len(cs),'original environment-lookup/error observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
cs=[]
def add(group,api,**v):cs.append(dict(group=group,request=dict(api=api,**v)))
names=[[],[65],[65,0,66],[0],[0x41,0xd800,0x1f600],[0xdcff,255,0x10ffff]]
values=[None,[],[65,0,66],[255,97],[0xc0,0x80,0xed,0xa0,0x80],[0xf0,0x9f,0x98,0x80],[254,255,0,65],[255,254,65,0]]
encodings=['UTF-8','ASCII','ISO-8859-1','UTF-16','UTF-16LE','UTF-32BE']
for foreign,filesystem,name,value,api in itertools.product(encodings,encodings,names,values,['posixGet','posixLookup']):
    add('POSIX-distinct-name-IGNORE-value-ROUNDTRIP-C-NUL-and-missing',api,**{'foreign':foreign,'filesystem':filesystem},name=name,value=value)
for field,value in itertools.product(['foreignFailure','queryFailure','filesystemFailure'],[None,[],[255]]):
    for failure in ['io:injected','fatal-injected']:
        add('POSIX-effect-failure-order-and-lazy-filesystem-encoding','posixGet',**{'foreign':'UTF-8','filesystem':'UTF-8',field:failure},name=[65],value=value)
units=[[],[65],[65,0,66],[0xd800],[0xdcff],[0xd800,0xdc00],[0x1f600&65535],[0xffff,0xd800,0xdc00,10]]
for name,unit,api in itertools.product(names,units,['windowsGet','windowsLookup']):
    add('Windows-name-and-result-UTF16-pairs-surrogates-and-NUL',api,name=name,replies=[dict(result=max(1,len(unit)),units=unit)])
for result,api in itertools.product([1,255,256],['windowsGet','windowsLookup']):
    add('Windows-return-value-equal-to-buffer-size-does-not-grow',api,name=[65],replies=[dict(result=result,units=[66])])
for sequence in [[257,1],[257,512,4],[1000,1001,65535,3]]:
    add('Windows-repeated-buffer-growth-with-racing-required-size','windowsGet',name=[65,0x1f600],replies=[dict(result=n,**(dict(units=[65,0xd83d,0xde00]) if i==len(sequence)-1 else {})) for i,n in enumerate(sequence)])
for errors,message,api in itertools.product([[203],[0],[6],[6,5],[124],[-1]], [None,[],[65,32,13,10],[0xd800,32]], ['windowsGet','windowsLookup']):
    add('Windows-zero-result-missing-empty-second-GetLastError-and-message-ownership',api,name=[65,0,66],replies=[dict(result=0)],errors=errors,message=message)
for code in list(range(4097))+[-1,-2**63,2**63-1,2**32,0xffffffff]:add('original-C-maperrno-every-code-through-4096-and-DWORD-wrap','map',code=code)
spaces=[9,10,11,12,13,32,0x85,0xa0,0x1680,0x2000,0x2001,0x2007,0x2028,0x2029,0x202f,0x205f,0x3000]
for code,message in itertools.product([0,1,2,6,7,10,17,33,109,124,145,188,202,203,206,1816,0xffffffff],[None,[],[65,13,10],[0xd800,65,0xdcff,32]]+[[65,p] for p in spaces]):
    add('Windows-original-error-record-errno-message-trim-fallback-and-free','windowsError',code=code,message=message)
for key,tag in itertools.product(['queryFailure','formatFailure'],['io:injected','fatal-injected']):
    add('Windows-native-effect-failure-boundaries','windowsGet',name=[65],replies=[dict(result=0)],errors=[6],message=[65],**{key:tag})
process=subprocess.run([args.oracle],input=''.join(json.dumps(c['request'])+'\n' for c in cs),text=True,capture_output=True)
assert process.returncode==0,process.stderr
observed=[json.loads(s) for s in process.stdout.rstrip('\n').split('\n')];assert len(observed)==len(cs)
for c,r in zip(cs,observed):c['expected']=r
m=dict(upstream='GHC 9.12.2 original platform-selected getEnv/lookupEnv, wide conversion, Windows errors, errnoToIOError and original C maperrno with pinned mingw-w64 v12.0.0 headers; controlled OS effects; real GHC codecs',cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for v in [m]+cs:gz.write((json.dumps(v,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
