/* Dev oracle only. Original arithmetic headers are byte-exact pinned copies. */
#include <stdio.h>
#include <stdlib.h>
#define ED25519_NO_INLINE_ASM
#if POINT_BITS==32
#define ED25519_FORCE_32BIT
#endif
#include "original/ed25519-donna-portable.h"
#if POINT_BITS==64
#define ED25519_64BIT
#include "original/curve25519-donna-64bit.h"
#include "original/modm-donna-64bit.h"
#else
#define ED25519_32BIT
#include "original/curve25519-donna-32bit.h"
#include "original/modm-donna-32bit.h"
#endif
#include "original/curve25519-donna-helpers.h"
typedef struct {bignum25519 x,y,z,t;} ge25519;
typedef ge25519 ge25519_p1p1;
typedef struct {bignum25519 ysubx,xaddy,t2d;} ge25519_niels;
typedef struct {bignum25519 ysubx,xaddy,z,t2d;} ge25519_pniels;
#include "original/ed25519-donna-basepoint-table.h"
#if POINT_BITS==64
#include "original/ed25519-donna-64bit-tables.h"
#else
#include "original/ed25519-donna-32bit-tables.h"
#endif
static int ed25519_verify(const unsigned char*x,const unsigned char*y,size_t n){size_t d=0;while(n--)d|=*x++ ^ *y++;return 1&((d-1)>>8);}
static unsigned count;
static uint64_t trace[512][40];
static char labels[512][48];
static void ep_snapshot(const char*label,int index,const ge25519*p){if(count>=512)abort();if(index<0)snprintf(labels[count],48,"%s",label);else snprintf(labels[count],48,"%s:%d",label,index);
#if POINT_BITS==64
const uint64_t*w=(const void*)p;
#else
const uint32_t*w=(const void*)p;
#endif
for(unsigned j=0;j<sizeof(*p)/sizeof(*w);j++)trace[count][j]=w[j];count++;}
#ifdef OBSERVED
#include "ObservedBase.h"
#else
#include "original/ed25519-donna-impl-base.h"
#endif
#define ED25519_FN(x) x
#ifdef OBSERVED
#include "ObservedExts.h"
#else
#include "original/ed25519-crypton-exts.h"
#endif
unsigned ep_trace_count(void){return count;}
const char*ep_trace_label(unsigned i){return labels[i];}
uint64_t ep_trace_word(unsigned i,unsigned j){return trace[i][j];}
static unsigned table_mode;
void ep_set_table(unsigned mode){table_mode=mode;}
static const uint8_t (*ep_table(void))[96]{static uint8_t table[256][96];if(!table_mode)return ge25519_niels_base_multiples;for(unsigned i=0;i<256;i++)for(unsigned j=0;j<96;j++)table[i][j]=table_mode==1?0:table_mode==2?255:(i*17+j*29);return table;}
/* Buffers supplied by caller retain all guards and untouched coordinates. */
int ep_run(int op,ge25519*r,ge25519*a,ge25519*b,bignum256modm s1,bignum256modm s2,uint32_t extra,unsigned char*out,const unsigned char*in){count=0;switch(op){
case 0:ge25519_p1p1_to_partial(r,a);break;
case 1:ge25519_p1p1_to_full(r,a);break;
case 2:ge25519_full_to_pniels((void*)r,a);break;
case 3:ge25519_add_p1p1(r,a,b);break;
case 4:ge25519_double_p1p1(r,a);break;
case 5:ge25519_nielsadd2_p1p1(r,a,(void*)b,extra);break;
case 6:ge25519_pnielsadd_p1p1(r,a,(void*)b,extra);break;
case 7:ge25519_double_partial(r,a);break;
case 8:ge25519_double(r,a);break;
case 9:ge25519_add(r,a,b);break;
case 10:ge25519_nielsadd2(r,(void*)b);break;
case 11:ge25519_pnielsadd((void*)r,a,(void*)b);break;
case 12:ed25519_point_encode(out,a);break;
case 13:return ge25519_unpack_negative_vartime(r,in);
case 14:ge25519_double_scalarmult_vartime(r,a,s1,s2);break;
case 15:return ge25519_windowb_equal(extra,(uint32_t)s1[0]);
case 16:ge25519_scalarmult_base_choose_niels((void*)r,ep_table(),extra,(signed char)in[0]);break;
case 17:ge25519_scalarmult_base_niels(r,ep_table(),s1);break;
case 18:return ed25519_point_decode_vartime(r,in);
case 19:return ed25519_point_eq(a,b);
case 20:return ed25519_point_is_identity(a);
case 21:ed25519_point_negate(r,a);break;
case 22:ed25519_point_add(r,a,b);break;
case 23:ed25519_point_double(r,a);break;
case 24:ed25519_point_mul_by_cofactor(r,a);break;
case 25:ed25519_point_base_scalarmul(r,s1);break;
case 26:ed25519_move_cond_pniels((void*)r,(void*)a,extra);break;
case 27:{ge25519_pniels table[15];for(int i=0;i<15;i++)memcpy(&table[i],i%2?(void*)a:(void*)b,sizeof(table[i]));ed25519_point_scalarmul_w_choose_pniels((void*)r,table,extra);break;}
case 28:ed25519_point_scalarmul(r,a,s1);break;
case 29:ed25519_base_double_scalarmul_vartime(r,s1,a,s2);break;
case 30:return ed25519_point_has_prime_order(a);
default:abort();}return -1;}
