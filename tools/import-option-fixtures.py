#!/usr/bin/env python3
"""Capture Options JSON through original Lua marshalers and JSON-filter environment."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/options/options.json'
sources=['upstream/pandoc/src/Text/Pandoc/Options.hs',
         'upstream/pandoc/src/Text/Pandoc/App.hs',
         'upstream/pandoc/data/abbreviations',
         'upstream/pandoc/src/Text/Pandoc/Filter/JSON.hs',
         'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/ReaderOptions.hs',
         'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Marshal/WriterOptions.hs',
         'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Utils.hs',
         'upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc.lua']
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
parser=argparse.ArgumentParser();parser.add_argument('--check-source',action='store_true');args=parser.parse_args()
if args.check_source:
    data=json.loads(target.read_text());assert data['sources']==hashes
    print(f'Verified {len(data["choices"])} option JSON cases and {len(data["reader"])} reader configurations')
    raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
choices={
 'cite_method':['citeproc','natbib','biblatex'],
 'email_obfuscation':['none','references','javascript'],
 'track_changes':['accept','reject','all','accept-changes','reject-changes','all-changes'],
 'wrap_text':['auto','none','preserve','wrap-auto','wrap-none','wrap-preserve'],
 'top_level_division':['part','chapter','section','default','top-level-part','top-level-chapter','top-level-section','top-level-default'],
 'reference_location':['block','section','document','end-of-block','end-of-section','end-of-document'],
 'figure_caption_position':['above','below'],
 'table_caption_position':['above','below'],
 'html_math_method':['plain','webtex','gladtex','mathml','mathjax','katex'],
}
cases=[]
for field,values in choices.items():
    for value in values+[v.upper() for v in values]+[v+' ' for v in values]+[None,True,False,0,1,{},[],{'value':'none'},'','unknown']:
        cases.append({'field':field,'input':value})
for method in ['plain','webtex','gladtex','mathml','mathjax','katex','unknown',None,42]:
    for url in [None,'','https://example.org/tex?q=','Привет 😀',False,5,[],{}]:
        cases.append({'field':'html_math_method','input':{'method':method,'url':url}})
    cases.append({'field':'html_math_method','input':{'method':method}})
reader_cases=[{'name':'library defaults','args':None},
 {'name':'application defaults','args':[]},
 {'name':'columns and tabs','args':['--columns=66','--tab-stop=8']},
 {'name':'standalone','args':['--standalone']},
 {'name':'comments and image extension','args':['--strip-comments','--default-image-extension=png']},
 {'name':'code classes','args':['--indented-code-classes=kotlin,sourceCode,kotlin']},
 {'name':'reject changes','args':['--track-changes=reject']},
 {'name':'all changes','args':['--track-changes=all']},
 {'name':'typst inputs preserve repeated keys','args':['--typst-input=k=v','--typst-input=k=other','--typst-input=empty=','--typst-input=unicode=Привет 😀']},
 {'name':'extensions','args':['--from=native+east_asian_line_breaks']},
 {'name':'custom abbreviations','args':['--abbreviations=$ABBREVS']},
 {'name':'empty abbreviations','args':['--abbreviations=$EMPTY']},
 {'name':'large columns','args':['--columns=2147483648','--tab-stop=17']},
]
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory)
    request=temp/'request.json';request.write_text(json.dumps(cases))
    script=temp/'options.lua'
    script.write_text('''function Writer(doc, opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')));local input=pandoc.json.decode(f:read('*a'),false);f:close()
 for _, c in ipairs(input) do
  local o=c.field=='track_changes' and pandoc.ReaderOptions{} or pandoc.WriterOptions{}
  local ok,err=pcall(function()
   o[c.field]=c.input
   o=c.field=='track_changes' and pandoc.ReaderOptions(o) or pandoc.WriterOptions(o)
  end)
  c.accepted=ok
  if ok then c.expected=o[c.field] else c.error=tostring(err) end
 end
 return pandoc.json.encode(input)
end''')
    output={'sources':hashes,'choices':json.loads(subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',env={**os.environ,'KOTDOC_REQUEST':str(request)}))}
    # This executable copies the genuine Aeson JSON without a Lua table round trip.
    capture=temp/'capture.py'
    capture.write_text('#!/usr/bin/env python3\nimport os,sys\nfrom pathlib import Path\nPath(os.environ["KOTDOC_CAPTURE"]).write_text(os.environ["PANDOC_READER_OPTIONS"])\nsys.stdout.write(sys.stdin.read())\n')
    capture.chmod(0o755)
    script.write_text('function Writer(doc,opts) pandoc.utils.run_json_filter(doc,os.getenv("KOTDOC_FILTER"),{"native"});return "" end')
    (temp/'abbreviations').write_text('foo.\n😀.\n\ue000.\nfoo.\n')
    (temp/'empty').write_text('')
    captured=temp/'captured.json'
    for c in reader_cases:
        env={**os.environ,'KOTDOC_CAPTURE':str(captured),'KOTDOC_FILTER':str(capture)}
        if c['args'] is None:cmd=['pandoc','-f','native','-t',str(script)]
        else:cmd=['pandoc','-f','native','-t','native','--filter',str(capture),*[a.replace('$ABBREVS',str(temp/'abbreviations')).replace('$EMPTY',str(temp/'empty')) for a in c['args']]]
        p=subprocess.run(cmd,input=b'[]',capture_output=True,env=env)
        assert p.returncode==0,(c,p.stderr)
        c['expected']=json.loads(captured.read_text())
    output['reader']=reader_cases
output['htmlSlides']=[{'constructor':v,'expected':v.lower()} for v in ['S5Slides','SlidySlides','SlideousSlides','DZSlides','RevealJsSlides','NoSlides']]
# HTMLSlideVariant has no Lua property; these expectations come from its deriveJSON declaration.
target.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
print(f'Imported {len(cases)} option JSON cases and {len(reader_cases)} reader configurations')
