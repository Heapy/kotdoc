#!/usr/bin/env python3
"""Finite read-only provenance/oracle verification. Never regenerates expectations."""
import argparse,base64,gzip,hashlib,json,pathlib,re,subprocess
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];FIX=HERE/'fixtures';TMP=pathlib.Path('/tmp/kotdoc-org-writer');MANIFEST=HERE/'provenance.json'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def check_copies():
 source=(ROOT/'upstream/pandoc/src/Text/Pandoc/Writers/Org.hs').read_text();assert len(source.splitlines())==735
 expected=source.replace('module Text.Pandoc.Writers.Org (writeOrg) where','module SourceOrgWriter where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad, report)','import Text.Pandoc.Class (PandocMonad, report)').replace('import Text.Pandoc.URI','import SourceUriPosix').replace('import Text.Pandoc.Citeproc.Locator','import SourceOrgLocator')
 assert(HERE/'SourceOrgWriter.hs').read_text()==expected,'Writer transform drift'
 source=(ROOT/'upstream/pandoc/src/Text/Pandoc/Citeproc/Locator.hs').read_text();assert len(source.splitlines())==334
 source=re.sub(r'module Text.Pandoc.Citeproc.Locator\n.*?\nwhere','module SourceOrgLocator where',source,flags=re.S).replace('import Citeproc.Types\n','').replace('import Text.Pandoc.Citeproc.Util','import SourceOrgLocatorUtil').split('\ntoLocatorMap ::')[0]
 assert(HERE/'SourceOrgLocator.hs').read_text()==source,'Used locator closure drift'
 source=(ROOT/'upstream/pandoc/src/Text/Pandoc/Citeproc/Util.hs').read_text();body=source[source.index('splitStrWhen ::'):source.index('\ntoIETF ::')].rstrip()
 assert(HERE/'SourceOrgLocatorUtil.hs').read_text().rstrip().endswith(body),'splitStrWhen drift'
 source=(ROOT/'upstream/pandoc/test/Tests/Writers/Org.hs').read_text().replace('module Tests.Writers.Org (tests) where','module OriginalOrgTests (tests) where').replace('import Test.Tasty\n','import OrgOracleTree\n').replace('import Tests.Helpers','import OrgOracleHelpers').replace('import Text.Pandoc\n','import Text.Pandoc hiding(writeOrg)\nimport SourceOrgWriter\n')
 assert(HERE/'OriginalOrgTests.hs').read_text()==source,'Original test transform drift'
def rows(name):return[json.loads(x)for x in gzip.decompress((FIX/(name+'.jsonl.gz')).read_bytes()).splitlines()]
def check_source():
 m=json.loads(MANIFEST.read_text());check_copies()
 for name,pin in m['files'].items():assert sha(ROOT/name)==pin,name
 for name,pin in m['runtime'].items():assert sha(pathlib.Path(name))==pin,name
 for name,count in m['counts'].items():assert len(rows(name))==count,name
 print('PASS exact735line writer/used334line locator transforms, all source/tool/helper/runtime/payload pins and counts',flush=True)
def observe(binary,queries):
 proc=subprocess.run([str(binary)],input=''.join(json.dumps(q,ensure_ascii=False)+'\n'for q in queries).encode(),capture_output=True,check=True)
 return[json.loads(x)for x in proc.stdout.splitlines()]
def verify():
 check_source();total=0
 for names,binary in[(['writer-observations','tables-observations','goldens-observations','extra-observations','deep-observations','header-int64-observations','tag-width-observations'],'oracle'),(['locator-observations'],'locator-oracle'),(['helper-observations'],'helper-oracle')]:
  for name in names:
   rs=rows(name);actual=observe(TMP/binary,[x['request']for x in rs]);assert len(actual)==len(rs)
   for r,a in zip(rs,actual):assert a==r['expected'],r['request']['name']
   total+=len(rs)
 original=[json.loads(x)for x in subprocess.check_output([str(TMP/'oracle'),'--original-tests']).splitlines()];assert original==rows('original8');total+=len(original)
 for mode,name in[('--classifiers','classifiers.bin.gz'),('--casefold-mappings','casefold-mappings.json')]:
  actual=subprocess.check_output([str(TMP/'oracle'),mode]);expected=(FIX/name).read_bytes()
  if name.endswith('.gz'):assert actual==gzip.decompress(expected)
  else:assert json.loads(actual)==json.loads(expected)
 print('PASS',total,'immutable original module/test/helper observations plus both exhaustive domain captures',flush=True)
def capture_manifest():
 assert not MANIFEST.exists(),'Refuse provenance replacement';check_copies()
 sources=['upstream/pandoc/src/Text/Pandoc/Writers/Org.hs','upstream/pandoc/src/Text/Pandoc/Citeproc/Locator.hs','upstream/pandoc/src/Text/Pandoc/Citeproc/Util.hs','upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/src/Text/Pandoc/Parsing.hs','vendor/doclayout-0.6/src/Text/DocLayout.hs','upstream/pandoc-types/src/Text/Pandoc/Walk.hs','upstream/pandoc/test/Tests/Writers/Org.hs','upstream/pandoc/test/Tests/Old.hs','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/test/Tests/Command.hs','upstream/pandoc/src/Text/Pandoc/App.hs','upstream/pandoc/src/Text/Pandoc/App/OutputSettings.hs','upstream/pandoc-types/src/Text/Pandoc/Definition.hs','vendor/text-2.1.2/src/Data/Text/Internal/Fusion/CaseMapping.hs','vendor/text-2.1.2/src/Data/Text/Internal/Transformation.hs','tools/haskell-pandoc-uri-reference/SourceUriPosix.hs','tools/extract-test-inventory.py','tools/check-org-writer-cli.py']
 sources += [str(p.relative_to(ROOT))for p in HERE.iterdir()if p.is_file()and p.name!='provenance.json']
 sources += [str(p.relative_to(ROOT))for p in FIX.rglob('*')if p.is_file()]
 runtime=[TMP/'oracle',TMP/'locator-oracle',TMP/'helper-oracle',pathlib.Path('/tmp/kotdoc-test-inventory-runtime/build/cache/plan.json')]
 runtime += list(pathlib.Path('/tmp/kotdoc-test-inventory-runtime/build/packagedb/ghc-9.12.2').glob('pandoc-*.conf'))
 runtime += list(pathlib.Path('/tmp/kotdoc-haskell/cabal-state/store/ghc-9.12.2-ea3d/package.db').glob('text-2.1.2-*.conf'))
 runtime += [pathlib.Path('/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc')]
 counts={p.name.removesuffix('.jsonl.gz'):len(gzip.decompress(p.read_bytes()).splitlines())for p in FIX.glob('*.jsonl.gz')if p.name!='cli-observations.jsonl.gz'}
 m=dict(pandoc='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e',pandocTypes='b2cbcefc4547296f47b8120740997a9c021d756f',text='text-2.1.2-b3a1',ghc='9.12.2',counts=counts,files={name:sha(ROOT/name)for name in sorted(set(sources))},runtime={str(p):sha(p)for p in runtime});MANIFEST.write_text(json.dumps(m,indent=2)+'\n');print('Captured provenance once; no expected values generated')
def main():
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');p.add_argument('--capture-manifest',action='store_true');a=p.parse_args()
 if a.capture_manifest:capture_manifest()
 elif a.verify_oracle:verify()
 elif a.check_source:check_source()
 else:p.error('choose a mode')
if __name__=='__main__':main()
