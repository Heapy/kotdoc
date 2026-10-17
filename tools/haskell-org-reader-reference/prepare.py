#!/usr/bin/env python3
"""Exact, finite source transformations; check mode never writes any file."""
import argparse,difflib,pathlib,sys
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--prepare',action='store_true');a=p.parse_args()
if a.check_source==a.prepare:p.error('choose exactly one mode')
pairs=[]
source=ROOT/'upstream/pandoc/src/Text/Pandoc/Readers/Org.hs'
pairs.append((source,HERE/'SourceOrgReader.hs','reader'))
for source in sorted((ROOT/'upstream/pandoc/src/Text/Pandoc/Readers/Org').glob('*.hs')):pairs.append((source,HERE/'SourceOrgReader'/source.name,'reader'))
source=ROOT/'upstream/pandoc/test/Tests/Readers/Org.hs';pairs.append((source,HERE/'OriginalOrgTests.hs','test'))
for source in sorted((ROOT/'upstream/pandoc/test/Tests/Readers/Org').rglob('*.hs')):pairs.append((source,HERE/'OriginalOrgTests'/source.relative_to(ROOT/'upstream/pandoc/test/Tests/Readers/Org'),'test'))
failed=False
for source,destination,kind in pairs:
 text=source.read_text()
 if kind=='reader':
  text=text.replace('Text.Pandoc.Readers.Org','SourceOrgReader').replace('Text.Pandoc.Class.PandocMonad','Text.Pandoc.Class')
  if source.name=='Meta.hs':text=text.replace('  ( metaExport','  ( collectLines\n  , collectAsList\n  , metaExport')
 else:
  text=text.replace('Tests.Readers.Org','OriginalOrgTests').replace('Test.Tasty (','OracleTree (').replace('Tests.Helpers','OracleHelpers')
  text=text.replace('getDefaultExtensions, readOrg)','getDefaultExtensions)\nimport SourceOrgReader (readOrg)')
  if source.name=='Directive.hs':text=text.replace('import Text.Pandoc\n','import Text.Pandoc hiding (readOrg)\nimport SourceOrgReader (readOrg)\n').replace('purely, test)','purely, test, testFiles)').replace('testWithFiles fileDefs = test (orgWithFiles fileDefs)','testWithFiles fileDefs = testFiles fileDefs (orgWithFiles fileDefs)')
  if source.name=='List.hs':text=text.replace('purely, test)','purely, test, testProfile)').replace('test orgFancyLists','testProfile "fancy_lists" orgFancyLists')
  if source.name=='Smart.hs':text=text.replace('purely, test)','purely, test, testProfile)').replace('test orgSmart','testProfile "smart" orgSmart')
 if a.prepare:destination.parent.mkdir(parents=True,exist_ok=True);destination.write_text(text)
 elif not destination.is_file()or destination.read_bytes()!=text.encode():
  failed=True;print('SOURCE TRANSFORM MISMATCH',destination.relative_to(ROOT));print(''.join(difflib.unified_diff(text.splitlines(True),destination.read_text().splitlines(True)if destination.exists()else[])))
if failed:raise SystemExit(1)
print(len(pairs),'exact original source/test module transforms verified'if a.check_source else'modules prepared; expected fixtures untouched')
