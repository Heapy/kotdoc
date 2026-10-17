#!/usr/bin/env python3
from pathlib import Path
import ctypes as C,subprocess,random,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');p.add_argument('--optimization',default='-O2',choices=['-O0','-O2','-O3']);a=p.parse_args();rng=random.Random(613321);aliases=[];traces=[]
for bits in [5]:
 path=f'/tmp/kotdoc-sha{bits}-observed.dylib';subprocess.run(['cc',a.optimization,'-shared','-fPIC','-DOBSERVED','-I'+str(D/'original/cbits'),str(D/'Md5Reference.c'),'-o',path],check=True);lib=C.CDLL(path);lib.ref_run.argtypes=[C.c_uint,C.c_void_p,C.c_void_p,C.c_uint32,C.c_uint32,C.c_void_p];lib.ref_trace.restype=C.POINTER(C.c_uint32)
 for alg in ['MD5']:
  cap=96;state=72
  for op in ([2,3,7]if True else[11,12,7]):
   for mode in [1,2,3]:
    for i in range(20):
     n=[0,1,55,56,63,64,65,127,128,129][i%10];arg=[0,1,n,n+1,0xffffffff][i%5]
     ctx=bytearray(rng.randbytes(cap+96));data=bytearray(rng.randbytes(max(n,64)+96));out=data if mode==1 else ctx;outpos=16 if mode==1 else 16+(8 if mode==2 else state+4)
     before=[bytes(x)for x in(ctx,data,out)];ca=(C.c_ubyte*len(ctx)).from_buffer(ctx);da=(C.c_ubyte*len(data)).from_buffer(data);oa=(C.c_ubyte*len(out)).from_buffer(out)
     lib.ref_reset();lib.ref_run(op,C.byref(ca,16),C.byref(da,16),n,arg,C.byref(oa,outpos))
     aliases.append('\t'.join([alg,str(op),str(n),str(arg),*[x.hex()for x in before],ctx.hex(),data.hex(),out.hex(),str(mode),str(outpos)]))
  for i in range(40):
   ctx=bytearray(rng.randbytes(cap+32));data=bytearray(rng.randbytes(96));out=bytearray(96);ca=(C.c_ubyte*len(ctx)).from_buffer(ctx);da=(C.c_ubyte*len(data)).from_buffer(data);oa=(C.c_ubyte*len(out)).from_buffer(out)
   before=bytes(ctx);lib.ref_reset();lib.ref_run(4,C.byref(ca,16),C.byref(da,16),64,0,C.byref(oa,16));n=lib.ref_count();trace=list(lib.ref_trace()[:n])
   traces.append('\t'.join([alg,before.hex(),data.hex(),ctx.hex(),','.join(map(str,trace))]))
for name,rows in [('AliasObservations.tsv',aliases),('TraceObservations.tsv',traces)]:
 blob='\n'.join(rows)+'\n'
 if a.verify:assert(D/name).read_text()==blob,name
 else:(D/name).write_text(blob)
 print('PASS',name,len(rows))
