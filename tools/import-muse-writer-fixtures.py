#!/usr/bin/env python3
"""Finite source/oracle verification for the complete pinned Muse writer."""
import argparse,base64,gzip,hashlib,importlib.util,json,pathlib,subprocess,sys
sys.dont_write_bytecode=True
ROOT=pathlib.Path(__file__).resolve().parents[1];TOOLS=ROOT/'tools/haskell-muse-writer-reference'
spec=importlib.util.spec_from_file_location('muse_writer_recipes',TOOLS/'requests.py');recipes=importlib.util.module_from_spec(spec);spec.loader.exec_module(recipes)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return [json.loads(l)for l in gzip.decompress(p.read_bytes()).splitlines()]
def encoded(rows):return ''.join(json.dumps(r,ensure_ascii=False)+'\n'for r in rows).encode()
def call(binary,args=(),input=None):return subprocess.run([str(binary),*map(str,args)],input=input,capture_output=True,check=True,cwd=ROOT).stdout
def sources():
 paths=[ROOT/'upstream/pandoc/src/Text/Pandoc'/p for p in ['Writers/Muse.hs','Writers/Shared.hs','Writers/Math.hs','Shared.hs','ImageSize.hs','URI.hs','Options.hs','Extensions.hs','Logging.hs','Error.hs','Class/IO.hs','App.hs','App/OutputSettings.hs','App/Input.hs']]
 paths += [ROOT/'upstream/pandoc/test'/p for p in ['Tests/Writers/Muse.hs','Tests/Helpers.hs','Tests/Old.hs','Tests/Command.hs','command/4172.md','testsuite.native','tables.native','writer.muse','tables.muse']]
 paths += [ROOT/'upstream/pandoc-types/src/Text/Pandoc'/p for p in ['Definition.hs','Builder.hs','Walk.hs']]
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc'/p for p in ['writers/MuseWriter.kt','writers/WriterMetadata.kt','writers/GridTable.kt','writers/AnnotatedTable.kt','ast/DocumentShared.kt','ast/TableSimplicity.kt','ast/Tables.kt','ast/Builder.kt','ast/PandocVisitor.kt','ast/UnicodeText.kt','ast/Asciify.kt','ast/Emoji.kt','readers/DokuWikiIdentifiers.kt','images/Dimensions.kt','uri/PandocURI.kt','parsing/ParserCharacters.kt','options/WriterOptions.kt','state/PandocState.kt','logging/Logging.kt','formats/HaskellDouble.kt','formats/NativeWriter.kt','formats/HaskellNumber.kt']]
 paths += [p for d in ['layout','templates','math']for p in (ROOT/'core/src/main/kotlin/io/heapy/kotdoc'/d).glob('*.kt')]
 paths += [ROOT/'core/src/main/resources/io/heapy/kotdoc'/p for p in ['parser-char-flags.json','dokuwiki-identifier-flags.tsv','case-mappings.json','emojis.json','pandoc-data/templates/default.muse']]
 paths += [ROOT/'core/src/test/kotlin/io/heapy/kotdoc/writers/MuseWriterTest.kt']
 paths += [ROOT/'cli/src/main/kotlin/io/heapy/kotdoc/cli'/p for p in ['Main.kt','HtmlCliOptions.kt','MuseCliWriter.kt']]
 paths += [ROOT/'core/src/test/resources/dokuwiki-reader'/p for p in ['cli-options.json','cli-warnings.json']]
 paths += [ROOT/'tools/haskell-pandoc-uri-reference/SourceUriPosix.hs',ROOT/'core/src/test/resources/dokuwiki-writer/observations.jsonl.gz',ROOT/'tools/check-muse-writer-cli.py',pathlib.Path(__file__)]
 paths += [p for p in TOOLS.iterdir()if p.is_file()and p.suffix in ['.py','.hs','.md','.json']]
 assert all(p.is_file()for p in paths),[str(p)for p in paths if not p.is_file()]
 return sorted(set(paths))
def copied():
 source=(TOOLS/'prepare.py').read_text().replace("(HERE/'SourceMuseWriter.hs').write_text(text)","assert (HERE/'SourceMuseWriter.hs').read_text()==text").replace("(HERE/'OriginalTests.hs').write_text(text)","assert (HERE/'OriginalTests.hs').read_text()==text")
 exec(source,{'__file__':str(TOOLS/'prepare.py')})
 assert(ROOT/'tools/haskell-pandoc-uri-reference/SourceUriPosix.hs').read_text()==(ROOT/'upstream/pandoc/src/Text/Pandoc/URI.hs').read_text().replace('module Text.Pandoc.URI (','module SourceUriPosix (')
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--original-cli');p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/muse-writer');modes=p.add_mutually_exclusive_group(required=True)
 for mode in ['refresh-manifest','check-source','verify-oracle']:modes.add_argument('--'+mode,action='store_true')
 a=p.parse_args();out=a.fixture_dir;copied();payloads=sorted(p for p in out.rglob('*')if p.is_file()and p.name!='manifest.json')
 if a.refresh_manifest:
  assert a.oracle and a.original_cli
  m=dict(pandocCommit='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e',typesCommit='b2cbcefc4547296f47b8120740997a9c021d756f',writerLines=732,original=164,observations=2962,extra=219,deep=9,goldens=2,largeWidths=4,captionDepth=2,captionShapes=15,captionDemand=1,classifierPoints=1114112,cli=192,templateBoundaries=12,commands=2,oracleSha256=sha(pathlib.Path(a.oracle)),originalCliSha256=sha(pathlib.Path(a.original_cli)),sourcePins={str(p.relative_to(ROOT)):sha(p)for p in sources()},payloadPins={str(p.relative_to(out)):sha(p)for p in payloads})
  (out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n');print('Recorded complete writer source/helper/tool and independent expected-byte pins');return
 m=json.loads((out/'manifest.json').read_text());assert m['pandocCommit']=='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e'and m['typesCommit']=='b2cbcefc4547296f47b8120740997a9c021d756f'
 assert m['sourcePins']=={str(p.relative_to(ROOT)):sha(p)for p in sources()};assert m['payloadPins']=={str(p.relative_to(out)):sha(p)for p in payloads}
 if a.original_cli:assert sha(pathlib.Path(a.original_cli))==m['originalCliSha256']
 original=read(out/'original164.jsonl.gz');assert len(original)==164
 seeds=read(ROOT/'core/src/test/resources/dokuwiki-writer/observations.jsonl.gz');documents=[r['document']for r in seeds];ranges=[tuple(map(int,l.split()))for l in(out/'unicode17-ranges.tsv').read_text().splitlines()];template=(out/'default.muse').read_text()
 for name,count,requests in [('observations',2962,recipes.requests(original,documents)),('extra',219,recipes.extra_requests(ranges,template)),('deep',9,recipes.deep_requests()),('goldens',2,recipes.golden_requests({r['name']:r['document']for r in seeds},template)),('large-widths',4,recipes.large_width_requests()),('caption-depth',2,recipes.caption_depth_requests()),('caption-shapes',15,recipes.caption_shape_requests(documents)),('caption-demand',1,recipes.caption_demand_requests())]:
  saved=read(out/(name+'.jsonl.gz'));assert len(saved)==count;assert[r['request']for r in saved]==requests,name
 for p in(out/'original').iterdir():
  source=ROOT/'upstream/pandoc/test'/({'Muse.hs':'Tests/Writers/Muse.hs','Old.hs':'Tests/Old.hs','4172.md':'command/4172.md','Helpers.hs':'Tests/Helpers.hs'}.get(p.name,p.name));assert p.read_bytes()==source.read_bytes()
 assert len(read(out/'commands.jsonl.gz'))==2 and len(read(out/'cli.jsonl.gz'))==192 and len(read(out/'cli-template-boundaries.jsonl.gz'))==12
 original_by_name={r['name']:r for r in original}
 for r in read(out/'cli.jsonl.gz'):
  if r['name'].startswith('original/'):
   assert r['exitCode']==0 and base64.b64decode(r['stderr'])==b''
   assert base64.b64decode(r['stdout'])==original_by_name[r['name'][9:]]['output'].encode()
  if r['name'].startswith('old/'):assert base64.b64decode(r['stdout'])==(out/'original'/r['name'][4:]).read_bytes()
 assert len(gzip.decompress((out/'classifiers.txt.gz').read_bytes()))==1114112
 assert(out/'default.muse').read_bytes()==(ROOT/'core/src/main/resources/io/heapy/kotdoc/pandoc-data/templates/default.muse').read_bytes()
 for r in read(out/'goldens.jsonl.gz'):assert r['expected']['output'].encode()==(out/'original'/r['request']['name'][4:]).read_bytes()
 print('Read-only exact copied modules/URI helper, source/recipe/payload/Old/command bytes passed')
 if a.verify_oracle:
  assert a.oracle and sha(pathlib.Path(a.oracle))==m['oracleSha256']
  assert [json.loads(l)for l in call(a.oracle,['--original-tests']).splitlines()]==original
  assert json.loads(call(a.oracle,['--defaults']))==json.loads((out/'defaults.json').read_text())
  for name in ['observations','extra','deep','goldens','large-widths','caption-depth','caption-shapes','caption-demand']:
   saved=read(out/(name+'.jsonl.gz'));actual=[json.loads(l)for l in call(a.oracle,input=encoded([r['request']for r in saved])).splitlines()];assert actual==[r['expected']for r in saved],name
  assert call(a.oracle,['--classifiers'])==gzip.decompress((out/'classifiers.txt.gz').read_bytes())
  commands=[json.loads(l)for l in call(a.oracle,['--commands','upstream/pandoc/test/command/4172.md']).splitlines()];assert commands==read(out/'commands.jsonl.gz')
  print('Original164 +2962 branches +219 extra +9 deep +2 Old +4 large percentage widths +18 caption branch/depth/demand probes +all1114112 five-predicate points +2 command extractions passed')
if __name__=='__main__':main()
