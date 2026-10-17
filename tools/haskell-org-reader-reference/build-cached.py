#!/usr/bin/env python3
"""Reuse an already built pinned full Pandoc library; never download or rebuild it."""
import argparse, pathlib, re, subprocess
HERE=pathlib.Path(__file__).resolve().parent
ROOT=HERE.parents[1]
p=argparse.ArgumentParser()
p.add_argument('--ghc',default='/tmp/kotdoc-haskell/ghc/bin/ghc')
p.add_argument('--store-db',default='/tmp/kotdoc-haskell/cabal-state/store/ghc-9.12.2-ea3d/package.db')
p.add_argument('--pandoc-db',default='/tmp/kotdoc-test-inventory-runtime/build/packagedb/ghc-9.12.2')
p.add_argument('--main',default='ReaderReference.hs')
p.add_argument('--output',default='/tmp/kotdoc-org-reader/oracle')
a=p.parse_args()
# prepare.py is invoked explicitly before a fresh immutable capture.
packages={}
global_db=subprocess.check_output([a.ghc,"--print-global-package-db"],text=True).strip()
for db in [global_db,a.store_db,a.pandoc_db]:
    for path in pathlib.Path(db).glob('*.conf'):
        text=path.read_text(); name=re.search(r'^name:\s*(\S+)',text,re.M); ident=re.search(r'^id:\s*(\S+)',text,re.M)
        if name and ident: packages[name[1]]=ident[1]
assert packages["text"]=="text-2.1.2-b3a1" and packages["pandoc"]=="pandoc-3.12.1-inplace" and packages["gridtables"]=="grdtbls-0.1.1.0-8ff59a97"
# A private package registration exposes unchanged original hidden module types.
# Unit id, ABI, import/library directories and compiled artifacts remain untouched.
original_conf=next(pathlib.Path(a.pandoc_db).glob('pandoc-3.12.1-*.conf'))
private_db=pathlib.Path('/tmp/kotdoc-org-reader/package-db');private_db.mkdir(parents=True,exist_ok=True)
registration=original_conf.read_text()
hidden=re.search(r'^hidden-modules:\s*(.*?)(?=^\S|\Z)',registration,re.M|re.S)
if hidden:
    hidden_names=hidden[1].strip();registration=registration[:hidden.start()]+registration[hidden.end():]
    exposed=re.search(r'^exposed-modules:\s*(.*?)(?=^\S|\Z)',registration,re.M|re.S)
    registration=registration[:exposed.end()]+'    '+hidden_names+'\n'+registration[exposed.end():]
(private_db/original_conf.name).write_text(registration)
subprocess.run([str(pathlib.Path(a.ghc).with_name('ghc-pkg')),'--package-db',str(private_db),'recache'],check=True)
output=pathlib.Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
cmd=[a.ghc,'--make','-O0','-rtsopts','-clear-package-db','-global-package-db','-package-db',a.store_db,'-package-db',a.pandoc_db,'-package-db','/tmp/kotdoc-org-reader/package-db','-hide-all-packages','-i'+str(HERE),'-i'+str(ROOT/'tools/haskell-pandoc-uri-reference'),'-i'+str(ROOT/'upstream/pandoc/test'),'-outputdir',str(output.parent/'objects'),'-o',str(output)]
for name in ['base','bytestring','text','aeson','containers','mtl','transformers','data-default','doctemplates','doclayout','pandoc','pandoc-types','safe','parsec','Diff','Glob','directory','filepath','process','tasty','tasty-golden','tasty-hunit','tasty-quickcheck','QuickCheck','temporary','time','xml','zip-archive','network-uri','http-types','base64-bytestring','attoparsec','gridtables','array','texmath']:
    cmd+=['-package-id',packages[name]]
subprocess.run(cmd+[str(HERE/a.main)],check=True,cwd=ROOT)
