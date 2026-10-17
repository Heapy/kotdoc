#include <stdint.h>
#include <string.h>
#include <stddef.h>
static uint32_t trace[2000000];static unsigned trace_count;
static void ref_snapshot(const char *label,int round,const uint32_t state[16]){unsigned id=!strcmp(label,"coreInput")?0:!strcmp(label,"coreRound")?1:!strcmp(label,"coreOutput")?2:!strcmp(label,"hcoreInput")?3:4;if(trace_count+18<2000000){trace[trace_count++]=id;trace[trace_count++]=round;memcpy(trace+trace_count,state,64);trace_count+=16;}}
#ifdef OBSERVED
#include "ObservedChaCha.c"
#else
#include "original/cbits/crypton_chacha.c"
#endif
void ref_reset(void){trace_count=0;}
unsigned ref_count(void){return trace_count;}
const uint32_t *ref_trace(void){return trace;}
unsigned ref_size(void){return sizeof(crypton_chacha_context);}
void ref_run(unsigned op,crypton_chacha_context *ctx,uint8_t *out,const uint8_t *input,uint32_t length,const uint8_t *key,uint32_t keylen,const uint8_t *iv,uint32_t ivlen,uint32_t rounds,uint64_t value){
 crypton_chacha_state *st=crypton_chacha_get_state(ctx);
 switch(op){case 0:chacha_init_key_state(st,keylen,key);break;case 1:crypton_chacha_init_core(st,keylen,key,ivlen,iv);break;case 2:crypton_hchacha_init_core(st,key,iv);break;case 3:crypton_chacha_init(ctx,rounds,keylen,key,ivlen,iv);break;case 4:crypton_hchacha(rounds,key,iv,out);break;case 5:crypton_xchacha_init(ctx,rounds,key,iv);break;case 6:chacha_core((int)rounds,(block*)out,st);break;case 7:hchacha_core((int)rounds,(uint32_t*)out,st);break;case 8:crypton_chacha_combine(out,ctx,input,length);break;case 9:crypton_chacha_generate(out,ctx,length);break;case 10:{uint64_t v=crypton_chacha_counter64(st);memcpy(out,&v,8);break;}case 11:{uint32_t v=crypton_chacha_counter32(st);memcpy(out,&v,4);break;}case 12:crypton_chacha_set_counter64(st,value);break;case 13:crypton_chacha_set_counter32(st,value);break;case 14:crypton_chacha_generate_simple_block(out,st,rounds);break;case 15:crypton_chacha_random(rounds,out,st,length);break;}
}
