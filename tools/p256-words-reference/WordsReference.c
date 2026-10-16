/* Development-only observer. Original p256.c is included verbatim, including its notice. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#ifdef OBSERVED
#include "ObservedP256.c"
#else
#include "../../vendor/crypton-2.0.1/cbits/p256/p256.c"
#endif

typedef struct { crypton_p256_digit before; crypton_p256_int value; crypton_p256_digit after; } guarded;
#define GUARD ((crypton_p256_digit)0xa5968778695a4b3cULL)
static void load(guarded *g, const char *hex) {
  uint8_t bytes[32]; unsigned x;
  g->before=g->after=GUARD;
  for(int i=0;i<32;i++){ if(sscanf(hex+2*i,"%2x",&x)!=1) exit(2); bytes[i]=x; }
  crypton_p256_from_bin(bytes,&g->value);
}
static void show(const crypton_p256_int *a) {
  for(int i=P256_NDIGITS-1;i>=0;i--) {
#if P256_BITSPERDIGIT == 64
    printf("%016" PRIx64, a->a[i]);
#else
    printf("%08" PRIx32, a->a[i]);
#endif
  }
}
int main(void) {
  char line[1024],op[32],ah[65],bh[65],mh[65]; int alias,shift;
  uint64_t top,mask;
  while(fgets(line,sizeof(line),stdin)) {
    if(sscanf(line,"%31s %d %d %64s %64s %64s %" SCNu64 " %" SCNu64,op,&alias,&shift,ah,bh,mh,&top,&mask)!=8) return 2;
    guarded a,b,m,c; load(&a,ah);load(&b,bh);load(&m,mh);load(&c,"a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5");
    crypton_p256_int *out=alias==1?&a.value:alias==2?&b.value:alias==3?&m.value:&c.value;
    uint64_t ret=0; uint8_t bin[36]; memset(bin,0xa5,sizeof(bin));
#ifdef OBSERVED
    memset(word_ops,0,sizeof(word_ops));word_corrections=0;word_steps=0;word_stopped=0;word_limit=shift;memset(word_state,0,sizeof(word_state));
#endif
    if(!strcmp(op,"init")) crypton_p256_init(out);
    else if(!strcmp(op,"clear")) crypton_p256_clear(out);
    else if(!strcmp(op,"bit")) ret=crypton_p256_get_bit(&a.value,shift);
    else if(!strcmp(op,"zero")) ret=crypton_p256_is_zero(&a.value);
    else if(!strcmp(op,"odd")) ret=crypton_p256_is_odd(&a.value);
    else if(!strcmp(op,"even")) ret=crypton_p256_is_even(&a.value);
    else if(!strcmp(op,"muladd")) ret=mulAdd(&a.value,top,(crypton_p256_digit)mask,P256_DIGITS(out));
    else if(!strcmp(op,"subtop")) ret=subTop(top,P256_DIGITS(&a.value),mask,P256_DIGITS(out));
    else if(!strcmp(op,"subm")) ret=subM(&m.value,top,P256_DIGITS(out),mask);
    else if(!strcmp(op,"addm")) ret=addM(&m.value,top,P256_DIGITS(out),mask);
    else if(!strcmp(op,"shl")) ret=crypton_p256_shl(&a.value,shift,out);
    else if(!strcmp(op,"shr")) crypton_p256_shr(&a.value,shift,out);
    else if(!strcmp(op,"shr1")) crypton_p256_shr1(&a.value,shift,out);
    else if(!strcmp(op,"cmp")) ret=(int64_t)crypton_p256_cmp(&a.value,&b.value);
    else if(!strcmp(op,"sub")) ret=(int64_t)crypton_p256_sub(&a.value,&b.value,alias==4?NULL:out);
    else if(!strcmp(op,"add")) ret=crypton_p256_add(&a.value,&b.value,alias==4?NULL:out);
    else if(!strcmp(op,"addd")) ret=crypton_p256_add_d(&a.value,top,alias==4?NULL:out);
    else if(!strcmp(op,"mod")) crypton_p256_mod(&m.value,&a.value,out);
    else if(!strcmp(op,"mul")) crypton_p256_modmul(&m.value,&a.value,top,&b.value,out);
    else if(!strcmp(op,"inv")) {
#ifdef OBSERVED
      if(!setjmp(word_jump)) crypton_p256_modinv_vartime(&m.value,&a.value,out);
#else
      crypton_p256_modinv_vartime(&m.value,&a.value,out);
#endif
    }
    else if(!strcmp(op,"valid")) ret=crypton_p256_is_valid_point(&a.value,&b.value);
    else if(!strcmp(op,"from")) { unsigned x;for(int i=0;i<32;i++){sscanf(ah+2*i,"%2x",&x);bin[i+2]=x;}crypton_p256_from_bin(bin+2,out); }
    else if(!strcmp(op,"to")) crypton_p256_to_bin(&a.value,bin+2);
    else if(!strcmp(op,"modadd")) crypton_p256e_modadd(&m.value,&a.value,&b.value,out);
    else if(!strcmp(op,"modsub")) crypton_p256e_modsub(&m.value,&a.value,&b.value,out);
    else if(!strcmp(op,"mont")) crypton_p256e_montmul(&a.value,&b.value,out);
    else if(!strcmp(op,"safe")) crypton_p256e_scalar_invert(&a.value,out);
    else if(!strcmp(op,"n")) *out=crypton_SECP256r1_n;
    else if(!strcmp(op,"p")) *out=crypton_SECP256r1_p;
    else if(!strcmp(op,"b")) *out=crypton_SECP256r1_b;
    else if(!strcmp(op,"r2")) *out=crypton_SECP256r1_r2;
    else if(!strcmp(op,"one")) *out=crypton_SECP256r1_one;
    else return 3;
    printf("{\"ret\":\"%" PRIu64 "\",\"a\":\"",ret);show(&a.value);printf("\",\"b\":\"");show(&b.value);printf("\",\"m\":\"");show(&m.value);printf("\",\"c\":\"");show(&c.value);
    printf("\",\"binary\":\"");for(int i=0;i<36;i++)printf("%02x",bin[i]);
    printf("\",\"guards\":%s",a.before==GUARD&&a.after==GUARD&&b.before==GUARD&&b.after==GUARD&&m.before==GUARD&&m.after==GUARD&&c.before==GUARD&&c.after==GUARD?"true":"false");
#ifdef OBSERVED
    printf(",\"ops\":[");for(int i=0;i<28;i++)printf("%s\"%" PRIu64 "\"",i?",":"",word_ops[i]);
    printf("],\"corrections\":%" PRIu64 ",\"stopped\":%s,\"state\":[",word_corrections,word_stopped?"true":"false");
    if(word_stopped)for(int i=0;i<4;i++){printf("%s\"",i?",":"");show(&word_state[i]);printf("\"");}printf("]");
#endif
    printf("}\n");
  }
  return 0;
}
