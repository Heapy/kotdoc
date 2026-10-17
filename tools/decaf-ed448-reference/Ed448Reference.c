/* Dev-only original point oracle; production never loads this library. */
#define __STDC_WANT_LIB_EXT1__ 1
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <sys/types.h>
#include <inttypes.h>
#ifndef NATIVE_VECTOR
#undef __ARM_NEON__
#undef __SSE2__
#undef __AVX2__
#endif
#if FIELD_BITS==32
#undef __aarch64__
#undef __amd64__
#undef __x86_64__
#undef __X86_64__
#endif
#define CRYPTON_DECAF_WORD_BITS SCALAR_WORD_BITS
#include "field.h"
#include "original/p448/f_generic.c"
#if FIELD_BITS==32
#include "original/p448/arch_32/f_impl.c"
#else
#include "original/p448/arch_ref64/f_impl.c"
#endif
#include "original/p448/f_arithmetic.c"
#include "original/utils.c"
#include "original/ed448goldilocks/scalar.c"

#if NORMALIZED
#include "NormalizedPoints.c"
#else
#include "original/ed448goldilocks/decaf.c"
#endif
#include "original/ed448goldilocks/decaf_tables.c"
#include "original/cbits/crypton_sha3.c"
#include "original/ed448goldilocks/eddsa.c"
int ed_run(int op,uint8_t*out,uint8_t*secret,uint8_t*pub,uint8_t*sig,uint8_t*kp,uint8_t*ctx,const uint8_t*message,size_t n,int prehashed,int for_prehash,const uint8_t*context,unsigned context_len) {
 switch(op) {
 case 0:clamp(out);break;
 case 1:hash_init_with_dom((void*)ctx,prehashed,for_prehash,context,context_len);break;
 case 2:crypton_decaf_ed448_prehash_init((void*)ctx);break;
 case 3:crypton_decaf_ed448_convert_private_key_to_x448(out,secret);break;
 case 4:crypton_decaf_ed448_derive_public_key(out,secret);break;
 case 5:crypton_decaf_ed448_sign_internal(out,secret,pub,message,n,prehashed,context,context_len);break;
 case 6:crypton_decaf_ed448_sign(out,secret,pub,message,n,prehashed,context,context_len);break;
 case 7:crypton_decaf_ed448_sign_prehash(out,secret,pub,(void*)ctx,context,context_len);break;
 case 8:crypton_decaf_ed448_derive_keypair((void*)kp,secret);break;
 case 9:crypton_decaf_ed448_keypair_extract_public_key(out,(void*)kp);break;
 case 10:crypton_decaf_ed448_keypair_extract_private_key(out,(void*)kp);break;
 case 11:crypton_decaf_ed448_keypair_destroy((void*)kp);break;
 case 12:crypton_decaf_ed448_keypair_sign(out,(void*)kp,message,n,prehashed,context,context_len);break;
 case 13:crypton_decaf_ed448_keypair_sign_prehash(out,(void*)kp,(void*)ctx,context,context_len);break;
 case 14:return crypton_decaf_ed448_verify(sig,pub,message,n,prehashed,context,context_len);
 case 15:return crypton_decaf_ed448_verify_prehash(sig,pub,(void*)ctx,context,context_len);
 case 16:crypton_decaf_ed448_prehash_update((void*)ctx,message,n);break;
 case 17:crypton_decaf_ed448_prehash_destroy((void*)ctx);break;
 }return 0;
}
void ed_abort_probe(void){uint8_t secret[57]={0},wrong[57]={0},signature[114],message[1]={0};crypton_decaf_ed448_prehash_ctx_t ctx;crypton_decaf_ed448_prehash_init(ctx);crypton_decaf_ed448_sign_prehash(signature,secret,wrong,ctx,message,0);}
