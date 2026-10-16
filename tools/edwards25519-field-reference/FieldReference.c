/* Dev oracle: ordinary headers and separately verified observer copy. */
#include "SourceProbe.c"
static uint64_t trace[64][10];
static char labels[64][32];
static unsigned traces;
#ifdef OBSERVED
static void ef_snapshot(const char*label,const bignum25519 a){strcpy(labels[traces],label);for(unsigned i=0;i<sizeof(bignum25519)/sizeof(WORD);i++)trace[traces][i]=a[i];traces++;}
#endif
unsigned ef_trace_count(void){return traces;}
const char*ef_trace_label(unsigned i){return labels[i];}
uint64_t ef_trace_word(unsigned i,unsigned j){return trace[i][j];}
#define BINARY(name) void ef_##name(bignum25519 out,const bignum25519 a,const bignum25519 b){curve25519_##name(out,a,b);}
BINARY(add) BINARY(add_after_basic) BINARY(add_reduce) BINARY(sub) BINARY(sub_after_basic) BINARY(sub_reduce) BINARY(mul) BINARY(mul_noinline)
void ef_copy(bignum25519 out,const bignum25519 a){curve25519_copy(out,a);}
void ef_neg(bignum25519 out,const bignum25519 a){curve25519_neg(out,a);}
void ef_square(bignum25519 out,const bignum25519 a){curve25519_square(out,a);}
void ef_square_times(bignum25519 out,const bignum25519 a,uint64_t count){curve25519_square_times(out,a,count);}
void ef_expand(bignum25519 out,const unsigned char*a){curve25519_expand(out,a);}
void ef_contract(unsigned char*out,const bignum25519 a){curve25519_contract(out,a);}
void ef_move(unsigned char*out,const unsigned char*a,uint64_t flag){curve25519_move_conditional_bytes(out,a,flag);}
void ef_swap(bignum25519 a,bignum25519 b,uint64_t flag){curve25519_swap_conditional(a,b,flag);}
void ef_pow250(bignum25519 b){traces=0;curve25519_pow_two5mtwo0_two250mtwo0(b);}
void ef_recip(bignum25519 out,const bignum25519 a){traces=0;curve25519_recip(out,a);}
void ef_pow252(bignum25519 out,const bignum25519 a){traces=0;curve25519_pow_two252m3(out,a);}

uint64_t ef_constant(unsigned index){
#if FIELD_BITS==32
 const uint64_t constants[]={reduce_mask_25,reduce_mask_26,twoP0,twoP13579,twoP2468,fourP0,fourP13579,fourP2468};
#else
 const uint64_t constants[]={reduce_mask_40,reduce_mask_51,reduce_mask_56,twoP0,twoP1234,fourP0,fourP1234};
#endif
 return constants[index];
}
