#!/usr/bin/env python3
"""Exact GHC 9.12.2 Windows CodePage bodies, compact tables and effect traces."""
import argparse,collections,gzip,hashlib,io,itertools,json,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-codepage-reference'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
vendor=root/'vendor/ghc-codepage-9.12.2';src=vendor/'libraries/ghc-internal/src/GHC/Internal/IO/Encoding'
manifest=json.loads((vendor/'SOURCE.json').read_text());assert manifest['revision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc'
for name,sha in manifest['files'].items():assert hashlib.sha256((vendor/name).read_bytes()).hexdigest()==sha,name
x=(src/'CodePage.hs').read_text();x=x[x.index('import GHC.Internal.Base'):x.rindex('#endif')];x=x.replace('import qualified GHC.Internal.IO.Encoding.CodePage.API as API','import qualified OriginalCodePageAPI as API').replace('import GHC.Internal.IO.Encoding.CodePage.Table','import OriginalCodePageTable').replace('import GHC.Internal.Windows (DWORD)','import CodePageEffects (DWORD,getConsoleCP,getACP)');a=x.index('-- Since the Win32 package');b=x.index('{-# NOINLINE currentCodePage #-}',a);x=x[:a]+x[b:]
assert (folder/'OriginalCodePage.hs').read_text()=='{-# LANGUAGE BangPatterns, CPP, MagicHash, NoImplicitPrelude, NondecreasingIndentation #-}\nmodule OriginalCodePage where\n'+x
x=(src/'CodePage/Table.hs').read_text().replace('module GHC.Internal.IO.Encoding.CodePage.Table where','module OriginalCodePageTable where');assert (folder/'OriginalCodePageTable.hs').read_text()==x
x=(src/'CodePage/API.hs').read_text();a=x.index('module GHC.Internal.IO.Encoding.CodePage.API');b=x.index('-- Required for WORDS_BIGENDIAN',a);x=x[:a]+'module OriginalCodePageAPI where\n\n'+x[b:];x=x.replace('import GHC.Internal.Foreign.C.Types','import GHC.Internal.Foreign.C.Types hiding (CWchar)').replace('import GHC.Internal.Windows hiding (LPCSTR)','import CodePageEffects').replace('import GHC.Internal.System.Posix.Internals','type CWchar = Word16');a=x.index('foreign import ccall unsafe "windows.h GetCPInfo"');b=x.index('-- | Returns a slow',a);x=x[:a]+x[b:];assert (folder/'OriginalCodePageAPI.hs').read_text()==x
initial=root/'vendor/ghc-environment-9.12.2/GHC/Internal/IO/Encoding.hs';x=initial.read_text();body=x[x.index("mkTextEncoding' ::"):x.index('\n\nlatin1_encode ::')];a=body.index('#if defined(mingw32_HOST_OS)');b=body.index('#endif',a)+len('#endif');body=body[:a]+body[a:b].split('#else')[0].split('\n',1)[1]+body[b:];assert (folder/'OriginalWindowsEncoding.hs').read_text().split("mkTextEncoding' ::",1)[1]==(body+'\n\n'+x[x.index('unknownEncodingErr ::'):]).split("mkTextEncoding' ::",1)[1]
x=(src/'CodePage.hs').read_text();body=x[x.index('{-# NOINLINE currentCodePage #-}'):x.index('\n\ncodePageEncoding ::')];staged=(folder/'OriginalLocaleCodePage.hs').read_text().split('\n where\n',1)[1];assert '\n'.join(l[2:] for l in staged.splitlines()).rstrip()==body.rstrip()
table_source=(src/'CodePage/Table.hs').read_text();tables={}
for cp,body in re.findall(r'\((\d+), SingleByteCP \{(.*?)\n   \}',table_source,re.S):
 fields={k:[int(n,16) for n in re.findall(r'\\x([0-9a-fA-F]+)',re.search(k+r' = ConvArray "(.*?)"#',body,re.S).group(1))] for k in ['decoderArray','encoderIndices','encoderValues']}
 fields['encoderMax']=int(re.search(r"encoderMax = '\\(\d+)'",body).group(1));tables[cp]=fields
assert len(tables)==29
asset=root/'core/src/main/resources/io/heapy/kotdoc/resources/codepages.json.gz';raw=json.dumps(dict(blockBitSize=6,tables=tables),separators=(',',':')).encode()
assert gzip.decompress(asset.read_bytes())==raw
sources=['tools/import-codepage-fixtures.py','licenses/GHC-base-BSD-3-Clause.txt',str(initial.relative_to(root)),str(asset.relative_to(root))]
for directory in [vendor,folder]:sources += [str(f.relative_to(root)) for f in directory.rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
target=root/'core/src/test/resources/ghc-codepage/data.jsonl.gz'
if args.check_source or args.verify_oracle:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 if args.check_source:print('Verified',len(cs),'original Windows CodePage observations, all 29 compact tables and',len(m['sources']),'source hashes');raise SystemExit
else:
 cs=[]
 def add(group,**request):cs.append(dict(group=group,request=request))
 modes=['Error','Ignore','Transliterate','Roundtrip'];cps=list(map(int,tables))
 for cp in cps:
  add('all-compact-table-indices-values-and-signed-decoder-slots',api='table',cp=cp)
  for mode,direction in itertools.product(modes,['decode','encode','representable']):
   batches=([[],list(range(256))]+[[i] for i in range(128,256)]) if direction=='decode' else [[],list(range(256)),[0,65,0x20ac,0x410,0xfe80,0xd800,0xdcff,0x1f600,0x110000,0xffffffff]]
   add('table-CString-recovery-and-representability',api='whole',cp=cp,mode=mode,direction=direction,batches=batches)
  for direction in ['decode','encode']:
   for il,os,orr in itertools.product([0,1,3],[0,1,2,3,4,8],[0,1]):
    if orr>os:continue
    xs=[65,0x80,0xff] if direction=='decode' else [65,0x20ac,0x410]
    add('table-buffer-order-empty-reset-and-output-offset',api='step',cp=cp,mode='Error',direction=direction,input=xs,left=il,right=3,capacity=os,outLeft=0,outRight=orr)
 for cp,mode in itertools.product([65001,1200,1201,12000,12001],modes):
  for direction in ['decode','encode','representable']:
   add('special-Unicode-code-page-dispatch',api='whole',cp=cp,mode=mode,direction=direction,batches=[[],[0,65,127,128,255],[0xd800,0xdcff,0x10000,0x10ffff]] if direction!='decode' else [[],[0,65,255],[255,254,0,0,65,0,0,0]])
 names=['CP1252','cp1252','cP-1-2-5-2','CP-1252','CP 1252','CP(1252)','CP((1252))','CP( 1252 )','CP1252 ','CP1252\t','CP+1252','CP','CP0','CP4294967296','CP4294968548','CP0x4e4','CP0o2344','CP0b10011100100','CP( - 1252 )','CP(1252) ','ASCII','ascii','cp367','CP367','CP819','cp819','UTF8','utf-16le','UTF-8//IGNORE','CP12.0','CP1e3','CP१२५२','CP\u00a01252','CP1252\u00a0','CP 0x4e4','CP(0x4e4)','CP1_252','CP--1','CP(-(-1252))','CP(1252)junk']
 for name,mode in itertools.product(names,modes):
  add('Windows-mkTextEncoding-dispatch-aliases-and-Word32-reads',api='whole',named=True,name=name,mode=mode,direction='decode',batches=[[],[0,65,128,255]])
 dbcs=[[65],[129],[129,64],[130,64],[131,64],[254],[255],[253],[132,64],[129,0],[129,64,130,65,66],[130,64]*7,[65]*13+[255]+[65]*7,[129,64]*8+[129]]
 points=[[65],[0x400],[0x1f600],[0x8000],[0x20ac],[0xd800],[0xdcff],[0x110000],[0xffffffff],[0x400,0x1f600,65],[0x1f600]*7,[65]*13+[255]+[65]*7]
 for direction,xss in [('decode',dbcs),('encode',points)]:
  for xs,os,maxsize,offset in itertools.product(xss,[0,1,2,3,4,7,16],[1,2,4],[0,1]):
   add('Win32-API-buffer-prefix-binary-search-and-UTF16-bridge',api='step',cp=932,mode='Error',direction=direction,input=([17]+xs+[18]) if offset else xs,left=offset,right=len(xs)+offset,capacity=os+offset,outLeft=offset,outRight=offset,max=maxsize,dirty=True)
  for mode,term in itertools.product(modes,[False,True]):
   add('Win32-CString-growth-recovery-and-default-character',api='whole',cp=932,mode=mode,direction=direction,batches=[[]]+xss,terminate=term,dirty=True)
 for direction,err,scope in itertools.product(['decode','encode'],[5,87,1113],['info','convert']):
  add('Win32-errors-info-validation-and-empty-CString-demand',api='whole',cp=0xffffffff,mode='Ignore',direction=direction,batches=[[],[65],[255]],**{('infoError' if scope=='info' else direction+'Error'):err})
 for console,acp in itertools.product([0,1,932,1252,65001,0xffffffff],[0,1251,65001,0xffffffff]):add('console-codepage-or-ACP',api='current',console=console,acp=acp)
 for n,d,l in itertools.product([0,1,2,0xffffffff],[[],[63],[63,0],[1,2,3]],[[],list(range(11)),list(range(12)),list(range(13))]):add('CPINFO-Storable-layout-and-list-length-errors',api='cpinfo',max=n,default=d,lead=l)
 for direction,xss in [('encode',[[],[65],[0xd800],[0xdcff],[0x10000],[0x10ffff],[0x110000],[0xffffffff],[65,0x1f600,0xd800,66]]),('decode',[[],[65],[0xd800],[0xdc00],[0xd800,0xdc00],[0xdc00,65],[0xd800,65],[0xd83d,0xde00,65]])]:
  for xs,os,offset in itertools.product(xss,[0,1,2,3,4,8],[0,1]):
   add('native-UTF16-bridge-raw-Char-and-surrogate-boundaries',api='utf16',direction=direction,input=[17]+xs if offset else xs,left=offset,right=len(xs)+offset,capacity=os+offset,outLeft=offset,outRight=offset)
 for direction,size,left,right in itertools.product(['byte','cwchar'],range(5),range(3),range(3)):
  add('CWchar-byte-buffer-views-and-odd-metadata-errors',api='view',direction=direction,size=size,left=left,right=right)
 for console,acp,next_console,next_acp in itertools.product([0,932,1252,65001],[0,866,1251],[0,1251],[65001,932]):
  actions=[dict(op='console',value=next_console),dict(op='acp',value=next_acp)]
  actions += [dict(op='name',mode=mode) for mode in modes]
  actions += [dict(op='console',value=console),dict(op='acp',value=acp),dict(op='direct')]
  actions += [dict(op='name',mode=mode) for mode in reversed(modes)]
  actions += [dict(op='decode',mode=mode,bytes=[128,255,65]) for mode in modes]
  add('shared-locale-CAF-lazy-demand-snapshot-and-direct-query',api='locale',console=console,acp=acp,actions=actions)
 rng=random.Random(9122)
 for i in range(800):
  direction=rng.choice(['decode','encode']);xs=rng.choices([0,65,127,129,130,131,132,253,254,255,64,66] if direction=='decode' else [0,65,255,0x400,0x410,0x8000,0x1f600,0x1f610,0xd800,0xdcff],k=rng.randrange(25));il=rng.randrange(len(xs)+1);ir=rng.randrange(il,len(xs)+1);os=rng.randrange(17);orr=rng.randrange(os+1)
  add('random-Win32-buffer-cursors-and-capacities',api='step',cp=rng.choice([0,932,999,0xffffffff]),mode='Error',direction=direction,input=xs,left=il,right=ir,capacity=os,outLeft=rng.randrange(orr+1),outRight=orr,max=rng.choice([1,2,3,4]),dirty=True)
assert args.oracle,'--oracle is required'
with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
 inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in cs).encode());inp.seek(0)
 subprocess.run([args.oracle],stdin=inp,stdout=out,check=True,timeout=180);out.seek(0);observed=[json.loads(re.sub(rb'0x[0-9a-f]+@buf',b'<ptr>@buf',line)) for line in out]
assert len(observed)==len(cs),(len(observed),len(cs))
if args.verify_oracle:
 for i,(c,o) in enumerate(zip(cs,observed)):assert c['expected']==o,(i,c,o)
 print('Independent oracle agrees on all',len(cs),'CodePage observations');raise SystemExit
for c,o in zip(cs,observed):c['expected']=o
m=dict(cases=len(cs),caseHash=digest(cs),sources=hashes(),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f:
 with gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as z:z.write(('\n'.join(json.dumps(x,separators=(',',':')) for x in [m]+cs)+'\n').encode())
print('Wrote',len(cs),'original CodePage observations;',target.stat().st_size,'bytes;',m['caseHash'])
