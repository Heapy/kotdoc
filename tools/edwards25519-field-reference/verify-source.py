#!/usr/bin/env python3
"""Compile untouched field headers and verify all native table entries against source literals."""
import ctypes as C,re,subprocess,tempfile
from pathlib import Path
FOLDER=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='kotdoc-edwards-source-')as tmp:
 for bits,n,words in [(32,10,1030),(64,5,515)]:
  path=Path(tmp)/f'{bits}.dylib';subprocess.run(['clang','-O2','-std=c99',f'-DFIELD_BITS={bits}','-dynamiclib',str(FOLDER/'SourceProbe.c'),'-o',str(path)],check=True)
  lib=C.CDLL(str(path));lib.source_table_word.restype=C.c_uint64
  assert lib.source_field_limbs()==n and lib.source_table_words()==words and lib.source_packed_bytes()==24576
  text=(FOLDER/'original'/f'ed25519-donna-{bits}bit-tables.h').read_text();values=[int(v,16)for v in re.findall(r'0x[0-9a-fA-F]+',text)]
  offset=0
  for group,count in enumerate([4*n,n,n,n,32*3*n]):
   for i in range(count):assert lib.source_table_word(group,i)==values[offset+i],(bits,group,i)
   offset+=count
  for i,v in enumerate(re.findall(r'0x[0-9a-fA-F]+',(FOLDER/'original/ed25519-donna-basepoint-table.h').read_text())):assert lib.source_packed_byte(i)==int(v,16)
  print(f'PASS{bits}:full original headers compiled;{words}tablewords+24576bytes match every native entry; no Kotlin parity claim')
