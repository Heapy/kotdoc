#!/usr/bin/env python3
"""Mechanical development-only observation hooks; arithmetic and pinned files stay unchanged."""
import re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'vendor/crypton-2.0.1/cbits/p256/p256.c'
def extract():
 s=source.read_text()
 marker='#include "p256/p256.h"'
 assert s.count(marker)==1
 s=s.replace(marker,marker+'''\n#include <setjmp.h>
static uint64_t word_ops[28], word_corrections;
static int word_limit, word_steps, word_stopped;
static jmp_buf word_jump;
static crypton_p256_int word_state[4];
''')
 names=['mulAdd','subTop','subM','addM','crypton_p256_init','crypton_p256_clear','crypton_p256_get_bit','crypton_p256_is_zero','crypton_p256_is_odd','crypton_p256_is_even','crypton_p256_shl','crypton_p256_shr','crypton_p256_shr1','crypton_p256_cmp','crypton_p256_sub','crypton_p256_add','crypton_p256_add_d','crypton_p256_mod','crypton_p256_modmul','crypton_p256_modinv_vartime','crypton_p256_is_valid_point','crypton_p256_from_bin','crypton_p256_to_bin','crypton_p256e_modadd','crypton_p256e_modsub','crypton_p256e_montmul','crypton_p256e_scalar_invert']
 for i,name in enumerate(names):
  pattern=r'(?m)^([^\n;]*\b'+re.escape(name)+r'\([^;]*?\)\s*\{)'
  s,n=re.subn(pattern,lambda m:m[0]+f'\n  word_ops[{i}]++;',s,count=1)
  assert n==1,name
 marker='  for (;;) {\n    if (crypton_p256_is_even(&U)) {'
 assert s.count(marker)==1
 s=s.replace(marker,'''  for (;;) {
    word_ops[27]++; word_steps++;
    if (word_limit > 0 && word_steps > word_limit) {
      word_stopped=1; word_state[0]=R;word_state[1]=S;word_state[2]=U;word_state[3]=V;
      longjmp(word_jump,1);
    }
    if (crypton_p256_is_even(&U)) {''')
 marker='    top = addM(MOD, top, tmp + i, 0 - (top >> (P256_BITSPERDIGIT - 1)));'
 assert s.count(marker)==1
 s=s.replace(marker,'    word_corrections += top >> (P256_BITSPERDIGIT - 1);\n'+marker)
 return s
if __name__=='__main__':
 import sys
 target=root/'tools/p256-words-reference/ObservedP256.c'
 if '--check' in sys.argv:assert target.read_text()==extract();print('Verified exact P256 observation transformations')
 else:target.write_text(extract())
