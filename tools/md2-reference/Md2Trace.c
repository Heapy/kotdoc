#include <stdint.h>
#include <string.h>
static int count;static uint8_t traces[4096][48];static int kinds[4096],indices[4096],lengths[4096];
void md2_trace(int kind,int index,const uint8_t *bytes,int length){if(count<4096){kinds[count]=kind;indices[count]=index;lengths[count]=length;memcpy(traces[count],bytes,length);}count++;}
#include "ObservedMd2.c"
void md2_trace_reset(void){count=0;}
int md2_trace_count(void){return count;}
int md2_trace_kind(int i){return kinds[i];}
int md2_trace_index(int i){return indices[i];}
int md2_trace_length(int i){return lengths[i];}
void md2_trace_copy(int i,uint8_t *out){memcpy(out,traces[i],lengths[i]);}
void md2_observed_chunk(struct md2_ctx *ctx,const uint8_t *input){observed_md2_chunk(ctx,input);}
