"""Read the original renderTest expressions and goldens without consulting Kotlin."""
import re
from dataclasses import dataclass

@dataclass
class Function:
 name:str
 args:tuple=()
def apply(f,x):
 assert isinstance(f,Function),f
 args=f.args+(x,);name=f.name
 arity={'Just':1,'text':1,'literal':1,'Block':2,'lblock':2,'rblock':2,'cblock':2,'nest':2,'hang':3,
 'hcat':1,'hsep':1,'vcat':1,'vsep':1,'chomp':1,'nestle':1,'nowrap':1,'prefixed':2,'beforeNonBlank':1,
 'afterBreak':1,'blanklines':1,'vfill':1,'bold':1,'fg':2,'map':2}[name]
 if len(args)<arity:return Function(name,args)
 assert len(args)==arity
 if name in ['Just','text','literal']:return args[0]
 if name=='map':return [apply(args[0],v) for v in args[1]]
 if name=='bold':return ['styled',['weight',True],args[0]]
 if name=='fg':return ['styled',['fg',args[0]],args[1]]
 if name=='Block':return ['block',args[0],[[[None,[False,False,0,False,None,None,None],v]] for v in args[1]]]
 return [{'beforeNonBlank':'before','afterBreak':'after','vfill':'fill'}.get(name,name),*args]

class Parser:
 def __init__(self,s,start=0):self.s=s;self.at=start;self.token=None;self.next()
 def next(self):
  s=self.s
  while True:
   while self.at<len(s) and s[self.at].isspace():self.at+=1
   if s.startswith('--',self.at):self.at=s.find('\n',self.at);continue
   break
  start=self.at
  if s[start]=='"':
   self.at+=1;out=''
   while s[self.at]!='"':
    c=s[self.at];self.at+=1
    if c!='\\':out+=c;continue
    c=s[self.at];self.at+=1
    if c in 'nrt\\"':out+=dict(n='\n',r='\r',t='\t').get(c,c)
    elif c=='&':pass
    elif c.isdigit() or c=='x':
     pos=self.at-1 if c!='x' else self.at
     pattern=r'[0-9]+' if c!='x' else r'[a-fA-F0-9]+'
     m=re.match(pattern,s[pos:]);assert m
     out+=chr(int(m[0],10 if c!='x' else 16));self.at=pos+len(m[0])
    elif s[self.at-1:].startswith('ESC'):out+='\x1b';self.at+=2
    else:raise AssertionError(c)
   self.at+=1;self.token=('string',out);return
  m=re.match(r'\$\+\$|<\+>|<>|\$\$|::|[()\[\],$]|-?[0-9]+|[A-Za-z][A-Za-z0-9_]*',s[start:]);assert m,s[start:start+30]
  self.at+=len(m[0]);self.token=('int',int(m[0])) if re.fullmatch('-?[0-9]+',m[0]) else ('symbol',m[0])
  if self.token==('symbol','::'):
   m=re.match(r'\s*Doc\s+Text',s[self.at:]);assert m;suffix=len(m[0]);self.at+=suffix;self.next()
 def atom(self):
  kind,value=self.token;self.next()
  if kind in ['string','int']:return value
  if value=='(':
   result=self.expr();assert self.token[1]==')';self.next();return result
  if value=='[':
   result=[]
   while self.token[1]!=']':
    result.append(self.expr())
    if self.token[1]!=']':assert self.token[1]==',';self.next()
   self.next();return result
  if value in ['space','cr','empty','mempty']:return [{'mempty':'empty'}.get(value,value)]
  if value=='blankline':return ['blanklines',1]
  if value=='Nothing':return None
  if value=='blue':return 4
  return Function(value)
 def expr(self,minimum=0):
  left=self.atom()
  while True:
   kind,value=self.token
   if value in ['<>','<+>','$$','$+$','$']:
    prec={'$':0,'$$':5,'$+$':5,'<>':6,'<+>':6}[value]
    if prec<minimum:break
    self.next();right=self.expr(prec)
    left=apply(left,right) if value=='$' else [dict(zip(['<>','<+>','$$','$+$'],['append','spaced','above','separated']))[value],left,right]
   elif isinstance(left,Function) and (kind in ['string','int'] or value not in [')',']',',']):left=apply(left,self.atom())
   else:break
  return left

def original_render_tests(source):
 result=[]
 for match in re.finditer(r'renderTest\s+(?=")',source):
  p=Parser(source,match.end());title=p.atom();width=p.expr();doc=p.expr();expected=p.atom()
  assert isinstance(title,str) and isinstance(expected,str) and not isinstance(doc,Function)
  result.append(dict(name=title,width=width,doc=doc,golden=expected,line=source[:match.start()].count('\n')+1))
 assert len(result)==54
 return result
