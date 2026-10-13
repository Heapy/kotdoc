#!/usr/bin/env python3
"""Compare original gamma Float powf with JVM double pow rounded to Float."""
import argparse,json,os,random,struct,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--oracle',required=True);p.add_argument('--java',default=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java');a=p.parse_args()
rng=random.Random(12345);values=[rng.randrange(0x7f800000) for _ in range(100002)]
gammas=[struct.unpack('<I',struct.pack('<f',v))[0] for v in [0.5,1,1.1,1.5,2,2.2,3,0.3,17]]
requests=['("gamma","normal","data",['+','.join(map(str,[len(values)//3,1,g,0,len(values)]))+'],['+','.join(map(str,values))+'])' for g in gammas]
outs=subprocess.check_output([a.oracle],input=('\n'.join(requests)+'\n').encode()).splitlines()
jrequests=[str(g)+' '+str(len(values))+' '+' '.join(map(str,values)) for g in gammas]
source=Path(__file__).resolve().parent/'hdr-diagnostics/HdrPowProbe.java'
jouts=subprocess.check_output([a.java,str(source)],input=('\n'.join(jrequests)+'\n').encode()).splitlines()
total=0
for g,h,j in zip(gammas,outs,jouts):
 data=bytes.fromhex(json.loads(h)['hex']);native=struct.unpack('<'+'I'*len(values),data);jvm=list(map(int,j.split()))
 diffs=[(values[i],x,y) for i,(x,y) in enumerate(zip(native,jvm)) if x!=y]
 total+=len(diffs)
 print(json.dumps(dict(gammaBits=g,components=len(values),differences=len(diffs),examples=diffs[:5])))
print(json.dumps(dict(components=len(values)*len(gammas),differences=total)))
