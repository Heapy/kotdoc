#!/usr/bin/env python3
"""Execute original GHC Event.Manager with controlled or real native backends."""
import argparse,collections,gzip,hashlib,itertools,json,random,subprocess
from pathlib import Path
r=Path(__file__).resolve().parents[1];folder=r/'tools/haskell-ghc-event-reference';vendor=r/'vendor/ghc-event-9.12.2'
p=argparse.ArgumentParser();p.add_argument('--platform',required=True,choices=['macos-64','linux-64']);p.add_argument('--oracle');p.add_argument('--check-source',action='store_true');a=p.parse_args()
asset=r/f'core/src/main/resources/io/heapy/kotdoc/http/ghc-event-abi-{a.platform}.json';target=r/f'core/src/test/resources/ghc-event/{a.platform}.jsonl.gz'
pin=json.loads((vendor/'SOURCE.json').read_text());assert pin['revision']=='383be28ffdddf65b57b7b111bfc89808b4229ebc'
for name,h in pin['files'].items():assert hashlib.sha256((vendor/name).read_bytes()).hexdigest()==h,name
for f in (folder/'GHC/Internal/Event').rglob('*'):
 if not f.is_file():continue
 name=str(f.relative_to(folder/'GHC/Internal/Event'));raw=(vendor/name).read_bytes()
 if name=='Manager.hs':raw=raw.replace(b'      EventManager\n',b'      EventManager(..), State(..), onFdEvent\n',1).replace(b'    , FdData\n',b'    , FdData(..)\n',1).replace(b'    , FdKey(keyFd)\n',b'    , FdKey(..)\n',1)
 assert f.read_bytes()==raw,name
thread=(vendor/'Thread.hs').read_text();first=thread.index('closeFdWith ::');second=thread.index('threadWait ::');end=thread.index('-- used at least by RTS',second)
assert (folder/'OriginalThreadWait.hs').read_text().endswith(thread[first:second]+thread[second:end])
with gzip.open(r/f'core/src/test/resources/network-io/{a.platform}.jsonl.gz','rt') as f:parent=json.loads(next(f))
sources=set(parent['sources'])|{f'core/src/test/resources/network-io/{a.platform}.jsonl.gz','tools/import-ghc-event-fixtures.py'}
sources|={str(f.relative_to(r)) for f in vendor.rglob('*') if f.is_file()}
sources|={str(f.relative_to(r)) for f in folder.rglob('*') if f.is_file()}
def hashes():return {s:hashlib.sha256((r/s).read_bytes()).hexdigest() for s in sorted(sources)}
def digest(cs):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cs).encode()).hexdigest()
if a.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cs=[json.loads(x) for x in f]
 assert m['sources']==hashes() and m['cases']==len(cs) and m['caseHash']==digest(cs) and m['platform']==a.platform
 assert m['abi']==json.loads(asset.read_text()) and m['abiHash']==hashlib.sha256(asset.read_bytes()).hexdigest()
 assert m['groups']==dict(collections.Counter(c['group'] for c in cs))
 print('Verified',len(cs),'original GHC event observations,',a.platform,'and',len(sources),'source hashes');raise SystemExit
assert a.oracle
def ask(requests):
 proc=subprocess.run([a.oracle],input=''.join(json.dumps(x)+'\n' for x in requests),text=True,capture_output=True,timeout=120)
 assert proc.returncode==0,proc.stderr
 values=[json.loads(x) for x in proc.stdout.splitlines()];assert len(values)==len(requests),(len(values),len(requests),proc.stderr);return values
abi=ask([dict(api='abi')])[0];assert 'error' not in abi,abi
asset.parent.mkdir(parents=True,exist_ok=True);asset.write_text(json.dumps(abi,sort_keys=True,indent=2)+'\n')
cs=[]
def add(group,actions,native=False):cs.append(dict(group=group,request=dict(api='native-program' if native else 'program',actions=actions)))
def reg(label,event=1,multi=False,fd=123,**kw):return dict(op='register',id=label,events=event,multi=multi,fd=fd,**kw)
def dispatch(event,fd=123,**kw):return dict(op='dispatch',events=event,fd=fd,**kw)
def unreg(label,fd=123,**kw):return dict(op='unregister',id=label,fd=fd,**kw)
def close(fd=123,**kw):return dict(op='close',fd=fd,**kw)
for event,multi,notified in itertools.product(range(8),[False,True],range(8)):
 add('one-registration-event-lifetime-and-empty-entry', [reg('a',event,multi),dispatch(notified),dispatch(notified),unreg('a'),close()])
choices=list(itertools.product([0,1,2,3,4,7],[False,True]))
for (e1,m1),(e2,m2),notified in itertools.product(choices,choices,[0,1,2,3,4,7]):
 add('two-callback-LIFO-partition-and-rearm',[reg('a',e1,m1),reg('b',e2,m2),dispatch(notified),unreg('a'),dispatch(7),close()])
small=list(itertools.product([1,2,3],[False,True]))
for (e1,m1),(e2,m2),(e3,m3),notified in itertools.product(small,small,small,[1,2,3]):
 add('three-callback-stable-saved-order',[reg('a',e1,m1),reg('b',e2,m2),reg('c',e3,m3),dispatch(notified),dispatch(notified),unreg('b'),dispatch(3),close()])
for f1,f2,(e1,m1),(e2,m2) in itertools.product([123,124,155,-1],[123,124,155,-1],small,small):
 add('fd-table-hash-collisions-and-cookie-owner',[reg('a',e1,m1,f1),reg('b',e2,m2,f2),dispatch(3,f1),unreg('b',f1),dispatch(3,f2),close(f1),close(f2)])
for event,multi,phase,response,callback_error in itertools.product([0,1,2,3],[False,True],range(4),[False,9,22],[False,True]):
 actions=[reg('a',event,multi,callbackError=callback_error),reg('b',2,True),dispatch(3),unreg('a'),dispatch(7),close()]
 actions[phase]['responses']=[response]
 add('False-rollbacks-versus-throwing-backend-and-callback-errors',actions)
for first,second,last in itertools.product([0,-1,1],[0,2],['step','loop']):
 polls=[dict(result=first),dict(result=second),dict(result=0,control='shutdown')]
 if last=='loop' and (first>0 or second>0):polls[0]['control']='shutdown'
 add('step-nonblock-yield-block-and-loop-state',[dict(op=last,polls=polls)])
add('release-and-resume-loop',[dict(op='loop',polls=[dict(result=1,control='release')]),dict(op='loop',polls=[dict(result=1,control='shutdown')])])
add('shutdown-before-loop-and-finished-loop',[dict(op='shutdown'),dict(op='step'),dict(op='loop'),dict(op='loop')])
add('release-before-loop',[dict(op='release'),dict(op='loop',polls=[dict(result=0,control='shutdown')])])
add('callback-failure-cleans-loop',[reg('a',1,True,callbackError=True),dict(op='loop',polls=[dict(events=[[123,1]])])])
for fd,event,multi in itertools.product([-2**31,-2,-1,2**31-1],[0,1,2,3],[False,True]):
 add('actual-native-failed-registration-retained-state',[reg('a',event,multi,fd),reg('b',2,False,fd),dispatch(1,fd),unreg('a',fd),close(fd)],native=True)
rng=random.Random(912203)
for i in range(1000):
 actions=[]
 for n in range(rng.randint(2,14)):
  op=rng.choice(['register','register','dispatch','unregister','close','step']);fd=rng.choice([-2**31,-1,0,31,32,123,155,2**31-1])
  if op=='register':action=reg(rng.choice(['a','b','c']),rng.randrange(8),rng.choice([False,True]),fd,callbackError=rng.randrange(20)==0)
  elif op=='dispatch':action=dispatch(rng.randrange(8),fd)
  elif op=='unregister':action=unreg(rng.choice(['a','b','c','unknown']),fd)
  elif op=='close':action=close(fd)
  else:action=dict(op='step',fd=fd,polls=[dict(result=rng.randrange(3))])
  if rng.randrange(5)==0:action['responses']=[rng.choice([False,9,22])]
  actions.append(action)
 add('seeded-original-registry-state-machine',actions)
for managers,count in itertools.product([1,2],[0,1,2,4]):
 for mode in ['ready','close','cancel']:
  variants=([dict(closeError=x) for x in [False,True]] if mode=='close' else [dict(cancel=k,replace=x) for k in sorted({0,count//2,count}) for x in [False,True]] if mode=='cancel' else [{}])
  for variant in variants:cs.append(dict(group='original-threadWait-closeFdWith-native-readiness-cancellation-and-finally',request=dict(api='waiters',mode=mode,count=count,managers=managers,**variant)))
cs.append(dict(group='native-release-resume-shutdown-control-wakeups',request=dict(api='lifecycle')))
for count in [1,2,63,64,65,70,90]:cs.append(dict(group='native-event-array-capacity-growth-and-queued-readiness',request=dict(api='burst',count=count)))
for start in range(0,len(cs),64):
 group=cs[start:start+64]
 for c,result in zip(group,ask([c['request'] for c in group])):
  if c['request']['api'] in ['waiters','lifecycle','burst']:assert 'error' not in result,(c['request'],result)
  c['expected']=result
m=dict(upstream='GHC 9.12.2 original Event.Manager/Internal/Control/EPoll/KQueue',platform=a.platform,abi=abi,abiHash=hashlib.sha256(asset.read_bytes()).hexdigest(),sources=hashes(),cases=len(cs),caseHash=digest(cs),groups=dict(collections.Counter(c['group'] for c in cs)))
target.parent.mkdir(parents=True,exist_ok=True)
with target.open('wb') as f,gzip.GzipFile(filename='',fileobj=f,mode='wb',mtime=0) as gz:
 for x in [m]+cs:gz.write((json.dumps(x,ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(json.dumps({k:v for k,v in m.items() if k not in ['sources','abi']},indent=2))
