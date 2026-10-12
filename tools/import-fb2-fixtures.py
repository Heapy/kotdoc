#!/usr/bin/env python3
"""Pandoc 3.12.1 oracle for FB2 documents, diagnostics and binary decoding.

Kotlin is never involved in producing expected results. --check-source validates
the complete tracked FB2 corpus without needing an installed Pandoc binary.
"""
import argparse
import hashlib
import json
from pathlib import Path
import random
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'upstream/pandoc'
target = root / 'core/src/test/resources/fb2/oracle.json'
parser = argparse.ArgumentParser()
parser.add_argument('--check-source', action='store_true')
args = parser.parse_args()
paths = sorted(subprocess.check_output(['git', '-C', str(source), 'ls-files', 'test/*.fb2', 'test/**/*.fb2'], text=True).splitlines())
if args.check_source:
    fixtures = json.loads(target.read_text())['upstream']
    assert [f['path'] for f in fixtures] == paths
    for f in fixtures:
        assert hashlib.sha256((source / f['path']).read_bytes()).hexdigest() == f['sha256'], f['path']
    print(f'Verified {len(fixtures)} upstream FB2 sources')
    raise SystemExit()

assert subprocess.check_output(['pandoc', '--version'], text=True).splitlines()[0] == 'pandoc 3.12.1'
edges = []
def case(name, text):
    edges.append({'name': name, 'input': text})
def book(content, description='', notes='', extra=''):
    return ('<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">'
            + description + '<body><section>' + content + '</section></body>' + notes + extra + '</FictionBook>')
def inline(name, text):
    case(name, book('<p>' + text + '</p>'))

inline('all inline styles', 'a<strong>b</strong><strong>c</strong><emphasis>d</emphasis><strikethrough>e</strikethrough>'
       '<sub>f</sub><sup>g</sup><code>h<strong>ignored</strong>i</code><style name="x" xml:lang="en">j</style>')
inline('whitespace boundaries', ' \t a\r\nb <emphasis> c </emphasis>\n<strong>d</strong>\u00a0e\u2028f\u000bz ')
inline('CDATA and escaped text', 'a<![CDATA[b & <c>\r\nd]]>&amp;&lt;&gt;&quot;&apos;&#x1f600;&#10;z')
inline('comments and processing instructions merge text', 'a<!-- ignored -->b<?target data?>c')
inline('style diagnostics', '<style><unknown/><a>missing</a></style><style name=""><unknown/></style><other/>')
inline('nested links', '<a l:href="a">x<a l:href="b">drop</a><strong><a l:href="c">keep</a></strong></a>')
inline('namespace sensitive href', '<a href="wrong">a</a><a l:href="right">b</a><a xmlns:q="http://www.w3.org/1999/xlink" q:href="same">c</a>')
inline('inline images', '<image l:href="#a" alt="two words" title="ignored" id="ignored"/><image alt="drop"/>')
inline('empty image alternate', '<image l:href="##a" alt=""/><image l:href="#b"/>')
case('block images', book('<image id="x" l:href="#a" title="title" alt="two words"/><image/><image href="#wrong"/>'))
case('titles and levels', book('<title><p>a</p><empty-line/><ignored/><p>b</p></title><subtitle>x</subtitle>'
     '<section id="nested"><title><p>deep</p></title></section><title><p>restored</p></title>'))
case('stanza joining', book('<poem><stanza><v>a</v><v>b</v><subtitle>s</subtitle><v>c</v><v>d</v></stanza>'
     '<date>x<strong>skip</strong>y</date><unknown/></poem>'))
case('annotation and cite', book('<annotation><p>a</p><cite><p>b</p><text-author>c</text-author><subtitle>d</subtitle><empty-line/><table/><x/></cite>'
     '<subtitle>s</subtitle><empty-line/><table/><x/></annotation><epigraph id="e"><text-author>f</text-author><empty-line/><x/></epigraph>'))
case('unsupported tables are silent', book('<table><tr><td>ignored</td></tr></table><unknown/>'))
case('root and body diagnostics', '<FictionBook><unknown/><body><p>unexpected</p></body><description><unknown/><title-info><unknown/></title-info></description></FictionBook>')
case('wrong root', '<wrong><FictionBook/></wrong>')
case('metadata details', book('', '<description><title-info><author><first-name>A</first-name><middle-name/><last-name>B</last-name><nickname>C</nickname>'
     '<home-page>D</home-page><email>E</email><unknown/></author><author/><book-title>a<strong>skip</strong>b</book-title>'
     '<keywords> a,\u00a0b\u00a0,,\u0085c\u0085,</keywords><date value="ignored">now</date>'
     '<coverpage><image l:href="#cover"/></coverpage><lang>ignored</lang><genre>ignored</genre></title-info></description>'))
case('metadata last wins and authors accumulate', book('', '<description><title-info><book-title>first</book-title><author><nickname>A</nickname></author></title-info>'
     '<title-info><book-title>last</book-title><author><nickname>B</nickname></author></title-info></description>'))
case('cover requires FB2 namespace', '<FictionBook xmlns:l="http://www.w3.org/1999/xlink"><description><title-info><coverpage><image l:href="#x"/></coverpage></title-info></description></FictionBook>')
case('forward note', book('<p><a l:href="#n" type="note">label</a><a l:href="#missing" type="note">fallback</a><a l:href="#n" l:type="note">ordinary</a></p>',
     notes='<body name="notes"><section id="n"><title><p>drop</p></title><p>note</p></section></body>'))
case('notes sequential resolution and duplicate ids', book('<p><a l:href="#a" type="note">a</a><a l:href="#b" type="note">b</a></p>', notes=
     '<body name="notes"><section id="a"><p><a l:href="#b" type="note">forward unresolved</a></p></section>'
     '<section id="b"><p><a l:href="#a" type="note">back resolved</a></p></section><section id="a"><p>replaced</p></section></body>'))
case('first notes body only', book('<p><a l:href="#a" type="note">a</a><a l:href="#b" type="note">b</a></p>',
     notes='<body name="notes"><section id="a"><p>A</p></section></body><body name="notes"><section id="b"><p>B</p></section></body>'))
case('notes first title only', book('<p><a l:href="#n" type="note">n</a></p>', notes=
     '<body name="notes"><section/><section id="n"><title><p>drop</p></title><title><p>keep</p></title><section><title><p>nested</p></title></section></section></body>'))
case('binary validation and replacement', book('', extra='<binary/><binary id="x"/><binary id="a" content-type="first">YQ==</binary><binary id="a" content-type="second">Yg==</binary>'))
for name, content in [
    ('unknown entity', '&unknown;'), ('html entity', '&nbsp;'), ('zero character', '&#0;'),
    ('surrogate character', '&#xD800;'), ('out of range character', '&#x110000;'),
    ('uppercase hex marker', '&#X41;'), ('invalid reference', '&#xyz;'),
    ('undeclared prefix', '<u:strong>x</u:strong>'), ('raw control', '\x01'),
    ('huge character reference', '&#18446744073709551681;'),
]: inline(name, content)
for name, xml in [
    ('empty document', ''), ('text only', 'text'), ('multiple roots', '<FictionBook/><FictionBook/>'),
    ('trailing text', '<FictionBook/>tail'), ('leading text', 'lead<FictionBook/>'),
    ('missing close', '<FictionBook>'), ('wrong close', '<FictionBook></other>'),
    ('duplicate attributes', '<FictionBook><body><section id="first" id="last"/></body></FictionBook>'),
    ('whitespace around tags', '< FictionBook >< body >< section / ></ body ></ FictionBook >'),
    ('unicode space around root', '\u00a0<FictionBook/>\u00a0'),
    ('xml declaration and doctype', '<?xml version="1.0"?>\n<!DOCTYPE FictionBook SYSTEM "https://example.invalid/no-fetch.dtd">\n<FictionBook/>'),
    ('namespace aliases on close tags', '<f:FictionBook xmlns:f="urn:a" xmlns:g="urn:a"></g:FictionBook>'),
    ('empty prefix namespace', '<FictionBook xmlns:l=""><body><section><p><a l:href="x">a</a></p></section></body></FictionBook>'),
]: case(name, xml)
for name, declarations, content in [
    ('internal entity', '<!ENTITY x "abc">', '&x;'),
    ('nested entity', '<!ENTITY x "a&y;c"><!ENTITY y "b">', '&x;'),
    ('entity markup', '<!ENTITY x "<strong>bold</strong>">', '&x;'),
    ('entity markup attribute unresolved', '<!ENTITY x "<style name=\'&y;\'>text</style>"><!ENTITY y "s">', '&x;'),
    ('attribute entity markup', '<!ENTITY x "a<strong>bold</strong><![CDATA[ignored]]>c">', '<style name="&x;">x</style>'),
    ('recursive entity', '<!ENTITY x "&x;">', '&x;'),
    ('external entity is not loaded', '<!ENTITY x SYSTEM "file:///should-not-be-read">', '&x;'),
    ('parameter entity is ignored', '<!ENTITY % x "abc">', '&x;'),
    ('duplicate entity first wins', '<!ENTITY x "first"><!ENTITY x "second">', '&x;'),
    ('entity expansion at limit', '<!ENTITY x "' + 'a'*8192 + '">', '&x;'),
    ('entity expansion over limit', '<!ENTITY x "' + 'a'*8193 + '">', '&x;'),
    ('entity quotes not escaped in text', '<!ENTITY x \'' + '"'*8192 + '\'>', '&x;'),
]: case(name, '<!DOCTYPE FictionBook ['+declarations+']>'+book('<p>'+content+'</p>'))

samples = ['', 'A', 'AA', 'AAA', 'AAAA', 'AAAAA', 'A=', 'AA=', 'AA==', 'AAA=', 'A===', '=AAAA', 'AA=AAA', 'AAA=AAAA',
           'Y! W@J#j', 'YQ==ignored', 'YWJj-_YWJj', 'YWJjéYWJj', 'YQ====', 'YR==', 'YWK=']
rng = random.Random(0x464232)
samples += [''.join(rng.choice('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=!? \n') for _ in range(rng.randrange(1,100))) for _ in range(100)]
for index, text in enumerate(samples):
    case(f'lenient base64 {index}', book('', extra=f'<binary id="binary" content-type="application/octet-stream">{text}</binary>'))

with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory)
    writer = temp / 'capture.lua'
    writer.write_text('''function Writer(doc, opts)
  local media = pandoc.List{}
  for path, mime, contents in pandoc.mediabag.items() do
    local hex = contents:gsub('.', function(c) return string.format('%02x', string.byte(c)) end)
    media:insert({path=path, mime=mime, hex=hex})
  end
  return pandoc.json.encode({document=pandoc.json.decode(pandoc.write(doc, 'json')), media=media})
end
''')
    def oracle(text):
        log = temp / 'log.json'
        log.unlink(missing_ok=True)
        result = subprocess.run(['pandoc','-f','fb2','-t',str(writer),'--verbose','--log',str(log)], input=text, capture_output=True)
        answer = {'accepted': result.returncode == 0}
        if result.returncode == 0:
            answer.update(json.loads(result.stdout))
            answer['messages'] = json.loads(log.read_text())
        else:
            answer['error'] = result.stderr.decode()
        return answer
    upstream = []
    for path in paths:
        data = (source / path).read_bytes()
        upstream.append({'path':path, 'sha256':hashlib.sha256(data).hexdigest(), **oracle(data)})
    for edge in edges:
        edge.update(oracle(edge['input'].encode()))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({'upstream':upstream, 'edges':edges}, ensure_ascii=False, indent=2)+'\n')
print(f'Imported {len(upstream)} FB2 files and {len(edges)} edge cases')
