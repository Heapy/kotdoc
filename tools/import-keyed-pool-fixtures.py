#!/usr/bin/env python3
"""Original complete Data.KeyedPool observations with live and controlled effects."""
import argparse,collections,gzip,hashlib,itertools,json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];folder=root/'tools/haskell-keyed-pool-reference';target=root/'core/src/test/resources/keyed-pool/data.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');args=p.parse_args()
source=(root/'vendor/http-client-0.7.19/Data/KeyedPool.hs').read_bytes()
assert (folder/'LiveKeyedPool.hs').read_bytes()==source.replace(b'module Data.KeyedPool',b'module LiveKeyedPool')
controlled=source.replace(b'module Data.KeyedPool',b'module OriginalKeyedPool').replace(b'import Control.Concurrent (forkIOWithUnmask, threadDelay)',b'import PoolClock (forkIOWithUnmask, threadDelay, getCurrentTime, mkWeakIORef)').replace(b'UTCTime, getCurrentTime, addUTCTime',b'UTCTime, addUTCTime').replace(b'newIORef, mkWeakIORef, readIORef',b'newIORef, readIORef')
assert (folder/'OriginalKeyedPool.hs').read_bytes()==controlled
with gzip.open(root/'core/src/test/resources/http-loop/data.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=list(parent['sources'])+['core/src/test/resources/http-loop/data.jsonl.gz','tools/import-keyed-pool-fixtures.py']
sources += [str(f.relative_to(root)) for f in folder.rglob('*') if f.is_file()]
def hashes():return {s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sorted(set(sources))}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if args.check_source:
    with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
    assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs)
    assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
    print('Verified',len(cs),'original KeyedPool observations and',len(m['sources']),'source hashes');raise SystemExit
assert args.oracle
proc=subprocess.Popen([args.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
def ask(v):
    proc.stdin.write(json.dumps(v)+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,v
    result=json.loads(line)
    for cmd,result in zip(v['commands'],result['results']):
        if cmd['op']=='collect':assert result is True,('Original GC did not complete',v)
    return json.loads(line)
cs=[];q=10**12
cmd=lambda op,**extra:dict(op=op,**extra)
take=lambda key,name:cmd('take',key=key,**{'as':name})
release=lambda name,reuse=True:cmd('release',handle=name,reuse=reuse)
time=lambda day=60000,pico=0:cmd('time',time=[str(day),str(pico)])
def add(group,commands,engine='controlled',maxPerKey=3,maxTotal=10,**extra):
    r=dict(dict(engine=engine,maxPerKey=maxPerKey,maxTotal=maxTotal,commands=commands),**extra)
    cs.append(dict(group=group,request=r,expected=ask(r)))
keys=[[0],[0,0,0],[0,1,0,1],[2,1,0,2,1,0],[-2**63,0,2**63-1,-1,0]]
for engine,per,total,ks,reverse in itertools.product(['original','controlled'],[-2,-1,0,1,2,3,10,2**63-1],[-2,-1,0,1,2,3,5,2**63-1],keys,[False,True]):
    order=list(range(len(ks)));order=order[::-1] if reverse else order
    commands=[take(k,'a'+str(i)) for i,k in enumerate(ks)]+[release('a'+str(i)) for i in order]+[take(k,'b'+str(i)) for i,k in enumerate(ks)]
    commands += [release('b'+str(i),False) for i in range(len(ks))]
    add('idle-per-key-global-limits-active-unbounded-LIFO-and-key-order',commands,engine,per,total)
for engine,actions,null in itertools.product(['original','controlled'],itertools.product([False,True],repeat=4),[False,True]):
    add('exactly-once-release-and-null-valued-idle-resource',[take(0,'a')]+[release('a',x) for x in actions]+[take(0,'b'),release('b',False)],engine,nullValues=null)
for engine,tag,reuse in itertools.product(['original','controlled'],['create-failure','io','async'],[False,True]):
    add('create-errors-do-not-install-managed-finalizer',[take(0,'a'),take(0,'b'),release('b',reuse),take(0,'c'),release('c',False)],engine,createFailures=[1],createFailureKind=tag)
for engine,tag,per,total in itertools.product(['original','controlled'],['destroy-failure','io','async'],[0,1,2],[0,1,4]):
    add('destroy-errors-propagate-only-on-Reuse-overflow',[take(0,'a'),take(0,'b'),release('a'),release('b'),release('a',False),release('b',False),take(0,'c'),release('c',False)],engine,per,total,destroyAllFailure=True,destroyFailureKind=tag)
for tag in ['destroy-failure','io','async']:
    add('pool-finalization-order-closed-pool-take-and-return',[take(2,'a'),take(-1,'b'),take(2,'c'),release('a'),release('b'),release('c'),cmd('finalize',index=0),take(2,'d'),release('d'),release('d',False),cmd('tick')],destroyAllFailure=True,destroyFailureKind=tag)
for start,end in itertools.product([(60000,0),(60000,86399*q),(60000,86400*q+q//2),(60000,86401*q),(60000,-1)],[(60000,29*q),(60000,30*q),(60000,30*q+1),(60001,29*q),(60001,30*q),(60001,30*q+1)]):
    add('reaper-30-second-inclusive-boundary-raw-UTC-and-leaps',[time(*start),take(0,'a'),release('a'),time(*end),cmd('tick'),take(0,'b'),release('b',False)])
for a,b,now in [(100,0,40),(0,100,40),(100,50,81),(0,0,30),(0,0,31),(100,0,-1)]:
    add('reaper-retains-prefix-after-clock-rollback',[time(pico=a*q),take(0,'a'),take(0,'b'),release('a'),time(pico=b*q),release('b'),time(pico=now*q),cmd('tick'),take(0,'c'),take(0,'d'),release('c',False),release('d',False)])
add('reaper-empty-map-retry-wakes-after-return',[cmd('start-tick'),time(pico=100*q),take(0,'a'),release('a'),cmd('await-tick'),take(0,'b'),release('b',False)])
for tag in ['clock-failure','io','async']:
    add('release-clock-error-occurs-after-idempotence-flag',[take(0,'a'),cmd('fail-time',error=tag),release('a'),release('a',False),cmd('finalize',index=1),take(0,'b'),release('b'),cmd('finalize',index=0),cmd('tick')])
    for handler in [None,'handler-failure']:
        extra={} if handler is None else dict(reaperHandlerFailure=handler)
        commands=[take(0,'a'),release('a'),cmd('fail-time',error=tag),cmd('tick')]
        if handler is None:commands += [cmd('tick')]
        commands += [take(0,'b'),release('b',False)]
        add('reaper-clock-failure-and-handler-restart-or-exit',commands,**extra)
for tag in ['destroy-failure','io','async']:
    add('reaper-ignores-destructor-errors-and-continues-key-order',[take(2,'a'),take(-1,'b'),take(2,'c'),release('a'),release('b'),release('c'),time(pico=31*q),cmd('tick'),take(2,'d'),release('d',False)],destroyAllFailure=True,destroyFailureKind=tag)
add('managed-finalizer-DontReuse-and-idempotence',[take(0,'a'),take(1,'b'),cmd('finalize',index=1),cmd('finalize',index=1),release('a'),release('b'),cmd('finalize',index=2),take(1,'c'),release('c',False)])
for engine in ['original','controlled']:
    add('dummyManaged-has-no-finalizer-or-release-effect',[cmd('dummy',resource=99,**{'as':'a'}),cmd('peek',handle='a'),release('a'),release('a',False),cmd('keep',handle='a')],engine)
    add('create-outside-transaction-does-not-block-other-takes',[cmd('fork-take',key=0,worker='w',**{'as':'a'}),cmd('await-blocked'),take(0,'b'),release('b'),cmd('unblock'),cmd('join',worker='w'),release('a'),take(0,'c'),release('c',False)],engine,1,4,blockCreate=[1])
    add('overflow-destroy-outside-transaction-allows-concurrent-take',[take(0,'a'),take(0,'b'),release('a'),cmd('fork-release',handle='b',reuse=True,worker='w'),cmd('await-blocked'),take(0,'c'),release('c',False),cmd('unblock'),cmd('join',worker='w'),release('b',False)],engine,1,4,blockDestroy=[2])
    add('concurrent-second-release-observes-first-flag-before-destroy',[take(0,'a'),cmd('fork-release',handle='a',reuse=False,worker='w'),cmd('await-blocked'),release('a'),cmd('unblock'),cmd('join',worker='w'),take(0,'b'),release('b',False)],engine,blockDestroy=[1])
    add('async-interrupt-blocked-create-does-not-register-managed',[cmd('fork-take',key=0,worker='w',**{'as':'a'}),cmd('await-blocked'),cmd('interrupt',worker='w'),cmd('join',worker='w'),take(0,'b'),release('b',False)],engine,blockCreate=[1])
    add('async-interrupt-overflow-destroy-retains-pool-and-release-flag',[take(0,'a'),take(0,'b'),release('a'),cmd('fork-release',handle='b',reuse=True,worker='w'),cmd('await-blocked'),cmd('interrupt',worker='w'),cmd('join',worker='w'),release('b',False),take(0,'c'),release('c',False)],engine,1,4,blockDestroy=[2])
for commands in [[take(0,'a'),cmd('forget',handle='a'),cmd('collect',destroyCount=1)],
                 [take(0,'a'),release('a'),cmd('forget',handle='a'),cmd('forget-pool'),cmd('collect',destroyCount=1)],
                 [take(0,'a'),take(1,'b'),release('a'),cmd('forget',handle='a'),cmd('forget',handle='b'),cmd('forget-pool'),cmd('collect',destroyCount=2)]]:
    add('actual-native-GC-of-managed-and-idle-pool',commands,'original')
proc.stdin.close();assert proc.wait()==0
m=dict(upstream='http-client-0.7.19 complete original Data.KeyedPool with live/effect-boundary observations',sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
    for value in [m]+cs:gz.write((json.dumps(value,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
