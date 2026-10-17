"""Read-only-original expectation capture; never invokes Kotlin."""
import pathlib,json,subprocess,re,itertools
ROOT=pathlib.Path(__file__).resolve().parents[2];r=pathlib.Path('/tmp/kotdoc-org-writer');qs=[]
def st(s):return dict(t='Str',c=s)
def add(xs,kind='locator'):qs.append(dict(name=kind+'/'+str(len(qs)),kind=kind,input=xs))
def tokens(s):
 out=[]
 for text in re.split('( )',s):
  if text:out.append(dict(t='Space')if text==' 'else st(text))
 return out
source=(ROOT/'upstream/pandoc/src/Text/Pandoc/Writers/Org.hs').read_text();labels=re.findall(r'\("([^"]*)"\s*,\s*"([^"]*)"\)',source[source.index('locmap ='):])
for lab,_ in labels:
 for value in ['3','33-44','XIV','(a)','(iv)','3(a)','hello','1.2.3','{33}','[17]-[18]']:
  for suffix in ['',', more']:
   add(tokens(lab+' '+value+suffix))
for text in ['','23','33-44','xvii','1.2.3','1.2.','a.6','(1)(a)','{23}','{p. 23}','{chapter 3 [a]}','{{x}}','{p.}','{hello}','{  p.23}',', p. 4',' p. 4','\u00a0p.\u00a04','p.3','p. 3; 4','pp. 3, 4','p. ٣','p. ４','PAGE 33','PaGe 33','p. NaN','p. Infinity','1(2)(3)','p. (a)-(b)','p. (i)-(iv)','p. (some text in brackets)']:
  add(tokens(text));add([st(text)])
base=[st('x')]
variants=[dict(t=t,c=base)for t in ['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps']]
variants += [dict(t='Quoted',c=[dict(t=t),base])for t in ['SingleQuote','DoubleQuote']]
variants += [dict(t='Code',c=[['',[],[]],'x']),dict(t='Math',c=[dict(t='InlineMath'),'x']),dict(t='RawInline',c=['html','<b>x</b>']),dict(t='Note',c=[dict(t='Para',c=base)]),dict(t='Span',c=[['id',[],[]],base]),dict(t='Span',c=[['',['a','b'],[['style','baseline']]],base]),dict(t='Span',c=[['',[],[['style','baseline']]],base]),dict(t='Span',c=[['',[],[['style','baseline'],['x','y']]],base]),dict(t='Link',c=[['',[],[]],base,['target','title']]),dict(t='Image',c=[['',[],[]],base,['target','title']]),dict(t='Cite',c=[[],base]),dict(t='SoftBreak'),dict(t='LineBreak'),dict(t='Space'),st(''),st('x')]
for v in variants:
 for kind in ['csl','split','comma']:add([v],kind)
 for before,after in [('{','}'),('p. ','3'),('',''),('p. {','} tail')]:add(tokens(before)+[v]+tokens(after))
p=r/'locator-observations.jsonl';assert not p.exists();out=subprocess.run([str(r/'locator-oracle')],input=''.join(json.dumps(q,ensure_ascii=False)+'\n'for q in qs).encode(),capture_output=True,check=True);rows=[json.loads(l)for l in out.stdout.splitlines()];assert len(rows)==len(qs);p.write_text(''.join(json.dumps(dict(request=q,expected=e),ensure_ascii=False,separators=(',',':'))+'\n'for q,e in zip(qs,rows)));print(len(qs),'independent original locator/helper observations',flush=True)
