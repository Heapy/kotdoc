#!/usr/bin/env python3
"""Extract unchanged complete Donna and mechanically add dev-only observers."""
import argparse,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'tools/x25519-reference'
def extract(bits):
 source=(ROOT/f'vendor/crypton-2.0.1/cbits/curve25519/curve25519-donna{"-c64" if bits==64 else ""}.c').read_text()
 # Observer declaration follows the source typedefs, before any arithmetic.
 marker='static void\nfmonty' if bits==64 else 'static void fmonty'
 assert source.count(marker)==1
 observed=source.replace(marker,'static void x25_round(unsigned, const limb*, const limb*, const limb*, const limb*);\nstatic void x25_inverse(const char*, const limb*);\n'+marker)
 loop='      byte <<= 1;';assert observed.count(loop)==1
 observed=observed.replace(loop,'      x25_round(i*8+j,nqx,nqz,nqpqx,nqpqz);\n'+loop)
 start=observed.index('crecip(');end=observed.index('\n}\n',start)
 body=observed[start:end]
 def hook(m):
  name,args=m.group(1),[s.strip() for s in m.group(2).split(',')]
  label=('mul' if name=='fmul' else 'square'+(args[2] if name=='fsquare_times' else ''))+':'+args[0]
  return m.group(0)+f' x25_inverse("{label}",{args[0]});'
 body,count=re.subn(r'\b(fmul|fsquare|fsquare_times)\(([^;]+)\);',hook,body)
 assert count==(49 if bits==32 else 22),(bits,count)
 observed=observed[:start]+body+observed[end:]
 return source,observed
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
 for bits in (32,64):
  for stem,text in zip(('Original','Observed'),extract(bits)):
   path=OUT/f'{stem}{bits}.h'
   if a.check:assert path.read_text()==text,path
   else:path.write_text(text)
 print('Verified exact Donna sources and observer transformations' if a.check else 'Extracted Donna sources and observers')
