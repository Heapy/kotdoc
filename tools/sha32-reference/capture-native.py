#!/usr/bin/env python3
"""Pinned, unchanged portable C oracle. Expected bytes never originate in Kotlin."""
from pathlib import Path
import argparse,ctypes as C,random,subprocess,json,hashlib
D=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');p.add_argument('--optimization',default='-O2',choices=['-O0','-O2','-O3']);a=p.parse_args()
rng=random.Random(6131256);rows=[]
for bits in [1,256]:
 libpath=f'/tmp/kotdoc-sha{bits}-reference.dylib'
 subprocess.run(['cc',a.optimization,'-shared','-fPIC','-I'+str(D/'original/cbits'),str(D/f'Sha{bits}Reference.c'),'-o',libpath],check=True)
 lib=C.CDLL(libpath);assert lib.ref_size()==(96 if bits==1 else 168);assert lib.ref_state_offset()==(72 if bits==1 else 136);lib.ref_run.argtypes=[C.c_uint,C.c_void_p,C.c_void_p,C.c_uint32,C.c_uint32,C.c_void_p];lib.ref_helper.argtypes=[C.c_uint,C.c_uint64,C.c_uint64];lib.ref_helper.restype=C.c_uint64
 for alg in (['SHA1']if bits==1 else ['SHA224','SHA256']):
  cap=96 if bits==1 else 192
  for op in ([0,1,2,3,4,5,6,7,8]if alg!='SHA224'else[9,10,11,12,4,5,6,7,8]):
   for i in range(120):
    length=([0,1,7,55,56,57,63,64,65,119,120,127,128,129,255,256,257,2901][i%18]if i<72 else rng.randrange(4097))
    arg=([0,1,55,56,63,64,127,128,0x7fffffff,0x80000000,0xffffffff,length,max(0,length-1),length+1][i%14])
    ctx=bytearray(rng.randbytes(cap+32));data=bytearray(rng.randbytes(max(length,64)+32));out=bytearray(rng.randbytes(96))
    # Safe pointer alignment, guards, full raw counter/state widths; no overlapping memcpy arguments.
    before=[bytes(x)for x in(ctx,data,out)]
    ca=(C.c_ubyte*len(ctx)).from_buffer(ctx);da=(C.c_ubyte*len(data)).from_buffer(data);oa=(C.c_ubyte*len(out)).from_buffer(out)
    lib.ref_run(op,C.byref(ca,16),C.byref(da,16),length,arg,C.byref(oa,16))
    rows.append('\t'.join([alg,str(op),str(length),str(arg),*[x.hex()for x in before],ctx.hex(),data.hex(),out.hex()]))
 for op in range(7):
  vals=[0,1,2,0x7fffffff,0x80000000,0xffffffff,0x100000000,0x7fffffffffffffff,0x8000000000000000,0xffffffffffffffff]
  for x in vals:
   for y in vals:rows.append('\t'.join(['mask',str(op),str(x),str(y),str(lib.ref_helper(op,x,y))]))
blob='\n'.join(rows)+'\n';path=D/'NativeObservations.tsv'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(rows),'unchanged portable C observations')
