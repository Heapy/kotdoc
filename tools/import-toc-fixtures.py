#!/usr/bin/env python3
"""Capture original Pandoc TOC and PathTemplate behavior, without running Kotlin.

Original examples: Tests.Shared (John MacFarlane) and pandoc-structure.lua
(Albert Krewinkel), GPL-2.0-or-later; see pinned sources and licenses/.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/structure/toc.json'
sources = [
    'upstream/pandoc/src/Text/Pandoc/Chunks.hs',
    'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs',
    'upstream/pandoc/src/Text/Pandoc/Shared.hs',
    'upstream/pandoc/test/Tests/Shared.hs',
    'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-structure.lua',
    'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Structure.hs',
    'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/Chunks.hs',
]
hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources}
originals = [c for c in json.loads((root / 'core/src/test/resources/native/upstream.json').read_text()) if c['accepted']]
parser = argparse.ArgumentParser()
parser.add_argument('--check-source', action='store_true')
args = parser.parse_args()
if args.check_source:
    data = json.loads(target.read_text())
    assert data['sources'] == hashes
    assert [(c['path'], c['sha256'], c['document']) for c in data['corpus']] == [
        (c['path'], c['sha256'], c['json']) for c in originals]
    for c in originals:
        assert c['sha256'] == hashlib.sha256((root / 'upstream/pandoc' / c['path']).read_bytes()).hexdigest()
    print(f"Verified {len(data['corpus'])} corpus, {len(data['edges'])} edge, {len(data['trees'])} tree and {len(data['paths'])} path cases")
    raise SystemExit()
assert subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0] == 'pandoc 3.12.1'


def tag(t, c=None): return {'t': t, **({'c': c} if c is not None else {})}
def text(s): return [tag('Str', s)]
def attr(id='', classes=None, kvs=None): return [id, classes or [], kvs or []]
def header(s, level=1, id='', classes=None, kvs=None, inlines=None):
    return tag('Header', [level, attr(id, classes, kvs), text(s) if inlines is None else inlines])
def div(blocks, id='', classes=None): return tag('Div', [attr(id, classes), blocks])
def para(s): return tag('Para', text(s))
def doc(blocks): return {'pandoc-api-version': [1, 23, 1, 2], 'meta': {}, 'blocks': blocks}

edges = []
def case(name, blocks, numbers=False, depth=3):
    edges.append({'name': name, 'document': doc(blocks), 'numbers': numbers, 'depth': depth})

# All seven original Haskell examples.
case('original empty case', [])
case('original no headers', [tag('HorizontalRule')])
case('original unlinked header', [header('H1')])
case('original linked header', [header('H1', id='h1')])
case('original nested headlines', [header('H1a'), header('H2', 2)])
case('original only referenced headers', [header('H1a'), header('H2', 2, 'h2')])
case('original section id used as backup', [div([header('H1')], 'sec', ['section'])])
body = [header('First', id='first'), para('A sentence placed below the first structure.'),
        header('Subsection', 2, 'subsection'), para('Mauris ac felis vel velit tristique imperdiet.'),
        header('Second', id='second'), para('Integer placerat tristique nisl.')]
case('original Lua blocks example', [header('First'), para('A sentence placed below the first structure.'),
     header('Subsection', 2), para('Mauris ac felis vel velit tristique imperdiet.'), header('Second'), para('Integer placerat tristique nisl.')])
link = tag('Link', [attr('link'), [tag('Strong', text('linked')), tag('Note', [para('inside link')])], ['https://example.org/', 'title']])
rich = [*text('Title'), tag('Space'), tag('Emph', [link, tag('Note', [para('note')])]),
        tag('Image', [attr('image'), [link], ['image.png', '']]),
        tag('Cite', [[{'citationId': 'ref', 'citationPrefix': [link], 'citationSuffix': [tag('Note', [para('suffix')])],
                      'citationMode': tag('NormalCitation'), 'citationNoteNum': 0, 'citationHash': 0}], [link]]),
        tag('RawInline', ['html', '<a href="#raw">raw</a>']), tag('Code', [attr(), 'code']),
        tag('Math', [tag('InlineMath'), 'x^2']), tag('SoftBreak'), tag('LineBreak')]
for numbered in [False, True]:
    case(f'rich inline cleaning {numbered}', [header('', id='title', inlines=rich)], numbered)
    for classes in [[], ['unlisted'], ['unnumbered'], ['unlisted', 'unnumbered']]:
        for kvs in [[], [['number', '']], [['number', 'custom'], ['number', 'second']]]:
            for depth in [0, 1, 3, 2147483648]:
                case(f'classes {classes} values {kvs} numbered {numbered} depth {depth}',
                     [header('Parent', id='p', classes=classes, kvs=kvs), header('Child', 2, 'c'), header('Next', id='next')], numbered, depth)
    for cls in [[], ['section'], ['column'], ['columns'], ['fragment'], ['cell']]:
        case(f'div wrappers {cls} {numbered}', [div([div([header('Inside', 2, 'inside')], 'inner')], 'outer', cls)], numbered)
    case(f'headings inside other blocks {numbered}', [tag('BlockQuote', [header('Quote')]),
         tag('BulletList', [[header('List')]]), para('body'), header('Visible')], numbered)
    for depth in [-1, 0, 1, 2, 3, 6]:
        case(f'level gaps {numbered} {depth}', [header('One', 1), header('Four', 4), header('Two', 2), header('Six', 6)], numbered, depth)
rng = random.Random(0x544f43)
for i in range(100):
    def blocks(nesting):
        result = []
        for k in range(rng.randrange(1, 9)):
            kind = rng.randrange(3 if nesting else 2)
            if kind == 0: result.append(para('body'))
            elif kind == 1: result.append(header(f'H{i}-{k}', rng.randrange(1, 8), rng.choice(['', f'id{i}-{k}']),
                rng.choice([[], ['unlisted'], ['unnumbered'], ['unlisted', 'unnumbered']]), rng.choice([[], [['number', 'X']]])))
            else: result.append(div(blocks(nesting-1), f'd{i}-{k}', rng.choice([[], ['section'], ['column']])))
        return result
    case(f'seeded sections {i}', blocks(2), bool(rng.randrange(2)), rng.randrange(0, 9))
corpus = [{'path': c['path'], 'sha256': c['sha256'], 'document': c['json']} for c in originals]
trees = []
for numbered in [False, True]:
    for depth in [0, 1, 2, 6]:
        for level in [1, 2]:
            trees.append({'name': f'chunk TOC numbers={numbered} depth={depth} level={level}', 'document': doc(body),
                          'numbers': numbered, 'depth': depth, 'chunkLevel': level})
paths = []
for template in ['chunk-%n', '%i-%h-%s-%n', '%n/%i\\%h/%s', '../%i/../../%n.html', '%i%i-%x-%%n', '', 'Привет 😀/%h']:
    for title, identifier, number in [('Title', 'id', '1.2'), ('%s/%n', '%h-%i', '%n'), ('../a\\b', 'x/y', 'a\\b'), ('', '', '')]:
        # An explicit nonempty identifier avoids testing ensureIds here.
        paths.append({'template': template, 'heading': title, 'identifier': identifier or 'empty', 'sectionNumber': number,
                      'chunkNumber': 1})
for number in [9, 10, 99, 100, 999, 1000, 1001]:
    paths.append({'template': '%n-%h-%i-%s', 'heading': 'Last', 'identifier': 'last', 'sectionNumber': 'final', 'chunkNumber': number})

with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory)
    request = temp / 'request.json'
    request.write_text(json.dumps({'corpus': corpus, 'edges': edges, 'trees': trees, 'paths': paths}))
    script = temp / 'toc.lua'
    script.write_text('''
local function document(d) return pandoc.read(pandoc.json.encode(d), 'json') end
local function encoded(block) return pandoc.write(pandoc.Pandoc{block}, 'json') end
local function tree(t)
 local s=t[0]
 local children={}
 for _,c in ipairs(t) do children[#children+1]=tree(c) end
 return {title=encoded(pandoc.Plain(s.title)), number=s.number, id=s.id, path=s.path, level=s.level, children=children}
end
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')))
 local input=pandoc.json.decode(f:read('*a'),false);f:close()
 for _,c in ipairs(input.corpus) do
  local d=document(c.document)
  c.default=encoded(pandoc.structure.table_of_contents(d))
  c.numbered=encoded(pandoc.structure.table_of_contents(d,{number_sections=true,toc_depth=6}))
 end
 for _,c in ipairs(input.edges) do
  c.expected=encoded(pandoc.structure.table_of_contents(document(c.document),{number_sections=c.numbers,toc_depth=c.depth}))
 end
 for _,c in ipairs(input.trees) do
  local d=pandoc.structure.split_into_chunks(document(c.document),{number_sections=c.numbers,chunk_level=c.chunkLevel})
  c.tree=tree(d.toc)
  c.expected=encoded(pandoc.structure.table_of_contents(d,{number_sections=c.numbers,toc_depth=c.depth}))
 end
 for _,c in ipairs(input.paths) do
  local blocks={}
  for i=1,c.chunkNumber-1 do blocks[#blocks+1]=pandoc.Header(1,'Before',{id='before-'..i}) end
  blocks[#blocks+1]=pandoc.Header(1,{pandoc.Str(c.heading)},pandoc.Attr(c.identifier,{},{{'number',c.sectionNumber}}))
  local chunks=pandoc.structure.split_into_chunks(pandoc.Pandoc(blocks),{path_template=c.template}).chunks
  c.expected=chunks[#chunks].path
 end
 return pandoc.json.encode(input)
end
''')
    result = json.loads(subprocess.check_output(['pandoc', '-f', 'native', '-t', str(script)], input=b'[]',
        env={**os.environ, 'KOTDOC_REQUEST': str(request)}))
for group in ['corpus', 'edges', 'trees']:
    for c in result[group]:
        for key in ['default', 'numbered', 'expected']:
            if key in c: c[key] = json.loads(c[key])['blocks'][0]
        if 'tree' in c:
            def normalize(t):
                t['title'] = json.loads(t['title'])['blocks'][0]['c']
                t['number'] = t.get('number')
                t['children'] = [normalize(child) for child in t['children']]
                return t
            normalize(c['tree'])
result['sources'] = hashes
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n')
print(f'Imported {len(corpus)} corpus, {len(edges)} edge, {len(trees)} tree and {len(paths)} path cases')
