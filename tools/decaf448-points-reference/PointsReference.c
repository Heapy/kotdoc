/* Dev-only original point oracle; production never loads this library. */
#define __STDC_WANT_LIB_EXT1__ 1
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <sys/types.h>
#include <inttypes.h>
#ifndef NATIVE_VECTOR
#undef __ARM_NEON__
#undef __SSE2__
#undef __AVX2__
#endif
#if FIELD_BITS==32
#undef __aarch64__
#undef __amd64__
#undef __x86_64__
#undef __X86_64__
#endif
#define CRYPTON_DECAF_WORD_BITS SCALAR_WORD_BITS
#include "field.h"
#include "original/p448/f_generic.c"
#if FIELD_BITS==32
#include "original/p448/arch_32/f_impl.c"
#else
#include "original/p448/arch_ref64/f_impl.c"
#endif
#include "original/p448/f_arithmetic.c"
#include "original/utils.c"
#include "original/ed448goldilocks/scalar.c"
static unsigned traces;
static uint64_t trace[20000][16];static char labels[20000][32];
static void dp_snapshot(const char*label,const gf a){assert(traces<20000);strcpy(labels[traces],label);for(unsigned j=0;j<NLIMBS;j++)trace[traces][j]=a->limb[j];traces++;}
#ifdef OBSERVED
#include "ObservedPoints.c"
#else
#include "original/ed448goldilocks/decaf.c"
#endif
unsigned dp_trace_count(void){return traces;}
const char*dp_trace_label(unsigned i){return labels[i];}
uint64_t dp_trace_word(unsigned i,unsigned j){return trace[i][j];}
#include "original/ed448goldilocks/decaf_tables.c"
uint64_t dp_run(int op,point_t out,point_t a,point_t b,uint64_t m,uint64_t m2,uint64_t m3,int flag,unsigned char*bytes){traces=0;switch(op){
case 0:crypton_gf_invert(out->x,a->x,flag);break;
case 1:API_NS(deisogenize)(out->x,out->y,out->z,a,m,m2,m3);break;
case 2:API_NS(point_encode)(bytes,a);break;
case 3:return (int64_t)API_NS(point_decode)(out,bytes,(crypton_decaf_bool_t)m);
case 4:API_NS(point_sub)(out,a,b);break;
case 5:API_NS(point_add)(out,a,b);break;
case 6:point_double_internal(out,a,flag);break;
case 7:API_NS(point_double)(out,a);break;
case 8:API_NS(point_negate)(out,a);break;
case 9:cond_neg_niels((niels_s*)out,m);break;
case 10:pt_to_pniels((pniels_s*)out,a);break;
case 11:pniels_to_pt(out,(pniels_s*)a);break;
case 12:niels_to_pt(out,(niels_s*)a);break;
case 13:add_niels_to_pt(out,(niels_s*)a,flag);break;
case 14:sub_niels_from_pt(out,(niels_s*)a,flag);break;
case 15:add_pniels_to_pt(out,(pniels_s*)a,flag);break;
case 16:sub_pniels_from_pt(out,(pniels_s*)a,flag);break;
case 17:prepare_fixed_window((pniels_t*)out,a,flag);break;
case 18:return API_NS(point_eq)(a,b);
case 19:return API_NS(point_valid)(a);
case 20:API_NS(point_debugging_torque)(out,a);break;
case 21:API_NS(point_debugging_pscale)(out,a,bytes);break;
case 22:API_NS(point_cond_sel)(out,a,b,(crypton_decaf_bool_t)m);break;
case 23:API_NS(point_mul_by_ratio_and_encode_like_eddsa)(bytes,a);break;
case 24:return (int64_t)API_NS(point_decode_like_eddsa_and_mul_by_ratio)(out,bytes);
case 25:crypton_decaf_ed448_convert_public_key_to_x448(bytes,bytes+64);break;
case 26:API_NS(point_mul_by_ratio_and_encode_like_x448)(bytes,a);break;
case 27:API_NS(point_destroy)(out);break;
case 28:API_NS(point_copy)(out,a);break;
default:abort();}return 0;}
uint64_t dp_table(unsigned index,unsigned limb){const gf*data=index<4?(const gf*)API_NS(point_base):index<244?API_NS(precomputed_base_as_fe):API_NS(precomputed_wnaf_as_fe);return data[index<4?index:index<244?index-4:index-244]->limb[limb];}
void dp_table_bytes(unsigned index,unsigned char*out){gf v;for(unsigned j=0;j<NLIMBS;j++)v->limb[j]=dp_table(index,j);crypton_gf_serialize(out,v);}
uint64_t dp_constant(unsigned which,unsigned i){return which==0?RISTRETTO_FACTOR->limb[i]:which==1?point_scalarmul_adjustment->limb[i]:which==2?precomputed_scalarmul_adjustment->limb[i]:which==3?crypton_decaf_x448_base_point[i]:((word_t*)API_NS(point_identity))[i];}
uint64_t dp_profile(unsigned i){const uint64_t p[]={ARCH_WORD_BITS,WBITS,NLIMBS,sizeof(point_t),__alignof__(point_t),sizeof(niels_s),sizeof(pniels_s),sizeof(precomputed_s),API_NS(alignof_precomputed_s),API_NS(sizeof_precomputed_wnafs)};return p[i];}
void dp_table_raw_bytes(unsigned index,unsigned char*out){gf v;for(unsigned j=0;j<NLIMBS;j++)v->limb[j]=dp_table(index,j);memcpy(out,v,sizeof(gf));}
int dp_assert_probe(int which){point_t out={0},a={0},b={0};unsigned char bytes[128]={0};return dp_run(which?23:0,out,a,b,0,0,0,1,bytes);}
