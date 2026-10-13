#!/usr/bin/env python3
"""Capture full chunk content/TOC/reference behavior and finite navigation snapshots.

Uses pinned Pandoc only; Kotlin output is never an expectation source.
Original Lua structure examples: Albert Krewinkel, GPL-2.0-or-later.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
target = root / 'core/src/test/resources/structure/chunks.json.gz'
sources = ['tools/import-chunk-fixtures.py', 'core/src/test/resources/html/identifiers.json', 'upstream/pandoc/src/Text/Pandoc/Chunks.hs',
           'upstream/pandoc/src/Text/Pandoc/Shared.hs',
           'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-structure.lua',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/Chunks.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Structure.hs']
hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources}
originals = [c for c in json.loads((root / 'core/src/test/resources/native/upstream.json').read_text()) if c['accepted']]
p = argparse.ArgumentParser(); p.add_argument('--check-source', action='store_true'); args = p.parse_args()
if args.check_source:
    data = json.loads(gzip.decompress(target.read_bytes()))
    assert data['sources'] == hashes
    assert data['oracle'] == 'pandoc 3.12.1'
    corpus = data['cases'][:len(originals)*2]
    for c, original in zip(corpus[::2], originals):
        assert c['document'] == original['json']
        assert c['source'] == original['path']
        assert c['sourceSha256'] == hashlib.sha256((root / 'upstream/pandoc' / c['source']).read_bytes()).hexdigest()
    for a,b in zip(corpus[::2], corpus[1::2]):
        assert a['document'] == b['document'] and a['source'] == b['source'] and a['sourceSha256'] == b['sourceSha256']
    print(f"Verified {len(data['cases'])} independent chunk cases including {len(originals)} original documents twice")
    raise SystemExit()
assert subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0] == 'pandoc 3.12.1'

def tag(t, c=None): return {'t': t, **({'c': c} if c is not None else {})}
def attr(id='', classes=None, kvs=None): return [id, classes or [], kvs or []]
def text(s): return [tag('Str', s)]
def header(s, level=1, id='', classes=None, kvs=None, inlines=None):
    return tag('Header', [level, attr(id, classes, kvs), text(s) if inlines is None else inlines])
def para(s): return tag('Para', text(s))
def div(blocks, id='', classes=None, kvs=None): return tag('Div', [attr(id, classes, kvs), blocks])
def link(id, attrid=''): return tag('Link', [attr(attrid), text('link '+id), [id, 'target title']])
def links(ids): return tag('Para', [link('#'+i) for i in ids])
def doc(blocks, meta=None): return {'pandoc-api-version': [1,23,1,2], 'meta': meta or {}, 'blocks': blocks}
cases = []
def case(name, blocks, numbers=False, level=1, base=None, template='chunk-%n', meta=None, document=None, **extras):
    cases.append({'name': name, 'document': document if document is not None else doc(blocks, meta),
                  'numbers': numbers, 'level': level, 'base': base, 'template': template, **extras})
for original in originals:
    for numbers, level in [(False, 1), (True, 2)]:
        case(f"corpus {original['path']} {numbers}/{level}", [], numbers, level, document=original['json'],
             source=original['path'], sourceSha256=original['sha256'])
case('original Lua empty chunked document', [])
original_body = [header('First', id='first'), para('A sentence placed below the first structure.'),
                 header('Subsection', 2, 'subsection'), para('Mauris ac felis vel velit tristique imperdiet.'),
                 header('Second', id='second'), para('Integer placerat tristique nisl.')]
case('original Lua TOC for chunked document', original_body, level=2)
case('original Lua TOC depth one', original_body)
for meta in [{}, {'title':tag('MetaString', 'Book title')}, {'title':tag('MetaInlines', [link('#a'), tag('Space'), tag('Emph',text('title'))])},
             {'title':tag('MetaBlocks',[para('Block title')])}, {'title':tag('MetaBlocks',[para('one'),para('two')])},
             {'title':tag('MetaBool', True)}, {'title':tag('MetaList',[tag('MetaString','List title')])}]:
    for blocks in [[], [para('preamble')], [para('preamble'),header('A',id='a'),para('after')], [header('A',id='a'),para('after')]]:
        case(f'title representation {len(cases)}', blocks, meta=meta, template='%i-%h-%s-%n.html')
for levels in [[1,2,2,1], [1,3,2,3,1], [3,5,4,2,1], [1,2,3,2,3], [0,1,2,0], [-2,-1,0,1], [1,6,2,5,3,4]]:
    blocks = [para('before')]
    for i, level in enumerate(levels): blocks += [header('H'+str(i),level),para('body'+str(i))]
    for chunk_level in [-1,0,1,2,3,6,9223372036854775807]:
        for base in [None, 1, 3]:
            case(f'level sequence {levels} chunk={chunk_level} base={base}',blocks,True,chunk_level,base)
for base in [-9223372036854775808, -2, 0, 9223372036854775807]:
    case(f'base integer boundary {base}', [header('A',1),header('B',2)], True, 1, base)
for classes in [[], ['unlisted'], ['unnumbered'], ['unlisted','unnumbered']]:
    for numbers in [False,True]:
        for kvs in [[], [['number','']], [['number','X'],['number','second']]]:
            case(f'number classes {classes} {numbers} {kvs}',[header('A',classes=classes,kvs=kvs),header('B',2),header('C')],numbers,2)
for template in ['', 'chunk-%n', '%i-%h-%s-%n', '../%i/../../%n.html', '%h%i%s%n', '%x-%%n']:
    case(f'path substitutions {template}', [para('preamble'),header('%s/%n',id='%h-%i',kvs=[['number','%n']]),header('Last')],
         True, 1, template=template, meta={'title': tag('MetaString', '../%h\\%i')})
# Postorder ID allocation, duplicate explicit IDs, nested containers and ignored IDs.
for blocks in [[header('Same'),header('Same',2),header('Same',3),header('Same')],
               [header('Same'),header('Explicit',id='same'),header('Same')],
               [header('Explicit',id='same'),header('Same'),header('Other',id='same')],
               [header(''),header('',2),header('123'),header('😀')],
               [div([header('Inner',id='head')],id='outer')],
               [div([header('A'),div([header('B',2)],id='nested')],id='wrapper',classes=['column'])],
               [header('A'),tag('BlockQuote',[div([header('Same')],classes=['section'])]),header('Same')],
               [header('A'),tag('BulletList',[[div([header('Same')],classes=['section'])]]),header('Same')],
               [header('A'),tag('Para',[tag('Note',[div([header('Same')],classes=['section'])])]),header('Same')],
               [header('Same'),para('body'),div([header('Same',2)],classes=['other','section']),header('Same')]]:
    case(f'identifiers and wrappers {len(cases)}', blocks, level=3)
# Every AST identifier-bearing node, including intentional exclusions.
ids = ['div','header','table','span','link','image','code','codeblock','figure','caption','row','cell','head','body','foot','raw','rawinline','metaonly','missing','duplicate']
caption = [[tag('Span',[attr('caption'),text('short')])], [para('long')]]
cell = [attr('cell'),tag('AlignDefault'),1,1,[links(['header'])]]
row = [attr('row'),[cell]]
table = tag('Table',[attr('table'),caption,[[tag('AlignDefault'),tag('ColWidthDefault')]],
                     [attr('head'),[row]], [[attr('body'),0,[],[row]]], [attr('foot'),[row]]])
all_nodes = [header('First',id='first'), div([para('div content')],'div'),
             div([header('Header node',2,'header')],id='div-with-header'), table,
             tag('CodeBlock',[attr('codeblock'),'code']), tag('Figure',[attr('figure'),caption,[para('figure')]]),
             tag('RawBlock',['html','<b id="raw"><b id="duplicate">']),
             tag('Para',[tag('Span',[attr('span'),[link('#raw')]]),link('#raw','link'),
                         tag('Image',[attr('image'),[link('#raw')],['#raw','image title']]),
                         tag('Code',[attr('code'),'code']),tag('RawInline',['html','<b id="rawinline">'])]),
             header('Second',id='second'),tag('RawBlock',['html','<b id="duplicate">']),links(ids)]
meta = {'title':tag('MetaInlines',[link('#raw')]), 'nested':tag('MetaMap',{'links':tag('MetaBlocks',[links(ids)])}),
        'raw':tag('MetaBlocks',[tag('RawBlock',['html','<b id="metaonly">'])])}
for level in [1,2,6]: case(f'all attribute locations and metadata {level}', all_nodes, level=level, meta=meta)
for fmt in ['html','html4','html5','HTML','Html','HTML4','html+raw_html','latex','']:
    for inline in [False,True]:
        raw = tag('RawInline' if inline else 'RawBlock',[fmt,'<a id="a"/><SCRIPT><b id="hidden"></SCRIPT><i id="b" ID="upper">'])
        blocks = [header('First',id='first'), tag('Para',[raw]) if inline else raw, header('Second',id='second'), links(['a','b','hidden','upper'])]
        case(f'raw format exact spelling {fmt} inline={inline}',blocks)
# All raw HTML corpus cases go through complete Kotlin chunk reference rewriting as well.
html_cases = json.loads((root / 'core/src/test/resources/html/identifiers.json').read_text())['cases']
for c in html_cases:
    case('raw HTML consumer '+c['name'], [header('First',id='first'),tag('RawBlock',['html',c['html']]),
         header('Second',id='second'),links(c['candidates'])])
for targets in [['#a','#missing','#','a','#a%20b','#a b','other.html#a','https://example.org/#a'], ['##a','#a?x','#a#b']]:
    case(f'link targets {targets}',[header('First',id='first'),div([],id='a'),div([],id='a b'),div([],id='#a'),
         div([],id='a?x'),div([],id='a#b'),header('Second',id='second'),tag('Para',[link(t) for t in targets])])
# The original source specifically does not rewrite heading/TOC copies.
case('heading title and TOC remain unrewritten', [header('',id='first',inlines=[link('#second')]),header('Second',id='second')],
     meta={'title':tag('MetaInlines',[link('#second')])})
rng = random.Random(0x4348554e4b)
for n in range(150):
    blocks=[]
    for k in range(rng.randrange(1,25)):
        kind=rng.randrange(5)
        if kind < 2: blocks.append(header(rng.choice(['Same','',f'H{n}-{k}','Привет 😀']), rng.randrange(1,7),
            rng.choice(['','duplicate',f'id{n}-{k}']),rng.choice([[],['unlisted'],['unnumbered']]),rng.choice([[],[['number','X']]])))
        elif kind == 2: blocks.append(links(['same','duplicate',f'id{n}-{k-1}']))
        elif kind == 3: blocks.append(div([header('Nested',rng.randrange(1,7)),para('body')],classes=rng.choice([[],['column'],['section']])))
        else: blocks.append(para('body'))
    case(f'seeded hierarchy {n}',blocks,bool(rng.randrange(2)),rng.randrange(0,7),rng.choice([None,1,2]),
         rng.choice(['chunk-%n','%i-%s-%n','']),meta={'title':tag('MetaString','Preamble')})

with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory); request = temp/'request.json'
    request.write_text(json.dumps([{'documentJson': json.dumps(c['document']), 'template': c['template'],
        'numbers': c['numbers'], 'levelText': str(c['level']), 'baseText': None if c['base'] is None else str(c['base'])}
        for c in cases]))
    script = temp/'chunks.lua'
    script.write_text('''
local function jsondoc(blocks,meta) return pandoc.write(pandoc.Pandoc(blocks,meta),'json') end
local function tree(t)
 local s=t[0];local children={}
 for _,c in ipairs(t) do children[#children+1]=tree(c) end
 return {title=jsondoc{pandoc.Plain(s.title)},number=s.number,id=s.id,path=s.path,level=tostring(s.level),children=children}
end
local function base(c)
 return {heading=jsondoc{pandoc.Plain(c.heading)},id=c.id,level=tostring(c.level),number=c.number,
 sectionNumber=c.section_number,path=c.path,unlisted=c.unlisted,contents=jsondoc(c.contents)}
end
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')))
 local cases=pandoc.json.decode(f:read('*a'),false);f:close()
 local results={}
 for _,c in ipairs(cases) do
  local input=pandoc.read(c.documentJson,'json')
  local options={path_template=c.template,number_sections=c.numbers,chunk_level=tonumber(c.levelText)}
  if type(c.baseText)=='string' then options.base_heading_level=tonumber(c.baseText) end
  local d=pandoc.structure.split_into_chunks(input,options)
  local chunks={};for _,chunk in ipairs(d.chunks) do chunks[#chunks+1]=base(chunk) end
  local function shape(chunk)
   if not chunk then return nil end
   local b=base(chunk)
   for key,value in pairs(b) do assert(value==chunks[b.number][key], 'snapshot base differs: '..key) end
   return {number=chunk.number,up=shape(chunk.up),prev=shape(chunk.prev),next=shape(chunk.next)}
  end
  local navigation={};for _,chunk in ipairs(d.chunks) do navigation[#navigation+1]=shape(chunk) end
  results[#results+1]={meta=jsondoc({},d.meta),toc=tree(d.toc),chunks=chunks,navigation=navigation,
              tocList=jsondoc{pandoc.structure.table_of_contents(d,{number_sections=c.numbers,toc_depth=6})}}
 end
 return pandoc.json.encode(results)
end
''')
    captured = json.loads(subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',
                        env={**os.environ,'KOTDOC_REQUEST':str(request)}))
assert len(captured) == len(cases)
result = cases
for c, expected in zip(result, captured): c['expected'] = expected
def block_list(s): return json.loads(s)['blocks']
def normalize_tree(t):
    t['title']=block_list(t['title'])[0]['c']; t['number']=t.get('number'); t['level']=int(t['level'])
    t['children']=[normalize_tree(child) for child in t['children']] if t['children'] else []
    return t
for c in result:
    e=c['expected']; e['meta']=json.loads(e['meta'])['meta']; e['toc']=normalize_tree(e['toc'])
    e['tocList']=block_list(e['tocList'])[0]
    if not e['chunks']: e['chunks']=[]
    if not e['navigation']: e['navigation']=[]
    for chunk in e['chunks']:
        chunk['heading']=block_list(chunk['heading'])[0]['c']; chunk['contents']=block_list(chunk['contents'])
        chunk['sectionNumber']=chunk.get('sectionNumber'); chunk['level']=int(chunk['level'])
output=json.dumps({'oracle':'pandoc 3.12.1','sources':hashes,'cases':result},ensure_ascii=False,separators=(',',':')).encode()+b'\n'
target.write_bytes(gzip.compress(output,mtime=0))
print(f'Captured {len(result)} complete chunk cases ({len(output):,} uncompressed bytes)')
