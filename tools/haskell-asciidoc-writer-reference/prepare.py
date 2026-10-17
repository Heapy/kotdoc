#!/usr/bin/env python3
"""Finite read-only provenance/oracle verification. Never regenerates expectations."""
import argparse,base64,gzip,hashlib,json,pathlib,re,subprocess
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];FIX=HERE/'fixtures';TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer');MANIFEST=HERE/'provenance.json'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def check_copies():
 source=(ROOT/'upstream/pandoc/src/Text/Pandoc/Writers/AsciiDoc.hs').read_text();assert len(source.splitlines())==912
 expected=re.sub(r'module Text.Pandoc.Writers.AsciiDoc \(.*?\) where','module SourceAsciiDocWriter where',source,count=1,flags=re.S).replace('import Text.Pandoc.Class.PandocMonad (PandocMonad, report)','import Text.Pandoc.Class (PandocMonad, report)').replace('import Text.Pandoc.URI','import SourceUriPosix')
 assert(HERE/'SourceAsciiDocWriter.hs').read_text()==expected,'Writer transform drift'
 source=(ROOT/'upstream/pandoc/test/Tests/Writers/AsciiDoc.hs').read_text().replace('module Tests.Writers.AsciiDoc (tests) where','module OriginalAsciiDocTests (tests) where').replace('import Test.Tasty\n','import AsciiDocOracleTree\n').replace('import Tests.Helpers','import AsciiDocOracleHelpers').replace('import Text.Pandoc\n','import Text.Pandoc hiding(writeAsciiDoc,writeAsciiDocLegacy)\nimport SourceAsciiDocWriter\n')
 assert(HERE/'OriginalAsciiDocTests.hs').read_text()==source,'Original test transform drift'
def rows(name):return[json.loads(x)for x in gzip.decompress((FIX/(name+'.jsonl.gz')).read_bytes()).splitlines()]
def check_source():
 m=json.loads(MANIFEST.read_text());check_copies()
 for name,pin in m['files'].items():assert sha(ROOT/name)==pin,name
 for name,pin in m['runtime'].items():assert sha(pathlib.Path(name))==pin,name
 for name,count in m['counts'].items():assert len(rows(name))==count,name
 print('PASS exact912line writer/test transforms, all source/tool/helper/runtime/payload pins and counts',flush=True)
def observe(binary,queries):
 proc=subprocess.run([str(binary)],input=''.join(json.dumps(q,ensure_ascii=False)+'\n'for q in queries).encode(),capture_output=True,check=True)
 return[json.loads(x)for x in proc.stdout.splitlines()]
def verify():
 check_source();total=0
 for names,binary in [(['goldens','branches','command-ast','header-int64','extra','nonfinite-width'],'oracle'),(['table-helpers'],'helper-oracle'),(['scalars'],'scalar-oracle'),(['identifiers'],'identifier-oracle'),(['deep'],'deep-oracle'),(['sections','sections-second'],'sections-oracle'),(['chomp'],'chomp-oracle'),(['chomp-metrics'],'chomp-metrics-oracle')]:
  for name in names:
   rs=rows(name)
   # Bound observation memory and retain source order; deep outputs can be large.
   for start in range(0,len(rs),64):
    batch=rs[start:start+64];actual=observe(TMP/binary,[x['request']for x in batch]);assert len(actual)==len(batch)
    for r,a in zip(batch,actual):assert a==r['expected'],r['request']['name']
   total+=len(rs);print('PASS',name,len(rs),flush=True)
 original=[json.loads(x)for x in subprocess.check_output([str(TMP/'oracle'),'--original-tests']).splitlines()];assert original==rows('original12');total+=len(original)
 for binary,name in [('demand-oracle','demand-native'),('chomp-demand-oracle','chomp-demand-native-only')]:
  actual=[json.loads(x)for x in subprocess.check_output([str(TMP/binary)]).splitlines()];assert actual==rows(name);total+=len(actual)
 for mode,name in [('--classifiers','classifiers.bin.gz'),('--lower-mappings','lower-mappings.json'),('--upper-mappings','upper-mappings.json'),('--casefold-mappings','casefold-mappings.json')]:
  actual=subprocess.check_output([str(TMP/'oracle'),mode]);expected=(FIX/name).read_bytes()
  if name.endswith('.gz'):assert actual==gzip.decompress(expected)
  else:assert json.loads(actual)==json.loads(expected)
 print('PASS',total,'immutable original module/test/helper observations and four exhaustive domain captures',flush=True)
def capture_manifest():
 assert not MANIFEST.exists(),'Refuse provenance replacement';check_copies()
 sources=['upstream/pandoc/src/Text/Pandoc/Writers/AsciiDoc.hs','upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/src/Text/Pandoc/Parsing.hs','vendor/doclayout-0.6/src/Text/DocLayout.hs','upstream/pandoc-types/src/Text/Pandoc/Walk.hs','upstream/pandoc/test/Tests/Writers/AsciiDoc.hs','upstream/pandoc/test/Tests/Old.hs','upstream/pandoc/test/Tests/Helpers.hs','upstream/pandoc/test/Tests/Command.hs','upstream/pandoc/src/Text/Pandoc/App.hs','upstream/pandoc/src/Text/Pandoc/App/OutputSettings.hs','upstream/pandoc-types/src/Text/Pandoc/Definition.hs','vendor/text-2.1.2/src/Data/Text/Internal/Fusion/CaseMapping.hs','vendor/text-2.1.2/src/Data/Text/Internal/Transformation.hs','tools/haskell-pandoc-uri-reference/SourceUriPosix.hs','tools/extract-test-inventory.py','tools/check-asciidoc-writer-cli.py']
 sources += [str(p.relative_to(ROOT))for p in HERE.rglob('*')if p.is_file()and p.name!='provenance.json' and '__pycache__' not in p.parts]
 sources += [str(p.relative_to(ROOT))for p in FIX.rglob('*')if p.is_file()]
 sources += ['upstream/pandoc/src/Text/Pandoc/Readers/Native.hs','upstream/pandoc/src/Text/Pandoc/Error.hs','upstream/pandoc/src/Text/Pandoc/Parsing/General.hs','upstream/pandoc/src/Text/Pandoc/Parsing/Lists.hs','upstream/pandoc/src/Text/Pandoc/ImageSize.hs']
 sources += [str(p.relative_to(ROOT))for p in (ROOT/'vendor/filepath-1.5.4.0').rglob('*.hs')]
 sources += ['upstream/pandoc/test/'+name for name in ['testsuite.native','tables.native','writer.asciidoc','writer.asciidoc_legacy','tables.asciidoc','tables.asciidoc_legacy']]
 sources += ['upstream/pandoc/data/templates/default.asciidoc']
 sources += ['upstream/pandoc/src/Text/Pandoc/'+name for name in ['Templates.hs','Data.hs','App/Input.hs','Class/IO.hs','Logging.hs','Options.hs','Readers.hs']]
 sources += [str(p.relative_to(ROOT)) for p in (ROOT/'core/src/main/kotlin/io/heapy/kotdoc/writers').glob('AsciiDoc*.kt')]
 sources += [str(p.relative_to(ROOT)) for p in (ROOT/'core/src/test/kotlin/io/heapy/kotdoc/writers').glob('AsciiDoc*.kt')]
 sources += ['cli/src/main/kotlin/io/heapy/kotdoc/cli/'+name for name in ['Main.kt','HtmlCliOptions.kt','AsciiDocCliWriter.kt']]
 sources += ['core/src/main/kotlin/io/heapy/kotdoc/'+name for name in ['writers/OrgCitationLocator.kt','writers/WriterMetadata.kt','writers/WriterTables.kt','uri/PandocURI.kt','uri/NetworkURI.kt','parsing/ParserCharacters.kt','parsing/RegexCharacters.kt','readers/DokuWikiIdentifiers.kt','layout/DocLayout.kt','latex/LaTeXReaderText.kt','images/Dimensions.kt','formats/HaskellNumber.kt']]
 sources += sorted(set(str(p.relative_to(ROOT)) for p in (ROOT/'upstream/pandoc/test/command').glob('*.md') if str(p.relative_to(ROOT)) in (FIX/'commands-inventory.json').read_text()))
 runtime=[*[TMP/name for name in ['oracle','helper-oracle','scalar-oracle','identifier-oracle','deep-oracle','sections-oracle','chomp-oracle','chomp-metrics-oracle','demand-oracle','chomp-demand-oracle']],pathlib.Path('/tmp/kotdoc-test-inventory-runtime/build/cache/plan.json')]
 runtime += list(pathlib.Path('/tmp/kotdoc-test-inventory-runtime/build/packagedb/ghc-9.12.2').glob('pandoc-*.conf'))
 runtime += list(pathlib.Path('/tmp/kotdoc-haskell/cabal-state/store/ghc-9.12.2-ea3d/package.db').glob('text-2.1.2-*.conf'))
 runtime += [pathlib.Path('/tmp/kotdoc-haskell/ghc/bin/ghc'),pathlib.Path('/tmp/kotdoc-haskell/ghc/lib/ghc-9.12.2/lib/package.conf.d/text-2.1.2-b3a1.conf')]
 # Exact cached compiled-library artifacts linked by the original module oracle.
 for conf in list(runtime):
  if conf.suffix=='.conf':
   text=conf.read_text();match=re.search(r'^library-dirs:\s*(.*?)\n[a-z-]+:',text,re.M|re.S)
   if match:
    for directory in match[1].split():runtime += list(pathlib.Path(directory.replace('${pkgroot}',str(conf.parent.parent))).glob('libHS*.a'))
 runtime += [pathlib.Path('/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc')]
 counts={p.name.removesuffix('.jsonl.gz'):len(gzip.decompress(p.read_bytes()).splitlines())for p in FIX.glob('*.jsonl.gz')}
 m=dict(pandoc='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e',pandocTypes='b2cbcefc4547296f47b8120740997a9c021d756f',text='text-2.1.2-b3a1',ghc='9.12.2',counts=counts,files={name:sha(ROOT/name)for name in sorted(set(sources))},runtime={str(p):sha(p)for p in runtime});MANIFEST.write_text(json.dumps(m,indent=2)+'\n');print('Captured provenance once; no expected values generated')
def main():
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');p.add_argument('--capture-manifest',action='store_true');a=p.parse_args()
 if a.capture_manifest:capture_manifest()
 elif a.verify_oracle:verify()
 elif a.check_source:check_source()
 else:p.error('choose a mode')
if __name__=='__main__':main()
