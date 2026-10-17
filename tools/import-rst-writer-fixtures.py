#!/usr/bin/env python3
"""Original-only RST fixture capture and finite read-only source/oracle verification."""
import argparse,base64,gzip,hashlib,importlib.util,json,pathlib,shlex,subprocess,os,sys
sys.dont_write_bytecode=True
ROOT=pathlib.Path(__file__).resolve().parents[1];TOOLS=ROOT/'tools/haskell-rst-writer-reference'
spec=importlib.util.spec_from_file_location('rst_recipes',TOOLS/'requests.py');recipes=importlib.util.module_from_spec(spec);spec.loader.exec_module(recipes)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def lines(b):return[json.loads(l)for l in b.splitlines()]
def encoded(xs):return ''.join(json.dumps(x,ensure_ascii=False)+'\n'for x in xs).encode()
def call(binary,args=(),input=None):return subprocess.check_output([str(binary),*map(str,args)],input=input,cwd=ROOT)
def write_rows(path,xs):path.write_bytes(gzip.compress(encoded(xs),mtime=0))
def read_rows(path):return lines(gzip.decompress(path.read_bytes()))
def sources():
 paths=[ROOT/'upstream/pandoc/src/Text/Pandoc'/p for p in ['Writers/RST.hs','Writers/Shared.hs','Shared.hs','ImageSize.hs','URI.hs','Options.hs','Extensions.hs','Logging.hs','Error.hs','Class/IO.hs','App.hs','App/OutputSettings.hs','App/Input.hs','App/CommandLineOptions.hs']]
 paths += [ROOT/'upstream/pandoc/test'/p for p in ['Tests/Writers/RST.hs','Tests/Helpers.hs','Tests/Old.hs','Tests/Command.hs','testsuite.native','tables.native','lhs-test.native','writer.rst','tables.rst','lhs-test.rst','lhs-test.rst+lhs']]
 paths += [ROOT/'upstream/pandoc-types/src/Text/Pandoc'/p for p in ['Definition.hs','Builder.hs','Walk.hs']]
 paths += list((ROOT/'upstream/pandoc/test/command').glob('*.md'))
 paths += [ROOT/'upstream/pandoc/pandoc.cabal']
 paths += [ROOT/'vendor/text-2.1.2/src/Data/Text/Internal/Fusion/CaseMapping.hs',ROOT/'vendor/text-2.1.2/src/Data/Text/Internal/Transformation.hs',ROOT/'vendor/text-2.1.2/text.cabal',ROOT/'tools/import-text-212-case.py']
 paths += [ROOT/'upstream/pandoc/data/templates/default.rst',ROOT/'upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs']
 paths += [ROOT/'tools/haskell-pandoc-uri-reference/SourceUriPosix.hs',ROOT/'core/src/test/resources/dokuwiki-writer/observations.jsonl.gz',pathlib.Path(__file__)]
 paths += [p for p in TOOLS.iterdir()if p.is_file()and p.suffix in ['.hs','.py','.md','.json','.diff']]
 for d in ['layout','templates']:paths+=list((ROOT/'core/src/main/kotlin/io/heapy/kotdoc'/d).glob('*.kt'))
 paths += [ROOT/'core/src/main/kotlin/io/heapy/kotdoc'/p for p in ['writers/RstWriter.kt','writers/WriterShared.kt','writers/WriterMetadata.kt','writers/GridTable.kt','writers/AnnotatedTable.kt','ast/DocumentShared.kt','ast/Builder.kt','ast/PandocVisitor.kt','ast/UnicodeText.kt','ast/Asciify.kt','ast/Emoji.kt','ast/AstComparison.kt','latex/LaTeXReaderText.kt','parsing/ParserCharacters.kt','parsing/RegexCharacters.kt','images/Dimensions.kt','uri/PandocURI.kt','formats/HaskellNumber.kt','formats/HaskellDouble.kt','formats/NativeWriter.kt','logging/Logging.kt','logging/LogMessage.kt','logging/Verbosity.kt','ast/Pandoc.kt','ast/Format.kt','options/WriterOptions.kt']]
 paths += [ROOT/'core/src/main/resources/io/heapy/kotdoc'/p for p in ['rst-writer-flags.tsv','text-2.1.2-case.json','parser-char-flags.json','regex-char-data.json','case-mappings.json','emojis.json','pandoc-data/templates/default.rst']]
 paths += [ROOT/'core/src/test/kotlin/io/heapy/kotdoc/writers/RstWriterTest.kt']
 paths += [ROOT/'cli/src/main/kotlin/io/heapy/kotdoc/cli'/p for p in ['Main.kt','HtmlCliOptions.kt','RstCliWriter.kt']]
 paths += [ROOT/'tools/check-rst-writer-cli.py',ROOT/'tools/check-muse-writer-cli.py']
 paths += [ROOT/'core/src/test/resources/muse-writer'/p for p in ['cli.jsonl.gz','cli-template-boundaries.jsonl.gz','commands.jsonl.gz']]
 paths += [ROOT/'core/src/test/resources/dokuwiki-reader'/p for p in ['cli-options.json','cli-warnings.json']]
 assert all(p.is_file()for p in paths),[str(p)for p in paths if not p.is_file()]
 return sorted(set(paths))
def copied():
 source=(TOOLS/'prepare.py').read_text().replace("(HERE/'SourceRstWriter.hs').write_text(text)","assert (HERE/'SourceRstWriter.hs').read_text()==text").replace("(HERE/'OriginalTests.hs').write_text(text)","assert (HERE/'OriginalTests.hs').read_text()==text")
 exec(source,{'__file__':str(TOOLS/'prepare.py')})
 assert(ROOT/'tools/haskell-pandoc-uri-reference/SourceUriPosix.hs').read_text()==(ROOT/'upstream/pandoc/src/Text/Pandoc/URI.hs').read_text().replace('module Text.Pandoc.URI (','module SourceUriPosix (')
def command_rows(oracle):
 # Read all files: the canonical Markdown block parser decides which are code blocks.
 raw=lines(call(oracle,['--commands',*sorted((ROOT/'upstream/pandoc/test/command').glob('*.md'))]));out=[]
 for r in raw:
  args=shlex.split(r['command']);writer=None;reader='markdown'
  for i,arg in enumerate(args):
   if arg in ['-t','-w','--to','--write']and i+1<len(args):writer=args[i+1]
   elif arg.startswith('--to=')or arg.startswith('--write='):writer=arg.split('=',1)[1]
   elif arg.startswith(('-t','-w'))and len(arg)>2:writer=arg[2:]
   if arg in ['-f','-r','--from','--read']and i+1<len(args):reader=args[i+1]
   elif arg.startswith('--from=')or arg.startswith('--read='):reader=arg.split('=',1)[1]
   elif arg.startswith(('-f','-r'))and len(arg)>2:reader=arg[2:]
  if writer and(writer=='rst'or writer.startswith('rst+')or writer.startswith('rst-')):r['file']=str(pathlib.Path(r['file']).relative_to(ROOT));r['reader']=reader;out.append(r)
 return out
def original_app(cli,args,input=b''):
 env=dict(os.environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data'));return subprocess.run([str(cli),'--emulate',*args],input=input,capture_output=True,cwd=ROOT/'upstream/pandoc/test',env=env)
def capture(out,oracle,cli):
 assert not out.exists()or not any(out.iterdir()),'Refuse to overwrite independently captured expectations';out.mkdir(parents=True,exist_ok=True)
 original=lines(call(oracle,['--original-tests']));assert len(original)==22;write_rows(out/'original.jsonl.gz',original)
 seeds=[r['document']for r in read_rows(ROOT/'core/src/test/resources/dokuwiki-writer/observations.jsonl.gz')]
 corpora={'display-math-stop':recipes.display_math_requests(),'identifier-profiles':recipes.identifier_profile_requests(json.loads((ROOT/'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-case.json').read_text()),json.loads((ROOT/'core/src/main/resources/io/heapy/kotdoc/case-mappings.json').read_text())),'casefolds':recipes.casefold_requests(json.loads((ROOT/'core/src/main/resources/io/heapy/kotdoc/case-mappings.json').read_text())),'observations':recipes.requests(original,seeds),'extra':recipes.extra_requests(),'boundaries':recipes.boundary_requests(),'captions':recipes.caption_requests(seeds),'deep':[dict(name=f'deep/{k}/{d}',deep=dict(kind=k,depth=d),options=recipes.BASE)for k,d in [('emph',4096),('span',4096),('note',4096),('div',4096),('quote',4096),('bullet',512),('ordered',512),('definition',256),('table',128),('mixed',128),('caption',4096),('grid-caption',4096)]],'widths':[dict(name=k,deep=dict(kind=k,depth=0),options=recipes.BASE|dict(listTables=True))for k in ['list-width-nan','list-width-inf','list-width-neg-inf','list-width-huge']]}
 commands=command_rows(oracle);assert len(commands)==43;write_rows(out/'commands.jsonl.gz',commands);command_requests=[]
 for r in commands:
  args=shlex.split(r['command'])[1:];result=original_app(cli,args,r['input'].encode());actual=(''.join('2> '+l+'\n'for l in result.stderr.decode().splitlines())+result.stdout.decode()).replace('\r','')+(f'=> {result.returncode}\n'if result.returncode else'');assert actual==r['expected'],(r['file'],r['number'],actual)
  ast=original_app(cli,['-f',r['reader'],'-t','json'],r['input'].encode());assert ast.returncode==0;opts=recipes.BASE|dict(listTables='--list-tables'in args)
  for a in args:
   if a.startswith('--columns='):opts['columns']=int(a.split('=')[1])
   if a=='--wrap=none':opts['wrap']='WrapNone'
   if a.startswith('rst+')and '+smart'in a:opts['extensions']=['smart']
  command_requests.append(dict(name='command/'+pathlib.Path(r['file']).name+'#'+str(r['number']),document=json.loads(ast.stdout),options=opts))
 corpora['command-writer']=command_requests
 golden=[]
 for source,target,opts in [('testsuite.native','writer.rst',{}),('tables.native','tables.rst',{}),('lhs-test.native','lhs-test.rst',dict(wrap='WrapPreserve')),('lhs-test.native','lhs-test.rst+lhs',dict(wrap='WrapPreserve',extensions=['literate_haskell']))]:
  ast=original_app(cli,['-f','native','-t','json'],(ROOT/'upstream/pandoc/test'/source).read_bytes());assert ast.returncode==0;q=dict(name='old/'+target,document=json.loads(ast.stdout),options=recipes.BASE|dict(columns=80,variables={'pandoc-version':''})|opts)
  if target=='writer.rst':q['template']=(ROOT/'upstream/pandoc/data/templates/default.rst').read_text()
  golden.append(q)
 corpora['goldens']=golden
 for name,requests in corpora.items():
  expected=lines(call(oracle,input=encoded(requests)));assert len(expected)==len(requests)
  if name=='goldens':
   for q,e in zip(requests,expected):assert e['output'].encode()==(ROOT/'upstream/pandoc/test'/q['name'][4:]).read_bytes()
  if name=='command-writer':
   for c,e in zip(commands,expected):assert(e['output']if e['output'].endswith('\n')else e['output']+'\n')==c['expected']
  write_rows(out/(name+'.jsonl.gz'),[dict(request=q,expected=e)for q,e in zip(requests,expected)])
 (out/'lowercase-mappings.json').write_bytes(call(oracle,['--lowercase-mappings']))
 classifiers=call(oracle,['--classifiers']);assert len(classifiers)==1114112;(out/'classifiers.txt.gz').write_bytes(gzip.compress(classifiers,mtime=0));ranges=[];prior=None
 for i,b in enumerate(classifiers):
  if b!=prior:ranges.append((i,b-32));prior=b
 (out/'classifier-ranges.tsv').write_text(''.join(f'{i}\t{v}\n'for i,v in ranges));(out/'default.rst').write_bytes((ROOT/'upstream/pandoc/data/templates/default.rst').read_bytes())
 original_dir=out/'original';original_dir.mkdir()
 for name in ['Tests/Writers/RST.hs','Tests/Old.hs','Tests/Helpers.hs','Tests/Command.hs','testsuite.native','tables.native','lhs-test.native','writer.rst','tables.rst','lhs-test.rst','lhs-test.rst+lhs']:(original_dir/pathlib.Path(name).name).write_bytes((ROOT/'upstream/pandoc/test'/name).read_bytes())
 print('Captured original22 +2215 +318 +83 +15 +12 +4 +43writer commands +4Old; fullclassifier domain;43originalAppcommands',flush=True)
def text_profile(out):
 import re
 source=(ROOT/'vendor/text-2.1.2/src/Data/Text/Internal/Fusion/CaseMapping.hs').read_text();section=source.split('lowerMapping = \\case\n',1)[1].split('  _ -> unI64 0',1)[0];expected={}
 for cp,packed in re.findall(r"'\\x([0-9a-f]+)'# -> unI64 ([0-9]+)",section):
  value=int(packed);points=[]
  while value:points.append(chr(value&((1<<21)-1)));value>>=21
  expected[int(cp,16)]=''.join(points)
 assert len(expected)==1433;assert dict(json.loads((out/'lowercase-mappings.json').read_text()))==expected
 data=json.loads((ROOT/'core/src/main/resources/io/heapy/kotdoc/text-2.1.2-case.json').read_text());assert {int(k,16):v for k,v in data['lower'].items()}==expected
 profile=json.loads((TOOLS/'runtime-profile.json').read_text())
 for path,pin in profile['packageDatabasePins'].items():
  q=pathlib.Path(path)
  if q.exists():assert sha(q)==pin
 return expected

def main():
 p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--original-cli');p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/rst-writer');g=p.add_mutually_exclusive_group(required=True)
 for mode in ['capture','refresh-manifest','check-source','verify-oracle']:g.add_argument('--'+mode,action='store_true')
 a=p.parse_args();out=a.fixture_dir;copied()
 if a.capture:capture(out,a.oracle,a.original_cli);return
 payloads=sorted(p for p in out.rglob('*')if p.is_file()and p.name!='manifest.json')
 if a.refresh_manifest:
  m=dict(pandocCommit='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e',typesCommit='b2cbcefc4547296f47b8120740997a9c021d756f',writerLines=997,originalLeaves=22,writerLeaves=19,flattenLeaves=3,expanded=2215,extra=318,boundaries=83,captions=15,casefolds=1,identifierProfiles=3,displayMathStop=3,cli=93,cliDisplayMath=3,textLowercasePoints=1114112,textLowerMappings=1433,deep=12,widths=4,goldens=4,commandWriter=43,commandsAvailableReaders=21,commandsMissingReaders=22,classifierPoints=1114112,junit=2725,oracleSha256=sha(pathlib.Path(a.oracle)),originalCliSha256=sha(pathlib.Path(a.original_cli)),sourcePins={str(p.relative_to(ROOT)):sha(p)for p in sources()},payloadPins={str(p.relative_to(out)):sha(p)for p in payloads});(out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n');print('Recorded source/helper/tool/independent expected-byte pins');return
 m=json.loads((out/'manifest.json').read_text());text_profile(out);assert m['sourcePins']=={str(p.relative_to(ROOT)):sha(p)for p in sources()};assert m['payloadPins']=={str(p.relative_to(out)):sha(p)for p in payloads};assert(out/'classifier-ranges.tsv').read_bytes()==(ROOT/'core/src/main/resources/io/heapy/kotdoc/rst-writer-flags.tsv').read_bytes()
 print('Read-only source/helper/tool/expected-byte/classifier pins passed',flush=True)
 if a.verify_oracle:
  assert sha(pathlib.Path(a.oracle))==m['oracleSha256'];assert lines(call(a.oracle,['--original-tests']))==read_rows(out/'original.jsonl.gz');assert call(a.oracle,['--classifiers'])==gzip.decompress((out/'classifiers.txt.gz').read_bytes());assert command_rows(a.oracle)==read_rows(out/'commands.jsonl.gz');assert json.loads(call(a.oracle,['--lowercase-mappings']))==json.loads((out/'lowercase-mappings.json').read_text())
  total=22
  for name in ['observations','extra','boundaries','captions','casefolds','identifier-profiles','display-math-stop','deep','widths','goldens','command-writer']:
   rows=read_rows(out/(name+'.jsonl.gz'));actual=lines(call(a.oracle,input=encoded(r['request']for r in rows)));assert actual==[r['expected']for r in rows],name;total+=len(rows)
  print(total,'original observations and full1114112classifierpoints +43commandextractions passed',flush=True)
if __name__=='__main__':main()
