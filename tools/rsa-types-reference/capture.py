#!/usr/bin/env python3
import json,subprocess,pathlib,argparse
p=argparse.ArgumentParser();p.add_argument('--oracle',default='/tmp/kotdoc-rsa-types-oracle');p.add_argument('--verify',action='store_true');a=p.parse_args();d=pathlib.Path(__file__).parent
qs=[]
for t in ['PublicKey','PrivateKey','KeyPair']:
 for op in ['show','debug','metadata','datatype','constructor','root','nfdata']:
  if op=='debug'and t=='PublicKey':continue
  for bad in [[],['publicRoot'],['privateRoot'],['pairRoot'],['size'],['n'],['e'],['pub'],['d'],['p'],['q'],['dP'],['dQ'],['qinv'],['n','e','size'],['d','p','q']]:
   for prec in ([0,10,11]if op=='show'and not bad else[0]):qs.append(dict(type=t,op=op,bad=bad,precedence=prec))
 for selector in ['size','n','e','d']:
  for bad in [[],['size'],['n'],['e'],['pub'],['d'],['privateRoot'],['pairRoot']]:qs.append(dict(type=t,op='selector',selector=selector,bad=bad))
for t in ['PublicKey','PrivateKey']:
 for bad in [[],['publicRoot'],['privateRoot'],['size'],['n'],['d']]:qs.append(dict(type=t,op='generic',bad=bad))
for bad in [[],['r'],['inverse'],['r','inverse']]:qs.append(dict(type='Blinder',op='root',bad=bad))
for e in range(5):qs.append(dict(type='Error',op='show',error=e))
public='PublicKey {public_size = 1, public_n = -2, public_e = 3}'
private='PrivateKey {private_pub = '+public+', private_d = 4, private_p = 5, private_q = 6, private_dP = 7, private_dQ = 8, private_qinv = 9}'
for t,value in [('PublicKey',public),('PrivateKey',private),('KeyPair','KeyPair ('+private+')')]:
 examples=[value,value+' tail',' '+value,'('+value+')','(('+value+'))',value.replace(' = ','='),value.replace(', ',',\n'),value.replace('1','0x10',1),value.replace('1','0o20',1),value.replace('1','0b10',1),value.replace('1','9223372036854775808',1),value.replace('1','1e2',1),value.replace('1','1.0',1),value.replace('1','+1',1),value.replace(' = 1',' = (-1)',1),value.replace('public_size','public_e',1),value.replace('public_size = 1, public_n = -2','public_n = -2, public_size = 1'),value[:-1],value+'}',value.replace('PublicKey {','PublicKey{'),value.replace(' = 1',' = 1..',1)]
 for s in examples:
  for prec in [0,10,11,12]:qs.append(dict(type=t,op='read',input=s,precedence=prec))
raw=''.join(json.dumps(q)+'\n'for q in qs)
r=subprocess.run([a.oracle],input=raw,text=True,capture_output=True,check=True);lines=r.stdout.splitlines();assert len(lines)==len(qs),(len(lines),len(qs))
rows=[dict(request=q,expected=json.loads(line))for q,line in zip(qs,lines)]
f=d/'Observations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
print('PASS',len(rows),'original RSA.Types method observations')
