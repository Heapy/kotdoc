#!/usr/bin/env python3
"""Independent projective integer specifications, never fixture generation."""
import gzip,json,argparse,hashlib
from pathlib import Path
P=2**448-2**224-1;D=-39082;F=Path(__file__).resolve().parents[2]/'core/src/test/resources/crypto-decaf448-points/data.jsonl.gz'
def valid(a):
 x,y,z,t=a;return x*y%P==z*t%P and (y*y-x*x-z*z-D*t*t)%P==0 and z!=0
def affine(a):x,y,z,t=a;return(x*pow(z,-1,P)%P,y*pow(z,-1,P)%P)
def addition(a,b,subtract=False):
 x,y=affine(a);u,v=affine(b)
 if subtract:u=-u
 product=D*x*y*u*v%P
 if (1+product)%P==0 or (1-product)%P==0:return None
 return((x*v+y*u)*pow(1+product,-1,P)%P,(y*v+x*u)*pow(1-product,-1,P)%P)
parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
counts={};rangeObservations=[];witnesses=[]
with gzip.open(F,'rt')as f:
 next(f)
 for line in f:
  row=json.loads(line);r=row['request'];e=row['expected'];op=r['op'];bits=r['bits'];n=16 if bits==32 else 8;radix=28 if bits==32 else 56;prefix=32//(bits//8)
  if op in ['constant','table','profile']:continue
  def point(words):return tuple(sum(int(v,16)<<(radix*i)for i,v in enumerate(words[j*n:(j+1)*n]))%P for j in range(4))
  a=point(r.get('a',['0']*(4*n)));b=point(r.get('b',['0']*(4*n)));out=point(e['out'][prefix:-prefix]);alias=r.get('alias','none')
  if alias in ['a-b','all']:b=a
  if r.get('boundary')in ['same-math-base-plus-p','tight-base-control']and op=='add':
   x,y,z,t=a;u,v,w,h=b
   bb=(y-x)%P;cc=(v-u)%P;dd=(v+u)%P;aa=cc*bb%P;bb=(y+x)%P;py=dd*bb%P
   ideal=[(y-x)%P,(v-u)%P,dd,aa,bb,py];bb=t*h%P;px=bb*78164%P;bb=(aa+py)%P;cc=(py-aa)%P;aa=z*w%P;aa2=2*aa%P
   ideal += [t*h%P,px,bb,cc,aa,aa2]
   if bits==32:ideal.append(aa2)
   py=(aa2+px)%P;aa=(aa2-px)%P;ideal += [py,aa,aa*py%P,py*cc%P,aa*bb%P,bb*cc%P]
   trace=row['expected']['trace'];assert len(ideal)==len(trace)
   differences=[i for i,(expected,state)in enumerate(zip(ideal,trace))if sum(int(v,16)<<(radix*j)for j,v in enumerate(state['words']))%P!=expected]
   witnesses.append(dict(bits=bits,scalarBits=r['scalarBits'],vectorMasks32=r.get('vectorMasks32',False),boundary=r['boundary'],firstDifferingFieldStage=differences[0]if differences else None,firstDifferingFieldOperation=trace[differences[0]]['label']if differences else None,sourcePointAddLine=346 if differences and differences[0]==5 else None))
  result=None
  if op=='invert':assert out[0]==0 if a[0]==0 else out[0]*a[0]%P==1;result=True
  elif op=='valid':assert bool(int(e['value'],16))==valid(a);result=True
  elif op=='select':assert out==(b if int(r.get('mask','0'),16)&((1<<r['scalarBits'])-1) else a);result=True
  elif op=='equal':assert bool(int(e['value'],16))==((a[1]*b[0]-b[1]*a[0])%P==0);result=True
  elif op in ['decode','decodeEd'] and int(e['value'],16):assert valid(out);result=True
  elif op=='encode' and valid(a):assert int.from_bytes(bytes(e['bytes'][1:57]),'little')<P and e['bytes'][1]%2==0;result=True
  elif op in ['add','sub','double','doubleInternal'] and valid(a)and (op in ['double','doubleInternal']or valid(b)):
   expected=addition(a,a if op in ['double','doubleInternal']else b,op=='sub')
   if expected is not None:
    sourceA=r.get('a',[]);sourceB=sourceA if alias in ['a-b','all']or op in ['double','doubleInternal']else r.get('b',[])
    tight=all(int(v,16)<=(1<<radix)for v in sourceA+sourceB)
    matches=out[2]!=0 and affine(out)==expected
    if tight:assert matches,(r,affine(out),expected);result=True
    else:rangeObservations.append(dict(requestHash=hashlib.sha256(json.dumps(r,sort_keys=True,separators=(',',':')).encode()).hexdigest(),bits=bits,scalarBits=r['scalarBits'],vectorMasks32=r.get('vectorMasks32',False),op=op,boundary=r.get('boundary'),matchesGroupMathematics=matches))
  elif op in ['negate','torque','pscale','copy'] and valid(a):
   expected=affine(a)
   if op in ['negate','torque']:expected=(-expected[0]%P,(-expected[1]if op=='torque'else expected[1])%P)
   assert valid(out)and affine(out)==expected,(r,out,expected);result=True
  if result:counts[op]=counts.get(op,0)+1
print('Independent projective mathematics PASS',sum(counts.values()),json.dumps(counts,sort_keys=True))

artifact=F.parents[5]/'tools/decaf448-points-reference/RangeObservations.json'
# Locate repository through this verifier, independently of fixture-directory depth.
artifact=Path(__file__).resolve().parent/'RangeObservations.json'
if args.check:assert json.loads(artifact.read_text())==rangeObservations
else:artifact.write_text(json.dumps(rangeObservations,indent=2)+'\n')
print('Non-tight original observations',len(rangeObservations),'group discrepancies',sum(not r['matchesGroupMathematics']for r in rangeObservations))

witnessPath=Path(__file__).resolve().parent/'RangeWitnesses.json'
if args.check:assert json.loads(witnessPath.read_text())==witnesses
else:witnessPath.write_text(json.dumps(witnesses,indent=2)+'\n')
print('Range witness first differing stages',[(w['bits'],w['boundary'],w['firstDifferingFieldStage'])for w in witnesses])
