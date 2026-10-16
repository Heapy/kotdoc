#!/usr/bin/env python3
"""Complete pinned Muse reader observations; expectations never come from Kotlin."""
import argparse,gzip,hashlib,importlib.util,json,pathlib,subprocess,sys
sys.dont_write_bytecode=True
ROOT=pathlib.Path(__file__).resolve().parents[1];TOOLS=ROOT/'tools/haskell-muse-reader-reference'
spec=importlib.util.spec_from_file_location('muse_recipes',TOOLS/'requests.py');recipes=importlib.util.module_from_spec(spec);spec.loader.exec_module(recipes)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def encoded(rows):return ''.join(json.dumps(r,ensure_ascii=False,separators=(',',':'))+'\n' for r in rows).encode()
def call(binary,args=(),input=None):return subprocess.run([str(binary),*map(str,args)],input=input,capture_output=True,check=True,cwd=ROOT)
def read(path):return [json.loads(l) for l in gzip.decompress(path.read_bytes()).splitlines()]
def put(path,data):
 assert not path.exists(),f'Refusing to overwrite expected payload: {path}'
 if path.suffix=='.gz':
  with gzip.GzipFile(filename=str(path),mode='wb',mtime=0) as f:f.write(data)
 else:path.write_bytes(data)
def observe(binary,requests):
 result=[json.loads(l) for l in call(binary,input=encoded(requests)).stdout.splitlines()];assert len(result)==len(requests)
 return [dict(request=q,expected=e) for q,e in zip(requests,result)]
def source_paths():
 names=['Readers/Muse.hs','Parsing.hs','Parsing/General.hs','Parsing/Future.hs','Parsing/Capabilities.hs','Parsing/State.hs','Sources.hs','Shared.hs','Options.hs','Extensions.hs','Logging.hs','Class/PandocMonad.hs','Class/PandocIO.hs','Class/IO.hs','App.hs','App/Input.hs','App/OutputSettings.hs']
 paths=[ROOT/'upstream/pandoc/src/Text/Pandoc'/n for n in names]
 paths += [ROOT/'upstream/pandoc/test'/n for n in ['Tests/Readers/Muse.hs','Tests/Helpers.hs','Tests/Command.hs','Tests/Old.hs','command/4172.md','command/5107.md']]
 paths += [ROOT/'upstream/pandoc-types/src/Text/Pandoc'/n for n in ['Definition.hs','Builder.hs','Walk.hs']]
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc'/n for n in ['parsing/Parsec.kt','parsing/ParserMachine.kt','parsing/ParserCharacters.kt','parsing/Sources.kt','parsing/Deferred.kt','parsing/Future.kt','parsing/ReaderParsec.kt','parsing/ListParsing.kt','readers/DokuWikiIdentifiers.kt','ast/Builder.kt','ast/TableCompaction.kt','ast/PandocVisitor.kt','ast/UnicodeText.kt','ast/Emoji.kt','ast/Asciify.kt','options/ReaderOptions.kt']]
 paths += [ROOT/'core/src/main/resources/io/heapy/kotdoc'/n for n in ['parser-char-flags.json','dokuwiki-identifier-flags.tsv','case-mappings.json','emojis.json']]
 paths += [p for p in TOOLS.iterdir() if p.is_file() and p.suffix in ['.py','.hs']]
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc/readers/MuseReader.kt',ROOT/'core/src/test/kotlin/io/heapy/kotdoc/readers/MuseReaderTest.kt',ROOT/'cli/src/main/kotlin/io/heapy/kotdoc/cli/Main.kt',ROOT/'cli/src/main/kotlin/io/heapy/kotdoc/cli/MuseCliReader.kt',pathlib.Path(__file__), ROOT/'tools/check-muse-cli.py', TOOLS/'README.md', TOOLS/'SOURCE-MATRIX.md']
 assert all(p.is_file() for p in paths),[str(p) for p in paths if not p.is_file()]
 return sorted(paths)
def copied_sources():
 ns={'__file__':str(TOOLS/'prepare.py')};exec((TOOLS/'prepare.py').read_text().replace("(HERE/'SourceMuseReader.hs').write_text(source)","assert (HERE/'SourceMuseReader.hs').read_text()==source").replace("(HERE/'OriginalTests.hs').write_text(source)","assert (HERE/'OriginalTests.hs').read_text()==source"),ns)
def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--original-cli');p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/muse-reader')
 modes=p.add_mutually_exclusive_group(required=True)
 for mode in ['generate','refresh-manifest','check-source','verify-oracle']:modes.add_argument('--'+mode,action='store_true')
 a=p.parse_args();out=a.fixture_dir;copied_sources()
 if a.generate:
  assert a.oracle;out.mkdir(parents=True,exist_ok=True)
  original=[json.loads(l) for l in call(a.oracle,['--original-tests']).stdout.splitlines()];assert len(original)==250
  defaults=json.loads(call(a.oracle,['--defaults']).stdout)
  ranges=(ROOT/'core/src/test/resources/dokuwiki-reader/unicode17-ranges.tsv').read_bytes();put(out/'unicode17-ranges.tsv',ranges);intervals=[tuple(map(int,l.split())) for l in ranges.decode().splitlines()]
  put(out/'original250.jsonl.gz',encoded(original));put(out/'defaults.json',(json.dumps(defaults)+'\n').encode())
  for name,requests in [('observations',recipes.requests(original)),('extra',recipes.extra_requests(intervals,defaults)),('deep',recipes.deep_requests()+recipes.stress_requests())]:put(out/(name+'.jsonl.gz'),encoded(observe(a.oracle,requests)))
  traces=[]
  for q in recipes.trace_requests():
   r=call(a.oracle,input=encoded([q]));traces.append(dict(request=q,expected=json.loads(r.stdout),trace=r.stderr.decode().splitlines()))
  put(out/'letter-flags.txt.gz',call(a.oracle,['--letters']).stdout);put(out/'trace.jsonl.gz',encoded(traces));put(out/'letter-alias.json',call(a.oracle,['--letter-alias']).stdout)
  commands=[json.loads(l) for l in call(a.oracle,['--commands',*[ROOT/'upstream/pandoc/test/command'/n for n in ['4172.md','5107.md']]]).stdout.splitlines()]
  for c in commands:c['file']=str(pathlib.Path(c['file']).relative_to(ROOT))
  put(out/'commands.jsonl.gz',encoded(commands));(out/'original').mkdir(exist_ok=True)
  for src in ['Tests/Readers/Muse.hs','command/4172.md','command/5107.md']:put(out/'original'/pathlib.Path(src).name,(ROOT/'upstream/pandoc/test'/src).read_bytes())
 payloads=sorted(p for p in out.rglob('*') if p.is_file() and p.name!='manifest.json')
 if a.generate or a.refresh_manifest:
  assert a.oracle and a.original_cli
  m=dict(originalCliSha256=sha(pathlib.Path(a.original_cli)),pandocCommit='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e',typesCommit='b2cbcefc4547296f47b8120740997a9c021d756f',readerLines=1012,original=250,observations=2464,extra=277,deep=11,trace=4,classifierAliasPoints=1114112,oracleSha256=sha(pathlib.Path(a.oracle)),sourcePins={str(p.relative_to(ROOT)):sha(p) for p in source_paths()},payloadPins={str(p.relative_to(out)):sha(p) for p in payloads})
  (out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n');print('Recorded source/helper/tool and independent payload pins');return
 m=json.loads((out/'manifest.json').read_text());assert m['pandocCommit']=='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e';assert m['typesCommit']=='b2cbcefc4547296f47b8120740997a9c021d756f'
 if a.original_cli:assert sha(pathlib.Path(a.original_cli))==m['originalCliSha256']
 assert m['sourcePins']=={str(p.relative_to(ROOT)):sha(p) for p in source_paths()};assert m['payloadPins']=={str(p.relative_to(out)):sha(p) for p in payloads}
 original=read(out/'original250.jsonl.gz');defaults=json.loads((out/'defaults.json').read_text());ranges=[tuple(map(int,l.split())) for l in (out/'unicode17-ranges.tsv').read_text().splitlines()]
 assert len(original)==250
 for name,count,requests in [('observations',2464,recipes.requests(original)),('extra',277,recipes.extra_requests(ranges,defaults)),('deep',11,recipes.deep_requests()+recipes.stress_requests()),('trace',4,recipes.trace_requests())]:
  saved=read(out/(name+'.jsonl.gz'));assert len(saved)==count;assert [r['request'] for r in saved]==requests
 assert json.loads((out/'letter-alias.json').read_text())==dict(points=1114112,mismatches=0)
 letters=gzip.decompress((out/'letter-flags.txt.gz').read_bytes());assert len(letters)==1114112 and set(letters)=={48,49}
 for src in ['Tests/Readers/Muse.hs','command/4172.md','command/5107.md']:assert (out/'original'/pathlib.Path(src).name).read_bytes()==(ROOT/'upstream/pandoc/test'/src).read_bytes()
 assert len(read(out/'commands.jsonl.gz'))==4
 assert len(read(out/'cli-library.jsonl.gz'))==250
 for name,count in [('cli-input',35),('cli-warnings',22),('cli-options',16)]:assert len(json.loads((out/(name+'.json')).read_text()))==count
 print('Read-only exact source transformations/helper/tool/payload/request/original-byte checks passed')
 if a.verify_oracle:
  assert a.oracle and sha(pathlib.Path(a.oracle))==m['oracleSha256']
  assert [json.loads(l) for l in call(a.oracle,['--original-tests']).stdout.splitlines()]==original
  assert json.loads(call(a.oracle,['--defaults']).stdout)==defaults
  for name in ['observations','extra','deep']:
   saved=read(out/(name+'.jsonl.gz'));assert observe(a.oracle,[r['request'] for r in saved])==saved,name
  for r in read(out/'trace.jsonl.gz'):
   actual=call(a.oracle,input=encoded([r['request']]));assert json.loads(actual.stdout)==r['expected'];assert actual.stderr.decode().splitlines()==r['trace']
  assert call(a.oracle,['--letter-alias']).stdout==(out/'letter-alias.json').read_bytes();assert call(a.oracle,['--letters']).stdout==letters
  commands=[json.loads(l) for l in call(a.oracle,['--commands',*[ROOT/'upstream/pandoc/test/command'/n for n in ['4172.md','5107.md']]]).stdout.splitlines()]
  for c in commands:c['file']=str(pathlib.Path(c['file']).relative_to(ROOT))
  assert commands==read(out/'commands.jsonl.gz')
  print('Read-only original250 +2464 branches +277 extra +11 deep +4 trace +all1114112 character-alias points +4 command extractions replay passed')
if __name__=='__main__':main()
