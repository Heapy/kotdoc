#!/usr/bin/env python3
"""Original Pandoc slide-level and prepSlides→makeSections comparisons.

The oracle exposes the composed preparation through pandoc.structure.make_sections;
standalone prepSlides branches are additionally tested from the Haskell source.
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

root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/structure/slides.jsonl.gz'
sources=['tools/import-slide-fixtures.py','upstream/pandoc/src/Text/Pandoc/Slides.hs',
         'upstream/pandoc/src/Text/Pandoc/Shared.hs',
         'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Structure.hs']
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
originals=[c for c in json.loads((root/'core/src/test/resources/native/upstream.json').read_text()) if c['accepted']]
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');args=p.parse_args()
if args.check_source:
    with gzip.open(target,'rt') as f:
        manifest=json.loads(next(f));assert manifest['sources']==hashes
        count=0
        for line in f:
            c=json.loads(line)
            if count < len(originals):
                o=originals[count];assert c['document']==o['json'] and c['source']==o['path']
                assert c['sourceSha256']==hashlib.sha256((root/'upstream/pandoc'/c['source']).read_bytes()).hexdigest()
            count+=1
        assert count==manifest['cases']
    print(f'Verified {count} original slide-level cases and {count*5} prepared section comparisons')
    raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
def tag(t,c=None):return {'t':t,**({'c':c} if c is not None else {})}
def attr(id='',classes=None,kvs=None):return [id,classes or [],kvs or []]
def text(s):return [tag('Str',s)]
def h(level,id=''):return tag('Header',[level,attr(id),text('Heading '+str(level))])
def para(s='body'):return tag('Para',text(s))
def div(blocks,id=''):return tag('Div',[attr(id,['custom'],[['key','value']]),blocks])
def doc(blocks):return {'pandoc-api-version':[1,23,1,2],'meta':{},'blocks':blocks}
cases=[]
def case(name,blocks):cases.append({'name':name,'document':doc(blocks)})
for c in originals:cases.append({'name':'corpus '+c['path'],'document':c['json'],'source':c['path'],'sourceSha256':c['sha256']})
case('empty',[])
for level in [-2,0,1,2,3,6,7,99]:
    for next in [None,h(1),h(3),tag('HorizontalRule'),para(),div([]),div([h(1),para()]),tag('BlockQuote',[h(1),para()])]:
        case(f'heading adjacency {level} {next}',[h(level)]+([] if next is None else [next]))
case('Div and remainder use independent thresholds',[div([h(2),para()]),h(4),div([h(1),para()])])
case('only Div is recursively traversed',[tag('BlockQuote',[h(1),para()]),tag('BulletList',[[h(2),para()]])])
case('accepted heading consumes following Div',[h(3),div([h(1),para()])])
case('rejected heading allows following Div traversal',[h(7),div([h(1),para()])])
for before in [[],[para()],[h(1)],[tag('HorizontalRule')]]:
    for after in [[],[h(1)],[h(2)],[h(3)],[para()],[tag('HorizontalRule')],[div([h(1),para()])]]:
        case(f'hr splitting {len(cases)}',before+[tag('HorizontalRule')]+after)
for id in ['refs','Refs','']:
    for body in [[],[h(1,'references')],[h(2,'references'),para()],[para(),h(1)], [div([h(1),para()])]]:
        for final in [True,False]:
            case(f'refs extraction {id} {len(cases)}',[para(),div(body,id)]+([] if final else [para('after')]))
for prefix in [[h(1)], [h(3)], [div([h(1),para()])], [div([h(3),para()])], [div([div([h(1),para()])])], [div([para(),h(1)])]]:
    case(f'initial heading or Div {len(cases)}',prefix)
rng=random.Random(0x534c494445)
def blocks(depth):
    result=[]
    for _ in range(rng.randrange(0,12)):
        kind=rng.randrange(5 if depth else 4)
        if kind==0: result.append(h(rng.randrange(0,8)))
        elif kind==1:result.append(tag('HorizontalRule'))
        elif kind==2:result.append(para())
        elif kind==3:result.append(tag('BlockQuote',[h(1),para()]))
        else:result.append(div(blocks(depth-1),rng.choice(['refs','','container'])))
    return result
for i in range(150):case(f'seeded blocks {i}',blocks(2))
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory);request=temp/'request.json';request.write_text(json.dumps([json.dumps(c['document']) for c in cases]))
    script=temp/'slides.lua';script.write_text('''
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')))
 local input=pandoc.json.decode(f:read('*a'),false);f:close()
 local result={}
 for _,json in ipairs(input) do
  local d=pandoc.read(json,'json')
  local level=pandoc.structure.slide_level(d)
  local variants={}
  for _,options in ipairs{
    {slide_level=0}, {slide_level=1,number_sections=true},
    {slide_level=2,number_sections=true,base_level=1},
    {slide_level=6}, {slide_level=-1,number_sections=true,base_level=2}} do
   local sections=pandoc.structure.make_sections(d,options)
   variants[#variants+1]={options=options,expected=pandoc.write(pandoc.Pandoc(sections),'json')}
  end
  result[#result+1]={level=tostring(level),variants=variants}
 end
 return pandoc.json.encode(result)
end
''')
    results=json.loads(subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',
                       env={**os.environ,'KOTDOC_REQUEST':str(request)}))
assert len(results)==len(cases)
lines=[json.dumps({'sources':hashes,'oracle':'pandoc 3.12.1','cases':len(cases)},separators=(',',':'))]
for c,r in zip(cases,results):
    c['level']=int(r['level']);c['variants']=r['variants']
    for variant in c['variants']:variant['expected']=json.loads(variant['expected'])['blocks']
    lines.append(json.dumps(c,ensure_ascii=False,separators=(',',':')))
target.write_bytes(gzip.compress(('\n'.join(lines)+'\n').encode(),mtime=0))
print(f'Captured {len(cases)} slide-level cases and {len(cases)*5} prepared section comparisons')
