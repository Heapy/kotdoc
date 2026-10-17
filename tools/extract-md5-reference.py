#!/usr/bin/env python3
from pathlib import Path
import argparse,re
R=Path(__file__).resolve().parent.parent;D=R/'tools/md5-reference';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
for f in(D/'original/cbits').iterdir():assert f.read_bytes()==(R/'vendor/crypton-2.0.1/cbits'/f.name).read_bytes(),f
s=(D/'original/cbits/crypton_md5.c').read_text();begin=s.index('static void md5_do_chunk');end=s.index('\n}',begin)+2;body=s[begin:end]
pattern=r'^(\s*)R\(f\d, (\w), (\w), (\w), (\w), [^\n]+\);$';matches=list(re.finditer(pattern,body,re.M));assert len(matches)==64
# MD5 source updates its first macro argument; rotate names into canonical a,b,c,d.
def instrument(m):
 i=next(i for i,x in enumerate(matches)if x.start()==m.start());args=m.groups()[1:5];return m[0]+f' ref_snapshot({i},4,(uint32_t[]){{'+','.join([args[3],*args[:3]])+'});'
body=re.sub(pattern,instrument,body,flags=re.M);i=body.index('\n\tR(');body=body[:i]+'\n\tref_snapshot(999,16,w);'+body[i:];result=s[:begin]+body+s[end:];target=D/'ObservedMd5.c'
if a.check:assert target.read_text()==result
else:target.write_text(result)
print('PASS unchanged MD5 sources and mechanically verified64-round observer')
