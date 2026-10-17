#!/usr/bin/env python3
"""Build observations against cached pinned Pandoc/text-2.1.2; no downloads."""
import pathlib,re,subprocess,json
HERE=pathlib.Path(__file__).resolve().parent
OUT=pathlib.Path('/tmp/kotdoc-identifier-profile');OUT.mkdir(exist_ok=True)
ghc='/tmp/kotdoc-haskell/ghc/bin/ghc'
dbs=[subprocess.check_output([ghc,'--print-global-package-db'],text=True).strip(),'/tmp/kotdoc-haskell/cabal-state/store/ghc-9.12.2-ea3d/package.db','/tmp/kotdoc-test-inventory-runtime/build/packagedb/ghc-9.12.2'];pk={}
for db in dbs:
 for p in pathlib.Path(db).glob('*.conf'):
  t=p.read_text();n=re.search(r'^name:\s*(\S+)',t,re.M);i=re.search(r'^id:\s*(\S+)',t,re.M)
  if n and i:pk[n[1]]=i[1]
assert pk['text']=='text-2.1.2-b3a1' and pk['pandoc']=='pandoc-3.12.1-inplace'
cmd=[ghc,'--make','-O0','-rtsopts','-clear-package-db','-global-package-db','-package-db',dbs[1],'-package-db',dbs[2],'-hide-all-packages','-outputdir',str(OUT/'objects'),'-o',str(OUT/'oracle')]
for n in ['base','bytestring','text','aeson','containers','pandoc','pandoc-types']:cmd+=['-package-id',pk[n]]
(OUT/'build-command.json').write_text(json.dumps(cmd+[str(HERE/'Reference.hs')],indent=2)+'\n')
subprocess.run(cmd+[str(HERE/'Reference.hs')],check=True)
