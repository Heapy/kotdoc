/* Development oracle: original source, normalized observer and bounded prefix are separate variants. */
#define __STDC_WANT_LIB_EXT1__ 1
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <sys/types.h>
#include <inttypes.h>
#undef __ARM_NEON__
#undef __SSE2__
#undef __AVX2__
#if SCALAR_WORD_BITS==32 && !defined(DS_MIXED_ARCH64)
#undef __aarch64__
#undef __amd64__
#undef __x86_64__
#undef __X86_64__
#endif
#define CRYPTON_DECAF_WORD_BITS SCALAR_WORD_BITS
#include "word.h"
#include "original/utils.c"
static unsigned traces,trace_mode;
static uint64_t trace[20000][18],extra[20000][3];
static unsigned lengths[20000];static int indexes[20000];static char labels[20000][48];
static void ds_snapshot(unsigned mode,const char*label,int index,const crypton_decaf_word_t*a,unsigned length,uint64_t e0,uint64_t e1,uint64_t e2){if(mode!=trace_mode)return;assert(traces<20000 && length<=18);strcpy(labels[traces],label);indexes[traces]=index;lengths[traces]=length;for(unsigned j=0;j<length;j++)trace[traces][j]=a[j];extra[traces][0]=e0;extra[traces][1]=e1;extra[traces][2]=e2;traces++;}
#if VARIANT==1
#include "NormalizedObservedScalar.c"
#elif VARIANT==2
#include "BoundedObservedScalar.c"
#else
#include "original/ed448goldilocks/scalar.c"
#endif
unsigned ds_trace_count(void){return traces;}
unsigned ds_trace_length(unsigned i){return lengths[i];}
int ds_trace_index(unsigned i){return indexes[i];}
const char*ds_trace_label(unsigned i){return labels[i];}
uint64_t ds_trace_word(unsigned i,unsigned j){return trace[i][j];}
uint64_t ds_trace_extra(unsigned i,unsigned j){return extra[i][j];}
uint64_t ds_run(int op,scalar_t out,scalar_t a,scalar_t b,scalar_t p,uint64_t w,unsigned char*bytes,size_t length,int mode){traces=0;trace_mode=mode;switch(op){
case 0:sc_subx(out,a->limb,b,p,(crypton_decaf_word_t)w);break;
case 1:sc_montmul(out,a,b);break;
case 2:API_NS(scalar_mul)(out,a,b);break;
case 3:sc_montsqr(out,a);break;
case 4:return (uint64_t)(int64_t)API_NS(scalar_invert)(out,a);
case 5:API_NS(scalar_sub)(out,a,b);break;
case 6:API_NS(scalar_add)(out,a,b);break;
case 7:API_NS(scalar_set_unsigned)(out,w);break;
case 8:return API_NS(scalar_eq)(a,b);
case 9:scalar_decode_short(out,bytes,length);break;
case 10:return (uint64_t)(int64_t)API_NS(scalar_decode)(out,bytes);
case 11:API_NS(scalar_destroy)(out);break;
case 12:API_NS(scalar_decode_long)(out,bytes,length);break;
case 13:API_NS(scalar_encode)(bytes,a);break;
case 14:API_NS(scalar_cond_sel)(out,a,b,(crypton_decaf_bool_t)w);break;
case 15:API_NS(scalar_halve)(out,a);break;
case 16:API_NS(scalar_copy)(out,a);break;
case 17:crypton_decaf_bzero(bytes,length);break;
default:abort();}return 0;}
uint64_t ds_constant(unsigned which,unsigned i){return which==0?MONTGOMERY_FACTOR:which==1?sc_p->limb[i]:which==2?sc_r2->limb[i]:which==3?API_NS(scalar_one)->limb[i]:API_NS(scalar_zero)->limb[i];}
uint64_t ds_profile(unsigned i){const uint64_t v[]={WBITS,ARCH_WORD_BITS,SCALAR_BITS,SCALAR_LIMBS,SCALAR_SER_BYTES,sizeof(scalar_t),__alignof__(scalar_t),sizeof(big_register_t)};return v[i];}
