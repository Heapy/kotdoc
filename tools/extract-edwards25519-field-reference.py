#!/usr/bin/env python3
"""Exact source preparation for the distinct Edwards Donna field/table checkpoint."""
import argparse,hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];VENDOR=ROOT/'vendor/crypton-2.0.1';SOURCE=VENDOR/'cbits/ed25519';OUT=ROOT/'tools/edwards25519-field-reference'
FILES=['curve25519-donna-32bit.h','curve25519-donna-64bit.h','curve25519-donna-helpers.h','ed25519-donna-portable.h','ed25519-donna-portable-identify.h','ed25519-donna.h','ed25519-donna-32bit-tables.h','ed25519-donna-64bit-tables.h','ed25519-donna-basepoint-table.h']
NOTICE=['curve25519-donna-32bit.h','curve25519-donna-64bit.h','curve25519-donna-helpers.h','ed25519-donna.h']
def prepared():
 meta=json.loads((VENDOR/'SOURCE.json').read_text());assert meta['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 files={};manifest={'status':'complete pinned field/helper/table source inventory; scalar/point/public remaining; independent verification is recorded in fixtures','archiveSha256':meta['sha256'],'sources':{},'functions':{},'tables':{'profile32Words':1030,'profile64Words':515,'packedBasepointBytes':24576},'limits':['Distinct Edwards Donna implementation: do not reuse X25519 field arithmetic.','Both algorithm profiles will run on native host, not actual32 OS ABI.','Source little-endian expand and conditional byte moves cast byte pointers to word pointers: alignment/effective-type constraints must be recorded in oracle metadata.','Conditional swap intended masks0/1; signed negation of INT_MIN for arbitrary mask is C UB.','square_times32 nonpositive count eventually signed decrement UB; square_times64 count0 requires2^64 iterations. Positive finite schedules only, no invented identity.']}
 for name in FILES:
  data=(SOURCE/name).read_bytes();sha=hashlib.sha256(data).hexdigest();assert sha==meta['files']['cbits/ed25519/'+name]
  files[OUT/'original'/name]=data;manifest['sources']['vendor/crypton-2.0.1/cbits/ed25519/'+name]=sha
  names=re.findall(r'^((?:curve25519_)\w+)\([^\n]*',data.decode(),re.M)
  if names:manifest['functions'][name]=names
 for name,count in [('ed25519-donna-32bit-tables.h',1030),('ed25519-donna-64bit-tables.h',515),('ed25519-donna-basepoint-table.h',24576)]:assert len(re.findall(r'0x[0-9a-fA-F]+',(SOURCE/name).read_text()))==count
 assert len(manifest['functions']['curve25519-donna-32bit.h'])==15
 assert len(manifest['functions']['curve25519-donna-64bit.h'])==16
 assert len(manifest['functions']['curve25519-donna-helpers.h'])==3
 helper=(SOURCE/'curve25519-donna-helpers.h').read_text()
 def hook(m):
  name,args=m[1],[v.strip()for v in m[2].split(',')]
  label=('mul'if name=='mul_noinline'else'square'+args[2])+':'+args[0]
  return m[0]+f' ef_snapshot("{label}",{args[0]});'
 observed,count=re.subn(r'curve25519_(square_times|mul_noinline)\(([^;]+)\);',hook,helper)
 assert count==30,count
 files[OUT/'ObservedHelpers.h']=observed.encode()
 notice=b'\n\n'.join((SOURCE/name).read_bytes().split(b'*/',1)[0]+b'*/'for name in NOTICE)+b'\n'
 files[ROOT/'licenses/ed25519-donna-public-domain.txt']=notice
 files[OUT/'manifest.json']=(json.dumps(manifest,indent=2)+'\n').encode()
 return files
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
 for path,data in prepared().items():
  if args.check:assert path.read_bytes()==data,path
  else:path.write_bytes(data)
 print('Verified9 exact Edwards field/platform/table originals,15/16functions+3helpers and notices'if args.check else'Prepared exact Edwards field/platform/table originals and notices')
