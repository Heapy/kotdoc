/* Dev oracle: arithmetic below is included verbatim, never production-linked. */
#include <stdint.h>
#if FIELD_BITS == 64
#include "OriginalField64.h"
#define N 5
#else
#include "OriginalField32.h"
#define N 10
#endif
void x25_sum(limb *out,const limb *in) { fsum(out,in); }
void x25_difference(limb *out,const limb *in) {
#if FIELD_BITS == 64
 fdifference_backwards(out,in);
#else
 fdifference(out,in);
#endif
}
void x25_scalar(limb *out,const limb *in,limb scalar) { fscalar_product(out,in,scalar); }
void x25_mul(limb *out,const limb *a,const limb *b) { fmul(out,a,b); }
void x25_expand(limb *out,const uint8_t *bytes) { fexpand(out,bytes); }
void x25_contract(uint8_t *out,limb *in) { fcontract(out,in); }
#if FIELD_BITS == 64
void x25_square(limb *out,const limb *in,limb count) { fsquare_times(out,in,count); }
uint64_t x25_load(const uint8_t *in) { return load_limb(in); }
void x25_store(uint8_t *out,uint64_t in) { store_limb(out,in); }
#else
void x25_square(limb *out,const limb *in) { fsquare(out,in); }
void x25_product(limb *out,const limb *a,const limb *b) { fproduct(out,a,b); }
void x25_reduce_degree(limb *out) { freduce_degree(out); }
void x25_reduce_coefficients(limb *out) { freduce_coefficients(out); }
void x25_square_inner(limb *out,const limb *in) { fsquare_inner(out,in); }
int64_t x25_div26(int64_t in) { return div_by_2_26(in); }
int64_t x25_div25(int64_t in) { return div_by_2_25(in); }
int32_t x25_eq32(int32_t a,int32_t b) { return s32_eq(a,b); }
int32_t x25_gte32(int32_t a,int32_t b) { return s32_gte(a,b); }
#endif
