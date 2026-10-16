#!/usr/bin/env python3
import hashlib,json,sys,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];V=ROOT/'vendor/crypton-2.0.1';D=ROOT/'tools/decaf448-points-reference'
FILES=['include/field.h','include/word.h','include/constant_time.h','include/portable_endian.h','include/decaf/common.h','include/arch_32/arch_intrinsics.h','include/arch_ref64/arch_intrinsics.h','p448/f_field.h','p448/f_generic.c','p448/f_arithmetic.c','p448/arch_32/f_impl.h','p448/arch_32/f_impl.c','p448/arch_ref64/f_impl.h','p448/arch_ref64/f_impl.c','include/decaf.h','include/decaf/point_448.h','include/decaf/point_255.h','include/decaf/ed448.h','include/decaf/shake.h','include/decaf/sha512.h','ed448goldilocks/decaf.c','ed448goldilocks/decaf_tables.c','ed448goldilocks/scalar.c','utils.c']
def sources():
 meta=json.loads((V/'SOURCE.json').read_text());assert meta['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914';result={}
 for f in FILES:
  p=V/'cbits/decaf'/f;data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==meta['files']['cbits/decaf/'+f];result[D/'original'/f]=data
 p=V/'cbits/crypton_sha3.h';data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==meta['files']['cbits/crypton_sha3.h'];result[D/'original/crypton_sha3.h']=data
 point=(V/'cbits/decaf/ed448goldilocks/decaf.c').read_text();observed=[];count=0
 matrix=json.loads((D/'SourceMatrix.json').read_text());assert len(matrix['functions'])==45;assert sum(f['checkpoint']=='geometry-codecs-niels'for f in matrix['functions'])==28
 for row in matrix['functions']:
  prefix='\n'.join(point.splitlines()[row['line']-1:row['line']+6]);assert row['name']in prefix,row
 for name,up in [('OriginalEd448Spec.hs','tests/Ed448Spec.hs'),('OriginalCurve448Spec.hs','tests/Curve448Spec.hs')]:
  original=(V/up).read_text();renamed=original.replace('module '+Path(up).stem+' (spec) where','module '+Path(name).stem+' where');assert renamed!=original;result[D/name]=renamed.encode()

 writes=['copy','add','sub','add_nr','sub_nr','subx_nr','weak_reduce','mulw','mul','sqr','isr','cond_neg','cond_swap','cond_sel','deserialize']
 for line in point.splitlines():
  m=re.search(r'crypton_gf_('+('|'.join(writes))+r')\s*\(\s*([^,;)]+)',line)
  if m and not line.lstrip().startswith(('/*','*','//')):
   name,target=m[1],m[2].strip();assert line.count(';')==1,line
   end=line.index(';')+1;before=line[:end];after=line[end:]
   snapshots='dp_snapshot("'+name+'",'+target+');'
   if name=='cond_swap':
    second=line[m.end():].split(',')[1].strip();snapshots='dp_snapshot("cond_swap0",'+target+');dp_snapshot("cond_swap1",'+second+');'
   conditional=re.match(r'(\s*if\s*\([^)]*\)\s*)(.*)',before)
   if conditional:line=conditional[1]+'{'+conditional[2]+snapshots+'}'+after
   else:line=before+snapshots+after
   count+=1
  observed.append(line)
 assert count>180,count
 result[D/'ObservedPoints.c']=('\n'.join(observed)+'\n').encode()
 text=(V/'cbits/decaf/ed448goldilocks/decaf_tables.c').read_text()
 literals=re.findall(r'FIELD_LITERAL\(([^)]+)\)',text);assert len(literals)==340
 words=[[int(v,16)for v in re.findall(r'0x[0-9a-fA-F]+',part)]for part in literals];assert all(len(v)==8 for v in words)
 result[D/'TableLiterals.json']=(json.dumps(dict(base=words[:4],comb=words[4:244],wnaf=words[244:]),separators=(',',':'))+'\n').encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('Verified exact Decaf point originals and all340tableelements')
