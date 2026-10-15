#!/usr/bin/env python3
"""Translate original TeX text accents, literal commands and negation exceptions."""
import argparse,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1];s=(root/'vendor/texmath-0.13.3.1/src/Text/TeXMath/Readers/TeX.hs').read_text();q=r'"(?:\\.|[^"\\])*"';ch=r"'(?:\\.|[^'\\])*'"
def hs(s):
 s=s[1:-1];out='';i=0
 while i<len(s):
  c=s[i];i+=1
  if c!='\\':out+=c;continue
  c=s[i];i+=1
  if c in 'xo' or c.isdigit():
   base=16 if c=='x' else 8 if c=='o' else 10;prefix='' if c in 'xo' else c
   m=re.match('[0-9a-fA-F]+' if base==16 else '[0-7]+' if base==8 else '[0-9]*',s[i:]);out+=chr(int(prefix+m[0],base));i+=len(m[0])
  elif c=='&':pass
  else:out+={'n':'\n','r':'\r','t':'\t','\\':'\\','"':'"',"'":"'"}[c]
 return out
quote=lambda x:json.dumps(x,ensure_ascii=False).replace('$','${\'$\'}')
accents={}
for name in ['grave','acute','circ','tilde','umlaut','dot','macron','cedilla','hacek','breve']:
 rows=re.findall(r'^'+name+r' ('+ch+r') = ('+q+r')$',s,re.M);assert rows,name
 accents[name]=[(ord(hs(a)),hs(b)) for a,b in rows]
neg=re.findall('('+q+r')\s*->\s*('+q+')',s.split('toNeg x = case x of',1)[1].split('_ ',1)[0]);assert len(neg)==26
literals=re.findall(r'\(('+q+r'), return ('+q+r')\)',s.split('textCommands = M.fromList',1)[1].split('\nparseC',1)[0]);assert len(literals)==24
out='// Generated from Text.TeXMath.Readers.TeX, © 2009 John MacFarlane, GPL-2.0-or-later.\npackage io.heapy.kotdoc.math\n\n'
for name,rows in [('mathTeXNegations',neg),('mathTeXTextLiterals',literals)]:out+='internal val '+name+'=mapOf(\n'+',\n'.join('    '+quote(hs(a))+' to '+quote(hs(b)) for a,b in rows)+'\n)\n'
out+='internal val mathTeXAccents=mapOf(\n'+',\n'.join('    '+quote(name)+' to mapOf('+','.join(str(a)+' to '+quote(b) for a,b in rows)+')' for name,rows in accents.items())+'\n)\n'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args();target=root/'core/src/main/kotlin/io/heapy/kotdoc/math/MathTeXReaderData.kt'
if args.check:assert target.read_text()==out
else:target.write_text(out)
print('Verified' if args.check else 'Translated',len(neg),'negations,',len(literals),'literal commands and',sum(map(len,accents.values())),'accent exceptions')
