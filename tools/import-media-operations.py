#!/usr/bin/env python3
"""Independent makeDataURI/fill/extract/ZIP expectations from Pandoc 3.12.1."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
target = root/'core/src/test/resources/media/operations.json'
sources = ['upstream/pandoc/src/Text/Pandoc/Class/IO.hs',
           'upstream/pandoc/src/Text/Pandoc/Class/PandocMonad.hs',
           'upstream/pandoc/src/Text/Pandoc/SelfContained.hs',
           'upstream/pandoc/src/Text/Pandoc/UTF8.hs',
           'upstream/pandoc/test/Tests/MediaBag.hs',
           'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-mediabag.lua',
           'vendor/zip-archive-0.5/Zip.hs']
hashes = {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
parser=argparse.ArgumentParser(); parser.add_argument('--check-source',action='store_true');args=parser.parse_args()
if args.check_source:
    data=json.loads(target.read_text());assert data['sources']==hashes
    print(f'Verified {len(data["uri"])} URI creation cases and {len(data["extract"])} media operation cases')
    raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
def node(t,c):return {'t':t,'c':c}
def image(src,alt=None):return node('Image',[['i',['custom'],[['x','y']]],[node('Str','alt')] if alt is None else alt,[src,'title']])
def doc(images,meta=None):return {'pandoc-api-version':[1,23,1,2],'meta':meta or {},'blocks':[node('Para',images)]}
def media(path,hex,mime=None):return {'path':path,'hex':hex,**({'mime':mime} if mime is not None else {})}
uri=[]
for mime in ['text/plain','text/html;charset=ascii','text/x+xml','image/svg+xml','application/x+xml;charset=utf-8','Text/plain','application/octet-stream','']:
    for raw in [b'',b'foo',b'\xef\xbb\xbfhi\r\n',b'<> &?/#=\"\'~\x00\r', 'Привет 😀'.encode(),b'\xff\x80',b'\xc0\xaf']:
        uri.append({'mime':mime,'hex':raw.hex()})
cases=[]
def case(name,destination,document,seeds=None,local=None):cases.append({'name':name,'destination':'$TEMP/'+destination,'document':document,'seeds':seeds or [],'local':local or {}})
seeds=[media('z.bin','000102ff'),media('a.txt',('compressible '*500).encode().hex()),media('é/😀.png','89504e47'),media('empty','')]
for dest in ['out','out/archive.zip','flat.zip','out/archive.ZIP','out/./nested']:
    case('directory and archive '+dest,dest,doc([image(s['path']) for s in seeds]),seeds)
case('empty bag makes no output','empty.zip',doc([]))
case('seeded keys and metadata','out',doc([image('./a.png'),image('https://example.invalid/x'),image('a%20b.png')],{'title':node('MetaInlines',[image('a.png')])}),
     [media('a.png','61'),media('https://example.invalid/x','62','image/jpeg'),media('a%20b.png','63')])
case('fetch local data and missing','out',doc([image('local.png'),image('data:image/png;base64,YQ=='),image('missing.png')]),local={'local.png':'000102ff'})
case('nested alt image and repeated failure','out',doc([image('outer-missing',[image('data:image/png;base64,Yg==')]),image('outer-missing')]))
# Two distinct canonical keys can intentionally map to the same hash filename;
# directory writes use last item, ZIP foldr keeps first item.
hashname=hashlib.sha1(b'x').hexdigest()+'.png'
for dest in ['out','out/collision.zip']:
    case('output path collision '+dest,dest,doc([image('https://example.invalid/x'),image(hashname)]),
         [media('https://example.invalid/x','78','image/png'),media(hashname,'79','image/png')])
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory)
    request=temp/'request.json';request.write_text(json.dumps(uri))
    script=temp/'uri.lua'
    script.write_text('''function Writer(doc, opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')));local input=pandoc.json.decode(f:read('*a'));f:close()
 for _, c in ipairs(input) do
   local bytes=c.hex:gsub('..',function(x) return string.char(tonumber(x,16)) end)
   local ok,result=pcall(pandoc.mediabag.make_data_uri,c.mime,bytes)
   c.accepted=ok;if ok then c.expected=result end
 end
 return pandoc.json.encode(input)
end''')
    for c in uri:
        request.write_text(json.dumps([c]))
        result=subprocess.run(['pandoc','-f','native','-t',str(script)],input=b'[]',capture_output=True,env={**os.environ,'KOTDOC_REQUEST':str(request)})
        if result.returncode==0:c.update(json.loads(result.stdout)[0])
        else:
            assert b'Invalid UTF-8 stream' in result.stderr,result.stderr
            c['accepted']=False
    prep=temp/'seed.lua'
    prep.write_text('''function Pandoc(doc)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')));local input=pandoc.json.decode(f:read('*a'));f:close()
 for _, c in ipairs(input) do
  pandoc.mediabag.insert(c.path,c.mime,c.hex:gsub('..',function(x) return string.char(tonumber(x,16)) end))
 end
 return doc
end''')
    for n,c in enumerate(cases):
        work=temp/str(n);work.mkdir()
        for p,h in c['local'].items():(work/p).write_bytes(bytes.fromhex(h))
        request.write_text(json.dumps(c['seeds']))
        log=temp/'log.json'
        result=subprocess.run(['pandoc','-f','json','-t','json','--lua-filter',str(prep),'--extract-media',c['destination'].replace('$TEMP',str(work)),
                               '--resource-path',str(work),'--verbose','--log',str(log)],input=json.dumps(c['document']).encode(),capture_output=True,cwd=work,
                               env={**os.environ,'KOTDOC_REQUEST':str(request)},timeout=30)
        assert result.returncode==0,(c['name'],result.stderr)
        c['expected']=json.loads(result.stdout.decode().replace(str(work),'$TEMP'))
        c['messages']=[m for m in json.loads(log.read_text().replace(str(work),'$TEMP')) if m['type'] not in ['RunningFilter','FilterCompleted']]
        c['files']={str(p.relative_to(work)):p.read_bytes().hex() for p in sorted(work.rglob('*')) if p.is_file() and str(p.relative_to(work)) not in c['local']}
target.write_text(json.dumps({'sources':hashes,'uri':uri,'extract':cases},ensure_ascii=False,indent=2)+'\n')
print(f'Imported {len(uri)} URI creation cases and {len(cases)} media operation cases')
