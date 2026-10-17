#include <stdint.h>
#include <string.h>
#include <stdlib.h>
static struct trace_record {char label[8];int32_t index;uint32_t reserved;uint64_t state[25];} records[25000];
static unsigned record_count;
static void sha3_snapshot(const char*label,int index,uint64_t*state){if(record_count>=25000)abort();struct trace_record*r=&records[record_count++];memset(r,0,sizeof(*r));strncpy(r->label,label,7);r->index=index;memcpy(r->state,state,200);}
#ifdef OBSERVED
#include "ObservedSha3.c"
#else
#include "original/cbits/crypton_sha3.c"
#endif
#include "original/cbits/decaf/utils.c"
#include "original/cbits/decaf/include/decaf/shake.h"
void ref_reset(void){record_count=0;}
unsigned ref_count(void){return record_count;}
const void*ref_trace(void){if(sizeof(struct trace_record)!=216)abort();return records;}
void ref_run(int op,void*ctx,uint8_t*data,unsigned len,unsigned arg){switch(op){
case 0:crypton_sha3_init(ctx,arg);break;
case 1:crypton_sha3_update(ctx,data,len);break;
case 2:crypton_sha3_finalize_with_pad_byte(ctx,arg);break;
case 3:crypton_sha3_output(ctx,data,len);break;
case 4:crypton_sha3_finalize(ctx,arg,data);break;
case 5:crypton_sha3_finalize_shake(ctx);break;
case 6:crypton_sha3_finalize_cshake(ctx);break;
case 7:crypton_keccak_init(ctx,arg);break;
case 8:crypton_keccak_update(ctx,data,len);break;
case 9:crypton_keccak_finalize(ctx,arg,data);break;
case 10:crypton_decaf_shake256_init(ctx);break;
case 11:crypton_decaf_shake256_update(ctx,data,len);break;
case 12:crypton_decaf_shake256_output(ctx,data,len);break;
case 13:crypton_decaf_shake256_final(ctx,data,len);break;
case 14:crypton_decaf_shake256_destroy(ctx);break;
default:abort();}}
void ref_hash(uint8_t*out,size_t outlen,uint8_t*in,size_t inlen){ref_reset();crypton_decaf_shake256_hash(out,outlen,in,inlen);}
