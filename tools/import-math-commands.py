#!/usr/bin/env python3
"""Translate every original TeX command table, retaining duplicate insertion order."""
import argparse,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=(root/'vendor/texmath-0.13.3.1/src/Text/TeXMath/Readers/TeX/Commands.hs').read_text()
q=r'"(?:\\.|[^"\\])*"'
def hs(s):
 s=s[1:-1];out='';i=0
 while i<len(s):
  c=s[i];i+=1
  if c!='\\':out+=c;continue
  c=s[i];i+=1
  if c.isdigit():
   m=re.match(r'\d*',s[i:]);out+=chr(int(c+m[0]));i+=len(m[0])
  elif c=='x':
   m=re.match(r'[0-9a-fA-F]+',s[i:]);out+=chr(int(m[0],16));i+=len(m[0])
  elif c=='&':pass
  else:out+={'n':'\n','r':'\r','t':'\t','\\':'\\','"':'"'}[c]
 return out
quote=lambda x:json.dumps(x,ensure_ascii=False).replace('$','${\'$\'}')
def expression(s):
 ts=re.findall(q+r'|[A-Za-z]+|[-+]?\d+|[%/()]',s);at=0
 def take():
  nonlocal at
  x=ts[at];at+=1;return x
 def exp():
  nonlocal at
  t=take()
  if t=='(':
   x=exp();assert take()==')';return x
  if t.startswith('"'):return quote(hs(t))
  if t=='ESpace':
   rest=ts[at:];ns=re.findall(r'-?\d+',s);assert len(ns)==2,s;at=len(ts)
   return 'MathExp.ESpace(MathRational('+ns[0]+'L,'+ns[1]+'L))'
  if t=='str':return 'MathExp.EText(MathTextType.TextNormal,'+exp()+')'
  if t in ['EIdentifier','ENumber','EMathOperator']:return 'MathExp.'+t+'('+exp()+')'
  if t in ['ESymbol','EText']:
   enum=take();return 'MathExp.'+t+'('+('MathSymbolType.' if t=='ESymbol' else 'MathTextType.')+enum+','+exp()+')'
  if t=='ESuper':return 'MathExp.ESuper('+exp()+','+exp()+')'
  raise AssertionError((s,t))
 result=exp();assert at==len(ts),(s,ts[at:]);return result
names=['styleOps','textOps','enclosures','operators','symbolMapOverrides','siUnitMap'];tables={}
for name in names:
 body=source.split(name+' =',1)[1].split('\n\n',1)[0]
 rows=re.findall(r'^\s*[\[,]?\s*\(('+q+r'),\s*(.*?)\)\s*\]?\s*(?:--[^\n]*)?$',body,re.M)
 assert rows and len(rows)==len(re.findall(r'^\s*[\[,]?\s*\("',body,re.M)),name
 tables[name]=[(hs(k),v.strip()) for k,v in rows]
text='// Generated from complete Text.TeXMath.Readers.TeX.Commands.\n// © 2009–2022 John MacFarlane; GPL-2.0-or-later, licenses/Texmath-GPL-2.0.txt.\npackage io.heapy.kotdoc.math\n\n'
for name,rows in tables.items():
 typ='(List<MathExp>)->MathExp' if name=='styleOps' else '(String)->MathExp' if name=='textOps' else 'MathExp'
 text+='internal fun mathCommands'+name[0].upper()+name[1:]+'(): List<Pair<String,'+typ+'>> = listOf(\n'
 lines=[]
 for key,value in rows:
  if name=='styleOps':
   sty=re.findall(r'EStyled (Text\w+)',value);assert len(sty)==(2 if '.' in value else 1)
   val='MathExp.EStyled(MathTextType.'+sty[-1]+',xs)'
   if len(sty)==2:val='MathExp.EStyled(MathTextType.'+sty[0]+',listOf('+val+'))'
   val='{xs: List<MathExp>->'+val+'}'
  elif name=='textOps':
   sty=re.search(r'EText (Text\w+)',value)[1];val='{s: String->MathExp.EText(MathTextType.'+sty+',s)}'
  else:val=expression(value)
  lines.append('    '+quote(key)+' to '+val)
 text+=',\n'.join(lines)+'\n)\n\n'
text=text.rstrip()+'\n'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args();target=root/'core/src/main/kotlin/io/heapy/kotdoc/math/MathTeXCommandsData.kt'
if args.check:assert target.read_text()==text
else:target.write_text(text)
print('Verified' if args.check else 'Translated',{k:len(v) for k,v in tables.items()})
