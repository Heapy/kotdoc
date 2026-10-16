#!/usr/bin/env python3
import hashlib,json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];V=ROOT/'vendor/crypton-2.0.1';D=ROOT/'tools/decaf448-scalar-reference'
FILES=['include/word.h','include/constant_time.h','include/portable_endian.h','include/decaf/common.h','include/decaf.h','include/decaf/point_448.h','include/decaf/point_255.h','include/arch_32/arch_intrinsics.h','include/arch_ref64/arch_intrinsics.h','ed448goldilocks/scalar.c','utils.c']
def sources():
 meta=json.loads((V/'SOURCE.json').read_text());assert meta['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914';result={}
 for f in FILES:
  p=V/'cbits/decaf'/f;data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==meta['files']['cbits/decaf/'+f];result[D/'original'/f]=data
 for name,up in [('OriginalEd448Spec.hs','tests/Ed448Spec.hs'),('OriginalCurve448Spec.hs','tests/Curve448Spec.hs')]:
  original=(V/up).read_text();renamed=original.replace('module '+Path(up).stem+' (spec) where','module '+Path(name).stem+' where');assert renamed!=original
  result[D/name]=renamed.encode()
 text=(V/'cbits/decaf/ed448goldilocks/scalar.c').read_text();observed=text
 def add(old,new):
  nonlocal observed
  assert observed.count(old)==1,(old,observed.count(old));observed=observed.replace(old,new)
 add('    chain = 0;\n    for (i=0; i<SCALAR_LIMBS; i++) {','    ds_snapshot(1,"subx:diff",-1,out->limb,SCALAR_LIMBS,borrow,0,0);\n    chain = 0;\n    for (i=0; i<SCALAR_LIMBS; i++) {')
 add('\n}\n\nstatic CRYPTON_DECAF_NOINLINE void sc_montmul','\n    ds_snapshot(1,"subx:sum",-1,out->limb,SCALAR_LIMBS,0,0,0);\n}\n\nstatic CRYPTON_DECAF_NOINLINE void sc_montmul')
 add('        hi_carry = chain >> WBITS;','        hi_carry = chain >> WBITS;\n        ds_snapshot(1,"mont",i,accum,SCALAR_LIMBS+1,hi_carry,0,0);')
 add('    /* Sliding window */','    for(i=0;i<=LAST;i++) ds_snapshot(2,"inverse:table",i,precmp[i]->limb,SCALAR_LIMBS,0,0,0);\n    /* Sliding window */')
 add('        trailing <<= 1;','        trailing <<= 1;\n        ds_snapshot(2,i>=0?"inverse:prefix":"inverse:zero-tail",i,out->limb,SCALAR_LIMBS,residue,trailing,started);')
 add('    crypton_decaf_bzero(precmp, sizeof(precmp));','    crypton_decaf_bzero(precmp, sizeof(precmp));\n    ds_snapshot(2,"inverse:final",-1,out->limb,SCALAR_LIMBS,0,0,0);')
 add('    scalar_decode_short(t1, &ser[i], ser_len-i);','    scalar_decode_short(t1, &ser[i], ser_len-i);\n    ds_snapshot(3,"long:initial",i,t1->limb,SCALAR_LIMBS,0,0,0);')
 add('        API_NS(scalar_destroy)(t1);\n        return;','        API_NS(scalar_destroy)(t1);\n        ds_snapshot(3,"long:final",-1,s->limb,SCALAR_LIMBS,0,0,0);\n        return;')
 add('        API_NS(scalar_add)(t1, t1, t2);','        API_NS(scalar_add)(t1, t1, t2);\n        ds_snapshot(3,"long:chunk",i,t1->limb,SCALAR_LIMBS,0,0,0);')
 add('    API_NS(scalar_destroy)(t2);','    API_NS(scalar_destroy)(t2);\n    ds_snapshot(3,"long:final",-1,s->limb,SCALAR_LIMBS,0,0,0);')
 needle='residue = (residue<<1) | ((w>>(i%WBITS))&1);';assert observed.count(needle)==1
 normalized=observed.replace(needle,'residue = (residue<<1) | ((i>=0) ? ((w>>(i%WBITS))&1) : 0);')
 result[D/'NormalizedObservedScalar.c']=normalized.encode()
 stop='        if (started) sc_montsqr(out,out);';assert observed.count(stop)==1
 bounded=observed.replace(stop,'        if(i<0){ds_snapshot(2,"inverse:bounded-stop",i,out->limb,SCALAR_LIMBS,residue,trailing,started);return (crypton_decaf_error_t)-777;}\n'+stop)
 result[D/'BoundedObservedScalar.c']=bounded.encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('Verified scalar originals, separately normalized-zero-tail and defined-prefix observers')
