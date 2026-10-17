#include <stdint.h>
#include <string.h>
#include "original/crypton_sha512.c"
static uint32_t probe_start,probe_stop,probe_final_pos,probe_final_index,probe_n,probe_padlen,probe_blocks;
static uint64_t events[20000],selections[2000];static unsigned event_count,selection_count;
static void probe_byte(uint32_t pos,uint32_t index,uint8_t b,int input,uint32_t inputmask,uint32_t paddingmask,uint32_t sizemask){uint64_t v[]={pos,index,b,input,inputmask,paddingmask,sizemask};memcpy(events+event_count*7,v,sizeof(v));event_count++;}
static void probe_select(uint32_t pos,uint32_t mask){selections[selection_count*2]=pos;selections[selection_count*2+1]=mask;selection_count++;}
#undef CRYPTON_HASHED
#define CRYPTON_HASHED(m) ref_observed_##m
#define ref_observed_get_index crypton_sha512_get_index
#define ref_observed_incr_sz crypton_sha512_incr_sz
#include "ObservedPrefix.c"
uint64_t ref_bound(uint32_t len){uint64_t bits[2];return len + SHA512_BLOCK_SIZE + sizeof(bits);}
void ref_probe(uint32_t len,uint32_t n,uint64_t low,uint64_t high,uint32_t start,uint32_t stop,uint64_t *result){
 struct sha512_ctx ctx;uint8_t out[64];memset(&ctx,0,sizeof(ctx));ctx.sz[0]=low;ctx.sz[1]=high;probe_start=start;probe_stop=stop;event_count=selection_count=probe_blocks=0;
 ref_observed_finalize_prefix(&ctx,NULL,len,n,out);
 uint64_t values[]={ref_bound(len),probe_n,probe_padlen,probe_final_pos,probe_final_index,probe_blocks,event_count,selection_count,ctx.sz[0],ctx.sz[1]};memcpy(result,values,sizeof(values));
}
uint64_t *ref_events(void){return events;}
uint64_t *ref_selections(void){return selections;}
