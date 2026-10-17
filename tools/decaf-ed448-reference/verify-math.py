#!/usr/bin/env python3
"""Independent affine integer/Edwards model + hashlib SHAKE; development only."""
import argparse,gzip,hashlib,json,sys
from pathlib import Path
D=Path(__file__).resolve().parent;P=2**448-2**224-1;N=2**446-13818066809895115352007386748515426880336692474882178609894547503885
def add(a,b,twist=False):
 x,y=a;u,v=b;d=-39082 if twist else-39081;k=d*x*y*u*v%P
 return((x*v+y*u)*pow(1+k,-1,P)%P,(y*v+(x*u if twist else-x*u))*pow(1-k,-1,P)%P)
def mul(a,n,twist=False):
 out=(0,1)
 while n:
  if n&1:out=add(out,a,twist)
  a=add(a,a,twist);n>>=1
 return out
# Source literal generator, independent affine arithmetic and isogeny.
base=json.loads((D/'TableLiterals.json').read_text())['base'];x,y,z,t=[sum(v<<(56*i)for i,v in enumerate(limbs))%P for limbs in base];assert x*y%P==z*t%P
x,y=mul((x*pow(z,-1,P)%P,y*pow(z,-1,P)%P),pow(4,-1,N),True);u=(x*x+y*y)%P;v=(y*y-x*x)%P;den=u*(2-v)%P;G=((2-v)*2*x*y*pow(den,-1,P)%P,v*u*pow(den,-1,P)%P);assert (G[0]**2+G[1]**2-1+39081*G[0]**2*G[1]**2)%P==0

def encode(a):return list(a[1].to_bytes(56,'little'))+[(a[0]&1)<<7]
def hash(data,n):return hashlib.shake_256(bytes(data)).digest(n)
def expanded(secret):
 value=bytearray(hash(secret,114));value[0]&=252;value[56]=0;value[55]|=128;return int.from_bytes(value[:57],'little'),value[57:]
def sign(secret,public,message,ph,context):
 s,seed=expanded(secret);dom=b'SigEd448'+bytes([int(ph!=0),len(context)])+bytes(context);nonce=int.from_bytes(hash(dom+seed+bytes(message),114),'little')%N;R=encode(mul(G,nonce));challenge=int.from_bytes(hash(dom+bytes(R)+bytes(public)+bytes(message),114),'little')%N;return R+list(((nonce+challenge*s)%N).to_bytes(57,'little'))
def main(path,check=False):
 seen=set();counts={};observations=[]
 with gzip.open(path,'rt')as f:
  next(f)
  for line in f:
   row=json.loads(line);r=row['request'];op=r['op'];out=row['expected']['frames']['out'];key=json.dumps({k:v for k,v in r.items()if k not in['bits','scalarBits','vectorMasks32','prehashChunks']},sort_keys=True)
   if key in seen:continue
   seen.add(key)
   if r.get('prehashSignature'):
    expected=sign(r['secret'],r['public'],hash(r['message'],64),1,r.get('context',[]));assert expected==r['signature'] and row['expected']['ret']==-1;counts['prehashSignVerify']=counts.get('prehashSignVerify',0)+1;observations.append(dict(op='prehashSignVerify',inputSha256=hashlib.sha256(key.encode()).hexdigest(),expected=expected))
   if r.get('alias')or op not in ['derivePublicKey','sign','signInternal','convertPrivateKeyToX448']:continue
   if op=='derivePublicKey':expected=encode(mul(G,expanded(r['secret'])[0]));length=57
   elif op=='convertPrivateKeyToX448':expected=list(hash(r['secret'],56));length=56
   else:expected=sign(r['secret'],r['public'],r['message'],r.get('prehashed',0),r.get('context',[]));length=114
   assert out[32:32+length]==expected,(op,r);counts[op]=counts.get(op,0)+1;observations.append(dict(op=op,inputSha256=hashlib.sha256(key.encode()).hexdigest(),expected=expected))
 result=dict(kind='independent affine Edwards and hashlib SHAKE, not Kotlin expectations',generator=encode(G),counts=counts,observations=observations);artifact=D/'MathObservations.json'
 if check:assert json.loads(artifact.read_text())==result
 else:artifact.write_text(json.dumps(result,separators=(',',':'))+'\n')
 print('PASS independent affine Ed448/hashlib model',counts)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('corpus');p.add_argument('--check',action='store_true');a=p.parse_args();main(a.corpus,a.check)
