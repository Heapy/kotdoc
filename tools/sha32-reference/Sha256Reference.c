#include <stdint.h>
#include <stddef.h>
#include <string.h>
static uint32_t trace[2000000];static unsigned trace_len;
static void ref_snapshot(unsigned round,unsigned size,const uint32_t *v){trace[trace_len++]=round;trace[trace_len++]=size;memcpy(trace+trace_len,v,size*4);trace_len+=size;}
#ifdef OBSERVED
#include "ObservedSha256.c"
#else
#include "original/cbits/crypton_sha256.c"
#endif
void ref_reset(void){trace_len=0;}
unsigned ref_count(void){return trace_len;}
const uint32_t *ref_trace(void){return trace;}
void ref_run(unsigned op, struct sha256_ctx *ctx,uint8_t *data,uint32_t len,uint32_t arg,uint8_t *out){
 switch(op){case 0:crypton_sha256_init(ctx);break;case 1:crypton_sha256_update(ctx,data,len);break;case 2:crypton_sha256_finalize(ctx,out);break;case 3:crypton_sha256_finalize_prefix(ctx,data,len,arg,out);break;case 4:sha256_do_chunk_generic(ctx,(void*)data);break;case 5:sha256_do_chunk(ctx,(void*)data);break;case 6:crypton_sha256_incr_sz(ctx,(void*)out,arg);break;case 7:crypton_sha256_select_digest(ctx,out,arg);break;case 8:*(uint32_t*)out=crypton_sha256_get_index(ctx);break;
case 9:crypton_sha224_init((void*)ctx);break;case 10:crypton_sha224_update((void*)ctx,data,len);break;case 11:crypton_sha224_finalize((void*)ctx,out);break;case 12:crypton_sha224_finalize_prefix((void*)ctx,data,len,arg,out);break;}}
uint64_t ref_helper(unsigned op,uint64_t a,uint64_t b){switch(op){case 0:return constant_time_msb(a);case 1:return constant_time_lt(a,b);case 2:return constant_time_ge(a,b);case 3:return constant_time_is_zero(a);case 4:return constant_time_eq(a,b);case 5:return constant_time_msb_64(a);case 6:return constant_time_lt_64(a,b);}return 0;}

unsigned ref_size(void){return sizeof(struct sha256_ctx);}
unsigned ref_state_offset(void){return offsetof(struct sha256_ctx,h);}
