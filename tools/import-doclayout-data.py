#!/usr/bin/env python3
"""Translate the original width table and shortcut guards, never oracle outputs."""
import argparse,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'vendor/doclayout-0.6/src/Text'
out=root/'core/src/main/resources/io/heapy/kotdoc/doclayout-widths.json'
target=root/'core/src/main/kotlin/io/heapy/kotdoc/layout/WidthShortcuts.kt'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
rows=[]
for value,kind in re.findall(r"\(\s*'([^']+)'\s*,\s*(\w+)\s*\)",(source/'unicodeWidth.inc').read_text()):
 cp={'\\NUL':0,'\\DEL':127}.get(value)
 if cp is None:cp=int(value[1:]) if value.startswith('\\') else ord(value)
 rows.append([cp,kind])
assert rows and rows[0]==[0,'Control'] and rows[-1]==[983040,'Ambiguous']
assert all(a[0]<b[0] for a,b in zip(rows,rows[1:]))
table=json.dumps(rows,separators=(',',':'))+'\n'
hs=(source/'DocLayout.hs').read_text()
kt=['// Generated from DocLayout 0.6 shortcut guards, BSD-3-Clause; see licenses/.',
    'package io.heapy.kotdoc.layout','','internal object WidthShortcuts {']
def cond(s):
 s=s.strip()
 if s=='otherwise':return 'else'
 s=re.sub(r"'\\x([0-9A-Fa-f]+)'",lambda m:'0x'+m[1],s)
 assert re.fullmatch(r'[c 0-9xA-Fa-f<>=&]+',s),s
 return s
def result(s):
 m=re.fullmatch(r'(narrow|wide|combining|control|ambiguous)State',s.strip());assert m,s
 return 'Width.'+m[1].capitalize()
for name in ['Narrow','Wide']:
 body=hs.split('updateMatchState'+name+' (MatchState',1)[1].split('  where',1)[0]
 clauses=re.split(r'\n    \| ',body)[1:]
 kt+=['',f'    fun {name.lower()}(c: Int): Width? = when {{']
 for clause in clauses:
  first,*rest=clause.splitlines();condition,value=first.rsplit(' =',1)
  value=value.split('--',1)[0].strip();condition=cond(condition)
  if value:kt.append(f'        {condition} -> {result(value)}')
  elif any('if |' in line for line in rest):
   kt.append(f'        {condition} -> when {{')
   for line in rest:
    line=line.split('--',1)[0].strip()
    if not line:continue
    m=re.fullmatch(r'(?:if )?\| (.+) -> (\w+)',line);assert m,line
    kt.append(f'            {cond(m[1])} -> {result(m[2])}')
   kt.append('        }')
  else:
   special={'c == 0x200D':'ZWJ','c == 0xFE0F':'EmojiPresentationMod','c >= 0x1F3FB && c <= 0x1F3FF':'EmojiSkinToneMod'}
   assert condition in special,condition
   kt.append(f'        {condition} -> Width.{special[condition]}')
 kt+=['        else -> null','    }']
kt+=['}'];code='\n'.join(kt)+'\n'
if a.check:
 assert out.read_text()==table and target.read_text()==code
else:
 out.parent.mkdir(parents=True,exist_ok=True);target.parent.mkdir(parents=True,exist_ok=True)
 out.write_text(table);target.write_text(code)
print(('Verified' if a.check else 'Generated')+f' {len(rows)} original Unicode width boundaries and both shortcut classifiers')
