#!/usr/bin/env python3
"""Exercise the installed Kotlin CLI against independent original HTML results."""
import argparse,difflib,gzip,json,subprocess,tempfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--original-goldens-only',action='store_true');settings=parser.parse_args()
cli=root/'cli/build/install/cli/bin/cli'
extension_tables=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/extensions.json').read_text())['supported']
with gzip.open(root/'core/src/test/resources/html-writer/data.jsonl.gz','rt') as f:
 manifest=json.loads(next(f));cases=[json.loads(line) for line in f]
assert manifest['originalUnitCount']==17
count=0
with tempfile.TemporaryDirectory(prefix='kotdoc-html-cli-') as tmp:
 def check(case,index):
  global count
  fmt,document,opts=case['request']
  unsupported=[e for e in opts.get('extensions',[]) if e not in extension_tables.get(fmt,extension_tables['_'])]
  if unsupported:
   # Library records accept arbitrary extensions; the original CLI validates its
   # format registry first. Preserve the original rejection instead of relaxing it.
   assert unsupported==['literate_haskell']
   r=subprocess.run([str(cli),'-f','json','-t',fmt+'+lhs'],input=b'',capture_output=True,cwd=root)
   expected=("The extension 'literate_haskell' is not supported for "+fmt+".\nUse --list-extensions="+fmt+" to list supported extensions.\n").encode()
   assert r.returncode==23 and r.stdout==b'' and r.stderr==expected,(index,r.returncode,r.stdout,r.stderr)
   count+=1
   return
  # The observation protocol defaults to WrapNone; Pandoc's CLI defaults to Auto.
  args=[str(cli),'-f','json','-t',fmt,'--wrap=none']
  payload=json.dumps(document).encode()
  if case['group']=='original-fixed-golden':
   args[2]='native';payload=(root/'upstream/pandoc/test'/case['source']).read_bytes()
  names={'wrapText':'wrap','tableOfContents':'toc','listOfFigures':'lof','listOfTables':'lot','incremental':'incremental','numberSections':'number-sections','numberOffset':'number-offset','sectionDivs':'section-divs','identifierPrefix':'id-prefix','htmlQTags':'html-q-tags','slideLevel':'slide-level','referenceLocation':'reference-location','preferAscii':'ascii','emailObfuscation':'email-obfuscation','highlightMethod':'syntax-highlighting','tocDepth':'toc-depth','columns':'columns','setextHeaders':'markdown-headings','figureCaptionPosition':'figure-caption-position','tableCaptionPosition':'table-caption-position'}
  for key,value in opts.items():
   if key in names and value is not None:
    if isinstance(value,bool):value=str(value).lower()
    if key=='setextHeaders':value='setext' if value=='true' else 'atx'
    if key=='wrapText':value=value.removeprefix('wrap-')
    if key=='numberOffset':value=','.join(map(str,value))
    args.append('--'+names[key]+'='+str(value))
   elif key=='mathMethod':
    value=value if isinstance(value,str) else value['method']+':'+value['url']
    args.append('--math-method='+value)
   elif key=='extensions':
    assert value in [[],['literate_haskell']]
    if value:args[4]+='+lhs'
   elif key=='defaultTemplate':args.append('-s')
   elif key=='template':
    path=Path(tmp)/'fixture.html';path.write_text(value)
    args+=['-s','--template',str(path)]
   elif key=='variables':
    for name,v in value.items():
     if case.get('source')=='s5.native' and name in ['header-includes','include-before','include-after','css']:continue
     args+=['--variable',name+'='+v] if isinstance(v,str) else ['--variable-json',name+'='+json.dumps(v)]
   else:assert key in names,(key,value)
  # --number-offset enables numbering; reproduce the final library record field.
  if 'numberSections' in opts:args.append('--number-sections='+str(opts['numberSections']).lower())
  # These command-level inputs exercise original include/CSS options too.
  if case.get('source')=='s5.native' and 'header-includes' in opts.get('variables',{}):
   for flag in ['-H','-B','-A']:args += [flag,str(root/'upstream/pandoc/test/insert')]
   args+=['-c','main.css']
  # Preserve literal CR in HTML attributes; text=True performs newline conversion.
  result=subprocess.run(args,input=payload,capture_output=True,cwd=root)
  assert result.returncode==0,(index,args,result.stderr)
  expected=case['expected']['output']
  if 'defaultTemplate' not in opts and 'template' not in opts and not expected.endswith('\n'):expected+='\n'
  actual=result.stdout.decode()
  assert actual==expected,(index,case['group'],args,''.join(difflib.unified_diff(expected.splitlines(True),actual.splitlines(True))))
  count+=1
  if count%25==0:print('Verified',count,'installed HTML CLI cases',flush=True)
 selected=[]
 for i,c in enumerate(cases,1):
  if c['group'] in ['original-html-unit','original-fixed-golden']:selected.append((i,c))
 # One representative of every available HTML writer and each branch family;
 # use all obfuscation methods, math methods, and wrap modes across the selection.
 seen=set()
 groups={'all-api-wrap-prefix-quotes','span-like-and-csl','raw-format-math-gates','email-obfuscation','media-categories-dimensions','sections-slides-and-notes','figures-and-alt-captions','highlighting-block-inline','math-methods-and-fallback','ascii-entry-points','nested-note-state-and-identifiers'}
 for i,c in enumerate(cases,1):
  if c['group'] not in groups:continue
  fmt,_,opts=c['request']
  if fmt in ['html4-markup','html5-markup','epub2','epub3'] or 'template' in opts:continue
  key=(c['group'],fmt,json.dumps(opts.get('mathMethod','')),opts.get('emailObfuscation'),opts.get('wrapText'))
  if key not in seen:seen.add(key);selected.append((i,c))
 if settings.original_goldens_only:selected=[(i,c) for i,c in selected if c['group']=='original-fixed-golden']
 for i,c in selected:check(c,i)
 # Exact original Pandoc 3.12.1 observations for CLI ordering and final newline.
 template=Path(tmp)/'variables.html'
 template.write_text('$for(css)$$css$;$endfor$|$title-prefix$|$for(x)$$x$;$endfor$')
 regressions=[
  (['-c','first.css','-V','css=variable.css','-c','second.css','-T','old','-T','new'],b'variable.css;first.css;second.css;|oldnew|'),
  (['-V','css=variable.css','-c','first.css','-c','second.css','-T','new'],b'variable.css;first.css;second.css;|new|'),
  (['-T','old','-V','title-prefix=user','-T','new'],b'|oldusernew|'),
  (['-V','x=old','--variable-json','x=["a","b"]','-V','x=last'],b'||a;b;last;')]
 document=json.dumps({'pandoc-api-version':[1,23,1,2],'meta':{},'blocks':[]}).encode()
 for args,expected in ([] if settings.original_goldens_only else regressions):
  r=subprocess.run([str(cli),'-f','json','-t','html5','-s','--template',str(template)]+args,input=document,capture_output=True,cwd=root)
  assert r.returncode==0 and r.stdout==expected and r.stderr==b'',(args,r.returncode,r.stdout,r.stderr,expected)
  count+=1
print('Verified',count,'original HTML CLI goldens' if settings.original_goldens_only else 'installed HTML CLI cases, including all 17 original units, 16 original fixed goldens and 4 original CLI ordering/newline regressions')
