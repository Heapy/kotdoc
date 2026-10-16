#!/usr/bin/env python3
"""Independent expected observations from pinned original Pandoc, never Kotlin."""
import argparse, gzip, hashlib, json, pathlib, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1]
def original_requests():
    requests=[]
    def add(name, native, **options): requests.append(dict(name=name,native=native,**options))
    for path in sorted((ROOT/'upstream/pandoc/test').rglob('*.native')):
        add('native/'+str(path.relative_to(ROOT/'upstream/pandoc/test')),path.read_text())
    # Source-level branch witnesses, passed only to the original Haskell reader/writer.
    witnesses={
      'raw':'[RawBlock (Format "html") "<p>a</p>",RawBlock (Format "html") "<p>b</p>",RawBlock (Format "x") "lost",Para [RawInline (Format "html") "<i>x</i>",RawInline (Format "x") "lost",RawInline (Format "dokuwiki") "**raw**"]]',
      'breaks':'[Para [Str "a",SoftBreak,Str "b",LineBreak,Str "c"]]',
      'header':'[Header 2 ("",[],[]) [Emph [Str "a//**__"],Quoted SingleQuote [Str "quote"],Code ("",[],[]) "code",Math InlineMath "x",Note [Para [Str "hidden"]],LineBreak,Link ("",[],[]) [Str "label"] ("url", "")]]',
      'code':'[BlockQuote [BlockQuote [CodeBlock ("",["coffee"],[]) "x\\n",Para [Str "x"]]],CodeBlock ("",[],[]) "",CodeBlock ("",["unknown"],[]) "x"]',
      'ordered-quote':'[BlockQuote [OrderedList (3,Decimal,Period) [[Para [Str "a"],Para [Str "b"]],[Plain [Str "c"]]]]]',
      'definitions':'[DefinitionList [([Strong [Str "term"]],[[Para [Str "one"]],[Para [Str "two"]]]),([Str "complex"],[[Para [Str "a"],Para [Str "b"]]])]]',
      'list-div':'[BulletList [[Div ("",[],[]) [Para [Str "a"],BulletList [[Plain [Str "b"]]]]],[Para [Str "a"],CodeBlock ("",[],[]) "z"],[Para [Str "a"],OrderedList (2,Decimal,Period) [[Plain [Str "b"]]]]]]',
      'links':'[Para [Link ("",[],[]) [Str "abc"] ("mailto:def", "ignored"),Link ("",[],[]) [Str "https://x/a b"] ("https://x/a%20b", ""),Link ("",[],[]) [Emph [Str "x"]] ("/help", ""),Link ("",[],[]) [Str "x"] ("//help", ""),Link ("",[],[]) [Str "x"] ("https://x", "")]]',
      'note':'[BlockQuote [Para [Str "quote",Note [Para [Str "note"],BulletList [[Para [Str "list"]]]]]]]',
      'image':'[Para [Image ("",[],[("width","2in"),("height","3cm")]) [Str "alt"] ("p", "title"),Image ("",[],[("height","12px")]) [] ("q", ""),Image ("",[],[("width","50%"),("height","40%")]) [] ("r", ""),Image ("",[],[("width","10px"),("width","20px")]) [Strong [Str "alt"]] ("s", "")]]',
      'figure':'[Figure ("id",["class"],[]) (Caption (Just [Str "short"]) [Para [Str "caption"]]) [Para [Image ("",[],[]) [] ("p", "")]],Figure ("",[],[]) (Caption Nothing []) []]',
      'lineblock':'[LineBlock [[Str "a"],[],[Strong [Str "b"]]]]',
      'empty-raw':'[RawBlock (Format "x") "a",RawBlock (Format "dokuwiki") "x",RawBlock (Format "dokuwiki") "y",Div ("",[],[]) [],HorizontalRule]',
    }
    for name,native in witnesses.items():
        for wrap in ['auto','none','preserve']:
            for raw in [False,True]: add('branch/'+name+'/'+wrap+'/'+str(raw),native,wrap=wrap,rawHtml=raw)
    for lang in ['cs','coffee','commonlisp','gcc','html','makefile','objectivec','r','sqlmysql','sqlpostgresql','sci','xorg','unknown']:
        add('language/'+lang,'[CodeBlock ("",["'+lang+'"],[]) "x"]')
    for dpi in [72,96,144,0,-10]: add('dpi/'+str(dpi),witnesses['image'],dpi=dpi)
    meta='Pandoc (Meta {unMeta = fromList [("title", MetaInlines [Strong [Str "title"]]),("body",MetaString "override"),("toc",MetaBool False),("x",MetaBlocks [RawBlock (Format "x") "meta"]) ]}) [RawBlock (Format "x") "body",Para [Str "text"]]'
    for template in ['$title$:$body$:$if(toc)$TOC$endif$:$meta-json$', '$body$']:
        add('template/'+str(len(requests)),meta,template=template,toc=True)
    default_template=(ROOT/'core/src/main/resources/io/heapy/kotdoc/pandoc-data/templates/default.dokuwiki').read_text()
    goldens=[('testsuite.native','writer.dokuwiki',True),('tables.native','tables.dokuwiki',False),('dokuwiki_inline_formatting.native','dokuwiki_inline_formatting.dokuwiki',True),('dokuwiki_multiblock_table.native','dokuwiki_multiblock_table.dokuwiki',True),('dokuwiki_external_images.native','dokuwiki_external_images.dokuwiki',True)]
    for native,golden,standalone in goldens:
        add('original-old/'+golden,(ROOT/'upstream/pandoc/test'/native).read_text(),**(dict(template=default_template) if standalone else {}))
    add('original-command/3824.md#1','[BulletList [[Para [Str "hi"],CodeBlock ("",[],[]) " there"],[Para [Str "ok"]]]]')
    add('original-command/5107.md#1',' - foo\n   1. bar\n   - baz\n',reader='muse')
    add('original-command/5107.md#2',' - foo\n   1. bar\n   2. baz\n',reader='muse')
    return requests,goldens

def deep_requests():
    return [dict(name=f'deep/{kind}/{depth}',deep=dict(kind=kind,depth=depth))
            for kind,depth in [('emph',4096),('emph',8192),('div',4096),('quote',4096),('note',4096),('bullet',1024),('ordered',1024),('definition',1024),('table',1024),('diagnostic',4096),('mixed',256)]]

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def packed(records): return ''.join(json.dumps(r,ensure_ascii=False,separators=(',',':'))+'\n' for r in records).encode()
def observe(oracle, requests):
    p=subprocess.run([oracle],input=''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in requests),text=True,capture_output=True,check=True,cwd=ROOT)
    observations=[json.loads(line) for line in p.stdout.splitlines()]
    assert len(observations)==len(requests)
    records=[]
    for request,result in zip(requests,observations):
        assert result['name']==request['name']; assert 'error' not in result,result
        records.append(dict(request={k:v for k,v in request.items() if k!='native'}, **result))
    return records

def pinned_sources():
    result=[ROOT/'upstream/pandoc'/p for p in ['src/Text/Pandoc/Writers/DokuWiki.hs','src/Text/Pandoc/Writers/Shared.hs','src/Text/Pandoc/Shared.hs','src/Text/Pandoc/URI.hs','src/Text/Pandoc/ImageSize.hs','src/Text/Pandoc/Options.hs','src/Text/Pandoc/Extensions.hs','src/Text/Pandoc/Logging.hs','src/Text/Pandoc/Templates.hs','src/Text/Pandoc/Readers/Native.hs','src/Text/Pandoc/Readers/Muse.hs','test/Tests/Old.hs']]
    result += [ROOT/'upstream/pandoc-types'/p for p in ['src/Text/Pandoc/Definition.hs','src/Text/Pandoc/Builder.hs','src/Text/Pandoc/Walk.hs']]
    result += list((ROOT/'upstream/pandoc/test').rglob('*.native'))
    result += [ROOT/'tools/haskell-pandoc-uri-reference/SourceUriPosix.hs', pathlib.Path(__file__),ROOT/'core/src/main/resources/io/heapy/kotdoc/pandoc-data/templates/default.dokuwiki']
    folder=ROOT/'tools/haskell-dokuwiki-writer-reference'
    result += [folder/p for p in ['DokuWikiWriterReference.hs','SourceDokuWiki.hs','prepare.py','build-cached.py','dokuwiki-writer-reference.cabal','cabal.project']]
    return sorted(set(result))

def check_copied_sources():
    folder=ROOT/'tools/haskell-dokuwiki-writer-reference'
    source=(ROOT/'upstream/pandoc/src/Text/Pandoc/Writers/DokuWiki.hs').read_text()
    expected=source.replace('module Text.Pandoc.Writers.DokuWiki ( writeDokuWiki ) where','module SourceDokuWiki ( writeDokuWiki ) where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad, report)','import Text.Pandoc.Class (PandocMonad, report)').replace('import Text.Pandoc.URI (escapeURI, isURI)','import SourceUriPosix (escapeURI, isURI)')
    assert (folder/'SourceDokuWiki.hs').read_text()==expected,'Copied writer differs beyond declared module/import wiring'
    uri=(ROOT/'upstream/pandoc/src/Text/Pandoc/URI.hs').read_text()
    assert (ROOT/'tools/haskell-pandoc-uri-reference/SourceUriPosix.hs').read_text()==uri.replace('module Text.Pandoc.URI (','module SourceUriPosix (',1),'Copied URI helper differs'

def payloads():
    out=ROOT/'core/src/test/resources/dokuwiki-writer'
    return [out/'observations.jsonl.gz',out/'deep.jsonl.gz']+sorted((out/'original').iterdir())

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--oracle')
    mode=parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check-source',action='store_true',help='Read-only pinned source/tool/helper/payload checks')
    mode.add_argument('--verify-oracle',action='store_true',help='Read-only replay of every expected observation')
    mode.add_argument('--generate-deep',action='store_true',help='Create only the new bounded deep corpus; original expectations stay immutable')
    mode.add_argument('--refresh-manifest',action='store_true',help='Record current reviewed source/provenance hashes')
    args=parser.parse_args()
    requests,goldens=original_requests(); deep=deep_requests()
    out=ROOT/'core/src/test/resources/dokuwiki-writer'; manifest=out/'manifest.json'
    check_copied_sources()
    if args.generate_deep:
        assert args.oracle,'--oracle required'
        assert not (out/'deep.jsonl.gz').exists(),'Deep expectations already exist; refusing to overwrite'
        records=observe(args.oracle,deep)
        with gzip.GzipFile(filename=str(out/'deep.jsonl.gz'),mode='wb',mtime=0) as f: f.write(packed(records))
        print(len(records),'new independent deep observations; original corpus unchanged')
        return
    if args.refresh_manifest:
        assert args.oracle,'--oracle required to record binary provenance'
        old=json.loads(manifest.read_text())
        old.update(schema=2,deepCount=len(deep),oracleSha256=digest(pathlib.Path(args.oracle)),
                   sourcePins={str(p.relative_to(ROOT)):digest(p) for p in pinned_sources()},
                   payloadPins={str(p.relative_to(ROOT)):digest(p) for p in payloads()},
                   requestSha256=hashlib.sha256(packed(requests)).hexdigest(),deepRequestSha256=hashlib.sha256(packed(deep)).hexdigest())
        manifest.write_text(json.dumps(old,indent=2)+'\n');print('Recorded source/tool/helper/payload pins');return
    m=json.loads(manifest.read_text()); assert m['schema']==2
    assert m['pandocCommit']=='e51c9c6054c8f4ec5c3209d5abe10939dfe2963e'
    assert m['typesCommit']=='b2cbcefc4547296f47b8120740997a9c021d756f'
    for p in pinned_sources(): assert m['sourcePins'][str(p.relative_to(ROOT))]==digest(p),f'Source/tool pin mismatch: {p}'
    assert set(m['sourcePins'])=={str(p.relative_to(ROOT)) for p in pinned_sources()},'Source pin inventory changed'
    for p in payloads(): assert m['payloadPins'][str(p.relative_to(ROOT))]==digest(p),f'Payload pin mismatch: {p}'
    assert set(m['payloadPins'])=={str(p.relative_to(ROOT)) for p in payloads()},'Payload inventory changed'
    assert m['requestSha256']==hashlib.sha256(packed(requests)).hexdigest()
    assert m['deepRequestSha256']==hashlib.sha256(packed(deep)).hexdigest()
    assert m['count']==len(requests)==len(gzip.decompress((out/'observations.jsonl.gz').read_bytes()).splitlines()),'Original payload/request count mismatch'
    assert m['deepCount']==len(deep)==len(gzip.decompress((out/'deep.jsonl.gz').read_bytes()).splitlines()),'Deep payload/request count mismatch'
    assert m['nativeDocuments']==len(list((ROOT/'upstream/pandoc/test').rglob('*.native')))==266
    for native,golden,_ in goldens:
        for name in [native,golden]: assert (out/'original'/name).read_bytes()==(ROOT/'upstream/pandoc/test'/name).read_bytes(),name
    for name in ['3824.md','5107.md']: assert (out/'original'/name).read_bytes()==(ROOT/'upstream/pandoc/test/command'/name).read_bytes(),name
    print('Read-only source/tool/helper/payload/original-golden checks passed')
    if args.verify_oracle:
        assert args.oracle,'--oracle required'
        assert m['oracleSha256']==digest(pathlib.Path(args.oracle)),'Oracle binary provenance differs; rebuild using recorded source/configuration'
        for filename,rs in [('observations.jsonl.gz',requests),('deep.jsonl.gz',deep)]:
            expected=gzip.decompress((out/filename).read_bytes())
            actual=packed(observe(args.oracle,rs));assert actual==expected,f'Oracle replay mismatch: {filename}'
            print(f'Read-only original oracle replay passed: {filename} ({len(rs)} cases)')
if __name__=='__main__': main()
