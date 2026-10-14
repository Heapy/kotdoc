#!/usr/bin/env python3
"""Package every pinned Pandoc template verbatim; expectations are not involved."""
import argparse
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
source=root/'upstream/pandoc/data/templates';dest=root/'core/src/main/resources/io/heapy/kotdoc/pandoc-data'
files={str(f.relative_to(source)):f.read_bytes() for f in sorted(source.rglob('*')) if f.is_file()}
expected={'templates/'+name:data for name,data in files.items()}
expected['templates.txt']=(''.join('templates/'+name+'\n' for name in files)).encode()
if a.check:
 actual={str(f.relative_to(dest)):f.read_bytes() for f in dest.rglob('*') if f.is_file()}
 assert actual==expected,'Packaged Pandoc template drift'
else:
 for name,data in expected.items():
  path=dest/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
print(f'{"Verified" if a.check else "Packaged"} {len(files)} unchanged Pandoc templates')
