#!/usr/bin/env python3
"""Translate the complete original ordered operator dictionary, retaining duplicates."""
import argparse,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=(root/'vendor/texmath-0.13.3.1/src/Text/TeXMath/Readers/MathML/MMLDict.hs').read_text()
quoted=r'"(?:\\.|[^"\\])*"'
pattern=re.compile(r'Operator \{oper = ('+quoted+r'), description = ('+quoted+r'), form = (F\w+), priority = (-?\d+), lspace = (-?\d+), rspace = (-?\d+), properties = (\[[^\n]*?\])\}')
def hs(s):
 s=s[1:-1];out='';i=0
 while i<len(s):
  c=s[i];i+=1
  if c!='\\':out+=c;continue
  c=s[i];i+=1
  if c.isdigit():
   end=i
   while end<len(s) and s[end].isdigit():end+=1
   out+=chr(int(c+s[i:end]));i=end
  elif c in 'xo':
   base=16 if c=='x' else 8;match=re.match('[0-9a-fA-F]+' if base==16 else '[0-7]+',s[i:]);assert match
   out+=chr(int(match[0],base));i+=len(match[0])
  elif c=='&':pass
  else:out+={'n':'\n','r':'\r','t':'\t','b':'\b','f':'\f','v':'\v','a':'\a','\\':'\\','"':'"',"'":"'"}[c]
 return out
rows=[]
for a,b,c,d,e,f,g in pattern.findall(src):rows.append([hs(a),hs(b),c,int(d),int(e),int(f),[hs(s) for s in re.findall(quoted,g)]])
assert len(rows)==len(re.findall(r'Operator \{oper =',src)) and rows
data='// Generated from texmath 0.13.3.1; © 2014 Matthew Pickering, GPL-2.0-or-later.\npackage io.heapy.kotdoc.math\n\n'
for start in range(0,len(rows),100):
 data+=f'private fun operators{start//100}()=listOf(\n'
 lines=[]
 for a,b,c,d,e,f,g in rows[start:start+100]:
  q=lambda x:json.dumps(x,ensure_ascii=False).replace('$','${\'$\'}')
  lines.append('    MathOperatorRecord('+q(a)+','+q(b)+',FormType.'+c+','+str(d)+'L,'+str(e)+'L,'+str(f)+'L,listOf('+','.join(q(s) for s in g)+'))')
 data+=',\n'.join(lines)+'\n)\n'
data+='internal val mathMLOperatorTable: List<MathOperatorRecord> = ownMathList(buildList {\n'+''.join(f'    addAll(operators{i}())\n' for i in range((len(rows)+99)//100))+'})\n'
target=root/'core/src/main/kotlin/io/heapy/kotdoc/math/MathMLOperatorData.kt'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
if a.check:assert target.read_text()==data
else:target.write_text(data)
print('Verified' if a.check else 'Translated',len(rows),'ordered original MathML operators')
