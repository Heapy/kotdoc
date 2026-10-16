#!/usr/bin/env python3
import hashlib,json,sys,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];V=ROOT/'vendor/crypton-2.0.1';D=ROOT/'tools/decaf448-field-reference'
FILES=['include/field.h','include/word.h','include/constant_time.h','include/portable_endian.h','include/decaf/common.h','include/arch_32/arch_intrinsics.h','include/arch_ref64/arch_intrinsics.h','p448/f_field.h','p448/f_generic.c','p448/f_arithmetic.c','p448/arch_32/f_impl.h','p448/arch_32/f_impl.c','p448/arch_ref64/f_impl.h','p448/arch_ref64/f_impl.c']
def sources():
 meta=json.loads((V/'SOURCE.json').read_text());assert meta['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914';result={}
 for f in FILES:
  p=V/'cbits/decaf'/f;data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==meta['files']['cbits/decaf/'+f];result[D/'original'/f]=data
 text=(V/'cbits/decaf/p448/f_arithmetic.c').read_text();observed=[];lines=text.splitlines();stage=0
 for line in lines:
  observed.append(line)
  if re.match(r'    crypton_gf_(sqr|sqrn|mul|copy)\s*\(',line):
   target=re.search(r'\(\s*(\w+)',line)[1];observed.append(f'    df_snapshot("stage{stage}",{target});');stage+=1
 assert stage==27;result[D/'ObservedArithmetic.c']=('\n'.join(observed)+'\n').encode()
 inventory={}
 for p in sorted((V/'cbits/decaf').rglob('*')):
  if p.is_file():
   t=p.read_text();inventory[str(p.relative_to(V))]=dict(sha256=hashlib.sha256(p.read_bytes()).hexdigest(),lines=len(t.splitlines()),apiMacroIdentifiers=sorted(set(re.findall(r'API_NS\((\w+)\)',t))))
 for name in ['Crypto/PubKey/Curve448.hs','Crypto/PubKey/Ed448.hs','tests/Curve448Spec.hs','tests/Ed448Spec.hs']:
  p=V/name;inventory[name]=dict(sha256=hashlib.sha256(p.read_bytes()).hexdigest(),lines=len(p.read_text().splitlines()))
 result[D/'RemainingSourceInventory.json']=(json.dumps(inventory,indent=2)+'\n').encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('Verified exact p448 originals and27ISRstage observer')
