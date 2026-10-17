#!/usr/bin/env python3
"""Independent original RSA Read lexical-demand and numeric-conversion probes."""
import json,pathlib,re,subprocess,argparse
D=pathlib.Path(__file__).parent
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args()
pub='PublicKey {public_size = 1, public_n = -2, public_e = 3}'
private='PrivateKey {private_pub = '+pub+', private_d = 4, private_p = 5, private_q = 6, private_dP = 7, private_dQ = 8, private_qinv = 9}'
values={'PublicKey':pub,'PrivateKey':private,'KeyPair':'KeyPair ('+private+')'}
fields=['public_size','public_n','public_e'];secrets=['private_d','private_p','private_q','private_dP','private_dQ','private_qinv']
def context(t,field,token,partial):
 s=values[t];m=re.search(r'\b'+field+r' = (-?\d+)',s);assert m
 return s[:m.start(1)]+token+(''if partial else s[m.end(1):])
qs=[]
def add(t,field,token,end,method,projection,partial):
 s=context(t,field,token,partial)
 if method.startswith('readList'):s='['+s+(''if partial else'] tail')
 qs.append(dict(type=t,site=field,token=token,method=method,projection=projection,input=s,end=end,prefixLength=3))
methods=['read','readPrec','readList','readListPrec']
# lexP alternatives and every branch of decimal fraction/exponent/base/quotes.
tokens=['',' ','-','--','-+','+','1','-1','1.','-1.','1.2','-1.2','(-1.2','1e','-1e','1e2','-1e2','1e+','-1e+2','1E-2','-1E-2','1.2e3','-1.2e-3','0x','-0x','0x1','-0b1','0o7','-hello','hello','_name','-"abc','"abc','-\'a',"'a",'-"abc"',"-'a'",'"\\x41','"\\NUL','"\\SO','"\\&','"\\  ','!','-!','(',')',',','[',']','{','}',"a'",'\\','€']
for field in ['public_size','public_n']:
 for token in tokens:
  for end in ['eof','tail','head']:
   for method in methods:
    for projection in ['ignore','alternative']:add('PublicKey',field,token,end,method,projection,True)
# Every inherited Int/Integer site, including nested public fields/newtype wrappers.
for t in values:
 for field in fields+([]if t=='PublicKey'else secrets):
  for token in ['-1.2','-1e2','(-1.2','-1e+2','-hello','-"abc',"-'a"]:
   for end in ['eof','tail','head']:
    for method in methods:add(t,field,token,end,method,'alternative',True)
  for token in ['-123','(-123)','((123))','0x1f','-0b101','0o77','18446744073709551617','-18446744073709551617','1.2','-1e2','-hello','-"abc"',"-'a'",'- (1)','+1']:
   for method in methods:
    for projection in ['value','remainder']:add(t,field,token,'eof',method,projection,False)
# Input-root and empty partial-input controls, precedence branch before fields.
for t in values:
 for method in methods:
  for projection in ['ignore','alternative']:
   qs.append(dict(type=t,method=method,projection=projection,input=values[t],end='root'))
wire=''.join(json.dumps(q)+'\n'for q in qs)
r=subprocess.run(['/tmp/kotdoc-rsa-types-lex-fix/original'],input=wire,capture_output=True,text=True,check=True)
lines=r.stdout.splitlines();assert len(lines)==len(qs)
rows=[dict(request=q,expected=json.loads(s))for q,s in zip(qs,lines)]
f=D/'LexObservations.json'
if a.verify:assert json.loads(f.read_text())==rows
else:f.write_text(json.dumps(rows,indent=2)+'\n')
print('PASS',len(rows),'original lexical-demand/finite conversion rows;21 numeric sites;4 Read methods')
