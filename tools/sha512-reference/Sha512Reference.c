#include <stdint.h>
#include <string.h>
static uint64_t trace[2000000];static unsigned trace_len;
static void ref_snapshot(unsigned round,unsigned size,const uint64_t *v) {if(trace_len+size+2<2000000){trace[trace_len++]=round;trace[trace_len++]=size;memcpy(trace+trace_len,v,size*8);trace_len+=size;}}
#ifdef OBSERVED
#include "ObservedSha512.c"
#else
#include "original/cbits/crypton_sha512.c"
#endif
void ref_reset(void){trace_len=0;}
unsigned ref_count(void){return trace_len;}
const uint64_t *ref_trace(void){return trace;}
void ref_run(unsigned op,struct sha512_ctx *ctx,uint8_t *data,uint32_t len,uint32_t arg,uint8_t *out){
 switch(op){case 0:crypton_sha512_init(ctx);break;case 1:crypton_sha384_init(ctx);break;case 2:crypton_sha512t_init(ctx,arg);break;case 3:crypton_sha512_update(ctx,data,len);break;case 4:crypton_sha384_update(ctx,data,len);break;case 5:crypton_sha512t_update(ctx,data,len);break;case 6:crypton_sha512_finalize(ctx,out);break;case 7:crypton_sha384_finalize(ctx,out);break;case 8:crypton_sha512t_finalize(ctx,arg,out);break;case 9:crypton_sha512_finalize_prefix(ctx,data,len,arg,out);break;case 10:crypton_sha384_finalize_prefix(ctx,data,len,arg,out);break;case 11:sha512_do_chunk_generic(ctx,(void*)data);break;case 12:sha512_do_chunk(ctx,(void*)data);break;case 13:crypton_sha512_incr_sz(ctx,(void*)out,arg);break;case 14:crypton_sha512_select_digest(ctx,out,arg);break;case 15:*(uint32_t*)out=crypton_sha512_get_index(ctx);break;}}
uint64_t ref_helper(unsigned op,uint64_t a,uint64_t b){switch(op){case 0:return constant_time_msb(a);case 1:return constant_time_lt(a,b);case 2:return constant_time_ge(a,b);case 3:return constant_time_is_zero(a);case 4:return constant_time_eq(a,b);case 5:return constant_time_msb_64(a);case 6:return constant_time_lt_64(a,b);}return 0;}
int ref_backend(void){
#ifdef WITH_ARMV8_SHA512
return crypton_sha512_armv8_available();
#else
return -1;
#endif
}
