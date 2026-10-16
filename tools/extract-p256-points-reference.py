#!/usr/bin/env python3
"""Verified development-only entry counters and raw point/table observations."""
import re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'vendor/crypton-2.0.1/cbits/p256/p256_ec.c'
NAMES=['felem_inv','point_double','point_add_mixed','point_add','point_add_or_double_vartime','copy_conditional','select_affine_point','select_jacobian_point','scalar_base_mult','point_to_affine','scalar_mult','crypton_p256_base_point_mul','crypton_p256_points_mul_vartime','crypton_p256e_point_add','crypton_p256e_point_negate','crypton_p256e_point_mul']
def body(s,name):
 start=s.index('{',s.index(name+'('))+1;end=start;level=1
 while level:level+=(s[end]=='{')-(s[end]=='}');end+=1
 return start,end-1

def observed():
 s=SOURCE.read_text();marker='#include "p256/p256_gf.h"';assert s.count(marker)==1;s=s.replace(marker,'/* Field header supplied by development observer. */')
 def change(name,old,new):
  nonlocal s
  start,end=body(s,name);chunk=s[start:end];assert chunk.count(old)==1,(name,old);s=s[:start]+chunk.replace(old,new)+s[end:]
 def append(name,text):
  nonlocal s
  start,end=body(s,name);s=s[:end]+'\n'+text+'\n'+s[end:]
 def snapshot(label,*args):
  return f'point_snapshot("{label}", '+', '.join(args)+(', NULL'*(3-len(args)))+f', {len(args)});'
 for i,name in enumerate(NAMES):
  start,end=body(s,name);s=s[:start]+f'\n point_ops[{i}]++;'+s[start:]
 for name,label,args in [
  ('felem_inv','inverse',['out']),('point_double','double',['x_out','y_out','z_out']),('point_add_mixed','mixed',['x_out','y_out','z_out']),('point_add','add',['x_out','y_out','z_out']),('point_add_or_double_vartime','add-or-double',['x_out','y_out','z_out']),('copy_conditional','copy',['out']),('select_affine_point','select-affine',['out_x','out_y']),('select_jacobian_point','select-jacobian',['out_x','out_y','out_z']),('scalar_base_mult','base-mult',['nx','ny','nz']),('point_to_affine','affine',['x_out','y_out']),('scalar_mult','scalar-mult',['nx','ny','nz'])]:append(name,snapshot(label,*args))
 change('point_add_or_double_vartime','    point_double(x_out, y_out, z_out, x1, y1, z1);','    point_branch("add-double-branch",1);\n    point_double(x_out, y_out, z_out, x1, y1, z1);\n    '+snapshot('add-or-double','x_out','y_out','z_out'))
 change('point_add_or_double_vartime','  felem_sum(r, r, r);','  point_branch("add-double-branch",0);\n  felem_sum(r, r, r);')
 for name,label in [('select_affine_point','table-affine'),('select_jacobian_point','table-jacobian')]:
  start,end=body(s,name);s=s[:start]+'\n const limb *point_table_start=table;'+s[start:]
  for output in (['out_x','out_y'] if name=='select_affine_point' else ['out_x','out_y','out_z']):
   old=f'      {output}[j] |= *table & mask;'
   change(name,old,f'      point_table("{label}",table-point_table_start,*table);\n'+old)
 change('scalar_base_mult','      n_is_infinity_mask &= ~p_is_noninfinite_mask;','      n_is_infinity_mask &= ~p_is_noninfinite_mask;\n      '+snapshot('base-window','nx','ny','nz'))
 change('scalar_mult','  memset(nx, 0, sizeof(felem));','  point_event("precomp",(const limb *)precomp,16*3*NLIMBS);\n  memset(nx, 0, sizeof(felem));')
 change('scalar_mult','    n_is_infinity_mask &= ~p_is_noninfinite_mask;','    n_is_infinity_mask &= ~p_is_noninfinite_mask;\n    '+snapshot('scalar-window','nx','ny','nz'))
 change('crypton_p256_points_mul_vartime','    return;','    point_event("both-zero",NULL,0);\n    return;')
 change('crypton_p256_points_mul_vartime','  if (crypton_p256_is_zero(n2) != 0) {','  if (crypton_p256_is_zero(n2) != 0) {\n    point_branch("double-scalar-branch",0);')
 change('crypton_p256_points_mul_vartime','  } else if (crypton_p256_is_zero(n1) != 0) {','  } else if (crypton_p256_is_zero(n1) != 0) {\n    point_branch("double-scalar-branch",1);')
 change('crypton_p256_points_mul_vartime','    point_add_or_double_vartime(x1, y1, z1, x1, y1, z1, x2, y2, z2);','    point_branch("double-scalar-branch",2);\n    point_add_or_double_vartime(x1, y1, z1, x1, y1, z1, x2, y2, z2);')
 return s
if __name__=='__main__':
 path=ROOT/'tools/p256-points-reference/ObservedPoints.c'
 if '--check' in sys.argv:assert path.read_text()==observed();print('Verified exact P256 point observation transformations')
 else:path.write_text(observed())
