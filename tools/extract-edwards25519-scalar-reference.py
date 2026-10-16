#!/usr/bin/env python3
import hashlib,json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];FOLDER=ROOT/'tools/edwards25519-scalar-reference';V=ROOT/'vendor/crypton-2.0.1'
FILES=['modm-donna-32bit.h','modm-donna-64bit.h','ed25519-donna-portable.h','ed25519-donna-portable-identify.h']
def sources():
 meta=json.loads((V/'SOURCE.json').read_text());assert meta['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914';result={}
 for name in FILES:
  p=V/'cbits/ed25519'/name;data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==meta['files']['cbits/ed25519/'+name];result[FOLDER/'original'/name]=data
 for bits,n in [(32,9),(64,5)]:
  text=(V/f'cbits/ed25519/modm-donna-{bits}bit.h').read_text();assert len(re.findall(r'^\w+256_\w+\(|^lt_modm\(',text,re.M))==16
  # Add observers only after complete local/output stages; preserve all original arithmetic.
  observed=text.replace('static bignum256modm_element_t\nlt_modm','static void sm_snapshot(const char*,const bignum256modm);\n\nstatic bignum256modm_element_t\nlt_modm',1)
  marker='\n\tmul64x64_128(c, modm_m[0], q3[0])'if bits==64 else'\n\tc = mul32x32_64(modm_m[0], q3[0]);'
  assert observed.count(marker)==1;observed=observed.replace(marker,'\n\tsm_snapshot("q3",q3);'+marker)
  marker='\n\tpb = 0;\n\tpb += r2[0]';assert observed.count(marker)==1;observed=observed.replace(marker,'\n\tsm_snapshot("r2",r2);'+marker)
  marker='\n\treduce256_modm(r);\n\treduce256_modm(r);';assert observed.count(marker)==1;observed=observed.replace(marker,'\n\tsm_snapshot("difference",r);'+marker)
  marker='\n\tbarrett_reduce256_modm(r, q1, r1);';assert observed.count(marker)==1;observed=observed.replace(marker,'\n\tsm_snapshot("q1",q1);\n\tsm_snapshot("r1",r1);'+marker)
  start=observed.index('reduce256_modm(');end=observed.index('\n}',start);observed=observed[:end]+'\n\tsm_snapshot("reduce",r);'+observed[end:]
  result[FOLDER/f'Observed{bits}.h']=observed.encode()
 original=(V/'tests/ECC/Edwards25519Spec.hs').read_text().replace('module ECC.Edwards25519Spec (spec) where','module OriginalEdwards25519Spec where');result[FOLDER/'OriginalEdwards25519Spec.hs']=original.encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('Verified exact modm originals/16helpers/profile and mechanical stage observers')
