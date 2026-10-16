"""Independent source-branch recipes; no Kotlin output is read."""
import random
VARIANTS=[[],['amuse'],['auto_identifiers'],['amuse','auto_identifiers'],['amuse','auto_identifiers','ascii_identifiers'],['amuse','auto_identifiers','gfm_auto_identifiers','ascii_identifiers']]
def requests(original):
 result=[]
 def add(name,text,exts=None,**kwargs):result.append(dict(name=name,input=text,extensions=['amuse'] if exts is None else exts,**kwargs))
 for r in original:
  for n,exts in enumerate(VARIANTS):add(f'original-variant/{n}/{r["name"]}',r['input'],exts)
 samples=['','#title\n','#title ','#title x\n#title y\n','#title x[1]\n\n[1] final\n','#cover x\n#cover-image y\n','#- x\n','#💡 x\n','; comment\n#id\n* A\n#id\n* B\n','* É\n\n* É\n','* [1]\n\n[1] x\n','x[1]\n\n[1] a\n\n[1] b\n','x{1}\n\n{1} {2}\n\n{2} {1}\n','x[1]\n\n[1] [2]\n\n[2] [1]\n','[1] unreferenced\n\n[1] duplicate\n','[1] x[1]\n','[01] bad\n','[0] bad\n','[123456789012345678901234567890] value\n',' x :: y\n :: z\n',' 1. x\n II. y\n z. q\n',' +--+\n |x |\n +--+\n','+--+\n|x |\n+--+\n|y |\n+--+\n','+-----+\n|#x y |\n+-----+\n','|+ first +|\n|+ second +|\n','a || b\nc ||| d\ne | f\n','| \n','a |b\n','a \t| b\n','a\r\nb\r','x\x00','\x00','**a*','*a**','*a_','*a\nb*','*a\n\nb*','<class name="a b" name="c" id="ignored">x</class>','<div id="first" id="second" class="a\u00a0b" x="q">\nx\n</div>','<code id="x" class="a b" foo="bar">x</code>','<example id="x" class="a b" foo="bar">\n x\n</example>','<literal style="latex">x</literal>','<literal>\nx\n</literal>','<verbatim>\n**x**\n</verbatim>','<<<a >>>b<<< c>>>','[[URL:x.png][[1]]]','[[x.png 50 f][a[1]]]','[[x.PNG]]','[[x.png 5.0 r]]','[[x.png 50 r extra]]','>\n> \n>  x\n','<verse>\n\n *x*\n</verse>','{{{\n\t x\n\t\n\t y\n}}}','<quote>\n - x\n\n   </quote>\n\n   y\n</quote>','<quote>\n<verse>\n</quote>\n</verse>','\t- x\n\t  - y\n',' - x\n\n\n - y\n',' - [1] x\n\n[1] note\n']
 for n,text in enumerate(samples):
  for v,exts in enumerate(VARIANTS):add(f'branch/{n}/{v}',text,exts)
 for n in range(1,18):
  for v,exts in enumerate([[],['amuse']]):
   add(f'note-cycle/{n}/{v}','start[1]\n\n'+'\n\n'.join(f'[{i}] text[{i%n+1}]' for i in range(1,n+1))+'\n',exts)
 for ext in ['.eps','.gif','.jpg','.jpeg','.pbm','.png','.tiff','.xbm','.xpm']:
  for suffix in ['', ' 42', ' r',' l',' f',' 42 f',' 42 r','\t42\tl']:
   add(f'image/{ext}/{suffix!r}',f'[[file name{ext}{suffix}][desc]]')
 for n,chunks in enumerate([[['first',3,4,'a '],['second',10,1,'| b\n']],[['empty',3,1,''],['chunk',1,1,'#title x\n\n* y\n']],[['one',1,1,'<em>'],['two',8,2,'x</em>']],[['chunk',4,1,'x[1]\n\n[1] a\n\n[1] b\n']]]):result.append(dict(name=f'sources/{n}',chunks=chunks,extensions=['amuse','auto_identifiers']))
 for stop in [0,1,4,8,9,-1]:add(f'options/tab-stop/{stop}','\t- x\n\t  - y\n',options={'extensions':[],'standalone':True,'columns':17,'tab-stop':stop,'indented-code-classes':['ignored'],'abbreviations':['x'],'default-image-extension':'png','track-changes':'reject-changes','strip-comments':True,'typst-inputs':[['x','y']]})
 rng=random.Random(618250);atoms=['a','b',' ','\n','\t','*','**','***','_','=','[1]','{1}','[[',']]','[',']','<em>','</em>','<quote>','</quote>','; ','#title ',' - ',' 1. ','|','||','|+','+|','{{{','}}}','~~','\x00','💡']
 for n in range(500):add(f'bounded-mixed/{n}',''.join(rng.choice(atoms) for _ in range(rng.randrange(1,20))),[] if n%2 else ['amuse','auto_identifiers'])
 return result

def deep_requests():
 def letters(n):
  result=''
  while True:
   result=chr(97+n%26)+result;n=n//26-1
   if n<0:return result
 texts=[('inline/4096','<sub>'*4096+'x'+'</sub>'*4096),('quote/1024','<quote>\n'*1024+'x\n'+'</quote>\n'*1024),('notes/1024','start[1]\n\n'+'\n\n'.join(f'[{n}] x'+(f'[{n+1}]' if n<1024 else '') for n in range(1,1025))+'\n'),('lists/256',''.join(' '*n+'- x\n' for n in range(1,513,2))),('blocks/10000','x\n\n'*10000),('table/2000','a | b\n'*2000),('metadata/2048',''.join(f'#key{letters(n)} value\n' for n in range(2048))),('symbols/20000','!'*20000),('directive/inline2048','#title '+'<sub>'*2048+'x'+'</sub>'*2048+'\n')]
 return [dict(name='deep/'+name,input=text,extensions=['amuse'],observeShow=True) for name,text in texts]

def trace_requests():
 return [dict(name='trace/emacs-notes',input='x[1]\n\n[1] a\n\nb\n',trace=True),dict(name='trace/emacs-nested',input='x[1]\n\n[1] <quote>\na\n</quote>\n',trace=True),dict(name='trace/amuse-no-parseBlock',input='a\n\n - b\n',extensions=['amuse'],trace=True),dict(name='trace/duplicate',input='x[1]\n\n[1] a\n\n[1] b\n',trace=True)]

def extra_requests(unicode_ranges,defaults):
 result=[]
 def add(name,text,exts=None,**kwargs):result.append(dict(name=name,input=text,extensions=['amuse'] if exts is None else exts,**kwargs))
 for style,markers in [('decimal',['0','1','99999999999999999999999999999999999']),('upperroman',['I','IV','XXXIX']),('lowerroman',['i','iv','xxxix']),('upperalpha',['A','B','Z']),('loweralpha',['a','b','z'])]:
  for first in markers:
   for mode in [[],['amuse']]:
    for gap in ['\n','\n\n','\n\n\n']:add(f'list-style/{style}/{first}/{mode}/{len(gap)}',f' {first}. a'+gap+f' {first}. b\n',mode)
 for n,content in enumerate(['123 École 😀 foo','İ I ı i K Σ ς σ','a\u0301 a_._b','\u0085\u00a0\u2003\u2028\u2029',''.join(chr(p) for a,b in unicode_ranges for p in range(a,b+1))]):
  for mode in [[],['amuse']]:
   for ids in [['auto_identifiers'],['auto_identifiers','ascii_identifiers'],['auto_identifiers','gfm_auto_identifiers'],['auto_identifiers','gfm_auto_identifiers','ascii_identifiers']]:add(f'identifier/{n}/{mode}/{ids}',f'* {content}\n\n* {content}\n',mode+ids)
 for n,space in enumerate([' ','\t','\n','\r','\x1c','\x85','\xa0','\u1680','\u2000','\u2007','\u2028','\u202f','\u205f','\u3000']):
  add(f'attributes/words/{n}',f'<div id="x" class="a{space}b" id="y" extra="v">\ntext\n</div>\n')
  add(f'inline/whitespace/{n}',f'a{space}*b*{space}=c=')
 for n,text in enumerate(['#key- value\n','#cover a\n#cover-image b\n','#title [1]\n\nx[1]\n\n[1] final\n',';x\n#é\n* x\n#é\n* y\n',';x\n#id\n* x\n\n* x\n','a[1][2]\n\n[2] old\n\n[1] inner[2]\n\n[2] new\n','<code a b="x" class="a b">t</code>','<div x="a\nb">\nx\n</div>','<sub>x\n\ny</sub>','<comment>x\n</comment>junk\n',' +---+---+\n |a  |b  |\n +---+---+\n','|+ first +|\n|+ last +|\n','[1] no reference\n\n[1] again\n','* title[1]\n\n[1] text\n',' - x\n\n   [1] note\n\npara[1]\n']):
  for mode in VARIANTS:add(f'state-and-tags/{n}/{mode}',text,mode)
 for n,chunks in enumerate([[['one',1,1,'a '],['two',5,2,'| b\n']],[['one',1,1,''],['chunk',7,1,'; x\n#id\n* y\n']],[['Ж😀',2,5,'#bad'],['new\nname',90,8,' no\n']],[['chunk',1,1,'x[1]\n\n[1] a\n\n[1] b\n']],[['one',1,1,'<example>\n'],['two',9,2,'x\n</example>\n']]]):result.append(dict(name=f'extra-sources/{n}',chunks=chunks,extensions=['amuse','auto_identifiers']))
 for col in [2147483647,2147483648,4294967296,9223372036854775807,-9223372036854775808]:
  for n,text in enumerate([' - x\n   - y\n',' - x\n\n   continued\n','> a\n> b\n','+--+\n|x |\n+--+\n']):
   result.append(dict(name=f'wide-position/{col}/{n}',chunks=[['first',1,1,'\n'],['wide',7,col,text]],extensions=['amuse']))
 for stop in [2147483648,4294967296,9223372036854775807,-9223372036854775808]:
  opts=dict(defaults);opts['tab-stop']=stop
  add(f'wide-tab-stop/{stop}','\t- x\n\t  - y\n',options=opts)
 return result

def stress_requests():
 return [dict(name='stress/notes4096',input='start[1]\n\n'+'\n\n'.join(f'[{n}] x'+(f'[{n+1}]' if n<4096 else '') for n in range(1,4097))+'\n',extensions=['amuse'],observeShow=True),dict(name='stress/quote4096',input='<quote>\n'*4096+'x\n'+'</quote>\n'*4096,extensions=['amuse'],observeShow=True)]
