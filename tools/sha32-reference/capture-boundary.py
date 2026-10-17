from pathlib import Path
import ctypes as C,subprocess,argparse
D=Path(__file__).resolve().parent;p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();rows=[]
subprocess.run(['cc','-O2','-shared','-fPIC',str(D/'PrefixBoundary.c'),'-o','/tmp/kotdoc-sha32-boundary.dylib'],check=True);lib=C.CDLL('/tmp/kotdoc-sha32-boundary.dylib');lib.ref_control.argtypes=[C.c_uint32,C.c_uint32,C.c_uint64,C.c_uint32,C.c_void_p]
for length in [0,1,55,56,63,64,65,0x7fffffff-145,0x7fffffff-73,0x7fffffff-72,0x7fffffff-71,0x7fffffff-1,0x7fffffff]:
 for n in [0,1,length//2,max(0,length-1),length,length+1,0x80000000,0xffffffff]:
  for counter in [0,1,55,56,63,64,0x7fffffffffffffff,0xffffffffffffffff]:
   for pos in sorted(set([0,1,max(0,n-1),n,n+1,max(0,length-1),length,length+1,length+71,length+72,length+73])):
    pos&=0xffffffff;out=(C.c_uint64*10)();lib.ref_control(length,n,counter,pos,out);rows.append('\t'.join(map(str,[length,n,counter,pos,*out])))
blob='\n'.join(rows)+'\n';path=D/'PrefixBoundary.tsv'
if a.verify:assert path.read_text()==blob
else:path.write_text(blob)
print('PASS',len(rows),'virtual control observations; not actual 2GiB hashing')
