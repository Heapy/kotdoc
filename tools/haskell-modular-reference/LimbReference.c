#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <crypton_bignum.h>
_Static_assert(sizeof(limb_t) == 8, "64-bit reference limbs required");

void kotdoc_limb_op(uint32_t op, uint32_t n, const uint64_t *a,
                   const uint64_t *b, const uint64_t *m, uint64_t scalar,
                   uint64_t *r, uint64_t *t, uint64_t *ret)
{
    *ret = 0;
    switch (op) {
    case 0: *ret = sub_n(r,a,b,n); break;
    case 1: *ret = add_n(r,a,b,n); break;
    case 2: memcpy(r,a,n*8); *ret = shl1(r,n); break;
    case 3: select_n(r,a,b,scalar,n); break;
    case 4: *ret = eq_mask(scalar,a[0]); break;
    case 5: *ret = mont_n0(scalar); break;
    case 6: memcpy(r,b,n*8); *ret = addmul_1(r,a,n,scalar); break;
    case 7: memcpy(t,a,n*16); mont_reduce(r,t,m,mont_n0(m[0]),n); break;
    case 8: mont_mul(r,a,b,m,mont_n0(m[0]),n,t); break;
    case 9: mont_sqr(r,a,m,mont_n0(m[0]),n,t); break;
    case 10: mont_r2(r,m,n,t); break;
    }
}

uint32_t kotdoc_from_be(uint64_t *r, uint32_t n, const uint8_t *src, uint32_t len)
{ return from_be(r,n,src,len); }
void kotdoc_to_be(uint8_t *dst, uint32_t len, const uint64_t *a, uint32_t n)
{ to_be(dst,len,a,n); }

static uint64_t audit_muls, audit_squares, audit_windows, audit_reads, audit_hash, audit_wiped;
static int allocation_failure;
static void *audit_calloc(size_t count,size_t size)
{ return allocation_failure ? NULL : calloc(count,size); }
static void audit_mul(limb_t *r,const limb_t *a,const limb_t *b,const limb_t *m,limb_t n0,uint32_t n,limb_t *t)
{ audit_muls++; mont_mul(r,a,b,m,n0,n,t); }
static void audit_sqr(limb_t *r,const limb_t *a,const limb_t *m,limb_t n0,uint32_t n,limb_t *t)
{ audit_squares++; mont_sqr(r,a,m,n0,n,t); }
static void audit_read(uint32_t address)
{ audit_reads++; audit_hash = (audit_hash * UINT64_C(0x100000001b3)) ^ address; }
static void audit_clear(const limb_t *space, uint32_t words)
{ uint32_t i; audit_wiped=1; for(i=0;i<words;i++) if(space[i]!=0) audit_wiped=0; }

#define calloc audit_calloc
#include "OriginalPowm.c"
#undef calloc

int kotdoc_powm_audit(uint8_t *out,const uint8_t *base,uint32_t baselen,
                     const uint8_t *exp,uint32_t explen,const uint8_t *mod,uint32_t modlen,
                     uint64_t *stats, uint32_t fail_allocation)
{
    int rc;
    audit_muls=audit_squares=audit_windows=audit_reads=audit_hash=audit_wiped=0;
    allocation_failure=fail_allocation;
    rc=kotdoc_original_powm(out,base,baselen,exp,explen,mod,modlen);
    stats[0]=audit_muls; stats[1]=audit_squares; stats[2]=audit_windows;
    stats[3]=audit_reads; stats[4]=audit_hash; stats[5]=audit_wiped;
    return rc;
}
