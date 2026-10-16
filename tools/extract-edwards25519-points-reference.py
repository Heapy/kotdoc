#!/usr/bin/env python3
"""Byte-exact pinned portable Donna point sources and mechanical loop observers."""
import hashlib,json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];V=ROOT/'vendor/crypton-2.0.1';D=ROOT/'tools/edwards25519-points-reference'
FILES=['ed25519-donna.h','ed25519-donna-impl-base.h','ed25519-crypton-exts.h','ed25519-donna-portable.h','ed25519-donna-portable-identify.h','curve25519-donna-32bit.h','curve25519-donna-64bit.h','curve25519-donna-helpers.h','modm-donna-32bit.h','modm-donna-64bit.h','ed25519-donna-basepoint-table.h','ed25519-donna-32bit-tables.h','ed25519-donna-64bit-tables.h','ed25519-donna-64bit-x86.h','ed25519-donna-64bit-x86-32bit.h']
def sources():
 meta=json.loads((V/'SOURCE.json').read_text());assert meta['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914';result={}
 for f in FILES:
  data=(V/'cbits/ed25519'/f).read_bytes();assert hashlib.sha256(data).hexdigest()==meta['files']['cbits/ed25519/'+f];result[D/'original'/f]=data
 base=(V/'cbits/ed25519/ed25519-donna-impl-base.h').read_text()
 assert len(re.findall(r'^ge25519_\w+\(',base,re.M))==18
 # Observer callbacks have no writes to the original point state.
 ob=base
 def insert(old,new):
  nonlocal ob
  assert ob.count(old)==1,(old,ob.count(old));ob=ob.replace(old,new)
 insert('\tr->z[0] = 2;','\tr->z[0] = 2;\n ep_snapshot("base:init",-1,r);')
 insert('\t\tge25519_nielsadd2(r, &t);\n\t}\n\tge25519_double_partial', '\t\tge25519_nielsadd2(r, &t);\n ep_snapshot("base:odd",i,r);\n\t}\n\tge25519_double_partial')
 insert('\tge25519_double(r, r);\n\tge25519_scalarmult_base_choose_niels','\tge25519_double(r, r);\n ep_snapshot("base:double4",-1,r);\n\tge25519_scalarmult_base_choose_niels')
 insert('\tge25519_nielsadd2(r, &t);\n\tfor(i = 2;', '\tge25519_nielsadd2(r, &t);\n ep_snapshot("base:even",0,r);\n\tfor(i = 2;')
 insert('\t\tge25519_nielsadd2(r, &t);\n\t}\n}', '\t\tge25519_nielsadd2(r, &t);\n ep_snapshot("base:even",i,r);\n\t}\n}')
 insert('\t\t\tge25519_p1p1_to_partial(r, &t);\n\t\t}\n\t}', '\t\t\tge25519_p1p1_to_partial(r, &t);\n\t\t}\n ep_snapshot("double",i,r);\n\t}')
 result[D/'ObservedBase.h']=ob.encode()
 ext=(V/'cbits/ed25519/ed25519-crypton-exts.h').read_text();assert len(re.findall(r'^ED25519_FN\(ed25519_.*',ext,re.M))==17
 # Five scalar exports are accepted separately;12 point exports plus two private selectors.
 ob=ext
 old='        ge25519_double(r, r);\n\n        // lower';assert ob.count(old)==1;ob=ob.replace(old,'        ge25519_double(r, r);\n ep_snapshot("multiply:high",i,r);\n\n        // lower')
 old='            ge25519_p1p1_to_full(r, &t);\n        }\n    }';assert ob.count(old)==1;ob=ob.replace(old,'            ge25519_p1p1_to_full(r, &t);\n        }\n ep_snapshot("multiply:low",i,r);\n    }')
 result[D/'ObservedExts.h']=ob.encode()
 original=(V/'tests/ECC/Edwards25519Spec.hs').read_text().replace('module ECC.Edwards25519Spec (spec) where','module OriginalEdwards25519Spec where');result[D/'OriginalEdwards25519Spec.hs']=original.encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('Verified point source18base+12exports+2private and mechanical loop observers')
