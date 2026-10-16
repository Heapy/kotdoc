#!/usr/bin/env python3
"""Pinned extracts plus independently verified normalized/observed/bounded C copies."""
import hashlib,json,re,runpy,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'tools/decaf448-point-multiplication-reference'
def sources():
 base=runpy.run_path(str(ROOT/'tools/extract-decaf448-points-reference.py'));base['sources'].__globals__['D']=D;result=base['sources']();source=(ROOT/'vendor/crypton-2.0.1/cbits/decaf/ed448goldilocks/decaf.c').read_text()
 needle='current -= delta << pos;';normalized='current -= ((int64_t)delta) * (((int64_t)1) << pos);';assert source.count(needle)==1
 plain=source.replace(needle,normalized);result[D/'NormalizedPoints.c']=plain.encode()
 observed=plain
 def matching(text,start):
  depth=1
  for i in range(start,len(text)):
   if text[i]=='{':depth+=1
   elif text[i]=='}':
    depth-=1
    if depth==0:return i
  raise AssertionError('unbalanced source')
 def transform(name,callback):
  nonlocal observed
  m=re.search(re.escape(name)+r'\s*\([^;]+?\)\s*\{',observed);assert m,name;end=matching(observed,m.end());body=observed[m.end():end];observed=observed[:m.end()]+callback(body)+observed[end:]
 def loop_end(body,header,line):
  assert body.count(header)==1,header;start=body.index(header)+len(header);end=matching(body,start);return body[:end]+line+'\n'+body[end:]
 transform('API_NS(point_scalarmul)',lambda b:loop_end(b,'for (; i>=0; i-=WINDOW) {','pm_fields_snapshot("scalarmul:window",i,(const word_t*)tmp,4*NLIMBS,bits,inv,0);'))
 transform('API_NS(point_double_scalarmul)',lambda b:loop_end(b,'for (; i>=0; i-=WINDOW) {','pm_fields_snapshot("double:window",i,(const word_t*)tmp,4*NLIMBS,bits1,bits2,0);'))
 transform('API_NS(point_dual_scalarmul)',lambda b:loop_end(b,'for (i=0; i<SCALAR_BITS; i+=WINDOW) {','pm_fields_snapshot("dual:window",i,(const word_t*)working,4*NLIMBS,bits1,bits2,0);'))
 def gray(b):
  n='crypton_gf_copy(zs[idx], pn_tmp->z);';assert b.count(n)==1;return b.replace(n,n+'pm_fields_snapshot("precompute:gray",i*16+j,(const word_t*)start,4*NLIMBS,idx,gray,0);')
 transform('API_NS(precompute)',gray)
 transform('API_NS(precomputed_scalarmul)',lambda b:loop_end(b,'for (j=0; j<n; j++) {','pm_fields_snapshot("comb:window",i*5+j,(const word_t*)out,4*NLIMBS,tab,invert,0);'))
 transform('API_NS(base_double_scalarmul_non_secret)',lambda b:loop_end(b,'for (i--; i >= 0; i--) {','pm_fields_snapshot("nonsecret:round",i,(const word_t*)combo,4*NLIMBS,contp,contv,0);'))
 transform('crypton_decaf_x448',lambda b:loop_end(b,'for (t = X_PRIVATE_BITS-1; t>=0; t--) {','pm_x448_snapshot(t,x2,z2,x3,z3,swap);'))
 def wnaf(b):
  assert b.count(normalized)==1;b=b.replace(normalized,'pm_wnaf_snapshot(0,w,current,position,pos,delta);'+normalized)
  n='            position--;';assert b.count(n)==1;return b.replace(n,n+'pm_wnaf_snapshot(1,w,current,position,pos,delta);')
 transform('recode_wnaf',wnaf)
 lines=[];count=0
 writes=['copy','add','sub','add_nr','sub_nr','subx_nr','weak_reduce','strong_reduce','mulw','mul','sqr','isr','cond_neg','cond_swap','cond_sel','deserialize']
 for line in observed.splitlines():
  # Scalar snapshots are after the source halve, before its caller consumes the words.
  m=re.search(r'API_NS\(scalar_halve\)\((\w+),',line)
  if m:line=line.replace(';',';pm_scalar_snapshot("scalar:halve",'+m[1]+');',1)
  m=re.search(r'crypton_gf_('+('|'.join(writes))+r')\s*\(\s*([^,;)]+)',line)
  if m and not line.lstrip().startswith(('/*','*','//')):
   name,target=m[1],m[2].strip();end=line.index(';')+1;before=line[:end];after=line[end:];snapshots='dp_snapshot("'+name+'",'+target+');'
   if name=='cond_swap':
    second=line[m.end():].split(',')[1].strip();snapshots='dp_snapshot("cond_swap0",'+target+');dp_snapshot("cond_swap1",'+second+');'
   conditional=re.match(r'(\s*if\s*\([^)]*\)\s*)(.*)',before)
   line=conditional[1]+'{'+conditional[2]+snapshots+'}'+after if conditional else before+snapshots+after;count+=1
  lines.append(line)
 assert count>180;observed='\n'.join(lines)+'\n';result[D/'ObservedPoints.c']=observed.encode()
 bounded=observed.replace(normalized,'if(delta<0){pm_wnaf_snapshot(2,w,current,position,pos,delta);return -777;}'+needle)
 assert bounded!=observed;result[D/'BoundedWnafPoints.c']=bounded.encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('Verified exact originals, one signed-multiply normalization, field/loop observers and bounded original wNAF prefix')
