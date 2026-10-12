#!/usr/bin/env python3
"""Capture extension registry/read/format-path behavior from pinned Pandoc, not Kotlin."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
target = root/'core/src/test/resources/options/extensions.json'
sources = ['upstream/pandoc/src/Text/Pandoc/Extensions.hs',
           'upstream/pandoc/src/Text/Pandoc/Format.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/Format.hs',
           'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Format.hs',
           'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-format.lua']
hashes = {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
parser=argparse.ArgumentParser();parser.add_argument('--check-source',action='store_true');args=parser.parse_args()
if args.check_source:
    data=json.loads(target.read_text());assert data['sources']==hashes
    print('Verified independent extension and format fixture source hashes')
    raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
source=(root/sources[0]).read_text()
names=re.findall(r'\bExt_(\w+)',source.split('data Extension =')[1].split('deriving')[0])
formats=set(re.findall(r'(?:getDefaultExtensions|getAll) "([^"]+)"',source))
for flag in ['--list-input-formats','--list-output-formats']:
    formats.update(subprocess.check_output(['pandoc',flag],text=True).splitlines())
formats.update(['','unknown','MARKDOWN','Markdown','markdown+smart','native','json','x.lua','mobi'])
read_names=names+['lhs','lhs ',' smart','SMART','smart()','(smart)','smart -- comment','smart {- comment -}', '', 'custom','😀','\ue000','table_attributes','header_attributes']
read_names += ['smart'+chr(n) for n in [9,10,11,12,13,28,29,30,31,32,0x85,0xa0,0x1680,0x180e,0x2000,0x2001,0x2002,0x2003,0x2004,0x2005,0x2006,0x2007,0x2008,0x2009,0x200a,0x200b,0x2028,0x2029,0x202f,0x205f,0x3000]]
read_cases=[[n] for n in read_names]+[list(reversed(names))+['custom','😀','\ue000','lhs','smart '],['smart','smart ','lhs','literate_haskell','smart\t'],['custom','custom','other','']]
extensions=re.findall(r'"(\.[^"]+)"\s*-> defFlavor', (root/sources[1]).read_text())
paths=[[prefix+ext] for ext in extensions+['.1','.9','.0','.10','.unknown','.rmd'] for prefix in ['book','/tmp/book','.','https://example.org/books/book']]
paths += [[x] for x in ['', '/', 'https://example.org','https://example.org/','https://example.org/?x=.md','https://example.org/a.md?x=.html#file.epub','https://example.org/a%2Emd','https://example.org/a.MD','https://example.org/é.md','https://example.org/a b.md','mailto:a.md','data:a.md','file:///a.md','//example.org/a.md?x=.html','a.md?x=.html','/a.md/','a.md/.','a\\b.md','a.MD','a.Rmd','a.rmd','.md','..','foo.','a.İPYNB']]
paths += [['a.unknown','b.md','c.rst'],['a.rst','b.md'],['a.unknown'],[]]
specs=['','+smart','-smart','native','NATIVE','native+east_asian_line_breaks','native-east_asian_line_breaks','native+east_asian_line_breaks-east_asian_line_breaks','native-east_asian_line_breaks+east_asian_line_breaks','native+','native++','native+smart','native+z+table_attributes+header_attributes','native+SMART','native+lhs','native+smart ','native+foo.bar']
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory)
    request=temp/'request.json'; request.write_text(json.dumps({'formats':sorted(formats),'read':read_cases,'paths':paths}))
    script=temp/'oracle.lua'
    script.write_text('''function Writer(doc, opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')));local input=pandoc.json.decode(f:read('*a'));f:close()
 local out={formats={},read={},paths={}}
 for _, name in ipairs(input.formats) do
  table.insert(out.formats,{name=name,defaults=pandoc.format.default_extensions(name),supported=pandoc.format.all_extensions(name),config=pandoc.format.extensions(name)})
 end
 for _, names in ipairs(input.read) do
  local opts=pandoc.ReaderOptions{extensions=names}
  table.insert(out.read,{input=names,expected=opts.extensions})
 end
 for _, paths in ipairs(input.paths) do
  table.insert(out.paths,{input=paths,expected=pandoc.format.from_path(paths) or false})
 end
 return pandoc.json.encode(out)
end''')
    output=json.loads(subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',env={**os.environ,'KOTDOC_REQUEST':str(request)}))
    output['specs']=[]
    for spec in specs:
        p=subprocess.run(['pandoc','-f',spec,'-t','native'],input=b'[]',capture_output=True)
        output['specs'].append({'spec':spec,'accepted':p.returncode==0,'error':p.stderr.decode().rstrip('\n')})
    # A real custom reader exposes the extension set selected by parseFlavoredFormat.
    reader=temp/'custom-reader+name.lua'
    reader.write_text('''Extensions={smart=true,citations=false,custom=false,east_asian_line_breaks=false}
function Reader(input,opts)
 return pandoc.Pandoc({pandoc.Plain({pandoc.Str(pandoc.json.encode(opts.extensions))})})
end''')
    suffixes=['','+smart','-smart','+smart-smart','-smart+smart','+citations','-citations+citations-citations','+custom','+custom-custom','+east_asian_line_breaks-smart','+SMART','+unknown','+','++smart']
    output['custom']=[]
    for suffix in suffixes:
        p=subprocess.run(['pandoc','-f',str(reader)+suffix,'-t','json'],input=b'',capture_output=True)
        entry={'suffix':suffix,'accepted':p.returncode==0}
        if p.returncode==0:entry['expected']=json.loads(json.loads(p.stdout)['blocks'][0]['c'][0]['c'])
        else:entry['error']=p.stderr.decode().rstrip('\n').replace(str(reader),'custom-reader+name.lua')
        output['custom'].append(entry)
output['sources']=hashes
target.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
print('Imported '+', '.join(f'{len(output[k])} {k}' for k in ['formats','read','paths','specs','custom']))
