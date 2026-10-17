#!/usr/bin/env python3
"""Capture independent original parser/helper observations; never overwrite payloads."""
import gzip,json,pathlib,subprocess
HERE=pathlib.Path(__file__).resolve().parent;OUT=HERE/'fixtures/helpers.jsonl.gz';assert not OUT.exists()
requests=[]
def add(mode,inputs):
 for value in inputs:requests.append(dict(mode=mode,input=value))
base=['','\n','\r\n','x','x\n','\tx\n',' \n','  \nrest','\t','😀\n','Ж x\n']
for mode in ['hline','headerStart','tableStart','latexEnvStart','bulletListStart','orderedListStart','drawerStart','metaLineStart','commentLineStart','exampleLineStart','noteMarker','endOfBlock','anyLine','newline','blanklines','argKey','argWord','tagWord','anchor']:add(mode,base)
add('hline',['-----\n','  ------ \nrest','----\n','-----x\n','\t-----\t\n'])
add('headerStart',['* x\n','**  x','***\tx\n',' * x\n','**** \n'])
add('tableStart',['| x |\n','  |','\t|x'])
add('latexEnvStart',['\\begin{equation}\n','\\begin{gather*}\n','\\begin{align}\n','\\begin{foo}\n','\\begin{foo*}x','\\begin{😀}\n'])
add('bulletListStart',['- x\n','* x\n',' * x\n','+\n','- [@4] x','- [@a] x','- [@Z] x','- [@18446744073709551616] x','\t- x'])
add('orderedListStart',['4. x\n','9)\n','a. x\n','A) x\n','1. [@4]x','3. [@a] x','0. x'])
for inp in ['a. x','Z) x','27. x','1. [@q] x']:requests.append(dict(mode='orderedListStart',input=inp,extensions=['Ext_fancy_lists']))
add('drawerStart',[':NAME:\n','  :N: \n','::\n',':a b:\n',':a\nb:\n'])
add('noteMarker',['[1]','[0]','[12]x','[fn:a]','[fn:a:b]','[fn:]','[fn:é]','[fn:a b]'])
add('anchor',['<<a>> ','<<α😀>>x','<<a b>>','<<>>','<<a/b>>\t'])
add('export',['','\n','unknown:x\n','H:0','H:3','H:18446744073709551616','H:-1',"'t",'d:("a""b")','d:(not "LOGBOOK")','d:("a" )'])
for key in ['^',"'",'*','-','\\n','arch','author','creator','d','e','email','f','p','tags','tex','todo','|']:
 add('export',[key+':'+v+'\n'for v in ['t','nil','NIL','nİl','()','{}','headline','verbatim','x','0']])
add('export',['arch:headline \n','tex:verbatim \n','^:{} \n',"H:3 author:nil creator:t email:nil -:nil ':t \\n:t f:() |:{}\n",'date:foo toc:t unknown:x x y\n'])
for mode in ['isImageFilename','cleanLinkText','translateLang','originalLang']:
 add(mode,['','x.png','X.PNG','/a.png','a.png?x','http://x.png','http://\u0000x.png','x\u0000.png','./a','../a','file:a','file:/a','file://a','C:\\a','C:a','\\\\host\\a','/a','a:b',':b','é:x','😀:x','C++','R','jupyter-python','emacs-lisp'])
lines=''.join(json.dumps(x,ensure_ascii=False)+'\n'for x in requests)
p=subprocess.run(['/tmp/kotdoc-org-reader/helper-oracle'],input=lines.encode(),capture_output=True,check=True);rows=p.stdout.splitlines();assert len(rows)==len(requests);OUT.write_bytes(gzip.compress(p.stdout,mtime=0));pathlib.Path('/tmp/kotdoc-org-reader/helper-capture.stderr').write_bytes(p.stderr);print('captured',len(rows),'independent original helper observations')
