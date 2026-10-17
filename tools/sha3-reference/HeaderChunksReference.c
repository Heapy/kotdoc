/* Development-only dependency observer: original shake.h unchanged; no input pages are touched. */
#include <sys/mman.h>
#include <stdint.h>
#include <stdlib.h>
#define crypton_sha3_update virtual_update
#define crypton_sha3_output virtual_output
#include "original/cbits/decaf/include/decaf/shake.h"
static uint64_t offsets[32],lengths[32],base;
static unsigned count;
void virtual_update(struct sha3_ctx*ctx,const uint8_t*in,uint32_t len){(void)ctx;if(count>=32)abort();offsets[count]=(uintptr_t)in-base;lengths[count++]=len;}
void virtual_output(struct sha3_ctx*ctx,uint8_t*out,uint32_t len){virtual_update(ctx,out,len);}
int ref_header_chunks(int output,uint64_t length){count=0;size_t size=length?length:1;void*region=mmap(NULL,size,PROT_NONE,MAP_PRIVATE|MAP_ANON,-1,0);if(region==MAP_FAILED)return -1;base=(uintptr_t)region;crypton_decaf_shake256_ctx_t ctx;if(output)crypton_decaf_shake256_output(ctx,region,length);else crypton_decaf_shake256_update(ctx,region,length);munmap(region,size);return count;}
uint64_t ref_header_offset(unsigned i){return offsets[i];}
uint64_t ref_header_length(unsigned i){return lengths[i];}
