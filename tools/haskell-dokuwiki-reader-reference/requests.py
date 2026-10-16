# Independent source-branch input recipes; no Kotlin import or execution.
import random

def original_options(name):
    if not name.startswith('math groups and comments/'): return []
    group=name.split('/')[1]
    return ['tex_math_dollars' if group in ['dollars','double dollars'] else 'tex_math_single_backslash' if group in ['parentheses','brackets'] else 'tex_math_double_backslash']

def requests(original,defaults):
    cases=[]
    def add(name,input,**options):cases.append(dict(name=name,input=input,**options))
    for n,record in enumerate(original):
        for extensions in [original_options(record['name']),['auto_identifiers'],['smart','old_dashes'],['auto_identifiers','ascii_identifiers','gfm_auto_identifiers','smart']]:
            add('original-options/'+str(n)+'/'+','.join(extensions),record['input'],extensions=extensions)
    branches={
      'empty':['','\n','\n\n',' \t','\r','\r\n','\t\n\t\n','\u00a0','a\r\nb','a\n\nb','a\n \n b','a\nb\n'],
      'format':['****','** x**','**x **','**x\n y**','**a //b// c**','**x','a//b//c','%% x%%','%%x %%','%%%%','%%a\tb\nc%%',"''**bold**''",'<nowiki>a\n\n b</nowiki>','<nowiki>unterminated','<sub>x</sub>','<sub> x</sub>','((a\n b))','((<code>x</code>))','((one))((two))'],
      'raw':['<html>x</html>','<php>x</php>','<latex>x</latex>','<HTML>\n x</HTML>','<PHP> \n x</PHP>','<LATEX>x</LATEX>','<Html>x</Html>','<html>x','<HTML> x</HTML>tail'],
      'code':['<code></code>','<file - name>text</file>','<code java title> \ntext\n</code>','<code  java>text</code>','<code\tpython>text</code>','<code\npython>text</code>','<code java>  text</code>','<file filename>text</file>','foo<code>x</code>bar','<code>x','<code>x</file>','<file>x</code></file>'],
      'header':['= x ==','======= x ==','======x==tail','=== a **b** ===','== É 😀 text ==\n== É 😀 text ==','=== ((note)) ===','=== <code>x</code> ===','  == x ==','=== x\ncontinued ==','====== x ='],
      'list':['  * x\n  * y','   * x\n   * y','    * x','  - x\n    * y\n    - z\n  - q','  * x<code>z</code>\n  * y','  * x\n\n  * y','  * \n','  * x\n    code','  * x\n     - y','  * <code>\n x\n</code>\n'],
      'quote':['> a\n> b\n','>> a\n> b\n>> c','> a\n> <code>x</code>\n> b','> <code>x</code>\n> <code>y</code>\n','>\n','>>> a\n>> b\n> c','> a\nnot quoted','> ** a**','> a\n> <code>x</code>\n'],
      'table':['|x|y|\n','^ x ^ y ^\n|a|b|\n','|  a|b  |  c  | d |\n','|x| trailing **garbage**\n','|x|\n^y^z^\n','|','||','|x','|x\n','|a|\n|b','|<code>x</code>|\n','|((n))|\n','|a\\\\ b|\n','|a\n b|\n','|a|\n\n|b|\n'],
      'entity':['&amp; &#65; &#x1f600; &#0; &#55296; &#999999999999999999999;','&bogus; &amp &#; &#x;','a &NotEqualTilde; b','&lt;http://x&gt;'],
      'link':['[[x]]','[[x|]]','[[ x | a  b ]]','[[]]','[[x|a|b]]','[[a\nb]]','[[x|**literal**]]','[[x','[[x]]tail','[[a://b]]','[[://x]]'],
      'smart':['"one" \'two\' aren\'t -- --- ...','"not closed','x\'s','(("quote"))','=== "heading" ==='],
    }
    extensions=[[],['smart'],['auto_identifiers','ascii_identifiers'],['tex_math_dollars','tex_math_single_backslash','tex_math_double_backslash']]
    for family,inputs in branches.items():
        for n,input in enumerate(inputs):
            for exts in extensions:add(f'branch/{family}/{n}/'+','.join(exts),input,extensions=exts)
    paths=['page','ns:page',':ns:page','.page','..page','...:page','.:ns:page','..:page','./page','/path','a:b','a://b','𐐀>page','𐐀://x','wp>page','WP>page','>page','a.b>page']
    prefixes=['callto','doku','phpfn','tel','wp','wpde','wpes','wpfr','wpjp','wppl','unknown']
    for n,path in enumerate(paths+[p+'>a:b' for p in prefixes]):
        add(f'path/{n}','[['+path+']] {{'+path+'}}')
    for n,param in enumerate(['','?100','?100x200','?0x0','?x100','?100x','?100x1x2','?50%','?١٠','?100&200x300','?linkonly&100x200','?200&linkonly','?foo=1&x=2','?','?x100&80x40']):
        for pad in ['none','left','right','both']:
            path=(' ' if pad in ['left','both'] else '')+'ns:pic'+param+(' ' if pad in ['right','both'] else '')
            add(f'image/{n}/{pad}','{{'+path+'| alt }}')
    urls=['http://x','HTTP://x/a.','http://x/a_b','http://x/a(b)','http://x/a{b}','http://x/a[b]','http://x/%%//%%b','http://x/a,','http://x/a?b=c','http://x/a%20b','http://x/a&amp;b','http://x/😀','http://x/𐐀','unknown:x','tel:+123','İrc:x','Keyparc:x','<a.b+c@example-domain.com>','<a..b@example.com>','<a@-a.com>','<a@a-.com>']
    for n,url in enumerate(urls):add(f'url/{n}',url)
    for n,chunks in enumerate([[['first',4,3,'== x ==\n'],['second',20,1,'|a|\n']],[['chunk',1,1,'a\n\n'],['chunk',5,1,'> b\n']],[['one',1,1,'**a'],['two',10,1,'b**']],[['empty',9,7,'']],[['tab',2,4,'|bad']]]):
        cases.append(dict(name=f'sources/{n}',chunks=chunks,extensions=['auto_identifiers']))
    opts=dict(defaults);opts.update({'standalone':True,'columns':17,'tab-stop':9,'indented-code-classes':['ignored'],'default-image-extension':'png','strip-comments':True})
    add('options/all-fields','=== É ===\n\n  code\n\n{{image}}\n<html>raw</html>',options=opts,extensions=['smart','auto_identifiers'])
    rng=random.Random(618115)
    atoms=['x',' ','\n','\t','**','//','__',"''",'%%','((','))','[[',']]','{{','}}','|','^','=','>','  * ','<code>','</code>','<html>','</html>','&amp;','$x$','~~NOTOC~~','http://x/']
    for n in range(300):add(f'bounded-mixed/{n}',''.join(rng.choice(atoms) for _ in range(rng.randrange(1,18))),extensions=['smart','tex_math_dollars','auto_identifiers'])
    return cases

def deep_requests():
    specs=[('subscript/1024','<sub>'*1024+'x'+'</sub>'*1024),('note/512','(('*512+'x'+'))'*512),('quote/4096','>'*4096+' x\n'),('list/256',''.join('  '*n+'* x\n' for n in range(1,257))),('table/1000','|a|b|\n'*1000),('symbol/10000','!'*10000),('percent/20000','%%'+'x'*20000+'%%')]
    return [dict(name='deep/'+name,input=text,observeShow=True) for name,text in specs]

def trace_requests():
    return [dict(name='trace/plain',input='a\n\nb\n',trace=True),dict(name='trace/quote',input='> a\n> b\n',trace=True),dict(name='trace/lookahead',input='text\n== head ==\n\n  * item\n',trace=True,extensions=['auto_identifiers']),dict(name='trace/chunks',chunks=[['chunk',3,1,'x\n\n'],['named',8,1,'|x|\n']],trace=True),dict(name='trace/nul',input='x\x00',trace=True)]

def unicode_requests(ranges):
    points=''.join(chr(p) for start,end in ranges for p in range(start,end+1));result=[]
    for n,exts in enumerate([['auto_identifiers'],['auto_identifiers','ascii_identifiers'],['auto_identifiers','gfm_auto_identifiers'],['auto_identifiers','gfm_auto_identifiers','ascii_identifiers']]):
        for name,content in [('all-new',points),('surrounded','prefix'+points+'suffix'),('one','\U0001e6c0')]:result.append(dict(name=f'unicode17/{name}/{n}',input=f'=== {content} ===\n',extensions=exts))
    return result
