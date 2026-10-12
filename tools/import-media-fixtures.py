#!/usr/bin/env python3
"""Capture original MediaBag/MIME behavior through Pandoc's Lua API."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--check-source',action='store_true')
args=parser.parse_args()
source_files=['core/src/main/resources/io/heapy/kotdoc/mime-types.tsv',
    'upstream/pandoc/src/Text/Pandoc/MediaBag.hs','upstream/pandoc/src/Text/Pandoc/MIME.hs',
    'upstream/pandoc/src/Text/Pandoc/URI.hs','upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs']
source_hashes={path:hashlib.sha256((root/path).read_bytes()).hexdigest() for path in source_files}
output=root/'core/src/test/resources/media/oracle.json'
if args.check_source:
    fixture=json.loads(output.read_text())
    assert fixture['sources']==source_hashes, 'Media fixture source drift'
    expected_extensions={'extension '+line.split('\t')[0] for line in (root/source_files[0]).read_text().splitlines()}
    assert expected_extensions=={f['name'] for f in fixture['media'] if f['name'].startswith('extension ')}
    print(f'Verified {len(fixture["media"])} media fixture sources and complete MIME extension coverage')
    raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
table=[line.split('\t') for line in (root/'core/src/main/resources/io/heapy/kotdoc/mime-types.tsv').read_text().splitlines()]
cases=[{'name':'extension '+ext,'path':'file.'+ext} for ext in dict(table)]
cases += [{'name':'reverse '+mime,'path':'https://example.invalid/file','mime':mime} for mime in sorted(set(m for _,m in table))]
for path in ['', '.', './', '/', '//', '/..', '/../foo', 'a/..', 'a/../b.png', '../x.png', '../../x.png', './x.png', 'x.png/',
    'a//b/./c/../x.png','a\\b\\x.png','foo..bar.png','%2e%2e/x.png','a%20b.png','a b.png','a%2Fb.png','a%5Cb.png',
    'x%FF.png','C:\\x.png','C:/x.png','C:x.png','\\\\server\\x.png','http://example.invalid/x.png',
    'https://example.invalid/a?x=.png','unknown:thing.png','data:image/png;base64,YQ==','DATA:image/png;base64,YQ==',
    'data:not valid', 'layout-cache','Formula-x/','image.SVG','image.svgz','image.png.gz','.png','file.', 'file',
    '\U00010000.png','\uE000.png']:
    cases.append({'name':'path '+path,'path':path})
    cases.append({'name':'explicit MIME '+path,'path':path,'mime':'image/png'})
for mime in ['application/javascript','image/png;charset=utf-8','IMAGE/PNG','application/unknown','text/plain; charset=utf-8','image/svg+xml','']:
    cases.append({'name':'mime '+mime,'path':'https://example.invalid/a.unknown','mime':mime})
data_cases=['data:text/plain;base64,YWJj','DATA:text/plain;base64,YWJj','data:text/plain;charset=utf-8;base64,YWJj',
    'data:text/plain;base64,YQ%3D%3D','data:text/plain;base64,Y%51==','data:text/plain;base64,Y Q==',
    'data:image/png;base64,%FFYWJj','data:application/octet-stream,%00%FF%80A','data:text/plain,hello%20world',
    'data:text/plain;charset=utf-8,hello','data:;base64,YQ==','data:,abc','data:text/plain;base64,YQ==extra',
    'data:text/plain; base64,YQ==','data:text/plain,abc?ignored#fragment','data:text/plain,%E2%82%AC',
    'data:text/plain,Привет','data:text/plain;base64,']
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory)
    request=temp/'request.json'
    request.write_text(json.dumps({'media':cases,'data':data_cases}))
    script=temp/'oracle.lua'
    script.write_text('''function Writer(doc, opts)
  local f=assert(io.open(os.getenv('KOTDOC_REQUEST')))
  local input=pandoc.json.decode(f:read('*a')); f:close()
  local media=pandoc.List{}
  for _, c in ipairs(input.media) do
    pandoc.mediabag.empty()
    pandoc.mediabag.insert(c.path, c.mime, 'oracle')
    c.expected=pandoc.mediabag.list()
    c.lookup_mime=select(1,pandoc.mediabag.lookup(c.path))
    media:insert(c)
  end
  local data=pandoc.List{}
  pandoc.mediabag.empty()
  for _, url in ipairs(input.data) do
    local mime, bytes=pandoc.mediabag.fetch(url)
    data:insert({url=url,mime=mime,hex=bytes:gsub('.',function(c) return string.format('%02x',string.byte(c)) end)})
  end
  return pandoc.json.encode({media=media,data=data})
end
''')
    import os
    result=subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',env={**os.environ,'KOTDOC_REQUEST':str(request)})
    output=root/'core/src/test/resources/media/oracle.json'
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps({'sources':source_hashes,**json.loads(result)},ensure_ascii=False,indent=2)+'\n')
print(f'Imported {len(cases)} MediaBag/MIME cases and {len(data_cases)} data URI fetch cases')
