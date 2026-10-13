#!/usr/bin/env python3
"""Independently observe TagSoup through pinned Pandoc's raw-HTML cross-chunk references.

This verifies identifier discovery, not full token/position/warning output.
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
target = root / 'core/src/test/resources/html/identifiers.json'
sources = ['tools/import-html-id-fixtures.py', 'upstream/pandoc/src/Text/Pandoc/Chunks.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Structure.hs']
hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sources}
p = argparse.ArgumentParser(); p.add_argument('--check-source', action='store_true'); args = p.parse_args()
if args.check_source:
    data = json.loads(target.read_text())
    assert data['sources'] == hashes
    assert data['oracle'] == 'pandoc 3.12.1'
    assert len(data['cases']) == 3670
    print(f"Verified {len(data['cases'])} original Pandoc HTML identifier captures")
    raise SystemExit()
assert subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0] == 'pandoc 3.12.1'
cases = []
def case(name, html, ids): cases.append({'name': name, 'html': html, 'candidates': list(dict.fromkeys(ids))})
entities = json.loads((root / 'core/src/test/resources/html/tagsoup.json').read_text())['html5']
for name, value in entities:
    case('entity ' + name, '<a id="&' + name + '">', [value, '&' + name])
candidates = ['a', 'b', 'c', 'x', 'q', '&', 'A', '😀', '�', '��', 'value', 'a/', 'b/', 'a&b', 'a;b', 'a"', "a'", 'a=b']
for template in ['<a %s>', '<a %s/>', '<a %s', '</a %s>', '<!D %s>', '<?xml %s?>', '<script %s>x',
                 '<!-- <a %s> -->', '<![CDATA[<a %s>]]>', '<script><a %s></script>',
                 '<style><a %s></style>', '<SCRIPT>x</script><a %s>', '<script>x</scriptx><a %s>',
                 '<script>x</script?><a %s>']:
    for attrs in ['id=a', 'ID=a', 'id = a', 'id=a id=b', 'id="" id=a', 'id="a" ID="b"',
                  'id="a"id="b"', "id='a'bad id=b", "'broken id=a", 'x id=a', '/id=a',
                  'id=a/', 'id=a=b', 'id="&amp;"', 'id="&#65;"', 'id="&#x1f600;"',
                  'id="&#xD800;"', 'id="&#xD800;&#xDC00;"', 'id="&#1114112;"',
                  'id="a&b"', 'id="a;b"', 'id="a\"', "id='a\"", 'id=a\r\nb', 'id=a\x0bb']:
        case(template % attrs, template % attrs, candidates)
rng = random.Random(0x544147534f5550)
frags = list(" \n!-</>#&;xy01[]?'\"") + ['CDATA', 'amp', 'gt', 'lt', 'id=a', "id='b'", 'id="c"',
        '<a id=a>', '<b id=b/>', '<q id="c">', '<script>', '</script>', '<!--', '-->', '<?xml', '<!D']
for i in range(1000):
    raw = ''.join(rng.choice(frags) for _ in range(rng.randrange(1, 60)))
    case(f'seeded damaged HTML {i}', raw, candidates)
# Unicode name classes, script-boundary handling, entity failure, and EOF transitions.
for name in ['a', 'é', '中', '𐐀', 'K', 'ſcript', 'script', 'SCRIPT', 'scriptx', 'scrip', 'Ⅷ', '²', '١', '😀', 'a\x00']:
    for ending in ['>', '/>', '?><a id=b>', '\t id=b>', '\x0b id=b>']:
        case(f'Unicode name {name!r} ending {ending!r}', '<' + name + ' id=a' + ending, candidates)
for raw in ['<a id=&amp>', '<a id=&notit;>', '<a id="&#x;">', '<a id="&#١;">', '<a id="&#0;">',
            '<a id="a"', '<a id=a', '<a id', '</a id=a><b id=b>', '<!a id=a>', '<?a id=a?>',
            '<a id="&#xDFFF;&#xD800;">', '<a id="&#xD800;">', '<a id="a&#38;b">']:
    case('boundary ' + raw, raw, candidates + ['\x00', '&#x;', '&#١;', '&notit;', '?'])
assert len(cases) == 3670, len(cases)
with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory)
    request = temp / 'request.json'; request.write_text(json.dumps(cases))
    script = temp / 'ids.lua'
    script.write_text('''
function Writer(doc, opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')))
 local cases=pandoc.json.decode(f:read('*a'),false);f:close()
 for _,c in ipairs(cases) do
  local links={}
  for _,id in ipairs(c.candidates) do links[#links+1]=pandoc.Link('probe','#'..id) end
  local d=pandoc.Pandoc{
   pandoc.Header(1,'Source',pandoc.Attr('oracle-source-chunk')),
   pandoc.RawBlock('html',c.html),
   pandoc.Header(1,'References',pandoc.Attr('oracle-references-chunk')),
   pandoc.Para(links)}
  local chunks=pandoc.structure.split_into_chunks(d,{path_template='part-%n.html'}).chunks
  local found={}
  pandoc.Pandoc(chunks[2].contents):walk{Link=function(link)
   if link.target:sub(1,14)=='part-001.html#' then found[#found+1]=link.target:sub(15) end
  end}
  c.found=found
 end
 return pandoc.json.encode(cases)
end
''')
    result = json.loads(subprocess.check_output(['pandoc', '-f', 'native', '-t', str(script)], input=b'[]',
                        env={**os.environ, 'KOTDOC_REQUEST': str(request)}))
# Lua serializes an untyped empty table as an object; this field is a list.
for c in result:
    if c['found'] == {}: c['found'] = []
assert result[0]['found'], 'Oracle did not discover a known entity identifier'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps({'oracle': 'pandoc 3.12.1', 'sources': hashes, 'cases': result}, ensure_ascii=False, separators=(',', ':')) + '\n')
print(f'Captured {len(result)} original Pandoc HTML identifier cases')
