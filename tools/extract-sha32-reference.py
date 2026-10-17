#!/usr/bin/env python3
"""Verify pinned copies and construct explicitly instrumented development observers."""
from pathlib import Path
import re,argparse,hashlib,json
D=Path(__file__).resolve().parent/'sha32-reference';R=D.parent.parent
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
for f in (D/'original/cbits').iterdir():assert f.read_bytes()==(R/'vendor/crypton-2.0.1/cbits'/f.name).read_bytes(),f
for bits in [1,256]:
 s=(D/f'original/cbits/crypton_sha{bits}.c').read_text();begin=s.index(f'static void sha{bits}_do_chunk_generic');end=s.index('\n}',begin)+2;body=s[begin:end]
 if bits==1:
  pattern=r'^(\s*)R\((\w), (\w), (\w), (\w), (\w), ([^\n]+);$';matches=list(re.finditer(pattern,body,re.M));assert len(matches)==80
  def instrument(m):
   i=next(i for i,x in enumerate(matches)if x.start()==m.start());args=m.groups()[1:6];return m[0]+f' ref_snapshot({i},5,(uint32_t[]){{'+','.join([args[4],*args[:4]])+'});'
  body=re.sub(pattern,instrument,body,flags=re.M)
  pos=body.index('\n\tR(');body=body[:pos]+'\n\tref_snapshot(999,16,w);'+body[pos:]
 else:
  pattern=r'^(\s*)R\((\w), (\w), (\w), (\w), (\w), (\w), (\w), (\w), k\[i \+ (\d)\], w\[i \+ \d\]\);$';assert len(re.findall(pattern,body,re.M))==8
  def instrument(m):
   args=m.groups()[1:9];return m[0]+f' ref_snapshot(i+{m[10]},8,(uint32_t[]){{'+','.join([args[7],*args[:7]])+'});'
  body=re.sub(pattern,instrument,body,flags=re.M);pos=body.index('\n\tfor (i = 0; i < 64;');body=body[:pos]+'\n\tref_snapshot(999,64,w);'+body[pos:]
 transformed=s[:begin]+body+s[end:]
 target=D/f'ObservedSha{bits}.c'
 if a.check:assert target.read_text()==transformed
 else:target.write_text(transformed)
print('PASS unchanged source copies and mechanically verified round observers')
