#!/usr/bin/env python3
"""Independent FB2 writer + shared-helper expectations from pinned Pandoc.

All image resources in corpus tests are explicitly seeded with deterministic
bytes, so generation never downloads resources from upstream test URLs.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--check-source', action='store_true')
args = parser.parse_args()
if args.check_source:
    originals = [f for f in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if f['accepted']]
    fixtures = json.loads((root/'core/src/test/resources/fb2/writer.json').read_text())['corpus']
    assert [f['path'] for f in fixtures] == [f['path'] for f in originals], 'FB2 writer corpus inventory drift'
    for fixture, original in zip(fixtures, originals):
        assert fixture['document'] == original['json'], fixture['path']
        assert fixture['sha256'] == original['sha256'] == hashlib.sha256((root/'upstream/pandoc'/fixture['path']).read_bytes()).hexdigest(), fixture['path']
    print(f'Verified {len(fixtures)} FB2 writer corpus sources and input ASTs')
    raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0] == 'pandoc 3.12.1'
def tag(t,c=None): return {'t':t, **({'c':c} if c is not None else {})}
def text(s): return [tag('Str',s)]
def attr(id='',classes=None,values=None): return [id, classes or [], values or []]
def para(s): return tag('Para',text(s))
def doc(blocks,meta=None): return {'pandoc-api-version':[1,23,1,2], 'meta':meta or {}, 'blocks':blocks}
def image(url, alt='alt', title=''): return tag('Image',[attr(),text(alt),[url,title]])
edges=[]
def case(name,blocks,meta=None,**options): edges.append({'name':name,'document':doc(blocks,meta),**options})

case('empty document',[])
case('empty strings and escaping',[tag('Para',[tag('Str',''),tag('Str','<>\"\'&\t\r\n\x01😀')]), tag('Plain',[])])
case('all inline variants',[tag('Para',[
    *[tag(t,text('Straße ὀδός ﬃ')) for t in ['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps']],
    tag('Code',[attr('1code'),'a\nb']), tag('Quoted',[tag('SingleQuote'),text('q')]), tag('Quoted',[tag('DoubleQuote'),text('q')]),
    tag('SoftBreak'),tag('LineBreak'), tag('Math',[tag('InlineMath'),'x^2']),tag('RawInline',['FB2','<x/>']),
    tag('Link',[attr('1link'),text('link'),['#1target','ignored']])])])
case('block code lines',[tag('CodeBlock',[attr(),'']), tag('CodeBlock',[attr(),'\na\n\nb\n']),tag('CodeBlock',[attr(),'a\r\nb'])])
case('line block empty stanzas',[tag('LineBlock',[[],text('a'),text('b'),[],[],text('c'),[]])])
case('metadata representations',[],{'title':tag('MetaString','Book'), 'author':tag('MetaList',[
    tag('MetaString','One'),tag('MetaString','First Last'),tag('MetaString','First Middle Other Last a@b second@b'),tag('MetaString','a@b')]),
    'date':tag('MetaInlines',text('2026')), 'genre':tag('MetaBool',True), 'lang':tag('MetaString','ru-RU'),
    'abstract':tag('MetaBlocks',[tag('Plain',text('abstract')),para('second')])})
case('metadata genre block flattening',[],{'genre':tag('MetaBlocks',[para('a'),para('b')])})
case('title lines',[tag('Header',[1,attr('1'),[tag('LineBreak'),*text('a'),tag('LineBreak'),tag('LineBreak'),*text('b'),tag('LineBreak')]])])
case('section wrapper ids',[tag('Div',[attr('outer',['custom'],[['role','main'],['x','outer']]),[
    tag('Header',[2,attr('inner',['unnumbered'],[['x','header'],['role','heading']]),text('title')]),para('body')]])])
case('non-section div classes',[tag('Div',[attr('outer',[c]),[tag('Header',[2,attr('inner'),text(c)]),para('body')]]) for c in ['column','columns','fragment','cell']])
case('section siblings and nested wrappers',[para('intro'),tag('Header',[2,attr('a'),text('a')]),para('A'),
    tag('Div',[attr('b'),[tag('Header',[2,attr('b-head'),text('b')]),para('B')]]),tag('Header',[1,attr('c'),text('c')])])
for style in ['DefaultStyle','Example','Decimal','UpperAlpha','LowerAlpha','UpperRoman','LowerRoman']:
    for delim in ['DefaultDelim','Period','OneParen','TwoParens']:
        for start in [0,1,26,27,3999,4000]:
            case(f'list {style} {delim} {start}',[tag('OrderedList',[[start,tag(style),tag(delim)],[[tag('Plain',text('a'))],[para('b')],[para('c')]]])])
case('numeric list maximum bound',[tag('OrderedList',[[9223372036854775807,tag('Decimal'),tag('Period')],[[para('a')],[para('b')]]])])
case('nested lists and indentation',[tag('BulletList',[[para('first'),para('second'),tag('CodeBlock',[attr(),'a\nb\n']),
    tag('BlockQuote',[para('q')]), tag('OrderedList',[[2,tag('Decimal'),tag('Period')],[[tag('Plain',text('nested'))]]])]])])
case('definition ordering with notes',[tag('DefinitionList',[[[tag('Note',[para('term note')])],[[para('definition'),tag('Para',[tag('Note',[para('definition note')])])]]]])])
case('nested notes snapshot',[tag('Para',[tag('Note',[tag('Para',[*text('outer'),tag('Note',[para('inner')])])]),tag('Note',[para('next')])])])
case('notes in metadata twice',[],{'title':tag('MetaInlines',[*text('title'),tag('Note',[para('note')])])})
case('headers inside notes quotes lists',[tag('BlockQuote',[tag('Header',[1,attr('1'),text('x')])]),tag('Para',[tag('Note',[tag('Header',[1,attr(),text('n')])])])])
case('raw FB2 content',[tag('RawBlock',['fB2','<x a="&quot;">a<![CDATA[<&>]]><!--drop-->b</x>tail<y/>']),tag('RawBlock',['html','drop'])])
case('malformed raw FB2',[tag('RawBlock',['fb2','<x>'])])
case('raw missing image attr matching',[tag('RawBlock',['fb2','<image l:href="#image1" alt="raw"/>']),tag('Para',[image('missing-kotdoc.png')])],prefill=False)
case('figure captions',[tag('Figure',[attr('fig'),[None,[para('first'),para('second')]],[
    tag('Para',[tag('Image',[attr(),[],['a.png','title']])]),tag('Para',[image('b.png','explicit')])]])])
case('image title metadata cover and order',[tag('Para',[image('one.png',title='title')]),tag('Para',[*text('inline '),image('two.jpg')])],
    {'cover-image':tag('MetaString','cover.png'), 'title':tag('MetaInlines',[image('title.png')])})
case('missing images in body notes and cover',[tag('Para',[image('missing-kotdoc.png')]),tag('Para',[tag('Note',[tag('Para',[image('missing-kotdoc.png')])])])],
    {'cover-image':tag('MetaString','missing-kotdoc.png')},prefill=False)
for url in ['data:image/png;base64,YQ==','data:image/jpeg;base64,YQ==','data:image/gif;base64,YQ==','data:image/png,abc',
            'DATA:image/png;base64,YQ==','data:IMAGE/PNG;base64,YQ==','data:image/png;charset=utf-8;base64,YQ==',
            'data:image/png;base64, YQ==','data:image/png;base64,YQ%3D%3D','data:image/png;base64,not-base64!',
            'data:image/png;base64,image/jpeg,YQ==','data:image/png;base64','data:;base64,YQ==']:
    case('image URI '+url,[tag('Para',[image(url)])],prefill=False)
case('webtex URL encoding',[tag('Para',[tag('Math',[tag('DisplayMath'),'\\frac{x + 1}{α} / ~'])]),tag('Para',[tag('Math',[tag('InlineMath'),'y^2'])])],
     webtex='data:image/png;base64,',prefill=False)

with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory)
    prep=temp/'prepare.lua'
    input_media=temp/'media.json'
    # Capturing shared helpers before the original writer also supplies independent
    # expected AST for makeSections and blocksToInlines across every source document.
    shared_path=temp/'shared.json'
    prep.write_text('''function Pandoc(doc)
  local seeded = pandoc.List{}
  if os.getenv('KOTDOC_PREFILL') == 'true' then
    local function add(path)
      pandoc.mediabag.insert(path, 'image/png', 'oracle')
      seeded:insert(path)
    end
    doc:walk{Image=function(i) add(i.src) end}
    local cover = doc.meta['cover-image']
    if cover then add(pandoc.utils.stringify(cover)) end
  end
  local f=assert(io.open(os.getenv('KOTDOC_MEDIA'), 'w'))
  f:write(pandoc.json.encode(seeded)); f:close()
  local function asjson(blocks) return pandoc.json.decode(pandoc.write(pandoc.Pandoc(blocks), 'json')) end
  local shared = {sections=asjson(pandoc.utils.make_sections(false, nil, doc.blocks)),
    flattened=asjson({pandoc.Plain(pandoc.utils.blocks_to_inlines(doc.blocks))})}
  f=assert(io.open(os.getenv('KOTDOC_SHARED'), 'w')); f:write(pandoc.json.encode(shared)); f:close()
  return doc
end
''')
    import os
    def oracle(fixture):
        log=temp/'log.json'
        log.unlink(missing_ok=True)
        env={**os.environ,'KOTDOC_PREFILL':str(fixture.get('prefill',True)).lower(),'KOTDOC_MEDIA':str(input_media),'KOTDOC_SHARED':str(shared_path)}
        command=['pandoc','-f','json','-t','fb2','--lua-filter',str(prep),'--verbose','--log',str(log)]
        if 'webtex' in fixture: command+=['--webtex='+fixture['webtex']]
        result=subprocess.run(command,input=json.dumps(fixture['document']).encode(),capture_output=True,env=env,cwd=source,timeout=30)
        fixture['accepted']=result.returncode==0
        fixture['media']=json.loads(input_media.read_text())
        fixture['shared']=json.loads(shared_path.read_text())
        if result.returncode==0:
            fixture['expected']=result.stdout.decode()
            fixture['messages']=json.loads(log.read_text())
        else: fixture['error']=result.stderr.decode()
    source=root/'upstream/pandoc'
    corpus=[]
    for original in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()):
        if not original['accepted']: continue
        fixture={'path':original['path'],'sha256':original['sha256'],'document':original['json']}
        oracle(fixture); corpus.append(fixture)
    for fixture in edges: oracle(fixture)
    output=root/'core/src/test/resources/fb2/writer.json'
    # One oracle case per line keeps generated diffs manageable without dropping
    # any AST, output bytes or diagnostics from the independent expectations.
    encode=lambda fixtures: ',\n'.join(json.dumps(f,ensure_ascii=False,separators=(',',':')) for f in fixtures)
    output.write_text('{\n"corpus":[\n'+encode(corpus)+'\n],\n"edges":[\n'+encode(edges)+'\n]\n}\n')
print(f'Imported {len(corpus)} corpus documents and {len(edges)} writer edge cases')
