#include <stdint.h>
#include <stddef.h>
#if SCALAR_BITS==32
#define ED25519_FORCE_32BIT
#endif
#define ED25519_NO_INLINE_ASM
#include "original/ed25519-donna-portable.h"
#if defined(OBSERVED)
#if SCALAR_BITS==32
#include "Observed32.h"
#else
#include "Observed64.h"
#endif
#else
#if SCALAR_BITS==32
#include "original/modm-donna-32bit.h"
#else
#include "original/modm-donna-64bit.h"
#endif
#endif
static uint64_t snapshots[32][9];static char labels[32][24];static unsigned count;
#ifdef OBSERVED
static void sm_snapshot(const char*label,const bignum256modm a){strcpy(labels[count],label);for(unsigned i=0;i<bignum256modm_limb_size;i++)snapshots[count][i]=a[i];count++;}
#endif
unsigned sm_count(void){return count;}
uint64_t sm_trace_word(unsigned i,unsigned j){return snapshots[i][j];}
const char*sm_label(unsigned i){return labels[i];}
uint64_t sm_constant(unsigned group,unsigned i){return group?modm_mu[i]:modm_m[i];}
uint64_t sm_lt(uint64_t a,uint64_t b){return lt_modm(a,b);}
void sm_reduce(bignum256modm r){count=0;reduce256_modm(r);}
void sm_barrett(bignum256modm r,const bignum256modm a,const bignum256modm b){count=0;barrett_reduce256_modm(r,a,b);}
void sm_add(bignum256modm r,const bignum256modm a,const bignum256modm b){count=0;add256_modm(r,a,b);}
void sm_mul(bignum256modm r,const bignum256modm a,const bignum256modm b){count=0;mul256_modm(r,a,b);}
void sm_expand(bignum256modm r,const uint8_t*a,size_t len){count=0;expand256_modm(r,a,len);}
void sm_expand_raw(bignum256modm r,const uint8_t*a){expand_raw256_modm(r,a);}
void sm_contract(uint8_t*out,const bignum256modm a){contract256_modm(out,a);}
void sm_window4(int8_t*out,const bignum256modm a){contract256_window4_modm(out,a);}
void sm_sliding(int8_t*out,const bignum256modm a,int window){contract256_slidingwindow_modm(out,a,window);}
void sm_sub_batch(bignum256modm r,const bignum256modm a,const bignum256modm b,size_t size){sub256_modm_batch(r,a,b,size);}
int sm_lt_batch(const bignum256modm a,const bignum256modm b,size_t size){return lt256_modm_batch(a,b,size);}
int sm_lte_batch(const bignum256modm a,const bignum256modm b,size_t size){return lte256_modm_batch(a,b,size);}
int sm_zero(const bignum256modm a){return iszero256_modm_batch(a);}
int sm_one(const bignum256modm a){return isone256_modm_batch(a);}
int sm_128(const bignum256modm a){return isatmost128bits256_modm_batch(a);}
