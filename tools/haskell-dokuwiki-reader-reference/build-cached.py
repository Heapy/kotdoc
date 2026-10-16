#!/usr/bin/env python3
"""Reuse an already built pinned full Pandoc library; never download or rebuild it."""
import argparse, pathlib, re, subprocess
HERE=pathlib.Path(__file__).resolve().parent
ROOT=HERE.parents[1]
p=argparse.ArgumentParser()
p.add_argument('--ghc',default='/tmp/kotdoc-haskell/ghc/bin/ghc')
p.add_argument('--store-db',default='/tmp/kotdoc-haskell/cabal-state/store/ghc-9.12.2-ea3d/package.db')
p.add_argument('--pandoc-db',default='/tmp/kotdoc-test-inventory-runtime/build/packagedb/ghc-9.12.2')
p.add_argument('--output',default='/tmp/kotdoc-dokuwiki-reader/oracle')
a=p.parse_args()
subprocess.run(['python3',str(HERE/'prepare.py')],check=True)
packages={}
global_db=subprocess.check_output([a.ghc,"--print-global-package-db"],text=True).strip()
for db in [global_db,a.store_db,a.pandoc_db]:
    for path in pathlib.Path(db).glob('*.conf'):
        text=path.read_text(); name=re.search(r'^name:\s*(\S+)',text,re.M); ident=re.search(r'^id:\s*(\S+)',text,re.M)
        if name and ident: packages[name[1]]=ident[1]
output=pathlib.Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
cmd=[a.ghc,'--make','-O0','-rtsopts','-clear-package-db','-global-package-db','-package-db',a.store_db,'-package-db',a.pandoc_db,'-hide-all-packages','-i'+str(HERE),'-i'+str(ROOT/'upstream/pandoc/test'),'-outputdir',str(output.parent/'objects'),'-o',str(output)]
for name in ['base','bytestring','text','aeson','containers','mtl','transformers','data-default','doctemplates','doclayout','pandoc','pandoc-types','safe','parsec','Diff','Glob','directory','filepath','process','tasty','tasty-golden','tasty-hunit','tasty-quickcheck','QuickCheck','temporary','time','xml','zip-archive']:
    cmd+=['-package-id',packages[name]]
subprocess.run(cmd+[str(HERE/'ReaderReference.hs')],check=True,cwd=ROOT)

subprocess.run([a.ghc,'--make','-O2','-outputdir',str(output.parent/'identifier-objects'),'-o',str(output.parent/'identifier-flags'),str(HERE/'IdentifierFlags.hs')],check=True,cwd=ROOT)
