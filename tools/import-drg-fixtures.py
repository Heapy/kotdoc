#!/usr/bin/env python3
from pathlib import Path
import argparse,json,gzip,hashlib,subprocess,itertools
R=Path(__file__).resolve().parents[1];D=R/'tools/chacha-drg-reference';OUT=R/'core/src/test/resources/crypto-chacha-drg/data.jsonl.gz';p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--verify-oracle',action='store_true');p.add_argument('--refresh-pins',action='store_true');a=p.parse_args()
proc=subprocess.Popen([a.oracle],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
def call(r):
 proc.stdin.write(json.dumps(r,separators=(',',':'))+'\n');proc.stdin.flush();line=proc.stdout.readline();assert line,r;return json.loads(line)
def pattern(n,k=0):return[(i*73+k)%256 for i in range(n)]
if a.verify_oracle or a.refresh_pins:
 rows=[json.loads(s)for s in gzip.decompress(OUT.read_bytes()).decode().splitlines()];header=rows.pop(0)
 for i,row in enumerate(rows):assert call(row['request'])==row['expected'],(i,row)
 for path,h in header['sources'].items():
  current=hashlib.sha256((R/path).read_bytes()).hexdigest()
  if a.refresh_pins:header['sources'][path]=current
  else:assert current==h,path
 if a.refresh_pins:header['sources']['vendor/ram-0.22.1/SOURCE.json']=hashlib.sha256((R/'vendor/ram-0.22.1/SOURCE.json').read_bytes()).hexdigest()
 if a.refresh_pins:OUT.write_bytes(gzip.compress(('\n'.join(json.dumps(x,separators=(',',':'))for x in [header]+rows)+'\n').encode(),mtime=0))
else:
 secret=call(dict(mode='secret'));samples=call(dict(mode='samples'));requests=[]
 for size in [0,1,31,39,40,41,64,128]:
  for n in [-2147483648,-17,-1,0,1,15,16,17,31,32,33,63,64,65,129,513]:requests.append(dict(seed=pattern(size,11),lengths=[n,0,1,15,16,17,0,33,-1,65]))
 for words in [['0']*5,['18446744073709551615']*5,['1','2','3','4','5'],['72623859790382856','1230066625199609624','2387509390608836392','3544952156018063160','4702394921427289928']]+samples:
  requests.append(dict(seedMode='words',words=words,lengths=[0,1,15,16,17,31,32,33,63,64,65,-1]))
 for k in [0,1,7,8,63,64,127,255,319,320,321,511,640,1024]:
  for n in sorted({(1<<k)-1,1<<k,(1<<k)+1,-(1<<k)-1,-(1<<k),-(1<<k)+1}):
   requests.append(dict(mode='seedInteger',integer=str(n)));requests.append(dict(seedMode='integer',integer=str(n),lengths=[1,16,17,33,0,-1,65]))
 for n in range(0,82):requests.append(dict(mode='seedBinary',seed=pattern(n,19)))
 for i in range(100):
  for action in ['sequence','map','apply','bind','pure','ignoreMap']:
   requests.append(dict(seed=pattern(40,i),action=action,length=i%66,other=(i*7)%67,lengths=[-1,0,i%66,17,33,65]))
  for action in ['sequence','map','apply','bind','pure','ignoreMap']:
   requests.append(dict(mode='probabilistic',material=pattern(i*3,i),secret=secret,action=action,length=i%66,other=(i*7)%67,lengths=[-1,0,i%66,17,33,65]))
  requests.append(dict(mode='seedNew',seed=pattern(40,i)))
  requests.append(dict(mode='seedNew',seed=pattern(40,i),insecure=True))
  for insecure in [False,True]:requests.append(dict(mode='drgNew',seed=pattern(40,i),insecure=insecure))
  for length in [-1,0,1,17,65]:
   for ignore in [False,True]:requests.append(dict(mode='withRandomBytes',seed=pattern(40,i),length=length,ignore=ignore))
  requests.append(dict(mode='insecureSeedFormula',entropy=pattern(64,i)))
 for n in [0,1,31,32,39,40,41,63,64,65,100]:
  for insecure in [False,True]:requests.append(dict(mode='seedProvider',entropy=pattern(n,27),insecure=insecure))
 for name in ['prob-ignore','prob-zero','drg-zero','drg-negative','with-ignore','map-ignore','apply-ignore','bind-ignore','pure-ignore']:requests.append(dict(mode='demand',name=name))
 rows=[]
 for i,r in enumerate(requests):
  observe=dict(r)
  if r.get('mode')=='probabilistic':observe['actual']=True
  rows.append(dict(request=r,expected=call(observe)))
 paths=[Path(__file__),R/'tools/extract-drg-reference.py',R/'licenses/crypton-BSD-3-Clause.txt',R/'licenses/ram-BSD-3-Clause.txt',R/'vendor/crypton-2.0.1/SOURCE.json',R/'vendor/ram-0.22.1/SOURCE.json']+sorted(p for p in D.rglob('*')if p.is_file()and'work-in-progress'not in p.parts)
 header=dict(cases=len(rows),sources={str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths})
 OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_bytes(gzip.compress(('\n'.join(json.dumps(x,separators=(',',':'))for x in [header]+rows)+'\n').encode(),mtime=0))
proc.stdin.close();proc.wait();assert proc.returncode==0,proc.returncode
print('PASS',len(rows),'actual DRG/Types/Seed and ordinary-observed/controlled Probabilistic rows',len(header['sources']),'pins')
