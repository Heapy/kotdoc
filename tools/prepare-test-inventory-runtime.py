#!/usr/bin/env python3
"""Prepare isolated original test build and export-only structured registration observer."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]


def prepare(directory,toolchain):
    directory=directory.resolve();directory.mkdir(parents=True,exist_ok=True)
    project=(ROOT/'tools/haskell-html-reader-reference/cabal.project').read_text()
    packages=[ROOT/'upstream/pandoc',ROOT/'upstream/pandoc-types']
    for name,version in [('texmath','0.13.3.1'),('citeproc','0.14.1'),('commonmark-pandoc','0.3.0.2')]:
        archive=toolchain/'cabal-state/packages/hackage.haskell.org'/name/version/(name+'-'+version+'.tar.gz')
        if not archive.is_file():raise ValueError('Missing cached source archive: '+str(archive))
        packages.append(archive)
    project=project.replace('packages: . ../../upstream/pandoc ../../upstream/pandoc-types','packages: '+' '.join(map(str,packages)))
    project+='\nwith-compiler: '+str(toolchain/'ghc/bin/ghc')+'\ntests: True\n'
    (directory/'cabal.project').write_text(project)
    (directory/'cabal.project.freeze').write_bytes((ROOT/'tools/haskell-html-reader-reference/cabal.project.freeze').read_bytes())
    return directory


def compile_observer(directory,toolchain):
    plan=json.loads((directory/'build/cache/plan.json').read_text())
    units=plan['install-plan']
    test=next(x for x in units if x.get('pkg-name')=='pandoc' and x.get('component-name')=='test:test-pandoc')
    aeson=next(x['id'] for x in units if x.get('pkg-name')=='aeson' and x.get('component-name')=='lib')
    dependencies=list(dict.fromkeys(test['depends']+[aeson]))
    work=directory/'observer';work.mkdir(exist_ok=True)
    original=(ROOT/'upstream/pandoc/test/test-pandoc.hs').read_bytes()
    before=b'module Main (main) where';after=b'module InventoryOriginalMain (main, tests) where'
    if original.count(before)!=1:raise ValueError('Original main module packaging changed')
    patched=original.replace(before,after)
    (work/'InventoryOriginalMain.hs').write_bytes(patched)
    # This reversible sole replacement preserves every registration/test body.
    if patched.replace(after,before)!=original:raise ValueError('Unexpected observer source edit')
    databases=list((directory/'build/packagedb').glob('ghc-*'))
    stores=list((toolchain/'cabal-state/store').glob('ghc-9.12.2-*'))
    if len(databases)!=1 or len(stores)!=1:raise ValueError('Ambiguous compiler package databases')
    command=[str(toolchain/'ghc/bin/ghc'),'--make','-O0','-XHaskell2010','-threaded','-rtsopts',
             '-clear-package-db','-global-package-db','-package-db',str(stores[0]/'package.db'),'-package-db',str(databases[0]),
             '-hide-all-packages','-i'+str(ROOT/'upstream/pandoc/test'),'-i'+str(work),
             '-outputdir',str(work/'objects'),'-o',str(work/'runtime-inventory')]
    for dependency in dependencies:command+=['-package-id',dependency]
    command+=[str(ROOT/'tools/test-inventory-runtime-reference/RuntimeInventory.hs')]
    (work/'compile-command.json').write_text(json.dumps(command,indent=2)+'\n')
    subprocess.run(command,check=True,cwd=ROOT)
    return work/'runtime-inventory'


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,required=True)
    parser.add_argument('--toolchain',type=Path,default=Path('/tmp/kotdoc-haskell'))
    parser.add_argument('--compile-observer',action='store_true')
    args=parser.parse_args()
    if args.compile_observer:print(compile_observer(args.directory,args.toolchain))
    else:
        prepare(args.directory,args.toolchain)
        print('Prepared unchanged source build; build pandoc:test:test-pandoc with CABAL_DIR='+str(args.toolchain/'cabal-state'))
