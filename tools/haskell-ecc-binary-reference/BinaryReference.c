#include <stdint.h>
#include <stdlib.h>
#include <string.h>
_Static_assert(sizeof(uint64_t)==8,"64-bit reference limbs required");
static uint64_t audit_ops[10],audit_hash,audit_reads,audit_sizes[16],audit_frees[16],audit_cleared[16];
static uint64_t audit_ladder_ops[10];
static void *audit_ptrs[16];
static unsigned audit_calls,audit_free_count,audit_fail;
static const uint64_t *audit_root;
static void *observed_calloc(size_t count,size_t size)
{
    unsigned i=audit_calls++;void *p;
    audit_sizes[i]=count*size;
    p=audit_calls==audit_fail ? NULL : calloc(count,size);audit_ptrs[i]=p;return p;
}
static void observed_free(void *p)
{
    unsigned i;size_t j;uint64_t zero=1;
    for(i=0;i<audit_calls;i++) if(audit_ptrs[i]==p) {
        for(j=0;j<audit_sizes[i];j++) if(((uint8_t *)p)[j]!=0) zero=0;
        audit_frees[audit_free_count]=i+1;audit_cleared[audit_free_count++]=zero;
        audit_ptrs[i]=NULL;break;
    }
    free(p);
}
static void audit_swap(const uint64_t *a,const uint64_t *b,uint32_t i)
{
    audit_reads+=2;
    audit_hash=(audit_hash*UINT64_C(0x100000001b3))^((a-audit_root)+i);
    audit_hash=(audit_hash*UINT64_C(0x100000001b3))^((b-audit_root)+i);
}
#define calloc observed_calloc
#define free observed_free
#include "OriginalBinaryEcc.c"
#undef calloc
#undef free

void kotdoc_binary_reset(uint32_t fail)
{
    memset(audit_ops,0,sizeof(audit_ops));memset(audit_sizes,0,sizeof(audit_sizes));
    memset(audit_ladder_ops,0,sizeof(audit_ladder_ops));
    memset(audit_frees,0,sizeof(audit_frees));memset(audit_cleared,0,sizeof(audit_cleared));
    memset(audit_ptrs,0,sizeof(audit_ptrs));audit_calls=audit_free_count=0;
    audit_hash=audit_reads=0;audit_root=NULL;audit_fail=fail;
}
void kotdoc_binary_stats(uint64_t *ops,uint64_t *ladder,uint64_t *hash,uint64_t *reads,uint64_t *sizes,uint64_t *freed,uint64_t *cleared,uint32_t *counts)
{
    memcpy(ladder,audit_ladder_ops,sizeof(audit_ladder_ops));
    memcpy(ops,audit_ops,sizeof(audit_ops));*hash=audit_hash;*reads=audit_reads;
    memcpy(sizes,audit_sizes,sizeof(audit_sizes));memcpy(freed,audit_frees,sizeof(audit_frees));
    memcpy(cleared,audit_cleared,sizeof(audit_cleared));counts[0]=audit_calls;counts[1]=audit_free_count;
}
int kotdoc_binary_helper(uint32_t op,uint64_t *space,uint32_t n,uint32_t m,const uint32_t *terms,uint32_t nterms,uint64_t arg,uint32_t alias)
{
    uint32_t width=2*n+4;uint64_t *a=space+2,*b=a+width,*t=b+width,*out=t+width;
    bfield f;f.n=n;f.m=m;f.nterms=nterms;f.t=t;
    if(alias==1) out=a;else if(alias==2) out=b;
    memcpy(f.terms,terms,nterms*sizeof(uint32_t));audit_root=space;
    switch(op) {
        case 0:clmul32((uint32_t)a[0],(uint32_t)b[0],out);break;
        case 1:clmul(a[0],b[0],out,out+1);break;
        case 2:out[0]=spread(arg);break;
        case 3:poly_mul_generic(out,a,b,n);break;
        case 4:poly_mul(out,a,b,n);break;
        case 5:poly_sqr(out,a,n);break;
        case 6:poly_reduce(out,t,n,m,terms,nterms);break;
        case 7:fe_mul(&f,out,a,b);break;
        case 8:fe_sqr(&f,out,a);break;
        case 9:fe_add(&f,out,a,b);break;
        case 10:return fe_is_zero(&f,a);
        case 11:fe_inv(&f,out,a,b);break;
        case 12:cswap(a,b,arg,n);break;
    }
    return 0;
}
