#!/usr/bin/env python3
"""Observe unchanged time-hourglass 0.3.0, including controlled native boundaries."""
import argparse, collections, gzip, hashlib, itertools, json, random, re, subprocess, tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-hourglass-format-reference'
target = root / 'core/src/test/resources/hourglass-format/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle-dir')
p.add_argument('--check-source', action='store_true')
p.add_argument('--verify-oracle', action='store_true')
a = p.parse_args()
sources = [Path(__file__).resolve()]
for d, manifest, archive, notice in [
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
if a.check_source or a.verify_oracle:
    with gzip.open(target,'rt') as f:
        manifest=json.loads(next(f));cases=[json.loads(x) for x in f]
    assert manifest['sources']==hashes() and manifest['cases']==len(cases) and manifest['caseHash']==digest(cases)
    assert manifest['groups']==dict(collections.Counter(c['group'] for c in cases))
    if a.check_source:
        print('Verified',len(cases),'hourglass format observations and',len(manifest['sources']),'source hashes');raise SystemExit
else:
    cases=[];rng=random.Random(0x4847464f524d4154)
    def pts(s):return list(map(ord,s))
    def fmt(s):return dict(string=pts(s))
    def elems(*es):return dict(elements=list(es))
    def add(group,api,model='portable',**v):cases.append(dict(group=group,model=model,request=dict(api=api,**v)))
    standards=['Year2','Year4','Year','Month','Month2','MonthName_Short','DayYear','DayYear3','Day','Day2','Hour','Minute','Second','UnixSecond','MilliSecond','MicroSecond','NanoSecond','TimezoneName','TzHM_Colon_Z','TzHM_Colon','TzHM','Tz_Offset','Spaces']
    elements=standards+[dict(precision=n) for n in [-2**63,-1,0,1,2,3,4,5,6,7,8,9,10,2**63-1]]+[dict(text=n) for n in [0,9,10,14,32,34,39,48,92,127,0xd800,0xdc00,0x10000,0x10ffff]]
    original=dict(function=dict(kind='original',name=pts('customTimeFormatFct')))
    elements.append(original)
    edge=[-2**63,-1000000001,-1000000000,-1,0,1,999999999,1000000000,1000000001,2**63-1]
    texts=['','YYYYY','YYY','MMMM','MonMI','TZH:MTZHM','p0p1p9p10p99','msusμns','\\','\\M\\I\\H\\S','HH','YYMMDDHMIS','YYYYMMDDHMIS','YYYY-MM-DDTH:MI:S.msusns','Mon DD\\t\\h YYYY at HH\\hMI\\mS\\s.p9\\n\\s']
    for s in texts:
        add('string-longest-match-and-escape-meta','meta',format=fmt(s),other=fmt(s))
    for e in elements:
        add('all-format-elements-derived-Show-Eq','meta',format=elems(e),other=elems(rng.choice(elements)))
    for _ in range(1000):
        s=rng.choices(pts('YMDHISJonp0129msuμTZ:OFEPC \\')+[0,10,39,0xd800,0xdc00,0x10000,0x10ffff],k=rng.randrange(40))
        add('raw-Haskell-Char-string-to-format-mixed-tokenization','meta',format=dict(string=s),other=dict(string=rng.choice([s,s[::-1]])))
    for name in [[],pts('same'),[0xd800,0xdc00],[0x10000]]:
        a0=dict(function=dict(kind='ignore',name=name,printed=[],drop=0,replaceSuffix=False))
        b0=dict(function=dict(kind='reject',name=name,message=pts('different callback')))
        add('callback-Eq-is-name-only','meta',format=elems(a0),other=elems(b0))
    posix=dict(tm=[12,34,5,16,9,126],clockSeconds=123,clockNanos=987654321,zoneSeconds=-12345,gmOk=True,localOk=True)
    windows=dict(systemTime=[2026,10,5,16,5,34,12,999],fileOk=True,fileTime='133000000009876543',zoneOk=True,mode=0,bias=180,standardBias=0,daylightBias=-60)
    for model,config in [('posix',posix),('windows',windows)]:
        for kind,e in itertools.product(range(11),elements):
            for _ in range(3):
                add('print-'+model+'-all-elements-and-eleven-Time-types','print',model,format=elems(e),kind=kind,scalar=rng.choice(edge),ep=[rng.choice(edge),rng.choice(edge)],dt=[rng.choice(edge),rng.randrange(12),rng.choice(edge)]+[rng.choice(edge) for _ in range(4)],local=rng.choice([True,False]),timezone=rng.choice(edge+[-840,-720,840,720]),config=config)
        failed=dict(config,**({'gmOk':False} if model=='posix' else {'fileOk':False}))
        const=dict(function=dict(kind='ignore',name=pts('constant'),printed=[0xd800,0xdc00],drop=0,replaceSuffix=False))
        for fs in [elems(),elems('Spaces'),elems('UnixSecond'),elems('MilliSecond'),elems('NanoSecond'),elems('TzHM_Colon_Z'),elems('Year'),elems(const),elems(const,'Year'),elems('Year',const),elems(dict(precision=0),'Year'),elems('Year',dict(precision=0))]:
            add('print-'+model+'-unused-date-and-error-demand-order','print',model,format=fs,kind=2,scalar=0,ep=[2**63-1,-1],dt=[0,0,0,0,0,0,0],local=True,timezone=0,config=failed)
        invalidmonth=dict(config,**({'tm':[12,34,5,16,12,126]} if model=='posix' else {'systemTime':[2026,13,5,16,5,34,12,999]}))
        for fs in [elems('Year'),elems('Month2'),elems('Hour'),elems('Minute'),elems('Second'),elems('NanoSecond'),elems('TimezoneName'),elems(const)]:
            add('print-'+model+'-native-month-validation-independent-of-time-field','print',model,format=fs,kind=2,scalar=0,ep=[0,123456789],dt=[2024,1,29,0,0,0,0],local=True,timezone=0,config=invalidmonth)
    # All parse entry points observe success without forcing its value as well as full payloads.
    alphabet=['','0','1','9','12','99','123','999','123456789','2024','2024-02-29T23:59:60Z','2024-00-00','Jan','Dec','JAN','+0000','-1200','+1400','+9999','-12:34','Z','+840','-720','841','-721','18446744073709551616','9223372036854775808','x',' ', '\t\n','\u00a0','\u2003','\u2028','\u2029','\u200b','٠١','１２','\x00','\\"']
    for e,s,action in itertools.product(elements,alphabet,['localE','globalE','local','global']):
        model,config=rng.choice([('posix',posix),('windows',windows)])
        add('all-elements-parse-validation-errors-and-Maybe-entry-points','parse',model,format=elems(e),input=pts(s),action=action,part='full',config=config)
    for cp in list(range(256))+[0x1680,0x180e,0x2000,0x200a,0x2028,0x2029,0x202f,0x205f,0x3000,0xd800,0xdc00,0x10000,0x10ffff]:
        for e in [dict(text=65),'Spaces','TimezoneName','MonthName_Short','Year4','TzHM_Colon']:
            add('raw-Char-errors-GHC-space-and-ASCII-digit-semantics','parse',format=elems(e),input=[cp,cp,cp,cp,cp,49,32],action='localE',part='full')
    combinations=[
        (fmt('YYYY YYYY'),'2025 2024'),(elems('Year4','Spaces','DayYear'),'2025 60'),(elems('DayYear','Spaces','Year4'),'60 2025'),
        (fmt('MM-DD'),'00-99'),(fmt('H:MI:S'),'99:99:99'),(fmt('msusns'),'123456789'),(fmt('nsusms'),'789456123'),
        (fmt('p9msusns'),'123456789987654321'),(fmt('msusnsp9'),'123456789999999999'),
        (elems('TzHM','Spaces','TzHM_Colon_Z'),'+1234 Z'),(elems('Tz_Offset','Spaces','TzHM_Colon_Z'),'-720 Z'),
        (dict(builtin=0),'2024-02-31tail'),(dict(builtin=1),'2024-02-29T23:59:60+99:99tail'),
        (fmt('YYYYMMDDHMIS'),'20261016053412Z'),(fmt('YYMMDDHMIS'),'261016053412Z'),
    ]
    for (fs,s),action,part in itertools.product(combinations,['localE','globalE','local','global'],['full','tag','remaining','timezone','date','time']):
        for model,config in [('posix',posix),('windows',windows)]:
            add('sequential-overwrite-nanosecond-masks-day-year-and-UTC-conversion','parse',model,format=fs,input=pts(s),action=action,part=part,config=config)
    for model,config in [('posix',posix),('windows',windows)]:
        failconfig=dict(config,**({'gmOk':False} if model=='posix' else {'fileOk':False}))
        callbacks=[original,
            dict(function=dict(kind='ignore',name=pts('consume'),printed=pts('constant'),drop=1,replaceSuffix=False)),
            dict(function=dict(kind='ignore',name=pts('rewrite'),printed=pts('constant'),drop=0,replaceSuffix=True,suffix=pts('2024'))),
            dict(function=dict(kind='reject',name=pts('fail'),message=[0xd800,0xdc00])),
            dict(function=dict(kind='error',name=pts('error'),error='callback demanded')),
            dict(function=dict(kind='replace',name=pts('replace'),dt=[2024,1,29,23,59,60,123456789],timezone=0,suffix=pts('2024')))]
        for cb,action,part,s in itertools.product(callbacks,['localE','globalE','local','global'],['full','tag','remaining','timezone','date','time'],['','001','000','366','367','x','2024']):
            add('custom-format-callbacks-replacement-rejection-and-deferred-results','parse',model,format=elems(cb,'Year4'),input=pts(s),action=action,part=part,config=config)
        for fs,s,action,part in itertools.product([
            elems('UnixSecond'),elems('UnixSecond','Spaces','Year4'),elems('UnixSecond','Spaces','Year4',dict(text=65)),
            elems('UnixSecond','Spaces',callbacks[-1]),elems('UnixSecond','Spaces','UnixSecond'),
            elems('UnixSecond','Spaces','Tz_Offset'),elems('UnixSecond','Spaces',callbacks[3]),
            elems('UnixSecond','Spaces',original),elems('UnixSecond','Spaces',callbacks[2],'Year4')],
            ['9223372036854775807 2024','9223372036854775807 x','9223372036854775807 001','9223372036854775807 +840'],
            ['localE','globalE','local','global'],['full','tag','remaining','timezone','date','time']):
            add('EPOCH-native-failure-laziness-overwrite-and-later-parser-error-priority','parse',model,format=fs,input=pts(s),action=action,part=part,config=failconfig)
        for kind in ['partialDate','partialTime']:
            cb=dict(function=dict(kind=kind,name=pts(kind),dt=[2024,1,29,23,59,60,123456789],timezone=0,suffix=pts('2024')))
            for suffix,action,part in itertools.product([[],['Year4'],['Hour'],['Tz_Offset']],['localE','globalE','local','global'],['full','tag','remaining','timezone','date','time']):
                add('independently-suspended-DateTime-fields-from-custom-callback','parse',model,format=elems(cb,*suffix),input=pts('x'),action=action,part=part,config=config)
        for fs in [elems('UnixSecond','Spaces','Year4','Spaces','UnixSecond'),elems('UnixSecond','Spaces','Hour','Spaces','TzHM'),elems('UnixSecond','Spaces','DayYear','Spaces','Year4')]:
            for action,part in itertools.product(['localE','globalE','local','global'],['full','tag','remaining','timezone','date','time']):
                add('native-DateTime-constructor-versus-fields-demand-in-parse-state','parse',model,format=fs,input=pts('123 2024 0000'),action=action,part=part,config=config)
    import calendar
    property_formats=[fmt('YYYY-MM-DDTH:MI:S.msusns'),fmt('Mon DD\\t\\h YYYY at HH\\hMI\\mS\\s.p9\\n\\s'),
        elems('Year4','Spaces',original,'Spaces','Hour','Spaces','Minute','Spaces','Second','Spaces',dict(precision=9)),dict(builtin=0),dict(builtin=1)]
    # Original Arbitrary Date range is 1800..2202 on both 64-bit platform modules.
    for _ in range(1000):
        y=rng.randrange(1800,2203);m=rng.randrange(1,13);d=rng.randrange(1,calendar.monthrange(y,m)[1]+1)
        dt=[y,m-1,d,rng.randrange(24),rng.randrange(60),rng.randrange(60),rng.randrange(100000001)]
        for i,fs in enumerate(property_formats):
            value=dt if i<3 else dt[:3]+([0,0,0,0] if i==3 else dt[3:6]+[0])
            add('original-Tests.hs-format-roundtrip-properties-1000-valid-dates','roundtrip','native',format=fs,dt=value)
    for fs in [dict(builtin=1),fmt('YYYY-MM-DDTH:MI:STZHM'),fmt('YYYY-MM-DDTH:MI:STZOFS')]:
        for s in ['2024-02-29T23:59:60Z','2024-02-29T23:59:60+02:00','2024-02-29T23:59:60-1200','2024-02-29T23:59:60+840']:
            for action in ['localE','globalE','local','global']:
                add('actual-native-parse-UTC-normalization-and-trailing-input','parse','native',format=fs,input=pts(s),action=action,part='full')
    for seconds in [-11644473600,-3506716800,-1,0,1,946684800,2147483648,32503680000]+[rng.randrange(-11644473600,32503680001) for _ in range(1000)]:
        add('original-Tests.hs-ISO-date-and-Unix-second-format-properties-versus-Data.Time','propertyPrint','native',format=elems(),scalar=seconds)
    for offset in range(-660,661):
        sign='-' if offset<0 else '+';value=abs(offset);s=f'{sign}{value//60:02}{value%60:02}'
        add('original-Tests.hs-timezone-parse-property-all-generator-offsets','parse',format=fmt('TZHM'),input=pts(s),action='localE',part='timezone')
    for _ in range(1000):
        s=''.join(rng.choices('0123456789',k=rng.randrange(20,200)))
        e=rng.choice(['Year','Month','DayYear','Day','Tz_Offset',dict(precision=rng.randrange(20,200))])
        add('unbounded-numeric-fold-and-precision-Int64-wrap','parse',format=elems(e),input=pts(s),action='localE',part='full')
assert a.oracle_dir,'--oracle-dir required'
for model,exe in [('portable','hourglass-format-reference'),('native','hourglass-format-reference'),('posix','hourglass-format-posix-model'),('windows','hourglass-format-windows-model')]:
    selected=[c for c in cases if c['model']==model]
    with tempfile.TemporaryFile() as inp,tempfile.TemporaryFile() as out:
        inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in selected).encode());inp.seek(0)
        subprocess.run([str(Path(a.oracle_dir)/exe)],stdin=inp,stdout=out,check=True,timeout=180);out.seek(0)
        observed=[json.loads(line) for line in out]
    assert len(selected)==len(observed),(model,len(selected),len(observed))
    for c,o in zip(selected,observed):
        if a.verify_oracle:assert c['expected']==o,(c,o)
        else:c['expected']=o
        if c['request']['api']=='roundtrip':assert o['equal'],(c,o)
if a.verify_oracle:
    print('Independent oracle agrees on all',len(cases),'hourglass format observations');raise SystemExit
m=dict(cases=len(cases),sources=hashes(),caseHash=digest(cases),groups=dict(collections.Counter(c['group'] for c in cases)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for x in [m]+cases:gz.write((json.dumps(x,separators=(',',':'))+'\n').encode())
print('Wrote',len(cases),'original observations;',target.stat().st_size,'bytes;',m['caseHash'])
