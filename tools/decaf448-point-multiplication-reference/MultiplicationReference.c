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
struct field_state {char label[32];uint64_t words[16];};
static struct field_state fields[20000];static unsigned field_count;
static void dp_snapshot(const char*label,const gf a){assert(field_count<20000);struct field_state*s=&fields[field_count++];memset(s,0,sizeof(*s));strcpy(s->label,label);for(unsigned j=0;j<NLIMBS;j++)s->words[j]=a->limb[j];}
static unsigned logic_count,lengths[2000];static int indexes[2000];static uint64_t logic[2000][64],extras[2000][3];static char labels[2000][40];
static void pm_logic(const char*label,int index,const uint64_t*words,unsigned n,uint64_t a,uint64_t b,uint64_t c){assert(logic_count<2000 && n<=64);unsigned k=logic_count++;strcpy(labels[k],label);indexes[k]=index;lengths[k]=n;for(unsigned j=0;j<n;j++)logic[k][j]=words[j];extras[k][0]=a;extras[k][1]=b;extras[k][2]=c;}
static void pm_fields_snapshot(const char*label,int index,const word_t*a,unsigned n,uint64_t x,uint64_t y,uint64_t z){uint64_t words[64];for(unsigned j=0;j<n;j++)words[j]=a[j];pm_logic(label,index,words,n,x,y,z);}
static void pm_scalar_snapshot(const char*label,const crypton_decaf_448_scalar_t a){uint64_t words[CRYPTON_DECAF_448_SCALAR_LIMBS];for(unsigned j=0;j<CRYPTON_DECAF_448_SCALAR_LIMBS;j++)words[j]=a->limb[j];pm_logic(label,0,words,CRYPTON_DECAF_448_SCALAR_LIMBS,0,0,0);}
static void pm_wnaf_snapshot(int stage,unsigned w,uint64_t current,int position,unsigned pos,int delta){uint64_t words[]={current,(uint64_t)(int64_t)position,pos,(uint64_t)(int64_t)delta};pm_logic(stage==0?"wnaf:before":stage==1?"wnaf:after":"wnaf:bounded-stop",w,words,4,0,0,0);}
static void pm_x448_snapshot(int t,const gf x2,const gf z2,const gf x3,const gf z3,mask_t swap){uint64_t words[64];const crypton_gf_s*a[]={x2,z2,x3,z3};for(unsigned j=0;j<4;j++)for(unsigned k=0;k<NLIMBS;k++)words[j*NLIMBS+k]=a[j]->limb[k];pm_logic("x448:round",t,words,4*NLIMBS,swap,0,0);}
#if VARIANT==0
#include "original/ed448goldilocks/decaf.c"
#elif VARIANT==1
#include "NormalizedPoints.c"
#elif VARIANT==2
#include "ObservedPoints.c"
#else
#include "BoundedWnafPoints.c"
#endif
#include "original/ed448goldilocks/decaf_tables.c"
unsigned pm_field_count(void){return field_count;}
const void*pm_field_blob(void){assert(sizeof(struct field_state)==160);return fields;}
unsigned pm_logic_count(void){return logic_count;}
const char*pm_logic_label(unsigned i){return labels[i];}
int pm_logic_index(unsigned i){return indexes[i];}
unsigned pm_logic_length(unsigned i){return lengths[i];}
uint64_t pm_logic_word(unsigned i,unsigned j){return logic[i][j];}
uint64_t pm_logic_extra(unsigned i,unsigned j){return extras[i][j];}
int64_t pm_run(int op,point_t out,point_t out2,point_t a,point_t b,scalar_t s1,scalar_t s2,void*table,void*gf_in,void*gf_out,void*control,int count,int index,uint64_t allow,uint64_t short_circuit,uint8_t*bytes,const uint8_t*base_bytes,const uint8_t*scalar_bytes){field_count=logic_count=0;switch(op){
case 0:API_NS(point_scalarmul)(out,a,s1);break;
case 1:API_NS(point_double_scalarmul)(out,a,s1,b,s2);break;
case 2:API_NS(point_dual_scalarmul)(out,out2,a,s1,s2);break;
case 3:crypton_gf_batch_invert(gf_out,gf_in,count);break;
case 4:batch_normalize_niels(table,gf_in,gf_out,count);break;
case 5:API_NS(precompute)(table,a);break;
case 6:constant_time_lookup_niels((niels_s*)out,table,count,index);break;
case 7:API_NS(precomputed_scalarmul)(out,table,s1);break;
case 8:return API_NS(direct_scalarmul)(bytes,base_bytes,s1,(crypton_decaf_bool_t)allow,(crypton_decaf_bool_t)short_circuit);
case 9:return crypton_decaf_x448(bytes,base_bytes,scalar_bytes);
case 10:crypton_decaf_x448_generate_key(bytes,scalar_bytes);break;
case 11:crypton_decaf_x448_derive_public_key(bytes,scalar_bytes);break;
case 12:return recode_wnaf(control,s1,count);
case 13:prepare_wnaf_table((pniels_t*)out,a,count);break;
case 14:API_NS(precompute_wnafs)(table,a);break;
case 15:API_NS(base_double_scalarmul_non_secret)(out,s1,a,s2);break;
case 16:API_NS(precomputed_destroy)(table);break;
default:abort();}return 0;}
int pm_assert_probe(int which){gf in[2]={0},out[2];point_t p={0};precomputed_s table;field_count=logic_count=0;if(which==0)crypton_gf_batch_invert(out,(const gf*)in,1);else if(which==1)crypton_gf_batch_invert(out,(const gf*)in,2);else API_NS(precompute)(&table,p);return 0;}
uint64_t pm_table(unsigned group,unsigned i){return ((const word_t*)(group?API_NS(precomputed_wnaf_as_fe):API_NS(precomputed_base_as_fe)))[i];}
void pm_point_fixture(unsigned which,point_t out){if(which==0)API_NS(point_copy)(out,API_NS(point_base));else if(which==1)API_NS(point_copy)(out,API_NS(point_identity));else if(which==2)API_NS(point_double)(out,API_NS(point_base));else if(which==3)API_NS(point_debugging_torque)(out,API_NS(point_base));else if(which==4)API_NS(point_negate)(out,API_NS(point_base));else{uint8_t factor[56]={2};API_NS(point_debugging_pscale)(out,API_NS(point_base),factor);}}
void pm_encoded_fixture(unsigned which,uint8_t*out){point_t a;pm_point_fixture(which,a);API_NS(point_encode)(out,a);}
void pm_encode_point(const point_t a,uint8_t*out){API_NS(point_encode)(out,a);}
uint64_t pm_valid_point(const point_t a){return API_NS(point_valid)(a);}
