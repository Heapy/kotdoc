#!/usr/bin/env python3
"""Development-only independent requests -> copied pinned original Org reader."""
import gzip,json,pathlib,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
TOOL=pathlib.Path(__file__).resolve().parent
OUT=TOOL/'fixtures'/'expanded.jsonl.gz'
if OUT.exists(): raise SystemExit('refusing to replace immutable expectations')
ORACLE=pathlib.Path('/tmp/kotdoc-org-reader/oracle')
EXT=['auto_identifiers','citations','special_strings','task_lists']
requests=[]
def add(name,text,**extra): requests.append(dict(name=name,input=text,extensions=EXT,**extra))
# Source state changes must be tested before and after the affected content.
settings={'arch':['t','nil','headline','garbage'],'d':['nil','t','("A")','(not "A")','("A""B")'], 'H':['0','1','2','-1','18446744073709551617'], '\\n':['t','nil'], '^':['nil','{}','t'], '*':['t','nil'], '-':['t','nil'], "'":["t","nil"], 'author':['t','nil'], 'creator':['t','nil'], 'email':['t','nil'], 'e':['t','nil'], 'f':['t','nil'], 'p':['t','nil'], 'tags':['t','nil'], 'tex':['t','nil','verbatim','garbage'], 'todo':['t','nil'], '|':['t','nil'], 'unknown':['t','nil','word']}
body='''#+title: A /title/
#+author: First
#+creator: Creator
#+email: x@y
* TODO Heading :A:ARCHIVE:
SCHEDULED: <2020-01-02>
word_1 x^{2} /emph/ -- ... 'quote' \\alpha{} [fn:note]
next
:A:
drawer
:END:
| a | b |
|---+---|
| c | d |
[fn:note] A note.
'''
for key,values in settings.items():
 for value in values:
  for where in ['before','after']:
   setting=f'#+OPTIONS: {key}:{value}\n'
   add(f'option/{key}/{value}/{where}',setting+body if where=='before'else body+setting)
for key in ['title','subtitle','author','date','description','keywords','institute','header-includes','nocite','bibliography','creator','email','language','latex_class','latex_class_options','latex_header','latex_header_extra','html_head','html_head_extra','unknown','result','seq_todo','todo','typ_todo']:
 for value in ['', 'plain', '/marked/ and [[https://example.org][link]]', 'A\n#+%s: B'%key]:
  add(f'meta/{key}/{value}',f'#+{key}: {value}\ntext\n')
for tag1 in ['',':export:',':noexport:',':ARCHIVE:',':A:']:
 for tag2 in ['',':export:',':noexport:',':ARCHIVE:',':A:']:
  for prefix in ['', '#+SELECT_TAGS: A\n', '#+EXCLUDE_TAGS: A\n']:
   add(f'tree/{tag1}/{tag2}/{prefix}',prefix+f'Root text\n* Parent {tag1}\nparent\n** Child {tag2}\nchild\n*** Grandchild\nlast\n')
for properties in [':ID: x',':CUSTOM_ID: x',':CLASS: a b',':UNNUMBERED: nil',':UNNUMBERED: t',':ID: a\n:ID: b',':CUSTOM_ID: a\n:ID: b',':odd: value']:
 add('properties/'+properties,':PROPERTIES:\n'+properties+'\n:END:\n* Heading\n:PROPERTIES:\n'+properties+'\n:END:\ntext\n* Heading\n')
for type_ in ['quote','verse','example','src','html','latex','ascii','comment','abstract','Abstract','note','warning','tip','caution','important','custom']:
 for header in ['', ' -i', ' -n 4', ' C :exports both', ' :id foo :class bar', ' x=y', ' raw text']:
  for content in ['a\nb\n','  a\n\tb\n\n',',* header\n,,#+keyword: x\n']:
   add(f'block/{type_}/{header}/{content}',f'#+caption: Caption\n#+name: name\n#+begin_{type_}{header}\n{content}#+end_{type_}\n')
for switch in ['-n','+n','-n 12','-n -2','-i','-l "label"','-x','+x','-n 3 -i','-n 3 +n 5']:
 for export in ['code','results','both','none']:
  add(f'code/{switch}/{export}',f'#+begin_src python {switch} :exports {export}\n  x\n#+end_src\n\n#+RESULTS:\n: y\n')
for text in ['a | b','<l> | <r2>','<2> | <0>','<9223372036854775808> | <1>',' | ','x | y | z']:
 for marker in ['', '|---+---|\n', '|<l>|<r>|\n']:
  add(f'table/{text}/{marker}',f'#+caption: Caption\n#+name: id\n|{text}|\n'+marker+'| body | next |\n')
for target in ['https://example.org/x?q=a','./x.png','../x.PNG','file:x.jpg','/tmp/x.pdf','C:\\x.jpg','unknown','manual','#manual','', 'x:y','custom:arg']:
 for description in ['', 'Title /emphasis/', './x.png']:
  add(f'link/{target}/{description}',f'[[{target}][{description}]] [[{target}]]\n<<manual>>\n#+LINK: custom https://example.org/%s\n')
for delim in ['*','/','+','_','=','~','$']:
 for content in ['x','a b',' x','x ','a\nb','a\nb\nc','a\nb\nc\nd', '/nested/', 'a\u200bb']:
  for surround in [('', ''),('x','x'),('(',')')]:
   add(f'inline/{delim}/{content}/{surround}',surround[0]+delim+content+delim+surround[1]+'\n')
for raw in ['[cite:@one]','[cite/text:@one;@two]','[cite/noauthor:pre;@one suf;post]','[cite/foo:@x]','cite:a,b,','[[citep:a][pre:: suf]]','[cite/t/caps-full: @x]','[cite/n/bcf:@a]']:
 for ext in [EXT, [e for e in EXT if e!='citations']]: add('citation/'+raw+'/'+str(ext),raw,extensions_override=ext)
# Resolve all final-state references, missing references and note self-recursion.
for text in ['[fn:x]\n\n[fn:x] note\n','[fn:x:a [fn:x]]\n','[fn:x]\n\n[fn:x] [fn:y]\n[fn:y] [fn:x]\n', '{{{m(x)}}}\n#+MACRO: m <$1/$2>\n','#+MACRO: a {{{a($1)}}}\n{{{a(x)}}}\n','#+TODO: |\n* TODO t\n', '#+TODO: A B | C D\n* B t\n* D t\n']:
 add('future/'+text,text)
for raw in ['\\alpha{}','\\today','\\textbf{bold}','\\unknown{x}','\\(a+b\\)','\\[a+b\\]','$$a$$','\\begin{equation}x\\end{equation}\n']:
 for setting in ['t','nil','verbatim']:add(f'tex/{raw}/{setting}',f'#+OPTIONS: tex:{setting}\n'+raw+'\n')
for file in ['a.org','../a.org','missing.org','bad.org']:
 for args in ['', ' example',' src python',' export html',' :lines "1-3"',' :lines "-3--1"',' :minlevel 2']:
  add(f'include/{file}/{args}',f'#+INCLUDE: "{file}"{args}\n',files=[{'path':'a.org','bytes':list(b'* A\ntext\n** B\nlast\n')},{'path':'bad.org','bytes':[255]}])
for request in requests:
 if 'extensions_override' in request:request['extensions']=request.pop('extensions_override')
# Bind every record to its exact request. Expected values come only from original.
raw=''.join(json.dumps(q,ensure_ascii=True,separators=(',',':'))+'\n'for q in requests).encode()
pathlib.Path('/tmp/kotdoc-org-reader/expanded-requests.jsonl').write_bytes(raw)
result=subprocess.run([str(ORACLE)],input=raw,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
pathlib.Path('/tmp/kotdoc-org-reader/expanded-oracle.stderr').write_bytes(result.stderr)
answers=[json.loads(line)for line in result.stdout.splitlines()]
assert len(answers)==len(requests)
with gzip.GzipFile(filename=str(OUT),mode='wb',mtime=0)as out:
 for q,a in zip(requests,answers):
  assert q['name']==a.pop('name')
  out.write((json.dumps({'request':q,'expected':a},ensure_ascii=True,separators=(',',':'))+'\n').encode())
print(f'captured {len(requests)} independent original Org observations')
