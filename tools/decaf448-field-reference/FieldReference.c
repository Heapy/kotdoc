/* Dev-only portable profiles. Original arithmetic files are unchanged. */
#define __STDC_WANT_LIB_EXT1__ 1
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <sys/types.h>
#include <inttypes.h>
/* Select the generic word-level conditional helpers, without native SIMD.
 * A forced32 profile on a64 host must not select a64-bit big_register_t. */
#undef __ARM_NEON__
#undef __SSE2__
#undef __AVX2__
#if FIELD_BITS==32
#undef __aarch64__
#undef __amd64__
#undef __x86_64__
#undef __X86_64__
#endif
#define CRYPTON_DECAF_WORD_BITS FIELD_BITS
#include "field.h"
#include "original/p448/f_generic.c"
#if FIELD_BITS==32
#include "original/p448/arch_32/f_impl.c"
#else
#include "original/p448/arch_ref64/f_impl.c"
#endif
static unsigned traces;
static uint64_t trace[32][16];
static char labels[32][16];
static void df_snapshot(const char*label,const gf a){assert(traces<32);strcpy(labels[traces],label);for(unsigned i=0;i<NLIMBS;i++)trace[traces][i]=a->limb[i];traces++;}
#ifdef OBSERVED
#include "ObservedArithmetic.c"
#else
#include "original/p448/f_arithmetic.c"
#endif
unsigned df_trace_count(void){return traces;}
const char*df_trace_label(unsigned i){return labels[i];}
uint64_t df_trace_word(unsigned i,unsigned j){return trace[i][j];}
uint64_t df_run(int op,gf out,gf a,gf b,int amount,uint64_t mask,unsigned char*bytes,const unsigned char*input){traces=0;switch(op){
case 0:crypton_gf_copy(out,a);break;
case 1:crypton_gf_add_RAW(out,a,b);break;
case 2:crypton_gf_sub_RAW(out,a,b);break;
case 3:crypton_gf_bias(out,amount);break;
case 4:crypton_gf_weak_reduce(out);break;
case 5:crypton_gf_strong_reduce(out);break;
case 6:crypton_gf_add(out,a,b);break;
case 7:crypton_gf_sub(out,a,b);break;
case 8:crypton_gf_mul(out,a,b);break;
case 9:crypton_gf_sqr(out,a);break;
case 10:crypton_gf_mulw_unsigned(out,a,(uint32_t)mask);break;
case 11:return crypton_gf_isr(out,a);
case 12:return crypton_gf_eq(a,b);
case 13:return crypton_gf_lobit(a);
case 14:crypton_gf_serialize(bytes,a);break;
case 15:return crypton_gf_deserialize(out,input,(uint8_t)mask);
case 16:crypton_gf_sqrn(out,a,amount);break;
case 17:crypton_gf_sub_nr(out,a,b);break;
case 18:crypton_gf_subx_nr(out,a,b,amount);break;
case 19:crypton_gf_mulw(out,a,amount);break;
case 20:crypton_gf_cond_sel(out,a,b,(mask_t)mask);break;
case 21:crypton_gf_cond_neg(out,(mask_t)mask);break;
case 22:crypton_gf_cond_swap(out,a,(mask_t)mask);break;
case 23:crypton_gf_mul_qnr(out,a);break;
case 24:crypton_gf_div_qnr(out,a);break;
case 25:crypton_gf_add_nr(out,a,b);break;
default:abort();}return 0;}
uint64_t df_constant(int which,unsigned i){return which==0?ZERO->limb[i]:which==1?ONE->limb[i]:MODULUS->limb[i];}

uint64_t df_profile(unsigned i){const uint64_t p[]={NLIMBS,8*sizeof(word_t),8*sizeof(dword_t),GF_HEADROOM,GF_BITS,__alignof__(crypton_gf_s),GF_LIT_LIMB_BITS,8*sizeof(big_register_t)};return p[i];}
