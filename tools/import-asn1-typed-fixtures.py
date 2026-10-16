#!/usr/bin/env python3
"""Original ASN1 primitive/BER/DER code, all five upstream generators and properties."""
import argparse, collections, gzip, hashlib, itertools, json, random, re, subprocess, tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-asn1-typed-reference'
target = root / 'core/src/test/resources/asn1-typed/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle-dir')
p.add_argument('--secondary-oracle-dir')
p.add_argument('--check-source', action='store_true')
p.add_argument('--verify-oracle', action='store_true')
a = p.parse_args()
sources = [Path(__file__).resolve()]
for d, manifest, archive, notice in [
    (root/'vendor/crypton-asn1-types-0.4.1', root/'vendor/crypton-asn1-types-0.4.1/SOURCE.json', '61efc63877a0d3ddb992a793839d9427c1597e095a6e1f63c04e05fd3e4e302d', 'crypton-asn1-types-BSD-3-Clause.txt'),
    (root/'vendor/crypton-asn1-encoding-0.10.0', root/'vendor/crypton-asn1-encoding-0.10.0/SOURCE.json', '49813a0ee253f628cb8102dd6857905609b52e490c73c554d7cdf0cbce2926d2', 'crypton-asn1-encoding-BSD-3-Clause.txt'),
    (root/'vendor/time-hourglass-0.3.0', root/'vendor/time-hourglass-0.3.0/SOURCE.json', '9c7c7c74dda5033160328606cb3e8724201abadf9a140271c73c89a2612b9d22', 'time-hourglass-BSD-3-Clause.txt'),
    (root/'vendor/Win32-2.14.2.1', root/'tools/haskell-hourglass-reference/Win32-source.json', '69d15a9fb4ef718353aaf8700a64c5885743d4f34a94f7da273fa12584df0315', 'Win32-BSD-3-Clause.txt'),
]:
    m = json.loads(manifest.read_text())
    assert m['sha256'] == archive
    assert m['package']+'-'+m['version'] == d.name
    assert m['url'] == 'https://hackage-content.haskell.org/package/'+d.name+'.tar.gz'
    assert set(m['files']) == {str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f != manifest}
    for f, h in m['files'].items():
        assert hashlib.sha256((d/f).read_bytes()).hexdigest() == h, (d, f)
    assert (root/'licenses'/notice).read_bytes() == (d/'LICENSE').read_bytes()
    sources += [manifest, root/'licenses'/notice]+[d/f for f in m['files']]
sources += [f for f in folder.rglob('*') if f.is_file()]
def hashes():
    return {str(f.relative_to(root)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(set(sources))}
def digest(cs):
    return hashlib.sha256(''.join(json.dumps(c, sort_keys=True, separators=(',', ':')) for c in cs).encode()).hexdigest()
sources += [f for f in (root/'tools/haskell-hourglass-reference').rglob('*') if f.is_file()]
d=root/'vendor/ghc-bignum-1.3-encode';m=json.loads((d/'SOURCE.json').read_text())
assert m['package']=='ghc-bignum' and m['version']=='1.3' and m['ref']=='ghc-9.12.2-release'
assert set(m['files'])=={str(f.relative_to(d)) for f in d.rglob('*') if f.is_file() and f.name!='SOURCE.json'}
for f,h in m['files'].items():assert hashlib.sha256((d/f).read_bytes()).hexdigest()==h,f
assert (root/'licenses/ghc-bignum-BSD-3-Clause.txt').read_bytes()==(d/'LICENSE').read_bytes()
sources += [f for f in d.rglob('*') if f.is_file()]+[root/'licenses/ghc-bignum-BSD-3-Clause.txt',root/'vendor/ghc-9.12.2/rts/StgPrimFloat.c',root/'vendor/ghc-9.12.2/GHC/Internal/Float.hs',root/'licenses/GHC-base-BSD-3-Clause.txt']
original=(root/'vendor/crypton-asn1-encoding-0.10.0/tests/Tests.hs').read_text();at=original.index('\n\n')
adapter=original[:at]+'\n\n-- Only adapter change: expose original test functions and generators.\nmodule UpstreamTests where'+original[at:]
assert (folder/'UpstreamTests.hs').read_text()==adapter
if a.check_source or a.verify_oracle:
    with gzip.open(target,'rt') as f:
        manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
    assert manifest['sources']==hashes() and manifest['cases']==len(cases) and manifest['caseHash']==digest(cases)
    assert manifest['groups']==dict(collections.Counter(c['group'] for c in cases))
    assert sum(c['request']['api']=='property' for c in cases)==5000
    assert collections.Counter(c.get('sourceProfile') for c in cases)=={None:38577,'positive':37,'negative':37}
    if a.check_source:
        print('Verified',len(cases),'typed ASN1 observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
    assert a.oracle_dir,'--oracle-dir required'
    generated=subprocess.check_output([str(Path(a.oracle_dir)/'asn1-typed-reference'),'--generate-tests','1000'],timeout=180)
    cases=[json.loads(x) for x in generated.splitlines()];assert len(cases)==5000
    rng=random.Random(0x41534e3154595045)
    def add(group,api,model='portable',**v):cases.append(dict(group=group,model=model,request=dict(api=api,**v)))
    def bs(s):return list(s.encode('latin1'))
    edge=[-2**63,-2**63+1,-1,0,1,127,128,255,256,2**63-1]
    payloads=[[],[0],[255],[0,0],[255,255],[0,128],[255,127],list(range(256))]+[[rng.randrange(256) for _ in range(rng.randrange(50))] for _ in range(300)]
    for method,b in itertools.product(['boolean','integer','double','bits','octets','null','oid'],payloads+[[i] for i in range(256)]):
        for der in ([False,True] if method=='boolean' else [False]):add('primitive-getters-empty-canonical-and-arbitrary-bytes','get',method=method,bytes=b,der=der)
    for h,e,mantissa in itertools.product(range(256),[[],[0],[255],[128],[0,1],[255,255],[3,255,0,1]],[[],[0],[1],[255]]):
        add('real-all-header-bits-bases-scales-lengths-and-special-values','get',method='double',bytes=[h]+e+mantissa,der=False)
    for _ in range(1500):
        add('real-arbitrary-raw-binary-exponents-and-mantissas','get',method='double',bytes=[rng.randrange(256) for _ in range(rng.randrange(2,100))],der=False)
    realbits=[0,1,2,(1<<52)-1,1<<52,0x0010000000000001,0x3ff0000000000000,0x7fefffffffffffff,0x8000000000000000,0x8000000000000001,0x7ff0000000000000,0xfff0000000000000,0x7ff8000000000000,0xfff8000000000123]+[rng.randrange(2**64) for _ in range(1000)]
    for bits in realbits:add('putDouble-IEEE754-boundaries-normalization-and-specials','put',method='double',bits=str(bits))
    mantissas=edge+[-(2**63+1),2**63,2**64-1,2**64,2**64+1,2**100+2**46+1,-2**256+1]
    exponents=edge+[-2**31,-1075,-1074,-1022,-53,53,1023,1024,2**31-1,2**32]
    for m,e,native in itertools.product(mantissas,exponents,[False,True]):
        add('GHC-Integer-encodeFloat-small-Int-GMP-and-Native-backends','float',mantissa=str(m),exponent=e,native=native)
    for _ in range(1000):
        m=rng.randrange(-2**rng.randrange(64,1025),2**rng.randrange(64,1025));e=rng.choice(exponents+[rng.randrange(-2000,2001)])
        add('GHC-encodeFloat-arbitrary-limbs-rounding-truncation-and-exponent-wrap','float',mantissa=str(m),exponent=e,native=rng.choice([False,True]))
    for n in edge+[-2**512,2**512]+[rng.randrange(-2**512,2**512) for _ in range(100)]:add('putInteger-arbitrary-twos-complement','put',method='integer',integer=str(n))
    for n,b in itertools.product([0,1,7,8,9,2**64-1],payloads[:8]):add('putBitString-independent-length-and-payload','put',method='bits',length=str(n),bytes=b)
    oids=[[],['0'],['0','0'],['2','39','0'],['2','999','-1','0'],[str(2**512),str(-2**512),str(2**512)]]
    for _ in range(100):oids.append([str(rng.randrange(-2**512,2**512)) for _ in range(rng.randrange(10))])
    for oid in oids:add('putOID-unchecked-first-two-arcs-zero-and-negative-suffixes','put',method='oid',oid=oid)
    for b in payloads[:20]:add('putString-byte-identity','put',method='string',bytes=b)
    for ty in range(2):
        bases=['261016053412','2610160534','491231235959','500101000000','510101000000','690101000000','700101000000','991231235959','000101000000','000000000000','999999999999','x',''] if ty==0 else ['20261016053412','202610160534','00000101000000','20240000000000','20249999999999','99991231235959','x','']
        for base,frac,tz in itertools.product(bases,['','.','.1','.12','.123','.1234','.x'],['','Z','+0000','-0600','+9999','+','-123','+12345','+12x4','+x234','+ab!!','ZZ','Ztail',' ']):
            add('getTime-fallback-2050-pivot-milliseconds-and-manual-timezone-suffix','get',method='time',type=ty,bytes=bs(base+frac+tz),der=False)
        for i in range(256):add('getTime-every-byte-non-ASCII-rejection','get',method='time',type=ty,bytes=bs('20261016053412' if ty else '261016053412')+[i],der=False)
    seq=['sequence'];set_=['set']
    values=[['boolean',False],['boolean',True],['null'],['integer','0'],['integer',str(-2**512)],['bits','18446744073709551615',[]],['octets',list(range(256))],['oid',['2','39','999999999999999999999']],['enumerated',str(2**512)]]
    values += [['real',str(n)] for n in realbits[:14]]+[['string',e,list(range(20))] for e in range(12)]
    values += [['other',c,t,[0,255]] for c,t in itertools.product(range(4),[-2**63,-1,0,1,31,128,2**63-1])]
    values += [['start',seq],['start',set_],['start',['container',3,-1]],['end',seq],['end',set_],['oid',[]],['oid',['0']]]
    for value,pc,length in itertools.product(values,[False,True],[['short',0],['long',2,128],['indefinite']]):
        add('all-constructors-encodeHeader-tag-class-and-length','header',value=value,constructed=pc,length=length)
    for n in edge+[129,65535,65536,2**56]:add('mkSmallestLength-Int64-boundaries','smallest',length=n)
    for value,method,part in itertools.product(values,['primitive','one','constructed'],['length','events','prefix','both']):
        add('primitive-and-constructed-encoding-exported-contracts','encode',value=value,values=[['integer','7']],method=method,part=part,count=0 if part=='prefix' else 1)
    streams=[[],[['end',seq]],[['start',seq],['integer','1']],[['start',seq],['integer','1'],['end',set_]], [['integer','1'],['oid',[]]], [['oid',[]],['end',seq]], [['oid',[]],['oid',['0']]]]
    for _ in range(400):streams.append(rng.choices(values,k=rng.randrange(15)))
    for stream in streams:
        for part in ['length','events','both','prefix']:
            add('encodeList-nesting-unclosed-markers-and-component-error-priority','encode',values=stream,value=['null'],method='list',part=part,count=rng.randrange(5))
        for part in ['bytes','chunks','prefix']:
            add('DER-tree-shape-top-level-End-and-lazy-output-prefix','write',values=stream,part=part,count=rng.randrange(8))
    for depth in [1,10,100,1000]:
        stream=[['start',seq]]*depth+[['integer','1']]+[['end',set_]]*depth
        add('deep-construction-DER-bytes','write',values=stream,part='bytes',count=0)
    for stream,part,n in itertools.product([[['integer','1'],['oid',[]]],[['oid',[]],['oid',['0']]],[['start',seq],['oid',[]],['end',set_]]],['bytes','chunks','prefix'],range(7)):
        add('lazy-DER-output-prefix-before-later-failure-and-zero-demand','write',values=stream,part=part,count=n)
    posix=dict(tm=[12,34,5,16,9,126],clockSeconds=123,clockNanos=987654321,zoneSeconds=0,gmOk=True,localOk=True)
    windows=dict(systemTime=[2026,10,5,16,5,34,12,999],fileOk=True,fileTime='0',zoneOk=True,mode=0,bias=0,standardBias=0,daylightBias=0)
    for model,config in [('posix',posix),('windows',windows)]:
        for ty,tz in itertools.product(range(2),[None,-2**63,-9999,-360,0,60,2**63-1]):
            for _ in range(8):
                dt=[rng.choice(edge),rng.randrange(12),rng.choice(edge)]+[rng.choice(edge) for _ in range(4)]
                add('putTime-'+model+'-generic-normalization-no-fraction-and-offset-Show','put',model,method='time',type=ty,dt=dt,timezone=tz,config=config)
    for ty,tz in itertools.product(range(2),[None,-360,0,60,120]):
        add('putTime-actual-native-common-platform-range','put','native',method='time',type=ty,dt=[2024,1,31,25,61,60,123456789],timezone=tz)
    for cl,tag,b,constructed in itertools.product(range(4),list(range(32))+[-1,128,2**63-1],payloads[:8],[False,True]):
        add('decodePrimitive-all-universal-tags-other-classes-and-ignored-header-fields','decodePrimitive',header=[cl,tag,constructed,['indefinite']],bytes=b)
    raw=[[],[0,0],[1,0],[1,1,1],[1,1,255],[2,0],[2,1,127],[2,2,0,1],[3,0],[4,0],[5,0],[6,0],[7,0],[16,0],[17,0],[0x30,0],[0x30,0x80,0,0],[0x30,3,2,1,1],[0x30,3,5,0,0],[1,0x81,1,1],[4,0x82,0,0],[4,0x80],[5],[0xff],[2,0,0xff]]
    raw += [[tag,1,b] for tag,b in itertools.product(range(32),[0,1,127,128,255])]
    raw += [[rng.randrange(256) for _ in range(rng.randrange(30))] for _ in range(500)]
    for b,der,repr_,part in itertools.product(raw,[False,True],[False,True],['tag','values','prefix']):
        cut=rng.randrange(len(b)+1)
        add('BER-DER-raw-Left-versus-lazy-typed-exceptions-policy-and-representation','binary',chunks=[b[:cut],[],b[cut:]],der=der,repr=repr_,part=part,count=rng.randrange(4))
assert a.oracle_dir,'--oracle-dir required'
def observe(exe,selected):
    with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
        inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in selected).encode());inp.seek(0)
        subprocess.run([str(exe)],stdin=inp,stdout=out,check=True,timeout=180);out.seek(0)
        return [json.loads(x) for x in out]
def profile(directory):
    o=observe(Path(directory)/'asn1-typed-reference',[dict(request=dict(api='get',method='double',bytes=[66],der=False))])[0]
    n=o['right']['value'][1]
    return {'9221120237041090560':'positive','18444492273895866368':'negative'}[n]
runtime_profile=profile(a.oracle_dir)
if a.verify_oracle:
    regenerated=[json.loads(x) for x in subprocess.check_output([str(Path(a.oracle_dir)/'asn1-typed-reference'),'--generate-tests','1000'],timeout=180).splitlines()]
    assert regenerated==[{k:v for k,v in c.items() if k!='expected'} for c in cases[:5000]]
for model,exe in [('portable','asn1-typed-reference'),('native','asn1-typed-reference'),('posix','asn1-typed-posix-model'),('windows','asn1-typed-windows-model')]:
    selected=[c for c in cases if c['model']==model and c.get('sourceProfile',runtime_profile)==runtime_profile]
    observed=observe(Path(a.oracle_dir)/exe,selected)
    assert len(selected)==len(observed),(model,len(selected),len(observed))
    for c,o in zip(selected,observed):
        if a.verify_oracle:assert c['expected']==o,(c,o)
        else:c['expected']=o
        if c['request']['api']=='property':assert o['property'],(c,o)
if a.verify_oracle:
    print('Independent oracle and original generators agree on',sum(c.get('sourceProfile',runtime_profile)==runtime_profile for c in cases),'typed ASN1 observations for',runtime_profile,'NaN profile; full corpus',len(cases));raise SystemExit
assert runtime_profile=='positive' and a.secondary_oracle_dir and profile(a.secondary_oracle_dir)=='negative'
paired=[]
for c in cases:
    o=c['expected']
    if isinstance(o,dict) and isinstance(o.get('right'),dict) and o['right'].get('value')==['real','9221120237041090560']:
        c['sourceProfile']='positive';c['request']['nanProfile']='positive'
        other=json.loads(json.dumps(c));other['sourceProfile']='negative';other['request']['nanProfile']='negative';other.pop('expected')
        paired.append(other)
assert len(paired)==37
other_observed=observe(Path(a.secondary_oracle_dir)/'asn1-typed-reference',paired)
assert len(other_observed)==len(paired)
for c,o in zip(paired,other_observed):
    assert o['right']['value']==['real','18444492273895866368'];c['expected']=o
cases+=paired
m=dict(cases=len(cases),sources=hashes(),caseHash=digest(cases),groups=dict(collections.Counter(c['group'] for c in cases)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for x in [m]+cases:gz.write((json.dumps(x,separators=(',',':'))+'\n').encode())
print('Wrote',len(cases),'original observations;',target.stat().st_size,'bytes;',m['caseHash'])
