#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,sys
R=Path(__file__).resolve().parents[1];V=R/'vendor/crypton-2.0.1';D=R/'tools/sha512-prefix-boundary-reference'
FILES=['crypton_sha512.c','crypton_sha512.h','crypton_hash_prefix.c','crypton_hash_prefix.h','crypton_bitfn.h','crypton_align.h']
def sources():
 m=json.loads((V/'SOURCE.json').read_text());assert m['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 out={}
 for f in FILES:
  p='cbits/'+f;data=(V/p).read_bytes();assert hashlib.sha256(data).hexdigest()==m['files'][p];out['original/'+f]=data
 c=out['original/crypton_hash_prefix.c'].decode()
 replacements=[('pos = 0;','pos = probe_start;\n\tindex = (index + probe_start) % HASHED(BLOCK_SIZE);'),('while (pos < len + HASHED(BLOCK_SIZE) + sizeof(bits)) {','while (pos < len + HASHED(BLOCK_SIZE) + sizeof(bits) && pos < probe_stop) {'),('b = *(data++) & (uint8_t) constant_time_lt(pos, n);','b = (uint8_t)(pos * 17 + 3) & (uint8_t) constant_time_lt(pos, n);'),('/* Store this byte into the buffer */','probe_byte(pos, index, b, pos < len, constant_time_lt(pos,n), constant_time_eq(pos,n), constant_time_ge(pos,n+padlen));\n\t\t/* Store this byte into the buffer */'),('HASHED_LOWER(do_chunk)(ctx, (void *) ctx->buf);','probe_blocks++;'),('CRYPTON_HASHED(select_digest)(ctx, out, out_mask);','probe_select(pos, out_mask);')]
 for a,b in replacements:assert c.count(a)==1,a;c=c.replace(a,b)
 c=c.replace('\n}\n','\n\tprobe_final_pos=pos;probe_final_index=index;probe_n=n;probe_padlen=padlen;\n}\n')
 out['ObservedPrefix.c']=c.encode();out['Transformations.json']=(json.dumps(dict(kind='segmented original prefix CONTROL observer, not ordinary hashing oracle',replacements=replacements,unchanged=['original uint32 positions/counters/clamp/masks/padding/loop predicate','original sizeof(bits) bound expression','original source byte-size/counter expressions'],virtualized=['input bytes=(pos*17+3)mod256','compression omitted; no state/hash claim','source start/index set to selected finite window, stop limits execution'],domain='0<=len<=Int.MAX_VALUE; source bound does not uint32-wrap; selected windows only, no huge allocation'),indent=2)+'\n').encode()
 return out
if __name__=='__main__':
 for p,data in sources().items():
  target=D/p
  if '--check'in sys.argv:assert target.read_bytes()==data,p
  else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
 print('PASS exact pinned prefix originals and mechanical segmented observer transformations')
