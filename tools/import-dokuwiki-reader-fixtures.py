#!/usr/bin/env python3
"""Independent complete original DokuWiki reader/test observations and read-only checks."""
import argparse,base64,gzip,hashlib,importlib.util,json,pathlib,subprocess,sys,tempfile
sys.dont_write_bytecode=True
ROOT=pathlib.Path(__file__).resolve().parents[1]
FOLDER=ROOT/'tools/haskell-dokuwiki-reader-reference'
spec=importlib.util.spec_from_file_location('dokuwiki_reader_recipes',FOLDER/'requests.py');recipes=importlib.util.module_from_spec(spec);spec.loader.exec_module(recipes)
COMMANDS=['11739.md','8178.md','5416.md','dokuwiki-quote.md','9630.md','9632.md','3824.md','8182.md','5107.md']
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(v):return json.dumps(v,ensure_ascii=False,separators=(',',':'))
def lines(rs):return ''.join(dump(r)+'\n' for r in rs).encode()
def load(path):return [json.loads(l) for l in gzip.decompress(path.read_bytes()).decode().splitlines()]
def save(path,data):
    assert not path.exists(),f'Refusing to overwrite accepted payload: {path}'
    with gzip.GzipFile(filename=str(path),mode='wb',mtime=0) as f:f.write(data)
def call(binary,args=(),input=None):return subprocess.run([str(binary),*map(str,args)],input=input,capture_output=True,check=True,cwd=ROOT)
def observe(oracle,requests):
    result=call(oracle,input=lines(requests));actual=[json.loads(l) for l in result.stdout.splitlines()];assert len(actual)==len(requests)
    return [dict(request=q,expected=e) for q,e in zip(requests,actual)]
def source_paths():
    result=[ROOT/'upstream/pandoc'/p for p in ['src/Text/Pandoc/Readers/DokuWiki.hs','test/Tests/Readers/DokuWiki.hs','test/Tests/Helpers.hs','test/Tests/Command.hs','src/Text/Pandoc/App.hs','src/Text/Pandoc/App/Input.hs','src/Text/Pandoc/App/OutputSettings.hs','src/Text/Pandoc/Parsing.hs','src/Text/Pandoc/Parsing/General.hs','src/Text/Pandoc/Parsing/Smart.hs','src/Text/Pandoc/Parsing/Math.hs','src/Text/Pandoc/Parsing/State.hs','src/Text/Pandoc/Shared.hs','src/Text/Pandoc/URI.hs','src/Text/Pandoc/XML.hs','src/Text/Pandoc/Options.hs','src/Text/Pandoc/Extensions.hs','src/Text/Pandoc/Class/PandocMonad.hs','src/Text/Pandoc/Class/IO.hs','src/Text/Pandoc/Class/PandocIO.hs','src/Text/Pandoc/Logging.hs','src/Text/Pandoc/Sources.hs']]
    result += [ROOT/'upstream/pandoc-types'/p for p in ['src/Text/Pandoc/Definition.hs','src/Text/Pandoc/Builder.hs','src/Text/Pandoc/Walk.hs']]
    result += [ROOT/'upstream/pandoc/test/command'/p for p in COMMANDS]
    result += [p for p in FOLDER.iterdir() if p.is_file() and p.suffix in ['.py','.hs','.kt']]
    result += [pathlib.Path(__file__), ROOT/'tools/check-dokuwiki-cli.py']
    return sorted(result)
def copied_sources():
    original=(ROOT/'upstream/pandoc/src/Text/Pandoc/Readers/DokuWiki.hs').read_text()
    expected=original.replace('module Text.Pandoc.Readers.DokuWiki (readDokuWiki) where','module SourceDokuWikiReader (readDokuWiki) where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad (..))','import Text.Pandoc.Class (PandocMonad (..))')
    assert (FOLDER/'SourceDokuWikiReader.hs').read_text()==expected
    source=(ROOT/'upstream/pandoc/test/Tests/Readers/DokuWiki.hs').read_text()
    expected=source.replace('module Tests.Readers.DokuWiki (tests) where','module OriginalTests (tests) where').replace('import Test.Tasty\n','import OracleTree\n').replace('import Tests.Helpers\n','import OracleHelpers\n')
    assert (FOLDER/'OriginalTests.hs').read_text()==expected

def main():
    p=argparse.ArgumentParser();p.add_argument('--oracle');p.add_argument('--identifier-oracle');p.add_argument('--original-cli');p.add_argument('--unicode-ranges',type=pathlib.Path)
    p.add_argument('--fixture-dir',type=pathlib.Path,default=ROOT/'core/src/test/resources/dokuwiki-reader')
    mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--generate',action='store_true');mode.add_argument('--refresh-manifest',action='store_true');mode.add_argument('--check-source',action='store_true');mode.add_argument('--verify-oracle',action='store_true')
    a=p.parse_args();out=a.fixture_dir;copied_sources()
    if a.generate:
        assert a.oracle and a.identifier_oracle and a.unicode_ranges and a.original_cli
        out.mkdir(parents=True,exist_ok=True)
        original=[json.loads(l) for l in call(a.oracle,['--original-tests']).stdout.splitlines()];assert len(original)==115
        defaults=json.loads(call(a.oracle,['--defaults']).stdout)
        save(out/'original115.jsonl.gz',lines(original));(out/'defaults.json').write_text(dump(defaults)+'\n')
        rs=recipes.requests(original,defaults);assert len(rs)==1340
        save(out/'observations.jsonl.gz',lines(observe(a.oracle,rs)))
        save(out/'deep.jsonl.gz',lines(observe(a.oracle,recipes.deep_requests())))
        trace=[]
        for q in recipes.trace_requests():
            result=call(a.oracle,input=lines([q]));trace.append(dict(request=q,expected=json.loads(result.stdout),trace=result.stderr.decode().splitlines()))
        save(out/'trace.jsonl.gz',lines(trace))
        (out/'unicode17-ranges.tsv').write_bytes(a.unicode_ranges.read_bytes())
        ranges=[tuple(map(int,l.split())) for l in a.unicode_ranges.read_text().splitlines()]
        save(out/'unicode17.jsonl.gz',lines(observe(a.oracle,recipes.unicode_requests(ranges))))
        expanded=call(a.identifier_oracle,['--expanded']).stdout;assert len(expanded)==0x110000
        save(out/'identifier-flags-expanded.txt.gz',expanded)
        (out/'identifier-ranges.tsv').write_bytes(call(a.identifier_oracle).stdout)
        commands=[json.loads(l) for l in call(a.oracle,['--commands',*[ROOT/'upstream/pandoc/test/command'/name for name in COMMANDS]]).stdout.splitlines()]
        for r in commands:r['file']=str(pathlib.Path(r['file']).relative_to(ROOT))
        save(out/'commands.jsonl.gz',lines(commands))
        subprocess.run([sys.executable,str(FOLDER/'capture-cli-options.py'),'--original-cli',a.original_cli,'--output',str(out/'cli-options.json')],check=True)
        subprocess.run([sys.executable,str(FOLDER/'capture-cli-input.py'),'--original-cli',a.original_cli,'--output',str(out/'cli-input.json')],check=True)
        subprocess.run([sys.executable,str(FOLDER/'capture-cli-warnings.py'),'--original-cli',a.original_cli,'--output',str(out/'cli-warnings.json')],check=True)
        original_dir=out/'original';original_dir.mkdir(exist_ok=True)
        for name in COMMANDS:(original_dir/name).write_bytes((ROOT/'upstream/pandoc/test/command'/name).read_bytes())
        (original_dir/'DokuWiki.hs').write_bytes((ROOT/'upstream/pandoc/test/Tests/Readers/DokuWiki.hs').read_bytes())
    payloads=sorted(p for p in out.rglob('*') if p.is_file() and p.name!='manifest.json')
    if a.generate or a.refresh_manifest:
        assert a.oracle and a.identifier_oracle
        manifest=dict(pandocCommit='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e',typesCommit='b2cbcefc4547296f47b8120740997a9c021d756f',readerLines=573,original115=115,observations=1340,deep=7,trace=5,unicode=12,classifierPoints=0x110000,unicode17Ranges=47,unicode17Points=4803,
            sourcePins={str(p.relative_to(ROOT)):digest(p) for p in source_paths()},payloadPins={str(p.relative_to(out)):digest(p) for p in payloads},oracleSha256=digest(pathlib.Path(a.oracle)),identifierOracleSha256=digest(pathlib.Path(a.identifier_oracle)))
        if a.original_cli:manifest['originalCliSha256']=digest(pathlib.Path(a.original_cli))
        elif (out/'manifest.json').exists():manifest['originalCliSha256']=json.loads((out/'manifest.json').read_text())['originalCliSha256']
        (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print('Recorded source and immutable independent payload pins');return
    m=json.loads((out/'manifest.json').read_text());assert m['pandocCommit']=='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e';assert m['typesCommit']=='b2cbcefc4547296f47b8120740997a9c021d756f'
    assert m['sourcePins']=={str(p.relative_to(ROOT)):digest(p) for p in source_paths()},'Source/tool/helper pin mismatch'
    assert m['payloadPins']=={str(p.relative_to(out)):digest(p) for p in payloads},'Payload pin mismatch'
    for name,count in [('original115',115),('observations',1340),('deep',7),('trace',5),('unicode17',12)]:assert len(load(out/(name+'.jsonl.gz')))==count
    original=load(out/'original115.jsonl.gz');defaults=json.loads((out/'defaults.json').read_text());assert [r['request'] for r in load(out/'observations.jsonl.gz')]==recipes.requests(original,defaults)
    assert [r['request'] for r in load(out/'deep.jsonl.gz')]==recipes.deep_requests()
    assert [r['request'] for r in load(out/'trace.jsonl.gz')]==recipes.trace_requests()
    ranges=[tuple(map(int,l.split())) for l in (out/'unicode17-ranges.tsv').read_text().splitlines()];assert len(ranges)==47 and sum(b-a+1 for a,b in ranges)==4803
    expanded=gzip.decompress((out/'identifier-flags-expanded.txt.gz').read_bytes());assert len(expanded)==0x110000
    rows=[tuple(map(int,l.split())) for l in (out/'identifier-ranges.tsv').read_text().splitlines()]
    actual=bytearray(0x110000)
    for (start,flag),(end,_) in zip(rows,rows[1:]+[(0x110000,0)]):actual[start:end]=bytes([65+flag])*(end-start)
    assert actual==expanded
    assert all(expanded[cp]==65 for start,end in ranges for cp in range(start,end+1))
    assert [r['request'] for r in load(out/'unicode17.jsonl.gz')]==recipes.unicode_requests(ranges)
    for name in COMMANDS:assert (out/'original'/name).read_bytes()==(ROOT/'upstream/pandoc/test/command'/name).read_bytes()
    assert (out/'original/DokuWiki.hs').read_bytes()==(ROOT/'upstream/pandoc/test/Tests/Readers/DokuWiki.hs').read_bytes()
    production=ROOT/'core/src/main/resources/io/heapy/kotdoc/dokuwiki-identifier-flags.tsv'
    if production.exists():assert production.read_bytes()==(out/'identifier-ranges.tsv').read_bytes()
    print('Read-only source/helper/tool/payload/requests/full Unicode domain/original-byte checks passed')
    if a.verify_oracle:
        assert a.oracle and a.identifier_oracle
        assert digest(pathlib.Path(a.oracle))==m['oracleSha256'];assert digest(pathlib.Path(a.identifier_oracle))==m['identifierOracleSha256']
        assert [json.loads(l) for l in call(a.oracle,['--original-tests']).stdout.splitlines()]==original
        for name in ['observations','deep','unicode17']:
            saved=load(out/(name+'.jsonl.gz'));assert observe(a.oracle,[r['request'] for r in saved])==saved,name
        for r in load(out/'trace.jsonl.gz'):
            result=call(a.oracle,input=lines([r['request']]));assert json.loads(result.stdout)==r['expected'];assert result.stderr.decode().splitlines()==r['trace']
        commands=[json.loads(l) for l in call(a.oracle,['--commands',*[ROOT/'upstream/pandoc/test/command'/name for name in COMMANDS]]).stdout.splitlines()]
        for r in commands:r['file']=str(pathlib.Path(r['file']).relative_to(ROOT))
        assert commands==load(out/'commands.jsonl.gz')
        assert call(a.identifier_oracle,['--expanded']).stdout==expanded;assert call(a.identifier_oracle).stdout==(out/'identifier-ranges.tsv').read_bytes()
        if a.original_cli:
            assert digest(pathlib.Path(a.original_cli))==m['originalCliSha256']
            for r in json.loads((out/'cli-input.json').read_text())+json.loads((out/'cli-warnings.json').read_text()):
                with tempfile.TemporaryDirectory() as tmp:
                    for name,value in r['files'].items():(pathlib.Path(tmp)/name).write_bytes(base64.b64decode(value))
                    result=subprocess.run([a.original_cli,'--emulate',*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=tmp,env=dict(__import__('os').environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
                    assert result.returncode==r['exitCode'] and result.stdout==base64.b64decode(r['stdout']) and result.stderr==base64.b64decode(r['stderr'])
            for r in json.loads((out/'cli-options.json').read_text()):
                result=subprocess.run([a.original_cli,'--emulate',*r['args']],input=base64.b64decode(r['input']),capture_output=True,cwd=ROOT/'upstream/pandoc/test',env=dict(__import__('os').environ,pandoc_datadir=str(ROOT/'upstream/pandoc/data')))
                assert result.returncode==r['exitCode'] and result.stdout==base64.b64decode(r['stdout']) and result.stderr==base64.b64decode(r['stderr'])
        print('Read-only original reader115 + observations1340 + deep7 + trace5 + Unicode12 + all1,114,112classifier points replay passed')
if __name__=='__main__':main()
