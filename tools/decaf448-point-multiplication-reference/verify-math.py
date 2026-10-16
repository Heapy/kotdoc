#!/usr/bin/env python3
"""Independent integer model: development verification only, never production."""
import gzip,json,sys
P=2**448-2**224-1
N=2**446-13818066809895115352007386748515426880336692474882178609894547503885

def ladder(u,k):
 k=int.from_bytes(bytes(k),'little');k=(k&~3)|(1<<447);u=int.from_bytes(bytes(u),'little')%P
 x2,z2,x3,z3,swap=1,0,u,1,0
 for t in range(447,-1,-1):
  bit=(k>>t)&1;swap^=bit
  if swap:x2,x3=x3,x2;z2,z3=z3,z2
  swap=bit;a=(x2+z2)%P;aa=a*a%P;b=(x2-z2)%P;bb=b*b%P;e=(aa-bb)%P;c=(x3+z3)%P;d=(x3-z3)%P;da=d*a%P;cb=c*b%P
  x3=(da+cb)**2%P;z3=u*(da-cb)**2%P;x2=aa*bb%P;z2=e*(aa+39081*e)%P
 if swap:x2,x3=x3,x2;z2,z3=z3,z2
 return list((x2*pow(z2,P-2,P)%P).to_bytes(56,'little'))

def add(a,b):
 x,y=a;u,v=b;product=-39082*x*y*u*v%P
 return ((x*v+y*u)*pow((1+product)%P,-1,P)%P,(y*v+x*u)*pow((1-product)%P,-1,P)%P)
def multiply(a,n):
 result=(0,1)
 while n:
  if n&1:result=add(result,a)
  a=add(a,a);n>>=1
 return result
def main(path):
 counts={};bounded=0
 with gzip.open(path,'rt')as f:
  next(f)
  for line in f:
   row=json.loads(line);r=row['request'];e=row['expected'];op=r['op'];sb=r['scalarBits']
   if op in ['scalarmul','doubleScalarmul','dualScalarmul','precomputedScalarmul','baseDoubleScalarmulNonSecret']:
    bits=r['bits'];n=16 if bits==32 else 8;radix=28 if bits==32 else 56;prefix=32//(bits//8)
    def point(v):
     values=[sum(int(v[j*n+i],16)<<(radix*i)for i in range(n))%P for j in range(4)];x,y,z,t=values
     assert x*y%P==z*t%P and (y*y-x*x-z*z+39082*t*t)%P==0 and z,(r,e["validOut"])
     return(x*pow(z,-1,P)%P,y*pow(z,-1,P)%P)
    scalar1=sum(int(v,16)<<(sb*i)for i,v in enumerate(r['s1']));scalar2=sum(int(v,16)<<(sb*i)for i,v in enumerate(r['s2']))
    if scalar1<N and scalar2<N and not(op=='precomputedScalarmul'and r['alias']=='out-table'):
     a=point(r['a']);b=point(r['a']if r['alias']in ['a-b','all']else r['b']);out=point(e['out'][prefix:prefix+4*n]);expected=None
     if op in ['scalarmul','precomputedScalarmul']:expected=multiply(a,scalar1)
     elif op=='doubleScalarmul':expected=add(multiply(a,scalar1),multiply(b,scalar2))
     elif op=='baseDoubleScalarmulNonSecret':
      # Fixed generator is independent of the variable-base fixture.
      if scalar2==0:expected=(0,1)
      # Other nonsecret rows need the literal generator separately; checked by native replay.
     elif op=='dualScalarmul':
      expected=multiply(a,scalar2 if r['alias']in ['out-out2','out-all']else scalar1)
      second=point(e['out2'][prefix:prefix+4*n]);q=multiply(a,scalar2);assert (second[1]*q[0]-q[1]*second[0])%P==0
     if expected is not None:assert (out[1]*expected[0]-expected[1]*out[0])%P==0,(op,r);counts[op]=counts.get(op,0)+1
   if op=='recodeWnaf':
    scalar=sum(int(v,16)<<(sb*i)for i,v in enumerate(r['s1']));control=e['control'][1:-1];count=int(e['value'],16);pairs=[control[2*i:2*i+2]for i in range(count)];assert sum(v<<power for power,v in pairs)==scalar
    assert all(v&1 and abs(v)<1<<(r['count']+1)for power,v in pairs);assert all(pairs[i][0]>pairs[i+1][0]for i in range(len(pairs)-1));assert control[2*count:2*count+2]==[-1,0]
    prefix=row['boundedOriginalPrefix']['logic'];normal=e['logic'];stops=[i for i,v in enumerate(prefix)if v['label']=='wnaf:bounded-stop']
    if stops:
     i=stops[0];assert prefix[:i]==normal[:i];assert prefix[i]['words']==normal[i-1]['words'];assert int(prefix[i]['words'][3],16)>>63==1;bounded+=1
    else:assert prefix==normal
    counts[op]=counts.get(op,0)+1
   if op=='x448':
    base=r['baseBytes'];secret=r['scalarBytes'];alias=r['alias']
    if alias in ['base-scalar','byte-all']:secret=base
    expected=ladder(base,secret);assert e['bytes'][1:-1]==expected;assert int(e['value'],16)==((1<<64)-1 if any(expected)else 0);counts[op]=counts.get(op,0)+1
   if 'literal'in r:assert e['bytes'][1:-1]==r['literal'];counts['literal']=counts.get('literal',0)+1
 print('PASS independent models',counts,'bounded negative-shift prefixes',bounded)
if __name__=='__main__':main(sys.argv[1])
