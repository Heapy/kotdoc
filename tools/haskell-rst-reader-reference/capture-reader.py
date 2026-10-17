#!/usr/bin/env python3
"""Independent source-driven RST requests, captured once from the full pinned module."""
import itertools,json,pathlib,subprocess,gzip
ROOT=pathlib.Path('/tmp/kotdoc-rst-reader');defaults=json.loads((ROOT/'default-options.json').read_text());qs=[]
def add(name,text='',**kw):qs.append(dict(name=name,input=text,**kw))
def opts(**kw):return dict(defaults,**kw)
labels=['include','table','list-table','csv-table','line-block','raw','role','container','replace','date','unicode','compound','pull-quote','epigraph','highlights','rubric','attention','caution','danger','error','hint','important','note','tip','warning','admonition','sidebar','topic','default-role','highlight','code','code-block','sourcecode','aafig','math','figure','image','bibliography','class','unknown']
for label in labels:
 for n,(top,fields,body)in enumerate([('heading','', 'body\n\nsecond'),('','', ''),('thing','   :name: named\n   :class: one two\n', '*emph*'),('thing','   :align: center\n   :width: 12px\n   :height: 2cm\n   :scale: 50%\n','caption\n\nlegend'),('','   :file: absent.rst\n',''),(' x','   :language: haskell\n   :format: html\n','  text')]):
  add(f'directive/{label}/{n}',f'.. {label}::{top}\n{fields}\n'+''.join('   '+s+'\n'for s in body.splitlines())+'\nend\n')
for label in ['replace','unicode','date','image','container','code','raw','unknown']:
 add('substitution/'+label,f'.. |sub| {label}:: 0x41\n\n|sub| and |sub|.\n')
for text in ['a','😀 title','\u1c89','x *y*','``x``','[1]_','A.B','a\tb']:
 for border in ['=','-','~','#']:
  for double in [False,True]:
   raw=(border*20+'\n'if double else'')+text+'\n'+border*20+'\n\n'+text+'\n'+border*20+'\n'
   for flags in [[],['auto_identifiers'],['auto_identifiers','ascii_identifiers'],['auto_identifiers','gfm_auto_identifiers']]:add(f'headers/{len(qs)}',raw,extensions=flags,options=opts(standalone=False))
for s in ['*x*','**x**','`x`','``x``',':code:`x`','`x`:emphasis:','`x <u>`_','[1]_','|x|','x_','x__','http://x.y','a@b.c','\\*','---','...','"x"',"'x'",'_`x`']:
 for pre in ['', 'a','(',"'",'😀','—',' ']:
  for post in ['', 'b',')',' ','.']:add(f'inline/{len(qs)}',pre+s+post+'\n',extensions=['smart']if pre=="'"else[])
for n in ['1','#','*','#label','CITE']:
 for blank in ['', '\n']:
  add(f'notes/{len(qs)}',f'foo [{n}]_ [{n}]_.\n\n.. [{n}]{blank}\n   note *text*\n\n.. [{n}] second\n')
for src in ['http://a.b','target_','`target`_','#here','data:image/png;base64,YQ==','a b','']:
 for keys in ['.. _a:', '.. _a:\n.. _b:', '.. __:', '__']:
  add(f'keys/{len(qs)}',f'{keys} {src}\n\na_ b_ a__ b__.\n')
for base in ['span','code','raw','math','emphasis','strong','superscript','subscript','title-reference','unknown']:
 for fields in ['', '   :class: one two\n','   :language: haskell\n   :format: html\n','   :class: a\n   :class: b\n   :language: x\n   :language: y\n   :format: x\n   :format: y\n']:
  add(f'roles/{len(qs)}',f'.. role:: new({base})\n{fields}\n:new:`x`\n')
for marker in ['*','+','-','•','‣','⁃','1.','(a)','A)','i.','(I)','#.','(@label)']:
 for body in ['one\n\n','one\n  continuation\n\n','one\n\n  nested\n\n','one\n\n  * nested\n\n']:
  add(f'list/{len(qs)}',marker+' '+body+marker+' two\n\nend\n')
for fields in ['', '   :widths: auto\n','   :widths: 1 2\n','   :header-rows: 2\n','   :header: "H1","H2"\n','   :delim: tab\n','   :quote: x\n','   :escape: \\\n','   :keepspace: true\n']:
 for body in ['a,b\nc,d','"a", "b"\nc,d','"a\nb",c','a\tb\nc\td',',\nx,','"bad']:
  add(f'csv/{len(qs)}','.. csv-table:: Caption\n'+fields+'\n'+''.join('   '+s+'\n'for s in body.splitlines())+'\n')
for widths in ['', 'auto','grid','1 2','0 -1','1, 2, 3']:
 fields=''if not widths else'   :widths: '+widths+'\n'
 for kind,body in [('table','+---+---+\n| H | J |\n+===+===+\n| a | b |\n+---+---+'),('list-table','* - H\n  - J\n* - a\n  - b')]:add(f'tables/{len(qs)}',f'.. {kind}:: Caption\n'+fields+'   :header-rows: 1\n\n'+''.join('   '+s+'\n'for s in body.splitlines())+'\n')
for top in ['', 'name', 'inc.rst']:
 for fields in ['', '   :literal:\n','   :code: haskell\n','   :start-line: 2\n   :end-line: 3\n','   :start-line: -3\n   :end-line: -1\n','   :start-after: BEGIN\n   :end-before: END\n']:
  add(f'include/{len(qs)}','.. include:: '+top+'\n'+fields+'\n',files=[['inc.rst',list(b'first\nBEGIN\n*included*\nEND\nlast\n')]])
add('include/nested','.. include:: a.rst\n\n',files=[['a.rst',list(b'.. include:: b.rst\n\n')],['b.rst',list(b'hello\n')]])
add('include/loop','.. include:: a.rst\n\n',files=[['a.rst',list(b'.. include:: a.rst\n\n')]])
for body in ['','abc\n','\ufeffhello\r\n','é\n']:
 add('raw-file/'+str(len(qs)),'.. raw:: html\n   :file: raw.txt\n\n',files=[['raw.txt',list(body.encode())]])
for chars in ['0x41 x42 \\x43 U+44 u45 \\u46 &#x47;','0x110000 xD800 x xyz','abc .. ignored\nnext','x41 x1F600','0x 0xG x12345678901234567890']:
 add('unicode/'+str(len(qs)),chars,kind='unicode')
for raw in ['', '\n','x','x\n\ny\n','x\n\n\ny\n\n','a \\\\ b\n\nc','\n \n x\n']:
 add('chunks/'+str(len(qs)),raw,kind='chunks')
for a in [[],[0],[1],[0,1],[0,3,6],[0,3,4,9],[-1,0,4]]:
 for b in [[],[0],[1],[0,1],[0,3],[0,6],[0,3,6],[0,4,9]]:add('spans/'+str(len(qs)),kind='col-spans',indices=a,spans=b)
for i in [[],[0],[1],[0,2],[0,5,9],[0,5,8],[-1,0,4]]:
 for cols in [-1,0,1,4,80]:add('widths/'+str(len(qs)),kind='widths',indices=i,options=opts(columns=cols))
# Read-only failure probes and original Sources chunk/position behavior.
for line,col in [(1,1),(4,7),(2147483648,2147483648)]:
 add('chunks-position/'+str(len(qs)),chunks=[['doc.rst',line,col,'.. warning:: hi\n\n   body\n\n'],['tail.rst',5,2,'missing_\n']])
path=ROOT/'reader-observations.jsonl';assert not path.exists(),path
p=subprocess.run([str(ROOT/'full-oracle')],input=('\n'.join(json.dumps(q,ensure_ascii=False)for q in qs)+'\n').encode(),capture_output=True,check=True)
rs=[json.loads(x)for x in p.stdout.splitlines()];assert len(rs)==len(qs)
path.write_text(''.join(json.dumps({'request':q,'expected':r},ensure_ascii=False,separators=(',',':'))+'\n'for q,r in zip(qs,rs)))
(ROOT/'reader-capture-stderr.log').write_bytes(p.stderr)
print(len(rs),'independent source/runtime/helper observations captured',flush=True)
