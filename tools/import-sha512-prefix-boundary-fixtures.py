#!/usr/bin/env python3
from pathlib import Path
import argparse,ctypes as C,gzip,hashlib,json,subprocess,sys,tempfile
R=Path(__file__).resolve().parents[1];D=R/'tools/sha512-prefix-boundary-reference';T=R/'core/src/test/resources/crypto-sha512-prefix-boundary/data.jsonl.gz';MAX=2147483647;MASK=(1<<64)-1

def requests():
 bases=[]
 for length in [0,1,111,112,127,128,MAX-145,MAX-144,MAX-143,MAX-8,MAX-1,MAX]:
  for n in sorted(set([0,1,max(0,length-1),length,length+1,2147483648,4294967295])):
   for low,high in [(0,0),(111,0),(112,0),(127,0),(MASK,0),(MASK,MASK)]:bases.append((length,n,low,high))
 for index in range(128):
  for n in [0,MAX]:bases.append((MAX,n,index,0))
 for length,nInput,low,high in bases:
  n=min(nInput,length);initial=low%128;padidx=(low+n)%128;padlen=112-padidx if padidx<112 else 240-padidx;limit=length+144;end=n+padlen+16
  starts=sorted(set([0,1,111,112,127,128,*[max(0,x+d)for x in [length,n,n+padlen,end,limit]for d in [-2,-1,0,1]]]))
  for start in starts:
   if start>limit+1:continue
   stop=min(start+(260 if start==0 else 9),limit+2)
   yield dict(length=length,n=nInput,low=f'{low:016x}',high=f'{high:016x}',start=start,stop=stop)

def pins():
 subprocess.run([sys.executable,str(R/'tools/extract-sha512-prefix-boundary-reference.py'),'--check'],check=True)
 paths=[p for p in D.rglob('*')if p.is_file()and'work-in-progress'not in str(p)and'__pycache__'not in str(p)]+[R/'tools/extract-sha512-prefix-boundary-reference.py',Path(__file__).resolve(),R/'licenses/crypton-sha512-BSD-2-Clause.txt',R/'vendor/crypton-2.0.1/SOURCE.json']
 return{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest()for p in paths}

def main():
 p=argparse.ArgumentParser();p.add_argument('--verify-oracle',action='store_true');p.add_argument('--optimization',choices=['-O0','-O2','-O3'],default='-O2');a=p.parse_args();rows=[]
 with tempfile.TemporaryDirectory(prefix='kotdoc-sha512-prefix-')as tmp:
  lib=Path(tmp)/'probe.dylib';subprocess.run(['cc',a.optimization,'-std=c99','-DNO_INLINE_ASM','-shared','-fPIC','-I'+str(D/'original'),str(D/'PrefixBoundaryReference.c'),'-o',str(lib)],check=True);api=C.CDLL(str(lib));api.ref_probe.argtypes=[C.c_uint32,C.c_uint32,C.c_uint64,C.c_uint64,C.c_uint32,C.c_uint32,C.POINTER(C.c_uint64)];api.ref_events.restype=C.POINTER(C.c_uint64);api.ref_selections.restype=C.POINTER(C.c_uint64)
  for r in requests():
   out=(C.c_uint64*10)();api.ref_probe(r['length'],r['n'],int(r['low'],16),int(r['high'],16),r['start'],r['stop'],out);summary=list(out);events=api.ref_events();selections=api.ref_selections();e=dict(summary=[*summary[:8],f'{summary[8]:016x}',f'{summary[9]:016x}'],events=[[events[i*7+j]for j in range(7)]for i in range(summary[6])],selections=[[selections[i*2+j]for j in range(2)]for i in range(summary[7])]);rows.append(dict(request=r,expected=e))
 manifest=dict(cases=len(rows),sources=pins(),kind='segmented original prefix control observer; virtual bytes, no compression/hugeallocation')
 if a.verify_oracle:
  with gzip.open(T,'rt')as f:old=[json.loads(x)for x in f]
  assert old==[manifest,*rows]
 else:T.parent.mkdir(parents=True,exist_ok=True);T.write_bytes(gzip.compress(('\n'.join(json.dumps(x,separators=(',',':'))for x in [manifest,*rows])+'\n').encode(),mtime=0))
 print('PASS',len(rows),'segmented source boundary windows',len(manifest['sources']),'pins',a.optimization)
if __name__=='__main__':main()
