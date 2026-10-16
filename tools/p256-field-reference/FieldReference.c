/* Development-only guarded field observer; upstream files retain full notices. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#ifdef OBSERVED
#include "../p256-words-reference/ObservedP256.c"
static uint64_t field_ops[13];
static char field_trace[131072];
static size_t trace_used;
static void field_snapshot(const char *name, const uint64_t *values, int size) {
  trace_used+=snprintf(field_trace+trace_used,sizeof(field_trace)-trace_used,"%s[\"%s\",[",trace_used?",":"",name);
  for(int i=0;i<size;i++)trace_used+=snprintf(field_trace+trace_used,sizeof(field_trace)-trace_used,"%s\"%" PRIu64 "\"",i?",":"",values[i]);
  trace_used+=snprintf(field_trace+trace_used,sizeof(field_trace)-trace_used,"]]");
  if(trace_used>=sizeof(field_trace)-1024)abort();
}
#if P256_BITSPERDIGIT == 32
#include "ObservedField32.h"
#else
#include "ObservedField64.h"
#endif
#else
#include "../../vendor/crypton-2.0.1/cbits/p256/p256.c"
#include "p256/p256_gf.h"
#endif
#define GUARD ((limb)0xa5968778695a4b3cULL)
typedef struct { limb before; felem value; limb after; } guarded;
static void load(guarded *a, char *text) {
 a->before=a->after=GUARD;char *p=text;
 for(int i=0;i<NLIMBS;i++){a->value[i]=(limb)strtoull(p,&p,16);if(*p==',')p++;}
}
static void array(const limb *data,int n){printf("[");for(int i=0;i<n;i++)printf("%s\"%" PRIu64 "\"",i?",":"",(uint64_t)data[i]);printf("]");}
int main(void) {
 char line[8192],op[32],as[2048],bs[2048],ws[2048],ls[2048],hs[2048];int alias;uint64_t carry;
 while(fgets(line,sizeof(line),stdin)){
  if(sscanf(line,"%31s %d %" SCNu64 " %2047s %2047s %2047s %2047s %2047s",op,&alias,&carry,as,bs,ws,ls,hs)!=8)return 2;
  guarded a,b,c;load(&a,as);load(&b,bs);c.before=c.after=GUARD;for(int i=0;i<NLIMBS;i++)c.value[i]=(limb)0xa5a5a5a5a5a5a5a5ULL;
  limb *out=alias==1?a.value:alias==2?b.value:c.value;
  struct { crypton_p256_digit before;crypton_p256_int value;crypton_p256_digit after; } word={GUARD,{{0}},GUARD};
  char *p=ws;for(int i=0;i<P256_NDIGITS;i++){word.value.a[i]=strtoull(p,&p,16);if(*p==',')p++;}
#if P256_BITSPERDIGIT == 32
  struct { uint64_t before;uint64_t value[17];uint64_t after; } tmp={0x123456789abcdef0ULL,{0},0x123456789abcdef0ULL};
  p=ls;for(int i=0;i<17;i++){tmp.value[i]=strtoull(p,&p,16);if(*p==',')p++;}
#else
  struct { __uint128_t before;__uint128_t value[9];__uint128_t after; } tmp={0x123456789abcdef0ULL,{0},0x123456789abcdef0ULL};
  char *q=hs;p=ls;for(int i=0;i<9;i++){tmp.value[i]=strtoull(p,&p,16);tmp.value[i]|=((__uint128_t)strtoull(q,&q,16))<<64;if(*p==',')p++;if(*q==',')q++;}
#endif
#ifdef OBSERVED
  memset(field_ops,0,sizeof(field_ops));memset(word_ops,0,sizeof(word_ops));word_corrections=0;trace_used=0;field_trace[0]=0;
#endif
  int ret=0;const limb *constant=NULL;int constant_size=0;
  if(!strcmp(op,"carry"))felem_reduce_carry(out,carry);
  else if(!strcmp(op,"sum"))felem_sum(out,a.value,b.value);
  else if(!strcmp(op,"diff"))felem_diff(out,a.value,b.value);
  else if(!strcmp(op,"degree"))felem_reduce_degree(out,tmp.value);
  else if(!strcmp(op,"square"))felem_square(out,a.value);
  else if(!strcmp(op,"mul"))felem_mul(out,a.value,b.value);
  else if(!strcmp(op,"assign"))felem_assign(out,a.value);
  else if(!strcmp(op,"scalar3"))felem_scalar_3(out);
  else if(!strcmp(op,"scalar4"))felem_scalar_4(out);
  else if(!strcmp(op,"scalar8"))felem_scalar_8(out);
  else if(!strcmp(op,"zero"))ret=felem_is_zero_vartime(a.value);
  else if(!strcmp(op,"to"))to_montgomery(out,&word.value);
  else if(!strcmp(op,"from"))from_montgomery(&word.value,a.value);
  else {
   if(!strcmp(op,"one")){constant=kOne;constant_size=NLIMBS;}
   else if(!strcmp(op,"zeroConst")){constant=kZero;constant_size=NLIMBS;}
   else if(!strcmp(op,"p")){constant=kP;constant_size=NLIMBS;}
   else if(!strcmp(op,"2p")){constant=k2P;constant_size=NLIMBS;}
   else if(!strcmp(op,"precomputed")){constant=kPrecomputed;constant_size=NLIMBS*60;}
   else if(!strcmp(op,"r")){constant=kR.a;constant_size=P256_NDIGITS;}
   else if(!strcmp(op,"rInv")){constant=kRInv.a;constant_size=P256_NDIGITS;}
   else if(!strcmp(op,"zeroOffset")){
#if P256_BITSPERDIGIT == 32
    constant=zero31;
#else
    constant=zero53;
#endif
    constant_size=NLIMBS;
   }else return 3;
  }
  printf("{\"ret\":%d,\"a\":",ret);array(a.value,NLIMBS);printf(",\"b\":");array(b.value,NLIMBS);printf(",\"c\":");array(c.value,NLIMBS);printf(",\"word\":");array(word.value.a,P256_NDIGITS);printf(",\"constant\":");array(constant,constant_size);
  printf(",\"low\":[");for(int i=0;i<2*NLIMBS-1;i++)printf("%s\"%" PRIu64 "\"",i?",":"",(uint64_t)tmp.value[i]);printf("],\"high\":[");
  for(int i=0;i<2*NLIMBS-1;i++) {
#if P256_BITSPERDIGIT == 32
   uint64_t hi=0;
#else
   uint64_t hi=(uint64_t)(tmp.value[i]>>64);
#endif
   printf("%s\"%" PRIu64 "\"",i?",":"",hi);
  }
  printf("]");
  int guards=a.before==GUARD&&a.after==GUARD&&b.before==GUARD&&b.after==GUARD&&c.before==GUARD&&c.after==GUARD&&word.before==GUARD&&word.after==GUARD&&tmp.before==0x123456789abcdef0ULL&&tmp.after==0x123456789abcdef0ULL;
  printf(",\"guards\":%s",guards?"true":"false");
#ifdef OBSERVED
  printf(",\"ops\":[");for(int i=0;i<13;i++)printf("%s\"%" PRIu64 "\"",i?",":"",field_ops[i]);printf("],\"wordOps\":[");for(int i=0;i<28;i++)printf("%s\"%" PRIu64 "\"",i?",":"",word_ops[i]);printf("],\"trace\":[%s]",field_trace);
#endif
  printf("}\n");
 }
 return 0;
}
