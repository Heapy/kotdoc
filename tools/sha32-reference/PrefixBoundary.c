/* Development-only virtual control observer; no input allocation or hash execution.
 * Expressions copied from pinned crypton_hash_prefix.c after substituting
 * BLOCK_SIZE=64, sizeof(bits)=8, get_index=counter&63. Original stays unchanged.
 */
#include <stdint.h>
#include "original/cbits/crypton_hash_prefix.h"
void ref_control(uint32_t len,uint32_t n,uint64_t counter,uint32_t pos,uint64_t *out){
 uint32_t index=counter&63,padidx,padlen;
 n+=(len-n)&constant_time_lt(len,n);counter+=n;padidx=counter&63;
 padlen=64+56-padidx;padlen-=64&constant_time_lt(padidx,56);
 out[0]=n;out[1]=index;out[2]=padlen;out[3]=len+64+sizeof(uint64_t);
 out[4]=pos<out[3];out[5]=pos<len;out[6]=constant_time_lt(pos,n);
 out[7]=constant_time_eq(pos,n);out[8]=constant_time_ge(pos,n+padlen);out[9]=constant_time_eq(pos,n+padlen+sizeof(uint64_t));
}
