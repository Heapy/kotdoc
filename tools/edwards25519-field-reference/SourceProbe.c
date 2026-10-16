/* Dev-only compilation/table-shape probe, not an arithmetic parity oracle. */
#include <stdint.h>
#include <stddef.h>
#define ED25519_NO_INLINE_ASM
#if FIELD_BITS==32
#define ED25519_FORCE_32BIT
#endif
#include "original/ed25519-donna-portable.h"
#if FIELD_BITS==32
#include "original/curve25519-donna-32bit.h"
#else
#include "original/curve25519-donna-64bit.h"
#endif
#ifdef OBSERVED
static void ef_snapshot(const char*,const bignum25519);
#include "ObservedHelpers.h"
#else
#include "original/curve25519-donna-helpers.h"
#endif
typedef struct {bignum25519 x,y,z,t;} ge25519;
typedef struct {bignum25519 ysubx,xaddy,t2d;} ge25519_niels;
#if FIELD_BITS==32
#include "original/ed25519-donna-32bit-tables.h"
#define WORD uint32_t
#else
#include "original/ed25519-donna-64bit-tables.h"
#define WORD uint64_t
#endif
#include "original/ed25519-donna-basepoint-table.h"
size_t source_field_limbs(void){return sizeof(bignum25519)/sizeof(WORD);}
size_t source_table_words(void){return (sizeof(ge25519_basepoint)+sizeof(ge25519_ecd)+sizeof(ge25519_ec2d)+sizeof(ge25519_sqrtneg1)+sizeof(ge25519_niels_sliding_multiples))/sizeof(WORD);}
size_t source_packed_bytes(void){return sizeof(ge25519_niels_base_multiples);}
uint64_t source_table_word(unsigned group,unsigned i){
 const WORD*p;const unsigned n=sizeof(bignum25519)/sizeof(WORD);
 switch(group){
 case 0:
  switch(i/n){case 0:p=ge25519_basepoint.x;break;case 1:p=ge25519_basepoint.y;break;case 2:p=ge25519_basepoint.z;break;default:p=ge25519_basepoint.t;break;}i%=n;break;
 case 1:p=ge25519_ecd;break;
 case 2:p=ge25519_ec2d;break;
 case 3:p=ge25519_sqrtneg1;break;
 default:
  switch((i/n)%3){case 0:p=ge25519_niels_sliding_multiples[i/(3*n)].ysubx;break;case 1:p=ge25519_niels_sliding_multiples[i/(3*n)].xaddy;break;default:p=ge25519_niels_sliding_multiples[i/(3*n)].t2d;break;}i%=n;break;
 }
 return p[i];
}
unsigned source_packed_byte(unsigned i){return ge25519_niels_base_multiples[i/96][i%96];}
