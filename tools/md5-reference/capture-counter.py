from pathlib import Path
import random,ctypes as C,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();rng=random.Random(61332064);rows=[]
for alg in ['MD5']:
 bits=5;cap=96
 path=f'/tmp/kotdoc-sha{bits}-counter.dylib';subprocess.run(['cc','-O2','-shared','-fPIC','-I'+str(D/'original/cbits'),str(D/'Md5Reference.c'),'-o',path],check=True);lib=C.CDLL(path);lib.ref_run.argtypes=[C.c_uint,C.c_void_p,C.c_void_p,C.c_uint32,C.c_uint32,C.c_void_p]
 for counter in [0,1,55,56,63,64,(1<<61)-1,(1<<64)-129,(1<<64)-65,(1<<64)-64,(1<<64)-2,(1<<64)-1]:
  for length in [0,1,2,55,56,63,64,65,128,129,257]:
   for alignment in [0,1,2,3,7]:
    ctx=bytearray(rng.randbytes(cap+32));ctx[16:24]=counter.to_bytes(8,'little');data=bytearray(rng.randbytes(length+64));out=bytearray(rng.randbytes(96));before=[bytes(x)for x in(ctx,data,out)];ca=(C.c_ubyte*len(ctx)).from_buffer(ctx);da=(C.c_ubyte*len(data)).from_buffer(data);oa=(C.c_ubyte*len(out)).from_buffer(out)
    op=1;lib.ref_run(op,C.byref(ca,16),C.byref(da,16+alignment),length,0,C.byref(oa,16))
    rows.append('\t'.join([alg,str(op),str(length),'0',*[x.hex()for x in before],ctx.hex(),data.hex(),out.hex(),'0','16',str(16+alignment)]))
blob='\n'.join(rows)+'\n';path=D/'CounterObservations.tsv'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(rows),'unchanged C counter wrap/alignment observations')
