#include <stdint.h>
#include <string.h>
#include <crypton_ecc.h>
#include <crypton_f2m.h>
static uint32_t calls[4096],count;
static int override=2147483647;
void kotdoc_wrapper_reset(int status) { count=0;override=status; }
uint32_t kotdoc_wrapper_calls(uint32_t *out) { memcpy(out,calls,count*sizeof(uint32_t));return count; }
static void record(uint32_t n) { if(count<4096) calls[count++]=n; }
uint32_t kotdoc_wrapped_crypton_ecc_table_size(uint32_t plen,uint32_t klen)
{ return crypton_ecc_table_size(plen,klen); }
int kotdoc_wrapped_crypton_ecc_mul(uint8_t *ox,uint8_t *oy,const uint8_t *px,const uint8_t *py,const uint8_t *k,uint32_t kl,const uint8_t *a,const uint8_t *b,const uint8_t *p,uint32_t pl)
{ record(1);return override!=2147483647?override:crypton_ecc_mul(ox,oy,px,py,k,kl,a,b,p,pl); }
int kotdoc_wrapped_crypton_ecc_table_build(uint8_t *t,const uint8_t *px,const uint8_t *py,uint32_t kl,const uint8_t *a,const uint8_t *b,const uint8_t *p,uint32_t pl)
{ record(2);return override!=2147483647?override:crypton_ecc_table_build(t,px,py,kl,a,b,p,pl); }
int kotdoc_wrapped_crypton_ecc_table_mul(uint8_t *ox,uint8_t *oy,const uint8_t *t,const uint8_t *k,uint32_t kl,const uint8_t *a,const uint8_t *b,const uint8_t *p,uint32_t pl)
{ record(3);return override!=2147483647?override:crypton_ecc_table_mul(ox,oy,t,k,kl,a,b,p,pl); }
int kotdoc_wrapped_crypton_f2m_mul(uint8_t *ox,uint8_t *oy,const uint8_t *px,const uint8_t *py,const uint8_t *k,uint32_t kl,const uint8_t *b,uint32_t fl,const uint8_t *fx,uint32_t fxl)
{ record(4);return override!=2147483647?override:crypton_f2m_mul(ox,oy,px,py,k,kl,b,fl,fx,fxl); }
