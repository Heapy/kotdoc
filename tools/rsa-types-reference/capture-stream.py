#!/usr/bin/env python3
import pathlib,json,subprocess,argparse
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent;qs=[]
base=json.loads((d/'Observations.json').read_text())
for row in base:
 q=row['request']
 if q['op']=='read':qs.append(dict(q,op='readPrec'))
for t in ['PublicKey','PrivateKey','KeyPair']:
 show=next(r['expected']for r in base if r['request']==dict(type=t,op='show',bad=[],precedence=0))
 value=show if t=='PublicKey'else next(r['expected']for r in base if r['request']==dict(type=t,op='debug',bad=[],precedence=0))
 for op in ['readList','readListPrec']:
  for s in ['[] tail','['+value+'] tail','['+value+','+value+']rest','(['+value+'])tail','['+value+',]','[,'+value+']','['+value+' '+value+']','['+value+']x']:
   for prec in [0,12]:qs.append(dict(type=t,op=op,input=s,precedence=prec))
 for op in ['showPrefix','showListPrefix','debugPrefix']:
  if op=='debugPrefix'and t=='PublicKey':continue
  lengths=sorted(set([-1,0,1,8,9,10,11,12,24,25,26,27,len(show),len(show)+1,len(show)+6,512]))
  for bad in [[],['publicRoot'],['privateRoot'],['pairRoot'],['pub'],['size'],['d'],['suffix'],['listTail']]:
   for n in lengths:qs.append(dict(type=t,op=op,bad=bad,prefixLength=n,precedence=0))
r=subprocess.run(['/tmp/kotdoc-rsa-types-oracle'],input=''.join(json.dumps(q)+'\n'for q in qs),capture_output=True,text=True,check=True);lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)];f=d/'StreamObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'source Show/Read streaming/list observations')
