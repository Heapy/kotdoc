#!/usr/bin/env python3
"""Prepare original Pandoc with four modules exposed; never alter implementation."""
import argparse,io,subprocess,tarfile
from pathlib import Path
root=Path(__file__).resolve().parents[2];here=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--directory',type=Path,required=True);args=p.parse_args();out=args.directory.resolve();out.mkdir(parents=True,exist_ok=True);source=out/'pandoc'
original=(root/'upstream/pandoc/pandoc.cabal').read_text();patched=original
modules=['Text.Pandoc.Readers.HTML.Parsing','Text.Pandoc.Readers.HTML.TagCategories','Text.Pandoc.Readers.HTML.Types','Text.Pandoc.Readers.HTML.Table']
for name in modules:
 line='                   '+name+',\n';assert patched.count(line)==1;patched=patched.replace(line,'')
needle='  exposed-modules: Text.Pandoc,\n';assert patched.count(needle)==1
patched=patched.replace(needle,needle+''.join('                   '+name+',\n' for name in modules))
if not source.exists():
 source.mkdir()
 data=subprocess.check_output(['git','archive','HEAD'],cwd=root/'upstream/pandoc')
 with tarfile.open(fileobj=io.BytesIO(data)) as archive:archive.extractall(source,filter='data')
(source/'pandoc.cabal').write_text(patched)
files=subprocess.check_output(['git','ls-files','-z'],cwd=root/'upstream/pandoc').decode().split('\0')
for name in filter(None,files):
 expected=patched.encode() if name=='pandoc.cabal' else (root/'upstream/pandoc'/name).read_bytes()
 assert (source/name).read_bytes()==expected,name
config=(here/'cabal.project').read_text().replace('packages: . ../../upstream/pandoc ../../upstream/pandoc-types','packages: '+str(here)+' '+str(source)+' '+str(root/'upstream/pandoc-types'))
(out/'cabal.project').write_text(config)
(out/'cabal.project.freeze').write_bytes((here/'cabal.project.freeze').read_bytes())
print('Verified original Pandoc implementation, tests and data; only four module exposure declarations changed')
print(out/'cabal.project')
