/* Dev-only wrappers; original arithmetic is never production-linked. */
#include <stdint.h>
#include <string.h>
#if FIELD_BITS==32
#define N 10
#else
#define N 5
#endif
static uint64_t rounds[256][4][N];
static uint64_t inverses[300][N];
static char labels[300][24];
static unsigned nr,ni;
#ifdef OBSERVED
#if FIELD_BITS==32
#include "Observed32.h"
#else
#include "Observed64.h"
#endif
static void x25_round(unsigned step,const limb*a,const limb*b,const limb*c,const limb*d) {
 const limb *p[4]={a,b,c,d};nr=step+1;
 for(unsigned k=0;k<4;k++)for(unsigned j=0;j<N;j++)rounds[step][k][j]=(uint64_t)p[k][j];
}
static void x25_inverse(const char*label,const limb*a) {
 strcpy(labels[ni],label);for(unsigned j=0;j<N;j++)inverses[ni][j]=(uint64_t)a[j];ni++;
}
#else
#if FIELD_BITS==32
#include "Original32.h"
#else
#include "Original64.h"
#endif
#endif
void x25_swap(limb*a,limb*b,limb bit){swap_conditional(a,b,bit);}
void x25_monty(limb*x2,limb*z2,limb*x3,limb*z3,limb*x,limb*z,limb*xp,limb*zp,const limb*q){fmonty(x2,z2,x3,z3,x,z,xp,zp,q);}
void x25_cmult(limb*x,limb*z,const uint8_t*s,const limb*q){nr=0;cmult(x,z,s,q);}
void x25_recip(limb*out,const limb*z){ni=0;crecip(out,z);}
void x25_donna(uint8_t*out,const uint8_t*s,const uint8_t*q){nr=ni=0;crypton_curve25519_donna(out,s,q);}
void x25_expand(limb*out,const uint8_t*q){fexpand(out,q);}
unsigned x25_round_count(void){return nr;}
unsigned x25_inverse_count(void){return ni;}
const uint64_t*x25_round_data(void){return &rounds[0][0][0];}
const uint64_t*x25_inverse_data(void){return &inverses[0][0];}
const char*x25_inverse_label(unsigned i){return labels[i];}
