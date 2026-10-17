#include <stdint.h>
#include <string.h>
#include "original/cbits/crypton_md2.c"
void md2_chunk(struct md2_ctx *ctx,const uint8_t *input){md2_do_chunk(ctx,input);}
uint32_t md2_size(void){return sizeof(struct md2_ctx);}
void md2_counter(uint8_t *out,uint64_t n,uint32_t len){struct md2_ctx ctx;ctx.sz=n;ctx.sz+=len;memcpy(out,&ctx.sz,8);}
