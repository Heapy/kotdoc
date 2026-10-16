/* Development-only point observer; original files retain complete notices. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <inttypes.h>
#ifdef OBSERVED
#include "../p256-words-reference/ObservedP256.c"
static uint64_t field_ops[13],point_ops[16],point_hash,point_events;
static int trace_limit,trace_saved;
static size_t trace_used;
static char trace[262144];
static void hash_byte(unsigned value){point_hash=(point_hash^(value&255))*UINT64_C(0x100000001b3);}
static void field_snapshot(const char *name,const uint64_t *values,int n){
 for(const char*p=name;*p;p++)hash_byte((unsigned char)*p);hash_byte(0);
 for(int i=0;i<4;i++)hash_byte((unsigned)n>>(i*8));
 for(int i=0;i<n;i++)for(int j=0;j<8;j++)hash_byte(values[i]>>(j*8));
 point_events++;
 if(trace_saved<trace_limit){
  trace_used+=snprintf(trace+trace_used,sizeof(trace)-trace_used,"%s[\"%s\",[",trace_saved?",":"",name);
  for(int i=0;i<n;i++)trace_used+=snprintf(trace+trace_used,sizeof(trace)-trace_used,"%s\"%" PRIu64 "\"",i?",":"",values[i]);
  trace_used+=snprintf(trace+trace_used,sizeof(trace)-trace_used,"]]");trace_saved++;
  if(trace_used>sizeof(trace)-8192)abort();
 }
}
#if P256_BITSPERDIGIT == 32
#include "../p256-field-reference/ObservedField32.h"
#else
#include "../p256-field-reference/ObservedField64.h"
#endif
static void point_event(const char *name,const limb *v,int n){uint64_t values[16*3*NLIMBS];for(int i=0;i<n;i++)values[i]=v[i];field_snapshot(name,values,n);}
static void point_snapshot(const char *name,const limb*x,const limb*y,const limb*z,int count){uint64_t v[3*NLIMBS];const limb*ps[3]={x,y,z};for(int i=0;i<count*NLIMBS;i++)v[i]=ps[i/NLIMBS][i%NLIMBS];field_snapshot(name,v,count*NLIMBS);}
static void point_branch(const char*name,int value){uint64_t v=value;field_snapshot(name,&v,1);}
static void point_table(const char*name,int offset,limb value){uint64_t v[2]={(uint64_t)offset,value};field_snapshot(name,v,2);}
#include "ObservedPoints.c"
#else
#include "../../vendor/crypton-2.0.1/cbits/p256/p256.c"
#include "../../vendor/crypton-2.0.1/cbits/p256/p256_ec.c"
#endif
#define GUARD ((limb)UINT64_C(0xa5968778695a4b3c))
typedef struct{limb before;felem value;limb after;} guarded;
typedef struct{crypton_p256_digit before;crypton_p256_int value;crypton_p256_digit after;} word_guarded;
static void load(guarded*g,char*s){g->before=g->after=GUARD;for(int i=0;i<NLIMBS;i++){g->value[i]=strtoull(s,&s,16);if(*s==',')s++;}}
static void word_load(word_guarded*g,char*s){g->before=g->after=GUARD;for(int i=0;i<P256_NDIGITS;i++){g->value.a[i]=strtoull(s,&s,16);if(*s==',')s++;}}
static void array(const limb*v,int n){printf("[");for(int i=0;i<n;i++)printf("%s\"%" PRIu64 "\"",i?",":"",(uint64_t)v[i]);printf("]");}
int main(void){
 char line[32768],op[32],fields[6][512],ws[6][512];int alias,index,limit;uint64_t mask;
 while(fgets(line,sizeof(line),stdin)){
  int read=sscanf(line,"%31s %d %d %d %" SCNu64 " %511s %511s %511s %511s %511s %511s %511s %511s %511s %511s %511s %511s",op,&alias,&index,&limit,&mask,fields[0],fields[1],fields[2],fields[3],fields[4],fields[5],ws[0],ws[1],ws[2],ws[3],ws[4],ws[5]);if(read!=17)return 2;
  guarded f[9];word_guarded w[8];char poison[512];poison[0]=0;for(int i=0;i<NLIMBS;i++)strcat(poison,i?",a5a5a5a5":"a5a5a5a5");
  for(int i=0;i<6;i++)load(&f[i],fields[i]);for(int i=6;i<9;i++)load(&f[i],poison);
  for(int i=0;i<6;i++)word_load(&w[i],ws[i]);
  for(int i=6;i<8;i++){w[i].before=w[i].after=GUARD;for(int j=0;j<P256_NDIGITS;j++)w[i].value.a[j]=(limb)UINT64_C(0xa5a5a5a5a5a5a5a5);}
  limb *ox=alias==4?f[1].value:alias==1?f[0].value:alias==2?f[3].value:f[6].value;
  limb *oy=alias==3?f[6].value:alias==4?f[0].value:alias==1?f[1].value:alias==2?f[4].value:f[7].value;
  limb *oz=alias==3?f[6].value:alias==1?f[2].value:alias==2?f[5].value:f[8].value;
  crypton_p256_int *wx=alias==4?&w[3].value:alias==1?&w[2].value:alias==2?&w[4].value:&w[6].value;
  crypton_p256_int *wy=alias==3?&w[6].value:alias==4?&w[2].value:alias==1?&w[3].value:alias==2?&w[5].value:&w[7].value;
#ifdef OBSERVED
  memset(point_ops,0,sizeof(point_ops));memset(field_ops,0,sizeof(field_ops));memset(word_ops,0,sizeof(word_ops));point_hash=UINT64_C(0xcbf29ce484222325);point_events=0;trace_limit=limit;trace_saved=0;trace_used=0;trace[0]=0;
#endif
  if(!strcmp(op,"inv"))felem_inv(ox,f[0].value);
  else if(!strcmp(op,"double"))point_double(ox,oy,oz,f[0].value,f[1].value,f[2].value);
  else if(!strcmp(op,"mixed"))point_add_mixed(ox,oy,oz,f[0].value,f[1].value,f[2].value,f[3].value,f[4].value);
  else if(!strcmp(op,"add"))point_add(ox,oy,oz,f[0].value,f[1].value,f[2].value,f[3].value,f[4].value,f[5].value);
  else if(!strcmp(op,"adddouble"))point_add_or_double_vartime(ox,oy,oz,f[0].value,f[1].value,f[2].value,f[3].value,f[4].value,f[5].value);
  else if(!strcmp(op,"copy"))copy_conditional(ox,f[0].value,mask);
  else if(!strcmp(op,"selectaffine"))select_affine_point(ox,oy,kPrecomputed+(index/16)*(30*NLIMBS),index%16);
  else if(!strcmp(op,"selectjacobian")){
   limb table[16*3*NLIMBS];for(int i=0;i<16*3*NLIMBS;i++)table[i]=(limb)(UINT64_C(0x9e3779b97f4a7c15)*(i+1));memset(table,0,3*NLIMBS*sizeof(limb));select_jacobian_point(ox,oy,oz,table,index);
  }
  else if(!strcmp(op,"baseraw"))scalar_base_mult(ox,oy,oz,&w[0].value);
  else if(!strcmp(op,"affine"))point_to_affine(ox,oy,f[0].value,f[1].value,f[2].value);
  else if(!strcmp(op,"scalarraw"))scalar_mult(ox,oy,oz,f[0].value,f[1].value,&w[0].value);
  else if(!strcmp(op,"base"))crypton_p256_base_point_mul(&w[0].value,wx,wy);
  else if(!strcmp(op,"points"))crypton_p256_points_mul_vartime(&w[0].value,&w[1].value,&w[2].value,&w[3].value,wx,wy);
  else if(!strcmp(op,"pointadd"))crypton_p256e_point_add(&w[2].value,&w[3].value,&w[4].value,&w[5].value,wx,wy);
  else if(!strcmp(op,"negate"))crypton_p256e_point_negate(&w[2].value,&w[3].value,wx,wy);
  else if(!strcmp(op,"pointmul"))crypton_p256e_point_mul(&w[0].value,&w[2].value,&w[3].value,wx,wy);
  else return 3;
  int guards=1;printf("{\"fields\":[");for(int i=0;i<9;i++){if(i)printf(",");array(f[i].value,NLIMBS);guards&=f[i].before==GUARD&&f[i].after==GUARD;}printf("],\"words\":[");
  for(int i=0;i<8;i++){if(i)printf(",");array(w[i].value.a,P256_NDIGITS);guards&=w[i].before==GUARD&&w[i].after==GUARD;}printf("],\"guards\":%s",guards?"true":"false");
#ifdef OBSERVED
  printf(",\"ops\":[");for(int i=0;i<16;i++)printf("%s\"%" PRIu64 "\"",i?",":"",point_ops[i]);printf("],\"fieldOps\":[");for(int i=0;i<13;i++)printf("%s\"%" PRIu64 "\"",i?",":"",field_ops[i]);printf("],\"wordOps\":[");for(int i=0;i<28;i++)printf("%s\"%" PRIu64 "\"",i?",":"",word_ops[i]);printf("],\"hash\":\"%" PRIu64 "\",\"events\":\"%" PRIu64 "\",\"trace\":[%s]",point_hash,point_events,trace);
#endif
  printf("}\n");
 }
 return 0;
}
