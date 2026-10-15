#!/usr/bin/env python3
"""Original zip-archive records/bytes/errors; no Kotlin-generated expectations."""
import argparse,atexit,collections,gzip,hashlib,itertools,json,os,random,struct,subprocess,zlib
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/zip-codec/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle',default=os.environ.get('KOTDOC_ZIP_REFERENCE'));p.add_argument('--check-source',action='store_true');args=p.parse_args()
source=(root/'vendor/zip-archive-0.5/Zip.hs').read_bytes()
assert (root/'tools/haskell-zip-reference/Codec/Archive/Zip.hs').read_bytes()==source
assert (root/'tools/haskell-zip-reference/Codec/Archive/ZipWindows.hs').read_bytes()==source.replace(b'{-# LANGUAGE CPP #-}\n',b'{-# LANGUAGE CPP #-}\n#define _WINDOWS 1\n',1).replace(b'module Codec.Archive.Zip\n',b'module Codec.Archive.ZipWindows\n').replace(b'import System.FilePath\n',b'import System.FilePath.Windows\n')
a=source.index(b'cp437table =');b=source.index(b'\n\nfromString',a)
assert (root/'tools/haskell-zip-reference/cp437-clang-cpp.txt').read_bytes()==source[a:b].replace(b'\\\n',b'').rstrip()+b'\n'
archives=sorted(str(p.relative_to(root)) for p in (root/'upstream/pandoc').rglob('*') if p.is_file() and p.suffix.lower() in {'.docx','.pptx','.odt','.epub','.zip'})
sources=['tools/import-zip-codec-fixtures.py','vendor/zip-archive-0.5/Zip.hs','licenses/zip-archive-BSD-3-Clause.txt','vendor/filepath-1.5.4.0/System/FilePath/Internal.hs','vendor/zlib-0.7.1.1/Codec/Compression/Zlib/Internal.hs']+archives
sources+=sorted(str(p.relative_to(root)) for p in (root/'tools/haskell-zip-reference').rglob('*') if p.is_file() and 'dist-newstyle' not in p.parts)
def hashes():return {p:hashlib.sha256(os.readlink(root/p).encode() if (root/p).is_symlink() else (root/p).read_bytes()).hexdigest() for p in sources}
def digest(cs):
 h=hashlib.sha256()
 for c in cs:h.update(json.dumps(c,sort_keys=True,separators=(',',':')).encode())
 return h.hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(l) for l in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 assert {c['request'][1] for c in cs if c['request'][0]=='source'}==set(archives)
 assert sum(c['group']=='dos-date-fields' for c in cs)==65536
 sweep=[c['request'] for c in cs if c['group']=='every-character-paths']
 for win,drive in itertools.product([False,True],[False,True]):
  ranges=sorted((r[2],r[3]) for r in sweep if r[1]==win and r[4]==drive)
  assert ranges==[(s,min(s+1023,0x10ffff)) for s in range(0,0x110000,1024)]
 print(f'Verified {len(cs)} original ZIP observations, all {len(archives)} original archives and {len(sources)} source hashes');raise SystemExit
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
 if len(cs)%5000==0:print('Captured',len(cs),'ZIP observations',flush=True)
def cp(s):return list(map(ord,s)) if isinstance(s,str) else s
def entry(p='file',data=(),t=0,method='NoCompression',**kw):
 e=[cp(p),method,None,str(t),0,len(data),len(data),[],[],0,0,0,list(data)]
 for k,v in kw.items():e[int(k.removeprefix('f'))]=v
 return e
def archive(es=(),signature=None,comment=()):return [list(es),signature,list(comment)]
for path in archives:add(['source',path],'original-archives')
for start in range(0,0x110000,1024):
 for win,drive in itertools.product([False,True],[False,True]):
  add(['paths',win,start,min(start+1023,0x10ffff),drive],'every-character-paths')
paths=['','file','.', '..','./file','foo/../bar','foo///bar/','/','//','///','/foo','//foo','///foo','/a/../b','/../b','a/.','a/./','a//./','C:foo','C:/foo','C:\\foo\\bar','\\\\host\\share\\file','//?/C:/file','//?/UNC/host/share/file','é:/foo','𐐀:/foo','a\0b',[0xd800],[0xdc00],[0xd800,0xdc00],[0x10000],cp('漢🙂/file')]
for win,path in itertools.product([False,True],paths):add(['entry',win,cp(path),'0',[]],'path-normalization')
for b in range(256):
 for win in [False,True]:add(['entry',win,cp('prefix/')+[b]+cp('/file'),'0',[]],'path-byte-characters')
rng=random.Random(0x05020)
for _ in range(500):
 p=[rng.choice([0,46,47,58,63,65,67,92,233,0xd800,0xdc00,0x10000]) for _ in range(rng.randrange(30))]
 for win in [False,True]:add(['entry',win,p,'0',[]],'path-fuzz')
for n in [0,1,2,16,256,32751,32752,32753,65535,65536,100000]:
 for data in [list(b'x'*n),[i%256 for i in range(n)],[rng.randrange(256) for _ in range(n)]]:
  add(['entry',False,cp('payload'),'0',data],'compression-choice')
for t in [-10**100,-1,0,315532799,315532800,315532801,315532802,1700000000,4354819197,4354819198,4354819199,10**100]:
 a=archive([entry(t=t)]);add(['encode',False,a],'epoch-clamping');add(['decode',ask(['encode',False,a])],'epoch-decoding')
base=ask(['encode',False,archive([entry()])]);central=base.index(0x50,30)
# Mutate raw DOS dates in actual original-encoded central records.
central=next(i for i in range(len(base)-3) if base[i:i+4]==[0x50,0x4b,1,2])
for date in range(65536):
 b=base.copy();b[central+14:central+16]=list(struct.pack('<H',date));b[central+12:central+14]=list(struct.pack('<H',0xffff if date%2 else 0))
 add(['decode',b],'dos-date-fields')
for win,p,sign,comment in itertools.product([False,True],paths[:26],[None,[],[0,255]],[[0],[1,2,3]]):
 e=entry(p,list(b'data'),1700000001,f4=0x12345678,f7=[0,255],f8=[65],f9=0xffff,f10=0x1234,f11=0xa1ff0000)
 a=archive([e],sign,comment);add(['encode',win,a],'complete-record-encoding')
 a2=ask(['encode',win,a]);add(['decode',a2],'complete-record-decoding')
for length in [65535,65536,65537]:
 for field in ['path','extra','comment','signature','archive-comment']:
  e=entry();a=archive([e])
  if field=='path':e[0]=[65]*length
  if field=='extra':e[7]=[0]*length
  if field=='comment':e[8]=[0]*length
  if field=='signature':a[1]=[0]*length
  if field=='archive-comment':a[2]=[0]*length
  add(['encode',False,a],'word16-length-wrap');add(['decode',ask(['encode',False,a])],'wrapped-length-decoding')
for n in range(len(base)+1):add(['decode',base[:n]],'every-truncation')
for offset in range(len(base)):
 for value in [0,255]:
  b=base.copy();b[offset]=value;add(['decode',b],'header-byte-mutations')
for method,flags,compressed_size,offset in itertools.product([0,8,9,99],[0,1,8,64,65,73,0x800],[0,1,0xffffffff],[0,1,0xffffffff]):
 b=base.copy();b[central+10:central+12]=list(struct.pack('<H',method));b[central+8:central+10]=list(struct.pack('<H',flags));b[central+20:central+24]=list(struct.pack('<I',compressed_size));b[central+42:central+46]=list(struct.pack('<I',offset))
 add(['decode',b],'central-field-validation')
# File names: both encoding branches, every byte, malformed UTF8 sequences.
for name in [[i] for i in range(256)]+[[0xc0,0x80],[0xed,0xa0,0x80],[0xf4,0x90,0x80,0x80],[0xe2,0x28,0xa1]]:
 for flag in [0,0x800]:
  b=ask(['encode',False,archive([entry('x'*len(name))])]);c=30+len(name)
  b[c+8:c+10]=list(struct.pack('<H',flag));b[c+46:c+46+len(name)]=name
  add(['decode',b],'cp437-and-utf8')
# Actual descriptor streams; input generation does not compute expected decoding.
for data in [b'',b'plain',b'a'*1000,b'PK\x07\x08inside',b'PPK\x07\x08overlap']:
 for method,signed,delta in itertools.product([0,8],[False,True],[0,1]):
  compressed=data if method==0 else zlib.compress(data,wbits=-15)
  b=bytearray(ask(['encode',False,archive([entry('x',compressed,method='Deflate' if method else 'NoCompression',f6=len(data))])]))
  c=31+len(compressed);descriptor=(b'PK\x07\x08' if signed else b'')+struct.pack('<III',zlib.crc32(data),len(compressed)+delta,len(data))
  b[6:8]=struct.pack('<H',8);b[18:22]=b'\0'*4;b[c+8:c+10]=struct.pack('<H',8)
  b[c:c]=descriptor;add(['decode',list(b)],'data-descriptors')
for method in ['NoCompression','Deflate']:
 for payload in [[],[0],[255],[3,0],list(zlib.compress(b'contents',wbits=-15)),list(zlib.compress(b'x'*100,wbits=-15))+[1,2,3]]:
  e=entry(data=payload,method=method);add(['contents',e],'entry-contents')
  for size in [0,1,11,12,13,100]:
   e=entry(data=([0,255]*((size+1)//2))[:size],method=method,f2=0)
   add(['decrypt',cp('password'),e],'decryption-invalid')
# Independent encrypted inputs, decoded by actual original fromEncryptedEntry.
def encrypt(password,data):
 keys=[0x12345678,0x23456789,0x34567890]
 def crc(k,b):
  v=k^b
  for _ in range(8):v=(v>>1)^(0xedb88320 if v&1 else 0)
  return v
 def update(b):
  keys[0]=crc(keys[0],b);keys[1]=((keys[1]+(keys[0]&255))*134775813+1)&0xffffffff;keys[2]=crc(keys[2],keys[1]>>24)
 for b in password:update(b&255)
 out=[]
 for b in data:
  t=keys[2]|2;out.append(b^((((t*(t^1))&0xffffffff)>>8)&255));update(b)
 return out
for password,method in itertools.product([[],cp('password'),[0,255,0xd800,0x10000]],['NoCompression','Deflate']):
 plain=b'secret contents'*10;payload=plain if method=='NoCompression' else zlib.compress(plain,wbits=-15)
 encrypted=encrypt(password,list(range(12))+list(payload));e=entry(data=encrypted,method=method,f2=11)
 for p in [password,password+[1]]:add(['decrypt',p,e],'decryption-valid')
for mode in [0,0xa0000000,0xa1ff0000,0xf0000000,0x81ff0000]:add(['symlink',entry(data=list(b'../target\xff'),f11=mode)],'symlink-metadata')
for win,a,b in itertools.product([False,True],paths[:26],paths[:26]):
 es=[entry(a,[1]),entry(b,[2]),entry(a,[3])];ar=archive(es,[1],[2])
 add(['ops',win,ar,[['delete',cp(a)],['add',entry(b,[4])]]],'archive-updates')
 add(['find',win,ar,cp(a)],'archive-find')
for _ in range(500):add(['decode',[rng.randrange(256) for _ in range(rng.randrange(200))]],'binary-fuzz')
m=dict(cases=len(cs),sources=hashes(),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)),oracle='Complete unchanged retained zip-archive 0.5; explicit Windows CPP/import adapter; GHC 9.12.2/aarch64 macOS clang CPP (including line-spliced CP437 literal), original binary/zlib/time, complete field/byte and original archive SHA256 observations')
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as z:
 for c in [m]+cs:z.write((json.dumps(c,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print('Captured',len(cs),'ZIP observations; SHA',m['caseHash'],flush=True)
