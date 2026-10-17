#include "original/cbits/crypton_ripemd.c"
int ripemd_size(void){return sizeof(struct ripemd160_ctx);}
void ripemd_chunk(struct ripemd160_ctx*c,uint32_t*p){ripemd160_do_chunk(c,p);}
void ripemd_counter(uint8_t*out,uint64_t n,uint32_t len){struct ripemd160_ctx c;c.sz=n;c.sz+=len;memcpy(out,&c.sz,8);}
