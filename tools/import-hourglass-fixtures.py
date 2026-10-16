#!/usr/bin/env python3
"""Observe unchanged time-hourglass 0.3.0, including controlled native boundaries."""
import argparse, collections, gzip, hashlib, itertools, json, random, re, subprocess, tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-hourglass-reference'
target = root / 'core/src/test/resources/hourglass/data.jsonl.gz'
p = argparse.ArgumentParser()
p.add_argument('--oracle-dir')
p.add_argument('--check-source', action='store_true')
p.add_argument('--verify-oracle', action='store_true')
a = p.parse_args()
sources = [Path(__file__).resolve()]
for d, manifest, archive, notice in [
    (root/'vendor/time-hourglass-0.3.0', root/'vendor/time-hourglass-0.3.0/SOURCE.json', '9c7c7c74dda5033160328606cb3e8724201abadf9a140271c73c89a2612b9d22', 'time-hourglass-BSD-3-Clause.txt'),
    (root/'vendor/Win32-2.14.2.1', folder/'Win32-source.json', '69d15a9fb4ef718353aaf8700a64c5885743d4f34a94f7da273fa12584df0315', 'Win32-BSD-3-Clause.txt'),
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
if a.check_source or a.verify_oracle:
    with gzip.open(target, 'rt') as f:
        manifest = json.loads(next(f)); cases = [json.loads(line) for line in f]
    assert manifest['sources'] == hashes()
    assert manifest['cases'] == len(cases) and manifest['caseHash'] == digest(cases)
    assert manifest['groups'] == dict(collections.Counter(c['group'] for c in cases))
    if a.check_source:
        print('Verified', len(cases), 'hourglass observations and', len(manifest['sources']), 'source hashes'); raise SystemExit
else:
    cases = []; rng = random.Random(0x484f5552474c4153)
    def add(group, api, model='portable', **v):
        cases.append(dict(group=group, model=model, request=dict(api=api, **v)))
    edge = [-2**63, -2**63+1, -1000000001, -1000000000, -999999999, -61, -60, -1, 0, 1, 59, 60, 61, 999999999, 1000000000, 1000000001, 2**63-2, 2**63-1]
    def elapsed(group, x, y, n=None, d=None):
        add(group, 'elapsed', a=x, b=y, numerator=str(n if n is not None else rng.randrange(-2**100, 2**100)), denominator=str(d if d is not None else rng.randrange(1, 2**64)), integer=str(rng.randrange(-2**130, 2**130)))
    # Extract all seven original arithmetic literals; the millisecond helper is original test syntax.
    original = (root/'vendor/time-hourglass-0.3.0/tests/Tests.hs').read_text()
    literal_count = 0
    for name, operation in [('arithmeticTestAddRef', 'plus'), ('arithmeticTestSubRef', 'minus')]:
        block = original.split(name+' = map testRefToElapsedP', 1)[1].split('\n  ]', 1)[0]
        def number(s):
            s = s.strip(); return int(s.removeprefix('ms ').replace('_','')) * (1000000 if s.startswith('ms ') else 1)
        for fields in re.findall(r'\(\(([^,]+), ([^)]+)\), \(([^,]+), ([^)]+)\), \(([^,]+), ([^)]+)\)\)', block):
            values = list(map(number, fields))
            elapsed('original-Tests.hs-seven-arithmetic-literals', values[:2], values[2:4], 0, 1)
            cases[-1]['assertion'] = dict(operation=operation, expected=values[4:]); literal_count += 1
    assert literal_count == 7
    for x, y in itertools.product(edge, repeat=2):
        elapsed('ElapsedP-machine-overflow-normalization-and-component-numeric-instances', [x, y], [rng.choice(edge), rng.choice(edge)])
    for _ in range(800):
        elapsed('ElapsedP-arbitrary-Int64-and-exact-Rational', [rng.randrange(-2**63, 2**63) for _ in range(2)], [rng.randrange(-2**63, 2**63) for _ in range(2)])
    for s, ns in [(0,0),(100,1000000)]:
        elapsed('original-Tests.hs-Real-ElapsedP-regressions', [s,ns], [0,0], s*1000000000+ns, 1000000000)
        cases[-1]['rationalAssertion'] = [str(s*1000+ns//1000000), '1000']
        add('original-Tests.hs-Real-ElapsedSinceP-regressions', 'epoch', epoch=0, a=[s,ns], b=[0,0], integer='0')
        cases[-1]['rationalAssertion'] = [str(s*1000+ns//1000000), '1000']
    for _ in range(600):
        add('Duration-Period-monoids-flatten-and-normalize-overflow', 'duration', a=[rng.choice(edge) for _ in range(4)], b=[rng.choice(edge) for _ in range(4)], p=[rng.choice(edge) for _ in range(3)], q=[rng.choice(edge) for _ in range(3)], seconds=rng.choice(edge))
    for value, sec in itertools.product(edge, repeat=2):
        add('all-TimeInterval-instances-negative-division-and-wrap', 'interval', value=value, seconds=sec)
    for y, m in itertools.product([-2**63,-2**63+1,-400,-100,-4,-1,0,1,4,100,400,1600,1900,1969,1970,2000,2024,2100,2**63-1], range(12)):
        add('proleptic-calendar-all-months-negative-and-overflow-years', 'calendar', dt=[y,m,rng.randrange(-50,70)]+[rng.choice(edge) for _ in range(4)], period=[rng.choice(edge),rng.choice(edge),rng.randrange(-1000,1001)])
    for _ in range(800):
        add('dateAddPeriod-unchecked-day-normalization-leap-and-year-carry', 'calendar', dt=[rng.randrange(-10000,10001),rng.randrange(12),rng.randrange(-50,70)]+[rng.choice(edge) for _ in range(4)], period=[rng.randrange(-100,101),rng.randrange(-100,101),rng.randrange(-1000,1001)])
    for fn, width, n in itertools.product(range(4), [-20,-1,0,1,2,3,4,9,30], [-2**130,-1,0,1,9,10,99,100,999,1000,9999,10000,2**130]):
        add('padding-complete-finite-input-behavior', 'pad', function=fn, width=width, value=str(n))
    for n in edge+[-840,-720,-1,0,1,720,840,10000]:
        add('timezone-name-zero-sign-and-unchecked-Int64', 'timezone', value=n)
    for epoch in range(3):
        for x, y in itertools.product(edge, repeat=2):
            add('Epoch-all-three-offsets-and-ElapsedSinceP-numeric-semantics', 'epoch', epoch=epoch, a=[x,y], b=[rng.choice(edge),rng.choice(edge)], integer=str(rng.randrange(-2**130,2**130)))
    posix = dict(tm=[12,34,5,16,9,126], clockSeconds=123, clockNanos=987654321, zoneSeconds=-12345, gmOk=True, localOk=True)
    windows = dict(systemTime=[2026,10,5,16,5,34,12,999],fileOk=True,fileTime='133000000009876543',zoneOk=True,mode=0,bias=180,standardBias=0,daylightBias=-60)
    for model, config in [('posix',posix), ('windows',windows)]:
        for sec, ns in itertools.product(edge+[-11644473600,-3506716800,910692730086], [-2**63,-1,0,1,999999999,1000000000,2**63-1]):
            add('native-'+model+'-epoch-conversion-and-raw-nanoseconds', 'native', model, action='epochP', elapsed=[sec,ns], config=config)
        for kind in range(11):
            for _ in range(8):
                req = dict(kind=kind, scalar=rng.choice(edge), ep=[rng.choice(edge),rng.choice(edge)], second=[rng.choice(edge),rng.choice(edge)], dt=[rng.choice([0,1970,2026,2**63-1]),rng.randrange(12),rng.randrange(-2,40)]+[rng.choice(edge) for _ in range(4)], timezone=rng.choice([-2**63,-720,-1,0,1,840,2**63-1]), targetTimezone=rng.choice([-2**63,-720,-1,0,1,840,2**63-1]), duration=[rng.choice(edge) for _ in range(4)], config=config)
                for action in ['ep','e','ns','dt','date','tod','diff','diffP','add','localGlobal','localSet','localConvert','localShow','localCompare','localEqual']:
                    add('Time-LocalTime-'+model+'-generic-and-polymorphic-operations', 'time', model, action=action, **req)
                for to in range(11):
                    add('Time-'+model+'-all-eleven-source-and-target-types', 'time', model, action='convert', target=to, **req)
        for dt in [[2026,1,31,25,61,-1,1000000000],[0,0,0,0,0,0,-1],[-2**63,11,32,0,0,0,0]]:
            for action in ['dt','date','tod','identity','dateDt','dateIdentity']:
                add('GHC-specialized-date-and-identity-rewrite-rules', 'direct', model, action=action, dt=dt, config=config)
        for action in ['current','currentP','date','timezone','local','localAt']:
            for tz in [-840,-1,0,1,840]:
                add('Time.System-'+model+'-public-current-and-local-date-functions', 'system', model, action=action, timezone=tz, config=config)
    for sec, ns in itertools.product([-11644473600,-3506716800,-1,0,1,946684800,2147483647,2147483648,253402300799], [-1,0,1,999999999,1000000000]):
        add('actual-native-calendar-common-POSIX-Windows-range', 'native', 'native', action='epochP', elapsed=[sec,ns])
    for tm in [[-2**31,2**31-1,-1,0,0,2**31-1],[59,59,23,31,11,-2**31],[0,0,0,1,1,2147481748]]:
        add('POSIX-CInt-year-before-widening-and-raw-tm-fields', 'native', 'posix', action='epoch', seconds=-2**63, config=dict(posix,tm=tm))
    for month in [-2**31,-1,12,2**31-1]:
        add('POSIX-invalid-native-month-original-enumeration-error', 'native', 'posix', action='epoch', seconds=0, config=dict(posix,tm=[0,0,0,1,month,126]))
    for month in [0,13,65535,65536,-1]:
        add('Windows-invalid-SYSTEMTIME-month-Word16-wrap-and-error', 'native', 'windows', action='epoch', seconds=0, config=dict(windows,systemTime=[2026,month,0,1,0,0,0,0]))
    for model, config, field in [('posix',posix,'localOk'),('posix',posix,'gmOk'),('windows',windows,'zoneOk'),('windows',windows,'fileOk')]:
        for action in ['date','timezone','local','localAt']:
            add('Time.System-'+model+'-native-failure-ordering', 'system', model, action=action, timezone=0, config=dict(config,**{field:False}))
    for ok in [True,False]:
        add('POSIX-gmtime_r-failure', 'native', 'posix', action='epoch', seconds=0, config=dict(posix,gmOk=ok))
        for sec in edge:
            add('POSIX-localtime_r-at-epoch-zero-gmtoff-floor-division', 'native', 'posix', action='timezone', config=dict(posix,localOk=ok,zoneSeconds=sec))
    for sec, ns in itertools.product(edge, repeat=2):
        for action in ['now','nowP']:
            add('POSIX-clockCalendar-seconds-and-nanoseconds', 'native', 'posix', action=action, config=dict(posix,clockSeconds=sec,clockNanos=ns))
    for mode, bias, sb, db in itertools.product([0,1,2,3], [-2**31,-1,0,1,2**31-1], [-2**31,0,2**31-1], [-2**31,0,2**31-1]):
        add('Windows-timezone-Int32-bias-and-negation-overflow', 'native', 'windows', action='timezone', config=dict(windows,mode=mode,bias=bias,standardBias=sb,daylightBias=db))
    for action, changes in [('epoch',dict(fileOk=False)),('timezone',dict(zoneOk=False))]:
        add('Windows-native-error-propagation', 'native', 'windows', action=action, seconds=0, config=dict(windows,**changes))
    for word in [0,1,9999999,10000000,116444736000000000,2**63-1,2**63,2**64-1]+[rng.randrange(2**64) for _ in range(100)]:
        for action in ['now','nowP']:
            add('Windows-FILETIME-unsigned-100ns-resolution', 'native', 'windows', action=action, config=dict(windows,fileTime=str(word)))
assert a.oracle_dir, '--oracle-dir required'
for model, exe in [('portable','hourglass-reference'),('native','hourglass-reference'),('posix','hourglass-posix-model'),('windows','hourglass-windows-model')]:
    selected = [c for c in cases if c['model'] == model]
    with tempfile.TemporaryFile() as inp, tempfile.TemporaryFile() as out:
        inp.write(''.join(json.dumps(c['request'],separators=(',',':'))+'\n' for c in selected).encode()); inp.seek(0)
        subprocess.run([str(Path(a.oracle_dir)/exe)],stdin=inp,stdout=out,check=True,timeout=180); out.seek(0)
        observed = [json.loads(line) for line in out]
    assert len(selected) == len(observed)
    for c, o in zip(selected, observed):
        if a.verify_oracle: assert c['expected'] == o, (c, o)
        else: c['expected'] = o
        result = o if model in ['portable','native'] else o['output']
        if 'assertion' in c: assert result[c['assertion']['operation']] == c['assertion']['expected']
        if 'rationalAssertion' in c:
            from fractions import Fraction
            assert Fraction(*map(int,result['rational'])) == Fraction(*map(int,c['rationalAssertion']))
if a.verify_oracle:
    print('Independent oracle agrees on all',len(cases),'hourglass observations'); raise SystemExit
m = dict(cases=len(cases),sources=hashes(),caseHash=digest(cases),groups=dict(collections.Counter(c['group'] for c in cases)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f, gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for x in [m]+cases: gz.write((json.dumps(x,separators=(',',':'))+'\n').encode())
print('Wrote',len(cases),'original observations;',target.stat().st_size,'bytes;',m['caseHash'])
