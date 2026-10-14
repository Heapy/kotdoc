#!/usr/bin/env python3
"""Capture all exposed WriterOptions fields and real writer outputs via pinned Pandoc."""
import argparse,collections,gzip,hashlib,json,os,random,re,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];target=root/'core/src/test/resources/options/writer-options.jsonl.gz'
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');args=p.parse_args()
sources=['upstream/pandoc/src/Text/Pandoc/Options.hs','upstream/pandoc/src/Text/Pandoc/Writers/Native.hs','upstream/pandoc/src/Text/Pandoc/Writers/FB2.hs','upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/WriterOptions.hs','upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/Template.hs','upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/Context.hs','tools/import-writer-options-fixtures.py','tools/writer-options-reference.lua','core/src/test/resources/native/upstream.json']
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
options=(root/sources[0]).read_text();record=options[options.index('data WriterOptions ='):options.index('instance Default WriterOptions')]
fields=re.findall(r'writer\w+\s*::',record);assert len(fields)==39
assert 'instance ToJSON WriterOptions' not in options
# This is a test observation protocol, not an invented WriterOptions JSON instance.
def digest(cases):return hashlib.sha256(''.join(json.dumps(c,sort_keys=True,separators=(',',':')) for c in cases).encode()).hexdigest()
if args.check_source:
 with gzip.open(target,'rt') as f:m=json.loads(next(f));cases=[json.loads(line) for line in f]
 assert m['sources']==hashes and m['cases']==len(cases) and m['caseHash']==digest(cases)
 assert m['operations']==dict(collections.Counter(c['op'] for c in cases))
 assert m['fields']==39
 print(f'Verified {len(cases)} original WriterOptions/writer observations, all 39 source fields');raise SystemExit
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
cases=[]
def add(op='record',options=None,input='[]',**extra):cases.append(dict(op=op,options=options or {},input=input,**extra))
add()
for field in ['table_of_contents','list_of_figures','list_of_tables','incremental','number_sections','section_divs','reference_links','html_q_tags','setext_headers','list_tables','epub_title_page','prefer_ascii','link_images']:
 for b in [False,True]:add(options={field:b})
for field in ['tab_stop','columns','dpi','split_level','toc_depth','slide_level']:
 for n in [-2**63,-2147483649,-1,0,1,2147483648,2**63-1]:add(options={field:str(n)})
for field,values in dict(cite_method=['citeproc','natbib','biblatex'],email_obfuscation=['none','references','javascript'],wrap_text=['auto','none','preserve'],top_level_division=['part','chapter','section','default'],highlight_method=['none','default','idiomatic'],reference_location=['block','section','document'],figure_caption_position=['above','below'],table_caption_position=['above','below']).items():
 for value in values:add(options={field:value})
for field in ['identifier_prefix','epub_subdirectory','epub_metadata','reference_doc','chunk_template']:
 for value in ['', 'x','Привет😀','\0\r\n','%n-%s-%i-%h']:
  add(options={field:value})
for values in [[],['0','1','-1'],[str(-2**63),str(2**63-1)],['1']*9]:add(options={'number_offset':values})
for values in [[],['font.ttf'],['x','x','Привет😀']]:add(options={'epub_fonts':values})
for values in [[],['strikeout','hard_line_breaks'],['custom'],{'strikeout':True},{'custom':True}]:add(options={'extensions':values})
for value in ['plain','mathml','gladtex','mathjax','katex',{'method':'webtex','url':'missing-'}]:add(options={'html_math_method':value})
for values in [{},{'a':'x','b':True,'c':['x','y']},{'n':{'k':'v'},'nil':False}]:add(options={'variables':values})
for template,variables in [('',{}),('$body$',{'body':'hello'}),('$if(x)$yes$else$no$endif$',{'x':True}),('$for(xs)$$xs$$sep$, $endfor$',{'xs':['a','b','c']}),('text\n😀',{})]:
 add('template',options={'template':template,'variables':variables})
# The Native writer uses only columns and template presence; compare all
# accepted original Native files through genuine writer options in both modes.
originals=json.loads((root/'core/src/test/resources/native/upstream.json').read_text())
for case in originals:
 if case['accepted']:
  native=(root/'upstream/pandoc'/case['path']).read_text()
  for template in [False,True]:add('native',{'columns':'72',**({'template':''} if template else {})},native,originalFile=case['path'])
# 64-bit widths are passed as decimal strings to Lua tonumber, preserving integer
# precision rather than converting them through JSON/Lua floating point.
sample='Pandoc (Meta {unMeta = fromList [("title",MetaString "Hello")]}) [Para [Str "a",Space,Str "longer",Space,Str "line"],CodeBlock ("",[],[]) "x"]'
widths=[-2**63,-2**63+1,-2**62,-2147483649,-2147483648,-1,0,1,2,15,31,72,2147483647,2147483648,2**62,2**63-2,2**63-1]
rng=random.Random(0x397)
widths += [rng.randrange(-2**63,2**63) for _ in range(80)]
for width in widths:
 for template in [False,True]:add('native',{'columns':str(width),**({'template':'not the output'} if template else {})},sample)
for method in ['plain','mathml','gladtex','mathjax','katex',{'method':'webtex','url':'missing-'}]:
 add('fb2',{'html_math_method':method},'[Para [Math InlineMath "x+y",Space,Math DisplayMath "x^2"]]')
# Every field participates in a source record update through real Lua marshalers.
for _ in range(40):
 add(options={'columns':str(rng.randrange(-1000,1000)),'number_offset':[str(rng.randrange(-10,10)) for _ in range(rng.randrange(8))],'number_sections':bool(rng.randrange(2)),'table_of_contents':bool(rng.randrange(2)),'variables':{'x':rng.choice(['','a','😀'])},'epub_metadata':rng.choice(['','x','😀']),'reference_doc':rng.choice(['','a.docx','Привет']),'slide_level':str(rng.randrange(-20,20))})
with tempfile.TemporaryDirectory(prefix='kotdoc-writer-options-') as directory:
 request=Path(directory)/'request.json';request.write_text(json.dumps(cases))
 result=subprocess.run(['pandoc','-f','native','-t',str(root/'tools/writer-options-reference.lua')],input=b'[]',capture_output=True,env={**os.environ,'KOTDOC_WRITER_REQUEST':str(request)})
 if result.returncode:raise RuntimeError(result.stderr.decode())
 observed=json.loads(result.stdout);assert len(observed)==len(cases)
 for case,value in zip(cases,observed):case['expected']=value
m=dict(cases=len(cases),caseHash=digest(cases),sources=hashes,operations=dict(collections.Counter(c['op'] for c in cases)),fields=39,seed='0x397')
raw='\n'.join(json.dumps(c,separators=(',',':')) for c in [m]+cases)+'\n'
with target.open('wb') as out:
 with gzip.GzipFile(filename='',fileobj=out,mode='wb',mtime=0) as f:f.write(raw.encode())
print(json.dumps({k:v for k,v in m.items() if k!='sources'},indent=2))
