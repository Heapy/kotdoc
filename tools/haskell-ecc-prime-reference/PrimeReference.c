#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <crypton_bignum.h>
_Static_assert(sizeof(limb_t)==8,"64-bit reference limbs required");

static uint64_t audit_ops[10],audit_hash,audit_sizes[16],audit_frees[16],audit_cleared[16];
static void *audit_ptrs[16];
static unsigned audit_calls,audit_free_count,audit_fail;
static const limb_t *audit_root;
static void *observed_calloc(size_t count,size_t size)
{
    unsigned i=audit_calls++;
    void *p;
    audit_sizes[i]=count*size;
    p=audit_calls==audit_fail ? NULL : calloc(count,size);
    audit_ptrs[i]=p;
    return p;
}
static void observed_free(void *p)
{
    unsigned i,j; uint64_t zero=1;
    for(i=0;i<audit_calls;i++) if(audit_ptrs[i]==p) {
        for(j=0;j<audit_sizes[i];j++) if(((uint8_t *)p)[j]!=0) zero=0;
        audit_frees[audit_free_count]=i+1;
        audit_cleared[audit_free_count++]=zero;
        audit_ptrs[i]=NULL;
        break;
    }
    free(p);
}
static void audit_read(const limb_t *table,uint32_t index)
{
    uint64_t address;
    if(audit_root==NULL) audit_root=table;
    address=(table-audit_root)+index;
    audit_ops[9]++;
    audit_hash=(audit_hash*UINT64_C(0x100000001b3))^address;
}
#define calloc observed_calloc
#define free observed_free
#define crypton_powm_sec kotdoc_source_powm
#include "../../vendor/crypton-2.0.1/cbits/crypton_powm.c"
#include "OriginalPrimeEcc.c"
#undef calloc
#undef free
#undef crypton_powm_sec

void kotdoc_prime_reset(uint32_t fail)
{
    memset(audit_ops,0,sizeof(audit_ops));memset(audit_sizes,0,sizeof(audit_sizes));
    memset(audit_frees,0,sizeof(audit_frees));memset(audit_cleared,0,sizeof(audit_cleared));
    memset(audit_ptrs,0,sizeof(audit_ptrs));audit_calls=audit_free_count=0;
    audit_hash=0;audit_root=NULL;audit_fail=fail;
}
void kotdoc_prime_stats(uint64_t *ops,uint64_t *hash,uint64_t *sizes,uint64_t *freed,uint64_t *cleared,uint32_t *counts)
{
    memcpy(ops,audit_ops,sizeof(audit_ops));*hash=audit_hash;
    memcpy(sizes,audit_sizes,sizeof(audit_sizes));memcpy(freed,audit_frees,sizeof(audit_frees));
    memcpy(cleared,audit_cleared,sizeof(audit_cleared));counts[0]=audit_calls;counts[1]=audit_free_count;
}
