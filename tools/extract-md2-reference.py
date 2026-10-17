#!/usr/bin/env python3
from pathlib import Path
import re,argparse
R=Path(__file__).resolve().parent.parent;D=R/'tools/md2-reference';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
for n in ['cbits/crypton_md2.c','cbits/crypton_md2.h','cbits/crypton_bitfn.h','Crypto/Hash/MD2.hs','Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','tests/HashSpec.hs']:
 f=R/'vendor/crypton-2.0.1'/n;target=D/'original'/n
 if a.check:assert f.read_bytes()==target.read_bytes(),n
 else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(f.read_bytes())
c=(D/'original/cbits/crypton_md2.c').read_text();s=c
notice='\n'.join(re.sub(r'^ \* ?', '', line) for line in c.split('/*',1)[1].split(' */',1)[0].splitlines()).strip()
assert notice==(R/'licenses/crypton-md2-BSD-2-Clause.txt').read_text().strip(),'MD2 C copyright/license binding'
for x,y in [('crypton_md2_init','observed_md2_init'),('crypton_md2_update','observed_md2_update'),('crypton_md2_finalize','observed_md2_finalize'),('md2_do_chunk','observed_md2_chunk'),('S_table','observed_S_table'),('padding_table','observed_padding_table')]:s=re.sub(r'\b'+x+r'\b',y,s)
s=s.replace('t = (t + i) & 0xff;','t = (t + i) & 0xff;\n        md2_trace(1,i,x,48);')
x='for (i = 0; i < 16; i++)\n\t\tt = ctx->cksum[i] ^= observed_S_table[buf[i] ^ t];';assert s.count(x)==1;s=s.replace(x,'for (i = 0; i < 16; i++) {\n\t\tt = ctx->cksum[i] ^= observed_S_table[buf[i] ^ t];\n        md2_trace(2,i,ctx->cksum,16);\n    }')
expected='#include <stdint.h>\nextern void md2_trace(int,int,const uint8_t*,int);\n'+s
if a.check:assert(D/'ObservedMd2.c').read_text()==expected
else:(D/'ObservedMd2.c').write_text(expected)
k=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoMd2.kt'
if not k.exists():k=D/'work-in-progress/CryptoMd2.kt'
sourceTable=re.findall(r'0x[0-9a-fA-F]+',c.split('static uint8_t S_table[]')[1].split('};')[0]);ktTable=re.findall(r'0x[0-9a-fA-F]+',k.read_text().split('private val substitution=intArrayOf(')[1].split(')')[0]);assert len(sourceTable)==256 and sourceTable==ktTable
padding=re.findall(r'UBYTES\("([^"]*)"\)',c);assert len(padding)==17
for n,value in enumerate(padding):assert [int(x,16)for x in re.findall(r'\\x([0-9a-fA-F]+)',value)]==[n]*n
original=(R/'vendor/crypton-2.0.1/tests/HashSpec.hs').read_text().split('spec :: Spec')[0].replace('module HashSpec (\n    spec,\n) where','module OriginalMd2HashSpec where');assert(D/'OriginalMd2HashSpec.hs').read_text().startswith(original),'original test expectations modified'
public=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoMd2Hash.kt'
if not public.exists():public=D/'work-in-progress/CryptoMd2Hash.kt'
assert 'Prefix'not in public.read_text(),'MD2 has no source Prefix instance'
print('PASS 4 definitions/256 S bytes/17 padding payloads/verbatim test body/mechanical trace/no Prefix')
