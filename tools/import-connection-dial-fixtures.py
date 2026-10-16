#!/usr/bin/env python3
"""Independent full-module crypton-connection/SOCKS wire and acquisition observations."""
import argparse, collections, gzip, hashlib, itertools, json, random, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-connection-dial-reference'
target = root / 'core/src/test/resources/connection-dial/data.jsonl.gz'
p = argparse.ArgumentParser(); p.add_argument('--oracle'); p.add_argument('--check-source', action='store_true'); args = p.parse_args()
sources = ['tools/import-connection-dial-fixtures.py', 'vendor/http-tls-sources.json',
           'licenses/crypton-socks-BSD-3-Clause.txt', 'licenses/cereal-BSD-3-Clause.txt',
           'vendor/crypton-connection-0.4.6/Network/Connection.hs', 'vendor/crypton-connection-0.4.6/Network/Connection/Types.hs',
           'vendor/network-3.2.9.0/Network/Socket/Types.hsc', 'vendor/network-3.2.9.0/Network/Socket/ReadShow.hs',
           'vendor/ghc-9.12.2/GHC/Internal/Read.hs', 'vendor/ghc-9.12.2/GHC/Internal/Text/Read/Lex.hs']
for name, version in [('crypton-socks', '0.6.2'), ('cereal', '0.5.8.3')]:
    manifest_path = root / f'vendor/{name}-sources.json'
    m = json.loads(manifest_path.read_text()); assert m['version'] == version
    base = root / f'vendor/{name}-{version}'
    assert {str(f.relative_to(base)) for f in base.rglob('*') if f.is_file()} == set(m['files'])
    for f, digest in m['files'].items(): assert hashlib.sha256((base/f).read_bytes()).hexdigest() == digest, f
    sources += [str(manifest_path.relative_to(root))] + [str(f.relative_to(root)) for f in base.rglob('*') if f.is_file()]
assert b'test-suite' not in (root / 'vendor/crypton-socks-0.6.2/crypton-socks.cabal').read_bytes()
for f in ['OriginalConnection.hs', 'OriginalConnectionTypes.hs', 'ConnectionTlsEffects.hs']:
    original = root / 'tools/haskell-connection-reference' / f
    sources.append(str(original.relative_to(root))); s = original.read_bytes()
    if f == 'OriginalConnection.hs':
        s = s.replace(b'import Network.Socks5 (', b'import OriginalSocks (').replace(b'import Network.Socket\n', b'import ConnectionSocketEffects\n')
        s = s.replace(b'import qualified Network.Socket.ByteString as N', b'import qualified SocksSocketEffects as N').replace(b'import System.Environment', b'import ConnectionEnvironmentEffects')
    elif f == 'ConnectionTlsEffects.hs':
        s = s.replace(b'import System.IO.Unsafe', b'import System.IO.Unsafe\nimport qualified DialEffects')
        s = s.replace(b"emit v = evaluate (L.length $ encode v) >> modifyIORef' events (v:)", b'emit = DialEffects.emit')
    assert (folder/f).read_bytes() == s, f
for path, module in [('Network/Socks5.hs','OriginalSocks'), ('Network/Socks5/Command.hs','OriginalSocksCommand'),
                     ('Network/Socks5/Conf.hs','OriginalSocksConf'), ('Network/Socks5/Types.hs','OriginalSocksTypes'),
                     ('Network/Socks5/Wire.hs','OriginalSocksWire'), ('Network/Socks5/Lowlevel.hs','OriginalSocksLowlevel')]:
    s = (root / 'vendor/crypton-socks-0.6.2/src' / path).read_bytes()
    if module == 'OriginalSocksCommand':
        a, b = s.index(b'module Network.Socks5.Command'), s.index(b'import ')
        s = s[:a] + b'module OriginalSocksCommand where\r\n\r\n' + s[b:]
    else: s = s.replace(b'module '+path[:-3].replace('/','.').encode(), b'module '+module.encode(), 1)
    for name, mapped in [('Network.Socks5.Command','OriginalSocksCommand'), ('Network.Socks5.Conf','OriginalSocksConf'),
                         ('Network.Socks5.Types','OriginalSocksTypes'), ('Network.Socks5.Wire','OriginalSocksWire')]: s = s.replace(name.encode(), mapped.encode())
    s = s.replace(b'import           Network.Socket.ByteString (', b'import           SocksSocketEffects (')
    assert (folder/(module+'.hs')).read_bytes() == s, module
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes(): return {s: hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs): return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target,'rt') as f: metadata=json.loads(next(f)); cs=[json.loads(line) for line in f]
    assert metadata['sources']==hashes() and metadata['cases']==len(cs) and metadata['caseHash']==digest(cs)
    assert metadata['groups']==dict(collections.Counter(c['group'] for c in cs))
    print('Verified',len(cs),'original connection acquisition/SOCKS programs and',len(metadata['sources']),'source hashes'); raise SystemExit
assert args.oracle
cs=[]
def add(group,api,**v): cs.append(dict(group=group,request=dict(api=api,**v)))
def host(kind='ipv4', **v): return dict(kind=kind, **(dict(word=0x04030201) if kind=='ipv4' else dict(words=[0,1,0x11223344,0xffffffff]) if kind=='ipv6' else dict(bytes=[0,255,97])), **v)
hosts = [host(),host('ipv6'),host('domain')]
request = dict(command=dict(code=1),host=hosts[0],port=443)
good = [5,0,0,1,1,2,3,4,0,80]
for n in list(range(-3,259))+[-2**63,2**63-1]: add('enum-all-bytes-negative-conversion-and-8-bit-errors','enum',number=n)
for n in range(256):
    add('hello-every-method-byte','helloResponse',chunks=[[5,n]])
    add('hello-every-method-byte-encoding','encode',kind='helloResponse',method=dict(code=n))
    add('request-every-command-byte','request',chunks=[[5,n,99,1,1,2,3,4,255,254]])
    add('response-every-reply-byte','response',chunks=[[5,n,99,1,1,2,3,4,255,254]])
    add('rpc-every-reply-byte','rpc',chunks=[[5,n,99,1,1,2,3,4,255,254]],**request)
    add('connect-every-authentication-method','connect',host=hosts[0],port=443,chunks=[[5,n],good])
    for api in ['hello','helloResponse','request','response']:
        if n!=5: add('unsupported-versions-all-byte-values',api,chunks=[[n,0,0,1,1,2,3,4,0,80]])
    if n not in [1,3,4]: add('unknown-address-types-all-byte-values','response',chunks=[[5,0,0,n,0,0]])
for h,port in itertools.product(hosts+[dict(kind='domain',bytes=[]),dict(kind='domain',bytes=[97]*255),dict(kind='domain',bytes=[98]*256),dict(kind='domain',bytes=[0]*257)],[-1,0,1,443,65535,65536]):
    for kind in ['request','response']:
        add('wire-host-word-order-domain-length-overflow-and-port-order','encode',kind=kind,host=h,port=port,command=dict(other=255),reply=dict(other=0))
for n in [0,1,2,255,256,257]: add('hello-method-count-overflow','encode',kind='hello',methods=[dict(other=i%256) for i in range(n)])
for api,packet in [('hello',[5,3,0,1,255]),('helloResponse',[5,0]),('request',[5,1,0,4]+list(range(16))+[1,2]),('response',good)]:
    for i in range(len(packet)+1):
        add('partial-parsing-each-split-and-empty-feed',api,chunks=[packet[:i],packet[i:]])
        add('partial-parsing-EOF-each-truncation',api,chunks=[packet[:i],[]])
    add('partial-parsing-every-byte-separate',api,chunks=[[n] for n in packet])
    add('extra-bytes-in-current-chunk-are-errors',api,chunks=[packet+[99]])
    add('later-chunk-remains-unread',api,chunks=[packet,[99]])
for i in range(len(good)+1):
    add('connect-partial-response-and-EOF','connect',host=hosts[1],port=65535,chunks=[[5],[0],good[:i],good[i:]])
for chunks in [[[5,0]+good],[[5,0],[5,0,0,3,1,97,0,80]],[[5,0],[5,8,0,1,1,2,3,4,0,80]],['io:recv'],[[5,0],'fatal-recv']]:
    add('connect-coalescing-domain-reply-errors-and-IO-boundaries','connect',host=hosts[2],port=443,chunks=chunks)
add('server-listen-original-lowlevel-order','listen',chunks=[[5,3,0,1,2],[5,1,0,3,1,97,0,80]])
entries=[dict(id=1),dict(id=2),dict(id=3)]
def dial(group,**v):
    base=dict(host=[100,101,115,116],port=443,addresses=entries,close=True)
    base.update(v);add(group,'dial',**base)
for size in range(4):
    for outcomes in itertools.product([None,'createFailure','connectFailure'],repeat=size):
        addresses=[dict(id=i+1,**({} if failure is None else {failure:'io:'+failure+str(i)})) for i,failure in enumerate(outcomes)]
        dial('sequential-addresses-first-success-and-reversed-IO-errors',addresses=addresses)
for stage,tag,close in itertools.product(['createFailure','connectFailure','closeFailure'],['io:failure','fatal-failure'],[None,'io:close','fatal-close']):
    entry=dict(id=1,**{stage:tag})
    if close is not None: entry['closeFailure']=close
    dial('socket-acquisition-bracket-and-cleanup-error-replacement',addresses=[entry,dict(id=2)])
for failure in ['io:resolve','fatal-resolve']:dial('resolver-failure-is-outside-address-aggregation',resolveFailure=failure)
for secure,failure in itertools.product([False,True],[None,'contextNew-io','contextNew-fatal','handshake-io','handshake-fatal']):
    dial('outer-bracket-owns-socket-after-acquisition-and-before-TLS',secure=secure,**({} if failure is None else dict(failure=failure)))
for failure,close_failure in itertools.product(['contextNew-io','handshake-io','handshake-fatal'],['io:outer-close','fatal-outer-close']):
    dial('outer-TLS-bracket-close-failure-replaces-original-error',secure=True,failure=failure,
         addresses=[dict(id=1,closeFailure=close_failure)])
for failure in [None,'contextNew-io','handshake-fatal']:
    dial('successful-SOCKS-acquisition-enters-outer-TLS-bracket',secure=True,
         proxy=dict(kind='simple',host=[112],port=1080),chunks=[[5,0],good],
         **({} if failure is None else dict(failure=failure)))
for proxy in [dict(kind='other',host=[112],port=8080),dict(kind='simple',host=[112],port=1080)]:
    for chunks in [[[5,0],good],[[5,255]],[[5,0],[5,0,0,3,1,97,0,80]],['io:recv'],[[5,0],[5,1,0,1,0,0,0,0,0,0]]]:
        dial('proxy-identity-domain-destination-and-SOCKS-acquisition-failure-ownership',proxy=proxy,chunks=chunks,host=[0,255,0x100,0x1f600,0xd800],port=65536)
ports=['','0','1','65535','65536','-1','-0','+1',' 80','80 ','(80)','(( 80 ))','(80) ','0xff','0o777','0b1010','0x10000','1e2','1.0','- 0','-(0)','(--1)','( ( 0x10 ) )','9999999999999999999999999999999999999999999','80\t','\u00a080','\u200380','\u001c80','80\u00a0','0_1','80:90']
for prefix,port in itertools.product(['','proxy','[::1]','::1','Русский'],ports):
    dial('environment-first-colon-Read-PortNumber-bounds-parentheses-bases-and-whitespace',
         proxy=dict(kind='environment'),environment=list(map(ord,prefix+':'+port)),chunks=[[5,0],good])
for value in ['', 'proxy', 'proxy:1080', '[::1]', 'a:b:c']:
    for name in [None,[],[88,0,0xd800]]:
        dial('environment-default-name-raw-host-and-no-colon-default-port',proxy=dict(kind='environment',name=name),environment=list(map(ord,value)),chunks=[[5,0],good])
for failure in ['io:environment','fatal-environment']:
    dial('environment-catches-only-IO',proxy=dict(kind='environment'),environmentFailure=failure)
rng=random.Random(0x534f434b)
for i in range(300):
    data=[rng.randrange(256) for _ in range(rng.randrange(30))]
    chunks=[data[:rng.randrange(len(data)+1)]]
    chunks.append(data[len(chunks[0]):])
    add('seeded-malformed-wire-inputs',rng.choice(['hello','helloResponse','request','response']),chunks=chunks)
process=subprocess.run([args.oracle],input=''.join(json.dumps(c['request'])+'\n' for c in cs),capture_output=True,text=True)
assert process.returncode==0,process.stderr
observations=[json.loads(line) for line in process.stdout.rstrip('\n').split('\n')]
assert len(observations)==len(cs)
for c,result in zip(cs,observations):c['expected']=result
metadata=dict(upstream='complete original crypton-connection 0.4.6 and crypton-socks 0.6.2 modules; actual network 3.2.9.0 PortNumber Read and cereal 0.5.8.3; GHC 9.12.2',
              cases=len(cs),caseHash=digest(cs),sources=hashes(),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for v in [metadata]+cs:gz.write((json.dumps(v,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in metadata.items() if k!='sources'},indent=2))
