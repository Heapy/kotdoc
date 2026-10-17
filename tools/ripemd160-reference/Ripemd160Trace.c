#include <stdint.h>
static uint32_t states[4096][10];static int count;
void ripemd_trace(int i,uint32_t a,uint32_t b,uint32_t c,uint32_t d,uint32_t e,uint32_t f,uint32_t g,uint32_t h,uint32_t j,uint32_t k){if(count<4096){uint32_t v[10]={a,b,c,d,e,f,g,h,j,k};for(int n=0;n<10;n++)states[count][n]=v[n];}count++;}
#include "ObservedRipemd160.c"
void ripemd_observed_chunk(struct ripemd160_ctx*c,uint32_t*p){observed_ripemd160_do_chunk(c,p);}
void ripemd_trace_reset(void){count=0;}
int ripemd_trace_count(void){return count;}
void ripemd_trace_copy(int n,uint32_t*out){memcpy(out,states[n],40);}
