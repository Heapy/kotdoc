#!/usr/bin/env python3
"""Translate original ordered Unicode/LaTeX records; no Kotlin expectations generated."""
import argparse,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=(root/'vendor/texmath-0.13.3.1/src/Text/TeXMath/Unicode/ToTeX.hs').read_text()
quoted=r'"(?:\\.|[^"\\])*"';character=r"'(?:\\.|[^'\\])*'"
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
   base=16 if c=='x' else 8;m=re.match('[0-9a-fA-F]+' if base==16 else '[0-7]+',s[i:]);assert m
   out+=chr(int(m[0],base));i+=len(m[0])
  elif c=='&':pass
  else:out+={'n':'\n','r':'\r','t':'\t','b':'\b','f':'\f','v':'\v','a':'\a','\\':'\\','"':'"',"'":"'"}[c]
 return out
pattern=re.compile(r'Record \{uchar = ('+character+r'), commands = (\[.*?\]), category = (\w+), comments = ('+quoted+r')\}')
rows=[]
for c,commands,category,comments in pattern.findall(src):
 c=hs(c);assert len(c)==1
 pairs=re.findall(r'\(('+quoted+r'),('+quoted+r')\)',commands)
 rows.append((ord(c),[(hs(a),hs(b)) for a,b in pairs],category,hs(comments)))
assert len(rows)==len(re.findall(r'Record \{uchar =',src)) and rows
q=lambda x:json.dumps(x,ensure_ascii=False).replace('$','${\'$\'}')
s='// Generated Kotlin translation of texmath ToTeX records; GPL-2.0-or-later.\n// © 2014 Matthew Pickering; mapping © 2011 Günter Milde, LPPL 1.3 or later.\n// Modified work: kotdoc MathTeXUnicodeData.kt, 2026; see licenses/Math-TeX-Mapping-NOTICE.txt.\npackage io.heapy.kotdoc.math\n\n'
for start in range(0,len(rows),100):
 s+=f'private fun unicodeTeXRecords{start//100}()=listOf(\n'
 lines=[]
 for c,commands,category,comments in rows[start:start+100]:lines.append(f'    MathUnicodeRecord({c},listOf('+','.join(q(a)+' to '+q(b) for a,b in commands)+'),MathSymbolType.'+category+','+q(comments)+')')
 s+=',\n'.join(lines)+'\n)\n'
s+='internal val mathTeXUnicodeRecords: List<MathUnicodeRecord> = ownMathList(buildList {\n'+''.join(f'    addAll(unicodeTeXRecords{i}())\n' for i in range((len(rows)+99)//100))+'})\n'
target=root/'core/src/main/kotlin/io/heapy/kotdoc/math/MathTeXUnicodeData.kt'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
if args.check:assert target.read_text()==s
else:target.write_text(s)
print('Verified' if args.check else 'Translated',len(rows),'ordered original Unicode/TeX records')
