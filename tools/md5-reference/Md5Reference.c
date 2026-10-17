#include <stdint.h>
#include <stddef.h>
#include <string.h>
static uint32_t trace[2000000];static unsigned trace_len;
static void ref_snapshot(unsigned round,unsigned size,const uint32_t *v){trace[trace_len++]=round;trace[trace_len++]=size;memcpy(trace+trace_len,v,size*4);trace_len+=size;}
#ifdef OBSERVED
#include "ObservedMd5.c"
#else
#include "original/cbits/crypton_md5.c"
#endif
void ref_reset(void){trace_len=0;}
unsigned ref_count(void){return trace_len;}
const uint32_t *ref_trace(void){return trace;}
void ref_run(unsigned op, struct md5_ctx *ctx,uint8_t *data,uint32_t len,uint32_t arg,uint8_t *out){
 switch(op){case 0:crypton_md5_init(ctx);break;case 1:crypton_md5_update(ctx,data,len);break;case 2:crypton_md5_finalize(ctx,out);break;case 3:crypton_md5_finalize_prefix(ctx,data,len,arg,out);break;case 4:md5_do_chunk(ctx,(void*)data);break;case 5:md5_do_chunk(ctx,(void*)data);break;case 6:crypton_md5_incr_sz(ctx,(void*)out,arg);break;case 7:crypton_md5_select_digest(ctx,out,arg);break;case 8:*(uint32_t*)out=crypton_md5_get_index(ctx);break;
}}
uint64_t ref_helper(unsigned op,uint64_t a,uint64_t b){switch(op){case 0:return constant_time_msb(a);case 1:return constant_time_lt(a,b);case 2:return constant_time_ge(a,b);case 3:return constant_time_is_zero(a);case 4:return constant_time_eq(a,b);case 5:return constant_time_msb_64(a);case 6:return constant_time_lt_64(a,b);}return 0;}

unsigned ref_size(void){return sizeof(struct md5_ctx);}
unsigned ref_state_offset(void){return offsetof(struct md5_ctx,h);}
