#!/usr/bin/env python3
from pathlib import Path
import re,argparse
R=Path(__file__).resolve().parent.parent;D=R/'tools/ripemd160-reference';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
for name in ['cbits/crypton_ripemd.c','cbits/crypton_ripemd.h','cbits/crypton_bitfn.h','cbits/crypton_align.h','Crypto/Hash/RIPEMD160.hs','Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','tests/HashSpec.hs']:
 assert(R/'vendor/crypton-2.0.1'/name).read_bytes()==(D/'original'/name).read_bytes(),name
c=(D/'original/cbits/crypton_ripemd.c').read_text();notice='\n'.join(re.sub(r'^ \* ?','',l)for l in c.split('/*',1)[1].split(' */',1)[0].splitlines()).strip();assert notice==(R/'licenses/crypton-ripemd160-BSD-2-Clause.txt').read_text().strip()
s=c
for name in ['crypton_ripemd160_init','crypton_ripemd160_update','crypton_ripemd160_finalize','ripemd160_do_chunk']:s=re.sub(r'\b'+name+r'\b','observed_'+name,s)
i=0
def observed(m):
 global i
 result=m.group(0)+'\n ripemd_trace('+str(i)+',a1,b1,c1,d1,e1,a2,b2,c2,d2,e2);';i+=1;return result
s=re.sub(r'\bR\([^;]+\);',observed,s);assert i==160;s='#include <stdint.h>\nextern void ripemd_trace(int,'+','.join(['uint32_t']*10)+');\n'+s;assert(D/'ObservedRipemd160.c').read_text()==s
k=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoRipemd160.kt';k=k if k.exists()else D/'work-in-progress/CryptoRipemd160.kt';kt=k.read_text();expected=[]
for line in re.findall(r'\bR\(([^;]+)\);',c):
 xs=[x.strip()for x in line.split(',')];expected.append([('abcde'.index(x[0])+(5 if x[1]=='2'else 0))for x in xs[:5]]+[int(xs[5][1:]),int(xs[6][1:]),int(xs[7]),int(xs[8])])
actual=[[int(x)for x in row.split(',')]for row in re.findall(r'intArrayOf\(([^)]+)\)',kt.split('private val operations=arrayOf(')[1].split('fun chunk')[0])];assert actual==expected
sourceK=re.findall(r'#define K\d+\s+(0x[0-9a-f]+)',c);kotlinK=re.findall(r'0x[0-9a-f]+',kt.split('private val constants=')[1].split('private val operations=')[0]);assert [int(x,16)for x in sourceK]==[int(x,16)for x in kotlinK] and len(sourceK)==9
original=(R/'vendor/crypton-2.0.1/tests/HashSpec.hs').read_text().split('spec :: Spec')[0].replace('module HashSpec (\n    spec,\n) where','module OriginalRipemd160HashSpec where');assert(D/'OriginalRipemd160HashSpec.hs').read_text().startswith(original)
sourceIV=re.findall(r'ctx->h\[\d\] = (0x[0-9a-f]+);',c.split('#define K1')[0]);kotlinIV=re.findall(r'0x[0-9a-f]+',kt.split('val iv=intArrayOf(')[1].split(');')[0]);assert [int(x,16)for x in sourceIV]==[int(x,16)for x in kotlinIV] and len(sourceIV)==5
assert 'static uint8_t padding[64] = { 0x80, };'in c and 'padding[0]=128.toByte()'in kt
public=R/'core/src/main/kotlin/io/heapy/kotdoc/crypto/CryptoRipemd160Hash.kt';public=public if public.exists()else D/'work-in-progress/CryptoRipemd160Hash.kt';assert 'Prefix'not in public.read_text()
print('PASS 4 definitions/160 ordered R steps/9 constants/verbatim test body/trace/license/no Prefix')
